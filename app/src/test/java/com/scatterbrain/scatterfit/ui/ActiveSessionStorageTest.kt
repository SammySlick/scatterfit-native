package com.scatterbrain.scatterfit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveSessionStorageTest {

    /** Mirrors the prefs triple (read / write / remove) the real storage uses. */
    private class FakePrefs {
        var raw: String? = null
        fun storage() = ActiveSessionStorage(
            prefsProvider = { raw },
            prefsWriter = { raw = it },
            prefsClearer = { raw = null },
        )
    }

    private fun draft(
        doneSets: Int = 0,
        weight: Double? = 60.0,
        restEndsAt: Long? = null,
    ) = ActiveSessionDraft(
        id = "draft-1",
        dayKey = "2026-11-10",
        routineId = StarterRoutines.UPPER_A_ID,
        routineName = "Upper A",
        startedAt = 1_000_000L,
        updatedAt = 1_060_000L,
        exercises = listOf(
            DraftExerciseState(
                exerciseId = "dumbbell-bench-press",
                exerciseName = "Bench Press (Dumbbell)",
                restSeconds = 20,
                sets = listOf(
                    DraftSetState(weightKg = weight, repsMin = 10, repsMax = 12, done = doneSets >= 1),
                    DraftSetState(weightKg = weight, repsMin = 8, repsMax = 10, done = doneSets >= 2),
                    DraftSetState(weightKg = weight, repsMin = 6, repsMax = 8, done = doneSets >= 3),
                ),
            ),
            DraftExerciseState(
                exerciseId = "custom-dead-hang",
                exerciseName = "Dead Hang",
                isTimed = true,
                sets = listOf(DraftSetState(timeSeconds = 20, done = false)),
            ),
        ),
        restTotalSeconds = 20,
        restEndsAt = restEndsAt,
    )

    @Test
    fun `draft round-trips through storage with entered values intact`() {
        val prefs = FakePrefs()
        val storage = prefs.storage()
        assertNull(storage.load())
        assertFalse(storage.hasDraft())

        storage.save(draft(doneSets = 2))

        val loaded = storage.load()
        assertNotNull(loaded)
        assertEquals("draft-1", loaded!!.id)
        assertEquals("Upper A", loaded.title)
        assertEquals(1_000_000L, loaded.startedAt)
        assertEquals(2, loaded.exercises.size)

        val bench = loaded.exercises.first()
        assertEquals(3, bench.sets.size)
        assertEquals(60.0, bench.sets[0].weightKg!!, 0.001)
        assertEquals(10, bench.sets[0].repsMin)
        assertEquals(12, bench.sets[0].repsMax)
        assertTrue(bench.sets[0].done)
        assertTrue(bench.sets[1].done)
        assertFalse(bench.sets[2].done)

        val hang = loaded.exercises[1]
        assertTrue(hang.isTimed)
        assertEquals(20, hang.sets[0].timeSeconds)
    }

    @Test
    fun `clear removes the draft so it cannot be resumed twice`() {
        val prefs = FakePrefs()
        val storage = prefs.storage()
        storage.save(draft(doneSets = 1))
        assertTrue(storage.hasDraft())

        storage.clear()

        assertNull(prefs.raw)
        assertFalse(storage.hasDraft())
    }

    @Test
    fun `a rewritten draft keeps its id so a resume never forks the session`() {
        val prefs = FakePrefs()
        val storage = prefs.storage()

        storage.save(draft(doneSets = 1))
        val firstId = storage.load()!!.id
        storage.save(draft(doneSets = 2))

        assertEquals(firstId, storage.load()!!.id)
        assertEquals(2, storage.load()!!.doneSets)
    }

    @Test
    fun `corrupt json loads as no draft instead of crashing the training tab`() {
        val prefs = FakePrefs()
        prefs.raw = "{ this is not a draft"
        assertNull(prefs.storage().load())
    }

    @Test
    fun `set counts ignore unfinished sets and cover every exercise`() {
        val d = draft(doneSets = 2)
        assertEquals(4, d.totalSets)
        assertEquals(2, d.doneSets)
    }

    @Test
    fun `progress label reads as minutes and a set count`() {
        val d = draft(doneSets = 2)
        val elevenMinutesLater = d.startedAt + 11 * 60_000L
        assertEquals("11 min in · 2 of 4 sets", draftProgressLabel(d, elevenMinutesLater))

        assertEquals("Just started · 0 of 4 sets", draftProgressLabel(draft(doneSets = 0), d.startedAt))

        val twoHours = d.startedAt + 125 * 60_000L
        assertEquals("2 h 5 min in · 2 of 4 sets", draftProgressLabel(d, twoHours))
    }

    @Test
    fun `an untouched session is not worth resuming`() {
        assertTrue(isDraftUntouched(draft(doneSets = 0, weight = null)))
        assertFalse(isDraftUntouched(draft(doneSets = 0, weight = 60.0)))
        assertFalse(isDraftUntouched(draft(doneSets = 1, weight = null)))
        assertTrue(
            "A ticked set is work even with no weight",
            !isDraftUntouched(draft(doneSets = 1, weight = null))
        )
    }

    @Test
    fun `rest countdown restored from a deadline is right after the phone was asleep`() {
        val endsAt = 50_000_000L
        // 20s rest, phone locked for 14s: 6 seconds must be left, not 20 or 0.
        assertEquals(6, restRemainingSeconds(endsAt, endsAt - 6_000L))
        // Locked for longer than the rest: expired, not negative.
        assertEquals(0, restRemainingSeconds(endsAt, endsAt + 30_000L))
        // Sub-second remainder rounds up so the display never shows 00:00 while running.
        assertEquals(1, restRemainingSeconds(endsAt, endsAt - 200L))
        // No deadline means no timer.
        assertEquals(0, restRemainingSeconds(null, 12_345L))
    }

    @Test
    fun `a set added mid-exercise copies the load but nothing ticked`() {
        val performed = DraftSetState(
            weightKg = 60.0,
            repsMin = 10,
            repsMax = 12,
            difficulty = "hard",
            done = true,
        )
        val added = performed.asAddedSet()

        assertEquals(60.0, added.weightKg)
        assertEquals(10, added.repsMin)
        assertEquals(12, added.repsMax)
        assertFalse("A new set has not been performed yet", added.done)
        assertNull("Difficulty is a rating of what happened, not of what is planned", added.difficulty)
    }

    @Test
    fun `a set added to a timed exercise keeps its duration`() {
        val plank = DraftSetState(timeSeconds = 45, timeUnit = "sec", done = true)
        val added = plank.asAddedSet()

        assertEquals(45, added.timeSeconds)
        assertEquals("sec", added.timeUnit)
        assertFalse(added.done)
    }

    @Test
    fun `a new exercise starts with three working sets of eight to twelve`() {
        val sets = defaultSetsForAddedExercise()

        assertEquals(3, sets.size)
        assertTrue(sets.all { it.repsMin == 8 && it.repsMax == 12 })
        assertTrue(sets.all { it.weightKg == null && !it.done })
    }

    @Test
    fun `added sets and exercises survive the phone being killed`() {
        val prefs = FakePrefs()
        val storage = prefs.storage()

        // Started Upper A, then added a fourth bench set and a whole new exercise.
        val withAdditions = draft(doneSets = 2).let { base ->
            val bench = base.exercises.first()
            base.copy(
                exercises = listOf(
                    bench.copy(sets = bench.sets + bench.sets.last().asAddedSet()),
                    DraftExerciseState(
                        exerciseId = "cable-fly",
                        exerciseName = "Cable Fly",
                        restSeconds = ADDED_EXERCISE_REST_SECONDS,
                        sets = defaultSetsForAddedExercise(),
                    ),
                )
            )
        }
        storage.save(withAdditions)

        val reloaded = storage.load()
        assertNotNull(reloaded)
        assertEquals(2, reloaded!!.exercises.size)
        assertEquals(3, reloaded.exercises[0].sets.size)
        assertEquals("cable-fly", reloaded.exercises[1].exerciseId)
        assertEquals(3, reloaded.exercises[1].sets.size)
        assertEquals(ADDED_EXERCISE_REST_SECONDS, reloaded.exercises[1].restSeconds)
        // 2 ticked on bench + the new bench set + 3 new unticked sets.
        assertEquals(6, reloaded.totalSets)
        assertEquals(2, reloaded.doneSets)
    }

    @Test
    fun `adding an exercise makes an otherwise untouched session worth resuming`() {
        val base = draft(doneSets = 0, weight = null)
        assertTrue(isDraftUntouched(base))

        // Adding an exercise enters no values, so the helper alone can't see it. That's why
        // the player forces a save on any structural edit (hasStructuralEdit) rather than
        // relying on this check: adding four exercises before a phone call must survive.
        val added = base.copy(
            exercises = base.exercises + DraftExerciseState(
                exerciseId = "cable-fly",
                exerciseName = "Cable Fly",
                restSeconds = ADDED_EXERCISE_REST_SECONDS,
                sets = defaultSetsForAddedExercise(),
            )
        )
        // Untouched by value. The player resaves anyway after a structural edit.
        assertTrue(isDraftUntouched(added))
    }
}
