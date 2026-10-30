package com.scatterbrain.scatterfit.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class WeeklyCheckIn(
    val weekStart: String,                                  // Monday (or week anchor) day key
    val perDay: Map<String, Map<String, String>> = emptyMap(), // dayKey -> rowId -> raw value
    val weekly: Map<String, String> = emptyMap(),            // rowId -> raw value
)

class WeeklyCheckInStorage(
    private val context: Context? = null,
    private val prefs: SharedPreferences? =
        context?.getSharedPreferences("scatterfit_weekly_checkin", Context.MODE_PRIVATE),
) {
    private val inMemory = mutableMapOf<String, String>()
    private val json = Json { ignoreUnknownKeys = true }

    private fun keyFor(weekStart: String): String = "weekly_checkin:$weekStart"

    fun load(weekStart: String): WeeklyCheckIn {
        val key = keyFor(weekStart)
        val raw = prefs?.getString(key, null)
            ?: inMemory[key]
            ?: return WeeklyCheckIn(weekStart = weekStart)
        return try {
            json.decodeFromString<WeeklyCheckIn>(raw)
        } catch (_: Exception) {
            WeeklyCheckIn(weekStart = weekStart)
        }
    }

    fun save(record: WeeklyCheckIn) {
        val key = keyFor(record.weekStart)
        val raw = json.encodeToString(record)
        if (prefs != null) {
            prefs.edit().putString(key, raw).apply()
        } else {
            inMemory[key] = raw
        }
    }

    fun setDayValue(weekStart: String, dayKey: String, rowId: String, value: String?) {
        val current = load(weekStart)
        val dayMap = current.perDay[dayKey]?.toMutableMap() ?: mutableMapOf()
        val newPerDay = current.perDay.toMutableMap()
        if (value == null) {
            dayMap.remove(rowId)
            if (dayMap.isEmpty()) {
                newPerDay.remove(dayKey)
            } else {
                newPerDay[dayKey] = dayMap
            }
        } else {
            dayMap[rowId] = value
            newPerDay[dayKey] = dayMap
        }
        save(current.copy(perDay = newPerDay))
    }

    fun setWeeklyValue(weekStart: String, rowId: String, value: String?) {
        val current = load(weekStart)
        val weeklyMap = current.weekly.toMutableMap()
        if (value == null) {
            weeklyMap.remove(rowId)
        } else {
            weeklyMap[rowId] = value
        }
        save(current.copy(weekly = weeklyMap))
    }
}
