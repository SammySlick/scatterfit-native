package com.scatterbrain.scatterfit.ui

import com.scatterbrain.scatterfit.data.HealthRecord
import com.scatterbrain.scatterfit.data.RecordMethod
import com.scatterbrain.scatterfit.sync.SyncEngine
import com.scatterbrain.scatterfit.sync.SyncStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugScreenTest {

    private val dayMs = 24L * 60 * 60 * 1000

    @Test
    fun `null cursor reports 0 days and 0 fraction and no cursor yet`() {
        val state = SyncStore.MethodState(cursorMs = null, records = emptyList())
        val info = computeMethodDebugInfo(RecordMethod.STEPS, state, nowMs = 1_000_000_000L)

        assertEquals(0, info.recordCount)
        assertEquals("no cursor yet", info.cursorText)
        assertEquals("0/120 days backfilled", info.progressText)
        assertEquals(0f, info.progressFraction, 0.001f)
    }

    @Test
    fun `completed steps lookback reports 120 of 120 days and 1_0 fraction`() {
        val now = 10_000_000_000_000L
        val records = listOf(HealthRecord(start = "2026-10-09T00:00:00Z"))
        val state = SyncStore.MethodState(cursorMs = now, records = records)
        val info = computeMethodDebugInfo(RecordMethod.STEPS, state, nowMs = now)

        assertEquals(1, info.recordCount)
        assertTrue(info.cursorText.isNotEmpty())
        assertEquals("120/120 days backfilled", info.progressText)
        assertEquals(1f, info.progressFraction, 0.001f)
    }

    @Test
    fun `partial lookback calculates correct days clamped`() {
        val now = 10_000_000_000_000L
        val lookbackStart = now - SyncEngine.LOOKBACK_MS
        val cursor = lookbackStart + (30 * dayMs)
        val state = SyncStore.MethodState(cursorMs = cursor, records = emptyList())
        val info = computeMethodDebugInfo(RecordMethod.STEPS, state, nowMs = now)

        assertEquals("30/120 days backfilled", info.progressText)
        assertEquals(30f / 120f, info.progressFraction, 0.001f)
    }

    @Test
    fun `heart rate lookback uses 2 days target`() {
        val now = 10_000_000_000_000L
        val stateComplete = SyncStore.MethodState(cursorMs = now, records = emptyList())
        val infoComplete = computeMethodDebugInfo(RecordMethod.HEART_RATE, stateComplete, nowMs = now)

        assertEquals("2/2 days backfilled", infoComplete.progressText)
        assertEquals(1f, infoComplete.progressFraction, 0.001f)

        val cursor1Day = now - dayMs
        val stateHalf = SyncStore.MethodState(cursorMs = cursor1Day, records = emptyList())
        val infoHalf = computeMethodDebugInfo(RecordMethod.HEART_RATE, stateHalf, nowMs = now)

        assertEquals("1/2 days backfilled", infoHalf.progressText)
        assertEquals(0.5f, infoHalf.progressFraction, 0.001f)
    }
}
