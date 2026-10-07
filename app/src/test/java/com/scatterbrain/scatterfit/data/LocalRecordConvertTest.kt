package com.scatterbrain.scatterfit.data

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The converter's contract: demo/source-priority LocalRecords flow through the
 *  SAME parse path as Health Connect records, and the assembled daily data is
 *  identical to the pipeline run on the web's own records (web_records.json). */
class LocalRecordConvertTest {

    private val zone = java.time.ZoneId.of("UTC")
    private val now = java.time.Instant.parse("2026-10-07T18:00:00Z")

    @Test
    fun `scalar map lifts to JsonObject with doubles`() {
        val jo = LocalRecordConvert.toJsonObject(mapOf("count" to 7408, "app" to "com.test", "on" to true, "note" to null))
        assertEquals(7408.0, jo["count"]!!.toString().toDouble(), 0.0)
        assertEquals("com.test", jo["app"]!!.toString().removeSurrounding("\""))
        assertEquals(true, jo["on"]!!.toString() == "true")
        assertNull(jo["note"])
    }

    @Test
    fun `nested map recurses into JsonObject`() {
        val jo = LocalRecordConvert.toJsonObject(mapOf("foo" to mapOf("bar" to 1.5), "list" to listOf(1, 2)))
        assertEquals(1.5, jo["foo"]!!.toString().let { kotlinx.serialization.json.Json.parseToJsonElement(it).jsonObject["bar"]!!.toString().toDouble() }, 0.0)
        assertEquals("[1,2]", jo["list"]!!.toString())
    }

    @Test
    fun `demo month through the converter assembles identical daily data`() {
        val month = makeDemoRecords(now, zone)
        val records = HashMap<RecordMethod, List<HealthRecord>>()
        for ((key, list) in month.records) {
            val m = RecordMethod.entries.first { it.name == key }
            records[m] = LocalRecordConvert.convertAll(m, list)
        }
        val d = Daily.buildDailyDataFromMaps(records, Zones.DEFAULT_ZONES, zone, nowMs = now.toEpochMilli())

        // The strongest checks — spot values the DailyTest pins from the raw
        // records fixture. Same pipeline, same numbers, via the converter.
        assertEquals(7408.0, d.steps["2026-10-07"]!!, 0.01)
        assertEquals(79.67, d.weight["2026-10-07"]!!, 0.01)
        assertEquals(53.0, d.restingHr["2026-10-04"]!!, 0.01) // post-binge RHR spike (web fixture: 53 — the old 61 was from the pre-fix demo notes)
        assertNull(d.trainingMinutes["2026-10-04"]) // rest day — no zone data (web fixture: trainingMinutes empty, no 10-04 hrZones)
        assertEquals(28.0, d.hrZones["2026-10-05"]!!.z3, 0.01) // Tuesday run's z3 minutes survive the trip
        assertEquals(51.0, d.hrZones["2026-10-05"]!!.exerciseMinutes, 0.01)
        assertEquals(5, month.drinks.size) // pour list survived the trip
    }
}
