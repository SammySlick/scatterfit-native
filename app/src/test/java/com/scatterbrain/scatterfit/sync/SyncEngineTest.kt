package com.scatterbrain.scatterfit.sync

import com.scatterbrain.scatterfit.data.HealthRecord
import com.scatterbrain.scatterfit.data.RecordMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SyncEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val NOW = 1_790_000_000_000L // fixed clock for determinism

    private fun engine() = SyncEngine(SyncStore(tmp.newFolder()), nowMs = { NOW })

    private fun steps(start: String, count: Long = 100) = HealthRecord(
        app = "com.elink.fittrackhealth.pro",
        start = start,
        data = buildJsonObject { put("count", JsonPrimitive(count)) },
    )

    @Test
    fun `killed sync commits per slice - next sync resumes from last slice cursor`() = runTest {
        val e = engine()
        val dayMs = 24L * 60 * 60 * 1000
        val lookback = SyncEngine.LOOKBACK_MS
        val sliceEnd = NOW - lookback + 3 * dayMs // third daily slice

        // Sync 1: reader commits two slices, then dies (provider wedge / rate limit)
        val e2 = org.junit.Assert.assertFailsWith<Exception> {
            e.sync { methods, from, to, onSlice ->
                onSlice(RecordMethod.STEPS, sliceEnd, listOf(steps("2026-05-15T10:00:00Z")))
                onSlice(RecordMethod.STEPS, sliceEnd + dayMs, listOf(steps("2026-05-16T10:00:00Z")))
                throw IllegalStateException("provider wedged mid-read")
                @Suppress("USELESS_ELABORATED_PARAMETER", "UNREACHABLE_CODE")
                emptyMap<RecordMethod, List<HealthRecord>>()
            }
        }
        // Cache holds the committed slices; cursor is at the last committed slice end
        val after = e.store.load(RecordMethod.STEPS)
        assertEquals(sliceEnd + dayMs, after.cursorMs)
        assertEquals(2, after.records.size)

        // Sync 2: window must resume from cursor - overlap, NOT the full 120-day lookback
        var capturedFrom = 0L
        e.sync { _, from, _, _ ->
            capturedFrom = from
            emptyMap()
        }
        assertEquals(sliceEnd + dayMs - SyncEngine.OVERLAP_MS, capturedFrom)
    }

    @Test
    fun `first sync reads the full lookback window`() = runTest {
        var capturedFrom = 0L
        val e = engine()
        e.sync { _, from, to, _ ->
            capturedFrom = from
            assertEquals(NOW, to)
            mapOf(RecordMethod.STEPS to listOf(steps("2026-10-05T10:00:00Z")))
        }
        assertEquals(NOW - SyncEngine.LOOKBACK_MS - SyncEngine.OVERLAP_MS, capturedFrom)
    }

    @Test
    fun `second sync reads only the overlap behind the cursor`() = runTest {
        val e = engine()
        e.sync { _, _, _, _ -> mapOf(RecordMethod.STEPS to listOf(steps("2026-10-05T10:00:00Z"))) }
        var capturedFrom = 0L
        e.sync { _, from, _, _ ->
            capturedFrom = from
            emptyMap()
        }
        assertEquals(NOW - SyncEngine.OVERLAP_MS, capturedFrom)
    }

    @Test
    fun `overlap re-reads merge without duplicates`() = runTest {
        val e = engine()
        val rec = steps("2026-10-05T10:00:00Z")
        // pass 1 and 2 both return the same record (overlap window)
        e.sync { _, _, _, _ -> mapOf(RecordMethod.STEPS to listOf(rec)) }
        val r2 = e.sync { _, _, _, _ -> mapOf(RecordMethod.STEPS to listOf(rec)) }
        assertEquals(1, r2.merged[RecordMethod.STEPS]!!.size)
    }

    @Test
    fun `edited record in overlap window replaces its old self`() = runTest {
        val e = engine()
        val original = steps("2026-10-05T10:00:00Z", count = 100)
        e.sync { _, _, _, _ -> mapOf(RecordMethod.STEPS to listOf(original)) }

        val edited = steps("2026-10-05T10:00:00Z", count = 250) // same start, new data
        val r = e.sync { _, _, _, _ -> mapOf(RecordMethod.STEPS to listOf(edited)) }
        val out = r.merged[RecordMethod.STEPS]!!
        assertEquals(1, out.size) // fresh read wins over the stale cached row
        assertEquals("250", (out[0].data?.get("count") as? JsonPrimitive)?.content)
    }

    @Test
    fun `extended sleep end replaces the cached truncated session`() = runTest {
        val e = engine()
        val truncated = HealthRecord(app = "a", start = "2026-10-05T23:00:00Z", end = "2026-10-06T02:00:00Z")
        e.sync { _, _, _, _ -> mapOf(RecordMethod.SLEEP_SESSION to listOf(truncated)) }
        val extended = HealthRecord(app = "a", start = "2026-10-05T23:00:00Z", end = "2026-10-06T06:30:00Z")
        val r = e.sync { _, _, _, _ -> mapOf(RecordMethod.SLEEP_SESSION to listOf(extended)) }
        val out = r.merged[RecordMethod.SLEEP_SESSION]!!
        assertEquals(1, out.size)
        assertEquals("2026-10-06T06:30:00Z", out[0].end)
    }

    @Test
    fun `methods without records this pass keep their cache and advance cursor`() = runTest {
        val e = engine()
        e.sync { _, _, _, _ -> mapOf(RecordMethod.STEPS to listOf(steps("2026-10-05T10:00:00Z"))) }
        val r = e.sync { _, _, _, _ -> emptyMap() }
        assertEquals(1, r.merged[RecordMethod.STEPS]!!.size)
        assertEquals(0, r.fetched)
        // cursor still advanced: a third sync reads only the overlap
        var from3 = 0L
        e.sync { _, f, _, _ -> from3 = f; emptyMap() }
        assertEquals(NOW - SyncEngine.OVERLAP_MS, from3)
    }

    @Test
    fun `merged result is complete map across methods`() = runTest {
        val e = engine()
        val r = e.sync { _, _, _, _ ->
            mapOf(
                RecordMethod.STEPS to listOf(steps("2026-10-05T10:00:00Z")),
                RecordMethod.WEIGHT to listOf(
                    HealthRecord(app = "a", start = "2026-10-05T08:00:00Z", data = buildJsonObject { put("inKilograms", JsonPrimitive(82.4)) }),
                ),
            )
        }
        assertEquals(1, r.merged[RecordMethod.STEPS]!!.size)
        assertEquals(1, r.merged[RecordMethod.WEIGHT]!!.size)
    }
}
