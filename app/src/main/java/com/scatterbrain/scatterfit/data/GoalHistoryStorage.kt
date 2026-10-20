package com.scatterbrain.scatterfit.data

import android.content.Context
import android.content.SharedPreferences
import com.scatterbrain.scatterfit.core.GoalChange
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Goal history storage layer (device-only, settings storage).
 * Stores an append-only list of GoalChange, maintaining earliest-date-first ordering.
 * The active goal is defined as the latest entry (last entry in the ordered list).
 */
class GoalHistoryStorage(private val prefs: SharedPreferences? = null) {

    constructor(context: Context) : this(
        context.getSharedPreferences("scatterfit_settings_prefs", Context.MODE_PRIVATE)
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val inMemoryList = mutableListOf<GoalChange>()

    fun loadHistory(): List<GoalChange> {
        if (prefs == null) {
            return inMemoryList.sortedBy { it.date }
        }
        val raw = prefs.getString(KEY_GOAL_HISTORY, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<GoalChange>>(raw).sortedBy { it.date }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun append(change: GoalChange) {
        val current = loadHistory().toMutableList()
        current.add(change)
        val sorted = current.sortedBy { it.date }
        if (prefs == null) {
            inMemoryList.clear()
            inMemoryList.addAll(sorted)
        } else {
            val raw = json.encodeToString(sorted)
            prefs.edit().putString(KEY_GOAL_HISTORY, raw).apply()
        }
    }

    fun activeGoal(): GoalChange? = loadHistory().lastOrNull()

    fun clear() {
        if (prefs == null) {
            inMemoryList.clear()
        } else {
            prefs.edit().remove(KEY_GOAL_HISTORY).apply()
        }
    }

    companion object {
        private const val KEY_GOAL_HISTORY = "goal_history_v1"
    }
}
