package com.scatterbrain.scatterfit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

/** Derived RHR fills gaps where no source writes resting HR (Samsung Health
 *  doesn't share RHR with Health Connect). Source wins; derived (5th
 *  percentile, >=500-sample gate) only fills holes. */
class DerivedRhrTest {
    private val zone = ZoneId.of("UTC")

    private fun hrRecord(startIso: String, samples: String): HealthRecord =
        HealthRecord(app = "test", start = startIso, end = null,
            data = kotlinx.serialization.json.Json.parseToJsonElement(samples).jsonObject)

    private fun daySamples(day: String, count: Int): List<Long> {
        val startMs = parseMillis("${day}T00:00:00Z")!!
        val step = 86_400_000L / count
        return List(count) { startMs + it * step }
    }

    private fun samplesJson(times: List<Long>, bpm: (Int) -> Double): String =
        "[" + times.mapIndexed { i, t -> """{"time":"${java.time.Instant.ofEpochMilli(t)}","beatsPerMinute":${bpm(i)}}""" }.joinToString(",") + "]"

    @Test fun `derived rhr fills a day with no source resting hr`() {
        // 600 samples across the day, 50-70 bpm range: 5th percentile ~50.x
        val times = daySamples("2026-10-06", 600)
        val rec = hrRecord("2026-10-06T00:00:00Z", samplesJson(times) { i -> 50.0 + (i % 21) })
        val d = Daily.buildDailyData(
            steps = emptyList(), nutrition = emptyList(), burned = emptyList(),
            weight = emptyList(), restingHr = emptyList(), sleep = emptyList(),
            exercise = emptyList(), bodyFat = emptyList(),
            heartRate = listOf(rec), zones = Zones.DEFAULT_ZONES, zone = zone,
        )
        val rhr = d.restingHr["2026-10-06"]
        assertEquals(true, rhr != null && rhr >= 50.0 && rhr <= 51.5)
    }

    @Test fun `source resting hr wins over derived`() {
        val times = daySamples("2026-10-06", 600)
        val hr = hrRecord("2026-10-06T00:00:00Z", samplesJson(times) { i -> 50.0 + (i % 21) })
        val source = HealthRecord(app = "src", start = "2026-10-06T06:00:00Z", end = null,
            data = kotlinx.serialization.json.Json.parseToJsonElement("""{"beatsPerMinute":62.0}""").jsonObject)
        val d = Daily.buildDailyData(
            steps = emptyList(), nutrition = emptyList(), burned = emptyList(),
            weight = emptyList(), restingHr = listOf(source), sleep = emptyList(),
            exercise = emptyList(), bodyFat = emptyList(),
            heartRate = listOf(hr), zones = Zones.DEFAULT_ZONES, zone = zone,
        )
        assertEquals(62.0, d.restingHr["2026-10-06"]!!, 0.001)
    }

    @Test fun `sparse day stays a gap`() {
        // 40 samples << 500 gate -> no derived RHR, no source -> NO DATA
        val times = daySamples("2026-10-06", 40)
        val hr = hrRecord("2026-10-06T00:00:00Z", samplesJson(times) { i -> 55.0 })
        val d = Daily.buildDailyData(
            steps = emptyList(), nutrition = emptyList(), burned = emptyList(),
            weight = emptyList(), restingHr = emptyList(), sleep = emptyList(),
            exercise = emptyList(), bodyFat = emptyList(),
            heartRate = listOf(hr), zones = Zones.DEFAULT_ZONES, zone = zone,
        )
        assertNull(d.restingHr["2026-10-06"])
    }
}
