package com.scatterbrain.scatterfit.data

import com.scatterbrain.scatterfit.sync.SyncStore
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** The 2026-11-23 step undercount: two origins each write one daily step
 *  record for the same day, so a dedupe key of method+start made them collide
 *  and only the last one survived. maxAcrossOrigins then had one candidate and
 *  reported Samsung's count instead of Hume's — 1.5k against the band's 8k. */
class StepsByOriginTest {

    private val zone = ZoneId.of("Europe/London")

    private fun dailySteps(app: String, isoStart: String, count: Long) = HealthRecord(
        app = app,
        start = isoStart,
        end = isoStart,
        data = buildJsonObject { put("count", JsonPrimitive(count)) },
    )

    @Test
    fun `two origins on the same day get different dedupe keys`() {
        val hume = dailySteps(SOURCE_HUME, "2026-11-23T00:00:00.000Z", 8000)
        val samsung = dailySteps(SOURCE_SAMSUNG, "2026-11-23T00:00:00.000Z", 1500)

        assertNotEquals(
            SyncStore.key(RecordMethod.STEPS, hume),
            SyncStore.key(RecordMethod.STEPS, samsung),
        )
    }

    @Test
    fun `same origin same start still collides so a re-read replaces the cached row`() {
        val stale = dailySteps(SOURCE_HUME, "2026-11-23T00:00:00.000Z", 100)
        val fresh = dailySteps(SOURCE_HUME, "2026-11-23T00:00:00.000Z", 8000)

        assertEquals(SyncStore.key(RecordMethod.STEPS, stale), SyncStore.key(RecordMethod.STEPS, fresh))
    }

    @Test
    fun `both origins survive the cache merge so the max can be taken`() {
        // What the engine's merge map does: keyed insert, fresh last.
        val map = LinkedHashMap<String, HealthRecord>()
        for (r in listOf(
            dailySteps(SOURCE_HUME, "2026-11-23T00:00:00.000Z", 8000),
            dailySteps(SOURCE_SAMSUNG, "2026-11-23T00:00:00.000Z", 1500),
        )) {
            map[SyncStore.key(RecordMethod.STEPS, r)] = r
        }

        assertEquals(2, map.size)
    }

    @Test
    fun `debug panel shows both origins with the higher one marked as used`() {
        val rows = stepsByOriginForDay(
            records = listOf(
                dailySteps(SOURCE_HUME, "2026-11-23T00:00:00.000Z", 8000),
                dailySteps(SOURCE_SAMSUNG, "2026-11-23T00:00:00.000Z", 1500),
            ),
            zone = zone,
            dayKey = "2026-11-23",
        )

        assertEquals(2, rows.size)
        assertEquals("Hume", rows[0].name)
        assertEquals(8000L, rows[0].steps)
        assertTrue(rows[0].won)
        assertEquals("Samsung Health", rows[1].name)
        assertTrue(!rows[1].won)
    }

    @Test
    fun `no records for the day means an empty panel not a zero`() {
        val rows = stepsByOriginForDay(
            records = listOf(dailySteps(SOURCE_HUME, "2026-11-20T00:00:00.000Z", 8000)),
            zone = zone,
            dayKey = "2026-11-23",
        )
        assertTrue(rows.isEmpty())
    }
}
