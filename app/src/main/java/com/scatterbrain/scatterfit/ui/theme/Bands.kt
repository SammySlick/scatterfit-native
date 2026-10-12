package com.scatterbrain.scatterfit.ui.theme

import androidx.compose.ui.graphics.Color

data class Band(val label: String, val copy: String, val color: Color)

fun momentumBand(score: Double): Band = when {
    score < 20.0 -> Band("Off Track", "Focus on just logging one thing today", StateBad)
    score < 40.0 -> Band("Slipping", "Identify the friction: is it diet or steps?", StateSlip)
    score < 60.0 -> Band("Finding Rhythm", "A solid baseline, keep pushing", StateRhythm)
    score < 80.0 -> Band("Locked In", "Hitting targets consistently", StateLocked)
    else -> Band("Unstoppable", "Multi-week peak adherence", StatePeak)
}

fun readinessBand(state: String): Band = when (state.uppercase()) {
    "COMPROMISED" -> Band("Compromised", "Focus on Z2 recovery or rest", StateBad)
    "PRIMED" -> Band("Primed", "Push for Z4+ targets", StateLocked)
    else -> Band("Normal", "Standard training day", StateRhythm)
}
