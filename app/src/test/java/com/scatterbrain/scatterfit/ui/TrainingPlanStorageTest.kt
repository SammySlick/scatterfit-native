package com.scatterbrain.scatterfit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingPlanStorageTest {

    @Test
    fun `weekDaysFor produces Monday-first week spanning 7 days`() {
        // Wednesday in mid-month
        val week = weekDaysFor("2026-10-14")
        assertEquals(7, week.size)
        assertEquals("2026-10-12", week.first()) // Monday
        assertEquals("2026-10-18", week.last())  // Sunday
    }

    @Test
    fun `weekDaysFor correctly spans across a month boundary`() {
        // Thursday 1st October 2026 -> Monday is 28th September 2026, Sunday is 4th October 2026
        val week = weekDaysFor("2026-10-01")
        assertEquals(7, week.size)
        assertEquals("2026-09-28", week[0]) // Monday (September)
        assertEquals("2026-09-29", week[1])
        assertEquals("2026-09-30", week[2])
        assertEquals("2026-10-01", week[3]) // Thursday (October)
        assertEquals("2026-10-02", week[4])
        assertEquals("2026-10-03", week[5])
        assertEquals("2026-10-04", week[6]) // Sunday (October)
    }

    @Test
    fun `weekDaysFor correctly spans across a year boundary`() {
        // Friday 1st January 2027 -> Monday is 28th December 2026, Sunday is 3rd January 2027
        val week = weekDaysFor("2027-01-01")
        assertEquals(7, week.size)
        assertEquals("2026-12-28", week[0]) // Monday (2026)
        assertEquals("2026-12-29", week[1])
        assertEquals("2026-12-30", week[2])
        assertEquals("2026-12-31", week[3])
        assertEquals("2027-01-01", week[4]) // Friday (2027)
        assertEquals("2027-01-02", week[5])
        assertEquals("2027-01-03", week[6]) // Sunday (2027)
    }

    @Test
    fun `seven-row week view maths matches weekDaysFor anchor exactly`() {
        val anchor = "2026-10-15"
        val rows = weekDaysFor(anchor)
        assertEquals(7, rows.size)
        assertEquals(
            listOf(
                "2026-10-12",
                "2026-10-13",
                "2026-10-14",
                "2026-10-15",
                "2026-10-16",
                "2026-10-17",
                "2026-10-18"
            ),
            rows
        )
    }

    @Test
    fun `PlannedSessionsStorage round-trip, upsert-overwrite, and remove`() {
        var rawJson: String? = null
        val storage = PlannedSessionsStorage(
            prefsProvider = { rawJson },
            prefsWriter = { rawJson = it }
        )

        assertTrue(storage.load().isEmpty())

        // Upsert Monday routine
        val entry1 = PlannedSessionDay(dayKey = "2026-10-12", routineId = "r-upper")
        storage.upsert(entry1)
        assertEquals(1, storage.load().size)
        assertEquals("r-upper", storage.get("2026-10-12")?.routineId)

        // Upsert Tuesday standalone session
        val entry2 = PlannedSessionDay(dayKey = "2026-10-13", sessionName = "Running", note = "45 mins")
        storage.upsert(entry2)
        assertEquals(2, storage.load().size)
        assertEquals("Running", storage.get("2026-10-13")?.sessionName)

        // Overwrite Monday with a different routine (one entry per day max)
        val entry1Overwritten = PlannedSessionDay(dayKey = "2026-10-12", routineId = "r-push")
        storage.upsert(entry1Overwritten)
        assertEquals(2, storage.load().size)
        assertEquals("r-push", storage.get("2026-10-12")?.routineId)

        // Remove Tuesday
        storage.remove("2026-10-13")
        assertEquals(1, storage.load().size)
        assertNull(storage.get("2026-10-13"))
        assertNotNull(storage.get("2026-10-12"))
    }

    @Test
    fun `LoggedSessionsStorage add, loadByDay, and remove`() {
        var rawJson: String? = null
        val storage = LoggedSessionsStorage(
            prefsProvider = { rawJson },
            prefsWriter = { rawJson = it }
        )

        assertTrue(storage.loadAll().isEmpty())

        val session1 = LoggedSession(id = "s1", dayKey = "2026-10-12", name = "Upper Body", durationMinutes = 50)
        val session2 = LoggedSession(id = "s2", dayKey = "2026-10-12", name = "Tennis", durationMinutes = 60)
        val session3 = LoggedSession(id = "s3", dayKey = "2026-10-13", name = "Running", durationMinutes = 30)

        storage.add(session1)
        storage.add(session2)
        storage.add(session3)

        val all = storage.loadAll()
        assertEquals(3, all.size)
        assertEquals("s3", all[0].id)

        val day12Sessions = storage.loadByDay("2026-10-12")
        assertEquals(2, day12Sessions.size)
        assertTrue(day12Sessions.any { it.name == "Upper Body" })
        assertTrue(day12Sessions.any { it.name == "Tennis" })

        storage.remove("s2")
        assertEquals(2, storage.loadAll().size)
        assertEquals(1, storage.loadByDay("2026-10-12").size)
    }

    @Test
    fun `Log it creates LoggedSession with routine name`() {
        var rawJson: String? = null
        val storage = LoggedSessionsStorage(
            prefsProvider = { rawJson },
            prefsWriter = { rawJson = it }
        )

        val routine = Routine(id = "r1", name = "Hypertrophy Push")
        val plannedDay = "2026-10-14"

        // Simulating "Log it" action
        val logged = LoggedSession(dayKey = plannedDay, name = routine.name)
        storage.add(logged)

        val daySessions = storage.loadByDay(plannedDay)
        assertEquals(1, daySessions.size)
        assertEquals("Hypertrophy Push", daySessions.first().name)
        assertEquals(plannedDay, daySessions.first().dayKey)
    }

    @Test
    fun `PlannedSessionsStorage getPlanForDate agrees between training row, tomorrow card and planner`() {
        var rawJson: String? = null
        val storage = PlannedSessionsStorage(
            prefsProvider = { rawJson },
            prefsWriter = { rawJson = it }
        )

        val testDate = java.time.LocalDate.parse("2026-10-18")
        val routines = listOf(Routine(id = "r-pull", name = "Pull Hypertrophy"))

        // Planner writes a routine
        storage.upsert(PlannedSessionDay(dayKey = "2026-10-18", routineId = "r-pull"))

        // Both today's training row and tomorrow card read through getPlanForDate
        val planForToday = storage.getPlanForDate(testDate, routines)
        val planForTomorrow = storage.getPlanForDate(testDate, routines)

        assertEquals("Pull Hypertrophy", planForToday)
        assertEquals(planForToday, planForTomorrow)

        // Planner writes a standalone session name
        storage.upsert(PlannedSessionDay(dayKey = "2026-10-18", sessionName = "Sunday Long Run"))
        val planRun = storage.getPlanForDate(testDate, routines)
        assertEquals("Sunday Long Run", planRun)

        // Unplanned date returns null (Rest day)
        val unplannedDate = java.time.LocalDate.parse("2026-10-19")
        assertNull(storage.getPlanForDate(unplannedDate, routines))
    }
}
