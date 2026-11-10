package com.scatterbrain.scatterfit.ui

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * The in-progress copy of a workout session.
 *
 * The player used to hold a session ONLY in memory until Finish, so Android reclaiming
 * the process (phone asleep, low memory) silently destroyed the whole workout. This is
 * the on-disk mirror: written every couple of seconds and again whenever the app leaves
 * the foreground, read back on launch so the session can be resumed where it stopped.
 *
 * Deliberately separate from [SessionRecord]: a record is a finished, logged session,
 * a draft is unfinished and disposable. Nothing here counts towards training totals.
 */
@Serializable
data class DraftSetState(
    val weightKg: Double? = null,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val timeSeconds: Int? = null,
    val timeUnit: String = "sec",
    val difficulty: String? = null, // easy | medium | hard
    val done: Boolean = false,
)

@Serializable
data class DraftExerciseState(
    val exerciseId: String,
    val exerciseName: String,
    val restSeconds: Int = 0,
    val isTimed: Boolean = false,
    val sets: List<DraftSetState> = emptyList(),
)

@Serializable
data class ActiveSessionDraft(
    /** Stable across resumes: the same draft keeps its id as it is rewritten. */
    val id: String = UUID.randomUUID().toString(),
    val dayKey: String,
    val routineId: String? = null,
    val routineName: String? = null,
    val sessionName: String? = null,
    val startedAt: Long,
    val updatedAt: Long = 0L,
    val exercises: List<DraftExerciseState> = emptyList(),
    /** Rest countdown carried across a resume, as an absolute deadline. */
    val restTotalSeconds: Int = 0,
    val restEndsAt: Long? = null,
)

val ActiveSessionDraft.title: String
    get() = routineName ?: sessionName ?: "Workout"

val ActiveSessionDraft.totalSets: Int
    get() = exercises.sumOf { it.sets.size }

val ActiveSessionDraft.doneSets: Int
    get() = exercises.sumOf { ex -> ex.sets.count { it.done } }

/** "34 min in · 8 of 26 sets", or "Just started" before anything is ticked. */
fun draftProgressLabel(draft: ActiveSessionDraft, nowMillis: Long): String {
    val minutes = ((nowMillis - draft.startedAt) / 60_000L).coerceAtLeast(0L)
    val elapsed = when {
        minutes < 1L -> "Just started"
        minutes < 60L -> "$minutes min in"
        else -> "${minutes / 60L} h ${minutes % 60L} min in"
    }
    val done = draft.doneSets
    val total = draft.totalSets
    return if (total == 0) elapsed else "$elapsed · $done of $total sets"
}

/** True when the draft has nothing worth resuming (no set ticked, no weight entered). */
fun isDraftUntouched(draft: ActiveSessionDraft): Boolean = draft.exercises.all { ex ->
    ex.sets.none { it.done || it.weightKg != null || it.difficulty != null }
}

/**
 * A set added mid-workout, seeded from the set before it.
 *
 * The load and the rep/time target carry over, because adding a set almost always means
 * "another one like that". The tick and the difficulty rating do not: nothing has been
 * performed yet.
 */
fun DraftSetState.asAddedSet(): DraftSetState = copy(
    difficulty = null,
    done = false,
)

/** Rest used for an exercise added mid-workout when nothing better is known. */
const val ADDED_EXERCISE_REST_SECONDS: Int = 90

/** Sets a brand-new (mid-workout) exercise starts with: three working sets of 8-12. */
fun defaultSetsForAddedExercise(): List<DraftSetState> = List(3) {
    DraftSetState(repsMin = 8, repsMax = 12)
}

class ActiveSessionStorage(
    private val prefsProvider: () -> String?,
    private val prefsWriter: (String) -> Unit,
    private val prefsClearer: () -> Unit,
) {
    constructor(context: Context) : this(
        prefsProvider = {
            context.getSharedPreferences("scatterfit_training_prefs", Context.MODE_PRIVATE)
                .getString(KEY, null)
        },
        prefsWriter = { jsonStr ->
            context.getSharedPreferences("scatterfit_training_prefs", Context.MODE_PRIVATE)
                .edit().putString(KEY, jsonStr).apply()
        },
        prefsClearer = {
            context.getSharedPreferences("scatterfit_training_prefs", Context.MODE_PRIVATE)
                .edit().remove(KEY).apply()
        }
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun load(): ActiveSessionDraft? {
        val raw = prefsProvider() ?: return null
        return try {
            json.decodeFromString<ActiveSessionDraft>(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun save(draft: ActiveSessionDraft) {
        prefsWriter(json.encodeToString(draft))
    }

    fun clear() {
        prefsClearer()
    }

    fun hasDraft(): Boolean = load() != null

    /** True once a draft exists that isn't empty — the state the Training tab cares about. */
    fun hasResumableDraft(): Boolean = load()?.let { !isDraftUntouched(it) } == true

    companion object {
        private const val KEY = "training_active_session_draft"
    }
}
