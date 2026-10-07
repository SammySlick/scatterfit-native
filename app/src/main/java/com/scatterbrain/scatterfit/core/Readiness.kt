package com.scatterbrain.scatterfit.core

import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * READINESS + DERIVATIONS — Kotlin port of web `src/lib/analysis/readiness.ts`
 * and the derivation block of `patterns.ts`. Accepted per Native Foundations:
 * web vitest cases are the acceptance tests; green in JUnit means ported.
 *
 * The Readiness Intersection (Sam 2026-10-06 18:27): blend the biological
 * recovery signals — last night's sleep deviation and the RHR trend score —
 * into one 0-100 readiness state with an actionable recommendation.
 *
 * TIMEZONE RULE (same trap as DayKeys): readiness days are LOCAL days. All
 * day-key derivations here take a ZoneId (default = device zone) so a UTC
 * round-trip can never shift a night or a morning reading.
 */

/* ---------- sleepDeviationScore (patterns.ts) ---------- */

/**
 * Sam 2026-10-06 17:57: sleep soft target — only undersleeping is penalised.
 * A 9h recovery night scores 100, not the 20 the Math.abs version gave it.
 */
fun sleepDeviationScore(min: Double, target: Double): Double =
    max(0.0, min(100.0, if (min >= target) 100.0 else 100.0 - (target - min) / (target * 0.25) * 100.0))

/* ---------- percentile + spearman (patterns.ts) ---------- */

/** Linear-interpolation percentile of a SORTED list (never mutates the input). */
fun percentile(sorted: List<Double>, p: Double): Double? {
    if (sorted.isEmpty()) return null
    val i = (sorted.size - 1) * p
    val lo = floor(i).toInt()
    val hi = ceil(i).toInt()
    return sorted[lo.coerceIn(0, sorted.size - 1)] +
        (sorted[hi.coerceIn(0, sorted.size - 1)] - sorted[lo.coerceIn(0, sorted.size - 1)]) * (i - lo)
}

/** Spearman rank correlation. Null when either side is constant (no ranks). */
fun spearman(x: List<Double>, y: List<Double>): Double? {
    if (x.size != y.size || x.isEmpty()) return null
    val rx = ranks(x) ?: return null
    val ry = ranks(y) ?: return null
    val n = x.size
    val mx = rx.average()
    val my = ry.average()
    var num = 0.0
    var dx2 = 0.0
    var dy2 = 0.0
    for (i in 0 until n) {
        val dx = rx[i] - mx
        val dy = ry[i] - my
        num += dx * dy; dx2 += dx * dx; dy2 += dy * dy
    }
    val den = kotlin.math.sqrt(dx2 * dy2)
    return if (den == 0.0) null else num / den
}

/** Average ranks (ties share the mean). Null if all values identical. */
private fun ranks(values: List<Double>): List<Double>? {
    val sortedIdx = values.withIndex().sortedBy { it.value }
    val out = DoubleArray(values.size)
    var i = 0
    while (i < sortedIdx.size) {
        var j = i
        while (j < sortedIdx.size && sortedIdx[j].value == sortedIdx[i].value) j++
        val avg = (i + j - 1) / 2.0 + 1.0
        for (k in i until j) out[sortedIdx[k].index] = avg
        i = j
    }
    return if (out.distinct().size == 1) null else out.toList()
}

fun strengthOf(r: Double?): String = when {
    r == null -> "none"
    kotlin.math.abs(r) >= 0.5 -> "strong"
    kotlin.math.abs(r) >= 0.3 -> "moderate"
    kotlin.math.abs(r) >= 0.1 -> "weak"
    else -> "none"
}

/* ---------- derivedRestingHr (patterns.ts) ---------- */

/** Daily resting HR = 5th percentile of the day's samples; <500 samples = no estimate. */
const val MIN_HR_SAMPLES = 500

fun derivedRestingHr(samples: List<Pair<Long, Double>>, zone: ZoneId = ZoneId.systemDefault()): Map<String, Double> {
    val byDay = LinkedHashMap<String, MutableList<Double>>()
    for ((t, v) in samples) byDay.getOrPut(dayKey(t, zone)) { ArrayList() }.add(v)
    val out = LinkedHashMap<String, Double>()
    for ((d, list) in byDay) {
        if (list.size < MIN_HR_SAMPLES) continue
        out[d] = kotlin.math.round(percentile(list.sorted(), 0.05)!! * 10.0) / 10.0
    }
    return out
}

/* ---------- morningReadings (patterns.ts) ---------- */

/** First reading before 11:00 local per day; later readings are excluded. */
fun morningReadings(readings: List<Pair<Long, Double>>, zone: ZoneId = ZoneId.systemDefault()): Map<String, Double> {
    val out = LinkedHashMap<String, Pair<Long, Double>>()
    for ((t, v) in readings) {
        val zdt = Instant.ofEpochMilli(t).atZone(zone)
        if (zdt.hour >= 11) continue
        val k = dayKey(t, zone)
        val cur = out[k]
        if (cur == null || t < cur.first) out[k] = t to v
    }
    return out.mapValues { it.value.second }
}

/* ---------- readiness engine (readiness.ts) ---------- */

enum class ReadinessState { PRIME, NORMAL, COMPROMISED }

data class ReadinessResult(val score: Int, val state: ReadinessState, val recommendation: String)

/** Prime needs both signals >= 70 — a single strong signal is a "normal" ceiling. */
const val SINGLE_SIGNAL_CEILING = 70

fun computeReadiness(rhrScore: Double?, sleepScore: Double?): ReadinessResult? {
    if (rhrScore == null && sleepScore == null) return null

    // Both signals -> average. One signal -> use it, capped: a single leg can't
    // carry you to "prime" on its own (sleep quality alone says nothing about
    // cardiovascular recovery, and vice versa).
    val score = if (rhrScore != null && sleepScore != null) (rhrScore + sleepScore) / 2.0
                else min(SINGLE_SIGNAL_CEILING.toDouble(), rhrScore ?: sleepScore ?: 0.0)
    val rounded = score.roundToInt()

    return when {
        rounded >= 70 -> ReadinessResult(rounded, ReadinessState.PRIME,
            "Your body is primed. Push for your Z4+ targets today.")
        rounded <= 40 -> ReadinessResult(rounded, ReadinessState.COMPROMISED,
            "Recovery is compromised. Swap Z4+ for Z2 active recovery to protect your momentum.")
        else -> ReadinessResult(rounded, ReadinessState.NORMAL,
            "Baseline recovery. Stick to the scheduled plan.")
    }
}

/**
 * Adapter: pull the two signals from app data.
 * - rhrScore: latest non-null score on or before [day] from the RHR series
 *   (one source of truth — the same score the RHR card shows).
 * - sleepScore: sleepDeviationScore of the most recent non-empty night on or
 *   before [day] (nights are keyed by the local date sleep STARTED).
 */
fun readinessForData(
    data: DailyData,
    rhrDays: List<GoalDay>,
    sleepTargetHours: Double,
    day: String,
): ReadinessResult? {
    val rhrScore = rhrDays.filter { it.day <= day }.lastOrNull { it.score != null }?.score
    val lastNight = data.sleep.entries
        .filter { it.key <= day }
        .sortedByDescending { it.key }
        .firstOrNull { it.value.totalMin > 0 }?.value
    val sleepScore = lastNight?.let { sleepDeviationScore(it.totalMin, sleepTargetHours * 60.0) }
    return computeReadiness(rhrScore, sleepScore)
}
