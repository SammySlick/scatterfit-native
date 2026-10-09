package com.scatterbrain.scatterfit.data

import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

/** The ONLY file allowed to know Health Connect types exist (Foundations:
 *  "never leak HC types past this layer"). One reader per RecordMethod: read
 *  from the SDK, extract bare primitives, hand to HcTranslate — which emits
 *  the gateway wire shape the proven parse pipeline consumes.
 *
 *  Everything testable lives in HcTranslate + LocalRecordConvert (JVM tests).
 *  This file is deliberately dumb: no decisions, no maths, no filtering —
 *  just extraction. */
object HcReaders {

    /** Max wall-time for ONE readRecords page call. */
    const val PAGE_TIMEOUT_MS: Long = 30_000

    /** Read the window in slices of this size. A wedged provider call
     *  costs one slice, not the whole history. */
    const val SLICE_MS: Long = 24L * 60 * 60 * 1000

    /** Circuit breaker: never trust a page token chain to terminate. */
    const val MAX_PAGES_PER_CALL: Int = 400

    /** Pause between pages of the same paginated read. */
    const val PAGE_PAUSE_MS: Long = 250

    /** Rate-limit retries per page: backoff 30s, 60s, 120s, 240s, 480s. */
    const val RATE_RETRY_MAX: Int = 4
    const val RATE_BACKOFF_BASE_MS: Long = 30_000

    /** Window is [fromMs, toMs). Note: sleep sessions may START before fromMs —
     *  a session crossing the cursor boundary is read on its start timestamp,
     *  same convention the web's fetch uses (start-gte/lt). Sync cursors are
     *  per method; the caller owns how far back fromMs reaches. */
    suspend fun readAll(
        client: HealthConnectClient,
        fromMs: Long,
        toMs: Long,
        methods: Set<RecordMethod> = RecordMethod.entries.toSet(),
    ): Map<RecordMethod, List<HealthRecord>> {
        val out = HashMap<RecordMethod, MutableList<HealthRecord>>()
        val totalSlices = ((toMs - fromMs) + SLICE_MS - 1) / SLICE_MS
        var sliceStart = fromMs
        var slice = 0
        while (sliceStart < toMs) {
            val sliceEnd = minOf(sliceStart + SLICE_MS, toMs)
            slice++
            for (m in methods) {
                if (m == RecordMethod.FOOD_LOG) continue // in-app logger, not HC — write path, separate store
                val list = when (m) {
                    RecordMethod.STEPS -> readSteps(client, sliceStart, sliceEnd)
                    RecordMethod.DISTANCE -> readDistance(client, sliceStart, sliceEnd)
                    RecordMethod.NUTRITION -> readNutrition(client, sliceStart, sliceEnd)
                    RecordMethod.FOOD_LOG -> emptyList()
                    RecordMethod.TOTAL_CALORIES_BURNED -> readTotalCaloriesBurned(client, sliceStart, sliceEnd)
                    RecordMethod.WEIGHT -> readWeight(client, sliceStart, sliceEnd)
                    RecordMethod.RESTING_HEART_RATE -> readRestingHeartRate(client, sliceStart, sliceEnd)
                    RecordMethod.SLEEP_SESSION -> readSleepSession(client, sliceStart, sliceEnd)
                    RecordMethod.EXERCISE_SESSION -> readExerciseSession(client, sliceStart, sliceEnd)
                    RecordMethod.BODY_FAT -> readBodyFat(client, sliceStart, sliceEnd)
                    RecordMethod.HEART_RATE -> readHeartRate(client, sliceStart, sliceEnd)
                    RecordMethod.BASAL_METABOLIC_RATE -> readBasalMetabolicRate(client, sliceStart, sliceEnd)
                }
                if (list.isNotEmpty()) out.getOrPut(m) { ArrayList() }.addAll(list)
            }
            Log.d("ScatterFitSync", "readAll: slice $slice/$totalSlices done (${sliceStart}..${sliceEnd})")
            sliceStart = sliceEnd
        }
        return out
    }

    private fun window(fromMs: Long, toMs: Long) =
        TimeRangeFilter.between(Instant.ofEpochMilli(fromMs), Instant.ofEpochMilli(toMs))

    private suspend inline fun <reified T : Record> read(
        client: HealthConnectClient,
        fromMs: Long,
        toMs: Long,
        label: String,
        block: (T) -> HealthRecord,
    ): List<HealthRecord> {
        val out = ArrayList<HealthRecord>()
        var token: String? = null
        var page = 0
        do {
            // withTimeoutOrNull + abandoned child: if the provider wedges mid-IPC
            // and the call never reaches a suspension point, plain withTimeout
            // CANNOT interrupt it (seen live 2026-10-09: STEPS page hung 20+ min,
            // timeout never fired). The orphaned child stays blocked on IO but
            // the reader MOVES ON — one wedge costs one page, not the sync.
            // Rate-limit aware: the provider serves throttled (tiny) pages as
            // quota runs low, then rejects outright with RemoteException. Back
            // off and retry the SAME token instead of burning the slice.
            var response: ReadRecordsResponse<T>? = null
            for (attempt in 0..RATE_RETRY_MAX) {
                try {
                    response = withTimeoutOrNull(PAGE_TIMEOUT_MS) {
                        coroutineScope { async { client.readRecords(ReadRecordsRequest<T>(window(fromMs, toMs), pageToken = token)) }.await() }
                    }
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    if (msg.contains("Rate limited", ignoreCase = true) || msg.contains("quota", ignoreCase = true)) {
                        val wait = RATE_BACKOFF_BASE_MS * (1L shl attempt)
                        Log.w("ScatterFitSync", "readAll: $label page=${page + 1} RATE LIMITED — backing off ${wait / 1000}s (attempt $attempt/$RATE_RETRY_MAX)")
                        delay(wait)
                        continue
                    }
                    throw e
                }
                if (response == null) {
                    Log.w("ScatterFitSync", "readAll: $label page=${page + 1} TIMED OUT (${PAGE_TIMEOUT_MS / 1000}s) — skipping rest of slice")
                    break
                }
                if (attempt > 0) Log.d("ScatterFitSync", "readAll: $label page=${page + 1} rate-limit retry #$attempt succeeded")
                break
            }
            if (response == null) break
            page++
            val n = response.records.size
            Log.d("ScatterFitSync", "readAll: $label page=$page rows=$n more=${token != null}")
            if (page >= MAX_PAGES_PER_CALL) {
                Log.w("ScatterFitSync", "readAll: $label hit MAX_PAGES_PER_CALL=$MAX_PAGES_PER_CALL — token chain may be looping, breaking")
                break
            }
            out.addAll(response.records.map(block))
            token = response.pageToken
            // Pacing: the provider throttles under request pressure (pages
            // shrank 1000 -> 36 rows as quota drained, live 2026-10-09). A
            // small pause between pages is polite and costs little.
            if (token != null) delay(PAGE_PAUSE_MS)
        } while (token != null)
        return out
    }

    private suspend fun readSteps(client: HealthConnectClient, fromMs: Long, toMs: Long) =
        read(client, fromMs, toMs, "Steps") { r: StepsRecord ->
            HcTranslate.steps(r.metadata.dataOrigin.packageName, r.startTime.toEpochMilli(),
                r.endTime.toEpochMilli(), r.count)
        }

    private suspend fun readDistance(client: HealthConnectClient, fromMs: Long, toMs: Long) =
        read(client, fromMs, toMs, "Distance") { r: DistanceRecord ->
            HcTranslate.distance(r.metadata.dataOrigin.packageName, r.startTime.toEpochMilli(),
                r.endTime.toEpochMilli(), r.distance.inMeters)
        }

    private suspend fun readNutrition(client: HealthConnectClient, fromMs: Long, toMs: Long) =
        read(client, fromMs, toMs, "Nutrition") { r: NutritionRecord ->
            HcTranslate.nutrition(r.metadata.dataOrigin.packageName, r.startTime.toEpochMilli(),
                r.endTime.toEpochMilli(), r.name, r.mealType,
                r.energy?.inKilocalories, r.protein?.inGrams,
                r.totalCarbohydrate?.inGrams, r.totalFat?.inGrams)
        }

    private suspend fun readTotalCaloriesBurned(client: HealthConnectClient, fromMs: Long, toMs: Long) =
        read(client, fromMs, toMs, "TotalCaloriesBurned") { r: TotalCaloriesBurnedRecord ->
            HcTranslate.totalCaloriesBurned(r.metadata.dataOrigin.packageName, r.startTime.toEpochMilli(),
                r.endTime.toEpochMilli(), r.energy.inKilocalories)
        }

    private suspend fun readWeight(client: HealthConnectClient, fromMs: Long, toMs: Long) =
        read(client, fromMs, toMs, "Weight") { r: WeightRecord ->
            HcTranslate.weight(r.metadata.dataOrigin.packageName, r.time.toEpochMilli(), r.weight.inKilograms)
        }

    private suspend fun readRestingHeartRate(client: HealthConnectClient, fromMs: Long, toMs: Long) =
        read(client, fromMs, toMs, "RestingHeartRate") { r: RestingHeartRateRecord ->
            HcTranslate.restingHeartRate(r.metadata.dataOrigin.packageName, r.time.toEpochMilli(), r.beatsPerMinute)
        }

    private suspend fun readSleepSession(client: HealthConnectClient, fromMs: Long, toMs: Long) =
        read(client, fromMs, toMs, "SleepSession") { r: SleepSessionRecord ->
            HcTranslate.sleepSession(r.metadata.dataOrigin.packageName, r.startTime.toEpochMilli(),
                r.endTime.toEpochMilli(), r.title,
                r.stages.map { HcTranslate.StageInput(it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), it.stage) })
        }

    private suspend fun readExerciseSession(client: HealthConnectClient, fromMs: Long, toMs: Long) =
        read(client, fromMs, toMs, "ExerciseSession") { r: ExerciseSessionRecord ->
            HcTranslate.exerciseSession(r.metadata.dataOrigin.packageName, r.startTime.toEpochMilli(),
                r.endTime.toEpochMilli(), r.title, r.exerciseType)
        }

    private suspend fun readBodyFat(client: HealthConnectClient, fromMs: Long, toMs: Long) =
        read(client, fromMs, toMs, "BodyFat") { r: BodyFatRecord ->
            HcTranslate.bodyFat(r.metadata.dataOrigin.packageName, r.time.toEpochMilli(), r.percentage.value)
        }

    private suspend fun readHeartRate(client: HealthConnectClient, fromMs: Long, toMs: Long) =
        read(client, fromMs, toMs, "HeartRate") { r: HeartRateRecord ->
            HcTranslate.heartRate(r.metadata.dataOrigin.packageName, r.startTime.toEpochMilli(),
                r.endTime.toEpochMilli(), r.samples.map { HcTranslate.SampleInput(it.time.toEpochMilli(), it.beatsPerMinute) })
        }

    private suspend fun readBasalMetabolicRate(client: HealthConnectClient, fromMs: Long, toMs: Long) =
        read(client, fromMs, toMs, "BasalMetabolicRate") { r: BasalMetabolicRateRecord ->
            HcTranslate.basalMetabolicRate(r.metadata.dataOrigin.packageName, r.time.toEpochMilli(),
                r.basalMetabolicRate.inWatts)
        }
}
