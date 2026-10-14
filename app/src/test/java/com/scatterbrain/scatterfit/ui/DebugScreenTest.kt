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
    fun `completed steps lookback reports History complete with synced age`() {
        val now = 10_000_000_000_000L
        val records = listOf(HealthRecord(start = "2026-10-09T00:00:00Z"))
        val state = SyncStore.MethodState(cursorMs = now, records = records)
        val info = computeMethodDebugInfo(RecordMethod.STEPS, state, nowMs = now)

        assertEquals(1, info.recordCount)
        assertTrue(info.cursorText.isNotEmpty())
        // A completed sync's cursor is its completion time; the previous
        // "$days/120 backfilled" decayed one day per day (119 forever).
        assertEquals("History complete · synced just now", info.progressText)
        assertEquals(1f, info.progressFraction, 0.001f)
    }

    @Test
    fun `sync age renders in minutes hours and days`() {
        val now = 10_000_000_000_000L
        val steps = { h: Long -> SyncStore.MethodState(cursorMs = now - h * 3_600_000L, records = emptyList()) }

        assertEquals("History complete · synced 30m ago",
            computeMethodDebugInfo(RecordMethod.STEPS, steps(0.5.toLong()), nowMs = now).progressText)
        assertEquals("History complete · synced 3h ago",
            computeMethodDebugInfo(RecordMethod.STEPS, steps(3), nowMs = now).progressText)
        // 30h stale: still history-complete (backfill is done), staleness shown.
        assertEquals("History complete · synced 1d ago",
            computeMethodDebugInfo(RecordMethod.STEPS, steps(30), nowMs = now).progressText)
    }

    @Test
    fun `mid-backfill cursor still shows days backfilled`() {
        val now = 10_000_000_000_000L
        val lookbackStart = now - SyncEngine.LOOKBACK_MS
        val cursor = lookbackStart + (30 * dayMs)
        val state = SyncStore.MethodState(cursorMs = cursor, records = emptyList())
        val info = computeMethodDebugInfo(RecordMethod.STEPS, state, nowMs = now)

        assertEquals("30/120 days backfilled", info.progressText)
        assertEquals(30f / 120f, info.progressFraction, 0.001f)
        assertEquals(" · synced 90d ago", info.progressText.substringAfter("backfilled"))
    }

    @Test
    fun `heart rate live window with age`() {
        val now = 10_000_000_000_000L
        val stateComplete = SyncStore.MethodState(cursorMs = now, records = emptyList())
        val infoComplete = computeMethodDebugInfo(RecordMethod.HEART_RATE, stateComplete, nowMs = now)

        assertEquals("Live window: 2 days · synced just now", infoComplete.progressText)
        assertEquals(1f, infoComplete.progressFraction, 0.001f)

        val stateHalf = SyncStore.MethodState(cursorMs = now - dayMs, records = emptyList())
        val infoHalf = computeMethodDebugInfo(RecordMethod.HEART_RATE, stateHalf, nowMs = now)

        // cursor one day back has NOT reached the 2-day window edge yet.
        assertEquals("Live window: 1/2 days · synced 1d ago", infoHalf.progressText)
        assertEquals(0.5f, infoHalf.progressFraction, 0.001f)
    }
}
