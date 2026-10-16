package com.scatterbrain.scatterfit.ui

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class ExerciseRef(
    val id: String,
    val name: String,
    val muscle: String,
    val isCustom: Boolean = false,
)

@Serializable
data class PlannedSet(
    val type: String = "normal", // normal | warmup | failure
    val weightKg: Double? = null,
    val reps: Int? = null,
)

@Serializable
data class RoutineExercise(
    val exerciseId: String,
    val exerciseName: String,
    val sets: List<PlannedSet> = emptyList(),
)

@Serializable
data class Routine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val exercises: List<RoutineExercise> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

class RoutineStorage(context: Context) {
    private val prefs = context.getSharedPreferences("scatterfit_training_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadRoutines(): List<Routine> {
        val raw = prefs.getString("training_routines", null) ?: return emptyList()
        return try {
            json.decodeFromString<List<Routine>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveRoutines(routines: List<Routine>) {
        val raw = json.encodeToString(routines)
        prefs.edit().putString("training_routines", raw).apply()
    }

    fun get(id: String): Routine? {
        return loadRoutines().find { it.id == id }
    }

    fun upsert(routine: Routine) {
        val current = loadRoutines().toMutableList()
        val index = current.indexOfFirst { it.id == routine.id }
        if (index >= 0) {
            current[index] = routine
        } else {
            current.add(routine)
        }
        saveRoutines(current)
    }

    fun delete(id: String) {
        val current = loadRoutines().filterNot { it.id == id }
        saveRoutines(current)
    }
}
