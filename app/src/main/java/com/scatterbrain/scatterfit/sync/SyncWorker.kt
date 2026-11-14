package com.scatterbrain.scatterfit.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val startedMs = System.currentTimeMillis()
        val runLog = SyncRunLog(applicationContext)
        runLog.recordAttempt(startedMs)

        return try {
            // A background run can be the first thing this process ever does, so the hub
            // has to be initialised here — without this, syncNow() silently returns null
            // because engine and client were never created.
            SyncHub.init(applicationContext, CoroutineScope(Dispatchers.IO + SupervisorJob()))
            val result = SyncHub.syncNow()
            val durationMs = System.currentTimeMillis() - startedMs
            if (result == null) {
                // Not an error: either HC isn't available, or another sync already held
                // the concurrency guard. Recorded so a stuck "skipped" streak is visible.
                Log.d(TAG, "periodic sync skipped in ${durationMs}ms (engine/client unavailable or already running)")
                runLog.recordOutcome(startedMs, durationMs, SyncRunLog.OUTCOME_SKIPPED)
            } else {
                Log.d(TAG, "periodic sync ok in ${durationMs}ms")
                runLog.recordOutcome(startedMs, durationMs, SyncRunLog.OUTCOME_OK)
            }
            Result.success()
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startedMs
            Log.e(TAG, "periodic sync failed after ${durationMs}ms (attempt ${runAttemptCount + 1})", e)
            runLog.recordOutcome(startedMs, durationMs, "error: ${e.javaClass.simpleName}")
            // Retry transient failures (HC busy, provider wedged) with backoff instead of
            // waiting out the whole period. After the cap, return SUCCESS — not failure:
            // a PeriodicWorkRequest that returns failure is cancelled permanently and
            // background sync would stop for good, which is the bug this round exists to fix.
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val TAG = "ScatterFitSync"
        private const val UNIQUE_NAME = "scatterfit_periodic_sync"
        private const val MAX_RETRIES = 3

        fun schedule(context: Context) {
            // 30 min with a 10 min flex. Doze batches periodic work into maintenance
            // windows, so the effective period on a sleeping phone is longer than this;
            // SyncRunLog records what actually happens.
            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                30, TimeUnit.MINUTES,
                10, TimeUnit.MINUTES,
            )
                // No network constraint. Health Connect reads are local, so requiring a
                // connection only deferred the job in Doze for no benefit.
                .build()
            WorkManager.getInstance(context.applicationContext)
                // UPDATE, not KEEP. KEEP froze whichever request was enqueued first, so no
                // later change to interval or constraints could ever take effect without
                // clearing app data.
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
