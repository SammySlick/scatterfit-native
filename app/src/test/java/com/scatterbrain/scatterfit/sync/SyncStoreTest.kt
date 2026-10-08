package com.scatterbrain.scatterfit.sync

import com.scatterbrain.scatterfit.data.HealthRecord
import com.scatterbrain.scatterfit.data.RecordMethod
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SyncStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = SyncStore(tmp.newFolder())

    private fun rec(start: String = "2026-10-05T10:00:00Z", steps: Long = 1234) = HealthRecord(
        app = "com.elink.fittrackhealth.pro",
        start = start,
        data = buildJsonObject {
            put("count", JsonPrimitive(steps))
        },
    )

    @Test
    fun `missing file loads as empty state with null cursor`() {
        val s = store().load(RecordMethod.STEPS)
        assertEquals(null, s.cursorMs)
        assertTrue(s.records.isEmpty())
    }

    @Test
    fun `save then load round-trips records and cursor`() {
        val st = store()
        val recs = listOf(rec(), rec("2026-10-06T10:00:00Z"))
        st.save(RecordMethod.STEPS, 1000L, recs)
        val s = st.load(RecordMethod.STEPS)
        assertEquals(1000L, s.cursorMs)
        assertEquals(2, s.records.size)
        assertEquals(recs, s.records) // HealthRecord is a data class

        // the file itself is gateway wire shape: app at record level
        val onDisk = Json.parseToJsonElement(File(tmp.root, "STEPS.json").readText()).jsonObject
        val firstOnDisk = onDisk["records"]!!.jsonArray[0].jsonObject
        assertEquals("com.elink.fittrackhealth.pro", firstOnDisk["app"]!!.jsonPrimitive.content)
        assertEquals(SyncStore.toJson(recs[0]), firstOnDisk)
    }

    @Test
    fun `corrupt cache degrades to cold start, not a crash`() {
        val dir = tmp.newFolder()
        File(dir, "STEPS.json").writeText("{not json")
        val s = SyncStore(dir).load(RecordMethod.STEPS)
        assertTrue(s.records.isEmpty())
        assertEquals(null, s.cursorMs)
    }

    @Test
    fun `record with no start is dropped on load, not fatal`() {
        val dir = tmp.newFolder()
        val bad = buildJsonObject { put("app", JsonPrimitive("x")) }
        File(dir, "STEPS.json").writeText("""{"cursor":5,"records":[$bad]}""")
        val s = SyncStore(dir).load(RecordMethod.STEPS)
        assertTrue(s.records.isEmpty())
        assertEquals(5L, s.cursorMs)
    }

    @Test
    fun `overwrite save replaces previous records`() {
        val st = store()
        st.save(RecordMethod.WEIGHT, 10L, listOf(rec("2026-10-01T00:00:00Z")))
        st.save(RecordMethod.WEIGHT, 20L, listOf(rec("2026-10-02T00:00:00Z")))
        val s = st.load(RecordMethod.WEIGHT)
        assertEquals(1, s.records.size)
        assertEquals("2026-10-02T00:00:00Z", s.records[0].start)
    }
}
