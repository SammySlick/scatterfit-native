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
    fun `full-sync stamp reports History complete with synced age`() {
        val now = 10_000_000_000_000L
        val records = listOf(HealthRecord(start = "2026-10-09T00:00:00Z"))
        val state = SyncStore.MethodState(cursorMs = now, records = records, lastFullSyncMs = now)
        val info = computeMethodDebugInfo(RecordMethod.STEPS, state, nowMs = now)

        assertEquals(1, info.recordCount)
        assertTrue(info.cursorText.isNotEmpty())
        // A completed sync's cursor is its completion time; the previous
        // "$days/120 backfilled" decayed one day per day (119 forever).
        assertEquals("History complete · synced just now", info.progressText)
        assertEquals(1f, info.progressFraction, 0.001f)
    }

    @Test
    fun `mid-backfill cursor with no stamp still shows days backfilled`() {
        val now = 10_000_000_000_000L
        val lookbackStart = now - SyncEngine.LOOKBACK_MS
        val cursor = lookbackStart + (30 * dayMs)
        val state = SyncStore.MethodState(cursorMs = cursor, records = emptyList())
        val info = computeMethodDebugInfo(RecordMethod.STEPS, state, nowMs = now)

        assertEquals("30/120 days backfilled", info.progressText)
        assertEquals(30f / 120f, info.progressFraction, 0.001f)
    }

    @Test
    fun `a cursor near the edge without a stamp does NOT claim complete`() {
        // The killer case: cursor just past the window edge but no full-sync
        // stamp — e.g. a fast-path write on a fresh install. The old
        // "cursor >= window edge" check called this history-complete. It is not.
        val now = 10_000_000_000_000L
        val state = SyncStore.MethodState(cursorMs = now - dayMs, records = emptyList())
        val info = computeMethodDebugInfo(RecordMethod.STEPS, state, nowMs = now)

        assertEquals("119/120 days backfilled", info.progressText)
    }

    @Test
    fun `sync age renders in minutes hours and days from the stamp`() {
        val now = 10_000_000_000_000L
        val steps = { h: Long ->
            SyncStore.MethodState(cursorMs = now - h, records = emptyList(), lastFullSyncMs = now - h)
        }

        assertEquals("History complete · synced 30m ago",
            computeMethodDebugInfo(RecordMethod.STEPS, steps(1_800_000L), nowMs = now).progressText)
        assertEquals("History complete · synced 3h ago",
            computeMethodDebugInfo(RecordMethod.STEPS, steps(3 * 3_600_000L), nowMs = now).progressText)
        // 30h stale: still history-complete (the lookback is done), staleness shown.
        assertEquals("History complete · synced 1d ago",
            computeMethodDebugInfo(RecordMethod.STEPS, steps(30 * 3_600_000L), nowMs = now).progressText)
    }

    @Test
    fun `heart rate live window with stamp and age`() {
        val now = 10_000_000_000_000L
        val complete = SyncStore.MethodState(cursorMs = now, records = emptyList(), lastFullSyncMs = now)
        assertEquals("Live window: 2 days · synced just now",
            computeMethodDebugInfo(RecordMethod.HEART_RATE, complete, nowMs = now).progressText)
        assertEquals(1f, computeMethodDebugInfo(RecordMethod.HEART_RATE, complete, nowMs = now).progressFraction, 0.001f)

        // 30h stale HR: complete, age shown as 1d.
        val stale = SyncStore.MethodState(cursorMs = now - 30 * 3_600_000L, records = emptyList(), lastFullSyncMs = now - 30 * 3_600_000L)
        assertEquals("Live window: 2 days · synced 1d ago",
            computeMethodDebugInfo(RecordMethod.HEART_RATE, stale, nowMs = now).progressText)
    }

    @Test
    fun `heart rate mid-backfill without stamp shows fractional live window`() {
        val now = 10_000_000_000_000L
        val half = SyncStore.MethodState(cursorMs = now - dayMs, records = emptyList())
        val info = computeMethodDebugInfo(RecordMethod.HEART_RATE, half, nowMs = now)

        assertEquals("Live window: 1/2 days", info.progressText)
        assertEquals(0.5f, info.progressFraction, 0.001f)
    }
}
