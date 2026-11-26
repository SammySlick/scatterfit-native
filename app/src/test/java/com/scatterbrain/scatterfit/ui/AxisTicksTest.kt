package com.scatterbrain.scatterfit.ui

import com.scatterbrain.scatterfit.core.MetricId
import com.scatterbrain.scatterfit.core.ScoringSettings
import com.scatterbrain.scatterfit.core.MetricRegistry
import com.scatterbrain.scatterfit.ui.theme.DomainBody
import com.scatterbrain.scatterfit.ui.theme.DomainDrink
import com.scatterbrain.scatterfit.ui.theme.DomainFuel
import com.scatterbrain.scatterfit.ui.theme.DomainMove
import com.scatterbrain.scatterfit.ui.theme.DomainSleep
import com.scatterbrain.scatterfit.ui.theme.DomainTrain
import com.scatterbrain.scatterfit.ui.theme.JudgementBad
import com.scatterbrain.scatterfit.ui.theme.JudgementDestructive
import com.scatterbrain.scatterfit.ui.theme.JudgementGood
import com.scatterbrain.scatterfit.ui.theme.JudgementWarn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sam, 2026-11-26: "Numbers on the X and Y of the graphs."
 *
 * The risk in axis maths is silent: a tick list that stops BELOW the data still
 * draws a tidy chart, it just clips the tallest bar off the top of the plot. So
 * the contract these pin is bracketing — first tick at or below the minimum,
 * last tick at or above the maximum.
 */
class AxisTicksTest {

    @Test
    fun `ticks bracket the data so nothing is clipped`() {
        val cases = listOf(
            0.0 to 1934.0,      // calories, bars from zero
            0.0 to 8420.0,      // steps
            83.19 to 85.6,      // weight, line chart, tight range
            4.0 to 300.0,       // a spiky month
            0.0 to 0.4,         // body fat delta
        )
        cases.forEach { (min, max) ->
            val ticks = niceAxisTicks(min, max)
            assertTrue("no ticks for $min..$max", ticks.isNotEmpty())
            assertTrue("first tick ${ticks.first()} above min $min", ticks.first() <= min + 1e-9)
            assertTrue("last tick ${ticks.last()} below max $max", ticks.last() >= max - 1e-9)
        }
    }

    @Test
    fun `ticks sit on one even step`() {
        val ticks = niceAxisTicks(0.0, 1934.0)
        val step = ticks[1] - ticks[0]
        ticks.zipWithNext().forEach { (a, b) ->
            assertEquals("uneven spacing between $a and $b", step, b - a, 1e-9)
        }
    }

    @Test
    fun `a flat series returns one tick instead of dividing by zero`() {
        // Every weigh-in identical — no range exists, so inventing a spread
        // would draw a chart that lies about the variance.
        assertEquals(listOf(85.0), niceAxisTicks(85.0, 85.0))
    }

    @Test
    fun `degenerate input does not throw`() {
        assertTrue(niceAxisTicks(Double.NaN, 10.0).isEmpty())
        assertTrue(niceAxisTicks(0.0, 10.0, count = 1).isEmpty())
    }

    @Test
    fun `numbers stay short enough for a 34dp gutter`() {
        assertEquals("10k", shortAxisNumber(10_000.0))
        assertEquals("1.5k", shortAxisNumber(1500.0))
        assertEquals("1.2m", shortAxisNumber(1_200_000.0))
        assertEquals("0", shortAxisNumber(0.0))
        assertEquals("90", shortAxisNumber(90.0))
        assertEquals("2.5", shortAxisNumber(2.5))
    }

    @Test
    fun `x labels always include the first and last day`() {
        val labels = axisLabelIndexes(30)
        assertEquals(4, labels.size)
        assertEquals(0, labels.first())
        assertEquals(29, labels.last())
        assertEquals("labels must be strictly increasing", labels.sorted().distinct(), labels)
    }

    @Test
    fun `a 7-day window still gets four labels, not seven`() {
        // Seven weekday labels at 10sp collide into a grey bar on a 360dp plot.
        // Even the shortest window keeps the cap, which is why the cap lives in
        // this function rather than at the call sites.
        assertEquals(4, axisLabelIndexes(7).size)
        assertEquals(listOf(0, 1, 2), axisLabelIndexes(3))
        assertTrue(axisLabelIndexes(0).isEmpty())
        assertEquals(listOf(0), axisLabelIndexes(1))
    }

    @Test
    fun `weekly targets are divided onto a daily axis`() {
        val settings = ScoringSettings()
        // 14 units a week is 2 a day. Drawn undivided, every day would look
        // compliant against a line seven times too high.
        assertEquals(2.0, chartTargetFor(MetricId.ALCOHOL_UNITS, settings)!!, 1e-9)
        val zonePerDay = (settings.z2Target + settings.z3Target + settings.z4Target + settings.z5Target) / 7.0
        assertEquals(zonePerDay, chartTargetFor(MetricId.TRAINING_MINUTES, settings)!!, 1e-9)
    }

    @Test
    fun `metrics with no target get no target line`() {
        assertNull(chartTargetFor(MetricId.RESTING_HR, ScoringSettings()))
        assertNull(chartTargetFor(MetricId.CALORIES_BURNED, ScoringSettings()))
    }

    @Test
    fun `every metric keeps a domain hue and none borrows a judgement colour`() {
        val judgement = setOf(JudgementGood, JudgementWarn, JudgementBad, JudgementDestructive)
        val domain = setOf(DomainFuel, DomainMove, DomainSleep, DomainBody, DomainTrain, DomainDrink)
        MetricRegistry.ALL.forEach { def ->
            val colour = metricDomainColor(def.id)
            assertTrue("${def.id} uses a judgement colour", colour !in judgement)
            assertTrue("${def.id} is outside the domain palette", colour in domain)
        }
    }

    @Test
    fun `sleep reads in hours and minutes, not decimal hours`() {
        val sleep = MetricRegistry.byId(MetricId.SLEEP)
        assertEquals("7h 30m", formatMetricValue(sleep, 7.5))
        assertEquals("\u2014", formatMetricValue(sleep, null))
    }
}
