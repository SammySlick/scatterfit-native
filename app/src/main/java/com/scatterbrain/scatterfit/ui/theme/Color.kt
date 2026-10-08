package com.scatterbrain.scatterfit.ui.theme

import androidx.compose.ui.graphics.Color

// Judgement Palette (colour = judgement)
val JudgementGood = Color(0xFF4FD57F)        // good (on track)
val JudgementWarn = Color(0xFFF0BB3B)        // warn (watch)
val JudgementBad = Color(0xFFF14D4C)         // bad (off track)
val JudgementDestructive = Color(0xFFE64343) // destructive
val AccentHot = Color(0xFFF86111)            // hot / activity

// Neutrals
val AppBackground = Color(0xFF0A0A0A)
val AppCardSurface = Color(0xFF0E0E0E)
val SecondaryFill = Color(0xFF202225)
val MutedFill = Color(0xFF1C1D1F)
val AccentFill = Color(0xFF232426)

val TextForeground = Color(0xFFF4F4F4)
val TextMuted = Color(0xFF9499A0)
val TextFaint = Color(0xFF979FAB)
val TextFaintDark = Color(0xFF79818D)

val BorderDefault = Color.White.copy(alpha = 0.10f)
val BorderInput = Color.White.copy(alpha = 0.14f)
val RingFocus = Color(0xFFA2E126).copy(alpha = 0.60f)

// Brand & Chart Colours
val BrandPrimaryLime = Color(0xFFA3E635)    // THE brand colour: primary buttons, active state, momentum score
val OnPrimaryText = Color(0xFF0A0A0A)
val ChartBlue = Color(0xFF43B4D4)
val ChartAmber = Color(0xFFF0BB3B)
val ChartRed = Color(0xFFF14D4C)

// Legacy / Compatibility Aliases
val DarkCardSurface = AppCardSurface
val TealHighlight = BrandPrimaryLime
val DarkBackground = AppBackground
val DarkBorderOutline = BorderDefault
val StatusOnTarget = JudgementGood
val StatusNearTarget = JudgementWarn
val StatusOffTarget = JudgementBad
