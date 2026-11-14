package com.scatterbrain.scatterfit.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncRunLogTest {

    private val minute = 60_000L
    private val hour = 60 * minute
    private val day = 24 * hour

    // --- relativeAge ------------------------------------------------------

    @Test
    fun `relativeAge reports never for null or zero`() {
        assertEquals("never", relativeAge(1_000_000L, null))
        assertEquals("never", relativeAge(1_000_000L, 0L))
        assertEquals("never", relativeAge(1_000_000L, -5L))
    }

    @Test
    fun `relativeAge buckets minutes hours and days`() {
        val now = 10 * day
        assertEquals("just now", relativeAge(now, now - 5_000L))
        assertEquals("12 min ago", relativeAge(now, now - 12 * minute))
        assertEquals("3 h ago", relativeAge(now, now - 3 * hour))
        assertEquals("2 d ago", relativeAge(now, now - 2 * day))
    }

    @Test
    fun `relativeAge treats clock skew as just now`() {
        val now = 10 * day
        assertEquals("just now", relativeAge(now, now + 30_000L))
    }

    @Test
    fun `relativeAge boundaries`() {
        val now = 10 * day
        assertEquals("59 min ago", relativeAge(now, now - (59 * minute)))
        assertEquals("1 h ago", relativeAge(now, now - hour))
        assertEquals("23 h ago", relativeAge(now, now - (23 * hour)))
        assertEquals("1 d ago", relativeAge(now, now - day))
    }

    // --- stall detection --------------------------------------------------

    @Test
    fun `never attempted counts as stalled`() {
        assertTrue(isBackgroundSyncStalled(SyncRunSummary(), 10 * day))
    }

    @Test
    fun `recent attempt is not stalled`() {
        val now = 10 * day
        val summary = SyncRunSummary(lastAttemptMs = now - 20 * minute)
        assertFalse(isBackgroundSyncStalled(summary, now))
    }

    @Test
    fun `attempt older than the threshold is stalled`() {
        val now = 10 * day
        val summary = SyncRunSummary(lastAttemptMs = now - 4 * hour)
        assertTrue(isBackgroundSyncStalled(summary, now))
    }

    @Test
    fun `exactly at the threshold is not yet stalled`() {
        val now = 10 * day
        val summary = SyncRunSummary(lastAttemptMs = now - SYNC_STALL_THRESHOLD_MS)
        assertFalse(isBackgroundSyncStalled(summary, now))
    }

    // --- verdict ----------------------------------------------------------

    @Test
    fun `verdict says never run on a fresh install`() {
        val verdict = backgroundSyncVerdict(SyncRunSummary(), 10 * day)
        assertTrue(verdict.contains("never run"))
    }

    @Test
    fun `verdict points at battery settings when stalled`() {
        val now = 10 * day
        val summary = SyncRunSummary(lastAttemptMs = now - 5 * hour, lastOutcome = "ok")
        val verdict = backgroundSyncVerdict(summary, now)
        assertTrue(verdict.contains("Unrestricted"))
        assertTrue(verdict.contains("5 h ago"))
    }

    @Test
    fun `verdict names the error of the last run`() {
        val now = 10 * day
        val summary = SyncRunSummary(
            lastAttemptMs = now - 15 * minute,
            lastOutcome = "error: SecurityException",
        )
        val verdict = backgroundSyncVerdict(summary, now)
        assertTrue(verdict.contains("SecurityException"))
    }

    @Test
    fun `verdict is plain when a recent run succeeded`() {
        val now = 10 * day
        val summary = SyncRunSummary(lastAttemptMs = now - 15 * minute, lastOutcome = "ok")
        val verdict = backgroundSyncVerdict(summary, now)
        assertEquals("Last run 15 min ago.", verdict)
    }

    // --- storage ----------------------------------------------------------

    @Test
    fun `starts empty`() {
        val log = SyncRunLog()
        val summary = log.summary()
        assertNull(summary.lastAttemptMs)
        assertNull(summary.lastSuccessMs)
        assertNull(summary.lastOutcome)
        assertEquals(0, summary.totalRuns)
        assertEquals(0, summary.totalFailures)
    }

    @Test
    fun `attempt stamps the time and bumps the run count`() {
        val log = SyncRunLog()
        log.recordAttempt(1_000L)
        val summary = log.summary()
        assertEquals(1_000L, summary.lastAttemptMs)
        assertEquals(1, summary.totalRuns)
        assertNull(summary.lastSuccessMs)
    }

    @Test
    fun `ok outcome records success and duration`() {
        val log = SyncRunLog()
        log.recordAttempt(1_000L)
        log.recordOutcome(startedMs = 1_000L, durationMs = 2_500L, outcome = SyncRunLog.OUTCOME_OK)
        val summary = log.summary()
        assertEquals(1_000L, summary.lastAttemptMs)
        assertEquals(1_000L, summary.lastSuccessMs)
        assertEquals(2_500L, summary.lastDurationMs)
        assertEquals(SyncRunLog.OUTCOME_OK, summary.lastOutcome)
        assertEquals(0, summary.totalFailures)
    }

    @Test
    fun `failure counts, records the outcome and leaves last success alone`() {
        val log = SyncRunLog()
        log.recordAttempt(1_000L)
        log.recordOutcome(1_000L, 100L, SyncRunLog.OUTCOME_OK)

        log.recordAttempt(5_000L)
        log.recordOutcome(5_000L, 900L, "error: IOException")

        val summary = log.summary()
        assertEquals(5_000L, summary.lastAttemptMs)
        assertEquals(1_000L, summary.lastSuccessMs)   // untouched by the failure
        assertEquals("error: IOException", summary.lastOutcome)
        assertEquals(900L, summary.lastDurationMs)
        assertEquals(2, summary.totalRuns)
        assertEquals(1, summary.totalFailures)
    }

    @Test
    fun `skipped outcome counts as a failure without a success stamp`() {
        val log = SyncRunLog()
        log.recordAttempt(1_000L)
        log.recordOutcome(1_000L, 10L, SyncRunLog.OUTCOME_SKIPPED)

        val summary = log.summary()
        assertNull(summary.lastSuccessMs)
        assertEquals(1, summary.totalFailures)
        assertEquals(SyncRunLog.OUTCOME_SKIPPED, summary.lastOutcome)
    }

    @Test
    fun `recordOutcome without a prior attempt still stamps the attempt`() {
        val log = SyncRunLog()
        log.recordOutcome(startedMs = 7_000L, durationMs = 100L, outcome = SyncRunLog.OUTCOME_OK)
        val summary = log.summary()
        assertEquals(7_000L, summary.lastAttemptMs)
        assertEquals(7_000L, summary.lastSuccessMs)
        assertEquals(0, summary.totalRuns) // recording an outcome doesn't invent a run
    }
}
