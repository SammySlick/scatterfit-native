package com.scatterbrain.scatterfit.core

import com.scatterbrain.scatterfit.ui.LoggedExerciseInSession
import com.scatterbrain.scatterfit.ui.LoggedSetInSession
import com.scatterbrain.scatterfit.ui.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Acceptance tests for the session history maths. These exist because the
 * screens draw these strings verbatim: if the copy is wrong, the screen is
 * wrong, and nobody notices a "1 sets" for a month.
 */
class SessionHistoryTest {

    private fun set(
        weight: Double? = null,
        reps: Int? = null,
        time: Int? = null,
        done: Boolean = true,
    ) = LoggedSetInSession(weightKg = weight, repsMin = reps, timeSeconds = time, done = done)

    private fun ex(id: String, name: String, vararg sets: LoggedSetInSession) =
        LoggedExerciseInSession(exerciseId = id, exerciseName = name, sets = sets.toList())

    private fun session(
        dayKey: String,
        name: String = "Upper A",
        routineId: String? = "routine-upper-a",
        durationMinutes: Int? = 58,
        exercises: List<LoggedExerciseInSession> = emptyList(),
        id: String = dayKey + name,
        startedAt: Long = 1_760_000_000_000L,
    ) = SessionRecord(
        id = id,
        dayKey = dayKey,
        routineId = routineId,
        name = name,
        exercises = exercises,
        durationMinutes = durationMinutes,
        startedAt = startedAt,
        endedAt = startedAt + 3_600_000L,
    )

    private val upperA = session(
        "2026-11-10",
        exercises = listOf(
            ex("bench-db", "Bench Press (DB)",
                set(22.5, 8), set(22.5, 8), set(22.5, 6)),
            ex("curl-machine", "Preacher Curl (Machine)",
                set(25.0, 12), set(25.0, 10)),
        ),
    )

    // ------------------------------------------------------------ grouping

    @Test
    fun `months come newest first and sessions within a month newest first`() {
        val records = listOf(
            session("2026-10-02", name = "Oct early"),
            session("2026-11-10", name = "Nov late"),
            session("2026-11-01", name = "Nov early"),
        )
        val groups = groupRecordsByMonth(records)
        assertEquals(listOf("2026-11", "2026-10"), groups.map { it.key })
        assertEquals(listOf("Nov late", "Nov early"), groups[0].records.map { it.name })
    }

    @Test
    fun `a month with no sessions never appears`() {
        val groups = groupRecordsByMonth(listOf(session("2026-11-10"), session("2026-09-10")))
        assertEquals(listOf("2026-11", "2026-09"), groups.map { it.key })
    }

    @Test
    fun `no records groups to nothing rather than an empty month`() {
        assertTrue(groupRecordsByMonth(emptyList()).isEmpty())
    }

    @Test
    fun `month label is the uppercase section style used elsewhere`() {
        assertEquals("NOVEMBER", monthLabel("2026-11"))
        assertEquals("JANUARY", monthLabel("2027-01"))
    }

    // ------------------------------------------------------------ summary line

    @Test
    fun `summary line reads duration, sets, volume`() {
        assertEquals("58 min · 5 sets · 1,045 kg", sessionSummaryLine(upperA))
    }

    @Test
    fun `a single set reads set not sets`() {
        val one = session("2026-11-10", exercises = listOf(ex("x", "Curl", set(10.0, 10))))
        assertEquals("58 min · 1 set · 100 kg", sessionSummaryLine(one))
    }

    @Test
    fun `zero values are omitted instead of printed as zero`() {
        val noDuration = session("2026-11-10", durationMinutes = null, exercises = listOf(ex("x", "Curl", set(10.0, 10))))
        assertEquals("1 set · 100 kg", sessionSummaryLine(noDuration))
    }

    @Test
    fun `timed only session shows time under tension instead of a volume of zero`() {
        val timed = session("2026-11-10", exercises = listOf(ex("plank", "Plank", set(time = 45), set(time = 30))))
        assertEquals("58 min · 2 sets · 1m 15s TUT", sessionSummaryLine(timed))
    }

    @Test
    fun `a session with nothing logged says so plainly`() {
        assertEquals("No sets logged", sessionSummaryLine(session("2026-11-10", durationMinutes = null)))
    }

    @Test
    fun `skipped sets do not count towards anything`() {
        val skipped = session(
            "2026-11-10",
            exercises = listOf(ex("x", "Curl", set(10.0, 10), set(reps = 10, done = false))),
        )
        assertEquals("58 min · 1 set · 100 kg", sessionSummaryLine(skipped))
    }

    @Test
    fun `volume uses the logged rep count`() {
        // 22.5x8 + 22.5x8 + 22.5x6 = 180 + 180 + 135 = 495; 25x12 + 25x10 = 300 + 250 = 550
        assertEquals(1045.0, volumeKg(upperA), 0.001)
    }

    @Test
    fun `counts`() {
        assertEquals(5, doneSetCount(upperA))
        assertEquals(2, doneExerciseCount(upperA))
    }

    // ------------------------------------------------------------ muscle groups

    private val muscles = mapOf(
        "bench-db" to "Chest",
        "curl-machine" to "Arms",
    )

    @Test
    fun `muscle groups sum done sets and put other last`() {
        val withCustom = session(
            "2026-11-10",
            exercises = listOf(
                ex("bench-db", "Bench Press (DB)", set(22.5, 8), set(22.5, 8)),
                ex("curl-machine", "Preacher Curl (Machine)", set(25.0, 10)),
                ex("dead-hang", "Dead Hang", set(time = 30)),
                ex("dead-hang-2", "Dead Hang 2", set(time = 30)),
            ),
        )
        val groups = setsByMuscleGroup(withCustom) { muscles[it] }
        assertEquals(listOf("chest" to 2, "arms" to 1, "other" to 2), groups)
        assertEquals("other", groups.last().first)
    }

    @Test
    fun `muscle line is title cased and single spaced`() {
        assertEquals("Chest 9 · Arms 12", muscleGroupLine(listOf("chest" to 9, "arms" to 12)))
    }

    @Test
    fun `unknown exercise id lands in other rather than being dropped`() {
        val groups = setsByMuscleGroup(upperA) { null }
        assertEquals(listOf("other" to 5), groups)
    }

    // ------------------------------------------------------------ deltas

    @Test
    fun `previous record is the most recent earlier session of the same routine`() {
        val older = session("2026-11-01", id = "a")
        val olderStill = session("2026-10-20", id = "b")
        val otherRoutine = session("2026-11-05", id = "c", routineId = "routine-legs")
        val found = previousRecordFor(upperA, listOf(older, olderStill, otherRoutine))
        assertEquals("a", found?.id)
    }

    @Test
    fun `a one off session has nothing to compare against`() {
        val oneOff = session("2026-11-10", routineId = null)
        assertNull(previousRecordFor(oneOff, listOf(session("2026-11-01"))))
    }

    @Test
    fun `first time a routine is done there is no previous record`() {
        assertNull(previousRecordFor(upperA, listOf(upperA)))
    }

    @Test
    fun `heavier top set is up by the difference`() {
        val previous = session("2026-11-01", exercises = listOf(ex("bench-db", "Bench", set(20.0, 8))))
        val now = session("2026-11-10", exercises = listOf(ex("bench-db", "Bench", set(22.5, 8))))
        assertEquals(LoadDelta.Up(2.5), exerciseLoadDelta(now, previous))
        assertEquals("↑ 2.5 kg", loadDeltaLabel(LoadDelta.Up(2.5)))
    }

    @Test
    fun `same weight is held and never framed as failure`() {
        val previous = session("2026-11-01", exercises = listOf(ex("bench-db", "Bench", set(22.5, 8))))
        val now = session("2026-11-10", exercises = listOf(ex("bench-db", "Bench", set(22.5, 6))))
        assertEquals(LoadDelta.Held, exerciseLoadDelta(now, previous))
        assertEquals("held", loadDeltaLabel(LoadDelta.Held))
    }

    @Test
    fun `floating point noise reads as held not a 0_1kg drop`() {
        val previous = session("2026-11-01", exercises = listOf(ex("bench-db", "Bench", set(22.5, 8))))
        val now = session("2026-11-10", exercises = listOf(ex("bench-db", "Bench", set(22.5 - 1e-12, 8))))
        assertEquals(LoadDelta.Held, exerciseLoadDelta(now, previous))
    }

    @Test
    fun `lighter top set is down and neutral`() {
        val previous = session("2026-11-01", exercises = listOf(ex("bench-db", "Bench", set(25.0, 8))))
        val now = session("2026-11-10", exercises = listOf(ex("bench-db", "Bench", set(22.5, 8))))
        assertEquals(LoadDelta.Down(2.5), exerciseLoadDelta(now, previous))
        assertEquals("↓ 2.5 kg", loadDeltaLabel(LoadDelta.Down(2.5)))
    }

    @Test
    fun `no previous means no delta at all rather than a held badge`() {
        assertNull(exerciseLoadDelta(upperA, null))
        assertNull(loadDeltaLabel(null))
    }

    @Test
    fun `per exercise deltas compare like with like`() {
        val previous = session(
            "2026-11-01",
            exercises = listOf(
                ex("bench-db", "Bench", set(20.0, 8)),
                ex("curl-machine", "Curl", set(25.0, 10)),
            ),
        )
        val deltas = exerciseDeltas(upperA, previous)
        assertEquals(LoadDelta.Up(2.5), deltas["bench-db"])
        assertEquals(LoadDelta.Held, deltas["curl-machine"])
    }

    // ------------------------------------------------------------ weeks

    @Test
    fun `this week means the calendar week, not a trailing seven days`() {
        // Mon 9 Nov 2026 is the week start. Wed 11th is in it, Sun 8th is not.
        val monday = "2026-11-09"
        val records = listOf(
            session("2026-11-09", name = "Mon"),
            session("2026-11-11", name = "Wed"),
            session("2026-11-08", name = "Previous Sunday"),
            session("2026-11-16", name = "Next Monday"),
        )
        val totals = weeklyTotals(records, monday)
        assertEquals(2, totals.sessionCount)
    }

    @Test
    fun `week summary line reads count, volume, PBs and drops the quiet parts`() {
        assertEquals("4 sessions · 12,480 kg · 2 PBs", weeklySummaryLine(WeekTotals(4, 12_480.0, 2)))
        assertEquals("4 sessions · 12,480 kg", weeklySummaryLine(WeekTotals(4, 12_480.0, 0)))
        assertEquals("No sessions this week", weeklySummaryLine(WeekTotals(0, 0.0, 0)))
    }

    @Test
    fun `a PB needs history behind it or the first session ever claims one`() {
        val only = session("2026-11-10", exercises = listOf(ex("bench-db", "Bench", set(22.5, 8))))
        assertEquals(0, weeklyTotals(listOf(only), "2026-11-09").pbCount)
    }

    // ------------------------------------------------------------ formatting

    @Test
    fun `volume is grouped and drops a pointless decimal`() {
        assertEquals("8,420 kg", formatVolumeKg(8_420.0))
        assertEquals("1,045 kg", formatVolumeKg(1_045.0))
        assertEquals("12,480.5 kg", formatVolumeKg(12_480.5))
    }

    @Test
    fun `weight never prints a trailing point zero`() {
        assertEquals("22 kg", formatWeightKg(22.0))
        assertEquals("22.5 kg", formatWeightKg(22.5))
        assertEquals("140 kg", formatWeightKg(140.0))
    }

    @Test
    fun `set time and duration formats`() {
        assertEquals("0:45", formatSetTime(45))
        assertEquals("1:05", formatSetTime(65))
        assertEquals("8m 40s", formatDuration(520))
        assertEquals("8m", formatDuration(480))
    }
}
