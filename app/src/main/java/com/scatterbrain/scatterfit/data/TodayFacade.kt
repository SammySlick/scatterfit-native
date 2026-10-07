package com.scatterbrain.scatterfit.data

import com.scatterbrain.scatterfit.core.DailyData
import com.scatterbrain.scatterfit.core.GoalId
import com.scatterbrain.scatterfit.core.GoalSeries
import com.scatterbrain.scatterfit.core.HrZones
import com.scatterbrain.scatterfit.core.MomentumPoint
import com.scatterbrain.scatterfit.core.NightTotal
import com.scatterbrain.scatterfit.core.ReadinessResult
import com.scatterbrain.scatterfit.core.ScoringSettings
import com.scatterbrain.scatterfit.core.computeGoalSeries
import com.scatterbrain.scatterfit.core.computeMomentum
import com.scatterbrain.scatterfit.core.effectiveWeights
import com.scatterbrain.scatterfit.core.readinessForData

/** Everything the Today card needs, computed once from the assembled data.
 *  Mirrors the web's TodayPage `scored` useMemo + streak + readiness + sentence. */
data class CardInputs(
    val goals: List<GoalSeries>,
    val momentum: List<MomentumPoint>,
    /** Latest non-null 7-day rolling momentum. */
    val current: Double?,
    /** Rolling momentum 8 points back (one week). */
    val weekAgo: Double?,
    val delta: Double?,
    /** Plain average across this week's logged days (weekly framing). */
    val weekCurrent: Double?,
    val streak: GoalInsights.StreakResult,
    val readiness: ReadinessResult?,
    val sentence: String,
)

object TodayFacade {

    /** AssembledDaily -> the trimmed engine DailyData. One shared adapter so the
     *  facade, charts and spike screens all feed the engine the same maps. */
    fun toCoreDaily(ad: AssembledDaily): DailyData = DailyData(
        steps = ad.steps,
        caloriesEaten = ad.caloriesEaten,
        protein = ad.protein,
        trainingMinutes = ad.trainingMinutes,
        weight = ad.weight,
        bodyFat = ad.bodyFat,
        restingHr = ad.restingHr,
        drinks = ad.drinks,
        alcoholUnits = ad.alcoholUnits,
        sleep = ad.sleep.mapValues { (_, n) -> NightTotal(n.totalMin) },
        hrZones = ad.hrZones.mapValues { (_, z) -> HrZones(z.z2, z.z3, z.z4, z.z5) },
    )

    /** Full card computation. Pure in data and clock: [today] is the anchor day
     *  (callers pass todayKey(zone) — never the wall clock inside tests). */
    fun todayCard(
        ad: AssembledDaily,
        settings: ScoringSettings,
        today: String,
        days: Int = 30,
    ): CardInputs {
        val data = toCoreDaily(ad)
        val goals = computeGoalSeries(data, settings, days, today)
        val weights = effectiveWeights(settings.activeGoal)
        val momentum = computeMomentum(goals, days, weights)
        val current = momentum.lastOrNull { it.rolling != null }?.rolling
        val weekAgo = momentum.getOrNull(momentum.size - 8)?.rolling
        val delta = if (current != null && weekAgo != null) current - weekAgo else null
        // WEEKLY FRAMING: the unit is the week — Monday is a clean slate.
        // Anchored to [today] (no wall-clock read): the same window the web's
        // currentWeekKeys yields when today is the anchor.
        val thisWeek = weekKeysEndingAt(today, settings.weekMode)
        val weekVals = thisWeek.map { k ->
            val dayScores = goals.mapNotNull { g -> g.days.find { it.day == k }?.score }
            if (dayScores.isEmpty()) null else dayScores.sum() / dayScores.size
        }.filterNotNull()
        val weekCurrent = if (weekVals.isEmpty()) null else weekVals.sum() / weekVals.size
        val streak = GoalInsights.computeStreak(ad, today)
        val rhrSeries = goals.find { it.id == GoalId.RHR_TREND }
        val readiness = readinessForData(data, rhrSeries?.days ?: emptyList(), settings.sleepTargetHours, today)
        val sentence = GoalInsights.goalSentence(goals, weights, settings.activeGoal, current, today)
        return CardInputs(goals, momentum, current, weekAgo, delta, weekCurrent, streak, readiness, sentence)
    }
}
