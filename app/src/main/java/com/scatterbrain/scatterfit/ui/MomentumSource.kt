package com.scatterbrain.scatterfit.ui

import com.scatterbrain.scatterfit.core.ConsistencySummary
import com.scatterbrain.scatterfit.core.GOAL_WEIGHTS
import com.scatterbrain.scatterfit.core.GoalDay
import com.scatterbrain.scatterfit.core.GoalId
import com.scatterbrain.scatterfit.core.GoalPreset
import com.scatterbrain.scatterfit.core.GoalSeries
import com.scatterbrain.scatterfit.core.MomentumContribution
import com.scatterbrain.scatterfit.core.MetricScoreInput
import com.scatterbrain.scatterfit.core.ProgressInputs
import com.scatterbrain.scatterfit.core.ScoreShape
import com.scatterbrain.scatterfit.core.ScoringSettings
import com.scatterbrain.scatterfit.core.dailyMetricScore
import com.scatterbrain.scatterfit.core.dayBefore
import com.scatterbrain.scatterfit.core.dayKeyFromIso
import com.scatterbrain.scatterfit.core.goalLabel
import com.scatterbrain.scatterfit.core.lastNDayKeys
import com.scatterbrain.scatterfit.core.computeConsistencySummaries
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.core.unitsScore
import com.scatterbrain.scatterfit.core.weekContributions
import com.scatterbrain.scatterfit.data.AssembledDaily
import com.scatterbrain.scatterfit.data.CardInputs
import com.scatterbrain.scatterfit.data.Daily
import com.scatterbrain.scatterfit.data.GoalInsights
import com.scatterbrain.scatterfit.data.GoalTargetsStore
import com.scatterbrain.scatterfit.data.HealthRecord
import com.scatterbrain.scatterfit.data.LocalRecordConvert
import com.scatterbrain.scatterfit.data.RecordMethod
import com.scatterbrain.scatterfit.data.SettingsStorage
import com.scatterbrain.scatterfit.data.TodayFacade
import com.scatterbrain.scatterfit.data.Zones
import com.scatterbrain.scatterfit.data.currentPrimaryMetricValue
import com.scatterbrain.scatterfit.data.formatGoalValue
import com.scatterbrain.scatterfit.data.makeDemoRecords
import com.scatterbrain.scatterfit.data.primaryMetricFor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class StreakInfo(
    val count: Int,
    val isTodayLogged: Boolean,
    val isAtRisk: Boolean
)

data class GoalProjectionInfo(
    val goalName: String,
    val elapsedText: String,
    val targetText: String,
    val currentText: String,
    val isOnTrack: Boolean,
    val projectionText: String = targetText,
)

data class DomainScore(val id: String, val score: Double?)

interface MomentumSource {
    val scoringSettings: ScoringSettings get() = ScoringSettings()
    fun momentumForDay(dayKey: String): Double?
    fun scoreForDay(dayKey: String): Double? = momentumForDay(dayKey)
    fun metricScoreForDay(id: GoalId, dayKey: String): Double? = null
    fun contributions(days: Int, anchorDay: String = todayKey()): List<MomentumContribution> = emptyList()
    fun isDayLogged(dayKey: String): Boolean
    fun computeStreak(today: String = todayKey(), todayLoggedOverride: Boolean = false): StreakInfo
    fun computeGoalProjection(today: String = todayKey()): GoalProjectionInfo
    fun caloriesVsAverageForDay(dayKey: String): Int?
    fun watchSentenceForDay(dayKey: String): String
    fun readinessScoreForDay(dayKey: String): Int
    fun readinessStateForDay(dayKey: String): String
    fun readinessTargetForDay(dayKey: String): String
    fun readinessDeltaForDay(dayKey: String): Int?

    /** Actual sleep minutes for the night ending on/near [dayKey]; null = no data yet. */
    fun sleepMinutesForDay(dayKey: String): Double?
    /** Resting HR for [dayKey]; null = no data yet (NOT "warming up" — say nothing). */
    fun restingHrForDay(dayKey: String): Double?

    /** True when the source is running on the demo month (no synced cache). UI must SHOW it. */
    val isUsingDemoData: Boolean get() = false

    fun domainScoresForDay(dayKey: String): List<DomainScore>
    fun consistencySeries(dayKey: String): List<GoalSeries> = emptyList()
    /** AAG summaries for the Momentum — Breakdown screen (see ConsistencySummary.kt). */
    fun consistencySummaries(dayKey: String): List<ConsistencySummary> = emptyList()
    fun zone2MinutesForDay(dayKey: String): Double? = null
    val sleepTargetHours: Double get() = 8.0
}

class TodayFacadeMomentumSource(
    private val daily: AssembledDaily = run {
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val demoMonth = makeDemoRecords(now, zone)
        val records = HashMap<RecordMethod, List<HealthRecord>>()
        for ((key, list) in demoMonth.records) {
            val m = RecordMethod.entries.firstOrNull { it.name == key } ?: continue
            records[m] = LocalRecordConvert.convertAll(m, list)
        }
        Daily.buildDailyDataFromMaps(records, Zones.DEFAULT_ZONES, zone, nowMs = now.toEpochMilli())
    },
    private val settings: ScoringSettings = SettingsStorage().loadSettings(),
    private val zone: ZoneId = daily.zone,
    var clock: java.time.Clock = java.time.Clock.systemDefaultZone(),
) : MomentumSource {

    override val scoringSettings: ScoringSettings get() = settings
    override val isUsingDemoData: Boolean = daily.steps.isEmpty() && daily.weight.isEmpty()

    private val cardCache = mutableMapOf<String, CardInputs>()

    private fun getCard(dayKey: String): CardInputs {
        return cardCache.getOrPut(dayKey) {
            TodayFacade.todayCard(daily, settings, dayKey)
        }
    }

    override fun metricScoreForDay(id: GoalId, dayKey: String): Double? {
        return when (id) {
            GoalId.STEPS -> {
                val value = daily.steps[dayKey] ?: return null
                dailyMetricScore(
                    ScoreShape.ONE_SIDED,
                    MetricScoreInput(value = value, target = settings.stepTarget.toDouble())
                )
            }
            GoalId.PROTEIN -> {
                val value = daily.protein[dayKey] ?: return null
                if (value <= 0.0) return null
                dailyMetricScore(
                    ScoreShape.ONE_SIDED,
                    MetricScoreInput(value = value, target = settings.proteinTarget.toDouble())
                )
            }
            GoalId.EXERCISE -> {
                val value = daily.trainingMinutes[dayKey] ?: return null
                val target = settings.exerciseTargetPerWeek / 7.0
                dailyMetricScore(
                    ScoreShape.ONE_SIDED,
                    MetricScoreInput(value = value, target = target)
                )
            }
            GoalId.CALORIES -> {
                val value = daily.caloriesEaten[dayKey] ?: return null
                if (value <= 0.0) return null
                dailyMetricScore(
                    ScoreShape.TWO_SIDED,
                    MetricScoreInput(value = value, target = settings.kcalTarget.toDouble())
                )
            }
            GoalId.SLEEP -> {
                val value = sleepMinutesForDay(dayKey) ?: return null
                val target = settings.sleepTargetHours * 60.0
                dailyMetricScore(
                    ScoreShape.TWO_SIDED,
                    MetricScoreInput(value = value, target = target)
                )
            }
            GoalId.WEIGHT -> {
                val value = daily.weight[dayKey] ?: return null
                val start = daily.weight.entries.minByOrNull { it.key }?.value ?: return null
                val goal = settings.weightTarget
                if (start == goal) return null
                dailyMetricScore(
                    ScoreShape.PROGRESS,
                    MetricScoreInput(
                        value = value,
                        target = goal,
                        progress = ProgressInputs(current = value, start = start, goal = goal)
                    )
                )
            }
            GoalId.BODY_FAT -> {
                val value = daily.bodyFat[dayKey] ?: return null
                val start = daily.bodyFat.entries.minByOrNull { it.key }?.value ?: return null
                val goal = settings.bodyFatTarget
                if (start == goal) return null
                dailyMetricScore(
                    ScoreShape.PROGRESS,
                    MetricScoreInput(
                        value = value,
                        target = goal,
                        progress = ProgressInputs(current = value, start = start, goal = goal)
                    )
                )
            }
            GoalId.UNITS, GoalId.ALCOHOL -> {
                val value = daily.alcoholUnits[dayKey]
                if (value == null && daily.drinks[dayKey] == null) return null
                val units = value ?: 0.0
                unitsScore(units, settings.unitsPerWeek.toDouble())
            }
            GoalId.Z4PLUS -> {
                val zones = daily.hrZones[dayKey] ?: return null
                val z4p = zones.z4 + zones.z5
                val target = (settings.z4Target + settings.z5Target) / 7.0
                dailyMetricScore(
                    ScoreShape.ONE_SIDED,
                    MetricScoreInput(value = z4p, target = target)
                )
            }
            GoalId.RHR_TREND -> {
                val series = getCard(dayKey).goals.find { it.id == GoalId.RHR_TREND }
                series?.days?.firstOrNull { it.day == dayKey }?.score
            }
        }
    }

    fun computeDayMomentum(dayKey: String): Double? {
        val today = todayKey(clock)
        if (dayKey > today) return null

        val weights = GOAL_WEIGHTS[settings.activeGoal] ?: GOAL_WEIGHTS[GoalPreset.FAT_LOSS]!!
        val participating = mutableListOf<Pair<Double, Double>>() // (score, weight)

        for ((goalId, weight) in weights) {
            if (weight <= 0.0) continue
            val score = metricScoreForDay(goalId, dayKey)
            if (score != null) {
                participating.add(score to weight)
            }
        }

        val totalWeight = participating.sumOf { it.second }
        if (totalWeight <= 0.0) return null

        return participating.sumOf { it.first * it.second } / totalWeight
    }

    override fun momentumForDay(dayKey: String): Double? {
        val today = todayKey(clock)
        if (dayKey > today) return null

        // Old path (TodayFacade card rolling momentum):
        // val card = getCard(dayKey)
        // val point = card.momentum.find { it.day == dayKey } ?: card.momentum.lastOrNull()
        // return point?.daily ?: card.current

        // New path (Momentum Rework Spec v1 — GOAL_WEIGHTS-weighted mean over metrics WITH DATA):
        return computeDayMomentum(dayKey)
    }

    override fun scoreForDay(dayKey: String): Double? = momentumForDay(dayKey)

    override fun isDayLogged(dayKey: String): Boolean {
        return GoalInsights.dayLogged(daily, dayKey)
    }

    override fun computeStreak(today: String, todayLoggedOverride: Boolean): StreakInfo {
        val card = getCard(today)
        val streakResult = card.streak
        val todayLogged = todayLoggedOverride || isDayLogged(today)
        val count = streakResult.days
        val isAtRisk = !todayLogged
        return StreakInfo(
            count = count,
            isTodayLogged = todayLogged,
            isAtRisk = isAtRisk
        )
    }

    override fun computeGoalProjection(today: String): GoalProjectionInfo {
        val todayDate = try { LocalDate.parse(today) } catch (_: Exception) { LocalDate.now(zone) }
        val startDate = try {
            LocalDate.parse(daily.steps.keys.minOrNull() ?: today)
        } catch (_: Exception) {
            todayDate
        }
        val daysElapsed = ChronoUnit.DAYS.between(startDate, todayDate).coerceAtLeast(0)
        val elapsedText = "DAY ${daysElapsed + 1}"

        val primaryMetric = primaryMetricFor(settings.activeGoal)
        val targetVal = GoalTargetsStore.targetFor(primaryMetric)
        val targetText = formatGoalValue(targetVal, primaryMetric, settings.unitSystem)

        val currentVal = when (primaryMetric) {
            GoalId.BODY_FAT -> daily.bodyFat[today] ?: daily.bodyFat.values.lastOrNull()
            GoalId.WEIGHT -> daily.weight[today] ?: daily.weight.values.lastOrNull()
            GoalId.RHR_TREND -> daily.restingHr[today] ?: daily.restingHr.values.lastOrNull()
            else -> null
        }
        val currentText = formatGoalValue(currentVal, primaryMetric, settings.unitSystem)

        val goalName = goalLabel[settings.activeGoal]?.uppercase() ?: "FAT LOSS"

        return GoalProjectionInfo(
            goalName = goalName,
            elapsedText = elapsedText,
            targetText = targetText,
            currentText = currentText,
            isOnTrack = true
        )
    }

    override fun caloriesVsAverageForDay(dayKey: String): Int? {
        val eaten = daily.caloriesEaten[dayKey] ?: return null
        val past = lastNDayKeys(7, dayKey).mapNotNull { daily.caloriesEaten[it] }
        val avg = if (past.isNotEmpty()) past.sum() / past.size else settings.kcalTarget.toDouble()
        return (eaten - avg).toInt()
    }

    override fun watchSentenceForDay(dayKey: String): String {
        val card = getCard(dayKey)
        return card.sentence
    }

    override fun readinessScoreForDay(dayKey: String): Int {
        val card = getCard(dayKey)
        return card.readiness?.score ?: 70
    }

    override fun readinessStateForDay(dayKey: String): String {
        val card = getCard(dayKey)
        val state = card.readiness?.state ?: "prime"
        return when (state.lowercase()) {
            "prime" -> "PRIMED"
            "normal" -> "NORMAL"
            "compromised" -> "COMPROMISED"
            else -> "PRIMED"
        }
    }

    override fun readinessTargetForDay(dayKey: String): String {
        val card = getCard(dayKey)
        return card.readiness?.recommendation ?: "Z4+"
    }

    override fun readinessDeltaForDay(dayKey: String): Int? {
        val cardToday = getCard(dayKey)
        val yesterdayKey = dayBefore(dayKey)
        val cardYesterday = getCard(yesterdayKey)
        val scoreToday = cardToday.readiness?.score ?: return null
        val scoreYesterday = cardYesterday.readiness?.score ?: return null
        return scoreToday - scoreYesterday
    }

    override fun sleepMinutesForDay(dayKey: String): Double? {
        // NB: sleep[dayKey] is SleepNight, sleepNights is MergedNight — keep the
        // lookups separate (a joined elvis chain infers a common supertype with
        // no totalMin; that's the r86/r88 failure).
        val byMap = daily.sleep[dayKey]
        if (byMap != null) return byMap.totalMin
        return daily.sleepNights.lastOrNull { it.night <= dayKey }?.totalMin
    }

    override fun restingHrForDay(dayKey: String): Double? = daily.restingHr[dayKey]

    override fun consistencySeries(dayKey: String): List<GoalSeries> {
        val keys = lastNDayKeys(30, dayKey)
        val allGoalIds = listOf(
            GoalId.BODY_FAT to "Body fat",
            GoalId.CALORIES to "Calories",
            GoalId.UNITS to "Alcohol units",
            GoalId.PROTEIN to "Protein",
            GoalId.EXERCISE to "Exercise",
            GoalId.STEPS to "Steps",
            GoalId.SLEEP to "Sleep",
            GoalId.WEIGHT to "Weight",
        )

        return allGoalIds.map { (id, name) ->
            val days = keys.map { k ->
                GoalDay(k, metricScoreForDay(id, k))
            }
            val latestScore = days.lastOrNull { it.score != null }?.score ?: 0.0
            GoalSeries(id, name, days, latestScore, "Momentum Rework v1")
        }
    }

    override fun contributions(days: Int, anchorDay: String): List<MomentumContribution> {
        val seriesList = consistencySeries(anchorDay)
        val weights = GOAL_WEIGHTS[settings.activeGoal] ?: GOAL_WEIGHTS[GoalPreset.FAT_LOSS]!!
        return weekContributions(seriesList, weights, days)
    }

    override fun consistencySummaries(dayKey: String): List<ConsistencySummary> =
        computeConsistencySummaries(TodayFacade.toCoreDaily(daily), settings, 7, dayKey)

    override fun zone2MinutesForDay(dayKey: String): Double? = daily.hrZones[dayKey]?.z2

    override val sleepTargetHours: Double get() = settings.sleepTargetHours

    override fun domainScoresForDay(dayKey: String): List<DomainScore> {
        val card = getCard(dayKey)

        fun recentScore(seriesId: GoalId): Double? {
            val series = card.goals.find { it.id == seriesId } ?: return null
            val targetDate = runCatching { LocalDate.parse(dayKey) }.getOrNull()
            val candidateIndex = series.days.indexOfLast { it.day <= dayKey }
            if (candidateIndex < 0) return null
            val startIndex = (candidateIndex - 6).coerceAtLeast(0)
            for (i in candidateIndex downTo startIndex) {
                val day = series.days[i]
                if (targetDate != null) {
                    val dayDate = runCatching { LocalDate.parse(day.day) }.getOrNull()
                    if (dayDate != null && ChronoUnit.DAYS.between(dayDate, targetDate) > 7) break
                }
                if (day.score != null) return day.score
            }
            return null
        }

        val exerciseScore = recentScore(GoalId.EXERCISE)
        val stepsScore = recentScore(GoalId.STEPS)
        val caloriesScore = recentScore(GoalId.CALORIES)
        val alcoholScore = recentScore(GoalId.ALCOHOL)
        val unitsScore = recentScore(GoalId.UNITS)

        val combinedAlcoholScore = when {
            alcoholScore != null && unitsScore != null -> kotlin.math.min(alcoholScore, unitsScore)
            alcoholScore != null -> alcoholScore
            unitsScore != null -> unitsScore
            else -> null
        }

        val result = mutableListOf<DomainScore>()
        if (exerciseScore != null) result.add(DomainScore("Training", exerciseScore))
        if (stepsScore != null) result.add(DomainScore("Steps", stepsScore))
        if (caloriesScore != null) result.add(DomainScore("Food", caloriesScore))
        if (combinedAlcoholScore != null) result.add(DomainScore("Alcohol", combinedAlcoholScore))

        android.util.Log.d("MyDayCards", "domainScores day=$dayKey -> " +
            result.joinToString { "${it.id}=${it.score}" })

        return result
    }
}

typealias MomentumSourceStub = TodayFacadeMomentumSource
typealias ReadinessSourceStub = TodayFacadeMomentumSource
