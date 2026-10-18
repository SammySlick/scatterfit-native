package com.scatterbrain.scatterfit.ui.theme

import androidx.compose.ui.graphics.Color

// Judgement Palette (colour = judgement)
val JudgementGood = Color(0xFF4FD57F)        // good (on track)
val JudgementWarn = Color(0xFFF0BB3B)        // warn (watch)
val JudgementBad = Color(0xFFF14D4C)         // bad (off track)
val JudgementDestructive = Color(0xFFE64343) // destructive
val AccentHot = Color(0xFFF86111)            // hot / activity

// Neutrals (matching Lovable approved export: #0A0D0F / #13181C / #1C2227)
val AppBackground = Color(0xFF0A0D0F)
val AppCardSurface = Color(0xFF13181C)
val SecondaryFill = Color(0xFF1C2227)
val MutedFill = Color(0xFF1C2227)
val AccentFill = Color(0xFF1C2227)

val TextForeground = Color(0xFFF1F5F9)
val TextMuted = Color(0xFF8E9BAE)
val TextFaint = Color(0xFF979FAB)
val TextFaintDark = Color(0xFF79818D)

val BorderDefault = Color.White.copy(alpha = 0.10f)
val BorderInput = Color.White.copy(alpha = 0.14f)
val Track = Color(0xFF263037)
val AccentTeal   = Color(0xFF15B6A4)
val RingFocus = AccentTeal.copy(alpha = 0.60f)

// Brand & Chart Colours
val BrandPrimaryLime = AccentTeal    // Retired: aliased to AccentTeal
val OnPrimaryText = Color(0xFF0A0A0A)
val ChartBlue = Color(0xFF43B4D4)
val ChartAmber = Color(0xFFF0BB3B)
val ChartRed = Color(0xFFF14D4C)

// Legacy / Compatibility Aliases
val DarkCardSurface = AppCardSurface
val AppRowSurface = MutedFill
val TealHighlight = AccentTeal
val DarkBackground = AppBackground
val DarkBorderOutline = BorderDefault
val StatusOnTarget = JudgementGood
val StatusNearTarget = JudgementWarn
val StatusOffTarget = JudgementBad

// Colour System v2 tokens
val StateBad     = Color(0xFFF05B54)
val StateSlip    = Color(0xFFF29D1E)
val StateRhythm  = Color(0xFFF0BB3B)
val StateLocked  = Color(0xFF44B65E)
val StatePeak    = Color(0xFF3FA9F5)
val ZoneAccent   = Color(0xFFF17084)
