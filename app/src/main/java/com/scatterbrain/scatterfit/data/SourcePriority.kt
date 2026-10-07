package com.scatterbrain.scatterfit.data

import com.scatterbrain.scatterfit.core.dayKeyFromIso
import java.time.Instant

/**
 * SOURCE PRIORITY — the first-class per-source table (Native Foundations §L2).
 * Default tiers mirror the web app's lessons: Hume Band is the source of truth,
 * Samsung Health is a secondary duplicate writer, Myzone is fully excluded
 * (stale profile weight, fake 0% body fat). The table is the DEFAULT; the
 * per-metric source picker in Settings overrides per metric.
 */
enum class SourceTier { TRUTH, SECONDARY, UNRANKED, EXCLUDED }

const val SOURCE_HUME = "com.elink.fittrackhealth.pro"
const val SOURCE_SAMSUNG = "com.sec.android.app.shealth"
const val SOURCE_MYZONE = "com.myzone.myzoneble"
const val SOURCE_GOOGLE_FIT = "com.google.android.apps.fitness"

/** Display names, matching web `sourceName`. */
fun sourceName(app: String?): String = when (app) {
    null, "" -> "Unknown"
    SOURCE_HUME -> "Hume"
    SOURCE_SAMSUNG -> "Samsung Health"
    SOURCE_GOOGLE_FIT -> "Google Fit"
    else -> app
}

/**
 * Metrics that pick a trusted source when present (web: weight prefers Hume,
 * otherwise the latest reading from any eligible app). Other metrics take the
 * MAX across eligible origins — devices double-report the same activity, and
 * the sum would double-count. Resting HR is latest-wins by record order (web).
 */
private val TRUSTED_METRICS = setOf("weight", "bodyFat")

class SourcePriority(
    /** Per-metric user overrides: packageName -> tier. Replaces defaults for that metric. */
    val overrides: Map<String, Map<String, SourceTier>> = emptyMap(),
) {
    fun tierFor(metric: String, app: String): SourceTier =
        overrides[metric]?.get(app) ?: when (app) {
            SOURCE_HUME -> SourceTier.TRUTH
            SOURCE_SAMSUNG -> SourceTier.SECONDARY
            SOURCE_MYZONE -> SourceTier.EXCLUDED
            else -> SourceTier.UNRANKED
        }
}

/** A single data-origin record, in the shape every reader emits. */
data class LocalRecord(
    val start: String, // ISO instant
    val end: String,
    val app: String, // data origin packageName
    val data: Map<String, Any?>,
)

/** Per-day, per-origin totals with identical-window dedupe (web `perOriginDaily`). */
fun perOriginDaily(
    records: List<LocalRecord>,
    zone: java.time.ZoneId,
    read: (LocalRecord) -> Double?,
): Map<String, Map<String, Double>> {
    val out = mutableMapOf<String, MutableMap<String, Double>>()
    val seen = mutableSetOf<String>()
    for (r in records) {
        val v = read(r) ?: continue
        if (!v.isFinite()) continue
        val sig = "${r.app}|${r.start}|${r.end}"
        if (!seen.add(sig)) continue
        val bySrc = out.getOrPut(dayKeyFromInstant(r.start, zone)) { mutableMapOf() }
        bySrc[r.app] = (bySrc[r.app] ?: 0.0) + v
    }
    return out
}

fun dayKeyFromInstant(iso: String, zone: java.time.ZoneId): String =
    com.scatterbrain.scatterfit.core.dayKeyFromIso(iso, zone)

/**
 * Pick a value per day across eligible origins, web-faithful:
 * - MAX path (continuous metrics): sum per origin per day (identical windows
 *   deduped), then take the MAX origin — never the sum across devices.
 * - LATEST path (weight/bodyFat): skip EXCLUDED, keep each origin's last
 *   reading of the day, prefer the TRUTH origin even when it is older,
 *   otherwise the latest remaining reading.
 * Returns value and winning origin per day; day-keyed in the zone given.
 */
fun maxAcrossOrigins(
    records: List<LocalRecord>,
    metric: String,
    priority: SourcePriority,
    zone: java.time.ZoneId,
    read: (LocalRecord) -> Double?,
): Map<String, Pair<Double, String>> {
    val out = mutableMapOf<String, Pair<Double, String>>()
    if (metric in TRUSTED_METRICS) {
        // Latest path: per app per day keep the last reading (Hume sometimes logs
        // twice within a minute), then prefer TRUTH even when older (web rule).
        data class Pick(val app: String, val t: Long, val v: Double)
        val perAppDay = mutableMapOf<Pair<String, String>, Pick>() // (app, day) -> pick
        for (r in records) {
            if (priority.tierFor(metric, r.app) == SourceTier.EXCLUDED) continue
            val v = read(r) ?: continue
            val t = Instant.parse(r.start).toEpochMilli()
            val day = dayKeyFromInstant(r.start, zone)
            val id = r.app to day
            val cur = perAppDay[id]
            if (cur == null || t >= cur.t) perAppDay[id] = Pick(r.app, t, v)
        }
        for ((day, picks) in perAppDay.entries.groupBy { it.key.second }) {
            val list = picks.map { it.value }
            val truth = list.filter { priority.tierFor(metric, it.app) == SourceTier.TRUTH }
            val pool = if (truth.isNotEmpty()) truth else list
            val winner = pool.maxBy { it.t }
            out[day] = winner.v to winner.app
        }
        return out
    }
    // Max path: sum per origin per day (identical windows deduped), take the MAX origin.
    val bySrc = perOriginDaily(records, zone, read)
    for ((day, origins) in bySrc) {
        val eligible = origins.entries
            .filter { priority.tierFor(metric, it.key) != SourceTier.EXCLUDED }
        if (eligible.isEmpty()) continue
        val best = eligible.maxBy { it.value }
        out[day] = best.value to best.key
    }
    return out
}
