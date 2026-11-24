package com.scatterbrain.scatterfit.ui

/** The four meal sections, in the fixed order they are rendered. */
val MEAL_SECTIONS: List<String> = listOf("breakfast", "lunch", "dinner", "snacks")

/**
 * Which meal section the confirm card should be rendered inside.
 *
 * Sam, 2026-11-24: "you select the food item from the search list, then it
 * 'disappears', when in reality it's jumped to the top of the page... the card with
 * that item jumps up to the top of the page instead of just staying at the top of the
 * 'Snacks' card."
 *
 * The card was not moving at all - it was always rendered above the FOOD LOG header,
 * so it was simply off-screen above the section he had tapped "+" on. Rendering it
 * inside the section is the fix; this function decides which section that is.
 *
 * Order of precedence deliberately puts [chosen] first. It is the slot the user just
 * tapped (the "+" on Snacks), and it must beat [fallback], which is the logger's
 * stored meal type and can be stale - e.g. Breakfast, left over from an earlier add.
 * Getting that order wrong would put the card in a different section than the one
 * tapped, which is the same bug in a new costume.
 *
 * [fallback] exists only for paths with no sheet open: barcode scanning from the
 * camera, where there is no section to have tapped.
 */
fun confirmSectionFor(chosen: String?, fallback: String?): String {
    val candidates = listOf(chosen, fallback)
    for (candidate in candidates) {
        val key = candidate?.trim()?.lowercase()
        if (key != null && key in MEAL_SECTIONS) return key
    }
    // Nothing usable: Snacks is the last section and the usual home for an
    // unclassified item. Never return null - a card rendered in the wrong place is
    // recoverable, a card rendered nowhere loses the food the user just chose.
    return "snacks"
}
