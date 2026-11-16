package com.scatterbrain.scatterfit.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit

/**
 * Background Health Connect sync.
 *
 * Two things worth knowing, because both were wrong and both were invisible:
 *
 * 1. There is deliberately NO network constraint. Health Connect is a local
 *    database on the phone; reading it makes no requests. Requiring the phone
 *    to believe it has connectivity meant sync refused to run in a gym, a
 *    basement or on a plane — exactly where the data is being made.
 *
 * 2. The periodic request uses [ExistingPeriodicWorkPolicy.UPDATE], not KEEP.
 *    KEEP leaves an already-enqueued request untouched forever, so any later
 *    change to the constraints or the interval would never reach an installed
 *    phone. UPDATE replaces the stored work when the definition changes and is
 *    otherwise a no-op, which is what "keep it scheduled" actually means.
 */
class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        SyncDiagnostics.recordStart(applicationContext)
        return try {
            SyncHub.init(applicationContext, CoroutineScope(Dispatchers.IO + SupervisorJob()))
            // Pin the window to the last 2 days. syncNow() would otherwise run a
            // FULL 120-day backfill once the cache looks a day old, and a full
            // pass is what wedges the Samsung provider (700k+ step rows) and
            // gets the process killed mid-read. Backfill stays a foreground job;
            // the background tick only has to keep the recent days current.
            val result = SyncHub.syncNow(
                windowFromOverride = System.currentTimeMillis() - INCREMENTAL_WINDOW_MS,
            )
            if (result == null) {
                // syncNow returns null both for "already running" and "no
                // engine/permission". Neither is worth a retry-storm.
                SyncDiagnostics.recordFinish(applicationContext, "skipped", null)
                Log.d(TAG, "periodic sync: skipped (already running, or Health Connect unavailable)")
                Result.success()
            } else {
                SyncDiagnostics.recordFinish(applicationContext, "ok", result.merged.size)
                Log.d(TAG, "periodic sync: ok, methods=${result.merged.size}")
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "periodic sync failed", e)
            SyncDiagnostics.recordFinish(applicationContext, "failed", null, e.message)
            // Retry once on a backoff rather than waiting a whole period: a
            // transient failure (provider busy, phone mid-boot) shouldn't cost
            // two hours of stale data. Backoff is capped so it can't storm.
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ScatterFitSync"
        private const val UNIQUE_NAME = "scatterfit_periodic_sync"
        private const val ONE_SHOT_NAME = "scatterfit_manual_sync"

        /** How often the background job runs. Health Connect aggregate reads are
         *  cheap (the provider does the work), so 2h keeps the day current
         *  without waking the radio. WorkManager treats this as a floor, not a
         *  promise — Doze can push it later. */
        private const val PERIOD_HOURS = 2L
        private const val FLEX_MINUTES = 15L

        /** Same 2-day window the foreground incremental path uses. */
        private const val INCREMENTAL_WINDOW_MS = 2 * 86_400_000L

        internal fun constraints(): Constraints = Constraints.NONE

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(
                PERIOD_HOURS, TimeUnit.HOURS,
                FLEX_MINUTES, TimeUnit.MINUTES,
            )
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
            Log.d(TAG, "periodic sync scheduled: every ${PERIOD_HOURS}h, no network constraint")
        }

        /** Kick a sync immediately, off the worker path. Used by the Debug
         *  panel so we can prove the background machinery works without
         *  waiting two hours — and by the "Updated Nm ago" tap. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints())
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(ONE_SHOT_NAME, ExistingWorkPolicy.REPLACE, request)
            Log.d(TAG, "manual sync enqueued")
        }
    }
}
