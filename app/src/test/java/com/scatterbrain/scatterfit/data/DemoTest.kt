package com.scatterbrain.scatterfit.data

import com.scatterbrain.scatterfit.core.dayKeyFromIso
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Demo month parity tests. The expected values are NOT hand-written: they are
 * the output of the real web generator (src/lib/demo.ts) run at a fixed clock
 * (2026-10-07T18:00:00Z, UTC). If Kotlin and TypeScript ever disagree, these
 * fail with exact numbers. The scenario pathologies are visible in the data:
 * the binge weekend (Sep 18-19), the RHR spike the morning after (Sep 19-20),
 * the missed weigh-in (Sep 28), and the Sunday 16.6k walk (Sep 13).
 */
class DemoTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-07T18:00:00Z")

    private fun month() = makeDemoRecords(now, zone)

    @Test fun `deterministic - same clock generates identical months`() {
        val a = month()
        val b = makeDemoRecords(now, zone)
        assertEquals(a.drinks, b.drinks)
        assertEquals(a.keys, b.keys)
        assertEquals(a.records[RecordMethod.WEIGHT.name]!!.map { it.data["weight"] }, b.records[RecordMethod.WEIGHT.name]!!.map { it.data["weight"] })
        assertEquals(a.records[RecordMethod.RESTING_HEART_RATE.name]!!.map { it.data["beatsPerMinute"] }, b.records[RecordMethod.RESTING_HEART_RATE.name]!!.map { it.data["beatsPerMinute"] })
    }

    @Test fun `30 day window with the expected pour list`() {
        val m = month()
        assertEquals(30, m.keys.size)
        val pours = m.drinks.map { Triple(it.day, it.id, it.units) to it.kcal }
        assertEquals(
            listOf(
                Triple("2026-09-18", "stella-artois--pint", 2.6) to 227,
                Triple("2026-09-18", "stella-artois--pint", 2.6) to 227,
                Triple("2026-09-19", "-camden-hells--440ml-can", 2.02) to 176,
                Triple("2026-09-19", "peroni-nastro-azzurro--pint", 2.9) to 252,
                Triple("2026-09-30", "heineken--440ml-can", 1.89) to 165,
            ),
            pours,
        )
    }

    @Test fun `steps per day match the web generator`() {
        val m = month()
        val byDay = mutableMapOf<String, Int>()
        for (r in m.records[RecordMethod.STEPS.name]!!) {
            val day = dayKeyFromIso(r.start, zone)
            byDay[day] = (byDay[day] ?: 0) + (r.data["count"] as Int)
        }
        assertEquals(30, byDay.size)
        assertEquals(
            mapOf(
        "2026-09-08" to 9602,
        "2026-09-09" to 6152,
        "2026-09-10" to 7602,
        "2026-09-11" to 4985,
        "2026-09-12" to 6116,
        "2026-09-13" to 16635,
        "2026-09-14" to 4462,
        "2026-09-15" to 5055,
        "2026-09-16" to 6380,
        "2026-09-17" to 8175,
        "2026-09-18" to 4423,
        "2026-09-19" to 7429,
        "2026-09-20" to 2323,
        "2026-09-21" to 10897,
        "2026-09-22" to 1745,
        "2026-09-23" to 8663,
        "2026-09-24" to 4331,
        "2026-09-25" to 9804,
        "2026-09-26" to 7420,
        "2026-09-27" to 3380,
        "2026-09-28" to 4403,
        "2026-09-29" to 8700,
        "2026-09-30" to 5960,
        "2026-10-01" to 8712,
        "2026-10-02" to 4025,
        "2026-10-03" to 7600,
        "2026-10-04" to 5860,
        "2026-10-05" to 4557,
        "2026-10-06" to 8780,
        "2026-10-07" to 7408
            ),
            byDay,
        )
    }

    @Test fun `weight per day matches - including the missed weigh-in`() {
        val m = month()
        val byDay = mutableMapOf<String, Double>()
        for (r in m.records[RecordMethod.WEIGHT.name]!!) {
            byDay[dayKeyFromIso(r.start, zone)] = r.data["weight"].let { (it as Map<*, *>)["inKilograms"] as Double }
        }
        assertEquals(29, byDay.size) // Sep 28 is the fixture's missed weigh-in
        assertEquals(
            mapOf(
        "2026-09-08" to 80.93,
        "2026-09-09" to 80.72,
        "2026-09-10" to 80.88,
        "2026-09-11" to 80.3,
        "2026-09-12" to 80.7,
        "2026-09-13" to 80.06,
        "2026-09-14" to 80.32,
        "2026-09-15" to 80.76,
        "2026-09-16" to 80.41,
        "2026-09-17" to 80.0,
        "2026-09-18" to 79.69,
        "2026-09-19" to 80.86,
        "2026-09-20" to 80.59,
        "2026-09-21" to 79.55,
        "2026-09-22" to 79.82,
        "2026-09-23" to 79.68,
        "2026-09-24" to 80.43,
        "2026-09-25" to 80.73,
        "2026-09-26" to 81.99,
        "2026-09-27" to 80.54,
        "2026-09-29" to 80.35,
        "2026-09-30" to 79.22,
        "2026-10-01" to 79.86,
        "2026-10-02" to 79.31,
        "2026-10-03" to 79.8,
        "2026-10-04" to 80.14,
        "2026-10-05" to 79.61,
        "2026-10-06" to 79.89,
        "2026-10-07" to 79.67
            ),
            byDay,
        )
    }

    @Test fun `resting heart rate matches - with the post-binge spike`() {
        val m = month()
        val byDay = mutableMapOf<String, Int>()
        for (r in m.records[RecordMethod.RESTING_HEART_RATE.name]!!) {
            byDay[dayKeyFromIso(r.start, zone)] = r.data["beatsPerMinute"] as Int
        }
        assertEquals(
            mapOf(
        "2026-09-08" to 56,
        "2026-09-09" to 54,
        "2026-09-10" to 57,
        "2026-09-11" to 54,
        "2026-09-12" to 55,
        "2026-09-13" to 57,
        "2026-09-14" to 53,
        "2026-09-15" to 54,
        "2026-09-16" to 52,
        "2026-09-17" to 54,
        "2026-09-18" to 52,
        "2026-09-19" to 56,
        "2026-09-20" to 59,
        "2026-09-21" to 55,
        "2026-09-22" to 57,
        "2026-09-23" to 57,
        "2026-09-24" to 56,
        "2026-09-25" to 54,
        "2026-09-26" to 55,
        "2026-09-27" to 53,
        "2026-09-28" to 60,
        "2026-09-29" to 56,
        "2026-09-30" to 56,
        "2026-10-01" to 61,
        "2026-10-02" to 58,
        "2026-10-03" to 59,
        "2026-10-04" to 53,
        "2026-10-05" to 55,
        "2026-10-06" to 53,
        "2026-10-07" to 57
            ),
            byDay,
        )
    }

    @Test fun `sleep minutes per wake day match the web generator`() {
        val m = month()
        val byDay = mutableMapOf<String, Int>()
        for (r in m.records[RecordMethod.SLEEP_SESSION.name]!!) {
            val stages = r.data["stages"] as List<Map<String, Any?>>
            val first = Instant.parse(stages.first()["startTime"] as String)
            val lastT = Instant.parse(stages.last()["endTime"] as String)
            byDay[dayKeyFromIso(r.end, zone)] = ((lastT.epochSecond - first.epochSecond) / 60).toInt()
        }
        assertEquals(29, byDay.size) // Sep 28 is the fixture's missing night
        val expected = mapOf(
        "2026-09-08" to 476,
        "2026-09-09" to 449,
        "2026-09-10" to 432,
        "2026-09-11" to 448,
        "2026-09-12" to 304,
        "2026-09-13" to 438,
        "2026-09-14" to 424,
        "2026-09-15" to 437,
        "2026-09-16" to 416,
        "2026-09-17" to 409,
        "2026-09-18" to 469,
        "2026-09-19" to 309,
        "2026-09-20" to 429,
        "2026-09-21" to 486,
        "2026-09-22" to 472,
        "2026-09-23" to 441,
        "2026-09-24" to 465,
        "2026-09-25" to 438,
        "2026-09-26" to 438,
        "2026-09-27" to 469,
        "2026-09-29" to 434,
        "2026-09-30" to 468,
        "2026-10-01" to 407,
        "2026-10-02" to 407,
        "2026-10-03" to 317,
        "2026-10-04" to 405,
        "2026-10-05" to 475,
        "2026-10-06" to 445,
        "2026-10-07" to 485
        )
        for ((day, expMin) in expected) {
            val got = byDay[day] ?: throw AssertionError("missing sleep day $day")
            // stage tiles carry sub-minute rounding; the web totals from float ms
            assertEquals("sleep $day", expMin.toDouble(), got.toDouble(), 2.0)
        }
    }

    @Test fun `alcohol rollup per day`() {
        val m = month()
        val roll = getDemoAlcohol(m)
        assertEquals(Triple(454.0, 5.2, true), roll["2026-09-18"])
        assertEquals(Triple(428.0, 4.92, true), roll["2026-09-19"])
        assertEquals(Triple(165.0, 1.89, true), roll["2026-09-30"])
        assertEquals(3, roll.size)
    }
}
