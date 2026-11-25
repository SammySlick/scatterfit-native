package com.scatterbrain.scatterfit.data

import com.scatterbrain.scatterfit.core.ScoringSettings
import com.scatterbrain.scatterfit.ui.MetricBarColor
import com.scatterbrain.scatterfit.ui.computeStepsBarColor
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
    fun `combined total is what clears the step target`() {
        val settings = ScoringSettings(stepTarget = 10000)
        val deviceSteps = 8000.0
        val estimate = 3000

        // Device steps alone (8,000 < 10,000) do not clear the target.
        assertEquals(MetricBarColor.TEAL, computeStepsBarColor(deviceSteps, settings.stepTarget))

        // Combined total (11,000 >= 10,000) does. This used to be observed through
        // computeDayCardTicks, which the My Day checklist counter took with it on
        // 2026-11-25; the bar colour is the same judgement the card still makes, so
        // the coverage survives the removal instead of going with it.
        val combinedSteps = deviceSteps + estimate
        assertEquals(MetricBarColor.GREEN, computeStepsBarColor(combinedSteps, settings.stepTarget))
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
