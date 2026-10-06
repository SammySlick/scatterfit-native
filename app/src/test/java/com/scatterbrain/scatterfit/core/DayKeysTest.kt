package com.scatterbrain.scatterfit.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class DayKeysTest {

    private val london = ZoneId.of("Europe/London")
    private val ny = ZoneId.of("America/New_York")

    // ---- web vitest cases (daykeys.test.ts) ----

    @Test fun `weekKeysFor returns the FULL calendar week Mon-Sun even mid-week`() {
        // Friday 2026-10-02 -> the whole Mon 28 Sep..Sun 4 Oct week
        assertEquals(
            listOf("2026-09-28", "2026-09-29", "2026-09-30", "2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04"),
            weekKeysFor("2026-10-02"),
        )
    }

    @Test fun `weekKeysEndingAt truncates at the anchor week-to-date`() {
        assertEquals(
            listOf("2026-09-28", "2026-09-29", "2026-09-30", "2026-10-01", "2026-10-02"),
            weekKeysEndingAt("2026-10-02", "monday"),
        )
    }

    @Test fun `the two week helpers agree on the days they share`() {
        val full = weekKeysFor("2026-10-02")
        val toDate = weekKeysEndingAt("2026-10-02", "monday")
        assertEquals(full.take(5), toDate)
    }

    @Test fun `previousWeekKeys anchors cleanly across a month edge`() {
        assertEquals(
            listOf("2026-09-21", "2026-09-22", "2026-09-23", "2026-09-24", "2026-09-25", "2026-09-26", "2026-09-27"),
            previousWeekKeys("monday", "2026-10-01"),
        )
    }

    @Test fun `dayBefore handles month and leap-year edges`() {
        assertEquals("2026-02-28", dayBefore("2026-03-01"))
        assertEquals("2028-02-29", dayBefore("2028-03-01")) // 2028 is a leap year
    }

    // ---- port-specific acceptance (Foundations: day-keying trap) ----

    @Test fun `dayKey converts UTC instants to LOCAL days never raw Z`() {
        // 2023-10-02T01:00Z is Sunday Oct 1 in New York but Monday Oct 2 in London.
        val ts = 1696208400000L // 2023-10-02T01:00:00Z
        assertEquals("2023-10-02", dayKey(ts, london))
        assertEquals("2023-10-01", dayKey(ts, ny))
    }

    @Test fun `sleep crossing midnight belongs to the START day`() {
        // A sleep session starting 23:30 local on Oct 1 keys to Oct 1 (the night
        // it began), and a session starting 00:35 local on Oct 2 keys to Oct 2 —
        // even though BOTH have a UTC date of Oct 1. Only the local zone decides.
        val beforeMidnight = 1790893800000L // 2026-10-01T22:30Z = 23:30 BST Oct 1
        val afterMidnight  = 1790897700000L // 2026-10-01T23:35Z = 00:35 BST Oct 2
        assertEquals("2026-10-01", dayKey(beforeMidnight, london))
        assertEquals("2026-10-02", dayKey(afterMidnight, london))
    }

    @Test fun `dayBefore survives the UK DST spring-forward`() {
        // 2026-03-29 is the UK spring-forward day: 23h long. LocalDate is immune.
        assertEquals("2026-03-29", dayBefore("2026-03-30"))
        // ...and the autumn back, 2026-10-25 (25h day), same guarantee:
        assertEquals("2026-10-25", dayBefore("2026-10-26"))
    }

    @Test fun `addDaysKey crosses month boundaries both directions`() {
        assertEquals("2026-10-01", addDaysKey("2026-09-30", 1))
        assertEquals("2026-09-30", addDaysKey("2026-10-01", -1))
        assertEquals("2028-03-01", addDaysKey("2028-02-28", 1)) // leap year
    }

    @Test fun `lastNDayKeys is ordered oldest-first and honours endKey`() {
        assertEquals(
            listOf("2026-09-28", "2026-09-29", "2026-09-30"),
            lastNDayKeys(3, "2026-09-30", london),
        )
    }

    @Test fun `weekKeysEndingAt rolling mode is always 7 days`() {
        assertEquals(7, weekKeysEndingAt("2026-10-02", "rolling").size)
    }
}
