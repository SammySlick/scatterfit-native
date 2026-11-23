package com.scatterbrain.scatterfit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Acceptance tests for what a set row means.
 *
 * Three things got entangled here and each one was a real bug: the KG box showed the
 * routine's plan instead of what he last lifted, the REPS box edited the prescription
 * instead of recording the work, and volume multiplied the plan back as if it had
 * happened. The rule that keeps them apart is "performed beats planned, and planned is
 * only a fallback for records written before performed reps existed".
 */
class SetEntryTest {

    private fun set(
        weight: Double? = null,
        repsMin: Int? = null,
        repsMax: Int? = null,
        actualReps: Int? = null,
        done: Boolean = true,
    ) = LoggedSetInSession(
        weightKg = weight,
        repsMin = repsMin,
        repsMax = repsMax,
        actualReps = actualReps,
        done = done,
    )

    private fun ex(vararg sets: LoggedSetInSession) =
        LoggedExerciseInSession(exerciseId = "bench", exerciseName = "Bench", sets = sets.toList())

    // ------------------------------------------------- KG prefill

    @Test
    fun `each set starts at the weight actually lifted at that set last time`() {
        val previous = ex(set(80.0, actualReps = 12), set(120.0, actualReps = 8))
        assertEquals(80.0, performedWeightForSet(previous, 0))
        assertEquals(120.0, performedWeightForSet(previous, 1))
    }

    @Test
    fun `the plan no longer overrides what actually happened`() {
        // The routine says 120; he lifted 125 across all three sets.
        val previous = ex(set(125.0, actualReps = 6), set(125.0, actualReps = 6), set(125.0, actualReps = 5))
        assertEquals(125.0, performedWeightForSet(previous, 0))
        assertEquals(125.0, performedWeightForSet(previous, 2))
    }

    @Test
    fun `a set beyond last time's count carries the last lift rather than nothing`() {
        val previous = ex(set(80.0, actualReps = 12), set(120.0, actualReps = 8))
        assertEquals(
            "A fourth set is another one of the same lift, not an unknown",
            120.0, performedWeightForSet(previous, 3)
        )
    }

    @Test
    fun `unticked sets are not history`() {
        val previous = ex(set(80.0, actualReps = 12), set(999.0, actualReps = 1, done = false))
        assertEquals("The set he skipped never happened", 80.0, performedWeightForSet(previous, 0))
        assertEquals(80.0, performedWeightForSet(previous, 1))
    }

    @Test
    fun `a first-ever exercise has nothing to prefill`() {
        assertNull(performedWeightForSet(null, 0))
        assertNull(performedWeightForSet(ex(), 0))
    }

    @Test
    fun `a bodyweight set prefill stays empty instead of borrowing a neighbour's load`() {
        val previous = ex(set(null, actualReps = 15, repsMin = 15))
        assertNull(performedWeightForSet(previous, 0))
    }

    @Test
    fun `the box and the PREV column index done sets the same way`() {
        // He ticked sets 1 and 3, leaving 2 unticked. Both readers must see two done sets,
        // in order, or the number in the box argues with the number beside it.
        val previous = LoggedExerciseInSession(
            exerciseId = "bench",
            exerciseName = "Bench",
            sets = listOf(
                set(80.0, actualReps = 10),
                set(null, actualReps = null, done = false),
                set(90.0, actualReps = 8),
            ),
        )
        assertEquals("80 × 10", formatPreviousSetSummary(previous, 0))
        assertEquals("90 × 8", formatPreviousSetSummary(previous, 1))
        assertEquals(80.0, performedWeightForSet(previous, 0))
        assertEquals(90.0, performedWeightForSet(previous, 1))
    }

    // ------------------------------------------------- REPS are what was done

    @Test
    fun `PREV shows the reps performed, not the range that was asked for`() {
        val previous = ex(set(120.0, repsMin = 8, repsMax = 12, actualReps = 12))
        assertEquals("120 × 12", formatPreviousSetSummary(previous, 0))
    }

    @Test
    fun `an older record with no performed reps still reads as its prescription`() {
        val legacy = ex(set(120.0, repsMin = 8, repsMax = 12))
        assertEquals("120 × 8-12", formatPreviousSetSummary(legacy, 0))
    }

    // ------------------------------------------------- volume

    @Test
    fun `volume multiplies the reps performed`() {
        // Prescribed 8-12, did 12. 120 × 12 = 1,440, not 960 and not an average.
        val record = SessionRecord(
            dayKey = "2026-11-23",
            name = "Push",
            startedAt = 0L,
            exercises = listOf(ex(set(120.0, repsMin = 8, repsMax = 12, actualReps = 12))),
        )
        assertEquals(1_440.0, calculateTotalVolumeKg(record), 0.001)
        assertEquals(1_440.0, com.scatterbrain.scatterfit.core.volumeKg(record), 0.001)
    }

    @Test
    fun `a legacy record still counts its prescription as work`() {
        val record = SessionRecord(
            dayKey = "2026-11-01",
            name = "Push",
            startedAt = 0L,
            exercises = listOf(ex(set(100.0, repsMin = 10, repsMax = 10))),
        )
        assertEquals(1_000.0, calculateTotalVolumeKg(record), 0.001)
    }

    @Test
    fun `an unticked set contributes no volume however much was typed`() {
        val record = SessionRecord(
            dayKey = "2026-11-23",
            name = "Push",
            startedAt = 0L,
            exercises = listOf(ex(set(120.0, actualReps = 12, done = false))),
        )
        assertEquals(0.0, calculateTotalVolumeKg(record), 0.001)
    }

    @Test
    fun `reps typed on a bodyweight set still make it a volume session`() {
        val record = SessionRecord(
            dayKey = "2026-11-23",
            name = "Pull",
            startedAt = 0L,
            exercises = listOf(ex(set(null, actualReps = 9))),
        )
        assertFalse("Reps on a bodyweight set are still work", isTimedOnlySession(record))
    }
}
