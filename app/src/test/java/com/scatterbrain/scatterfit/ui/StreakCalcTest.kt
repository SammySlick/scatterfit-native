package com.scatterbrain.scatterfit.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StreakCalcTest {

    private val today = "2026-10-14"
    private val yesterday = "2026-10-13"
    private val twoDaysAgo = "2026-10-12"
    private val threeDaysAgo = "2026-10-11"

    @Test
    fun `empty history returns 0`() {
        val streak = computeStreak(
            foodByDay = emptyMap(),
            drinksByDay = emptyMap(),
            weightsByDay = emptySet(),
            todayKey = today
        )
        assertEquals(0, streak)
    }

    @Test
    fun `unbroken run returns length`() {
        // Today has food, yesterday has drink, twoDaysAgo has weigh-in -> streak = 3
        val streak = computeStreak(
            foodByDay = mapOf(today to 2),
            drinksByDay = mapOf(yesterday to 1),
            weightsByDay = setOf(twoDaysAgo),
            todayKey = today
        )
        assertEquals(3, streak)
    }

    @Test
    fun `today-empty grace preserves streak from yesterday`() {
        // Today is empty, yesterday has food, twoDaysAgo has drink, threeDaysAgo has weigh-in -> streak = 3
        val streak = computeStreak(
            foodByDay = mapOf(yesterday to 1),
            drinksByDay = mapOf(twoDaysAgo to 1),
            weightsByDay = setOf(threeDaysAgo),
            todayKey = today
        )
        assertEquals(3, streak)
    }

    @Test
    fun `gap breaks the count`() {
        // Today has food, yesterday empty, twoDaysAgo has weigh-in -> streak = 1
        val streak = computeStreak(
            foodByDay = mapOf(today to 1),
            drinksByDay = emptyMap(),
            weightsByDay = setOf(twoDaysAgo),
            todayKey = today
        )
        assertEquals(1, streak)
    }

    @Test
    fun `today and yesterday empty returns 0 even if older days logged`() {
        // Today empty, yesterday empty, twoDaysAgo has food -> streak = 0
        val streak = computeStreak(
            foodByDay = mapOf(twoDaysAgo to 1),
            drinksByDay = emptyMap(),
            weightsByDay = emptySet(),
            todayKey = today
        )
        assertEquals(0, streak)
    }
}
