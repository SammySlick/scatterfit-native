package com.scatterbrain.scatterfit.data

import com.scatterbrain.scatterfit.core.GoalId
import com.scatterbrain.scatterfit.core.GoalPreset
import com.scatterbrain.scatterfit.core.ScoringSettings
import com.scatterbrain.scatterfit.core.DayKeys.weekKeysEndingAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Acceptance: the REAL web goal-insights.ts was run on the real demo month at
 *  the fixed clock (2026-10-07T18:00:00Z, UTC) and its card values captured into
 *  web_card.json. Every assertion below must match exactly. */
class GoalInsightsTest {

    private val nowMs = parseMillis("2026-10-07T18:00:00Z")!!
    private val today = "2026-10-07"

    private fun built(): AssembledDaily {
        val records = HashMap<RecordMethod, List<HealthRecord>>()
        for ((key, list) in makeDemoRecords(java.time.Instant.ofEpochMilli(nowMs), java.time.ZoneId.of("UTC")).records) {
            val m = RecordMethod.entries.first { it.name == key }
            records[m] = LocalRecordConvert.convertAll(m, list)
        }
        return Daily.buildDailyDataFromMaps(records, Zones.DEFAULT_ZONES, java.time.ZoneId.of("UTC"), nowMs = nowMs)
    }

    private val d = built()

    @Test
    fun `dayLogged - today logged, yesterday not`() {
        assertTrue(GoalInsights.dayLogged(d, today))
        assertFalse(GoalInsights.dayLogged(d, "2026-10-06"))
        // empty day before the month starts
        assertFalse(GoalInsights.dayLogged(d, "2026-09-07"))
    }

    @Test
    fun `streak matches web - 1 day, nothing broken`() {
        val s = GoalInsights.computeStreak(d, today)
        assertEquals(1, s.days)
        assertNull(s.brokenAt)
    }

    @Test
    fun `weekContributions - current week matches web exactly`() {
        val goals = com.scatterbrain.scatterfit.core.computeGoalSeries(
            TodayFacade.toCoreDaily(d), ScoringSettings(), 30, today)
        val weights = com.scatterbrain.scatterfit.core.effectiveWeights(GoalPreset.FAT_LOSS)
        val (items, total) = GoalInsights.weekContributions(goals, weights, "monday", GoalInsights.WeekRef.CURRENT, today)

        val expectedIds = listOf("bodyFat", "calories", "units", "protein", "exercise", "weight")
        assertEquals(expectedIds, items.map { it.id.name.lowercase() })
        val byId = items.associate { it.id.name.lowercase() to it }
        assertEquals(85.0, byId["bodyfat"]!!.score!!, 0.0001)
        assertEquals(29.310344827586206, byId["bodyfat"]!!.points!!, 0.0001)
        assertEquals(GoalInsights.ContributionStatus.POSITIVE, byId["bodyfat"]!!.status)
        assertEquals(34.40528634361233, byId["calories"]!!.score!!, 0.0001)
        assertEquals(9.491113474099954, byId["calories"]!!.points!!, 0.0001)
        assertEquals(GoalInsights.ContributionStatus.NEGATIVE, byId["calories"]!!.status)
        assertNull(byId["units"]!!.score)
        assertNull(byId["units"]!!.points)
        assertEquals(GoalInsights.ContributionStatus.WATCH, byId["units"]!!.status)
        assertEquals(58.4375, byId["protein"]!!.score!!, 0.0001)
        assertNull(byId["exercise"]!!.score)
        assertEquals(100.0, byId["weight"]!!.score!!, 0.0001)
        assertEquals(68.1333548534103, total!!, 0.0001)
    }

    @Test
    fun `weekContributions - previous week matches web`() {
        val goals = com.scatterbrain.scatterfit.core.computeGoalSeries(
            TodayFacade.toCoreDaily(d), ScoringSettings(), 30, today)
        val weights = com.scatterbrain.scatterfit.core.effectiveWeights(GoalPreset.FAT_LOSS)
        val (items, total) = GoalInsights.weekContributions(goals, weights, "monday", GoalInsights.WeekRef.PREVIOUS, today)
        val byId = items.associate { it.id.name.lowercase() to it }
        assertEquals(62.26190476190479, byId["bodyfat"]!!.score!!, 0.0001)
        assertEquals(48.49339207048458, byId["calories"]!!.score!!, 0.0001)
        assertNull(byId["units"]!!.score)
        assertEquals(82.5, byId["protein"]!!.score!!, 0.0001)
        assertNull(byId["exercise"]!!.score)
        assertEquals(55.7202380952378, byId["weight"]!!.score!!, 0.0001)
    }

    @Test
    fun `goalSentence matches web`() {
        val goals = com.scatterbrain.scatterfit.core.computeGoalSeries(
            TodayFacade.toCoreDaily(d), ScoringSettings(), 30, today)
        val weights = com.scatterbrain.scatterfit.core.effectiveWeights(GoalPreset.FAT_LOSS)
        val sentence = GoalInsights.goalSentence(goals, weights, GoalPreset.FAT_LOSS, 71.26250429282047, today)
        assertEquals("Fat loss · on track — calories is the one to watch today", sentence)
    }

    @Test
    fun `checkInDue - due until the week is dismissed`() {
        val wk = weekKeysEndingAt(today, "monday").first()
        assertTrue(GoalInsights.checkInDue(today, "monday", null))
        assertTrue(GoalInsights.checkInDue(today, "monday", "2026-09-28"))
        assertFalse(GoalInsights.checkInDue(today, "monday", wk))
        // dismiss last week's key — a new week re-arms
        assertFalse(GoalInsights.checkInDue(today, "monday", wk))
    }
}
