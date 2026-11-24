package com.scatterbrain.scatterfit.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Judgement Palette (colour = judgement)
//
// ACCESSIBILITY NOTE (2026-11-24): JudgementWarn was referenced 27 times but
// JudgementGood 16 — amber had become the app's default tint, so every screen
// opened in a state of mild caution. Amber is for genuine warnings. For "this
// is a number, it is fine" use TextMuted or NeutralAttend below.
val JudgementGood = Color(0xFF4FD57F)        // good (on track)
val JudgementWarn = Color(0xFFF0BB3B)        // warn (watch)
val JudgementBad = Color(0xFFF14D4C)         // bad (off track)
val JudgementDestructive = Color(0xFFE64343) // destructive
val AccentHot = Color(0xFFF86111)            // hot / activity

// Neutrals v3 — "lighter dark" (2026-11-24, Sam: "less terminal screen, more
// fitness app"). The v1 ramp started at #0A0D0F, which is 3% luminance: on an
// OLED phone that reads as a black hole with text on it, and every card had to
// be outlined to be visible at all. The floor is lifted and the steps between
// surfaces are wider, so a card is legible by being LIGHTER than the page
// instead of by its border. Borders then drop to a hairline.
//
//   #16191E  page        (was #0A0D0F)
//   #20242B  card        (was #13181C)
//   #272C34  fill        (was #1C2227)
//
// Kept in the same names so every screen inherits this without being touched.
val AppBackground = Color(0xFF16191E)
val AppCardSurface = Color(0xFF20242B)
val SecondaryFill = Color(0xFF272C34)
val MutedFill = Color(0xFF272C34)

// Muted lifted from #8E9BAE: most of this app's copy is 11-12sp, and 6.4:1 at
// 11sp still squints. This is ~7.4:1 on the card colour.
val TextForeground = Color(0xFFF4F6F9)
val TextMuted = Color(0xFFA3AEBC)
val TextFaint = Color(0xFFA9B3C0)

// Hairlines, not outlines. A card should be found by its contrast with the
// page, not by a drawn rectangle around it.
val BorderDefault = Color.White.copy(alpha = 0.07f)
val BorderInput = Color.White.copy(alpha = 0.13f)
val Track = Color(0xFF333A44)
val AccentTeal   = Color(0xFF15B6A4)

// Brand & Chart Colours
val BrandPrimaryLime = AccentTeal    // Retired: aliased to AccentTeal
val OnPrimaryText = Color(0xFF0A0A0A)
val ChartBlue = Color(0xFF43B4D4)

// Legacy / Compatibility Aliases
val DarkCardSurface = AppCardSurface
val TealHighlight = AccentTeal

// Colour System v2 tokens
val StateBad     = Color(0xFFF05B54)
val StateSlip    = Color(0xFFF29D1E)
val StateRhythm  = Color(0xFFF0BB3B)
val StateLocked  = Color(0xFF44B65E)
val StatePeak    = Color(0xFF3FA9F5)

// --- v3: the colour that is allowed to mean nothing -------------------------
// Everyday state. Not on track, not off track: just today's reading. Use this
// wherever a row used to reach for JudgementWarn simply to have a colour.
val NeutralAttend = Color(0xFFB9C3D0)


// --- v4: domain colour ------------------------------------------------------
// Sam, 2026-11-24: "we need to introduce more colour into the app. It's so
// monochromatic at the moment that it makes it quite muddy. It's hard to
// differentiate cards and lists."
//
// THE RULE THAT KEEPS THIS FROM BECOMING NOISE: a domain colour says WHAT a
// thing is. It never says how well you are doing. Judgement colours above
// (good/warn/bad) are still the only ones allowed to say that, and they are
// only ever used on text and badges - never on a card edge, a chip or a
// background. If a card needs to be findable, it gets a domain hue. If a
// number needs to be judged, it gets a judgement colour. Nothing is both.
//
// One hue per domain, used identically on every screen, so the same colour
// means the same thing on Today, in the food log and in a chart. Hues are
// spread around the wheel and held at similar saturation, so a screen of six
// cards reads as six things rather than as a rainbow.
val DomainFuel  = Color(0xFFF2A03D) // food, calories, carbs
val DomainMove  = Color(0xFF2EC4B6) // steps, distance, movement
val DomainSleep = Color(0xFF8B95F6) // sleep
val DomainBody  = Color(0xFFC08CF2) // weight, body fat, dietary fat
val DomainTrain = Color(0xFF4FA8E8) // lifting, workouts
val DomainDrink = Color(0xFFE07FA8) // drinks

// Macros. Three hues that stay distinguishable sitting side by side at 13%
// alpha, which is where they always are. Protein moved off teal (it was the
// same teal as the steps bar) and FAT MOVED OFF JudgementWarn - the pill was
// amber, so "fat" read as a warning about fat.
val MacroProtein = Color(0xFF5FC3E4)
val MacroCarbs   = DomainFuel
val MacroFat     = DomainBody

/**
 * A card surface carrying a domain hue at low strength.
 *
 * Tinting the surface, rather than only the label, is what makes a wall of
 * cards stop reading as one grey mass: the difference between two cards is
 * then both hue and lightness, instead of 4% luminance. 0.07 is deliberately
 * low - the card still has to be a quiet block, and anything above ~0.12
 * starts to look like a status colour.
 */
fun domainSurface(accent: Color, strength: Float = 0.07f): Color =
    lerp(AppCardSurface, accent, strength)

/**
 * The hue for one meal slot. Breakfast, Lunch, Dinner and Snacks are four lists
 * stacked by the same layout, so the hue is the only cue that tells them apart
 * while scrolling.
 *
 * Keyed on the slot name rather than on position, because a day with no
 * breakfast must not shift the other three colours. Unknown slots (a new meal
 * type, or a Health Connect meal with no slot) fall back to the fuel hue rather
 * than to something arbitrary.
 */
fun mealSlotAccent(slot: String): Color = when (slot.trim().lowercase()) {
    "breakfast" -> DomainFuel
    "lunch" -> DomainTrain
    "dinner" -> DomainBody
    "snacks", "snack" -> DomainDrink
    else -> DomainFuel
}
