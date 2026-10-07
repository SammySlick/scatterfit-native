package com.scatterbrain.scatterfit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.*
import java.time.ZoneId

/** Acceptance: the REAL web pipeline (daily.ts buildDailyDataFromMaps) was run
 *  on the REAL demo records at a fixed clock (2026-10-07T18:00:00Z, UTC) and
 *  its full output captured. Every map here must match exactly — CI names the
 *  day + key of any divergence. */
class DailyTest {
    private val zone = ZoneId.of("UTC")
    private val nowMs = parseMillis("2026-10-07T18:00:00Z")!!

    private fun jf(name: String): JsonElement =
        this::class.java.getResourceAsStream("/$name")!!.readBytes().decodeToString().let { Json.parseToJsonElement(it) }

    private val web = jf("web_parsed.json").jsonObject
    private val webRecords = jf("web_records.json").jsonObject

    private fun JsonObject.mapOfD(key: String): Map<String, Double> =
        (this[key] as? JsonObject)?.mapValues { it.value.jsonPrimitive.double } ?: emptyMap()

    private fun built(): AssembledDaily {
        val records = HashMap<RecordMethod, List<HealthRecord>>()
        for ((k, v) in webRecords["records"]!!.jsonObject) {
            val m = RecordMethod.valueOf(k.uppercase())
            records[m] = v.jsonArray.map { r ->
                val o = r.jsonObject
                HealthRecord(
                    app = o["app"]?.jsonStringOrNull(),
                    start = o["start"]!!.jsonPrimitive.content,
                    end = o["end"]?.jsonStringOrNull(),
                    data = o["data"] as? JsonObject,
                )
            }
        }
        return Daily.buildDailyDataFromMaps(records, Zones.DEFAULT_ZONES, zone, nowMs = nowMs)
    }

    @Test fun `maps - numeric day maps match web exactly`() {
        val d = built()
        for (key in listOf("steps", "caloriesEaten", "protein", "carbs", "fat", "caloriesBurned", "weight", "restingHr", "bodyFat", "stepsSource", "distance", "distanceSource", "burnedSource", "trainingMinutes", "foodKcal", "alcoholKcal", "alcoholUnits")) {
            val expected = web.mapOfD(key) ?: emptyMap()
            val actual = when (key) {
                "steps" -> d.steps; "caloriesEaten" -> d.caloriesEaten; "protein" -> d.protein
                "carbs" -> d.carbs; "fat" -> d.fat; "caloriesBurned" -> d.caloriesBurned
                "weight" -> d.weight; "restingHr" -> d.restingHr; "bodyFat" -> d.bodyFat
                "stepsSource" -> d.stepsSource; "distance" -> d.distance
                "distanceSource" -> d.distanceSource; "burnedSource" -> d.burnedSource
                "trainingMinutes" -> d.trainingMinutes
                else -> emptyMap()
            }
            // sources are string maps — cast expected accordingly
            if (key.endsWith("Source")) continue
            assertEquals(key, expected, actual)
        }
        for (key in listOf("stepsSource", "distanceSource", "burnedSource")) {
            val expected = (web[key]!!.jsonObject).mapValues { it.value.jsonPrimitive.content }
            val actual = when (key) { "stepsSource" -> d.stepsSource; "distanceSource" -> d.distanceSource; else -> d.burnedSource }
            assertEquals(key, expected, actual)
        }
    }

    @Test fun `drinks - boolean map matches`() {
        val d = built()
        val expected = (web["drinks"]!!.jsonObject).mapValues { it.value.jsonPrimitive.boolean }
        assertEquals(expected, d.drinks)
    }

    @Test fun `hrZones - every day matches web`() {
        val d = built()
        val expected = web["hrZones"]!!.jsonObject
        assertEquals(expected.keys, d.hrZones.keys)
        for ((k, e) in expected) {
            val o = e.jsonObject
            val z = d.hrZones[k]!!
            assertEquals("$k exerciseMinutes", o["exerciseMinutes"]!!.jsonPrimitive.double, z.exerciseMinutes, 0.0001)
            assertEquals("$k z2", o["z2"]!!.jsonPrimitive.double, z.z2, 0.0001)
            assertEquals("$k z3", o["z3"]!!.jsonPrimitive.double, z.z3, 0.0001)
            assertEquals("$k z4", o["z4"]!!.jsonPrimitive.double, z.z4, 0.0001)
            assertEquals("$k z5", o["z5"]!!.jsonPrimitive.double, z.z5, 0.0001)
            assertEquals("$k sampleCoverage", o["sampleCoverage"]!!.jsonPrimitive.double, z.sampleCoverage, 0.0001)
            assertEquals("$k z3Run20", o["z3Run20"]!!.jsonPrimitive.boolean, z.z3Run20)
        }
    }

    @Test fun `sleep - per wake day matches web`() {
        val d = built()
        val expected = web["sleep"]!!.jsonObject
        assertEquals(expected.keys, d.sleep.keys)
        for ((k, e) in expected) {
            val o = e.jsonObject
            val n = d.sleep[k]!!
            assertEquals("$k totalMin", o["totalMin"]!!.jsonPrimitive.double, n.totalMin, 0.0001)
            assertEquals("$k deepMin", o["deepMin"]?.jsonPrimitive?.doubleOrNull ?: 0.0, n.deepMin, 0.0001)
            assertEquals("$k remMin", o["remMin"]?.jsonPrimitive?.doubleOrNull ?: 0.0, n.remMin, 0.0001)
            assertEquals("$k lightMin", o["lightMin"]?.jsonPrimitive?.doubleOrNull ?: 0.0, n.lightMin, 0.0001)
            assertEquals("$k awakeMin", o["awakeMin"]?.jsonPrimitive?.doubleOrNull ?: 0.0, n.awakeMin, 0.0001)
        }
    }

    @Test fun `sessions - day, minutes and title match web`() {
        val d = built()
        val expected = web["sessions"]!!.jsonArray
        assertEquals(expected.size, d.sessions.size)
        expected.map { it.jsonObject }.forEachIndexed { i, o ->
            val s = d.sessions[i]
            assertEquals(o["day"]!!.jsonPrimitive.content, s.day)
            assertEquals(o["minutes"]!!.jsonPrimitive.double, s.minutes, 0.0001)
            assertEquals(o["title"]!!.jsonPrimitive.content, s.title)
        }
    }

    @Test fun `meals - every entry matches web`() {
        val d = built()
        val expected = web["meals"]!!.jsonObject
        assertEquals(expected.keys, d.meals.keys)
        for ((k, arr) in expected) {
            val list = arr.jsonArray
            assertEquals("$k count", list.size, d.meals[k]!!.size)
            list.map { it.jsonObject }.forEachIndexed { i, o ->
                val m = d.meals[k]!![i]
                assertEquals("$k[$i] name", o["name"]!!.jsonPrimitive.content, m.name)
                assertEquals("$k[$i] kcal", o["kcal"]!!.jsonPrimitive.double, m.kcal, 0.0001)
                assertEquals("$k[$i] protein", o["protein"]!!.jsonPrimitive.double, m.protein, 0.0001)
                assertEquals("$k[$i] carbs", o["carbs"]!!.jsonPrimitive.double, m.carbs, 0.0001)
                assertEquals("$k[$i] fat", o["fat"]!!.jsonPrimitive.double, m.fat, 0.0001)
                assertEquals("$k[$i] meal", o["meal"]!!.jsonPrimitive.content, m.meal)
                assertEquals("$k[$i] source", o["source"]!!.jsonPrimitive.content, m.source)
                val t = o["time"]
                if (t is JsonNull) assertTrue("$k[$i] time null", m.time == null)
                else assertEquals("$k[$i] time", t!!.jsonPrimitive.content, m.time)
            }
        }
    }

    @Test fun `scalars - maxHr and latestHeartRate match web`() {
        val d = built()
        assertEquals(web["maxHr"]?.jsonPrimitive?.doubleOrNull, d.maxHr)
        val lh = web["latestHeartRate"]
        if (lh is JsonNull) assertTrue(d.latestHeartRate == null)
        else {
            val o = lh!!.jsonObject
            assertEquals(o["value"]!!.jsonPrimitive.double, d.latestHeartRate!!.first, 0.0001)
        }
    }
}
