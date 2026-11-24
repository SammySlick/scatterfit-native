package com.scatterbrain.scatterfit.core

import com.scatterbrain.scatterfit.ui.LoggedExerciseInSession
import com.scatterbrain.scatterfit.ui.LoggedSetInSession
import com.scatterbrain.scatterfit.ui.SessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressReportTest {

    private fun session(
        day: String,
        startedAt: Long,
        exerciseId: String = "squat",
        topKg: Double = 100.0,
        reps: Int = 5,
        sets: Int = 3,
    ) = SessionRecord(
        dayKey = day,
        name = "Session",
        startedAt = startedAt,
        endedAt = startedAt + 3_600_000,
        exercises = listOf(
            LoggedExerciseInSession(
                exerciseId = exerciseId,
                exerciseName = exerciseId,
                sets = List(sets) { LoggedSetInSession(weightKg = topKg, actualReps = reps, done = true) },
            ),
        ),
    )

    // --- trendWindow ------------------------------------------------------

    @Test
    fun `a gap is not a zero`() {
        // Three weigh-ins across a 30-day window. The average must be their
        // mean, not the total divided by 30.
        val w = trendWindow(
            mapOf("2026-11-01" to 96.0, "2026-11-10" to 95.0, "2026-11-20" to 94.0),
            todayKey = "2026-11-24",
            windowDays = 30,
        )
        assertEquals(3, w.readings)
        assertEquals(95.0, w.average!!, 0.001)
        assertEquals(-2.0, w.change!!, 0.001)
    }

    @Test
    fun `days outside the window are excluded at both ends`() {
        val w = trendWindow(
            mapOf(
                "2026-10-01" to 100.0, // before
                "2026-11-05" to 96.0,
                "2026-11-24" to 94.0,
                "2026-11-25" to 93.0, // after today
            ),
            todayKey = "2026-11-24",
            windowDays = 30,
        )
        assertEquals(2, w.readings)
        assertEquals(96.0, w.firstValue!!, 0.001)
        assertEquals(94.0, w.lastValue!!, 0.001)
    }

    @Test
    fun `window boundary is inclusive of the first day`() {
        // 30-day window ending 2026-11-24 starts 2026-10-26.
        val w = trendWindow(
            mapOf("2026-10-26" to 96.0, "2026-11-24" to 94.0),
            todayKey = "2026-11-24",
            windowDays = 30,
        )
        assertEquals(2, w.readings)
    }

    @Test
    fun `one reading cannot report a change`() {
        val w = trendWindow(mapOf("2026-11-20" to 94.0), "2026-11-24", 30)
        assertEquals(1, w.readings)
        assertNull(w.change)
        assertTrue(!w.hasEnoughToReport)
    }

    @Test
    fun `empty and malformed input does not throw`() {
        assertEquals(0, trendWindow(emptyMap(), "2026-11-24", 30).readings)
        assertEquals(0, trendWindow(mapOf("nonsense" to 1.0), "2026-11-24", 30).readings)
        assertEquals(0, trendWindow(mapOf("2026-11-24" to 1.0), "2026-11-24", 0).readings)
        assertEquals(0, trendWindow(mapOf("2026-11-24" to Double.NaN), "2026-11-24", 30).readings)
    }

    // --- direction --------------------------------------------------------

    @Test
    fun `a change inside the dead band is steady`() {
        assertEquals(TrendDirection.Steady, trendDirection(0.1, 0.2))
        assertEquals(TrendDirection.Steady, trendDirection(-0.1, 0.2))
        assertEquals(TrendDirection.Steady, trendDirection(null, 0.2))
        assertEquals(TrendDirection.Steady, trendDirection(0.2, 0.2))
    }

    @Test
    fun `a change outside the dead band has a direction`() {
        assertEquals(TrendDirection.Down, trendDirection(-0.3, 0.2))
        assertEquals(TrendDirection.Up, trendDirection(0.3, 0.2))
    }

    @Test
    fun `direction is not a judgement - up is not bad`() {
        // Guard against anyone wiring colour to direction without knowing the
        // goal. Up must be a neutral fact; the enum deliberately has no Good/Bad.
        val values = TrendDirection.entries.map { it.name }
        assertTrue(values.none { it.contains("Good") || it.contains("Bad") })
    }

    // --- sentences --------------------------------------------------------

    @Test
    fun `sentence says down when weight falls past the band`() {
        val w = trendWindow(mapOf("2026-11-01" to 96.0, "2026-11-20" to 94.2), "2026-11-24", 30)
        assertEquals("Weight down 1.8kg over 2 readings", trendSentence("Weight", w, "kg"))
    }

    @Test
    fun `sentence says steady inside the band`() {
        val w = trendWindow(mapOf("2026-11-01" to 94.0, "2026-11-20" to 94.1), "2026-11-24", 30)
        assertEquals("Weight steady over 2 readings", trendSentence("Weight", w, "kg"))
    }

    @Test
    fun `sentence is null when there is not enough to say`() {
        val w = trendWindow(mapOf("2026-11-20" to 94.0), "2026-11-24", 30)
        assertNull(trendSentence("Weight", w, "kg"))
    }

    // --- daysLogged -------------------------------------------------------

    @Test
    fun `days logged counts readings inside the window only`() {
        val values = mapOf(
            "2026-10-01" to 1.0, // outside
            "2026-11-01" to 1.0,
            "2026-11-02" to 1.0,
            "2026-11-02" to 1.0,
        )
        assertEquals(2, daysLogged(values, "2026-11-24", 30))
    }

    // --- trainingWindow ---------------------------------------------------

    @Test
    fun `training volume only counts sessions inside the window`() {
        val t = trainingWindow(
            listOf(
                session("2026-10-01", 1_000L, topKg = 100.0, reps = 5, sets = 3), // outside
                session("2026-11-20", 2_000L, topKg = 100.0, reps = 5, sets = 3),
            ),
            todayKey = "2026-11-24",
            windowDays = 30,
        )
        assertEquals(1, t.sessions)
        assertEquals(1500.0, t.totalVolumeKg, 0.001) // 100 * 5 * 3
        assertEquals(3, t.totalSets)
    }

    @Test
    fun `first ever top set is a PB and a repeat at the same weight is not - but beating it is`() {
        val t = trainingWindow(
            listOf(
                session("2026-11-10", 1_000L, topKg = 100.0),
                session("2026-11-17", 2_000L, topKg = 100.0), // matched, not beaten
                session("2026-11-20", 3_000L, topKg = 105.0), // beaten
            ),
            todayKey = "2026-11-24",
            windowDays = 30,
        )
        // 100 was the first ever top set, so it counts; matching it does not;
        // 105 beats it, so it counts. Two PBs, not three.
        assertEquals(2, t.pbs)
    }

    @Test
    fun `a PB set before the window still stops the window claiming it again`() {
        val t = trainingWindow(
            listOf(
                session("2026-09-01", 1_000L, topKg = 120.0),  // outside window
                session("2026-11-20", 2_000L, topKg = 110.0),  // below the old best
            ),
            todayKey = "2026-11-24",
            windowDays = 30,
        )
        assertEquals(1, t.sessions)
        assertEquals(0, t.pbs)
    }

    @Test
    fun `unticked sets are not volume`() {
        val record = SessionRecord(
            dayKey = "2026-11-20",
            name = "S",
            startedAt = 1_000L,
            exercises = listOf(
                LoggedExerciseInSession(
                    exerciseId = "squat",
                    exerciseName = "Squat",
                    sets = listOf(
                        LoggedSetInSession(weightKg = 100.0, actualReps = 5, done = true),
                        LoggedSetInSession(weightKg = 100.0, actualReps = 5, done = false),
                    ),
                ),
            ),
        )
        val t = trainingWindow(listOf(record), "2026-11-24", 30)
        assertEquals(1, t.totalSets)
        assertEquals(500.0, t.totalVolumeKg, 0.001)
    }
}
