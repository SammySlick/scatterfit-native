package com.scatterbrain.scatterfit.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Glide-path core — port of web src/lib/glide.ts (Sam's spec, 2026-10-06 16:44/16:57).
 *
 * Shared maths for any "state" series that gets:
 *  1. A trailing 7-day SMA as the "true" value (Method 1: divide by logged count).
 *  2. Rate of change = SMA today − SMA 7 days ago.
 *  3. A glide path driven by an editable rate stored as a PHASE TABLE.
 *  4. An ETA, hidden when the measured trend contradicts it.
 *
 * Gap handling (Sam's Methods 1/2/3) is identical for every such series:
 *  - 1–2 missing days: leave null → SMA divides by the logged-day count.
 *  - 3–6 missing days: linear interpolation backfills.
 *  - ≥7 missing days: gap break — SMA suppressed until 3 consecutive logged days.
 */
object Glide {

    fun dayDiff(a: String, b: String): Long =
        ChronoUnit.DAYS.between(LocalDate.parse(b), LocalDate.parse(a))

    /** Gap analysis + fill. Returns per-day filled values (linear interpolation
     *  for 3–6 day gaps) plus a mask of days where the SMA is suppressed by a
     *  ≥7-day gap break (until 3 consecutive logged days). */
    fun prepareSeries(raw: List<Double?>): Pair<List<Double?>, List<Boolean>> {
        val n = raw.size
        val filled = raw.toMutableList()
        val suppressed = BooleanArray(n)
        val logged = raw.map { it != null }

        // Method 2: linear interpolation across 3–6 day gaps only.
        var prev = -1
        for (i in 0 until n) {
            if (!logged[i]) continue
            if (prev >= 0) {
                val gap = i - prev - 1
                if (gap in 3..6) {
                    val a = raw[prev]!!
                    val b = raw[i]!!
                    for (j in prev + 1 until i) {
                        filled[j] = a + (b - a) * (j - prev) / (gap + 1.0)
                    }
                }
            }
            prev = i
        }

        // Method 3: ≥7-day gap breaks the trend until 3 consecutive logged days.
        var run = 0
        var breaking = false
        var nullRun = 0
        prev = -1
        for (i in 0 until n) {
            if (!logged[i]) {
                nullRun++
                if (breaking || nullRun >= 7) suppressed[i] = true
                continue
            }
            if (prev >= 0) {
                val gap = i - prev - 1
                if (gap >= 7) breaking = true
            }
            if (breaking) {
                suppressed[i] = true
                run++
                if (run >= 3) {
                    breaking = false
                    suppressed[i] = false
                }
            }
            nullRun = 0
            prev = i
        }
        return Pair(filled, suppressed.toList())
    }

    /** Trailing 7-day SMA over prepared (filled) values, divided by the count of
     *  available values (Method 1 for the 1–2 missing days), suppressed during a
     *  gap break. Returns null where the SMA is unknown. */
    fun smaSeries(filled: List<Double?>, suppressed: List<Boolean>): List<Double?> {
        return filled.mapIndexed { i, _ ->
            if (suppressed[i]) null
            else {
                val start = maxOf(0, i - 6)
                val slice = filled.subList(start, i + 1).filterNotNull()
                if (slice.isEmpty()) null else slice.sum() / slice.size
            }
        }
    }

    enum class EtaStatus { CROSSED, ON_PACE, STALLED, NO_DATA }

    data class Eta(val status: EtaStatus, val daysRemaining: Long? = null, val date: String? = null)

    /** ETA state from the current SMA, goal value and weekly rate. `losing` means
     *  the goal is BELOW the current value (fat loss, weight loss). */
    fun etaState(sma: Double?, goal: Double, rateWeekly: Double, today: LocalDate): Eta {
        if (sma == null) return Eta(EtaStatus.NO_DATA)
        val losing = rateWeekly > 0
        if (losing && sma <= goal) return Eta(EtaStatus.CROSSED)
        val rateDaily = abs(rateWeekly) / 7.0
        if (!losing || rateDaily < 1e-9) return Eta(EtaStatus.STALLED)
        // Gaining while the goal is to lose: moving away — stalled semantics.
        val movingAway = losing && sma > goal && rateWeekly <= 0
        if (movingAway) return Eta(EtaStatus.STALLED)
        val daysRemaining = (sma - goal) / rateDaily
        if (daysRemaining <= 0) return Eta(EtaStatus.CROSSED)
        val date = today.plusDays(ceil(daysRemaining).toLong()).toString()
        return Eta(EtaStatus.ON_PACE, daysRemaining.toLong(), date)
    }
}
