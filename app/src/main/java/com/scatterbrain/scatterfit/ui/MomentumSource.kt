package com.scatterbrain.scatterfit.ui

import com.scatterbrain.scatterfit.core.GoalId
import com.scatterbrain.scatterfit.core.GoalPreset
import com.scatterbrain.scatterfit.core.ScoringSettings
import com.scatterbrain.scatterfit.core.dayBefore
import com.scatterbrain.scatterfit.core.lastNDayKeys
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.data.AssembledDaily
import com.scatterbrain.scatterfit.data.CardInputs
import com.scatterbrain.scatterfit.data.Daily
import com.scatterbrain.scatterfit.data.GoalInsights
import com.scatterbrain.scatterfit.data.HealthRecord
import com.scatterbrain.scatterfit.data.LocalRecordConvert
import com.scatterbrain.scatterfit.data.RecordMethod
import com.scatterbrain.scatterfit.data.TodayFacade
import com.scatterbrain.scatterfit.data.Zones
import com.scatterbrain.scatterfit.data.makeDemoRecords
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
    val projectionText: String,
    val isOnTrack: Boolean
)

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
}

class TodayFacadeMomentumSource(
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val settings: ScoringSettings = ScoringSettings(activeGoal = GoalPreset.FAT_LOSS, kcalTarget = 2270),
    /** Real synced records (SyncHub). Null or empty per method -> demo month. */
    private val hcRecords: Map<RecordMethod, List<HealthRecord>>? = null,
) : MomentumSource {

    private val now = Instant.now()
    private val demoMonth = makeDemoRecords(now, zone)
    private val daily: AssembledDaily = run {
        val records = HashMap<RecordMethod, List<HealthRecord>>()
        if (hcRecords != null && hcRecords.isNotEmpty()) {
            for ((m, list) in hcRecords) if (list.isNotEmpty()) records[m] = list
        } else {
            for ((key, list) in demoMonth.records) {
                val m = RecordMethod.entries.firstOrNull { it.name == key } ?: continue
                records[m] = LocalRecordConvert.convertAll(m, list)
            }
        }
        Daily.buildDailyDataFromMaps(records, Zones.DEFAULT_ZONES, zone, nowMs = now.toEpochMilli())
    }

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
            LocalDate.parse(demoMonth.keys.firstOrNull() ?: today)
        } catch (_: Exception) {
            todayDate
        }
        val daysElapsed = ChronoUnit.DAYS.between(startDate, todayDate).coerceAtLeast(0)
        val elapsedText = "DAY ${daysElapsed + 1}"
        val targetDate = todayDate.plusDays(90)
        val targetFormatted = targetDate.format(DateTimeFormatter.ofPattern("dd.MM.yy"))

        return GoalProjectionInfo(
            goalName = "FAT LOSS",
            elapsedText = elapsedText,
            projectionText = "TARGET • $targetFormatted",
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
}

typealias MomentumSourceStub = TodayFacadeMomentumSource
typealias ReadinessSourceStub = TodayFacadeMomentumSource
