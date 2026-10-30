package com.scatterbrain.scatterfit.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit

class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            SyncHub.init(applicationContext, CoroutineScope(Dispatchers.IO + SupervisorJob()))
            val result = SyncHub.syncNow()
            Log.d("ScatterFitSync", "periodic sync done result=${result != null}")
            Result.success()
        } catch (e: Exception) {
            Log.e("ScatterFitSync", "periodic sync failed", e)
            Result.success() // never retry-storm; the next period covers it
        }
    }

    companion object {
        private const val UNIQUE_NAME = "scatterfit_periodic_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(2, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
