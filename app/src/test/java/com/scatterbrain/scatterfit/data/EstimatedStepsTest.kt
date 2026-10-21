package com.scatterbrain.scatterfit.data

import com.scatterbrain.scatterfit.core.ScoringSettings
import com.scatterbrain.scatterfit.ui.computeDayCardTicks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EstimatedStepsTest {

    @Test
    fun `estimate adds on top of device steps`() {
        val storage = EstimatedStepsStorage()
        val dayKey = "2026-10-21"
        val deviceSteps = 8390.0

        storage.saveEntry(
            EstimatedStepEntry(
                dayKey = dayKey,
                steps = 4000,
                note = "theatre day"
            )
        )

        val estimatedSteps = storage.getTotalEstimatedSteps(dayKey)
        val combinedTotal = deviceSteps + estimatedSteps

        assertEquals(4000, estimatedSteps)
        assertEquals(12390.0, combinedTotal, 0.001)
    }

    @Test
    fun `two estimates same day both count`() {
        val storage = EstimatedStepsStorage()
        val dayKey = "2026-10-21"

        storage.saveEntry(
            EstimatedStepEntry(
                dayKey = dayKey,
                steps = 2500,
                note = "morning theatre"
            )
        )
        storage.saveEntry(
            EstimatedStepEntry(
                dayKey = dayKey,
                steps = 1500,
                note = "afternoon rounds"
            )
        )

        val entries = storage.loadEntries(dayKey)
        assertEquals(2, entries.size)
        assertEquals(4000, storage.getTotalEstimatedSteps(dayKey))

        val allTotals = storage.getAllEstimatedTotals()
        assertEquals(4000, allTotals[dayKey])
    }

    @Test
    fun `tick triggers on combined total`() {
        val dayKey = "2026-10-21"
        val settings = ScoringSettings(stepTarget = 10000)
        val deviceSteps = 8000.0
        val estimate = 3000

        val zone = java.time.ZoneId.of("UTC")
        val bounds = ZoneBounds(130, 145, 160, 175)

        // Device steps alone (8,000 < 10,000) does not meet target
        val ticksBefore = computeDayCardTicks(
            daily = AssembledDaily(steps = mapOf(dayKey to deviceSteps), zoneBounds = bounds, zone = zone),
            settings = settings,
            todayKey = dayKey,
            isTrainingDay = false,
            totalSteps = deviceSteps
        )

        // Combined total (11,000 >= 10,000) triggers the step tick
        val combinedSteps = deviceSteps + estimate
        val ticksAfter = computeDayCardTicks(
            daily = AssembledDaily(steps = mapOf(dayKey to deviceSteps), zoneBounds = bounds, zone = zone),
            settings = settings,
            todayKey = dayKey,
            isTrainingDay = false,
            totalSteps = combinedSteps
        )

        assertEquals(ticksBefore.completedCount + 1, ticksAfter.completedCount)
    }

    @Test
    fun `retro entry keyed to the right local day`() {
        val storage = EstimatedStepsStorage()
        val retroDay = "2026-10-18"
        val today = "2026-10-21"

        storage.saveEntry(
            EstimatedStepEntry(
                dayKey = retroDay,
                steps = 5000,
                note = "Sunday long shift"
            )
        )

        assertEquals(5000, storage.getTotalEstimatedSteps(retroDay))
        assertEquals(0, storage.getTotalEstimatedSteps(today))

        val retroEntries = storage.loadEntries(retroDay)
        assertEquals(1, retroEntries.size)
        assertEquals("Sunday long shift", retroEntries.first().note)

        val todayEntries = storage.loadEntries(today)
        assertTrue(todayEntries.isEmpty())
    }
}
