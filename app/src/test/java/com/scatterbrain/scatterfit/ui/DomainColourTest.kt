package com.scatterbrain.scatterfit.ui

import androidx.compose.ui.graphics.Color
import com.scatterbrain.scatterfit.ui.theme.AppCardSurface
import com.scatterbrain.scatterfit.ui.theme.DomainBody
import com.scatterbrain.scatterfit.ui.theme.DomainDrink
import com.scatterbrain.scatterfit.ui.theme.DomainFuel
import com.scatterbrain.scatterfit.ui.theme.DomainMove
import com.scatterbrain.scatterfit.ui.theme.DomainSleep
import com.scatterbrain.scatterfit.ui.theme.DomainTrain
import com.scatterbrain.scatterfit.ui.theme.JudgementBad
import com.scatterbrain.scatterfit.ui.theme.JudgementGood
import com.scatterbrain.scatterfit.ui.theme.JudgementWarn
import com.scatterbrain.scatterfit.ui.theme.MacroCarbs
import com.scatterbrain.scatterfit.ui.theme.MacroFat
import com.scatterbrain.scatterfit.ui.theme.MacroProtein
import com.scatterbrain.scatterfit.ui.theme.domainSurface
import com.scatterbrain.scatterfit.ui.theme.mealSlotAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards on the palette rules in Color.kt. These are cheap and they catch the
 * class of mistake that produced the amber Fat pill: a decorative colour
 * quietly reusing a judgement colour, so a plain macronutrient read as a
 * warning.
 */
class DomainColourTest {

    private val judgement = mapOf(
        "good" to JudgementGood,
        "warn" to JudgementWarn,
        "bad" to JudgementBad,
    )

    private val domains = mapOf(
        "fuel" to DomainFuel,
        "move" to DomainMove,
        "sleep" to DomainSleep,
        "body" to DomainBody,
        "train" to DomainTrain,
        "drink" to DomainDrink,
    )

    @Test
    fun `no domain hue is a judgement colour`() {
        for ((domainName, domainColour) in domains) {
            for ((judgementName, judgementColour) in judgement) {
                assertNotEquals(
                    "domain '$domainName' reuses judgement '$judgementName'",
                    judgementColour.value.toLong(),
                    domainColour.value.toLong(),
                )
            }
        }
    }

    @Test
    fun `macro hues are distinct from each other`() {
        // They sit side by side in one row at 13% alpha, which is the only place
        // three hues have to be told apart at that strength.
        val macros = listOf(MacroProtein, MacroCarbs, MacroFat)
        assertEquals(3, macros.map { it.value.toLong() }.distinct().size)
    }

    @Test
    fun `no macro hue is a judgement colour`() {
        for (macro in listOf(MacroProtein, MacroCarbs, MacroFat)) {
            for ((judgementName, judgementColour) in judgement) {
                assertNotEquals(judgementName, judgementColour.value.toLong(), macro.value.toLong())
            }
        }
    }

    @Test
    fun `fat is not amber`() {
        // The specific regression: the Fat pill shipped in JudgementWarn.
        assertNotEquals(JudgementWarn.value.toLong(), MacroFat.value.toLong())
    }

    @Test
    fun `domain hues are all different so a screen of cards does not merge`() {
        assertEquals(domains.size, domains.values.map { it.value.toLong() }.distinct().size)
    }

    @Test
    fun `a tinted surface is not the untinted surface`() {
        for ((name, colour) in domains) {
            assertNotEquals(
                "tinting with '$name' changed nothing",
                AppCardSurface.value.toLong(),
                domainSurface(colour).value.toLong(),
            )
        }
    }

    @Test
    fun `stronger tint moves further from the base surface`() {
        val light = domainSurface(DomainSleep, 0.05f)
        val heavy = domainSurface(DomainSleep, 0.20f)
        assertNotEquals(light.value.toLong(), heavy.value.toLong())
    }

    @Test
    fun `each meal slot gets its own hue`() {
        val slots = listOf("breakfast", "lunch", "dinner", "snacks")
        assertEquals(4, slots.map { mealSlotAccent(it).value.toLong() }.distinct().size)
    }

    @Test
    fun `meal slot matching ignores case and padding`() {
        assertEquals(mealSlotAccent("Breakfast").value.toLong(), mealSlotAccent(" breakfast ").value.toLong())
        assertEquals(mealSlotAccent("snack").value.toLong(), mealSlotAccent("SNACKS").value.toLong())
    }

    @Test
    fun `an unknown slot still gets a usable colour`() {
        // Health Connect meals arrive with no slot. They must not be blank or
        // transparent - the card still has to be visible.
        assertTrue(mealSlotAccent("").alpha > 0f)
        assertEquals(DomainFuel.value.toLong(), mealSlotAccent("brunch").value.toLong())
    }

    @Test
    fun `a colour's alpha is opaque for every domain hue`() {
        for ((name, colour) in domains) {
            assertEquals("domain '$name' is translucent", 1f, colour.alpha, 0.001f)
        }
    }
}
