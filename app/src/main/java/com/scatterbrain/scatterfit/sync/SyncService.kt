package com.scatterbrain.scatterfit.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Foreground sync service — Foundations: "Foreground service is a sync/
 *  sync citizen from day one". Deliberately thin: one job (run the engine
 *  once), a persistent notification while it runs, then stop. All logic
 *  lives in SyncEngine/SyncHub; this file makes no decisions. */
class SyncService : Service() {

    private val TAG = "ScatterFitSync"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: kotlinx.coroutines.Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "service onStartCommand — starting foreground + sync")
        startForeground(NOTIF_ID, buildNotification())
        if (syncJob?.isActive == true) {
            // A sync is already in flight. Do NOT stopSelf here: stopping the
            // service destroys it, onDestroy cancels the scope, and the RUNNING
            // sync dies mid-read (seen live 2026-10-09 ~03:11: every activity
            // re-resume murdered the in-flight sync ~3s in). The running job
            // owns the shutdown; this start just re-asserts foreground.
            Log.d(TAG, "service onStartCommand — sync already in flight, leaving it alone")
            return START_NOT_STICKY
        }
        syncJob = scope.launch {
            SyncHub.syncNow()
            Log.d(TAG, "service sync returned — stopping")
            stopSelf()
        }
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
            context.startForegroundService(Intent(context, SyncService::class.java))
        }
    }
}
