package com.scatterbrain.scatterfit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayRowsTest {

    private val keys = (1..7).map { "2026-11-%02d".format(it) }

    @Test
    fun `every day gets a row, including days with no reading`() {
        val values = listOf<Double?>(94.0, null, null, 93.5, null, null, 93.2)
        val rows = DayRows.build(keys, values)
        assertEquals(7, rows.size)
        assertEquals("2026-11-07", rows.first().dayKey)
        assertEquals(93.2, rows[0].value!!, 1e-9)
        assertNull(rows[1].value)   // 6 Nov: skipped day still listed
        assertNull(rows[2].value)
        assertEquals(93.5, rows[3].value!!, 1e-9)
    }

    @Test
    fun `newest day is first`() {
        val rows = DayRows.build(keys, keys.map { 90.0 })
        assertEquals(keys.last(), rows.first().dayKey)
        assertEquals(keys.last(), rows.map { it.dayKey }.max())
    }

    @Test
    fun `a week of nothing still renders seven empty rows`() {
        val rows = DayRows.build(keys, keys.map { null })
        assertEquals(7, rows.size)
        assertEquals(0, DayRows.loggedCount(rows))
    }

    @Test
    fun `window longer than the requested count takes the newest`() {
        val long = (1..30).map { "2026-10-%02d".format(it) }
        val rows = DayRows.build(long, long.map { 1.0 }, count = 7)
        assertEquals(7, rows.size)
        assertEquals(long.last(), rows.first().dayKey)
        assertEquals(long[23], rows.last().dayKey)
    }

    @Test
    fun `short window returns what it has, not padded rows`() {
        val rows = DayRows.build(keys.take(2), listOf(1.0, 2.0))
        assertEquals(2, rows.size)
    }

    @Test
    fun `empty window is empty`() {
        assertEquals(0, DayRows.build(emptyList(), emptyList()).size)
    }

    @Test
    fun `averages ride along and may be null where the average is not yet ready`() {
        val avg = listOf<Double?>(null, null, 94.1, 94.0, 93.9, 93.8, 93.6)
        val rows = DayRows.build(keys, keys.map { 93.0 }, avg)
        assertEquals(93.6, rows[0].average!!, 1e-9)
        assertNull(rows[6].average)
    }

    @Test
    fun `mismatched lengths do not throw`() {
        val rows = DayRows.build(keys, listOf(1.0))
        assertEquals(7, rows.size)
        assertNull(rows[0].value)
    }
}
