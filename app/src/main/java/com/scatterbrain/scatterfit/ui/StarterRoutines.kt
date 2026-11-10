package com.scatterbrain.scatterfit.ui

import android.content.Context

/**
 * Routines imported from Sam's Hevy account and shipped inside the APK.
 *
 * These are ordinary [Routine] objects — nothing downstream special-cases them, so they
 * edit and delete like any routine Sam built in the app. [seedIfNeeded] writes them into
 * [RoutineStorage] exactly once, keyed off a prefs flag, so a deleted or edited routine
 * never comes back on the next app update.
 *
 * Source: https://hevy.com/routine/DwMFiwQ38mw ("Upper A", 11 exercises / 26 sets).
 * Hevy names are kept as [RoutineExercise.customName] where the app's library exercise
 * is called something else, so the routine reads the same as it does on the Hevy side.
 */
object StarterRoutines {

    /** Fixed ids: re-seeding stays idempotent and edits survive app updates. */
    const val UPPER_A_ID = "hevy-upper-a"
    private const val PREFS = "scatterfit_training_prefs"
    private const val SEEDED_KEY = "starter_routines_seeded_v1"

    /** Import time, fixed so the routine keeps its place in the list. */
    private const val IMPORTED_AT = 1794326400000L // 2026-11-10 16:00 UTC

    private const val REDUCE_SET_2 = "Reduce weight for set 2 by 20%"
    private const val STAY_IN_RANGE = "Reduce weight to stay in rep range"
    private const val REST_PAUSE = "Rest pause — keep the same weight, go heavy"

    val all: List<Routine> get() = listOf(upperA)

    val upperA: Routine = Routine(
        id = UPPER_A_ID,
        name = "Upper A",
        createdAt = IMPORTED_AT,
        updatedAt = IMPORTED_AT,
        exercises = listOf(
            RoutineExercise(
                exerciseId = "custom-cable-d-handle-external-rotation",
                exerciseName = "Cable D Handle External Rotation",
                tempo = "2-0-2-1",
                sets = repeated(2, repsMin = 10, repsMax = 12),
                note = "Shoulder warm-up. Light weight, D handle on the cable."
            ),
            RoutineExercise(
                exerciseId = "custom-dead-hang",
                exerciseName = "Dead Hang",
                isTimed = true,
                timeUnit = "sec",
                sets = List(2) { PlannedSet(timeSeconds = 20) },
                note = "Hang 20-30 seconds, 2 sets."
            ),
            RoutineExercise(
                exerciseId = "machine-hip-adduction",
                exerciseName = "Machine Hip Adduction",
                customName = "Hip Adduction (Machine)",
                tempo = "3-1-2-1",
                sets = repeated(2, repsMin = 12, repsMax = 15),
                note = REDUCE_SET_2
            ),
            RoutineExercise(
                exerciseId = "machine-chest-fly",
                exerciseName = "Machine Chest Fly (Pec Deck)",
                customName = "Butterfly (Pec Deck)",
                tempo = "3-0-2-1",
                sets = repeated(2, repsMin = 12, repsMax = 14),
                note = REDUCE_SET_2
            ),
            RoutineExercise(
                exerciseId = "machine-lateral-raise",
                exerciseName = "Machine Lateral Raise",
                customName = "Lateral Raise (Machine)",
                tempo = "4-1-4-1",
                sets = repeated(2, repsMin = 12, repsMax = 15),
                note = REDUCE_SET_2
            ),
            RoutineExercise(
                exerciseId = "incline-dumbbell-bench-press",
                exerciseName = "Incline Dumbbell Bench Press",
                customName = "Incline Bench Press (Dumbbell)",
                tempo = "2-0-2-1",
                sets = repeated(2, repsMin = 9, repsMax = 12),
                note = "High incline, 60-70°. $REDUCE_SET_2"
            ),
            RoutineExercise(
                exerciseId = "dumbbell-bench-press",
                exerciseName = "Dumbbell Bench Press",
                customName = "Bench Press (Dumbbell)",
                tempo = "3-0-2-1",
                restSeconds = 20,
                sets = restPauseSets(),
                note = REST_PAUSE
            ),
            RoutineExercise(
                exerciseId = "tricep-rope-pushdown",
                exerciseName = "Cable Tricep Rope Pushdown",
                customName = "Single Arm Triceps Pushdown (Cable)",
                tempo = "3-0-3-1",
                sets = repeated(2, repsMin = 12),
                note = "$STAY_IN_RANGE. Superset with Seated Incline Curl."
            ),
            RoutineExercise(
                exerciseId = "seated-incline-dumbbell-curl",
                exerciseName = "Incline Dumbbell Curl",
                customName = "Seated Incline Curl (Dumbbell)",
                tempo = "3-0-2-1",
                sets = repeated(3, repsMin = 12, repsMax = 15),
                note = "$STAY_IN_RANGE. Superset with Single Arm Triceps Pushdown."
            ),
            RoutineExercise(
                exerciseId = "skull-crusher",
                exerciseName = "Skull Crusher (Lying Triceps Extension)",
                customName = "Skullcrusher (Dumbbell)",
                tempo = "4-0-1-0",
                sets = repeated(3, repsMin = 12, repsMax = 15),
                note = STAY_IN_RANGE
            ),
            RoutineExercise(
                exerciseId = "machine-bicep-curl",
                exerciseName = "Machine Bicep Curl",
                customName = "Preacher Curl (Machine)",
                tempo = "3-0-2-1",
                restSeconds = 20,
                sets = restPauseSets(),
                note = REST_PAUSE
            ),
        )
    )

    private fun repeated(count: Int, repsMin: Int, repsMax: Int? = null): List<PlannedSet> =
        List(count) { PlannedSet(repsMin = repsMin, repsMax = repsMax) }

    /** Rest-pause block: one heavy set, then two shorter ones at the same weight. */
    private fun restPauseSets(): List<PlannedSet> = listOf(
        PlannedSet(repsMin = 8, repsMax = 12),
        PlannedSet(repsMin = 4, repsMax = 8),
        PlannedSet(repsMin = 2, repsMax = 4),
    )

    /**
     * Writes any shipped routine the user doesn't already have, then latches a flag so
     * this runs once per install. Safe to call on every launch.
     */
    fun seedIfNeeded(context: Context, storage: RoutineStorage) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(SEEDED_KEY, false)) return

        val existing = storage.loadRoutines().map { it.id }.toSet()
        for (routine in all) {
            if (routine.id !in existing) storage.upsert(routine)
        }
        prefs.edit().putBoolean(SEEDED_KEY, true).apply()
    }
}
