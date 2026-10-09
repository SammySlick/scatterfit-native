package com.scatterbrain.scatterfit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskChecklistTest {

    private val daily = AssembledDaily(
        steps = mapOf("2026-10-09" to 8420.0),
        caloriesEaten = mapOf("2026-10-09" to 345.0),
        weight = mapOf("2026-10-09" to 84.2),
        bodyFat = mapOf("2026-10-09" to 26.8),
    )

    @Test
    fun `weigh-in completes when a weight record exists for the day`() {
        val s = TaskChecklist.derive(daily, "2026-10-09", 1800)
        assertTrue(s.weighInCompleted)
        assertEquals("84.2kg • 26.8% body fat", s.weighInData)
    }

    @Test
    fun `weigh-in does not complete from another day's data`() {
        val s = TaskChecklist.derive(daily, "2026-10-08", 1800)
        assertFalse(s.weighInCompleted)
        assertNull(s.weighInData)
    }

    @Test
    fun `manual weigh-in tick completes without data`() {
        // Manual ticks are GONE by design (2026-10-09): a weigh-in from proper
        // scales is the truth, and manual entry will be a record write, not a
        // parallel state. No data + no record = not completed. Period.
        val s = TaskChecklist.derive(null, "2026-10-09", 1800)
        assertFalse(s.weighInCompleted)
        assertNull(s.weighInData)
    }

    @Test
    fun `food derives calories eaten against target`() {
        val s = TaskChecklist.derive(daily, "2026-10-09", 1800)
        assertTrue(s.foodLogCompleted)
        assertEquals("345 / 1,800 kcal", s.foodLogData)
    }

    @Test
    fun `steps are pure data - no data means null not a fake number`() {
        val empty = TaskChecklist.derive(AssembledDaily(), "2026-10-09", 1800)
        assertNull(empty.stepsToday)
        assertEquals(8420.0, TaskChecklist.derive(daily, "2026-10-09", 1800).stepsToday, 0.0)
    }
}
