package com.scatterbrain.scatterfit.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/** The readers' acceptance test: for every record type the web consumes, take
 *  a REAL record from web_records.json, reconstruct the primitives the Health
 *  Connect SDK would hand us, run HcTranslate, and require the emitted record
 *  to be verbatim-identical to the fixture. Pins the whole wire shape: field
 *  names, ISO millis format, stage/meal name maps, int-vs-double. */
class HcTranslateTest {

    private val fixture: Map<String, List<JsonObject>> = run {
        val root = Json.parseToJsonElement(
            javaClass.getResourceAsStream("/web_records.json")!!.bufferedReader().readText()
        ).jsonObject["records"]!!.jsonObject
        root.mapValues { (_, v) -> v.jsonArray.map { it.jsonObject } }
    }

    private fun String.ms() = Instant.parse(this).toEpochMilli()

    /** Same envelope the converter emits (app at record level — the parse's
     *  source-priority/weight/workout logic reads it there). */
    private fun envelope(r: HealthRecord): JsonObject = buildJsonObject {
        if (r.app != null) put("app", r.app)
        put("start", r.start)
        if (r.end != null) put("end", r.end)
        if (r.data != null) put("data", r.data)
    }

    private fun pick(type: String, i: Int = 0): JsonObject =
        fixture.getValue(type)[i]

    // ---- per-type round trips ----

    @Test
    fun steps() {
        val f = pick("steps")
        val got = HcTranslate.steps(f["app"]!!.jsonPrimitive.content, f["start"]!!.jsonPrimitive.content.ms(),
            f["end"]!!.jsonPrimitive.content.ms(), f["data"]!!.jsonObject["count"]!!.jsonPrimitive.long)
        assertEquals(f, envelope(got))
    }

    @Test
    fun stepsMultiple() { // sweep every step record, not just one
        for (f in fixture.getValue("steps")) {
            val got = HcTranslate.steps(f["app"]!!.jsonPrimitive.content, f["start"]!!.jsonPrimitive.content.ms(),
                f["end"]!!.jsonPrimitive.content.ms(), f["data"]!!.jsonObject["count"]!!.jsonPrimitive.long)
            assertEquals(f, envelope(got))
        }
    }

    @Test
    fun distance() {
        for (f in fixture.getValue("distance")) {
            val got = HcTranslate.distance(f["app"]!!.jsonPrimitive.content, f["start"]!!.jsonPrimitive.content.ms(),
                f["end"]!!.jsonPrimitive.content.ms(),
                f["data"]!!.jsonObject["distance"]!!.jsonObject["inMeters"]!!.jsonPrimitive.double)
            assertEquals(f, envelope(got))
        }
    }

    @Test
    fun totalCaloriesBurned() {
        for (f in fixture.getValue("totalCaloriesBurned")) {
            val got = HcTranslate.totalCaloriesBurned(f["app"]!!.jsonPrimitive.content, f["start"]!!.jsonPrimitive.content.ms(),
                f["end"]!!.jsonPrimitive.content.ms(),
                f["data"]!!.jsonObject["energy"]!!.jsonObject["inKilocalories"]!!.jsonPrimitive.double)
            assertEquals(f, envelope(got))
        }
    }

    @Test
    fun weight() {
        for (f in fixture.getValue("weight")) {
            val got = HcTranslate.weight(f["app"]!!.jsonPrimitive.content, f["start"]!!.jsonPrimitive.content.ms(),
                f["data"]!!.jsonObject["weight"]!!.jsonObject["inKilograms"]!!.jsonPrimitive.double)
            assertEquals(f, envelope(got))
        }
    }

    @Test
    fun restingHeartRate() {
        for (f in fixture.getValue("restingHeartRate")) {
            val got = HcTranslate.restingHeartRate(f["app"]!!.jsonPrimitive.content, f["start"]!!.jsonPrimitive.content.ms(),
                f["data"]!!.jsonObject["beatsPerMinute"]!!.jsonPrimitive.long)
            assertEquals(f, envelope(got))
        }
    }

    @Test
    fun bodyFat() {
        for (f in fixture.getValue("bodyFat")) {
            val got = HcTranslate.bodyFat(f["app"]!!.jsonPrimitive.content, f["start"]!!.jsonPrimitive.content.ms(),
                f["data"]!!.jsonObject["percentage"]!!.jsonPrimitive.double)
            assertEquals(f, envelope(got))
        }
    }

    @Test
    fun sleepSession() {
        for (f in fixture.getValue("sleepSession")) {
            val d = f["data"]!!.jsonObject
            val stages = d["stages"]!!.jsonArray.map { s ->
                val o = s.jsonObject
                HcTranslate.StageInput(o["startTime"]!!.jsonPrimitive.content.ms(),
                    o["endTime"]!!.jsonPrimitive.content.ms(),
                    STAGE_BY_NAME.getValue(o["stage"]!!.jsonPrimitive.content))
            }
            val got = HcTranslate.sleepSession(f["app"]!!.jsonPrimitive.content, f["start"]!!.jsonPrimitive.content.ms(),
                f["end"]!!.jsonPrimitive.content.ms(), d["title"]?.jsonPrimitive?.content, stages)
            assertEquals(f, envelope(got))
        }
    }

    @Test
    fun exerciseSession() {
        for (f in fixture.getValue("exerciseSession")) {
            val d = f["data"]!!.jsonObject
            val got = HcTranslate.exerciseSession(f["app"]!!.jsonPrimitive.content, f["start"]!!.jsonPrimitive.content.ms(),
                f["end"]!!.jsonPrimitive.content.ms(), d["title"]?.jsonPrimitive?.content,
                d["exerciseType"]!!.jsonPrimitive.int)
            assertEquals(f, envelope(got))
        }
    }

    @Test
    fun heartRate() {
        for (f in fixture.getValue("heartRate")) {
            val d = f["data"]!!.jsonObject
            val samples = d["samples"]!!.jsonArray.map { s ->
                val o = s.jsonObject
                HcTranslate.SampleInput(o["time"]!!.jsonPrimitive.content.ms(), o["beatsPerMinute"]!!.jsonPrimitive.long)
            }
            val got = HcTranslate.heartRate(f["app"]!!.jsonPrimitive.content, f["start"]!!.jsonPrimitive.content.ms(),
                f["end"]!!.jsonPrimitive.content.ms(), samples)
            assertEquals(f, envelope(got))
        }
    }

    @Test
    fun nutrition() {
        for (f in fixture.getValue("nutrition")) {
            val d = f["data"]!!.jsonObject
            fun inKcals(path: String) = d[path]?.jsonObject?.get("inKilocalories")?.jsonPrimitive?.double
            fun inGrams(path: String) = d[path]?.jsonObject?.get("inGrams")?.jsonPrimitive?.double
            val got = HcTranslate.nutrition(f["app"]!!.jsonPrimitive.content, f["start"]!!.jsonPrimitive.content.ms(),
                f["end"]!!.jsonPrimitive.content.ms(), d["name"]?.jsonPrimitive?.content,
                MEAL_BY_NAME.getValue(d["mealType"]!!.jsonPrimitive.content),
                inKcals("energy"), inGrams("protein"), inGrams("carbohydrates"), inGrams("fat"))
            assertEquals(f, envelope(got))
        }
    }

    /** BMR never appears in the fixture (the web computes BMR from settings) —
     *  contract test against the shape we define: Power in watts. */
    @Test
    fun basalMetabolicRate() {
        val got = HcTranslate.basalMetabolicRate("com.wear", 1788657600000L, 1.66)
        assertEquals(
            buildJsonObject {
                put("app", "com.wear")
                put("start", "2026-09-06T01:20:00.000Z")
                put("end", "2026-09-06T01:20:00.000Z")
                put("data", buildJsonObject {
                    put("basalMetabolicRate", buildJsonObject { put("inWatts", 1.66) })
                })
            },
            envelope(got),
        )
    }

    // ---- stage/meal name maps, pinned to connect-client 1.1.0 constants ----

    private val STAGE_BY_NAME = mapOf(
        "UNKNOWN" to 0, "AWAKE" to 1, "SLEEPING" to 2, "OUT_OF_BED" to 3,
        "LIGHT" to 4, "DEEP" to 5, "REM" to 6, "AWAKE_IN_BED" to 7,
    )

    private val MEAL_BY_NAME = mapOf(
        "unknown" to 0, "breakfast" to 1, "lunch" to 2, "dinner" to 3, "snack" to 4,
    )

    @Test
    fun stageNamesRoundTrip() {
        for ((name, int) in STAGE_BY_NAME) assertEquals(name, HcTranslate.stageName(int))
    }

    @Test
    fun mealNamesRoundTrip() {
        for ((name, int) in MEAL_BY_NAME) assertEquals(name, HcTranslate.mealName(int))
    }
}
