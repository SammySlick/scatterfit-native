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
 * Sources:
 *  - https://hevy.com/routine/DwMFiwQ38mw ("Upper A", 11 exercises / 26 sets).
 *  - Two full-body plans Sam pasted in chat 2026-11-14 ("Full Body 1/2 (ADHD-Friendly)").
 *
 * Hevy names are kept as [RoutineExercise.customName] where the app's library exercise
 * is called something else, so the routine reads the same as it does on the source side.
 */
object StarterRoutines {

    /** Fixed ids: re-seeding stays idempotent and edits survive app updates. */
    const val UPPER_A_ID = "hevy-upper-a"
    const val FULL_BODY_1_ID = "full-body-adhd-1"
    const val FULL_BODY_2_ID = "full-body-adhd-2"
    private const val PREFS = "scatterfit_training_prefs"

    /**
     * The pre-per-routine flag. Kept only so an install that seeded before the full body
     * plans existed doesn't get Upper A written back if Sam had deleted it.
     */
    private const val LEGACY_SEEDED_KEY = "starter_routines_seeded_v1"

    /** Import time, fixed so the routine keeps its place in the list. */
    private const val IMPORTED_AT = 1794326400000L // 2026-11-10 16:00 UTC
    private const val FULL_BODY_IMPORTED_AT = 1794657600000L // 2026-11-14 12:00 UTC

    private const val REDUCE_SET_2 = "Reduce weight for set 2 by 20%"
    private const val STAY_IN_RANGE = "Reduce weight to stay in rep range"
    private const val REST_PAUSE = "Rest pause — keep the same weight, go heavy"

    private const val STARTING_LOAD = "Start at 94 kg — adjust to the day"
    private const val EITHER_KIT = "Machine or dumbbells, whichever is free"
    private const val PUMP = "2 quick sets — chase the pump"

    val all: List<Routine> get() = listOf(upperA, fullBody1, fullBody2)

    /**
     * "Full Body ADHD Friendly Workout 1". Three working sets per movement, 8-12 reps,
     * 90 seconds rest; the pushdown is the optional arm finisher at 2 sets.
     */
    val fullBody1: Routine = Routine(
        id = FULL_BODY_1_ID,
        name = "Full Body 1 (ADHD-Friendly)",
        createdAt = FULL_BODY_IMPORTED_AT,
        updatedAt = FULL_BODY_IMPORTED_AT,
        exercises = listOf(
            RoutineExercise(
                exerciseId = "leg-press",
                exerciseName = "Leg Press",
                customName = "Leg Press (Machine)",
                restSeconds = 90,
                sets = List(3) {
                    PlannedSet(weightKg = 94.0, repsMin = 8, repsMax = 12)
                },
                note = "Quads and glutes. Back is supported, so no balance to think about. $STARTING_LOAD."
            ),
            RoutineExercise(
                exerciseId = "machine-chest-press",
                exerciseName = "Machine Chest Press",
                customName = "Seated Chest Press (Machine)",
                restSeconds = 90,
                sets = repeated(3, repsMin = 8, repsMax = 12),
                note = "Pecs, shoulders, triceps. $EITHER_KIT."
            ),
            RoutineExercise(
                exerciseId = "seated-cable-row",
                exerciseName = "Seated Cable Row",
                restSeconds = 90,
                sets = repeated(3, repsMin = 8, repsMax = 12),
                note = "Mid-back and biceps — the posture fixer. Pull to the belly, squeeze the shoulder blades."
            ),
            RoutineExercise(
                exerciseId = "tricep-rope-pushdown",
                exerciseName = "Cable Tricep Rope Pushdown",
                customName = "Cable Tricep Pushdowns",
                restSeconds = 90,
                sets = repeated(2, repsMin = 12, repsMax = 15),
                note = "$PUMP — optional if you're out of dopamine."
            ),
        )
    )

    /**
     * "Full Body ADHD Friendly Workout 2". Same shape; the dumbbell curl closes it out
     * at 2 sets.
     */
    val fullBody2: Routine = Routine(
        id = FULL_BODY_2_ID,
        name = "Full Body 2 (ADHD-Friendly)",
        createdAt = FULL_BODY_IMPORTED_AT + 1000L,
        updatedAt = FULL_BODY_IMPORTED_AT + 1000L,
        exercises = listOf(
            RoutineExercise(
                exerciseId = "seated-leg-curl",
                exerciseName = "Seated Leg Curl (Hamstring)",
                customName = "Seated Leg Curl (Machine)",
                restSeconds = 90,
                sets = repeated(3, repsMin = 8, repsMax = 12),
                note = "Hamstrings. Controlled on the way back, no letting it snap up."
            ),
            RoutineExercise(
                exerciseId = "lat-pulldown",
                exerciseName = "Lat Pulldown",
                customName = "Lat Pulldown (Machine)",
                restSeconds = 90,
                sets = repeated(3, repsMin = 8, repsMax = 12),
                note = "Wide part of the back plus biceps. Bar to the upper chest — never behind the neck."
            ),
            RoutineExercise(
                exerciseId = "seated-dumbbell-shoulder-press",
                exerciseName = "Seated Dumbbell Shoulder Press",
                customName = "Overhead Shoulder Press",
                restSeconds = 90,
                sets = repeated(3, repsMin = 8, repsMax = 12),
                note = "Shoulders and triceps. $EITHER_KIT."
            ),
            RoutineExercise(
                exerciseId = "standing-dumbbell-curl",
                exerciseName = "Standing Dumbbell Curl",
                customName = "Dumbbell Bicep Curls",
                restSeconds = 90,
                sets = repeated(2, repsMin = 12, repsMax = 15),
                note = "$PUMP to finish the arms."
            ),
        )
    )

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

    private fun seededKey(routineId: String) = "starter_routine_seeded_$routineId"

    /**
     * Writes any shipped routine the user has never been offered, then remembers each
     * one individually. Per-routine flags matter: a single latched flag meant routines
     * added later (the full body pair) would never reach an existing install, and
     * re-checking every routine would resurrect one Sam had deliberately deleted.
     */
    fun seedIfNeeded(context: Context, storage: RoutineStorage) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        // Installs that latched the old single flag already have Upper A. Mark it before
        // the loop so it isn't treated as new and written back over a deletion.
        if (prefs.getBoolean(LEGACY_SEEDED_KEY, false) && !prefs.getBoolean(seededKey(UPPER_A_ID), false)) {
            editor.putBoolean(seededKey(UPPER_A_ID), true)
        }

        val existing = storage.loadRoutines().map { it.id }.toSet()
        for (routine in all) {
            if (prefs.getBoolean(seededKey(routine.id), false)) continue
            if (routine.id !in existing) storage.upsert(routine)
            editor.putBoolean(seededKey(routine.id), true)
        }
        editor.apply()
    }
}
