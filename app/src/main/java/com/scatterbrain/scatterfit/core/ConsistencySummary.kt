package com.scatterbrain.scatterfit.core

import kotlin.math.min
import kotlin.math.roundToInt

/**
 * At-a-glance (AAG) consistency summaries for the Momentum — Breakdown screen.
 *
 * Scores/colour derive from computeGoalSeries where the engine has one (one
 * source of truth); the summariser adds the AAG layer: per-day tick squares,
 * logged/on-target counts, a right-edge weekly number, and a computed
 * one-line sentence. Nothing here invents targets — every threshold comes
 * from ScoringSettings.
 *
 * AAG metric set (decided 2026-10-13): Steps, Calories, Protein, Sleep,
 * Exercise, Alcohol units, Z2 training. RHR trend, Weight, Body fat and Z4+
 * capacity are TREND metrics — they belong on the deeper-dive screen, not AAG.
 *
 * SLEEP NOTE: the engine never scores sleep (soft target, see Scoring.kt) —
 * momentum deliberately excludes it. The Sleep summary here is AAG DISPLAY
 * ONLY: squares banded met/not-met against sleepTargetHours. It feeds no
 * momentum maths and must stay that way.
 *
 * EXERCISE NOTE: "sessions" is proxied as trainingMinutes > 0 that day. True
 * session semantics (incl. the elevated-HR attribution feature) are a parked
 * spec; when they land, only this file's session rule changes.
 */

enum class ConsistencySection { NEED_WORK, GOING_STEADY, GOOD_WORK }

/** One day of one metric. logged=false renders an empty outline square —
 *  never a fake zero. score is the engine band score (colour), except Sleep
 *  (display-only banding — see note above). */
data class ConsistencySquare(val day: String, val logged: Boolean, val score: Double?)

data class ConsistencySummary(
    /** Null for Z2 training (display-only rollup, not an engine series). */
    val id: GoalId?,
    val name: String,
    /** Null = unlogged: renders below all section headers (2026-10-13 rule:
     *  unlogged metrics sink to the bottom, never labelled as a verdict). */
    val section: ConsistencySection?,
    val unlogged: Boolean,
    val squares: List<ConsistencySquare>,
    val loggedDays: Int,
    val onTargetDays: Int,
    /** Right-edge weekly number, e.g. "58.2k" · "12,400 kcal" · "38/90m". */
    val rightEdge: String,
    /** Computed sentence under the metric — never static copy. */
    val sentence: String,
)

/** A day counts as "on target" when its engine score reaches Locked In (>= 60).
 *  Same threshold everywhere — change it here or nowhere. */
const val ON_TARGET_SCORE = 60.0

private fun List<ConsistencySquare>.onTargetCount(): Int =
    count { (it.score ?: 0.0) >= ON_TARGET_SCORE }

fun computeConsistencySummaries(
    data: DailyData,
    s: ScoringSettings,
    days: Int = 7,
    todayKey: String? = null,
): List<ConsistencySummary> {
    val series = computeGoalSeries(data, s, days, todayKey).associateBy { it.id }

    fun squaresOf(id: GoalId): List<ConsistencySquare>? =
        series[id]?.days?.map { ConsistencySquare(it.day, it.score != null, it.score) }

    val keys = lastNDayKeys(days, todayKey)

    fun weeklySum(map: Map<String, Double>, positiveOnly: Boolean = false): Double =
        keys.sumOf { k -> (map[k] ?: 0.0).let { if (positiveOnly && it <= 0.0) 0.0 else it } }

    fun fmtInt(v: Double): String = "%,d".format(v.roundToInt())

    val out = mutableListOf<ConsistencySummary>()

    // --- Steps ---
    squaresOf(GoalId.STEPS)?.let { sq ->
        val logged = sq.count { it.logged }
        val total = weeklySum(data.steps)
        val under = sq.count { it.logged && it.score!! < ON_TARGET_SCORE }
        out.add(ConsistencySummary(
            GoalId.STEPS, "Steps", null, logged == 0, sq, logged, sq.onTargetCount(),
            rightEdge = if (logged == 0) "—" else "%.1fk".format(total / 1000.0),
            sentence = if (logged == 0) "No steps logged in the last 7 days"
            else "Under ${"%,d".format(s.stepTarget)} on $under of $logged logged days — ${"%.1fk".format(total / 1000.0)} steps this week",
        ))
    }

    // --- Calories (weekly total + over/under vs target across logged days) ---
    run {
        val sq = squaresOf(GoalId.CALORIES) ?: return@run
        val loggedKeys = keys.filter { (data.caloriesEaten[it] ?: 0.0) > 0.0 }
        val total = weeklySum(data.caloriesEaten, positiveOnly = true)
        val delta = if (loggedKeys.isEmpty()) null
        else total - s.kcalTarget * loggedKeys.size
        out.add(ConsistencySummary(
            GoalId.CALORIES, "Calories", null, loggedKeys.isEmpty(), sq, loggedKeys.size, sq.onTargetCount(),
            rightEdge = if (loggedKeys.isEmpty()) "—" else "${fmtInt(total)} kcal",
            sentence = when {
                loggedKeys.isEmpty() -> "No calories logged in the last 7 days"
                delta!! > 0 -> "${fmtInt(delta)} kcal over target across ${loggedKeys.size} logged days"
                delta < 0 -> "${fmtInt(-delta)} kcal under target across ${loggedKeys.size} logged days"
                else -> "Exactly on target across ${loggedKeys.size} logged days"
            },
        ))
    }

    // --- Protein ---
    squaresOf(GoalId.PROTEIN)?.let { sq ->
        val logged = sq.count { it.logged }
        val total = weeklySum(data.protein, positiveOnly = true)
        val avg = if (logged == 0) null else total / logged
        out.add(ConsistencySummary(
            GoalId.PROTEIN, "Protein", null, logged == 0, sq, logged, sq.onTargetCount(),
            rightEdge = if (logged == 0) "—" else "${fmtInt(total)}g",
            sentence = if (avg == null) "No protein logged in the last 7 days"
            else "Logged-day average ${avg.roundToInt()}g vs ${s.proteinTarget}g target",
        ))
    }

    // --- Sleep (ONE row; AAG display-only banding — see header note) ---
    run {
        val targetMin = s.sleepTargetHours * 60.0
        val sq = keys.map { k ->
            val night = data.sleep[k]
            ConsistencySquare(
                k,
                night != null,
                night?.let { if (it.totalMin >= targetMin) 70.0 else 30.0 },
            )
        }
        val nights = sq.filter { it.logged }.map { data.sleep[it.day]!!.totalMin }
        val avgMin = if (nights.isEmpty()) null else nights.sum() / nights.size
        val onTarget = sq.count { (it.score ?: 0.0) >= ON_TARGET_SCORE }
        out.add(ConsistencySummary(
            GoalId.SLEEP, "Sleep", null, sq.none { it.logged }, sq, sq.count { it.logged }, onTarget,
            rightEdge = if (avgMin == null) "—"
            else {
                val t = nights.sum().roundToInt()
                "${t / 60}h ${t % 60}m"
            },
            sentence = if (avgMin == null) "No sleep records in the last 7 days"
            else {
                val m = avgMin.roundToInt()
                "Avg ${m / 60}h ${m % 60}m vs ${"%.0f".format(s.sleepTargetHours)}h target — on target $onTarget of ${nights.size} nights"
            },
        ))
    }

    // --- Exercise (sessions: any training minutes that day = a session) ---
    squaresOf(GoalId.EXERCISE)?.let { sq ->
        val sessions = keys.count { (data.trainingMinutes[it] ?: 0.0) > 0.0 }
        val totalMin = weeklySum(data.trainingMinutes)
        out.add(ConsistencySummary(
            GoalId.EXERCISE, "Exercise", null, sessions == 0, sq, sessions, sq.onTargetCount(),
            rightEdge = if (sessions == 0) "—" else "$sessions sessions",
            sentence = if (sessions == 0) "No training in the last 7 days"
            else "Trained $sessions of the last $days days — ${totalMin.roundToInt()} min this week",
        ))
    }

    // --- Alcohol units (weekly units vs weekly budget) ---
    squaresOf(GoalId.UNITS)?.let { sq ->
        val weekUnits = keys.sumOf { data.alcoholUnits[it] ?: 0.0 }
        out.add(ConsistencySummary(
            GoalId.UNITS, "Alcohol", null, sq.none { it.logged }, sq, sq.count { it.logged }, sq.onTargetCount(),
            rightEdge = "%.0f/%d".format(weekUnits, s.unitsPerWeek),
            sentence = "%.0f of ${s.unitsPerWeek} weekly units used".format(weekUnits),
        ))
    }

    // --- Z2 training (weekly minutes vs the real per-zone target) ---
    run {
        val dailyTarget = s.z2Target / 7.0
        val sq = keys.map { k ->
            val z = data.hrZones[k]
            ConsistencySquare(k, z != null, z?.let { min(100.0, it.z2 / dailyTarget * 100.0) })
        }
        val weekZ2 = keys.sumOf { data.hrZones[it]?.z2 ?: 0.0 }
        out.add(ConsistencySummary(
            null, "Z2 Training", null, sq.none { it.logged }, sq, sq.count { it.logged }, sq.onTargetCount(),
            rightEdge = "${weekZ2.roundToInt()}/${s.z2Target}m",
            sentence = "${weekZ2.roundToInt()} of ${s.z2Target} Z2 minutes this week",
        ))
    }

    // --- Sections: worst-average first over MEASURED metrics, then thirds
    //     (floor: 7 measured = 2/2/3 — with fewer than 9 metrics fixed 3/3/3
    //     would mislabel everything NEED_WORK). Unlogged metrics render below
    //     all sections (2026-10-13 review rule). ---
    val measured = out.filter { !it.unlogged }
    val unlogged = out.filter { it.unlogged }
    val avgOf = { c: ConsistencySummary -> c.squares.mapNotNull { it.score }.average() }
    val sorted = measured.sortedBy { avgOf(it) }
    val n = sorted.size
    val third = n / 3
    val withSections = sorted.mapIndexed { i, c ->
        c.copy(section = when {
            i < third -> ConsistencySection.NEED_WORK
            i < 2 * third -> ConsistencySection.GOING_STEADY
            else -> ConsistencySection.GOOD_WORK
        })
    }
    return withSections + unlogged.map { it.copy(section = null) }
}
