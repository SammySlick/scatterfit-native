package com.scatterbrain.scatterfit.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncServiceTest {

    @Test
    fun `simulate not-allowed foreground promotion - sync still runs via coroutine without crash`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        var foregroundAttempted = false
        var foregroundFailedException: Exception? = null
        var syncExecuted = false

        val notAllowedException = IllegalStateException(
            "startForegroundService() not allowed due to mAllowStartForeground false: service .sync.SyncService"
        )

        // Simulate service start where startForeground throws not-allowed exception
        val job = runSyncWithFallback(
            attemptForeground = {
                foregroundAttempted = true
                throw notAllowedException
            },
            onForegroundFailed = { e ->
                foregroundFailedException = e
            },
            executeSync = {
                syncExecuted = true
            },
            scope = testScope
        )

        testScope.advanceUntilIdle()

        // 1. Foreground was attempted
        assertTrue(foregroundAttempted)
        // 2. Exception was caught and passed to failure callback (no crash)
        assertNotNull(foregroundFailedException)
        assertEquals(notAllowedException, foregroundFailedException)
        // 3. Fallback coroutine ran sync to completion
        assertTrue(syncExecuted)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `successful foreground promotion runs sync on scope`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        var foregroundAttempted = false
        var foregroundFailed = false
        var syncExecuted = false

        val job = runSyncWithFallback(
            attemptForeground = {
                foregroundAttempted = true
            },
            onForegroundFailed = {
                foregroundFailed = true
            },
            executeSync = {
                syncExecuted = true
            },
            scope = testScope
        )

        testScope.advanceUntilIdle()

        assertTrue(foregroundAttempted)
        assertFalse(foregroundFailed)
        assertTrue(syncExecuted)
        assertTrue(job.isCompleted)
    }
}
