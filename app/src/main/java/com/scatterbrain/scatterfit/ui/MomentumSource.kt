package com.scatterbrain.scatterfit.ui

import com.scatterbrain.scatterfit.core.GoalId
import com.scatterbrain.scatterfit.core.GoalPreset
import com.scatterbrain.scatterfit.core.ScoringSettings
import com.scatterbrain.scatterfit.core.dayBefore
import com.scatterbrain.scatterfit.core.dayKeyFromIso
import com.scatterbrain.scatterfit.core.goalLabel
import com.scatterbrain.scatterfit.core.lastNDayKeys
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.data.AssembledDaily
import com.scatterbrain.scatterfit.data.CardInputs
import com.scatterbrain.scatterfit.data.Daily
import com.scatterbrain.scatterfit.data.GoalInsights
import com.scatterbrain.scatterfit.data.GoalTargetsStore
import com.scatterbrain.scatterfit.data.HealthRecord
import com.scatterbrain.scatterfit.data.LocalRecordConvert
import com.scatterbrain.scatterfit.data.RecordMethod
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
    fun momentumForDay(dayKey: String): Double?
    fun scoreForDay(dayKey: String): Double? = momentumForDay(dayKey)
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
    private val settings: ScoringSettings = ScoringSettings(activeGoal = GoalPreset.FAT_LOSS, kcalTarget = 2270),
    private val zone: ZoneId = daily.zone,
) : MomentumSource {

    override val isUsingDemoData: Boolean = daily.steps.isEmpty() && daily.weight.isEmpty()

    private val cardCache = mutableMapOf<String, CardInputs>()

    private fun getCard(dayKey: String): CardInputs {
        return cardCache.getOrPut(dayKey) {
            TodayFacade.todayCard(daily, settings, dayKey)
        }
    }

    override fun momentumForDay(dayKey: String): Double? {
        val today = todayKey(zone)
        if (dayKey > today) return null
        val card = getCard(dayKey)
        val point = card.momentum.find { it.day == dayKey } ?: card.momentum.lastOrNull()
        return point?.daily ?: card.current
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
