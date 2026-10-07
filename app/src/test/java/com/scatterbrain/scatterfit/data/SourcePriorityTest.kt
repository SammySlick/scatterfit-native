package com.scatterbrain.scatterfit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class SourcePriorityTest {
    private val zone = ZoneId.of("UTC")

    private fun rec(app: String, start: String, data: Map<String, Any?>) =
        LocalRecord(start, start, app, data)

    private fun stepsRec(app: String, day: String, count: Int) =
        rec(app, "${day}T10:00:00Z", mapOf("count" to count))

    private fun weightRec(app: String, day: String, hour: Int, kg: Double) =
        rec(app, "${day}T${hour.toString().padStart(2, '0')}:00:00Z", mapOf("weight" to mapOf("inKilograms" to kg)))

    @Test fun `continuous metrics take max across eligible origins`() {
        val recs = listOf(stepsRec(SOURCE_HUME, "2026-10-01", 8000), stepsRec(SOURCE_SAMSUNG, "2026-10-01", 12000))
        val out = maxAcrossOrigins(recs, "steps", SourcePriority(), zone) { it.data["count"] as Double? }
        assertEquals(12000.0 to SOURCE_SAMSUNG, out["2026-10-01"])
    }

    @Test fun `excluded sources never contribute - even when they are the max`() {
        val recs = listOf(stepsRec(SOURCE_HUME, "2026-10-01", 8000), stepsRec(SOURCE_MYZONE, "2026-10-01", 50000))
        val out = maxAcrossOrigins(recs, "steps", SourcePriority(), zone) { it.data["count"] as Double? }
        assertEquals(8000.0 to SOURCE_HUME, out["2026-10-01"])
    }

    @Test fun `all origins excluded means no value for that day`() {
        val recs = listOf(stepsRec(SOURCE_MYZONE, "2026-10-01", 50000))
        val out = maxAcrossOrigins(recs, "steps", SourcePriority(), zone) { it.data["count"] as Double? }
        assertNull(out["2026-10-01"])
    }

    @Test fun `identical windows from one origin are deduped before summing`() {
        val dup = stepsRec(SOURCE_HUME, "2026-10-01", 5000)
        val recs = listOf(dup, dup.copy(data = mapOf("count" to 5000)))
        val out = maxAcrossOrigins(recs, "steps", SourcePriority(), zone) { it.data["count"] as Double? }
        assertEquals(5000.0, out["2026-10-01"]!!.first, 1e-9)
    }

    @Test fun `weight prefers the truth source even when another app is later`() {
        val recs = listOf(
            weightRec(SOURCE_SAMSUNG, "2026-10-01", 9, 81.5),
            weightRec(SOURCE_HUME, "2026-10-01", 8, 80.2), // earlier but the scale
        )
        val out = maxAcrossOrigins(recs, "weight", SourcePriority(), zone) { (it.data["weight"] as Map<*, *>)["inKilograms"] as Double? }
        assertEquals(80.2 to SOURCE_HUME, out["2026-10-01"])
    }

    @Test fun `weight falls back to the latest eligible app when truth is absent`() {
        val recs = listOf(
            weightRec(SOURCE_SAMSUNG, "2026-10-01", 8, 81.0),
            weightRec(SOURCE_GOOGLE_FIT, "2026-10-01", 9, 80.5),
        )
        val out = maxAcrossOrigins(recs, "weight", SourcePriority(), zone) { (it.data["weight"] as Map<*, *>)["inKilograms"] as Double? }
        assertEquals(80.5 to SOURCE_GOOGLE_FIT, out["2026-10-01"])
    }

    @Test fun `per-metric override replaces the tier for that metric only`() {
        val pri = SourcePriority(overrides = mapOf("steps" to mapOf(SOURCE_MYZONE to SourceTier.SECONDARY)))
        val recs = listOf(stepsRec(SOURCE_HUME, "2026-10-01", 8000), stepsRec(SOURCE_MYZONE, "2026-10-01", 9000))
        val steps = maxAcrossOrigins(recs, "steps", pri, zone) { it.data["count"] as Double? }
        assertEquals(9000.0, steps["2026-10-01"]!!.first, 1e-9) // myzone now eligible
        // same table, different metric: myzone still excluded for weight
        val wr = rec(SOURCE_MYZONE, "2026-10-01T07:00:00Z", mapOf("weight" to mapOf("inKilograms" to 99.0)))
        val w = maxAcrossOrigins(listOf(wr), "weight", pri, zone) { (it.data["weight"] as Map<*, *>)["inKilograms"] as Double? }
        assertFalse(w.containsKey("2026-10-01"))
    }

    @Test fun `default tier table matches the foundations`() {
        val p = SourcePriority()
        assertEquals(SourceTier.TRUTH, p.tierFor("steps", SOURCE_HUME))
        assertEquals(SourceTier.SECONDARY, p.tierFor("steps", SOURCE_SAMSUNG))
        assertEquals(SourceTier.EXCLUDED, p.tierFor("steps", SOURCE_MYZONE))
        assertEquals(SourceTier.UNRANKED, p.tierFor("steps", "com.unknown.app"))
    }
}
