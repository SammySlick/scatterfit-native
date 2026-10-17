package com.scatterbrain.scatterfit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSummaryTest {

    @Test
    fun `PB detection - first-ever session with weight is a PB`() {
        val current = SessionRecord(
            id = "sess-1",
            dayKey = "2026-10-17",
            name = "Push A",
            startedAt = 1000L,
            endedAt = 3000L,
            exercises = listOf(
                LoggedExerciseInSession(
                    exerciseId = "bench-press",
                    exerciseName = "Bench Press",
                    sets = listOf(
                        LoggedSetInSession(weightKg = 60.0, repsMin = 10, done = true),
                        LoggedSetInSession(weightKg = 70.0, repsMin = 8, done = true)
                    )
                )
            )
        )

        val pbs = detectPersonalBests(current, emptyList())
        assertEquals(1, pbs.size)
        assertEquals("Bench Press", pbs[0].exerciseName)
        assertEquals(70.0, pbs[0].newMaxWeightKg, 0.001)
        assertNull(pbs[0].previousMaxWeightKg)
    }

    @Test
    fun `PB detection - new best exceeding prior max is a PB`() {
        val priorHistory = listOf(
            SessionRecord(
                id = "sess-0",
                dayKey = "2026-10-10",
                name = "Old Push",
                startedAt = 500L,
                endedAt = 2500L,
                exercises = listOf(
                    LoggedExerciseInSession(
                        exerciseId = "bench-press",
                        exerciseName = "Bench Press",
                        sets = listOf(
                            LoggedSetInSession(weightKg = 65.0, repsMin = 8, done = true)
                        )
                    )
                )
            )
        )

        val current = SessionRecord(
            id = "sess-1",
            dayKey = "2026-10-17",
            name = "New Push",
            startedAt = 1000L,
            endedAt = 3000L,
            exercises = listOf(
                LoggedExerciseInSession(
                    exerciseId = "bench-press",
                    exerciseName = "Bench Press",
                    sets = listOf(
                        LoggedSetInSession(weightKg = 70.0, repsMin = 8, done = true)
                    )
                )
            )
        )

        val pbs = detectPersonalBests(current, priorHistory)
        assertEquals(1, pbs.size)
        assertEquals("Bench Press", pbs[0].exerciseName)
        assertEquals(70.0, pbs[0].newMaxWeightKg, 0.001)
        assertEquals(65.0, pbs[0].previousMaxWeightKg!!, 0.001)
    }

    @Test
    fun `PB detection - tie or lower weight than prior max is NOT a PB`() {
        val priorHistory = listOf(
            SessionRecord(
                id = "sess-0",
                dayKey = "2026-10-10",
                name = "Old Push",
                startedAt = 500L,
                endedAt = 2500L,
                exercises = listOf(
                    LoggedExerciseInSession(
                        exerciseId = "bench-press",
                        exerciseName = "Bench Press",
                        sets = listOf(
                            LoggedSetInSession(weightKg = 70.0, repsMin = 8, done = true)
                        )
                    )
                )
            )
        )

        // Tie at 70kg -> not a PB
        val currentTie = SessionRecord(
            id = "sess-tie",
            dayKey = "2026-10-17",
            name = "Tie Push",
            startedAt = 1000L,
            exercises = listOf(
                LoggedExerciseInSession(
                    exerciseId = "bench-press",
                    exerciseName = "Bench Press",
                    sets = listOf(
                        LoggedSetInSession(weightKg = 70.0, repsMin = 8, done = true)
                    )
                )
            )
        )
        assertTrue(detectPersonalBests(currentTie, priorHistory).isEmpty())

        // Lower at 60kg -> not a PB
        val currentLower = SessionRecord(
            id = "sess-lower",
            dayKey = "2026-10-17",
            name = "Lower Push",
            startedAt = 1000L,
            exercises = listOf(
                LoggedExerciseInSession(
                    exerciseId = "bench-press",
                    exerciseName = "Bench Press",
                    sets = listOf(
                        LoggedSetInSession(weightKg = 60.0, repsMin = 8, done = true)
                    )
                )
            )
        )
        assertTrue(detectPersonalBests(currentLower, priorHistory).isEmpty())
    }

    @Test
    fun `summary maths - volume sum ignores undone sets and calculates correctly`() {
        val session = SessionRecord(
            id = "sess-vol",
            dayKey = "2026-10-17",
            name = "Chest Day",
            startedAt = 1000L,
            exercises = listOf(
                LoggedExerciseInSession(
                    exerciseId = "bench",
                    exerciseName = "Bench Press",
                    sets = listOf(
                        LoggedSetInSession(weightKg = 60.0, repsMin = 10, done = true), // 600
                        LoggedSetInSession(weightKg = 60.0, repsMin = 10, done = true), // 600
                        LoggedSetInSession(weightKg = 100.0, repsMin = 10, done = false) // undone!
                    )
                ),
                LoggedExerciseInSession(
                    exerciseId = "incline",
                    exerciseName = "Incline Press",
                    sets = listOf(
                        LoggedSetInSession(weightKg = 40.0, repsMin = 12, done = true) // 480
                    )
                )
            )
        )

        val totalVolume = calculateTotalVolumeKg(session)
        assertEquals(1680.0, totalVolume, 0.001)
    }

    @Test
    fun `summary maths - timed-only session volume path and time under tension`() {
        val timedSession = SessionRecord(
            id = "sess-time",
            dayKey = "2026-10-17",
            name = "Core Session",
            startedAt = 1000L,
            exercises = listOf(
                LoggedExerciseInSession(
                    exerciseId = "plank",
                    exerciseName = "Plank",
                    sets = listOf(
                        LoggedSetInSession(timeSeconds = 60, done = true),
                        LoggedSetInSession(timeSeconds = 45, done = true),
                        LoggedSetInSession(timeSeconds = 30, done = false) // undone
                    )
                )
            )
        )

        assertTrue(isTimedOnlySession(timedSession))
        assertEquals(0.0, calculateTotalVolumeKg(timedSession), 0.001)
        assertEquals(105, calculateTotalTimeUnderTensionSeconds(timedSession))
    }

    @Test
    fun `summary maths - average difficulty computes mode`() {
        val session = SessionRecord(
            id = "sess-diff",
            dayKey = "2026-10-17",
            name = "Mixed",
            startedAt = 1000L,
            exercises = listOf(
                LoggedExerciseInSession(
                    exerciseId = "squat",
                    exerciseName = "Squat",
                    sets = listOf(
                        LoggedSetInSession(weightKg = 80.0, repsMin = 5, difficulty = "easy", done = true),
                        LoggedSetInSession(weightKg = 80.0, repsMin = 5, difficulty = "hard", done = true),
                        LoggedSetInSession(weightKg = 80.0, repsMin = 5, difficulty = "hard", done = true)
                    )
                )
            )
        )

        assertEquals("HARD", calculateAverageDifficulty(session))

        // No difficulties set -> null
        val noDiffSession = SessionRecord(
            id = "sess-nodiff",
            dayKey = "2026-10-17",
            name = "No Diff",
            startedAt = 1000L,
            exercises = listOf(
                LoggedExerciseInSession(
                    exerciseId = "squat",
                    exerciseName = "Squat",
                    sets = listOf(
                        LoggedSetInSession(weightKg = 80.0, repsMin = 5, difficulty = null, done = true)
                    )
                )
            )
        )
        assertNull(calculateAverageDifficulty(noDiffSession))
    }

    @Test
    fun `summary maths - duration rounding respects minutes coercion`() {
        val elapsedSec1 = 125L // 2 min 5 sec -> 2 mins
        val dur1 = (elapsedSec1 / 60L).toInt().coerceAtLeast(1)
        assertEquals(2, dur1)

        val elapsedSec2 = 30L // 30 sec -> coerced to 1 min
        val dur2 = (elapsedSec2 / 60L).toInt().coerceAtLeast(1)
        assertEquals(1, dur2)
    }
}
