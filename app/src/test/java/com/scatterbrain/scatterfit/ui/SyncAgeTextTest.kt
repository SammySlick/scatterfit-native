package com.scatterbrain.scatterfit.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The label is the only place the app admits its data might be old, so its
 * states matter more than they look: a wrong label turns "background sync is
 * broken" into "everything is fine".
 */
class SyncAgeTextTest {

    private val now = 1_760_000_000_000L

    @Test
    fun `a running sync says so instead of showing an age it is about to change`() {
        assertEquals("Syncing…", syncAgeText(isSyncing = true, hasData = false, lastSyncMs = 0L, nowMs = now))
        assertEquals("Updating…", syncAgeText(isSyncing = true, hasData = true, lastSyncMs = now - 60_000L, nowMs = now))
    }

    @Test
    fun `never synced is called out rather than dressed up as fresh`() {
        val text = syncAgeText(isSyncing = false, hasData = false, lastSyncMs = 0L, nowMs = now)
        assertEquals("Not synced yet · tap to sync", text)
    }

    @Test
    fun `age is reported for the scale a person would use`() {
        assertEquals("Updated just now · tap to sync",
            syncAgeText(false, true, now - 5_000L, now))
        assertEquals("Updated 12m ago · tap to sync",
            syncAgeText(false, true, now - 12 * 60_000L, now))
        assertEquals("Updated 3h ago · tap to sync",
            syncAgeText(false, true, now - 3 * 3_600_000L, now))
        assertEquals("Updated 2d ago · tap to sync",
            syncAgeText(false, true, now - 2 * 86_400_000L, now))
    }

    @Test
    fun `every non-syncing state invites a refresh`() {
        val states = listOf(
            syncAgeText(false, false, 0L, now),
            syncAgeText(false, true, now - 30_000L, now),
            syncAgeText(false, true, now - 5 * 86_400_000L, now),
        )
        states.forEach { assertTrue("'$it' should invite a tap", it.contains("tap to sync")) }
    }

    @Test
    fun `a sync in progress never claims freshness`() {
        listOf(
            syncAgeText(true, false, 0L, now),
            syncAgeText(true, true, now - 5_000L, now),
        ).forEach {
            assertFalse("'$it' must not claim to be up to date", it.startsWith("Updated"))
        }
    }
}
