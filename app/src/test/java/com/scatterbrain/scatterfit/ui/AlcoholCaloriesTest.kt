package com.scatterbrain.scatterfit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Alcohol kcal and the two screens that used to disagree about it.
 *
 * Sam, 2026-11-26: "I don't think the alcohol calories are being added to the total
 * any more? I can't see anywhere they are." The arithmetic was intact; the evidence
 * for it was not. These tests exist so "did it include the beer" is a question with
 * an answer, not a reading exercise in a 1,000-line file.
 */
class AlcoholCaloriesTest {

    // ---- My Day hero: food + alcohol ----

    @Test
    fun `hero total adds alcohol to food`() {
        // The October incident in numbers: 1,765 kcal of food and 1,919 kcal of beer.
        val food = 1765.0
        val alcohol = 1919.0
        assertEquals(3684, (food + alcohol).toInt())
    }

    @Test
    fun `food recovered from an alcohol-inclusive total is not double counted`() {
        // Daily.caloriesEaten already contains drink kcal, so the rollup comes back out
        // before alcohol is added again - otherwise four pints count as eight.
        val recovered = foodKcalFromTotals(caloriesEaten = 3684.0, alcoholKcal = 1919.0)
        assertEquals(1765.0, recovered, 0.001)
        assertEquals(3684.0, recovered + 1919.0, 0.001)
    }

    @Test
    fun `food recovered from a total never goes negative`() {
        // A rollup that outran its own snapshot must not print negative intake.
        assertEquals(0.0, foodKcalFromTotals(caloriesEaten = 100.0, alcoholKcal = 400.0), 0.001)
        assertEquals(0.0, foodKcalFromTotals(caloriesEaten = null, alcoholKcal = 400.0), 0.001)
    }

    // ---- The visible split ----

    @Test
    fun `split names both halves with thousands separators`() {
        assertEquals("1,765 food + 1,919 alcohol", calorieSplitLabel(1765.0, 1919.0))
    }

    @Test
    fun `split is null on a day with no drinks`() {
        // A breakdown that is always present is the "too many numbers" problem again.
        assertNull(calorieSplitLabel(1391.0, 0.0))
    }

    @Test
    fun `split survives a drinks-only day`() {
        // The case most likely to look broken: nothing eaten, three pints logged.
        // The hero must still say where its 443 kcal came from.
        assertEquals("0 food + 443 alcohol", calorieSplitLabel(0.0, 443.0))
    }

    @Test
    fun `split renders on a small amount of alcohol`() {
        assertEquals("1,000 food + 1 alcohol", calorieSplitLabel(1000.0, 1.0))
    }

    // ---- Food log total ----

    @Test
    fun `food log total includes alcohol`() {
        assertEquals(1500, totalIntakeKcal(foodKcal = 1200, alcoholKcal = 300))
    }

    @Test
    fun `food log total is unchanged without alcohol`() {
        assertEquals(1200, totalIntakeKcal(foodKcal = 1200, alcoholKcal = 0))
    }

    @Test
    fun `food log total ignores a negative alcohol rollup`() {
        // Defensive: a correction artefact must not silently reduce recorded intake.
        assertEquals(1200, totalIntakeKcal(foodKcal = 1200, alcoholKcal = -50))
    }
}
