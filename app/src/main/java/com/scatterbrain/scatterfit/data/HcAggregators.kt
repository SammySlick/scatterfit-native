package com.scatterbrain.scatterfit.data

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.Period
import java.time.ZoneId

/** Daily-AGGREGATION reads for the high-volume record types (steps,
 *  distance, total calories burned).
 *
 *  Why not raw paging: Samsung Health writes steps in near-continuous
 *  increments — ~720k records / 120 days (live 2026-10-09). Raw record reads
 *  of that volume burn the provider's request quota (pages shrank 1000 -> 36
 *  rows under pressure, then outright rejection) and made first sync take
 *  hours. aggregateGroupByPeriod hands back ONE response of daily buckets
 *  for the whole window: one call per data origin, ~120 buckets, done.
 *
 *  Why PER ORIGIN: the web's proven daily pipeline (daily.ts) sums each data
 *  origin per day, then takes the MAX across origins — devices double-report
 *  the same activity (Samsung watch + Hume mirror). A single merged aggregate
 *  would defeat that (two origins writing steps would sum to double). So we
 *  aggregate per origin and synthesize one daily record per origin per day,
 *  with the origin's package name as the record's app: maxAcrossOrigins then
 *  behaves exactly as it does on raw records.
 *
 *  Local-day bucketing: AggregateGroupByPeriodRequest takes LocalDateTime
 *  bounds and Period.ofDays(1) — buckets are WALL-CLOCK local days, DST-safe,
 *  which matches the engine's day-keying rules (local-day trap: sleep crosses
 *  midnight, never UTC).
 *
 *  Origin discovery: the aggregation API cannot list origins, so we probe the
 *  freshest raw records (last [ORIGIN_PROBE_MS], newest first, capped pages)
 *  per type. Apps active in the probe window are assumed active for the
 *  window — an app that wrote 90 days ago but stopped gets missed; accepted
 *  for v1 (documented here on purpose). Blocked apps (myzone) never make the
 *  origin list, matching Records.clean.
 *
 *  Everything SDK-free is in [dailyRecords] — pure, JVM-tested. */
object HcAggregators {

    private const val TAG = "ScatterFitSync"

    /** Record types read via daily aggregation. Everything else stays raw. */
    val AGGREGATED: Set<RecordMethod> = setOf(
        RecordMethod.STEPS,
        RecordMethod.DISTANCE,
        RecordMethod.TOTAL_CALORIES_BURNED,
    )

    /** Origin probe window: last 48h of raw records (today + yesterday,
     *  matching the web's live-days idiom). */
    const val ORIGIN_PROBE_MS: Long = 2 * 24L * 60 * 60 * 1000

    /** Probe page cap — 2 pages is plenty to enumerate the writing apps. */
    const val ORIGIN_PROBE_MAX_PAGES: Int = 2

    /** More origins than this for one type is misbehaviour — stop. */
    const val MAX_ORIGINS: Int = 6

    /** Aggregate-call retries on rate limiting: 30s, 60s, 120s, 240s. */
    const val AGG_RETRY_MAX: Int = 3
    const val AGG_BACKOFF_BASE_MS: Long = 30_000

    /** Pure: one local day's aggregated values for one origin -> the
     *  gateway-shape records, paired with their method. A null value means
     *  "no data for that metric today" — emit nothing, same as the web
     *  (a day with no records is a gap, not a zero). JVM-tested. */
    fun dailyRecords(
        origin: String,
        startMs: Long,
        endMs: Long,
        methods: Set<RecordMethod>,
        stepsCount: Long?,
        distanceMeters: Double?,
        burnedKcal: Double?,
    ): List<Pair<RecordMethod, HealthRecord>> {
        val out = ArrayList<Pair<RecordMethod, HealthRecord>>(3)
        if (RecordMethod.STEPS in methods && stepsCount != null) {
            out += RecordMethod.STEPS to HcTranslate.steps(origin, startMs, endMs, stepsCount)
        }
        if (RecordMethod.DISTANCE in methods && distanceMeters != null) {
            out += RecordMethod.DISTANCE to HcTranslate.distance(origin, startMs, endMs, distanceMeters)
        }
        if (RecordMethod.TOTAL_CALORIES_BURNED in methods && burnedKcal != null) {
            out += RecordMethod.TOTAL_CALORIES_BURNED to
                HcTranslate.totalCaloriesBurned(origin, startMs, endMs, burnedKcal)
        }
        return out
    }

    /** Aggregate the whole window per origin, day-aligned. Calls [onSlice]
     *  once per (method, origin) with the origin's full bucket list — the
     *  buckets are already in memory, so the wedge risk lives in the CALL,
     *  not the processing; committing per origin is enough granularity.
     *
     *  Returns one map entry per method that produced records. Methods with
     *  nothing to report are ABSENT from the map — the engine treats an
     *  absent method as "not read this pass" and freezes its cursor instead
     *  of advancing it (so a failed origin discovery retries next sync
     *  rather than silently skipping the history). */
    suspend fun readAggregated(
        client: HealthConnectClient,
        fromMs: Long,
        toMs: Long,
        methods: Set<RecordMethod>,
        onSlice: suspend (RecordMethod, Long, List<HealthRecord>) -> Unit,
    ): Map<RecordMethod, List<HealthRecord>> {
        val aggregated = methods intersect AGGREGATED
        val out = HashMap<RecordMethod, MutableList<HealthRecord>>()
        if (aggregated.isEmpty()) return out

        // 1. Discover data origins (apps) from the freshest raw records.
        val byOrigin = LinkedHashMap<String, MutableSet<RecordMethod>>()
        for (m in aggregated) {
            val probe = HcReaders.probeRaw(client, m, toMs - ORIGIN_PROBE_MS, toMs, ORIGIN_PROBE_MAX_PAGES)
            for (r in probe) {
                val app = r.app ?: continue
                if (Records.isBlockedApp(r)) continue
                byOrigin.getOrPut(app) { LinkedHashSet() }.add(m)
            }
        }
        if (byOrigin.isEmpty()) {
            Log.w(TAG, "aggregate: no origins in probe window — aggregated types report nothing this pass")
            return out
        }
        if (byOrigin.size > MAX_ORIGINS) {
            Log.w(TAG, "aggregate: ${byOrigin.size} origins > MAX_ORIGINS=$MAX_ORIGINS — keeping the ${MAX_ORIGINS} first seen")
            val keep = byOrigin.keys.take(MAX_ORIGINS).toSet()
            byOrigin.keys.retainAll(keep)
        }

        // 2. One local-day aggregation call per origin, all metrics batched.
        val zone = ZoneId.systemDefault()
        val startLocal = Instant.ofEpochMilli(fromMs).atZone(zone).toLocalDate().atStartOfDay()
        val endLocal = Instant.ofEpochMilli(toMs).atZone(zone).toLocalDate().plusDays(1).atStartOfDay()
        for ((origin, mMethods) in byOrigin) {
            val metrics = buildSet {
                if (RecordMethod.STEPS in mMethods) add(StepsRecord.COUNT_TOTAL)
                if (RecordMethod.DISTANCE in mMethods) add(DistanceRecord.DISTANCE_TOTAL)
                if (RecordMethod.TOTAL_CALORIES_BURNED in mMethods) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
            }
            val request = AggregateGroupByPeriodRequest(
                metrics,
                TimeRangeFilter.between(startLocal, endLocal),
                Period.ofDays(1),
                setOf(DataOrigin(origin)),
            )
            var buckets: List<AggregationResultGroupedByPeriod> = emptyList()
            for (attempt in 0..AGG_RETRY_MAX) {
                try {
                    buckets = client.aggregateGroupByPeriod(request)
                    break
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    val rateLimited = msg.contains("Rate limited", ignoreCase = true) ||
                        msg.contains("quota", ignoreCase = true)
                    if (!rateLimited || attempt == AGG_RETRY_MAX) {
                        Log.w(TAG, "aggregate: $origin FAILED${if (rateLimited) " (rate limit, retries exhausted)" else ""}: $msg")
                        break
                    }
                    val wait = AGG_BACKOFF_BASE_MS * (1L shl attempt)
                    Log.w(TAG, "aggregate: $origin RATE LIMITED — backing off ${wait / 1000}s (attempt $attempt/$AGG_RETRY_MAX)")
                    delay(wait)
                }
            }
            if (buckets.isEmpty()) continue
            Log.d(TAG, "aggregate: $origin buckets=${buckets.size} metrics=${mMethods.map { it.name }}")

            // Buckets oldest -> newest; synthesize one daily record per
            // (origin, day, metric). The LAST bucket may end at tomorrow's
            // midnight even though the day is still in progress — its total
            // covers what exists; the next sync replaces it (same key).
            val sorted = buckets.sortedBy { it.startTime.toInstant(zone).toEpochMilli() }
            val lastEndMs = sorted.last().endTime.toInstant(zone).toEpochMilli()
            val perMethod = HashMap<RecordMethod, MutableList<HealthRecord>>()
            for (b in sorted) {
                val sMs = b.startTime.toInstant(zone).toEpochMilli()
                val eMs = b.endTime.toInstant(zone).toEpochMilli()
                for ((m, rec) in dailyRecords(
                    origin, sMs, eMs, mMethods,
                    b.result[StepsRecord.COUNT_TOTAL],
                    b.result[DistanceRecord.DISTANCE_TOTAL]?.inMeters,
                    b.result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories,
                )) {
                    perMethod.getOrPut(m) { ArrayList() }.add(rec)
                }
            }
            for ((m, list) in perMethod) {
                out.getOrPut(m) { ArrayList() }.addAll(list)
                onSlice(m, lastEndMs, list)
            }
        }
        return out
    }
}
