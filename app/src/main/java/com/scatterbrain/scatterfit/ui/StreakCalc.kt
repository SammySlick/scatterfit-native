package com.scatterbrain.scatterfit.ui

import com.scatterbrain.scatterfit.core.dayBefore

/**
 * Computes consecutive days with logged entries ending at [todayKey].
 * A day counts if food entries > 0 OR drink entries > 0 OR a weigh-in exists.
 * GRACE RULE: if today has no entries yet, count from yesterday.
 */
fun computeStreak(
    foodByDay: Map<String, Int>,
    drinksByDay: Map<String, Int>,
    weightsByDay: Set<String>,
    todayKey: String
): Int {
    fun isDayLogged(day: String): Boolean {
        return (foodByDay[day] ?: 0) > 0 || (drinksByDay[day] ?: 0) > 0 || weightsByDay.contains(day)
    }

    var streak = 0
    var currentDay = todayKey

    // Grace rule: if today has no entries yet, do not break the streak — count from yesterday
    if (!isDayLogged(todayKey)) {
        currentDay = dayBefore(todayKey)
    }

    while (isDayLogged(currentDay)) {
        streak++
        currentDay = dayBefore(currentDay)
    }

    return streak
}
