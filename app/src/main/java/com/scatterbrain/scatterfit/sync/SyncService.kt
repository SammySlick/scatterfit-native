package com.scatterbrain.scatterfit.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

fun canPromoteToHealthForeground(context: Context): Boolean {
    // On Android 14+ (API 34+), foreground service type "health" strictly requires holding health read permissions
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        return hasAnyHealthPermission(context)
    }
    return true
}

fun hasAnyHealthPermission(context: Context): Boolean {
    val healthPermissions = listOf(
        "android.permission.health.READ_STEPS",
        "android.permission.health.READ_SLEEP",
        "android.permission.health.READ_TOTAL_CALORIES_BURNED",
        "android.permission.health.READ_HEART_RATE",
        "android.permission.health.READ_RESTING_HEART_RATE",
        "android.permission.health.READ_WEIGHT",
        "android.permission.health.READ_EXERCISE",
        "android.permission.health.READ_NUTRITION",
        "android.permission.health.READ_BODY_FAT",
        "android.permission.health.READ_DISTANCE",
        "android.permission.health.READ_HEALTH_DATA_HISTORY"
    )
    return healthPermissions.any { perm ->
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }
}

fun runSyncWithFallback(
    attemptForeground: () -> Unit,
    onForegroundFailed: (Exception) -> Unit,
    executeSync: suspend () -> Unit,
    scope: CoroutineScope
): Job {
    try {
        attemptForeground()
    } catch (e: Exception) {
        onForegroundFailed(e)
    }
    return scope.launch {
        executeSync()
    }
}

/** Foreground sync service — Foundations: "Foreground service is a sync/
 *  sync citizen from day one". Deliberately thin: one job (run the engine
 *  once), a persistent notification while it runs, then stop. All logic
 *  lives in SyncEngine/SyncHub; this file makes no decisions. */
class SyncService : Service() {

    private val TAG = "ScatterFitSync"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "service onStartCommand — starting foreground + sync")

        if (syncJob?.isActive == true) {
            // A sync is already in flight. Do NOT stopSelf here: stopping the
            // service destroys it, onDestroy cancels the scope, and the RUNNING
            // sync dies mid-read (seen live 2026-10-09 ~03:11: every activity
            // re-resume murdered the in-flight sync ~3s in). The running job
            // owns the shutdown; this start just re-asserts foreground.
            Log.d(TAG, "service onStartCommand — sync already in flight, leaving it alone")
            return START_NOT_STICKY
        }

        syncJob = runSyncWithFallback(
            attemptForeground = {
                if (canPromoteToHealthForeground(this)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(
                            NOTIF_ID,
                            buildNotification(),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                        )
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIF_ID,
                            buildNotification(),
                            0
                        )
                    } else {
                        startForeground(NOTIF_ID, buildNotification())
                    }
                } else {
                    Log.w(TAG, "Health Connect permissions missing; skipping foreground promotion on Android 14+")
                }
            },
            onForegroundFailed = { e ->
                Log.e(TAG, "startForeground failed; falling back to background coroutine sync", e)
            },
            executeSync = {
                SyncHub.syncNow()
                Log.d(TAG, "service sync returned — stopping")
                stopSelf()
            },
            scope = scope
        )

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Cancel only if no sync is in flight — an in-flight sync keeps this
        // service alive by contract, so reaching onDestroy here means system
        // teardown; cancel to avoid leaking the scope.
        if (syncJob?.isActive != true) scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Health sync", NotificationManager.IMPORTANCE_MIN),
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("ScatterFit")
            .setContentText("Syncing health data")
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "sync"
        private const val NOTIF_ID = 42

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, SyncService::class.java))
            } catch (e: Exception) {
                Log.e("ScatterFitSync", "startForegroundService failed, running background coroutine sync", e)
                CoroutineScope(Dispatchers.IO).launch {
                    SyncHub.syncNow()
                }
            }
        }
    }
}
