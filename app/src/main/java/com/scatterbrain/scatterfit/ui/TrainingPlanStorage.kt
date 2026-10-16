package com.scatterbrain.scatterfit.ui

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

fun weekDaysFor(anchorDayKey: String): List<String> {
    val date = LocalDate.parse(anchorDayKey)
    val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return (0L..6L).map { monday.plusDays(it).format(DateTimeFormatter.ISO_LOCAL_DATE) }
}

class PlannedSessionsStorage(
    private val prefsProvider: () -> String?,
    private val prefsWriter: (String) -> Unit
) {
    constructor(context: Context) : this(
        prefsProvider = {
            context.getSharedPreferences("scatterfit_training_prefs", Context.MODE_PRIVATE)
                .getString("training_plan_sessions", null)
        },
        prefsWriter = { jsonStr ->
            context.getSharedPreferences("scatterfit_training_prefs", Context.MODE_PRIVATE)
                .edit().putString("training_plan_sessions", jsonStr).apply()
        }
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun load(): List<PlannedSessionDay> {
        val raw = prefsProvider() ?: return emptyList()
        return try {
            json.decodeFromString<List<PlannedSessionDay>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(sessions: List<PlannedSessionDay>) {
        prefsWriter(json.encodeToString(sessions))
    }

    fun get(dayKey: String): PlannedSessionDay? {
        return load().find { it.dayKey == dayKey }
    }

    fun upsert(entry: PlannedSessionDay) {
        val current = load().toMutableList()
        val index = current.indexOfFirst { it.dayKey == entry.dayKey }
        if (index >= 0) {
            current[index] = entry
        } else {
            current.add(entry)
        }
        save(current)
    }

    fun remove(dayKey: String) {
        val current = load().filterNot { it.dayKey == dayKey }
        save(current)
    }
}

class LoggedSessionsStorage(
    private val prefsProvider: () -> String?,
    private val prefsWriter: (String) -> Unit
) {
    constructor(context: Context) : this(
        prefsProvider = {
            context.getSharedPreferences("scatterfit_training_prefs", Context.MODE_PRIVATE)
                .getString("training_logged_sessions", null)
        },
        prefsWriter = { jsonStr ->
            context.getSharedPreferences("scatterfit_training_prefs", Context.MODE_PRIVATE)
                .edit().putString("training_logged_sessions", jsonStr).apply()
        }
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun loadAll(): List<LoggedSession> {
        val raw = prefsProvider() ?: return emptyList()
        return try {
            json.decodeFromString<List<LoggedSession>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(sessions: List<LoggedSession>) {
        prefsWriter(json.encodeToString(sessions))
    }

    fun add(session: LoggedSession) {
        // Future pipeline integration seam:
        // When session player / pipeline lands, completed sessions emit as EXERCISE_SESSION
        // records into HealthRecord store for Day Card training minutes.
        val current = loadAll().toMutableList()
        current.removeAll { it.id == session.id }
        current.add(0, session) // newest first
        save(current)
    }

    fun loadByDay(dayKey: String): List<LoggedSession> {
        return loadAll().filter { it.dayKey == dayKey }
    }

    fun remove(id: String) {
        val current = loadAll().filterNot { it.id == id }
        save(current)
    }
}
