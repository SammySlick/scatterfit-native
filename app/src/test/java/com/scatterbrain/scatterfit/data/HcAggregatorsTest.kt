package com.scatterbrain.scatterfit.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Acceptance for the daily-aggregation path (the 2026-10-09 pivot:
 *  one aggregateGroupByPeriod call per origin replaces ~720k raw records). */
class HcAggregatorsTest {

    private val ORIGIN = "com.sec.android.app.shealth"
    private val DAY = 24L * 60 * 60 * 1000

    @Test
    fun `daily records synthesizes per-method records via HcTranslate`() {
        val sMs = 1_790_000_000_000L
        val eMs = sMs + DAY
        val recs = HcAggregators.dailyRecords(
            ORIGIN, sMs, eMs,
            methods = HcAggregators.AGGREGATED,
            stepsCount = 8_432L,
            distanceMeters = 6_100.5,
            burnedKcal = 2_150.25,
        ).toMap()

        assertEquals(setOf(RecordMethod.STEPS, RecordMethod.DISTANCE, RecordMethod.TOTAL_CALORIES_BURNED), recs.keys)
        // The synthesized record must be EXACTLY what the raw translator would
        // have produced — the web's daily pipeline can't tell the difference.
        assertEquals(
            HcTranslate.steps(ORIGIN, sMs, eMs, 8_432L),
            recs[RecordMethod.STEPS],
        )
        assertEquals(
            HcTranslate.distance(ORIGIN, sMs, eMs, 6_100.5),
            recs[RecordMethod.DISTANCE],
        )
        assertEquals(
            HcTranslate.totalCaloriesBurned(ORIGIN, sMs, eMs, 2_150.25),
            recs[RecordMethod.TOTAL_CALORIES_BURNED],
        )
    }

    @Test
    fun `daily records skips metrics with no data - day is a gap not a zero`() {
        val recs = HcAggregators.dailyRecords(
            ORIGIN, 0L, DAY,
            methods = HcAggregators.AGGREGATED,
            stepsCount = 500L,
            distanceMeters = null,
            burnedKcal = null,
        ).map { it.first }
        assertEquals(listOf(RecordMethod.STEPS), recs)
    }

    @Test
    fun `daily records respects the requested method set`() {
        val recs = HcAggregators.dailyRecords(
            ORIGIN, 0L, DAY,
            methods = setOf(RecordMethod.STEPS),
            stepsCount = 1L,
            distanceMeters = 100.0, // ignored: DISTANCE not requested
            burnedKcal = 100.0,
        ).map { it.first }
        assertEquals(listOf(RecordMethod.STEPS), recs)
    }

    @Test
    fun `AGGREGATED covers exactly the high-volume types`() {
        assertEquals(
            setOf(RecordMethod.STEPS, RecordMethod.DISTANCE, RecordMethod.TOTAL_CALORIES_BURNED),
            HcAggregators.AGGREGATED,
        )
        // Heart rate must NOT be aggregated: zones and maxHR need samples.
        assertTrue(RecordMethod.HEART_RATE !in HcAggregators.AGGREGATED)
    }
}
