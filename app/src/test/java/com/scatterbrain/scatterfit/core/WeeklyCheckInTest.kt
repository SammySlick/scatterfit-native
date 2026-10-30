package com.scatterbrain.scatterfit.core

import com.scatterbrain.scatterfit.data.WeeklyCheckInStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyCheckInTest {

    @Test
    fun testAverageOfDividesByNonNullCount() {
        assertEquals(3.0, averageOf(listOf(4.0, null, 2.0, null))!!, 1e-6)
    }

    @Test
    fun testAverageOfAllNulls() {
        assertNull(averageOf(listOf<Double?>(null, null)))
    }

    @Test
    fun testCircularMeanMinutesAcrossMidnight() {
        assertEquals(0, circularMeanMinutes(listOf(23 * 60 + 50, 10)))
    }

    @Test
    fun testCircularMeanMinutesStandard() {
        assertEquals(7 * 60 + 40, circularMeanMinutes(listOf(7 * 60 + 30, 7 * 60 + 50)))
    }

    @Test
    fun testCircularMeanMinutesEmpty() {
        assertNull(circularMeanMinutes(emptyList()))
    }

    @Test
    fun testCountYes() {
        assertEquals(2, countYes(listOf(true, false, null, true)))
    }

    @Test
    fun testParseScaleValue() {
        assertEquals(3, parseScaleValue("3"))
        assertNull(parseScaleValue("0"))
        assertNull(parseScaleValue("6"))
        assertNull(parseScaleValue(null))
        assertNull(parseScaleValue("abc"))
    }

    @Test
    fun testFormatAverageScale15() {
        assertEquals("3.7", formatAverage(CheckInScale.SCALE_1_5, listOf("4", "4", "3")))
    }

    @Test
    fun testFormatAverageScale15Empty() {
        assertEquals("—", formatAverage(CheckInScale.SCALE_1_5, emptyList()))
    }

    @Test
    fun testFormatAverageYesNo() {
        assertEquals("2 of 3", formatAverage(CheckInScale.YES_NO, listOf("true", "false", "true")))
    }

    @Test
    fun testFormatAverageTextReturnsNull() {
        assertNull(formatAverage(CheckInScale.TEXT, listOf("anything")))
    }

    @Test
    fun testCheckInRowsUniqueAndCount() {
        val ids = CHECK_IN_ROWS.map { it.id }
        assertEquals(ids.toSet().size, ids.size)
        assertEquals(18, CHECK_IN_ROWS.size)
    }

    @Test
    fun testStorageRoundTripWithoutAndroid() {
        val storage = WeeklyCheckInStorage(context = null)
        storage.setDayValue("2026-10-26", "2026-10-27", "energy", "4")
        val reloaded = storage.load("2026-10-26")
        val raw = reloaded.perDay["2026-10-27"]?.get("energy")
        assertEquals(4, parseScaleValue(raw))

        storage.setDayValue("2026-10-26", "2026-10-27", "energy", null)
        val afterRemove = storage.load("2026-10-26")
        assertTrue(afterRemove.perDay["2026-10-27"].isNullOrEmpty())

        storage.setWeeklyValue("2026-10-26", "circ_umbilicus", "82.5")
        assertEquals("82.5", storage.load("2026-10-26").weekly["circ_umbilicus"])
        storage.setWeeklyValue("2026-10-26", "circ_umbilicus", null)
        assertNull(storage.load("2026-10-26").weekly["circ_umbilicus"])
    }
}
