package com.scatterbrain.scatterfit.ui

import com.scatterbrain.scatterfit.data.SettingsStorage
import com.scatterbrain.scatterfit.ui.theme.AccentTeal
import com.scatterbrain.scatterfit.ui.theme.StateBad
import com.scatterbrain.scatterfit.ui.theme.StateLocked
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import org.junit.Assert.assertEquals
import org.junit.Test

class MacroTargetsTest {

    @Test
    fun `formatMacroTarget returns dash when target is null or non-positive`() {
        assertEquals("—", formatMacroTarget(null))
        assertEquals("—", formatMacroTarget(0))
        assertEquals("—", formatMacroTarget(-10))
    }

    @Test
    fun `formatMacroTarget returns grams string when target is positive`() {
        assertEquals("200g", formatMacroTarget(200))
        assertEquals("70g", formatMacroTarget(70))
        assertEquals("150g", formatMacroTarget(150))
    }

    @Test
    fun `computeMacroBarColor maps zero or missing data to muted`() {
        assertEquals(TextMuted, computeMacroBarColor(current = 0, target = 200))
        assertEquals(TextMuted, computeMacroBarColor(current = 0, target = null))
        assertEquals(TextMuted, computeMacroBarColor(current = 50, target = null))
        assertEquals(TextMuted, computeMacroBarColor(current = 50, target = 0))
    }

    @Test
    fun `computeMacroBarColor maps within or near target to StateLocked green`() {
        // Target = 200g, 85% is 170g
        assertEquals(StateLocked, computeMacroBarColor(current = 200, target = 200))
        assertEquals(StateLocked, computeMacroBarColor(current = 190, target = 200))
        assertEquals(StateLocked, computeMacroBarColor(current = 175, target = 200))
    }

    @Test
    fun `computeMacroBarColor maps over target to StateBad red`() {
        assertEquals(StateBad, computeMacroBarColor(current = 201, target = 200))
        assertEquals(StateBad, computeMacroBarColor(current = 250, target = 200))
    }

    @Test
    fun `computeMacroBarColor maps in-progress below near-target to AccentTeal`() {
        assertEquals(AccentTeal, computeMacroBarColor(current = 100, target = 200))
        assertEquals(AccentTeal, computeMacroBarColor(current = 50, target = 200))
    }

    @Test
    fun `SettingsStorage defaults carb and fat targets to 0 when no goal is set`() {
        val storage = SettingsStorage()
        val settings = storage.loadSettings()
        assertEquals(0, settings.carbTargetG)
        assertEquals(0, settings.fatTargetG)
        assertEquals("—", formatMacroTarget(settings.carbTargetG.takeIf { it > 0 }))
        assertEquals("—", formatMacroTarget(settings.fatTargetG.takeIf { it > 0 }))
    }
}
