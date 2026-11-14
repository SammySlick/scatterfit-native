package com.scatterbrain.scatterfit.sync

import android.content.Context
import android.content.SharedPreferences

/** What the background sync worker has actually been doing, recorded by the worker
 *  itself. Exists because "background sync isn't happening" was previously
 *  unfalsifiable from inside the app: nothing distinguished "the OS never ran the
 *  job" from "the job ran and found nothing new". */
data class SyncRunSummary(
    val lastAttemptMs: Long? = null,
    val lastSuccessMs: Long? = null,
    val lastOutcome: String? = null,
    val lastDurationMs: Long? = null,
    val totalRuns: Int = 0,
    val totalFailures: Int = 0,
)

/** The periodic request is 30 min with a 10 min flex. Doze batches jobs into
 *  maintenance windows and can stretch that, but three hours with no attempt at all
 *  means the job is not being delivered — usually the OS, not the code. */
const val SYNC_STALL_THRESHOLD_MS = 3 * 60 * 60 * 1000L

/** "just now" / "12 min ago" / "3 h ago" / "2 d ago" / "never". */
fun relativeAge(nowMs: Long, thenMs: Long?): String {
    if (thenMs == null || thenMs <= 0L) return "never"
    val age = nowMs - thenMs
    if (age < 60_000L) return "just now"          // includes small clock skew
    if (age < 3_600_000L) return "${age / 60_000L} min ago"
    if (age < 86_400_000L) return "${age / 3_600_000L} h ago"
    return "${age / 86_400_000L} d ago"
}

/** True when the worker hasn't been attempted inside the stall threshold — including
 *  the never-ran case, which on a phone that's been running for days is the same
 *  problem wearing a different hat. */
fun isBackgroundSyncStalled(summary: SyncRunSummary, nowMs: Long): Boolean {
    val lastAttempt = summary.lastAttemptMs ?: return true
    if (lastAttempt <= 0L) return true
    return nowMs - lastAttempt > SYNC_STALL_THRESHOLD_MS
}

/** One line a human can act on. */
fun backgroundSyncVerdict(summary: SyncRunSummary, nowMs: Long): String {
    val lastAttempt = summary.lastAttemptMs
    if (lastAttempt == null || lastAttempt <= 0L) {
        return "Background sync has never run on this install."
    }
    if (isBackgroundSyncStalled(summary, nowMs)) {
        return "Not run for ${relativeAge(nowMs, lastAttempt)} — Android isn't delivering the job. Set Settings > Apps > ScatterFit > Battery to Unrestricted."
    }
    val outcome = summary.lastOutcome
    if (outcome != null && !outcome.equals("ok", ignoreCase = true)) {
        return "Last run: $outcome (${relativeAge(nowMs, lastAttempt)})."
    }
    return "Last run ${relativeAge(nowMs, lastAttempt)}."
}

/** Persists the worker's own history. Mirrors WorkoutProposalsStorage: a nullable
 *  context with an in-memory fallback so the logic is unit-testable without Android. */
class SyncRunLog(
    context: Context? = null,
    private val prefs: SharedPreferences? =
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
) {
    private var inMemory = SyncRunSummary()

    companion object {
        const val PREFS_NAME = "scatterfit_sync_runs"
        private const val KEY_LAST_ATTEMPT = "last_attempt_ms"
        private const val KEY_LAST_SUCCESS = "last_success_ms"
        private const val KEY_LAST_OUTCOME = "last_outcome"
        private const val KEY_LAST_DURATION = "last_duration_ms"
        private const val KEY_TOTAL_RUNS = "total_runs"
        private const val KEY_TOTAL_FAILURES = "total_failures"

        /** Outcome values recorded by the worker. */
        const val OUTCOME_OK = "ok"
        const val OUTCOME_SKIPPED = "skipped"
    }

    fun summary(): SyncRunSummary {
        val p = prefs ?: return inMemory
        return SyncRunSummary(
            lastAttemptMs = p.getLong(KEY_LAST_ATTEMPT, 0L).takeIf { it > 0L },
            lastSuccessMs = p.getLong(KEY_LAST_SUCCESS, 0L).takeIf { it > 0L },
            lastOutcome = p.getString(KEY_LAST_OUTCOME, null),
            lastDurationMs = p.getLong(KEY_LAST_DURATION, 0L).takeIf { it > 0L },
            totalRuns = p.getInt(KEY_TOTAL_RUNS, 0),
            totalFailures = p.getInt(KEY_TOTAL_FAILURES, 0),
        )
    }

    /** Called as soon as doWork is entered — before any sync work, so a run that dies
     *  mid-sync still leaves a timestamp proving the OS delivered the job. */
    fun recordAttempt(startedMs: Long) {
        val current = summary()
        write(current.copy(lastAttemptMs = startedMs, totalRuns = current.totalRuns + 1))
    }

    /** Called once the attempt finishes, ok or not. A non-ok outcome bumps the failure
     *  count and leaves lastSuccessMs where it was. */
    fun recordOutcome(startedMs: Long, durationMs: Long, outcome: String) {
        val current = summary()
        val ok = outcome.equals(OUTCOME_OK, ignoreCase = true)
        write(
            current.copy(
                lastAttemptMs = current.lastAttemptMs ?: startedMs,
                lastSuccessMs = if (ok) startedMs else current.lastSuccessMs,
                lastOutcome = outcome,
                lastDurationMs = durationMs,
                totalFailures = if (ok) current.totalFailures else current.totalFailures + 1,
            )
        )
    }

    private fun write(summary: SyncRunSummary) {
        val p = prefs
        if (p == null) {
            inMemory = summary
            return
        }
        p.edit()
            .putLong(KEY_LAST_ATTEMPT, summary.lastAttemptMs ?: 0L)
            .putLong(KEY_LAST_SUCCESS, summary.lastSuccessMs ?: 0L)
            .putLong(KEY_LAST_DURATION, summary.lastDurationMs ?: 0L)
            .putString(KEY_LAST_OUTCOME, summary.lastOutcome)
            .putInt(KEY_TOTAL_RUNS, summary.totalRuns)
            .putInt(KEY_TOTAL_FAILURES, summary.totalFailures)
            .apply()
    }
}
