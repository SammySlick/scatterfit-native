package com.scatterbrain.scatterfit.core

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Port of web src/lib/analysis/scoring.ts + the goal weights from goals.ts.
 * Tests are the acceptance: ScoringTest.kt mirrors web scoring.test.ts cases,
 * with expected values generated from the TypeScript engine itself.
 */

// ---------- Data types ----------

/** Per-day HR zone minutes (the slices the engine reads). */
data class HrZones(val z2: Double = 0.0, val z3: Double = 0.0, val z4: Double = 0.0, val z5: Double = 0.0)

/** Nightly sleep total (minutes can be fractional from Health Connect). */
data class NightTotal(val totalMin: Double)

/** One day's health record. Absent map key = no data (never a fake zero).
 *  Port of the web DailyData fields the scoring engine reads. */
data class DailyData(
    val steps: Map<String, Double> = emptyMap(),
    val caloriesEaten: Map<String, Double> = emptyMap(),
    val protein: Map<String, Double> = emptyMap(),
    val trainingMinutes: Map<String, Double> = emptyMap(),
    val weight: Map<String, Double> = emptyMap(),
    val bodyFat: Map<String, Double> = emptyMap(),
    val restingHr: Map<String, Double> = emptyMap(),
    val drinks: Map<String, Boolean> = emptyMap(),
    val alcoholUnits: Map<String, Double> = emptyMap(),
    val sleep: Map<String, NightTotal> = emptyMap(),
    val hrZones: Map<String, HrZones> = emptyMap(),
)

/** The settings slice the scoring engine reads. NOTE: kcalTarget and
 *  proteinTarget are RESOLVED values — the data layer applies macroMode
 *  (grams vs percent) before passing settings in; core never re-derives them. */
data class ScoringSettings(
    val stepTarget: Int = 10000,
    val kcalTarget: Int = 2500,
    val proteinTarget: Int = 160,
    val sleepTargetHours: Double = 8.0,
    val exerciseTargetPerWeek: Int = 210,
    val weightTarget: Double = 80.0,
    val weightRatePerWeek: Double = 0.5,
    val bodyFatTarget: Double = 17.0,
    val bodyFatRatePerWeek: Double = 0.3,
    val drinkDaysPerWeek: Int = 2,
    val unitsPerWeek: Int = 14,
    val z4Target: Int = 30,
    val z5Target: Int = 15,
    val weekMode: WeekMode = "monday",
    val activeGoal: GoalPreset = GoalPreset.FAT_LOSS,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val weightPhases: List<WeightPhase> = emptyList(),
    // personal stats (Mifflin-St Jeor inputs; web DEFAULT_SETTINGS values)
    val heightCm: Int = 178,
    val ageYears: Int = 35,
    val sex: com.scatterbrain.scatterfit.core.MifflinStJeor.Sex = com.scatterbrain.scatterfit.core.MifflinStJeor.Sex.MALE,
    val sexCustom: String = "",
    val bmrFormula: BmrFormula = BmrFormula.MALE,
)

enum class UnitSystem { METRIC, IMPERIAL }

data class WeightPhase(
    /** Day key (YYYY-MM-DD) the phase starts on. */
    val startDate: String,
    /** Weight (kg, internal) at the phase start — the pivot anchor. */
    val startWeight: Double,
    /** Weekly rate in kg/week, stored as a POSITIVE loss magnitude. */
    val rateWeekly: Double,
)

fun formatWeight(kg: Double, system: UnitSystem, sep: String = " "): String =
    if (system == UnitSystem.METRIC) "${"%.1f".format(kg)}${sep}kg"
    else "${"%.1f".format(kg * 2.20462)}${sep}lb"

// ---------- Goal identity ----------

enum class GoalId { STEPS, CALORIES, PROTEIN, SLEEP, EXERCISE, WEIGHT, BODY_FAT, ALCOHOL, UNITS, RHR_TREND, Z4PLUS }

enum class GoalPreset { FAT_LOSS, MAINTENANCE, BUILD_MUSCLE, FITNESS, CUSTOM }

val goalLabel: Map<GoalPreset, String> = mapOf(
    GoalPreset.FAT_LOSS to "Fat loss",
    GoalPreset.MAINTENANCE to "Maintenance",
    GoalPreset.BUILD_MUSCLE to "Build muscle",
    GoalPreset.FITNESS to "Get fitter",
    GoalPreset.CUSTOM to "Custom",
)

/** Preset weight tables (worksheet block 10). Prescriptive weights (custom)
 *  come with the editor — until then custom resolves to fat_loss. */
val GOAL_WEIGHTS: Map<GoalPreset, Map<GoalId, Double>> = mapOf(
    GoalPreset.FAT_LOSS to mapOf(
        GoalId.BODY_FAT to 10.0, GoalId.CALORIES to 8.0, GoalId.UNITS to 7.0,
        GoalId.PROTEIN to 6.0, GoalId.EXERCISE to 5.0, GoalId.WEIGHT to 0.0,
    ),
    GoalPreset.MAINTENANCE to mapOf(
        GoalId.CALORIES to 8.0, GoalId.BODY_FAT to 6.0, GoalId.UNITS to 6.0,
        GoalId.EXERCISE to 6.0, GoalId.SLEEP to 4.0, GoalId.WEIGHT to 0.0,
    ),
    GoalPreset.BUILD_MUSCLE to mapOf(
        GoalId.PROTEIN to 10.0, GoalId.EXERCISE to 8.0, GoalId.CALORIES to 6.0,
        GoalId.SLEEP to 6.0, GoalId.WEIGHT to 0.0,
    ),
    GoalPreset.FITNESS to mapOf(
        GoalId.EXERCISE to 10.0, GoalId.RHR_TREND to 7.0, GoalId.Z4PLUS to 6.0,
        GoalId.CALORIES to 4.0, GoalId.SLEEP to 5.0,
    ),
)

fun weightsFor(preset: GoalPreset): Map<GoalId, Double> =
    if (preset == GoalPreset.CUSTOM) GOAL_WEIGHTS.getValue(GoalPreset.FAT_LOSS) else GOAL_WEIGHTS.getValue(preset)

/** Effective weight table: SOFT targets (explicit 0 in the preset table, e.g.
 *  weight on fat loss) are lifted to HALF THE TOP weight ("soft targets count
 *  the same but halved"). Series with no entry at all stay unweighted —
 *  absence means "not part of this goal", not "soft". The denominator
 *  renormalises, so a soft series with no data still never drags the day. */
fun effectiveWeights(preset: GoalPreset): Map<GoalId, Double> {
    val table = weightsFor(preset)
    val top = table.values.maxOrNull() ?: 0.0
    val soft = (top / 2 * 10).toInt() / 10.0
    return table.mapValues { (_, w) -> if (w == 0.0) soft else w }
}

/** Weighted average over the series that HAVE a score on this day: a missing
 *  series never drags the day down, and zero-weight (soft) series never lift it. */
fun weightedDayScore(scores: List<Pair<GoalId, Double?>>, weights: Map<GoalId, Double>): Double? {
    var sum = 0.0
    var total = 0.0
    for ((id, score) in scores) {
        val w = weights[id] ?: 0.0
        if (score == null || w <= 0.0) continue
        sum += score * w
        total += w
    }
    return if (total > 0) sum / total else null
}

/** Momentum verdict word for the dashboard header ("Fat loss · on track"). */
fun goalVerdict(current: Double?): String {
    if (current == null) return "no data yet"
    if (current >= 60) return "on track"
    if (current >= 40) return "needs work"
    return "off track"
}

// ---------- Score primitives ----------

private fun clamp(v: Double): Double = max(0.0, min(100.0, v))

fun oneSidedScore(value: Double, target: Double): Double =
    clamp(value / max(1e-6, target) * 100)

fun twoSidedScore(value: Double, target: Double): Double =
    clamp(100 - abs(value - target) / max(1e-6, target * 0.25) * 100)

enum class PaceMode { REACH, LIMIT }

/** Pace verdict for an accumulating daily total. Early sparse data stays neutral. */
fun dailyPaceScore(
    value: Double?,
    target: Double,
    elapsedDay: Double,
    neutralBelow: Double = 0.0,
    mode: PaceMode = PaceMode.REACH,
): Double? {
    if (value == null || value < neutralBelow) return null
    val elapsed = max(0.1, min(1.0, elapsedDay))
    if (elapsedDay < 0.33 && value < target * elapsedDay * 0.75) return null
    val projected = value / elapsed
    return if (mode == PaceMode.LIMIT) {
        if (projected <= target) 100.0
        else clamp(100 - (projected - target) / max(1e-6, target * 0.1) * 100)
    } else oneSidedScore(projected, target)
}

fun lowerIsBetterScore(value: Double, target: Double): Double {
    val safeTarget = max(1e-6, target)
    return if (value <= safeTarget) 100.0 else clamp(100 - (value - safeTarget) / safeTarget * 100)
}

/** Weekly alcohol units: 100 at 0, ~85 at 4 u, 50 at target, 0 at 25 u (scaled with target). Continuous. */
fun unitsScore(units: Double, target: Double): Double {
    val t = max(1.0, target)
    val green = 4.0 / 14 * t
    val red = 25.0 / 14 * t
    if (units <= 0) return 100.0
    if (units <= green) return 100 - units / green * 15
    if (units <= t) return 85 - (units - green) / (t - green) * 35
    return clamp(50 - (units - t) / (red - t) * 50)
}

/** Shared progress scoring for weight and body-fat trends. */
fun progressTrendScore(
    now: Double?,
    before: Double?,
    target: Double,
    rate: Double,
    tolerance: Double,
    capBeforeTarget: Double,
): Double? {
    if (now == null || before == null) return null
    val needDown = before > target
    val crossed = if (needDown) now <= target + tolerance else now >= target - tolerance
    if (crossed) return 100.0
    val change = now - before
    val progress = if (needDown) -change else change
    val safeRate = max(0.01, rate)
    val raw = if (progress >= 0) 50 + progress / safeRate * 50 else 50 + progress / (2 * safeRate) * 50
    return min(capBeforeTarget, clamp(raw))
}

// ---------- Weight warmup (Sam's week-1 rule) ----------

enum class WeightWarmup { WEEK1, WEEK2, READY, NO_PHASES }

fun weightWarmup(day: String, trackingStart: String?): WeightWarmup {
    if (trackingStart == null) return WeightWarmup.NO_PHASES
    val daysIn = Glide.dayDiff(day, trackingStart)
    if (daysIn < 7) return WeightWarmup.WEEK1  // Days 1–7
    if (daysIn < 13) return WeightWarmup.WEEK2 // Days 8–13
    return WeightWarmup.READY                  // Day 14+
}

/** Index of the "before" SMA for the rate: Days 8-13 anchor to the Day-7 SMA
 *  (index = i - daysIn + 6); Day 14+ is the normal pairwise i-7. */
fun anchorPrevIdx(i: Int, day: String, trackingStart: String): Int {
    val daysIn = Glide.dayDiff(day, trackingStart).toInt()
    return if (daysIn < 13) max(0, i - daysIn + 6) else i - 7
}

// ---------- Small maths helpers (port of maths.ts, the bits scoring reads) ----------

fun average(values: List<Double>): Double? = if (values.isEmpty()) null else values.sum() / values.size

/** carryForwardGaps: fill nulls from the last known value forward (used for the steps why-line). */
fun carryForwardGaps(values: List<Double?>): List<Double?> {
    var last: Double? = null
    return values.map { v -> if (v != null) { last = v; v } else last }
}

fun rollingAverage(values: List<Double?>, window: Int): List<Double?> = values.mapIndexed { i, _ ->
    val slice = values.subList(max(0, i - window + 1), i + 1).filterNotNull()
    if (slice.isEmpty()) null else slice.sum() / slice.size
}

// ---------- Why-line text (port of weight.ts / bodyfat.ts display lines) ----------

/** Card one-liner (D3) for weight. Returns null during warmup. */
fun weightCardLine(
    sma: Double?,
    goalKg: Double,
    rateWeeklyKg: Double,
    warmup: WeightWarmup,
    formatWeight: (Double) -> String,
    today: LocalDate,
): String? {
    if (warmup == WeightWarmup.WEEK1 || warmup == WeightWarmup.WEEK2) return null
    if (warmup == WeightWarmup.NO_PHASES && sma == null) return null
    val eta = Glide.etaState(sma, goalKg, rateWeeklyKg, today)
    return when (eta.status) {
        Glide.EtaStatus.CROSSED -> "Target reached."
        Glide.EtaStatus.ON_PACE -> {
            val d = LocalDate.parse(eta.date ?: return null)
            val label = "${d.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${d.dayOfMonth}"
            "On pace to hit ${formatWeight(goalKg)} around $label."
        }
        Glide.EtaStatus.STALLED -> "Trend has flattened over the last 7 days."
        Glide.EtaStatus.NO_DATA -> null
    }
}

/** Why-line for the weight card, warmup-aware. */
fun weightWhyLine(
    latestAvgKg: Double?,
    goalKg: Double,
    rateKgPerWeek: Double,
    warmup: WeightWarmup,
    fmt: (Double) -> String,
): String {
    if (latestAvgKg == null) return "Not enough weigh-ins for a 7-day average"
    if (warmup == WeightWarmup.WEEK1) return "7-day average ${fmt(latestAvgKg)} — baseline week, no rate yet"
    if (warmup == WeightWarmup.WEEK2) return "7-day average ${fmt(latestAvgKg)} — rate settling (first-week water loss ignored)"
    return "7-day average ${fmt(latestAvgKg)}, target ${fmt(goalKg)} at ${fmt(abs(rateKgPerWeek))}/week"
}

/** Card one-liner (D3) for body fat. ETA shows only when the trend supports it. */
fun etaCardLine(sma: Double?, goal: Double, rateWeekly: Double, today: LocalDate): String? {
    val eta = Glide.etaState(sma, goal, rateWeekly, today)
    return when (eta.status) {
        Glide.EtaStatus.CROSSED -> "Target reached."
        Glide.EtaStatus.ON_PACE -> {
            val d = LocalDate.parse(eta.date ?: return null)
            val label = "${d.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${d.dayOfMonth}"
            "On pace to hit ${"%.0f".format(goal)}% around $label."
        }
        Glide.EtaStatus.STALLED -> "Trend has flattened over the last 7 days."
        Glide.EtaStatus.NO_DATA -> null
    }
}

// ---------- Goal series ----------

data class GoalDay(val day: String, val score: Double?)

data class GoalSeries(
    val id: GoalId,
    val name: String,
    val days: List<GoalDay>,
    val latest: Double?,
    val why: String,
)

/** todayKey injectable: series must NEVER depend on real wall-clock
 *  (the RHR swing tests drifted with the real calendar otherwise). */
fun computeGoalSeries(data: DailyData, s: ScoringSettings, days: Int, todayKey: String? = null): List<GoalSeries> {
    val keys = lastNDayKeys(days + 14, todayKey) // extra history for rolling windows
    val visibleFrom = keys.size - days
    val visible = keys.subList(keys.size - days, keys.size)

    val weightSeries = keys.map { data.weight[it] }
    val wPrep = Glide.prepareSeries(weightSeries)
    val weightAvg = Glide.smaSeries(wPrep.first, wPrep.second)
    val wTrackingStart = s.weightPhases.firstOrNull()?.startDate
    val bfPrepared = Glide.prepareSeries(keys.map { data.bodyFat[it] })
    val bfAvg = Glide.smaSeries(bfPrepared.first, bfPrepared.second)

    fun drinkWindow(i: Int): Int? {
        var count = 0
        var logged = false
        for (j in weekStartIndex(keys, i, s.weekMode)..i) {
            val v = data.drinks[keys[j]]
            if (v != null) logged = true
            if (v == true) count++
        }
        return if (logged) count else null
    }

    fun unitsWindow(i: Int): Double? {
        if (drinkWindow(i) == null) return null
        var total = 0.0
        for (j in weekStartIndex(keys, i, s.weekMode)..i) total += data.alcoholUnits[keys[j]] ?: 0.0
        return total
    }

    val stepDays = ArrayList<GoalDay>(days)
    val calDays = ArrayList<GoalDay>(days)
    val sleepDays = ArrayList<GoalDay>(days)
    val exDays = ArrayList<GoalDay>(days)
    val weightDays = ArrayList<GoalDay>(days)
    val bfDays = ArrayList<GoalDay>(days)
    val protDays = ArrayList<GoalDay>(days)
    val rhrDays = ArrayList<GoalDay>(days)
    val z4pDays = ArrayList<GoalDay>(days)
    val alcDays = ArrayList<GoalDay>(days)
    val unitDays = ArrayList<GoalDay>(days)

    keys.forEachIndexed { i, k ->
        if (i < visibleFrom) return@forEachIndexed

        val steps = data.steps[k]
        stepDays.add(GoalDay(k, steps?.let { oneSidedScore(it, s.stepTarget.toDouble()) }))

        val prot = data.protein[k]
        protDays.add(GoalDay(k, if (prot == null || prot <= 0) null else oneSidedScore(prot, s.proteinTarget.toDouble())))

        val eaten = data.caloriesEaten[k]
        // Partial-day trap: under 500 kcal logged is an incomplete day (forgot
        // to log), not a perfect zero-calorie score.
        calDays.add(GoalDay(k, if (eaten == null || eaten < 500) null else twoSidedScore(eaten, s.kcalTarget.toDouble())))

        // SOFT TARGET: sleep informs the card but is NEVER scored in the
        // momentum matrix — the aim is quality, not quantity.
        sleepDays.add(GoalDay(k, null))

        val zm = data.trainingMinutes[k]
        exDays.add(GoalDay(k, zm?.let { oneSidedScore(it, s.exerciseTargetPerWeek / 7.0) }))

        // Z4+ capacity: weekly Z4+Z5 minutes paced like training minutes.
        val zones = data.hrZones[k]
        val z4p = (zones?.z4 ?: 0.0) + (zones?.z5 ?: 0.0)
        z4pDays.add(GoalDay(k, if (zones == null) null else oneSidedScore(z4p, (s.z4Target + s.z5Target) / 7.0)))

        // RHR trend: directional, lower better. 7DA vs the previous 7DA —
        // needs 14 days of history, else null. Flat is neutral (50); the clamp
        // window is ±10 bpm (a normal ±3 swing costs ~15 pts, not 40).
        var rhrScore: Double? = null
        if (i >= 13) {
            val cur = average(keys.subList(i - 6, i + 1).mapNotNull { data.restingHr[it] })
            val prev = average(keys.subList(i - 13, i - 6).mapNotNull { data.restingHr[it] })
            if (cur != null && prev != null) {
                val d = cur - prev
                rhrScore = if (d <= 0.5) clamp(50 + min(10.0, max(0.0, -d)) * 5) else clamp(50 - min(10.0, d) * 5)
            }
        }
        rhrDays.add(GoalDay(k, rhrScore))

        // Week-1 rule: Days 1–7 no score (water-drop season); Days 8–13 the
        // rate anchors to the Day-7 SMA; Day 14+ pairwise.
        val wWarmup = weightWarmup(k, wTrackingStart)
        val wPrevIdx = if (wWarmup == WeightWarmup.WEEK2) anchorPrevIdx(i, k, wTrackingStart!!) else i - 7
        val wPrev = if (wPrevIdx >= 0) weightAvg[wPrevIdx] else weightAvg[i - 7]
        weightDays.add(GoalDay(
            k,
            if (data.weight[k] == null || wWarmup == WeightWarmup.WEEK1 || wPrev == null) null
            else progressTrendScore(weightAvg[i], wPrev, s.weightTarget, s.weightRatePerWeek, 0.2, 100.0),
        ))

        bfDays.add(GoalDay(
            k,
            if (data.bodyFat[k] == null) null
            else if (i - 7 < 0) null
            else progressTrendScore(bfAvg[i], bfAvg[i - 7], s.bodyFatTarget, s.bodyFatRatePerWeek, 0.2, 85.0),
        ))

        val drinkCount = drinkWindow(i)
        val cap = max(1, s.drinkDaysPerWeek).toDouble()
        alcDays.add(GoalDay(k, drinkCount?.let { lowerIsBetterScore(it.toDouble(), cap) }))

        val units = unitsWindow(i)
        val ucap = max(1, s.unitsPerWeek).toDouble()
        val drinksUnlogged = data.drinks[k] == true && (data.alcoholUnits[k] ?: 0.0) <= 0.0
        unitDays.add(GoalDay(k, units?.let { if (drinksUnlogged) 0.0 else unitsScore(it, ucap) }))
    }

    fun latest(d: List<GoalDay>): Double? = d.lastOrNull { it.score != null }?.score

    val filledSteps = carryForwardGaps(visible.map { data.steps[it] })
    val recentSteps = filledSteps.subList(max(0, filledSteps.size - 7), filledSteps.size).filterNotNull()
    val under = recentSteps.count { it < s.stepTarget }

    val sleepAvg = average(visible.subList(max(0, visible.size - 7), visible.size).mapNotNull { data.sleep[it]?.totalMin })
    val calLogged = visible.subList(max(0, visible.size - 7), visible.size).mapNotNull { data.caloriesEaten[it] }.filter { it > 0 }
    val calAvg = average(calLogged)
    val protAvg = average(visible.subList(max(0, visible.size - 7), visible.size).mapNotNull { data.protein[it] }.filter { it > 0 })
    val exAvg = average(visible.subList(max(0, visible.size - 7), visible.size).mapNotNull { data.trainingMinutes[it] })
    val latestBfAvg = bfAvg.lastOrNull { it != null }
    val latestWeightAvg = weightAvg.lastOrNull { it != null }
    val wWarmupToday = weightWarmup(visible.last(), wTrackingStart)
    val fmtWeight = { kg: Double -> formatWeight(kg, s.unitSystem) }

    return listOf(
        GoalSeries(
            GoalId.STEPS, "Steps", stepDays, latest(stepDays),
            if (recentSteps.isNotEmpty()) "$under of ${recentSteps.size} days with data under ${"%,d".format(s.stepTarget)}"
            else "No step data in the last 7 days",
        ),
        GoalSeries(
            GoalId.CALORIES, "Calories", calDays, latest(calDays),
            if (calLogged.size >= 3 && calAvg != null)
                "7-day average ${"%,d".format(calAvg.roundToInt())} kcal (${calLogged.size} logged days) vs ${"%,d".format(s.kcalTarget)} target"
            else "Not enough logged days yet",
        ),
        GoalSeries(
            GoalId.SLEEP, "Sleep", sleepDays, latest(sleepDays),
            if (sleepAvg != null) {
                val sa = sleepAvg.roundToInt()
                "7-day average ${floor(sa / 60.0).toInt()}h ${sa % 60}m vs ${"%.0f".format(s.sleepTargetHours)}h target · soft target — not scored"
            } else "No sleep records in the last 7 days",
        ),
        GoalSeries(
            GoalId.EXERCISE, "Exercise", exDays, latest(exDays),
            if (exAvg == null) "No HR data in the last 7 days"
            else "7-day average ${exAvg.roundToInt()} min, target ${s.exerciseTargetPerWeek} min/week",
        ),
        GoalSeries(
            GoalId.WEIGHT, "Weight", weightDays, latest(weightDays),
            weightWhyLine(latestWeightAvg, s.weightTarget, s.weightRatePerWeek, wWarmupToday, fmtWeight) +
                (weightCardLine(latestWeightAvg, s.weightTarget, s.weightRatePerWeek, wWarmupToday, fmtWeight, LocalDate.parse(visible.last())) ?: ""),
        ),
        GoalSeries(
            GoalId.BODY_FAT, "Body fat", bfDays, latest(bfDays),
            if (latestBfAvg != null) {
                val eta = etaCardLine(latestBfAvg, s.bodyFatTarget, s.bodyFatRatePerWeek, LocalDate.parse(visible.last()))
                "7-day average ${"%.1f".format(latestBfAvg)}%, target ${"%.0f".format(s.bodyFatTarget)}% at ${"%.1f".format(s.bodyFatRatePerWeek)}%/week" +
                    (eta?.let { " · $it" } ?: "")
            } else "Not enough body fat readings for a 7-day average",
        ),
        GoalSeries(
            GoalId.PROTEIN, "Protein", protDays, latest(protDays),
            if (protAvg != null) "Logged-day average ${protAvg.roundToInt()} g vs ${s.proteinTarget} g target"
            else "No protein logged in the last 7 days",
        ),
        GoalSeries(
            GoalId.ALCOHOL, "Alcohol", alcDays, latest(alcDays),
            "${drinkWindow(keys.size - 1) ?: 0} drink days of ${s.drinkDaysPerWeek} target this week",
        ),
        GoalSeries(
            GoalId.UNITS, "Alcohol units", unitDays, latest(unitDays),
            "${"%.1f".format(unitsWindow(keys.size - 1) ?: 0.0)} units of ${s.unitsPerWeek} target this week",
        ),
        GoalSeries(
            GoalId.RHR_TREND, "RHR trend", rhrDays, latest(rhrDays),
            if (rhrDays.lastOrNull()?.score == null) "Needs ~2 weeks of RHR history"
            else "7-day average vs the previous 7 days — lower is better",
        ),
        GoalSeries(
            GoalId.Z4PLUS, "Z4+ capacity", z4pDays, latest(z4pDays),
            "${((data.hrZones[keys.last()]?.z4 ?: 0.0) + (data.hrZones[keys.last()]?.z5 ?: 0.0)).roundToInt()}m Z4+ this week of ${s.z4Target + s.z5Target}m target",
        ),
    )
}

// ---------- Momentum ----------

data class MomentumPoint(val day: String, val daily: Double?, val rolling: Double?)

/** Momentum is a weighted sum per the ACTIVE GOAL's weight table. Omit
 *  `weights` for the legacy plain average (tests + verdict lines still use it). */
fun computeMomentum(series: List<GoalSeries>, days: Int, weights: Map<GoalId, Double>? = null): List<MomentumPoint> {
    val dayKeys = series.firstOrNull()?.days?.map { it.day } ?: emptyList()
    val daily = dayKeys.mapIndexed { i, _ ->
        if (weights == null) {
            val scores = series.mapNotNull { it.days.getOrNull(i)?.score }
            if (scores.isNotEmpty()) scores.sum() / scores.size else null
        } else {
            weightedDayScore(series.map { Pair(it.id, it.days.getOrNull(i)?.score) }, weights)
        }
    }
    val rolling = rollingAverage(daily, 7)
    return dayKeys.mapIndexed { i, day -> MomentumPoint(day, daily[i], rolling[i]) }.takeLast(days)
}

// ---------- Colour spectrum ----------

/**
 * Continuous score spectrum between the judgement stops:
 * 0 = off target (bad), 50 = near (warn), 100 = on target (good).
 * Web mixes in OKLab; this sRGB interpolation is the native equivalent
 * (promotion note: visually very close, not bit-identical).
 */
fun scoreToColorArgb(score: Double?): Long {
    val BAD = 0xFFF14D4CL
    val WARN = 0xFFF0BB3BL
    val GOOD = 0xFF4FD57FL
    if (score == null) return 0xFF8A8A8AL // muted foreground
    val t = max(0.0, min(100.0, score))
    fun mix(from: Long, to: Long, frac: Double): Long {
        val fa = (from shr 16) and 0xFF
        val fg = (from shr 8) and 0xFF
        val fb = from and 0xFF
        val ta = (to shr 16) and 0xFF
        val tg = (to shr 8) and 0xFF
        val tb = to and 0xFF
        val r = (fa + (ta - fa) * frac).toInt()
        val g = (fg + (tg - fg) * frac).toInt()
        val b = (fb + (tb - fb) * frac).toInt()
        return (0xFF000000L or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())
    }
    return if (t <= 50) mix(BAD, WARN, t * 2 / 100.0) else mix(WARN, GOOD, (t - 50) * 2 / 100.0)
}
