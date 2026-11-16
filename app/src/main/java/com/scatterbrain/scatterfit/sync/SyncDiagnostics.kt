package com.scatterbrain.scatterfit.sync

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager

/**
 * Records whether the scheduled sync is actually running on this device.
 *
 * This exists because "no background sync" and "background sync that Samsung
 * silently kills" look identical from the UI: stale data either way. Without a
 * record of the last run we can only guess, and the guess is usually wrong.
 */
object SyncDiagnostics {

    private const val PREFS = "scatterfit_sync_diag"
    private const val KEY_START = "worker_last_start"
    private const val KEY_FINISH = "worker_last_finish"
    private const val KEY_OUTCOME = "worker_last_outcome"
    private const val KEY_NOTE = "worker_last_note"

    data class Snapshot(
        val lastStartMs: Long,
        val lastFinishMs: Long,
        val outcome: String,
        val note: String?,
    ) {
        val neverRan: Boolean get() = lastStartMs <= 0L
    }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recordStart(ctx: Context) {
        prefs(ctx).edit().putLong(KEY_START, System.currentTimeMillis()).apply()
    }

    fun recordFinish(ctx: Context, outcome: String, methodCount: Int?, error: String? = null) {
        val note = error ?: methodCount?.let { "$it methods" }
        prefs(ctx).edit()
            .putLong(KEY_FINISH, System.currentTimeMillis())
            .putString(KEY_OUTCOME, outcome)
            .putString(KEY_NOTE, note)
            .apply()
    }

    fun snapshot(ctx: Context): Snapshot {
        val p = prefs(ctx)
        return Snapshot(
            lastStartMs = p.getLong(KEY_START, 0L),
            lastFinishMs = p.getLong(KEY_FINISH, 0L),
            outcome = p.getString(KEY_OUTCOME, "") ?: "",
            note = p.getString(KEY_NOTE, null),
        )
    }

    /** True when the OS is allowed to defer or kill our work. On Samsung this is
     *  the usual reason a 2-hourly job never fires, and it is invisible from the
     *  app unless we ask. */
    fun batteryRestricted(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return !pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    /** Whether the app was put to sleep by the OEM ("Sleeping apps" on One UI,
     *  "Deep sleeping apps" etc). App Standby buckets: 45+ means rarely used,
     *  50 is restricted. */
    fun standbyBucket(ctx: Context): Int {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return -1
        return am.currentStandbyBucket
    }
}
