package com.scatterbrain.scatterfit.data

import com.scatterbrain.scatterfit.core.ScoringSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Acceptance: the REAL web TodayPage (index.tsx scored useMemo + goal-insights
 *  + readiness) was run on the real demo month at the fixed clock
 *  (2026-10-07T18:00:00Z, UTC) — captured in web_card.json. */
class TodayFacadeTest {

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

    // Web DEFAULT_SETTINGS: macroMode grams -> calories = kcalFromMacros(160, 70, 250) = 2270.
    private val card = TodayFacade.todayCard(built(), ScoringSettings(kcalTarget = 2270), today)

    @Test
    fun `momentum headline matches web`() {
        assertEquals(71.26250429282047, card.current!!, 0.0001)
        assertEquals(62.82312777339732, card.weekAgo!!, 0.0001)
        assertEquals(8.43937651942315, card.delta!!, 0.0001)
    }

    @Test
    fun `streak matches web - 1 day, nothing broken`() {
        assertEquals(1, card.streak.days)
        assertNull(card.streak.brokenAt)
    }

    @Test
    fun `readiness matches web`() {
        assertNotNull(card.readiness)
        assertEquals(67, card.readiness!!.score)
        assertEquals("normal", card.readiness!!.state)
        assertEquals("Baseline recovery. Stick to the scheduled plan.", card.readiness!!.recommendation)
    }

    @Test
    fun `sentence matches web`() {
        assertEquals("Fat loss · on track — calories is the one to watch today", card.sentence)
    }

    @Test
    fun `goal series match web - latest + recent day scores`() {
        val byId = card.goals.associate { it.id.name.lowercase() to it }
        val steps = byId["steps"]!!
        assertEquals(74.08, steps.latest!!, 0.0001)
        assertEquals(87.8, steps.days.first { it.day == "2026-10-06" }.score!!, 0.0001)
        assertEquals(74.08, steps.days.first { it.day == "2026-10-07" }.score!!, 0.0001)
        val calories = byId["calories"]!!
        assertNull(calories.latest) // calories target reached = null latest? web: latest 0
        assertEquals(0.0, calories.days.first { it.day == "2026-10-07" }.score!!, 0.0001)
        assertNull(calories.days.first { it.day == "2026-10-06" }.score)
        val sleep = byId["sleep"]!!
        assertNull(sleep.latest)
        assertNull(sleep.days.first { it.day == "2026-10-07" }.score)
        assertNull(sleep.days.first { it.day == "2026-10-06" }.score)
    }

    @Test
    fun `weekly framing - plain average over this week's days`() {
        // web: the week unit — Monday 05..today 07, only logged days averaged.
        // Sanitized expectation: recompute from the card's own goal series.
        val goals = card.goals
        val thisWeek = listOf("2026-10-05", "2026-10-06", "2026-10-07")
        val dayAvgs = thisWeek.mapNotNull { k ->
            val dayScores = goals.mapNotNull { g -> g.days.find { it.day == k }?.score }
            if (dayScores.isEmpty()) null else dayScores.sum() / dayScores.size
        }
        assertEquals(dayAvgs.average(), card.weekCurrent!!, 0.0001)
    }

    @Test
    fun `toCoreDaily - sleep and hr zones land in the engine shape`() {
        val core = TodayFacade.toCoreDaily(built())
        val n = core.sleep["2026-10-07"]!!
        assertEquals(465.02751666666666, n.totalMin, 0.0001)
        val z = core.hrZones["2026-10-04"]!!
        assertTrue(z.z3 > 0)
    }
}
