package com.scatterbrain.scatterfit.ui

import kotlin.math.roundToInt

/**
 * One line in the "Recents" list, with enough evidence to explain why it is there.
 *
 * [uses] is shown on the row ("3x") because an ordering nobody can account for reads
 * as random. If most-used rises to the top, the count has to be visible or the list
 * looks arbitrary the moment a familiar food is not first.
 */
data class RecentFood(
    val name: String,
    val uses: Int,
    val daysAgo: Int,
    val kcal: Double,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val portionGrams: Double?,
    val portionLabel: String?,
    val displayName: String?,
    val lastUsedMs: Long,
)

/**
 * Rank a food history by how recently AND how often each food was logged.
 *
 * Sam, 2026-11-24: "there should be a dropdown list of 'recently used' with most used
 * foods rising to the top of the list. The recents on the current list just show 4 or
 * 5 food items, of which, only 2 are actually recently used."
 *
 * What was there before was not a recents list at all: the UI took the first five
 * saved presets with `presets.filter { it.kcal > 0 }.take(5)`. Presets are ordered by
 * last use, but they are deduplicated by name+kcal+macros, so a food logged once
 * months ago sat next to a staple. Nothing was counting.
 *
 * Score = uses / (1 + daysAgo / [DECAY_DAYS]).
 *
 * Frequency weighted by recency, rather than plain last-used order, because the
 * question a recents list answers is "what do I eat", not "what did I type last".
 * A food logged four times in the last fortnight is a habit; one logged yesterday and
 * never before is an experiment. Plain recency puts the experiment on top.
 *
 * A one-off older than [staleOneOffDays] is dropped entirely: it is not recent, and it
 * is not a habit, so it has no claim to a slot. That is precisely the "only 2 are
 * actually recently used" complaint.
 */
fun rankRecentFoods(
    meals: List<LoggedMeal>,
    nowMs: Long,
    limit: Int = 8,
    staleOneOffDays: Int = 30,
    decayDays: Double = 7.0,
): List<RecentFood> {
    if (meals.isEmpty() || limit <= 0) return emptyList()

    val grouped = meals
        .filter { it.name.isNotBlank() && it.timestamp > 0L }
        .groupBy { normaliseFoodName(it.name) }

    return grouped.values
        .mapNotNull { entries ->
            val newest = entries.maxByOrNull { it.timestamp } ?: return@mapNotNull null
            val uses = entries.size
            val daysAgo = (((nowMs - newest.timestamp).coerceAtLeast(0L)) / 86_400_000L).toInt()
            if (uses == 1 && daysAgo > staleOneOffDays) return@mapNotNull null

            val score = uses / (1.0 + daysAgo / decayDays)
            RecentFood(
                name = newest.name.trim(),
                uses = uses,
                daysAgo = daysAgo,
                kcal = newest.kcal,
                protein = newest.protein,
                carbs = newest.carbs,
                fat = newest.fat,
                portionGrams = newest.portionGrams,
                portionLabel = newest.portionLabel,
                displayName = newest.displayName,
                lastUsedMs = newest.timestamp,
            ) to score
        }
        .sortedWith(
            compareByDescending<Pair<RecentFood, Double>> { it.second }
                // A stable tie-break: identical scores fall back to the newer use, so
                // the order cannot flip between recompositions and look random.
                .thenByDescending { it.first.lastUsedMs }
                .thenBy { it.first.name.lowercase() }
        )
        .take(limit)
        .map { it.first }
}

/** "Chorizo", " chorizo ", "CHORIZO" are one food, not three. */
fun normaliseFoodName(raw: String): String =
    raw.trim().lowercase().replace(Regex("\\s+"), " ")

/** "3x" for a habit, nothing for a first-time entry. */
fun recentUsesLabel(uses: Int): String? = if (uses > 1) "${uses}x" else null

/** "Today" / "Yesterday" / "5d ago" - the second half of why a row is where it is. */
fun recentAgeLabel(daysAgo: Int): String = when {
    daysAgo <= 0 -> "today"
    daysAgo == 1 -> "yesterday"
    daysAgo < 7 -> "${daysAgo}d ago"
    daysAgo < 14 -> "last week"
    daysAgo < 60 -> "${(daysAgo / 7.0).roundToInt()}w ago"
    else -> "${(daysAgo / 30.0).roundToInt()}mo ago"
}
