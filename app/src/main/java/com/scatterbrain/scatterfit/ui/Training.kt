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
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val timeSecondsMin: Int? = null,
    val timeSecondsMax: Int? = null,
    val note: String? = null,
    val difficulty: String? = null, // easy | medium | hard
)

fun repsLabel(s: PlannedSet): String = when {
    s.repsMin != null && s.repsMax != null -> "${s.repsMin}-${s.repsMax}"
    s.repsMin != null -> "${s.repsMin}"
    s.repsMax != null -> "${s.repsMax}"
    else -> ""
}

fun timeLabel(s: PlannedSet): String = when {
    s.timeSecondsMin != null && s.timeSecondsMax != null -> "${s.timeSecondsMin}-${s.timeSecondsMax}s"
    s.timeSecondsMin != null -> "${s.timeSecondsMin}s"
    s.timeSecondsMax != null -> "${s.timeSecondsMax}s"
    else -> ""
}

fun formatTimeUnitValue(seconds: Int, timeUnit: String): String {
    return when (timeUnit.lowercase()) {
        "min" -> {
            val v = seconds / 60.0
            val rounded = kotlin.math.round(v * 10.0) / 10.0
            if (rounded % 1.0 == 0.0) "${rounded.toInt()}" else "$rounded"
        }
        "hour" -> {
            val v = seconds / 3600.0
            val rounded = kotlin.math.round(v * 10.0) / 10.0
            if (rounded % 1.0 == 0.0) "${rounded.toInt()}" else "$rounded"
        }
        else -> "$seconds"
    }
}

fun timeUnitSuffix(timeUnit: String): String = when (timeUnit.lowercase()) {
    "min" -> " min"
    "hour" -> " h"
    else -> "s"
}

fun timeLabelScaled(s: PlannedSet, timeUnit: String = "sec"): String {
    val minSec = s.timeSecondsMin
    val maxSec = s.timeSecondsMax
    if (minSec == null && maxSec == null) return ""
    val suffix = timeUnitSuffix(timeUnit)
    return when {
        minSec != null && maxSec != null -> {
            val minFormatted = formatTimeUnitValue(minSec, timeUnit)
            val maxFormatted = formatTimeUnitValue(maxSec, timeUnit)
            "$minFormatted-$maxFormatted$suffix"
        }
        minSec != null -> "${formatTimeUnitValue(minSec, timeUnit)}$suffix"
        maxSec != null -> "${formatTimeUnitValue(maxSec, timeUnit)}$suffix"
        else -> ""
    }
}

fun setTargetLabel(s: PlannedSet, isTimed: Boolean = false, timeUnit: String = "sec"): String {
    return if (isTimed || s.timeSecondsMin != null) timeLabelScaled(s, timeUnit) else repsLabel(s)
}

fun formatSetLine(setNumber: Int, set: PlannedSet, isTimed: Boolean, timeUnit: String = "sec"): String {
    val typePart = when (set.type) {
        "warmup" -> " (warmup)"
        "failure" -> " (failure)"
        else -> ""
    }
    val targetStr = if (isTimed || set.timeSecondsMin != null) {
        timeLabelScaled(set, timeUnit)
    } else {
        repsLabel(set)
    }
    val weightStr = set.weightKg?.let { if (it % 1.0 == 0.0) "${it.toInt()}kg" else "${it}kg" }

    val core = when {
        weightStr != null && targetStr.isNotBlank() -> {
            if (isTimed) "$targetStr @ $weightStr" else "$weightStr × $targetStr"
        }
        targetStr.isNotBlank() -> targetStr
        weightStr != null -> weightStr
        else -> "—"
    }
    val noteSuffix = if (!set.note.isNullOrBlank()) " · ${set.note}" else ""
    return "Set $setNumber$typePart: $core$noteSuffix"
}

fun formatExerciseSetsSummary(exercise: RoutineExercise): List<String> {
    val sets = exercise.sets
    if (sets.isEmpty()) return listOf("0 sets")

    val first = sets.first()
    val allIdentical = sets.all {
        it.type == first.type &&
        it.weightKg == first.weightKg &&
        it.repsMin == first.repsMin &&
        it.repsMax == first.repsMax &&
        it.timeSecondsMin == first.timeSecondsMin &&
        it.timeSecondsMax == first.timeSecondsMax &&
        it.note.isNullOrBlank()
    }

    if (allIdentical && first.note.isNullOrBlank()) {
        val count = sets.size
        val targetStr = if (exercise.isTimed || first.timeSecondsMin != null) {
            timeLabelScaled(first, exercise.timeUnit)
        } else {
            repsLabel(first)
        }
        val weightStr = first.weightKg?.let { if (it % 1.0 == 0.0) "${it.toInt()}" else "$it" }
        val typePrefix = when (first.type) {
            "warmup" -> "Warmup: "
            "failure" -> "Failure: "
            else -> ""
        }
        val compact = when {
            weightStr != null && targetStr.isNotBlank() -> "$typePrefix$count × $targetStr @ $weightStr"
            targetStr.isNotBlank() -> "$typePrefix$count × $targetStr"
            weightStr != null -> "$typePrefix$count × $weightStr kg"
            else -> "$typePrefix$count sets"
        }
        return listOf(compact)
    }

    return sets.mapIndexed { idx, s ->
        formatSetLine(idx + 1, s, exercise.isTimed, exercise.timeUnit)
    }
}

fun formatTempo(e: String, p1: String, c: String, p2: String): String? {
    val cleanE = e.trim()
    val cleanP1 = p1.trim()
    val cleanC = c.trim()
    val cleanP2 = p2.trim()
    if (cleanE.isEmpty() && cleanP1.isEmpty() && cleanC.isEmpty() && cleanP2.isEmpty()) {
        return null
    }
    val valE = cleanE.ifEmpty { "0" }
    val valP1 = cleanP1.ifEmpty { "0" }
    val valC = cleanC.ifEmpty { "0" }
    val valP2 = cleanP2.ifEmpty { "0" }
    return "$valE-$valP1-$valC-$valP2"
}

fun parseTempo(tempo: String?): List<String> {
    if (tempo.isNullOrBlank()) return listOf("", "", "", "")
    val parts = tempo.split("-").map { it.trim() }
    return List(4) { idx -> parts.getOrNull(idx) ?: "" }
}

@Serializable
data class RoutineExercise(
    val exerciseId: String,
    val exerciseName: String,
    val sets: List<PlannedSet> = emptyList(),
    val restSeconds: Int = 0, // 0 = OFF
    val note: String? = null, // exercise-level note
    val customName: String? = null, // when set, displays instead of library name
    val tempo: String? = null, // e.g. "2-0-2-1"
    val isTimed: Boolean = false, // when true, editor shows time inputs instead of reps
    val timeUnit: String = "sec", // sec | min | hour
) {
    val displayName: String get() = customName?.takeIf { it.isNotBlank() } ?: exerciseName
}

@Serializable
data class Routine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val exercises: List<RoutineExercise> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

// Legacy DTOs for seamless backward-compatible decoding of old stored routines
@Serializable
private data class LegacyPlannedSet(
    val type: String = "normal",
    val weightKg: Double? = null,
    val reps: Int? = null,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val timeSecondsMin: Int? = null,
    val timeSecondsMax: Int? = null,
    val note: String? = null,
    val difficulty: String? = null,
) {
    fun toPlannedSet(): PlannedSet = PlannedSet(
        type = type,
        weightKg = weightKg,
        repsMin = repsMin ?: reps,
        repsMax = repsMax,
        timeSecondsMin = timeSecondsMin,
        timeSecondsMax = timeSecondsMax,
        note = note,
        difficulty = difficulty,
    )
}

@Serializable
private data class LegacyRoutineExercise(
    val exerciseId: String,
    val exerciseName: String,
    val sets: List<LegacyPlannedSet> = emptyList(),
    val restSeconds: Int = 0,
    val note: String? = null,
    val customName: String? = null,
    val tempo: String? = null,
    val isTimed: Boolean = false,
    val timeUnit: String = "sec",
) {
    fun toRoutineExercise(): RoutineExercise = RoutineExercise(
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        sets = sets.map { it.toPlannedSet() },
        restSeconds = restSeconds,
        note = note,
        customName = customName,
        tempo = tempo,
        isTimed = isTimed,
        timeUnit = timeUnit,
    )
}

@Serializable
private data class LegacyRoutine(
    val id: String,
    val name: String,
    val exercises: List<LegacyRoutineExercise> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toRoutine(): Routine = Routine(
        id = id,
        name = name,
        exercises = exercises.map { it.toRoutineExercise() },
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun decodeRoutinesJson(raw: String): List<Routine> {
    val json = Json { ignoreUnknownKeys = true }
    return try {
        val legacy = json.decodeFromString<List<LegacyRoutine>>(raw)
        legacy.map { it.toRoutine() }
    } catch (_: Exception) {
        emptyList()
    }
}

class RoutineStorage(context: Context) {
    private val prefs = context.getSharedPreferences("scatterfit_training_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadRoutines(): List<Routine> {
        val raw = prefs.getString("training_routines", null) ?: return emptyList()
        return decodeRoutinesJson(raw)
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
