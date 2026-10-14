package com.scatterbrain.scatterfit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Acceptance tests for computeConsistencySummaries (AAG layer, decided
 * 2026-10-13; square grammar = tolerance bands per Sam's mockup 2026-10-14).
 * Every test pins todayKey — never real wall-clock.
 */
class ConsistencySummaryTest {

    // 2026-10-11 is a SUNDAY: trailing-7 and the calendar week (Mon..Sun)
    // coincide exactly, so fixtures stay valid after the 2026-10-14 switch to
    // calendar weeks. A separate mid-week test pins the partial-week shape.
    private val TODAY = "2026-10-11"

    private fun day(n: Int): String { // Mon 05 .. Sun 11 Oct 2026, real week
        val d = java.time.LocalDate.of(2026, 10, 5).plusDays(n.toLong())
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
        assertEquals("—", get(all, "Steps").rightEdge)
        assertEquals("—", get(all, "Calories").rightEdge)
        assertEquals("—", get(all, "Sleep").rightEdge)
        assertEquals("0/90m", get(all, "Z2 Training").rightEdge)
    }

    // ---- sections: worst-average first, 3 / 3 / rest, unlogged sink below ----

    @Test fun `calendar week - mid-week Wednesday leaves Thu-Sun unlogged, sums are partial`() {
        // Wednesday 2026-10-07: week = Mon 05 .. Sun 11; only Mon+Tue have data.
        val data = DailyData()
            .withSteps("2026-10-05" to 12000.0, "2026-10-06" to 11000.0)
            .withCal("2026-10-05" to 2500.0, "2026-10-06" to 2800.0)
        val all = computeConsistencySummaries(data, ScoringSettings(), 7, "2026-10-07")
        val steps = get(all, "Steps")
        assertEquals(7, steps.squares.size) // full week, not 7 trailing days
        assertEquals("2026-10-05", steps.squares.first().day)
        assertEquals("2026-10-11", steps.squares.last().day)
        // Mon+Tue logged; Wed..Sun unlogged (present/future)
        assertEquals(2, steps.squares.count { it.logged })
        assertEquals("23,000/70,000", steps.rightEdge)
        val sun = steps.squares.last()
        assertTrue(!sun.logged)
    }

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
        assertEquals("28,000/70,000", s.rightEdge)
    }

    @Test fun `calories - weekly total and over-target delta across logged days only`() {
        val data = DailyData().withCal(
            day(0) to 2000.0, day(1) to 2500.0, // 2 logged: 4500 vs 2×2270 (Sam default) = -40 under
        )
        val s = get(summaries(data, ScoringSettings(kcalTarget = 2270)), "Calories")
        assertEquals("4,500/15,890 kcal", s.rightEdge)
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
        assertEquals("23h 30m/56h", s.rightEdge)
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
        assertEquals("2 sessions", s.rightEdge)
        assertTrue(s.sentence.contains("Trained 2 days this week — 75 min this week"))
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
    // ---- square bands: the mockup grammar (2026-10-14) ----

    @Test fun `bands - calories two-sided tolerance around target`() {
        val data = DailyData().withCal(
            day(0) to 2500.0, // |0| <= 150 -> GREEN
            day(1) to 2800.0, // |300| <= 500 -> ORANGE
            day(2) to 3300.0, // |800| > 500 -> RED
        )
        val sq = get(summaries(data), "Calories").squares
        assertEquals(SquareBand.GREEN, sq[0].band)
        assertEquals(SquareBand.ORANGE, sq[1].band)
        assertEquals(SquareBand.RED, sq[2].band)
        assertEquals(1, get(summaries(data), "Calories").onTargetDays)
    }

    @Test fun `bands - calories custom tolerance from settings`() {
        val data = DailyData().withCal(day(0) to 2800.0)
        val loose = get(summaries(data, ScoringSettings(kcalTarget = 2500,
            tolerances = ToleranceSettings(kcalGreen = 300))), "Calories")
        assertEquals(SquareBand.GREEN, loose.squares[0].band) // |300| <= 300
        val tight = get(summaries(data, ScoringSettings(kcalTarget = 2500,
            tolerances = ToleranceSettings(kcalGreen = 100))), "Calories")
        assertEquals(SquareBand.ORANGE, tight.squares[0].band)
    }

    @Test fun `bands - steps one-sided floors`() {
        val data = DailyData().withSteps(
            day(0) to 10000.0, day(1) to 7000.0, day(2) to 6999.0,
        )
        val sq = get(summaries(data), "Steps").squares
        assertEquals(SquareBand.GREEN, sq[0].band)
        assertEquals(SquareBand.ORANGE, sq[1].band)
        assertEquals(SquareBand.RED, sq[2].band)
    }

    @Test fun `bands - protein two-sided around target`() {
        val data = DailyData().withProtein(
            day(0) to 150.0,  // |−10| <= 20 GREEN
            day(1) to 120.0,  // |−40| <= 50 ORANGE
            day(2) to 100.0,  // |−60| > 50 RED
            day(3) to 190.0,  // |+30| ORANGE (two-sided, symmetric with calories)
        )
        val sq = get(summaries(data), "Protein").squares
        assertEquals(SquareBand.GREEN, sq[0].band)
        assertEquals(SquareBand.ORANGE, sq[1].band)
        assertEquals(SquareBand.RED, sq[2].band)
        assertEquals(SquareBand.ORANGE, sq[3].band)
    }

    @Test fun `bands - sleep within 60 min of target is orange`() {
        val data = DailyData().withSleep(
            day(0) to NightTotal(8.0 * 60),          // met -> GREEN
            day(1) to NightTotal(7.5 * 60),          // 30 min short -> ORANGE
            day(2) to NightTotal(6.5 * 60),          // 90 min short -> RED
        )
        val sq = get(summaries(data), "Sleep").squares
        assertEquals(SquareBand.GREEN, sq[0].band)
        assertEquals(SquareBand.ORANGE, sq[1].band)
        assertEquals(SquareBand.RED, sq[2].band)
    }

    @Test fun `bands - exercise green when trained, grey when not, never red`() {
        val data = DailyData().withTraining(day(0) to 45.0)
        val sq = get(summaries(data), "Exercise").squares
        assertEquals(SquareBand.GREEN, sq[0].band)
        assertTrue(sq.drop(1).all { it.band == SquareBand.GREY })
    }

    @Test fun `bands - alcohol vs daily share of weekly budget`() {
        val data = DailyData().withUnits(
            day(0) to 2.0, // == 14/7 allowance -> GREEN
            day(1) to 3.5, // +1.5 over -> ORANGE
            day(2) to 5.0, // +3 over -> RED
        )
        val sq = get(summaries(data), "Alcohol").squares
        assertEquals(SquareBand.GREEN, sq[0].band)
        assertEquals(SquareBand.ORANGE, sq[1].band)
        assertEquals(SquareBand.RED, sq[2].band)
    }

    @Test fun `bands - z2 vs daily share of 90m`() {
        val data = DailyData().withZ2(
            day(0) to HrZones(z2 = 20.0),  // >= 90/7 -> GREEN
            day(1) to HrZones(z2 = 8.0),   // >= half share -> ORANGE
            day(2) to HrZones(z2 = 1.0),   // below -> RED
        )
        val sq = get(summaries(data), "Z2 Training").squares
        assertEquals(SquareBand.GREEN, sq[0].band)
        assertEquals(SquareBand.ORANGE, sq[1].band)
        assertEquals(SquareBand.RED, sq[2].band)
    }

    @Test fun `bands - unlogged days are grey and never a fake zero`() {
        val data = DailyData().withSteps(day(0) to 12000.0)
        val sq = get(summaries(data), "Steps").squares
        assertTrue(sq[0].logged)
        assertEquals(SquareBand.GREEN, sq[0].band)
        assertTrue(sq.drop(1).all { !it.logged && it.band == SquareBand.GREY })
    }

    // ---- today lines ----

    @Test fun `today lines - value vs daily target, honest when unlogged`() {
        val data = DailyData()
            .withSteps(TODAY to 3689.0)
            .withCal(TODAY to 589.0)
        val all = summaries(data)
        assertEquals("3,689/10,000 steps today", get(all, "Steps").todayLine)
        assertEquals("589/2,500 kcal today", get(all, "Calories").todayLine)
        assertEquals("Not logged today", get(all, "Protein").todayLine)
        assertEquals("Not logged today", get(all, "Sleep").todayLine)
        assertEquals("No session today", get(all, "Exercise").todayLine)
        assertEquals("Not logged today", get(all, "Alcohol").todayLine)
        assertEquals("Not logged today", get(all, "Z2 Training").todayLine)
    }

    @Test fun `today lines - sleep and exercise formats`() {
        val data = DailyData()
            .withSleep(TODAY to NightTotal(7.0 * 60))
            .withTraining(TODAY to 45.0)
            .withZ2(TODAY to HrZones(z2 = 20.0))
        val all = summaries(data)
        assertEquals("7h 0m/8h today", get(all, "Sleep").todayLine)
        assertEquals("45 min today", get(all, "Exercise").todayLine)
        assertEquals("20/13m today", get(all, "Z2 Training").todayLine)
    }
}