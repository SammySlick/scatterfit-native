package com.scatterbrain.scatterfit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Acceptance = web src/lib/metricTargets.test.ts (vitest cases ported 1:1). */
class MetricTargetsTest {

    @Test
    fun `weight verdict is factual and names its frame and goal`() {
        val result = metricTargetContext(MetricId.WEIGHT, 80.4, ScoringSettings(weightTarget = 80.0), recent = true)
        assertEquals("Above target", result.verdict)
        // 2026-10-04 21:21: comparison must name its frame AND the goal
        assertEquals("this week avg 80.4 kg vs 80.0 kg target", result.comparison)
        assertFalse(result.verdict.contains("moving", ignoreCase = true))
    }

    @Test
    fun `training minutes use the Z3+ weekly pace`() {
        // 14 Z3+ min vs 90/week pace (z3 45 + z4 30 + z5 15)
        assertEquals("Below pace", metricTargetContext(MetricId.TRAINING_MINUTES, 2.0, ScoringSettings(), recent = true).verdict)
    }

    @Test
    fun `alcohol pace comparison`() {
        assertEquals("On pace", metricTargetContext(MetricId.ALCOHOL_UNITS, 1.0, ScoringSettings(), recent = true).verdict)
    }

    @Test
    fun `movement action only when recent`() {
        assertNull(metricTargetContext(MetricId.STEPS, 2000.0, ScoringSettings(), recent = false).action)
        assertTrue(metricTargetContext(MetricId.STEPS, 2000.0, ScoringSettings(), recent = true).action!!.contains("steps"))
    }

    @Test
    fun `null mean means not enough data`() {
        val result = metricTargetContext(MetricId.STEPS, null, ScoringSettings())
        assertEquals("Not enough data", result.verdict)
        assertNull(result.action)
    }

    @Test
    fun `steps verdict and gap copy`() {
        val result = metricTargetContext(MetricId.STEPS, 8000.0, ScoringSettings(stepTarget = 10000), recent = true)
        assertEquals("Below target", result.verdict)
        assertEquals("this week avg 8,000 vs 10,000 daily target", result.comparison)
        assertEquals("2,000 steps would close today’s gap.", result.action)
    }

    @Test
    fun `sleep on target band is half an hour`() {
        assertEquals("On target", metricTargetContext(MetricId.SLEEP, 7.6, ScoringSettings(sleepTargetHours = 8.0)).verdict)
        assertEquals("Below target", metricTargetContext(MetricId.SLEEP, 7.4, ScoringSettings(sleepTargetHours = 8.0)).verdict)
        assertEquals("Above target", metricTargetContext(MetricId.SLEEP, 8.6, ScoringSettings(sleepTargetHours = 8.0)).verdict)
    }

    @Test
    fun `on-target weight is within a fifth of a kilo`() {
        assertEquals("On target", metricTargetContext(MetricId.WEIGHT, 80.2, ScoringSettings(weightTarget = 80.0)).verdict)
        assertEquals("On target", metricTargetContext(MetricId.WEIGHT, 79.8, ScoringSettings(weightTarget = 80.0)).verdict)
    }
}
