package com.scatterbrain.scatterfit.core

import com.scatterbrain.scatterfit.ui.SessionRecord
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The "how is this actually going" page.
 *
 * Sam, 2026-11-24: BWS "feels more like a diary and less like a data board",
 * and he wants to copy that but with a proper progress report on top. So this
 * is the reading-back layer: a window of days in, a small set of honest
 * verdict-free summaries out.
 *
 * Deliberately NOT a judgement engine. Nothing here returns good/bad. It
 * returns direction (down/up/steady), because weight falling is only good
 * during a cut, and this app must stay usable in a bulk. The screen chooses
 * what colour to use once it knows the goal.
 *
 * All pure. No Android, no storage, no clock — the caller passes `todayKey`.
 */
data class TrendWindow(
    /** First reading in the window, and when. */
    val firstValue: Double? = null,
    val firstDay: String? = null,
    /** Last reading in the window, and when. */
    val lastValue: Double? = null,
    val lastDay: String? = null,
    /** lastValue - firstValue. Null when either end is missing. */
    val change: Double? = null,
    /** Mean of the day values that exist. Never treats a gap as zero. */
    val average: Double? = null,
    /** How many days actually had a reading. */
    val readings: Int = 0,
) {
    val hasEnoughToReport: Boolean get() = readings >= 2 && change != null
}

enum class TrendDirection { Down, Up, Steady }

/**
 * Which way a series moved, with a dead band.
 *
 * Weight bounces 1kg on water alone. A "change" inside [steadyBand] reads as
 * Steady, so a page cannot announce progress that is really yesterday's
 * dinner.
 */
fun trendDirection(change: Double?, steadyBand: Double): TrendDirection = when {
    change == null -> TrendDirection.Steady
    change < -steadyBand -> TrendDirection.Down
    change > steadyBand -> TrendDirection.Up
    else -> TrendDirection.Steady
}

/**
 * Summarise a day-keyed series over the last [windowDays] days ending at
 * [todayKey] inclusive.
 *
 * Only days present in [values] count as readings — a missing day is missing,
 * never zero. This is the gap-fill rule from the chart work, applied here: a
 * week with three weigh-ins must not report a third of the truth.
 */
fun trendWindow(
    values: Map<String, Double>,
    todayKey: String,
    windowDays: Int,
): TrendWindow {
    if (windowDays <= 0) return TrendWindow()
    val today = runCatching { LocalDate.parse(todayKey) }.getOrNull() ?: return TrendWindow()
    val from = today.minusDays((windowDays - 1).toLong())

    val inWindow = values.entries
        .mapNotNull { (day, v) ->
            val d = runCatching { LocalDate.parse(day) }.getOrNull() ?: return@mapNotNull null
            if (d.isBefore(from) || d.isAfter(today)) return@mapNotNull null
            if (!v.isFinite()) return@mapNotNull null
            d to v
        }
        .sortedBy { it.first }

    if (inWindow.isEmpty()) return TrendWindow()

    val first = inWindow.first()
    val last = inWindow.last()
    return TrendWindow(
        firstValue = first.second,
        firstDay = first.first.toString(),
        lastValue = last.second,
        lastDay = last.first.toString(),
        // One reading is not a change. Returning 0.0 here would read as "steady",
        // which asserts something we have no evidence for: with a single weigh-in
        // this page would claim your weight held when it has only ever seen it once.
        change = if (inWindow.size >= 2) last.second - first.second else null,
        average = inWindow.map { it.second }.average(),
        readings = inWindow.size,
    )
}

/** How many of the last [windowDays] days have a reading. Used for "18 of 30". */
fun daysLogged(values: Map<String, Double>, todayKey: String, windowDays: Int): Int {
    val today = runCatching { LocalDate.parse(todayKey) }.getOrNull() ?: return 0
    val from = today.minusDays((windowDays - 1).toLong())
    return values.keys.count { day ->
        val d = runCatching { LocalDate.parse(day) }.getOrNull()
        d != null && !d.isBefore(from) && !d.isAfter(today)
    }
}

/** Step totals for the window. Gaps count as zero here — no steps walked is a real zero. */
fun stepWindow(values: Map<String, Double>, todayKey: String, windowDays: Int): TrendWindow {
    // Steps differ from weight: a day with no record inside a window where the
    // device was working usually means the phone was off, but the honest
    // reading for a TOTAL is zero. The average is over days with data, which is
    // why average and readings are exposed separately.
    return trendWindow(values, todayKey, windowDays)
}

/**
 * One line an actual human would say about a series, so the screen doesn't
 * invent its own phrasing.
 *
 * [unit] is appended raw ("kg", "%"); [decimals] controls rounding.
 */
fun trendSentence(
    label: String,
    window: TrendWindow,
    unit: String,
    decimals: Int = 1,
    steadyBand: Double = 0.2,
): String? {
    if (!window.hasEnoughToReport) return null
    val change = window.change ?: return null
    val dir = trendDirection(change, steadyBand)
    val mag = kotlin.math.abs(change)
    val shown = if (decimals == 0) mag.roundToInt().toString()
    else String.format(java.util.Locale.UK, "%.${decimals}f", mag)
    return when (dir) {
        TrendDirection.Steady -> "$label steady over ${window.readings} readings"
        TrendDirection.Down -> "$label down $shown$unit over ${window.readings} readings"
        TrendDirection.Up -> "$label up $shown$unit over ${window.readings} readings"
    }
}

/** Training summary for the window. */
data class TrainingWindow(
    val sessions: Int = 0,
    val totalVolumeKg: Double = 0.0,
    val totalSets: Int = 0,
    val pbs: Int = 0,
) {
    val isEmpty: Boolean get() = sessions == 0 && totalSets == 0
}

/**
 * Training totals for the window, reusing the same volume and set counting as
 * the HISTORY tab. Reusing matters: a progress page that disagrees with the
 * session list about how much you lifted is worse than no progress page.
 */
fun trainingWindow(
    records: List<SessionRecord>,
    todayKey: String,
    windowDays: Int,
): TrainingWindow {
    if (windowDays <= 0) return TrainingWindow()
    val today = runCatching { LocalDate.parse(todayKey) }.getOrNull() ?: return TrainingWindow()
    val from = today.minusDays((windowDays - 1).toLong())

    val inWindow = records.filter { r ->
        val d = runCatching { LocalDate.parse(r.dayKey) }.getOrNull()
        d != null && !d.isBefore(from) && !d.isAfter(today)
    }
    if (inWindow.isEmpty()) return TrainingWindow()

    // A PB is only countable in hindsight: a session set a PB if no earlier
    // session beat its top set for that exercise. Counted against the whole
    // history, not just the window, or every session in a fresh window is a PB.
    val earlierBest = HashMap<String, Double>()
    val ordered = records.sortedBy { it.startedAt }
    var pbs = 0
    for (r in ordered) {
        for (ex in r.exercises) {
            val top = ex.sets.filter { it.done }.mapNotNull { it.weightKg }.maxOrNull() ?: continue
            val best = earlierBest[ex.exerciseId]
            val isPb = best == null || top > best
            if (top > (best ?: Double.NEGATIVE_INFINITY)) earlierBest[ex.exerciseId] = top
            if (isPb && r in inWindow) pbs++
        }
    }

    return TrainingWindow(
        sessions = inWindow.size,
        totalVolumeKg = inWindow.sumOf { volumeKg(it) },
        totalSets = inWindow.sumOf { doneSetCount(it) },
        pbs = pbs,
    )
}
