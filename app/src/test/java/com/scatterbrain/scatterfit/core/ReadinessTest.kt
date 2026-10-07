package com.scatterbrain.scatterfit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Acceptance = web vitest cases (readiness.test.ts, patterns.test.ts,
 * metrics.test.ts). Values carried one-to-one, not re-derived.
 */
class ReadinessTest {

    /* ---------- computeReadiness (Sam 2026-10-06 18:27) ---------- */

    @Test fun `both signals null - null readiness claim without data`() {
        assertNull(computeReadiness(null, null))
    }

    @Test fun `sleep 100 + RHR 100 - 100 prime`() {
        val r = computeReadiness(100.0, 100.0)!!
        assertEquals(100, r.score)
        assertEquals(ReadinessState.PRIME, r.state)
        assertTrue(r.recommendation.contains("primed"))
    }

    @Test fun `both signals 85 - 85 prime`() {
        assertEquals(85, computeReadiness(85.0, 85.0)!!.score)
        assertEquals(ReadinessState.PRIME, computeReadiness(85.0, 85.0)!!.state)
    }

    @Test fun `one strong signal 100 no other - capped at 70 prime`() {
        assertEquals(70, computeReadiness(100.0, null)!!.score)
        assertEquals(ReadinessState.PRIME, computeReadiness(100.0, null)!!.state)
    }

    @Test fun `one weak signal 30 - compromised`() {
        val r = computeReadiness(null, 30.0)!!
        assertEquals(30, r.score)
        assertEquals(ReadinessState.COMPROMISED, r.state)
    }

    @Test fun `great sleep but RHR crashing - 50 normal`() {
        val r = computeReadiness(0.0, 100.0)!!
        assertEquals(50, r.score)
        assertEquals(ReadinessState.NORMAL, r.state)
    }

    @Test fun `compromised threshold inclusive at 40`() {
        assertEquals(ReadinessState.COMPROMISED, computeReadiness(39.0, 39.0)!!.state)
        assertEquals(ReadinessState.COMPROMISED, computeReadiness(40.0, 40.0)!!.state)
        assertEquals(ReadinessState.NORMAL, computeReadiness(41.0, 41.0)!!.state)
    }

    @Test fun `partial credit at boundary`() {
        assertEquals(70, computeReadiness(69.0, 71.0)!!.score)
    }

    /* ---------- sleepDeviationScore (patterns.ts, Sam 2026-10-06 17:57) ---------- */

    @Test fun `sleep one-sided - long recovery nights score 100`() {
        assertEquals(100.0, sleepDeviationScore(540.0, 450.0), 1e-9)  // 9h vs 7.5h target
        assertEquals(100.0, sleepDeviationScore(450.0, 450.0), 1e-9)  // on target
    }

    @Test fun `sleep deficit scales against quarter-of-target`() {
        // 150min deficit = 100 - 150/15x10 = 0 (from readinessForData test fixture)
        assertEquals(0.0, sleepDeviationScore(300.0, 450.0), 1e-9)
        // half the quarter-budget gone -> 50
        assertEquals(50.0, sleepDeviationScore(375.0, 450.0), 1e-9)
    }

    @Test fun `sleep never below zero`() {
        assertEquals(0.0, sleepDeviationScore(0.0, 450.0), 1e-9)
    }

    /* ---------- readinessForData ---------- */

    private fun dataWith(vararg nights: Pair<String, Double>): DailyData =
        DailyData(sleep = nights.toMap().mapValues { NightTotal(it.value) })

    @Test fun `uses most recent night on or before today`() {
        val r = readinessForData(
            dataWith("2026-10-05" to 450.0),
            listOf(GoalDay("2026-10-05", 90.0), GoalDay("2026-10-06", null)),
            7.5, "2026-10-06",
        )!!
        assertEquals(95, r.score) // sleep 100 (on target) + rhr 90 -> 95
    }

    @Test fun `ignores sleep after the requested day`() {
        assertNull(readinessForData(dataWith("2026-10-07" to 450.0), emptyList(), 7.5, "2026-10-06"))
    }

    @Test fun `underslept night pulls readiness down`() {
        val r = readinessForData(
            dataWith("2026-10-05" to 300.0),
            listOf(GoalDay("2026-10-05", 100.0)),
            7.5, "2026-10-06",
        )!!
        assertEquals(50, r.score) // sleep 0 + rhr 100 -> 50
        assertEquals(ReadinessState.NORMAL, r.state)
    }

    @Test fun `skips empty totalMin nights`() {
        val r = readinessForData(
            dataWith("2026-10-04" to 0.0, "2026-10-05" to 450.0),
            emptyList(), 7.5, "2026-10-06",
        )!!
        assertEquals(70, r.score) // sleep 100, no RHR -> single-leg cap 70
    }

    /* ---------- derivations (patterns.test.ts) ---------- */

    private val zone = ZoneId.of("Europe/London")

    @Test fun `resting HR needs 500 samples and uses 5th percentile`() {
        val t0 = LocalDateTime.of(2026, 9, 10, 8, 0).atZone(zone).toInstant().toEpochMilli()
        val samples = (0 until 600).map { (t0 + it * 30000L) to (50.0 + it % 100) }
        val few = (0 until 499).map {
            val t = LocalDateTime.of(2026, 9, 11, 8, 0).atZone(zone).toInstant().toEpochMilli()
            (t + it * 30000L) to 60.0
        }
        val out = derivedRestingHr(samples + few, zone)
        assertEquals(54.95, out["2026-09-10"]!!, 0.5) // 55.0 after round-to-1dp; web: toBeCloseTo(54.95, 0)
        assertNull(out["2026-09-11"])
    }

    @Test fun `morning readings exclude 11-00 and later`() {
        val zone = ZoneId.of("UTC") // web test uses ISO strings in UTC
        fun t(h: Int, d: Int, m: Int = 0) =
            LocalDateTime.of(2026, 9, d, h, m).atZone(zone).toInstant().toEpochMilli()
        val out = morningReadings(listOf(t(7, 10) to 80.0, t(12, 10) to 82.0, t(11, 11) to 81.0), zone)
        assertEquals(mapOf("2026-09-10" to 80.0), out)
    }

    @Test fun `percentile interpolates`() {
        assertEquals(54.95, percentile((50..99 step 1).map { it.toDouble() }, 0.05)!!, 1e-9)
        assertEquals(1.0, percentile(listOf(1.0, 2.0), 0.5)!!, 1e-9)
    }

    @Test fun `spearman detects perfect positive and null on constant`() {
        val x = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        assertEquals(1.0, spearman(x, listOf(2.0, 4.0, 6.0, 8.0, 10.0))!!, 1e-9)
        assertEquals(-1.0, spearman(x, x.reversed())!!, 1e-9)
        assertNull(spearman(x, listOf(3.0, 3.0, 3.0, 3.0, 3.0)))
    }

    /* ---------- metric axes (metrics.test.ts) ---------- */

    @Test fun `axis anchors cumulative metrics at zero`() {
        val d = metricAxisDomain(listOf(4200.0, 6900.0), AxisPolicy.ZERO)
        assertEquals(0.0, d.first, 1e-9)
        assertEquals(7591.0, d.second, 1e-9) // engine: 6900*1.1 = 7590.000000000001 -> ceil = 7591 on both platforms
    }

    @Test fun `axis gives resting HR a guarded floor and headroom`() {
        val d = metricAxisDomain(listOf(58.0, 61.0, 63.0), AxisPolicy.RESTING_HR)
        assertEquals(50.0, d.first, 1e-9)
        assertEquals(68.0, d.second, 1e-9)
        assertEquals(44.0, metricAxisDomain(listOf(49.0, 53.0), AxisPolicy.RESTING_HR).first, 1e-9)
    }

    @Test fun `axis keeps weight tight while including target`() {
        val d = metricAxisDomain(listOf(80.2, 80.6), AxisPolicy.TIGHT, 80.0)
        assertEquals(79.8, d.first, 1e-9)
        assertEquals(80.8, d.second, 1e-9)
    }

    @Test fun `axis pads body fat on both sides`() {
        val d = metricAxisDomain(listOf(20.4, 20.8), AxisPolicy.SYMMETRIC)
        assertEquals(19.9, d.first, 1e-9)
        assertEquals(21.3, d.second, 1e-9)
    }

    @Test fun `niceZeroAxis picks a clean ladder`() {
        val (domain, ticks) = niceZeroAxis(listOf(12500.0, 8200.0))
        assertEquals(0.0, domain[0], 1e-9)
        // engine-generated: ceil=13500, ceil/4=3375 -> step=5000, top=15000
        assertEquals(15000.0, domain[1], 1e-9)
        assertEquals(listOf(0.0, 5000.0, 10000.0, 15000.0), ticks)
        // engine-generated smaller cases
        assertEquals(listOf(0.0, 2000.0, 4000.0, 6000.0, 8000.0), niceZeroAxis(listOf(6900.0, 4200.0)).second)
        assertEquals(listOf(0.0, 250.0, 500.0, 750.0, 1000.0), niceZeroAxis(listOf(850.0)).second)
    }
}
