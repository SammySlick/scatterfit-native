package com.scatterbrain.scatterfit.data

import com.scatterbrain.scatterfit.core.Daily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

/** Acceptance for the real metric sources: series() and latest() over assembled days. */
class MetricSourcesTest {
    private val zone: ZoneId = ZoneId.of("Europe/London")
    private val nowMs: Long = 1760000000000L

    private fun key(daysAgo: Int): String = Daily.daysAgoKey(daysAgo, zone, nowMs)

    private fun source(steps: Map<String, Double>): StoreDailySource {
        val ad = AssembledDaily(steps = steps, zone = zone)
        return StoreDailySource(ad, { a, k -> a.steps[k] }, zone, nowMs)
    }

    @Test
    fun `series returns values in day order with null for gaps`() {
        val s = source(mapOf(key(0) to 9000.0, key(2) to 7000.0))
        val series = s.series(listOf(key(0), key(1), key(2)))
        assertEquals(9000.0, series[0].value)
        assertNull(series[1].value)
        assertEquals(7000.0, series[2].value)
    }

    @Test
    fun `latest walks back past missing days`() {
        val s = source(mapOf(key(0) to 9000.0, key(3) to 7000.0))
        assertEquals(9000.0, s.latest()!!.value)
    }

    @Test
    fun `latest falls back to the newest day that has data`() {
        val s = source(mapOf(key(3) to 7000.0))
        assertEquals(7000.0, s.latest()!!.value)
        assertEquals(key(3), s.latest()!!.dayKey)
    }

    @Test
    fun `empty cache means no latest`() {
        assertNull(source(emptyMap()).latest())
    }
}
