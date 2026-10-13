package com.scatterbrain.scatterfit.core

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * At-a-glance (AAG) consistency summaries for the Momentum — Breakdown screen.
 *
 * AAG metric set (decided 2026-10-13): Steps, Calories, Protein, Sleep,
 * Exercise, Alcohol units, Z2 training. RHR trend, Weight, Body fat and Z4+
 * capacity are TREND metrics — they belong on the deeper-dive screen, not AAG.
 *
 * SQUARE GRAMMAR (Sam's mockup, 2026-10-14): every square is coloured by a
 * per-metric TOLERANCE band against the daily target — green = inside
 * tolerance, orange = outside but close, red = way off, grey outline =
 * unlogged. A logged day always has a band; grey IS the unlogged state.
 * The 5-rung momentum ladder stays on scores/pills/hero cards — squares
 * never use it (blue Unstoppable squares across a calories row would lie).
 * Bands are DISPLAY ONLY: they feed no momentum maths and must stay that way.
 *
 * SLEEP NOTE: the engine never scores sleep (soft target, see Scoring.kt) —
 * momentum deliberately excludes it. The Sleep summary here is AAG DISPLAY
 * ONLY. It feeds no momentum maths and must stay that way.
 *
 * EXERCISE NOTE: "sessions" is proxied as trainingMinutes > 0 that day. True
 * session semantics (incl. the elevated-HR attribution feature) are a parked
 * spec; when they land, only this file's session rule changes.
 */

enum class ConsistencySection { NEED_WORK, GOING_STEADY, GOOD_WORK }

enum class SquareBand { GREEN, ORANGE, RED, GREY }

/**
 * Per-metric tolerance bands for the AAG squares (Sam, 2026-10-14 — user
 * changeable in the metric's target settings):
 *
 *  Calories  |v - kcalTarget| <= kcalGreen -> green; <= kcalOrange -> orange; else red
 *  Steps     >= stepTarget green; >= stepOrangeFloor orange; else red
 *  Protein   |v - proteinTarget| <= proteinGreenUnder green; <= proteinOrangeUnder orange; else red
 *  Sleep     >= target green; within sleepOrangeMin of target orange; else red
 *  Exercise  minutes > 0 green; none grey (no red — no such thing as a bad rest day)
 *  Alcohol   <= daily share of unitsPerWeek green; <= share + alcoholOrangeOver orange; else red
 *  Z2        >= daily share of z2Target green; >= half share orange; else red
 */
data class ToleranceSettings(
    val kcalGreen: Int = 150,
    val kcalOrange: Int = 500,
    val stepOrangeFloor: Int = 7000,
    val proteinGreenUnder: Int = 20,
    val proteinOrangeUnder: Int = 50,
    val sleepOrangeMin: Int = 60,
    val alcoholOrangeOver: Double = 2.0,
)

/** The tolerance band for one day of one metric. id == null means the Z2
 *  display-only rollup (not an engine series). value is the raw day value:
 *  steps, kcal, protein g, sleep minutes, training minutes, alcohol units,
 *  Z2 minutes. null value -> GREY (unlogged). */
fun squareBand(id: GoalId?, value: Double?, s: ScoringSettings): SquareBand {
    if (value == null) return SquareBand.GREY
    val t = s.tolerances
    return when (id) {
        GoalId.STEPS -> when {
            value >= s.stepTarget -> SquareBand.GREEN
            value >= t.stepOrangeFloor -> SquareBand.ORANGE
            else -> SquareBand.RED
        }
        GoalId.CALORIES -> {
            val d = abs(value - s.kcalTarget)
            when {
                d <= t.kcalGreen -> SquareBand.GREEN
                d <= t.kcalOrange -> SquareBand.ORANGE
                else -> SquareBand.RED
            }
        }
        GoalId.PROTEIN -> {
            val d = abs(value - s.proteinTarget)
            when {
                d <= t.proteinGreenUnder -> SquareBand.GREEN
                d <= t.proteinOrangeUnder -> SquareBand.ORANGE
                else -> SquareBand.RED
            }
        }
        GoalId.SLEEP -> {
            val targetMin = s.sleepTargetHours * 60.0
            when {
                value >= targetMin -> SquareBand.GREEN
                value >= targetMin - t.sleepOrangeMin -> SquareBand.ORANGE
                else -> SquareBand.RED
            }
        }
        GoalId.EXERCISE -> if (value > 0.0) SquareBand.GREEN else SquareBand.GREY
        GoalId.UNITS -> {
            val allow = s.unitsPerWeek / 7.0
            when {
                value <= allow -> SquareBand.GREEN
                value <= allow + t.alcoholOrangeOver -> SquareBand.ORANGE
                else -> SquareBand.RED
            }
        }
        null -> { // Z2 training rollup
            val share = s.z2Target / 7.0
            when {
                value >= share -> SquareBand.GREEN
                value >= share / 2.0 -> SquareBand.ORANGE
                else -> SquareBand.RED
            }
        }
        // Other ids (WEIGHT, BODY_FAT, RHR_TREND, Z4PLUS) are trend metrics —
        // never requested as AAG squares.
        else -> SquareBand.GREY
    }
}

/** One day of one metric. band is the display colour (mockup grammar above);
 *  score is the engine's day score where one exists (section ordering) —
 *  Sleep keeps a display-only 70/30 score so unlogged-vs-short still sorts. */
data class ConsistencySquare(
    val day: String,
    val logged: Boolean,
    val score: Double?,
    val band: SquareBand,
)

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
    /** Right-edge weekly number with its weekly target where one exists,
     *  e.g. "28,000/70,000" · "7,289/12,600 kcal" · "23h 30m/56h" · "8/14".
     *  Day counts are NOT shown (the squares already say it — 2026-10-14). */
    val rightEdge: String,
    /** Today's value vs the daily target, e.g. "3,689/10,000 steps today",
     *  or "Not logged today". */
    val todayLine: String,
    /** Computed sentence under the metric — never static copy. */
    val sentence: String,
)

fun computeConsistencySummaries(
    data: DailyData,
    s: ScoringSettings,
    days: Int = 7,
    todayKey: String? = null,
): List<ConsistencySummary> {
    val series = computeGoalSeries(data, s, days, todayKey).associateBy { it.id }
    val keys = lastNDayKeys(days, todayKey)
    val today = keys.last()

    fun weeklySum(map: Map<String, Double>, positiveOnly: Boolean = false): Double =
        keys.sumOf { k -> (map[k] ?: 0.0).let { if (positiveOnly && it <= 0.0) 0.0 else it } }

    fun fmtInt(v: Double): String = "%,d".format(v.roundToInt())

    fun bandOf(id: GoalId?, value: Double?): SquareBand = squareBand(id, value, s)

    fun engineScore(id: GoalId, k: String): Double? =
        series[id]?.days?.firstOrNull { it.day == k }?.score

    fun squaresFromSeries(id: GoalId, values: Map<String, Double>): List<ConsistencySquare> =
        keys.map { k ->
            val v = values[k]
            val band = bandOf(id, v)
            ConsistencySquare(k, band != SquareBand.GREY, engineScore(id, k), band)
        }

    val out = mutableListOf<ConsistencySummary>()

    // --- Steps ---
    val stepSquares = squaresFromSeries(GoalId.STEPS, data.steps)
    run {
        val logged = stepSquares.count { it.logged }
        val total = weeklySum(data.steps)
        out.add(ConsistencySummary(
            GoalId.STEPS, "Steps", null, logged == 0, stepSquares, logged,
            stepSquares.count { it.band == SquareBand.GREEN },
            rightEdge = if (logged == 0) "—" else "${fmtInt(total)}/${fmtInt((s.stepTarget * 7).toDouble())}",
            todayLine = if (data.steps[today] == null) "Not logged today"
            else "${fmtInt(data.steps[today]!!)}/${fmtInt(s.stepTarget.toDouble())} steps today",
            sentence = if (logged == 0) "No steps logged in the last 7 days"
            else {
                val under = stepSquares.count { it.logged && it.band != SquareBand.GREEN }
                "Under ${"%,d".format(s.stepTarget)} on $under of $logged logged days — ${fmtInt(total)} steps this week"
            },
        ))
    }

    // --- Calories ---
    run {
        val squares = keys.map { k ->
            val v = data.caloriesEaten[k]
            val band = if (v == null || v <= 0.0) SquareBand.GREY else bandOf(GoalId.CALORIES, v)
            ConsistencySquare(k, band != SquareBand.GREY, engineScore(GoalId.CALORIES, k), band)
        }
        val loggedKeys = keys.filter { (data.caloriesEaten[it] ?: 0.0) > 0.0 }
        val total = weeklySum(data.caloriesEaten, positiveOnly = true)
        val delta = if (loggedKeys.isEmpty()) null else total - s.kcalTarget * loggedKeys.size
        out.add(ConsistencySummary(
            GoalId.CALORIES, "Calories", null, loggedKeys.isEmpty(), squares, loggedKeys.size,
            squares.count { it.band == SquareBand.GREEN },
            rightEdge = if (loggedKeys.isEmpty()) "—" else "${fmtInt(total)}/${fmtInt((s.kcalTarget * 7).toDouble())} kcal",
            todayLine = if ((data.caloriesEaten[today] ?: 0.0) <= 0.0) "Not logged today"
            else "${fmtInt(data.caloriesEaten[today]!!)}/${fmtInt(s.kcalTarget.toDouble())} kcal today",
            sentence = when {
                loggedKeys.isEmpty() -> "No calories logged in the last 7 days"
                delta!! > 0 -> "${fmtInt(delta)} kcal over target across ${loggedKeys.size} logged days"
                delta < 0 -> "${fmtInt(-delta)} kcal under target across ${loggedKeys.size} logged days"
                else -> "Exactly on target across ${loggedKeys.size} logged days"
            },
        ))
    }

    // --- Protein ---
    run {
        val squares = squaresFromSeries(GoalId.PROTEIN, data.protein)
        val logged = squares.count { it.logged }
        val total = weeklySum(data.protein, positiveOnly = true)
        out.add(ConsistencySummary(
            GoalId.PROTEIN, "Protein", null, logged == 0, squares, logged,
            squares.count { it.band == SquareBand.GREEN },
            rightEdge = if (logged == 0) "—" else "${fmtInt(total)}/${fmtInt((s.proteinTarget * 7).toDouble())}g",
            todayLine = if (data.protein[today] == null || data.protein[today]!! <= 0.0) "Not logged today"
            else "${fmtInt(data.protein[today]!!)}/${fmtInt(s.proteinTarget.toDouble())}g today",
            sentence = if (logged == 0) "No protein logged in the last 7 days"
            else "Logged-day average ${(total / logged).roundToInt()}g vs ${s.proteinTarget}g target",
        ))
    }

    // --- Sleep (ONE row; AAG display-only banding — see header note) ---
    run {
        val targetMin = s.sleepTargetHours * 60.0
        val squares = keys.map { k ->
            val night = data.sleep[k]
            val v = night?.totalMin
            val band = bandOf(GoalId.SLEEP, v)
            ConsistencySquare(k, night != null, v?.let { if (it >= targetMin) 70.0 else 30.0 }, band)
        }
        val nights = squares.filter { it.logged }.map { data.sleep[it.day]!!.totalMin }
        val avgMin = if (nights.isEmpty()) null else nights.sum() / nights.size
        fun hm(m: Int) = "${m / 60}h ${m % 60}m"
        out.add(ConsistencySummary(
            GoalId.SLEEP, "Sleep", null, squares.none { it.logged }, squares, squares.count { it.logged },
            squares.count { it.band == SquareBand.GREEN },
            rightEdge = if (avgMin == null) "—" else "${hm(nights.sum().roundToInt())}/${"%.0f".format(s.sleepTargetHours * 7)}h",
            todayLine = if (data.sleep[today] == null) "Not logged today"
            else "${hm(data.sleep[today]!!.totalMin.roundToInt())}/${"%.0f".format(s.sleepTargetHours)}h today",
            sentence = if (avgMin == null) "No sleep records in the last 7 days"
            else {
                val m = avgMin.roundToInt()
                "Avg ${hm(m)} vs ${"%.0f".format(s.sleepTargetHours)}h target — on target ${squares.count { it.band == SquareBand.GREEN }} of ${nights.size} nights"
            },
        ))
    }

    // --- Exercise (sessions: any training minutes that day = a session) ---
    run {
        val squares = keys.map { k ->
            val v = data.trainingMinutes[k]
            val band = bandOf(GoalId.EXERCISE, v)
            ConsistencySquare(k, band != SquareBand.GREY, engineScore(GoalId.EXERCISE, k), band)
        }
        val sessions = squares.count { it.logged }
        val totalMin = weeklySum(data.trainingMinutes)
        out.add(ConsistencySummary(
            GoalId.EXERCISE, "Exercise", null, sessions == 0, squares, sessions,
            squares.count { it.band == SquareBand.GREEN },
            rightEdge = if (sessions == 0) "—" else "$sessions sessions",
            todayLine = if (data.trainingMinutes[today]?.let { it > 0 } == true)
                "${data.trainingMinutes[today]!!.roundToInt()} min today" else "No session today",
            sentence = if (sessions == 0) "No training in the last 7 days"
            else "Trained $sessions of the last $days days — ${totalMin.roundToInt()} min this week",
        ))
    }

    // --- Alcohol units (weekly units vs weekly budget) ---
    run {
        val squares = squaresFromSeries(GoalId.UNITS, data.alcoholUnits)
        val weekUnits = keys.sumOf { data.alcoholUnits[it] ?: 0.0 }
        out.add(ConsistencySummary(
            GoalId.UNITS, "Alcohol", null, squares.none { it.logged }, squares, squares.count { it.logged },
            squares.count { it.band == SquareBand.GREEN },
            rightEdge = "%.0f/%d".format(weekUnits, s.unitsPerWeek),
            todayLine = if (data.alcoholUnits[today] == null) "Not logged today"
            else "${fmtUnits(data.alcoholUnits[today]!!)}/${fmtUnits(s.unitsPerWeek / 7.0)} units today",
            sentence = "%.0f of ${s.unitsPerWeek} weekly units used".format(weekUnits),
        ))
    }

    // --- Z2 training (weekly minutes vs the real per-zone target) ---
    run {
        val dailyTarget = s.z2Target / 7.0
        val squares = keys.map { k ->
            val z = data.hrZones[k]
            val v = z?.z2
            val band = bandOf(null, v)
            ConsistencySquare(k, z != null, v?.let { min(100.0, it / dailyTarget * 100.0) }, band)
        }
        val weekZ2 = keys.sumOf { data.hrZones[it]?.z2 ?: 0.0 }
        out.add(ConsistencySummary(
            null, "Z2 Training", null, squares.none { it.logged }, squares, squares.count { it.logged },
            squares.count { it.band == SquareBand.GREEN },
            rightEdge = "${weekZ2.roundToInt()}/${s.z2Target}m",
            todayLine = if (data.hrZones[today] == null) "Not logged today"
            else "${data.hrZones[today]!!.z2.roundToInt()}/${dailyTarget.roundToInt()}m today",
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

/** Alcohol units render with up to 1 decimal, never ".0" noise. */
private fun fmtUnits(v: Double): String =
    if (v == v.roundToInt().toDouble()) "%d".format(v.roundToInt()) else "%.1f".format(v)
