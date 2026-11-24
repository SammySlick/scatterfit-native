package com.scatterbrain.scatterfit.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The confirm card used to render above the FOOD LOG header, so selecting a food for
 * Snacks put the card off-screen and the item appeared to vanish. These tests pin the
 * rule that keeps it inside the section the user actually tapped.
 */
class MealSectionTargetTest {

    @Test
    fun `chosen slot wins over a stale fallback`() {
        // The bug in its original form: mealTypeInput still said breakfast from an
        // earlier add, but the user tapped "+" on Snacks.
        assertEquals("snacks", confirmSectionFor("snacks", "breakfast"))
    }

    @Test
    fun `each section resolves to itself`() {
        MEAL_SECTIONS.forEach { slot ->
            assertEquals(slot, confirmSectionFor(slot, null))
        }
    }

    @Test
    fun `case and whitespace are tolerated`() {
        assertEquals("lunch", confirmSectionFor("  LUNCH  ", null))
        assertEquals("dinner", confirmSectionFor("Dinner", null))
    }

    @Test
    fun `falls back when nothing was chosen`() {
        // Barcode scan from the camera: no sheet open, so no chosen slot.
        assertEquals("lunch", confirmSectionFor(null, "lunch"))
    }

    @Test
    fun `unknown values never return null`() {
        // Losing the food entirely is the one unacceptable outcome.
        assertEquals("snacks", confirmSectionFor(null, null))
        assertEquals("snacks", confirmSectionFor("", ""))
        assertEquals("snacks", confirmSectionFor("brunch", "second-breakfast"))
    }

    @Test
    fun `an unusable chosen slot does not discard a usable fallback`() {
        assertEquals("dinner", confirmSectionFor("brunch", "dinner"))
    }
}
