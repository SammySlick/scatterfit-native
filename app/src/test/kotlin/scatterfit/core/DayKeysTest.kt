package scatterfit.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Ports the web vitest date cases. THE trap: 2026-10-03T23:30 in Europe/London
 * is a different UTC day in Tokyo. If these fail, every sleep number is wrong.
 */
class DayKeysTest {
    private val london = ZoneId.of("Europe/London")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun at(utc: String) = Instant.parse(utc)

    @Test fun `dayKey is local, never UTC`() {
        // 2026-10-03T23:30Z = Oct 3 23:30 London (BST) but Oct 4 08:30 Tokyo
        assertEquals("2026-10-03", DayKeys.dayKey(at("2026-10-03T23:30:00Z"), london))
        assertEquals("2026-10-04", DayKeys.dayKey(at("2026-10-03T23:30:00Z"), tokyo))
    }

    @Test fun `dayKey uses correct months (the web bug we fixed)`() {
        assertEquals("2026-10-03", DayKeys.dayKey(at("2026-10-03T12:00:00Z"), ZoneId.of("UTC")))
    }

    @Test fun `lastNDayKeys is consecutive and ends on the end day`() {
        val keys = DayKeys.lastNDayKeys(7, ZoneId.of("UTC"), at("2026-10-04T15:00:00Z"))
        assertEquals(listOf("2026-09-28", "2026-09-29", "2026-09-30",
            "2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04"), keys)
        assertEquals(7, keys.size)
        val week = keys.map { DayKeys.mondayOffset(it) }
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6), week)
    }

    @Test fun `lastNDayKeys with explicit endKey is clock-independent`() {
        val keys = DayKeys.lastNDayKeys(3, ZoneId.of("UTC"), endKey = "2026-10-04")
        assertEquals(listOf("2026-10-02", "2026-10-03", "2026-10-04"), keys)
    }

    @Test fun `monday weeks: currentWeekKeys is Mon to today`() {
        val week = DayKeys.currentWeekKeys(rolling = false, london, at("2026-10-04T18:00:00Z"))
        assertEquals(listOf("2026-09-28", "2026-09-29", "2026-09-30",
            "2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04"), week)
    }

    @Test fun `monday weeks: currentWeekKeys is partial mid-week`() {
        val week = DayKeys.currentWeekKeys(rolling = false, london, at("2026-09-30T18:00:00Z"))
        assertEquals(listOf("2026-09-28", "2026-09-29", "2026-09-30"), week)
    }

    @Test fun `rolling weeks: currentWeekKeys is the last 7 days`() {
        val week = DayKeys.currentWeekKeys(rolling = true, london, at("2026-10-04T18:00:00Z"))
        assertEquals(7, week.size)
        assertEquals("2026-10-04", week.last())
        assertEquals("2026-09-28", week.first())
    }

    @Test fun `previousWeekKeys monday: full previous week`() {
        val prev = DayKeys.previousWeekKeys(rolling = false, london, at("2026-10-04T18:00:00Z"))
        assertEquals(listOf("2026-09-21", "2026-09-22", "2026-09-23",
            "2026-09-24", "2026-09-25", "2026-09-26", "2026-09-27"), prev)
    }

    @Test fun `previousWeekKeys rolling: days 8-14 back`() {
        val prev = DayKeys.previousWeekKeys(rolling = true, london, at("2026-10-04T18:00:00Z"))
        assertEquals("2026-09-21", prev.first())
        assertEquals("2026-09-27", prev.last())
    }

    @Test fun `weekStartIndex honours both modes`() {
        val keys = DayKeys.lastNDayKeys(14, ZoneId.of("UTC"), at("2026-10-04T15:00:00Z"))
        assertEquals(7, DayKeys.weekStartIndex(keys, 13, rolling = false))
        assertEquals(7, DayKeys.weekStartIndex(keys, 13, rolling = true))
        assertEquals(7, DayKeys.weekStartIndex(keys, 8, rolling = false))
    }
}
