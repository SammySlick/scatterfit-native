package com.scatterbrain.scatterfit.ui

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
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
    val timeSeconds: Int? = null,
    val timeUnit: String = "sec", // sec | min | hour
    val note: String? = null,
    val difficulty: String? = null, // easy | medium | hard
)

fun repsLabel(s: PlannedSet): String = when {
    s.repsMin != null && s.repsMax != null -> "${s.repsMin}-${s.repsMax}"
    s.repsMin != null -> "${s.repsMin}"
    s.repsMax != null -> "${s.repsMax}"
    else -> ""
}

fun formatClockTime(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    val hours = s / 3600
    val remainder = s % 3600
    val mins = remainder / 60
    val secs = remainder % 60

    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

fun displayTimeLabel(seconds: Int?): String {
    if (seconds == null) return ""
    return formatClockTime(seconds)
}

fun timeLabel(s: PlannedSet): String = displayTimeLabel(s.timeSeconds)

fun timeLabelScaled(s: PlannedSet, timeUnit: String = "sec"): String {
    val sec = s.timeSeconds ?: return ""
    return when (timeUnit.lowercase()) {
        "min" -> {
            val v = sec / 60.0
            val rounded = kotlin.math.round(v * 10.0) / 10.0
            val numStr = if (rounded % 1.0 == 0.0) "${rounded.toInt()}" else "$rounded"
            "$numStr mins"
        }
        "hour" -> {
            val v = sec / 3600.0
            val rounded = kotlin.math.round(v * 10.0) / 10.0
            val numStr = if (rounded % 1.0 == 0.0) "${rounded.toInt()}" else "$rounded"
            "$numStr hours"
        }
        else -> "$sec secs"
    }
}

fun formatTimeEntry(seconds: Int?, unit: String): String {
    if (seconds == null) return ""
    val s = seconds.coerceAtLeast(0)
    return when (unit.lowercase()) {
        "min" -> {
            val mins = s / 60
            val secs = s % 60
            String.format(Locale.US, "%d:%02d", mins, secs)
        }
        "hour", "hrs" -> {
            val hours = s / 3600
            val mins = (s % 3600) / 60
            String.format(Locale.US, "%d:%02d", hours, mins)
        }
        else -> "$s"
    }
}

fun timeEntryText(seconds: Int?, unit: String): String = formatTimeEntry(seconds, unit)
fun displayTimeEntry(seconds: Int?, unit: String): String = formatTimeEntry(seconds, unit)

fun parseTimeEntry(text: String, unit: String): Int? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null

    return when (unit.lowercase()) {
        "sec" -> {
            if (!trimmed.all { it.isDigit() }) return null
            trimmed.toIntOrNull()
        }
        "min" -> {
            if (trimmed.contains(':')) {
                val parts = trimmed.split(':')
                if (parts.size != 2) return null
                val mins = parts[0].toIntOrNull() ?: return null
                val secs = parts[1].toIntOrNull() ?: return null
                if (mins < 0 || secs < 0 || secs > 59) return null
                mins * 60 + secs
            } else {
                if (!trimmed.all { it.isDigit() }) return null
                val m = trimmed.toIntOrNull() ?: return null
                m * 60
            }
        }
        "hour", "hrs" -> {
            if (trimmed.contains(':')) {
                val parts = trimmed.split(':')
                if (parts.size != 2) return null
                val hours = parts[0].toIntOrNull() ?: return null
                val mins = parts[1].toIntOrNull() ?: return null
                if (hours < 0 || mins < 0 || mins > 59) return null
                hours * 3600 + mins * 60
            } else {
                if (!trimmed.all { it.isDigit() }) return null
                val h = trimmed.toIntOrNull() ?: return null
                h * 3600
            }
        }
        else -> {
            if (!trimmed.all { it.isDigit() }) return null
            trimmed.toIntOrNull()
        }
    }
}

fun timeEntryToSeconds(text: String, unit: String): Int? = parseTimeEntry(text, unit)
fun parseTimeTargetField(text: String, unit: String): Int? = parseTimeEntry(text, unit)

fun parseTargetField(text: String): Pair<Int?, Int?>? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return Pair(null, null)

    // Clock strings like "1:30" contain ':' and must be rejected for reps
    if (!trimmed.all { it.isDigit() || it == '-' || it.isWhitespace() }) {
        return null
    }

    val dashCount = trimmed.count { it == '-' }
    if (dashCount > 1) return null

    if (dashCount == 0) {
        val num = trimmed.filterNot { it.isWhitespace() }.toIntOrNull() ?: return null
        return Pair(num, null)
    }

    val parts = trimmed.split('-').map { it.trim() }
    if (parts.size != 2) return null

    val leftStr = parts[0]
    val rightStr = parts[1]

    val leftNum = if (leftStr.isEmpty()) null else leftStr.toIntOrNull() ?: return null
    val rightNum = if (rightStr.isEmpty()) null else rightStr.toIntOrNull() ?: return null

    if (leftNum == null && rightNum == null) return Pair(null, null)

    return Pair(leftNum, rightNum)
}

fun formatRepsTargetEntry(minReps: Int?, maxReps: Int?): String = when {
    minReps != null && maxReps != null -> "$minReps-$maxReps"
    minReps != null -> "$minReps"
    maxReps != null -> "-$maxReps"
    else -> ""
}

fun timeUnitPickerLabel(unit: String): String = when (unit.lowercase()) {
    "min" -> "min"
    "hour", "hrs" -> "hrs"
    else -> "sec"
}

fun cycleTimeUnit(current: String): String = when (current.lowercase()) {
    "sec" -> "min"
    "min" -> "hour"
    else -> "sec"
}

fun cycleDifficulty(current: String?): String? = when (current?.lowercase()) {
    null -> "easy"
    "easy" -> "medium"
    "medium" -> "hard"
    "hard" -> null
    else -> null
}

fun setTargetLabel(s: PlannedSet, isTimed: Boolean = false): String {
    return if (isTimed || s.timeSeconds != null) displayTimeLabel(s.timeSeconds) else repsLabel(s)
}

fun isSetEmpty(s: PlannedSet): Boolean {
    return s.weightKg == null && s.repsMin == null && s.repsMax == null && s.timeSeconds == null
}

fun formatSetLine(setNumber: Int, set: PlannedSet, isTimed: Boolean): String {
    val typePart = when (set.type) {
        "warmup" -> " (warmup)"
        "failure" -> " (failure)"
        else -> ""
    }
    val noteSuffix = if (!set.note.isNullOrBlank()) " · ${set.note}" else ""

    if (isSetEmpty(set)) {
        return "Set $setNumber$typePart: —$noteSuffix"
    }

    val targetStr = if (isTimed || set.timeSeconds != null) {
        displayTimeLabel(set.timeSeconds)
    } else {
        repsLabel(set)
    }
    val weightStr = set.weightKg?.let {
        val num = if (it % 1.0 == 0.0) "${it.toInt()}" else "$it"
        "$num kg"
    }

    val core = when {
        weightStr != null && targetStr.isNotBlank() -> {
            if (isTimed || set.timeSeconds != null) "$targetStr @ $weightStr" else "$weightStr × $targetStr"
        }
        targetStr.isNotBlank() -> targetStr
        weightStr != null -> weightStr
        else -> "—"
    }
    return "Set $setNumber$typePart: $core$noteSuffix"
}

fun formatExerciseSetsSummary(exercise: RoutineExercise): List<String> {
    val sets = exercise.sets
    if (sets.isEmpty()) return listOf("0 sets")

    val hasEmptySet = sets.any { isSetEmpty(it) }

    val first = sets.first()
    val allIdentical = !hasEmptySet && sets.all {
        it.type == first.type &&
        it.weightKg == first.weightKg &&
        it.repsMin == first.repsMin &&
        it.repsMax == first.repsMax &&
        it.timeSeconds == first.timeSeconds &&
        it.note.isNullOrBlank()
    }

    if (allIdentical && first.note.isNullOrBlank()) {
        val count = sets.size
        val targetStr = if (exercise.isTimed || first.timeSeconds != null) {
            displayTimeLabel(first.timeSeconds)
        } else {
            repsLabel(first)
        }
        val weightStr = first.weightKg?.let {
            val num = if (it % 1.0 == 0.0) "${it.toInt()}" else "$it"
            "$num kg"
        }
        val typePrefix = when (first.type) {
            "warmup" -> "Warmup: "
            "failure" -> "Failure: "
            else -> ""
        }
        val compact = when {
            weightStr != null && targetStr.isNotBlank() -> "$typePrefix$count × $targetStr @ $weightStr"
            targetStr.isNotBlank() -> "$typePrefix$count × $targetStr"
            weightStr != null -> "$typePrefix$count × $weightStr"
            else -> "$typePrefix$count ${if (count == 1) "set" else "sets"}"
        }
        return listOf(compact)
    }

    return sets.mapIndexed { idx, s ->
        formatSetLine(idx + 1, s, exercise.isTimed)
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

@Serializable
data class PlannedSessionDay(
    val dayKey: String, // yyyy-MM-dd, LOCAL
    val routineId: String? = null,
    val sessionName: String? = null,
    val note: String? = null,
)

@Serializable
data class LoggedSession(
    val id: String = UUID.randomUUID().toString(),
    val dayKey: String,
    val name: String,
    val durationMinutes: Int? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

// Legacy DTOs for seamless backward-compatible decoding of old stored routines
@Serializable
private data class LegacyPlannedSet(
    val type: String = "normal",
    val weightKg: Double? = null,
    val reps: Int? = null,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val timeSeconds: Int? = null,
    val timeSecondsMin: Int? = null,
    val timeSecondsMax: Int? = null,
    val timeUnit: String = "sec",
    val note: String? = null,
    val difficulty: String? = null,
) {
    fun toPlannedSet(): PlannedSet = PlannedSet(
        type = type,
        weightKg = weightKg,
        repsMin = repsMin ?: reps,
        repsMax = repsMax,
        timeSeconds = timeSeconds ?: timeSecondsMin ?: timeSecondsMax,
        timeUnit = timeUnit,
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
