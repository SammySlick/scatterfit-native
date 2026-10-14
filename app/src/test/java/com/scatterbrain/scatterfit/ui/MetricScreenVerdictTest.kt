package com.scatterbrain.scatterfit.ui

import androidx.compose.ui.graphics.Color
import com.scatterbrain.scatterfit.ui.theme.StateLocked
import com.scatterbrain.scatterfit.ui.theme.StateSlip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricScreenVerdictTest {

    @Test
    fun `on target is green`() {
        assertEquals(StateLocked, verdictTone("On target"))
    }

    @Test
    fun `off target verdicts are orange never red`() {
        assertEquals(StateSlip, verdictTone("Below target"))
        assertEquals(StateSlip, verdictTone("Above target"))
    }

    @Test
    fun `tone is always a state colour never invented`() {
        val tones = listOf("On target", "Below target", "Above target", "anything else").map { verdictTone(it) }
        assertTrue(tones.all { it == StateLocked || it == StateSlip })
    }
}
