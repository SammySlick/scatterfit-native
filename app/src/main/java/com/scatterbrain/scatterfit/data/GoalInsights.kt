package com.scatterbrain.scatterfit.data

import com.scatterbrain.scatterfit.core.DailyData
import com.scatterbrain.scatterfit.core.WeekMode
import com.scatterbrain.scatterfit.core.dayBefore
import com.scatterbrain.scatterfit.core.previousWeekKeys
import com.scatterbrain.scatterfit.core.weekKeysEndingAt
import com.scatterbrain.scatterfit.core.GoalId
import com.scatterbrain.scatterfit.core.GoalPreset
import com.scatterbrain.scatterfit.core.GoalSeries
import com.scatterbrain.scatterfit.core.MifflinStJeor.bmrBase
import kotlin.math.roundToInt
import com.scatterbrain.scatterfit.core.ScoringSettings
import com.scatterbrain.scatterfit.core.goalLabel
import com.scatterbrain.scatterfit.core.goalVerdict

/**
 * Port of web src/lib/goal-insights.ts — the dashboard insight layer.
 * Operates on the FULL assembled daily data (meals, sessions, burn) — the
 * web's DailyData, not the trimmed engine input. Pure: every function takes
 * the data and an anchor day, never the wall clock.
 */
object GoalInsights {

    /** A day counts toward the streak when ANY of food, booze, or a workout is
     *  logged. Workout = Z3+ training minutes OR any HR exercise minutes (a walk
     *  in Z2 counts) OR a recorded session (Z3-only never matched the "even a
     *  walk counts" checklist claim). */
    fun dayLogged(data: AssembledDaily, day: String): Boolean {
        val food = (data.meals[day]?.size ?: 0) > 0 || (data.caloriesEaten[day] ?: 0.0) > 0
        val booze = (data.alcoholUnits[day] ?: 0.0) > 0 || data.drinks[day] == true
        val workout =
            (data.trainingMinutes[day] ?: 0.0) > 0 ||
                (data.hrZones[day]?.exerciseMinutes ?: 0.0) > 0 ||
                data.sessions.any { it.day == day }
        return food || booze || workout
    }

    data class StreakResult(val days: Int, val brokenAt: String?)

    /** Consecutive logged days ending today (or yesterday if today is still empty).
     *  Day stepping uses local date arithmetic — an earlier UTC round-trip skipped
     *  a day for anyone east of Greenwich. */
    fun computeStreak(data: AssembledDaily, today: String): StreakResult {
        val keys = (data.meals.keys + data.caloriesEaten.keys + data.alcoholUnits.keys + data.trainingMinutes.keys).toSortedSet()
        val logged = keys.filterTo(HashSet()) { dayLogged(data, it) }
        var days = 0
        var cursor = today
        if (cursor !in logged) cursor = dayBefore(cursor) // today not logged yet — don't break the streak
        while (cursor in logged) {
            days++
            cursor = dayBefore(cursor)
        }
        return StreakResult(days, if (days > 0) null else cursor)
    }

    data class Contribution(
        val id: GoalId,
        val label: String,
        val weight: Double,
        /** Average score across the week (null if no data). */
        val score: Double?,
        /** weight × score / ΣactiveWeights → its share of the momentum number. */
        val points: Double?,
        val status: ContributionStatus,
    )

    enum class ContributionStatus { POSITIVE, WATCH, NEGATIVE }

    private fun verdict(score: Double?): ContributionStatus = when {
        score == null -> ContributionStatus.WATCH
        score >= 80 -> ContributionStatus.POSITIVE
        score >= 50 -> ContributionStatus.WATCH
        else -> ContributionStatus.NEGATIVE
    }

    /** Card-speak for a week contribution: the card leads with the consequence,
     *  the arithmetic (±pts) stays on the momentum page. */
    fun contributionWord(status: ContributionStatus): String = when (status) {
        ContributionStatus.POSITIVE -> "is carrying you"
        ContributionStatus.WATCH -> "is holding steady"
        ContributionStatus.NEGATIVE -> "is dragging you down"
    }

    /** Build the sentence + evidence line for one goal card from its week
     *  contribution. "On target" days use the positive threshold (score ≥ 80).
     *  Returns null when the series didn't render a contribution this week. */
    data class CardLine(val status: ContributionStatus, val sentence: String, val evidence: String)

    fun cardContribution(
        goal: GoalSeries,
        contribution: Contribution?,
        weekKeys: List<String>,
    ): CardLine? {
        contribution ?: return null
        val scores = weekKeys.map { k -> goal.days.find { it.day == k }?.score }
        val onTarget = scores.count { it != null && it >= 80.0 }
        val anyData = scores.any { it != null }
        return CardLine(
            contribution.status,
            "${goal.name} ${contributionWord(contribution.status)}",
            if (anyData) "$onTarget of ${weekKeys.size} days on target" else "no data this week",
        )
    }

    /** Where the week's momentum points come from, in weight order. */
    fun weekContributions(
        goals: List<GoalSeries>,
        weights: Map<GoalId, Double>,
        weekMode: WeekMode,
        week: WeekRef,
        today: String,
    ): Pair<List<Contribution>, Double?> {
        val keys = if (week == WeekRef.CURRENT) weekKeysEndingAt(today, weekMode) else previousWeekKeys(weekMode, today)
        val active = goals.filter { (weights[it.id] ?: 0.0) > 0.0 }
            .sortedByDescending { weights[it.id] ?: 0.0 }
        val items = active.map { goal ->
            val scores = keys.mapNotNull { k -> goal.days.find { it.day == k }?.score }
            val score = if (scores.isEmpty()) null else scores.sum() / scores.size
            Contribution(goal.id, goal.name, weights[goal.id] ?: 0.0, score, null, verdict(score))
        }
        // Denominator counts ONLY series with data this week — a missing series is
        // skipped, never diluted to zero. Mirrors weightedDayScore: parts sum
        // exactly to the renormalised week score.
        val scoredWeight = items.sumOf { if (it.score != null) it.weight else 0.0 }
        val filled = items.map {
            it.copy(points = if (it.score == null || scoredWeight == 0.0) null else (it.weight * it.score!!) / scoredWeight)
        }
        val total = if (scoredWeight > 0) filled.sumOf { it.points ?: 0.0 } else null
        return filled to total
    }

    enum class WeekRef { CURRENT, PREVIOUS }

    data class RadarSpoke(
        val id: GoalId,
        val label: String,
        val weight: Double,
        /** This week's average score (0–100), null = no data (never zero). */
        val current: Double?,
        val previous: Double?,
    )

    /** Radar adapter: one spoke per weighted series, straight from weekContributions
     *  (current + previous) — no parallel maths. Ordered by effective weight. */
    fun radarSpokes(
        goals: List<GoalSeries>,
        weights: Map<GoalId, Double>,
        weekMode: WeekMode,
        today: String,
    ): List<RadarSpoke> {
        val (now, _) = weekContributions(goals, weights, weekMode, WeekRef.CURRENT, today)
        val before = weekContributions(goals, weights, weekMode, WeekRef.PREVIOUS, today).first
            .associate { it.id to it.score }
        return now.map { RadarSpoke(it.id, it.label, it.weight, it.score, before[it.id]) }
    }

    /** The dashboard sentence: goal · verdict · the one to watch today. */
    fun goalSentence(
        goals: List<GoalSeries>,
        weights: Map<GoalId, Double>,
        activeGoal: GoalPreset,
        momentum: Double?,
        today: String,
    ): String {
        val verdictWord = goalVerdict(momentum)
        val active = goals.filter { (weights[it.id] ?: 0.0) > 0.0 }
        val scored = active.mapNotNull { g ->
            val s = g.days.find { it.day == today }?.score ?: return@mapNotNull null
            g to s
        }.sortedBy { (g, s) -> (weights[g.id] ?: 0.0) * s }
        val missing = active.find { g -> g.days.find { it.day == today }?.score == null }
        val watch = scored.firstOrNull()?.first?.name ?: missing?.name
        return if (watch == null) "${goalLabel[activeGoal]} · $verdictWord"
        else "${goalLabel[activeGoal]} · $verdictWord — ${watch.lowercase()} is the one to watch today"
    }

    data class WeekRange(val start: String, val end: String)
    data class Consistency(val weigh: Int, val logs: Int, val training: Int, val of: Int)
    data class CheckInResult(
        val week: WeekRange,
        val consistency: Consistency,
        val avgIntake: Double?,
        val avgBurn: Double?,
        /** kg/week over the check-in window (least-squares). */
        val weightTrend: Double?,
        val confidence: Confidence?,
        /** Recommended calorie target (null = keep as is). */
        val recommendation: Int?,
        val reason: String?,
    )

    enum class Confidence { HIGH, MEDIUM, LOW }

    /** Weekly review — runs on the first visit of a new week covering the week just gone. */
    fun weeklyCheckIn(data: AssembledDaily, settings: ScoringSettings, today: String): CheckInResult {
        val prev = previousWeekKeys(settings.weekMode, today)
        val end = prev.lastOrNull() ?: today
        val start = prev.firstOrNull() ?: today
        val of = prev.size
        val weigh = prev.count { data.weight[it] != null }
        val logs = prev.count { (data.meals[it]?.size ?: 0) > 0 || (data.caloriesEaten[it] ?: 0.0) > 0 }
        val training = prev.count { (data.trainingMinutes[it] ?: 0.0) > 0 }
        val consistency = Consistency(weigh, logs, training, of)
        val intakes = prev.mapNotNull { data.caloriesEaten[it] }.filter { it > 0 }
        val burns = prev.mapNotNull { data.caloriesBurned[it] }.filter { it > 0 }
        val avgIntake = if (intakes.isEmpty()) null else intakes.sum() / intakes.size
        val avgBurn = if (burns.isEmpty()) null else burns.sum() / burns.size

        // weight trend over the check-in window: least-squares slope × 7.
        // x = actual day offset within the week (index-based regression treated a
        // Mon/Tue/Sun weigh-in pattern as 3 consecutive days).
        val pts = prev.mapIndexed { i, k -> i to data.weight[k] }.filter { it.second != null && it.second!! > 0 }
        var weightTrend: Double? = null
        if (pts.size >= 3) {
            val n = pts.size
            val sx = pts.sumOf { it.first.toDouble() }
            val sy = pts.sumOf { it.second!! }
            val sxx = pts.sumOf { (it.first * it.first).toDouble() }
            val sxy = pts.sumOf { (it.first * it.second!!).toDouble() }
            val denom = n * sxx - sx * sx
            if (denom != 0.0) weightTrend = (n * sxy - sx * sy) / denom * 7
        }

        // confidence from diary consistency (weigh-ins matter most — they anchor TDEE)
        val confidence: Confidence? = when {
            weigh == 0 && logs == 0 -> null
            weigh >= 5 && logs >= 5 -> Confidence.HIGH
            weigh + logs >= 5 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }

        // recommendation: goal pace for fat loss ≈ 0.5–1% bodyweight/week; ±100–300 kcal.
        // SIGN-AWARE: gaining on fat loss must tighten, not eat more. Only offers a
        // move when the diary is consistent enough to trust — and the suggestion
        // never lands below BMR (Mifflin-St Jeor from settings).
        var recommendation: Int? = null
        var reason: String? = null
        val trustworthy = confidence != null && confidence != Confidence.LOW && logs >= 4 && (avgIntake ?: 0.0) >= 1000
        if (weightTrend != null && avgIntake != null && settings.activeGoal == GoalPreset.FAT_LOSS && trustworthy) {
            val losing = weightTrend <= 0
            val pace = (if (losing) -weightTrend else weightTrend) / maxOf(1.0, settings.weightTarget) * 100
            val floor = bmrBase(settings)
            val apply: (Int, String) -> Unit = { move, why ->
                recommendation = maxOf(floor, (avgIntake + move).roundToInt())
                reason = why
            }
            if (losing) {
                if (pace > 1.2) {
                    apply(150, "Weight trend ${"%.1f".format(weightTrend)} kg/week — faster than a 1.2%/week pace, risk of muscle loss. Ease up a little")
                } else if (pace < 0.3) {
                    apply(-200, "Weight trend ${"%.1f".format(weightTrend)} kg/week — slower than a 0.3–1%/week pace. Tighten a little")
                }
                // 0.3–1.2% loss = on pace — no recommendation.
            } else if (pace > 0.3) {
                apply(-200, "Weight trend +${"%.1f".format(weightTrend)} kg/week — moving away from the goal. Tighten a little")
            }
            // small gain (≤0.3%) reads as water/noise — no recommendation.
        }
        return CheckInResult(WeekRange(start, end), consistency, avgIntake, avgBurn, weightTrend, confidence, recommendation, reason)
    }

    /** Due any day of a new week until dismissed for that week (the clean slate,
     *  whenever you open it). Week start derives from the passed day so callers
     *  can pin the clock. */
    fun checkInDue(today: String, weekMode: WeekMode, lastDismissed: String?): Boolean {
        val start = weekKeysEndingAt(today, weekMode).first()
        return start <= today && lastDismissed != start
    }
}
