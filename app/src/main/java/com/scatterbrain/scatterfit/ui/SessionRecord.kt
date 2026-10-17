package com.scatterbrain.scatterfit.ui

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class LoggedSetInSession(
    val weightKg: Double? = null,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val timeSeconds: Int? = null,
    val difficulty: String? = null, // easy | medium | hard
    val done: Boolean = false,
)

@Serializable
data class LoggedExerciseInSession(
    val exerciseId: String,
    val exerciseName: String,
    val sets: List<LoggedSetInSession> = emptyList(),
)

@Serializable
data class SessionRecord(
    val id: String = UUID.randomUUID().toString(),
    val dayKey: String, // yyyy-MM-dd, LOCAL
    val routineId: String? = null,
    val name: String,
    val exercises: List<LoggedExerciseInSession> = emptyList(),
    val durationMinutes: Int? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
)

class SessionRecordsStorage(
    private val prefsProvider: () -> String?,
    private val prefsWriter: (String) -> Unit,
) {
    constructor(context: Context) : this(
        prefsProvider = {
            context.getSharedPreferences("scatterfit_training_prefs", Context.MODE_PRIVATE)
                .getString("training_session_records", null)
        },
        prefsWriter = { jsonStr ->
            context.getSharedPreferences("scatterfit_training_prefs", Context.MODE_PRIVATE)
                .edit().putString("training_session_records", jsonStr).apply()
        }
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun loadAll(): List<SessionRecord> {
        val raw = prefsProvider() ?: return emptyList()
        return try {
            json.decodeFromString<List<SessionRecord>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(records: List<SessionRecord>) {
        prefsWriter(json.encodeToString(records))
    }

    fun add(record: SessionRecord) {
        val current = loadAll().toMutableList()
        current.removeAll { it.id == record.id }
        current.add(0, record) // newest first
        save(current)
    }

    fun loadByDay(dayKey: String): List<SessionRecord> {
        return loadAll().filter { it.dayKey == dayKey }
    }

    fun latestForExercise(exerciseId: String): LoggedExerciseInSession? {
        for (record in loadAll()) {
            val ex = record.exercises.find { it.exerciseId == exerciseId }
            if (ex != null && ex.sets.any { it.done }) {
                return ex
            }
        }
        return null
    }

    fun latestForRoutine(routineId: String): SessionRecord? {
        return loadAll().firstOrNull { it.routineId == routineId }
    }
}

fun formatPreviousSetSummary(previousExercise: LoggedExerciseInSession?, setIndex: Int): String {
    if (previousExercise == null) return "—"
    val doneSets = previousExercise.sets.filter { it.done }
    val prevSet = doneSets.getOrNull(setIndex) ?: return "—"

    val targetStr = when {
        prevSet.timeSeconds != null -> formatClockTime(prevSet.timeSeconds)
        prevSet.repsMin != null && prevSet.repsMax != null -> "${prevSet.repsMin}-${prevSet.repsMax}"
        prevSet.repsMin != null -> "${prevSet.repsMin}"
        else -> ""
    }
    val weightStr = prevSet.weightKg?.let { if (it % 1.0 == 0.0) "${it.toInt()}" else "$it" }

    return when {
        weightStr != null && targetStr.isNotBlank() -> "$weightStr × $targetStr"
        targetStr.isNotBlank() -> targetStr
        weightStr != null -> "$weightStr kg"
        else -> "—"
    }
}
