package com.scatterbrain.scatterfit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovingAverageTest {

    @Test
    fun `size is preserved`() {
        val v = listOf<Double?>(1.0, null, 3.0)
        assertEquals(3, MovingAverage.rolling(v, window = 3, minPoints = 1).size)
    }

    @Test
    fun `dense series averages the trailing seven days`() {
        val v = (1..7).map { it.toDouble() }
        val avg = MovingAverage.rolling(v, window = 7, minPoints = 3)
        // day 0..1 are below the min-point gate, day 2 onward report
        assertNull(avg[0])
        assertNull(avg[1])
        assertEquals(2.0, avg[2]!!, 1e-9)      // (1+2+3)/3 = 2
        assertEquals(4.0, avg[6]!!, 1e-9)      // (1+2+…+7)/7 = 4
    }

    @Test
    fun `a missing day is skipped not treated as zero`() {
        // 3 real readings of 10 around two holes: average must be 10, not 30/7.
        val v = listOf<Double?>(10.0, null, null, 10.0, null, 10.0, null)
        val avg = MovingAverage.rolling(v, window = 7, minPoints = 3)
        assertEquals(10.0, avg[5]!!, 1e-9)
    }

    @Test
    fun `zero is a real reading and must count`() {
        val v = listOf<Double?>(0.0, 0.0, 0.0)
        val avg = MovingAverage.rolling(v, window = 7, minPoints = 3)
        assertEquals(0.0, avg[2]!!, 1e-9)
    }

    @Test
    fun `too few readings in the window reports nothing`() {
        val v = listOf<Double?>(94.3, null, null, null, null, null, 94.1)
        val avg = MovingAverage.rolling(v, window = 7, minPoints = 3)
        assertTrue(avg.all { it == null })
    }

    @Test
    fun `two readings far apart do not masquerade as an average`() {
        val v = listOf<Double?>(90.0, null, null, null, null, null, 100.0)
        assertNull(MovingAverage.rolling(v, window = 7)[6])
        // ...but with minPoints lowered the caller gets the figure it asked for
        assertEquals(95.0, MovingAverage.rolling(v, window = 7, minPoints = 2)[6]!!, 1e-9)
    }

    @Test
    fun `window slides so old readings drop out`() {
        // Eight days: day 7's window must be days 1..7, excluding day 0.
        val v = listOf(0.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0, 7.0)
        val avg = MovingAverage.rolling(v, window = 7, minPoints = 3)
        assertEquals(7.0, avg[7]!!, 1e-9)
    }

    @Test
    fun `all null stays null and never throws`() {
        val v = listOf<Double?>(null, null, null, null)
        val avg = MovingAverage.rolling(v)
        assertTrue(avg.all { it == null })
    }

    @Test
    fun `empty input is empty output`() {
        assertTrue(MovingAverage.rolling(emptyList()).isEmpty())
    }

    @Test
    fun `window of one reports the reading itself`() {
        val v = listOf<Double?>(93.0, null, 91.0)
        val avg = MovingAverage.rolling(v, window = 1, minPoints = 1)
        assertEquals(93.0, avg[0]!!, 1e-9)
        assertNull(avg[1])
        assertEquals(91.0, avg[2]!!, 1e-9)
    }

    @Test
    fun `minPoints is clamped to the window`() {
        val v = listOf<Double?>(2.0, 4.0)
        val avg = MovingAverage.rolling(v, window = 2, minPoints = 99)
        assertEquals(3.0, avg[1]!!, 1e-9)
    }

    @Test
    fun `gap rule from the acceptance tests still holds`() {
        // A spike on one day must not make the average jump: 7-day smoothing exists for this.
        val v = listOf<Double?>(94.0, 94.0, 94.0, 96.0, 94.0, 94.0, 94.0)
        val avg = MovingAverage.rolling(v, window = 7, minPoints = 3)
        val latest = avg[6]!!
        assertTrue("average $latest should stay near the trend", latest < 94.5)
    }
}
