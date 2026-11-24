package com.scatterbrain.scatterfit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Recents ordering, pinned.
 *
 * The list this replaces was `presets.filter { it.kcal > 0 }.take(5)` - five items in
 * preset order, presented as "Recents". Nothing counted and nothing aged. These tests
 * exist so the ordering can be explained from the screen: uses and age, nothing else.
 */
class FoodRecentsTest {

    private val day = 86_400_000L
    private val now = 1_700_000_000_000L

    private fun meal(
        name: String,
        daysAgo: Int,
        kcal: Double = 300.0,
        protein: Double? = 20.0,
    ) = LoggedMeal(
        name = name,
        kcal = kcal,
        protein = protein,
        timestamp = now - daysAgo * day,
        dayKey = "2026-11-24",
    )

    @Test
    fun `a food eaten often outranks one eaten once yesterday`() {
        val meals = listOf(
            meal("Chicken", daysAgo = 2),
            meal("Chicken", daysAgo = 5),
            meal("Chicken", daysAgo = 9),
            meal("Halloumi", daysAgo = 1),
        )
        val ranked = rankRecentFoods(meals, now)
        // Chicken: 3 uses / (1 + 2/7) = 2.33. Halloumi: 1 / (1 + 1/7) = 0.875.
        assertEquals("Chicken", ranked.first().name)
        assertEquals(3, ranked.first().uses)
    }

    @Test
    fun `equal counts are ordered by how recent they are`() {
        val meals = listOf(
            meal("Porridge", daysAgo = 6),
            meal("Porridge", daysAgo = 20),
            meal("Chorizo", daysAgo = 1),
            meal("Chorizo", daysAgo = 3),
        )
        val ranked = rankRecentFoods(meals, now)
        assertEquals(listOf("Chorizo", "Porridge"), ranked.map { it.name })
    }

    @Test
    fun `a single old entry is not recent and drops out`() {
        val meals = listOf(
            meal("Birthday cake", daysAgo = 45),
            meal("Chicken", daysAgo = 2),
        )
        val ranked = rankRecentFoods(meals, now)
        assertEquals(listOf("Chicken"), ranked.map { it.name })
    }

    @Test
    fun `one old entry is kept when it repeats, because repetition is a habit`() {
        val meals = listOf(
            meal("Chilli", daysAgo = 60),
            meal("Chilli", daysAgo = 40),
        )
        val ranked = rankRecentFoods(meals, now)
        assertEquals(listOf("Chilli"), ranked.map { it.name })
        assertEquals(2, ranked.first().uses)
    }

    @Test
    fun `case and spacing do not split one food into two entries`() {
        val meals = listOf(
            meal("Chorizo", daysAgo = 3),
            meal("  chorizo ", daysAgo = 4),
            meal("CHORIZO", daysAgo = 5),
        )
        val ranked = rankRecentFoods(meals, now)
        assertEquals(1, ranked.size)
        assertEquals(3, ranked.first().uses)
        // The most recent spelling is the one displayed.
        assertEquals("Chorizo", ranked.first().name)
    }

    @Test
    fun `the newest values win, so a corrected entry is not overwritten by an old one`() {
        val meals = listOf(
            meal("Yoghurt", daysAgo = 8, kcal = 90.0, protein = 5.0),
            meal("Yoghurt", daysAgo = 1, kcal = 140.0, protein = 18.0),
        )
        val ranked = rankRecentFoods(meals, now)
        assertEquals(140.0, ranked.first().kcal, 0.001)
        assertEquals(18.0, ranked.first().protein!!, 0.001)
    }

    @Test
    fun `the limit is respected and blank names are ignored`() {
        val meals = (1..12).map { meal("Food $it", daysAgo = it) } + meal("   ", daysAgo = 1)
        val ranked = rankRecentFoods(meals, now, limit = 4)
        assertEquals(4, ranked.size)
        assertTrue(ranked.none { it.name.isBlank() })
    }

    @Test
    fun `uses label appears only when a food was eaten more than once`() {
        assertNull(recentUsesLabel(1))
        assertEquals("3x", recentUsesLabel(3))
    }

    @Test
    fun `age label reads as a person would say it`() {
        assertEquals("today", recentAgeLabel(0))
        assertEquals("yesterday", recentAgeLabel(1))
        assertEquals("4d ago", recentAgeLabel(4))
        assertEquals("last week", recentAgeLabel(9))
        assertEquals("3w ago", recentAgeLabel(21))
        assertEquals("3mo ago", recentAgeLabel(90))
    }
}
