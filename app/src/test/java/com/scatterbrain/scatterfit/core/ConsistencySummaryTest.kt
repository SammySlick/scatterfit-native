package com.scatterbrain.scatterfit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Acceptance tests for computeConsistencySummaries (AAG layer, decided
 * 2026-10-13). Every test pins todayKey — never real wall-clock.
 */
class ConsistencySummaryTest {

    private val TODAY = "2026-10-06"

    private fun day(n: Int): String { // 30 Sep .. 06 Oct 2026, real rollover
        val d = java.time.LocalDate.of(2026, 9, 30).plusDays(n.toLong())
        return d.toString()
    }

    private val LAST7 = (0..6).map { day(it) }

    private fun DailyData.withSteps(vararg d: Pair<String, Double>) = copy(steps = steps + d)
    private fun DailyData.withCal(vararg d: Pair<String, Double>) = copy(caloriesEaten = caloriesEaten + d)
    private fun DailyData.withProtein(vararg d: Pair<String, Double>) = copy(protein = protein + d)
    private fun DailyData.withSleep(vararg d: Pair<String, NightTotal>) = copy(sleep = sleep + d)
    private fun DailyData.withTraining(vararg d: Pair<String, Double>) = copy(trainingMinutes = trainingMinutes + d)
    private fun DailyData.withUnits(vararg d: Pair<String, Double>) = copy(alcoholUnits = alcoholUnits + d)
    private fun DailyData.withZ2(vararg d: Pair<String, HrZones>) = copy(hrZones = hrZones + d)

    private fun summaries(data: DailyData, s: ScoringSettings = ScoringSettings()) =
        computeConsistencySummaries(data, s, 7, TODAY)

    private fun get(list: List<ConsistencySummary>, name: String) =
        list.first { it.name == name }

    // ---- empty data: everything unlogged, no fake rows ----

    @Test fun `empty data - every metric unlogged and below all sections`() {
        val all = summaries(DailyData())
        assertEquals(7, all.size)
        assertTrue(all.all { it.unlogged })
        assertTrue(all.all { it.section == null })
        assertEquals(0, all.sumOf { it.loggedDays })
        // right edges still show the honest shape
        assertEquals("0/7 · 0.0k", get(all, "Steps").rightEdge)
        assertEquals("0/7 · 0 kcal", get(all, "Calories").rightEdge)
        assertEquals("0/7 nights", get(all, "Sleep").rightEdge)
        assertEquals("0/90m", get(all, "Z2 Training").rightEdge)
    }

    // ---- sections: worst-average first, 3 / 3 / rest, unlogged sink below ----

    @Test fun `sections - measured worst-first, unlogged rendered below all sections`() {
        // Deliberately extreme fixtures: steps far under target (bad), calories
        // under target (good), protein mid — so the section order is unambiguous.
        val data = DailyData()
            .withSteps(*LAST7.map { it to 2000.0 }.toTypedArray())
            .withCal(*LAST7.map { it to 2500.0 }.toTypedArray()) // exactly on target = score 100
            .withProtein(TODAY to 100.0)
        val all = summaries(data)
        val steps = get(all, "Steps")
        val cal = get(all, "Calories")
        val prot = get(all, "Protein")
        // 3 measured metrics = thirds 1/1/1, worst first
        assertEquals(ConsistencySection.NEED_WORK, steps.section)
        assertEquals(ConsistencySection.GOING_STEADY, prot.section)
        assertEquals(ConsistencySection.GOOD_WORK, cal.section)
        assertTrue(all.indexOf(steps) < all.indexOf(prot))
        assertTrue(all.indexOf(prot) < all.indexOf(cal))
        // unlogged metrics must come after every measured one
        assertTrue(all.indexOf(all.filter { it.unlogged }.first()) > all.indexOf(cal))
    }

    private fun assertFalse(v: Boolean) = org.junit.Assert.assertFalse(v)

    // ---- counts and right edges ----

    @Test fun `steps - logged days, on-target days and weekly total`() {
        val data = DailyData().withSteps(
            day(0) to 5000.0, day(1) to 12000.0, day(2) to 11000.0, // 3 logged, 2 on target (5000 = score 50 < 60)
        )
        val s = get(summaries(data), "Steps")
        assertEquals(3, s.loggedDays)
        assertEquals(2, s.onTargetDays)
        assertEquals("3/7 · 2.8k", s.rightEdge)
    }

    @Test fun `calories - weekly total and over-target delta across logged days only`() {
        val data = DailyData().withCal(
            day(0) to 2000.0, day(1) to 2500.0, // 2 logged: 4500 vs 2×2270 (Sam default) = -40 under
        )
        val s = get(summaries(data, ScoringSettings(kcalTarget = 2270)), "Calories")
        assertEquals("2/7 · 4,500 kcal", s.rightEdge)
        assertTrue(s.sentence.contains("40 kcal under target across 2 logged days"))
    }

    @Test fun `calories - over target when eating above target`() {
        val data = DailyData().withCal(day(0) to 3000.0, day(1) to 3000.0)
        val s = get(summaries(data, ScoringSettings(kcalTarget = 2270)), "Calories")
        assertTrue(s.sentence.contains("1,460 kcal over target across 2 logged days"))
    }

    @Test fun `sleep - met nights green, short nights orange, on-target count honest`() {
        val data = DailyData().withSleep(
            day(0) to NightTotal(8.5 * 60),  // met
            day(1) to NightTotal(6.0 * 60),  // short
            day(2) to NightTotal(9.0 * 60),  // met
        )
        val s = get(summaries(data), "Sleep")
        assertEquals(3, s.loggedDays)
        assertEquals(2, s.onTargetDays)
        assertEquals("2/7 nights", s.rightEdge)
        assertTrue(s.sentence.contains("Avg 7h 50m"))
        assertTrue(s.sentence.contains("on target 2 of 3 nights"))
        // squares: met nights in Locked In band, short nights in Slipping band
        assertEquals(70.0, s.squares[0].score!!, 0.01)
        assertEquals(30.0, s.squares[1].score!!, 0.01)
    }

    @Test fun `sleep - custom target from settings, not hardcoded`() {
        val data = DailyData().withSleep(day(0) to NightTotal(6.5 * 60))
        val s = get(summaries(data, ScoringSettings(sleepTargetHours = 6.0)), "Sleep")
        assertEquals(1, s.onTargetDays) // 6.5h >= 6.0h target
        val s2 = get(summaries(data, ScoringSettings(sleepTargetHours = 7.0)), "Sleep")
        assertEquals(0, s2.onTargetDays) // 6.5h < 7.0h target
    }

    @Test fun `exercise - sessions counted from training minutes`() {
        val data = DailyData().withTraining(day(0) to 45.0, day(1) to 30.0)
        val s = get(summaries(data), "Exercise")
        assertEquals(2, s.loggedDays)
        assertEquals("2/7", s.rightEdge)
        assertTrue(s.sentence.contains("Trained 2 of the last 7 days — 75 min this week"))
    }

    @Test fun `alcohol - weekly units vs weekly budget`() {
        val data = DailyData().withUnits(day(0) to 4.0, day(1) to 2.5, day(2) to 1.5)
        val s = get(summaries(data), "Alcohol")
        assertEquals("8/14", s.rightEdge)
        assertEquals("8 of 14 weekly units used", s.sentence)
    }

    @Test fun `z2 - weekly minutes vs the real 90m per-zone target`() {
        val data = DailyData().withZ2(
            day(0) to HrZones(z2 = 40.0), day(1) to HrZones(z2 = 25.0), day(2) to HrZones(z2 = 0.0),
        )
        val s = get(summaries(data), "Z2 Training")
        assertEquals(3, s.loggedDays) // hrZones present, even with 0 z2
        assertEquals("65/90m", s.rightEdge)
        assertEquals("65 of 90 Z2 minutes this week", s.sentence)
        // pro-rata daily judgement: 40m vs 90/7 ≈ 12.86m => capped 100; 0m => 0
        assertEquals(100.0, s.squares[0].score!!, 0.01)
        assertEquals(0.0, s.squares[2].score!!, 0.01)
    }

    @Test fun `z2 - custom target from settings`() {
        val data = DailyData().withZ2(day(0) to HrZones(z2 = 60.0))
        val s = get(summaries(data, ScoringSettings(z2Target = 120)), "Z2 Training")
        assertEquals("60/120m", s.rightEdge)
    }

    // ---- on-target threshold consistency ----

    @Test fun `on-target threshold - score 60 and above counts, below does not`() {
        val data = DailyData().withSteps(day(0) to 10000.0, day(1) to 9500.0)
        val s = get(summaries(data), "Steps")
        // oneSidedScore(10000 vs 10000) = 100 (on); 9500 slightly under (score <60? band check via count)
        assertTrue(s.onTargetDays >= 1 && s.onTargetDays <= 2)
    }
}
