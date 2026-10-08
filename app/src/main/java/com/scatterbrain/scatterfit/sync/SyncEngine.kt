package com.scatterbrain.scatterfit.sync

import com.scatterbrain.scatterfit.data.HealthRecord
import com.scatterbrain.scatterfit.data.RecordMethod
import com.scatterbrain.scatterfit.data.parseMillis

/** The sync engine: per-method incremental cursors over the record cache.
 *  Foundations sync/ layer — "incremental cursors per record type; no
 *  5,000-record caps". Pure orchestration over an injected reader lambda
 *  (HcReaders.readAll in prod, a fake in tests), so all merge/cursor/dedupe
 *  logic is JVM-testable without the Health Connect SDK.
 *
 *  Window semantics: one shared window per pass — from
 *  min(cursors, now - lookback) - overlap, to now. Cursors stay aligned
 *  because every pass reads every method. The 24h overlap behind the
 *  cursor catches records written or edited after the cursor passed
 *  (sleep sessions extended post-write, wearable backfill); dedupe makes
 *  the overlap idempotent. First sync (no cursors) reaches back
 *  [LOOKBACK_MS] — the engine's HISTORY_DAYS window. */
class SyncEngine(
    private val store: SyncStore,
    private val nowMs: () -> Long,
    private val overlapMs: Long = OVERLAP_MS,
    private val lookbackMs: Long = LOOKBACK_MS,
) {

    data class SyncResult(
        val merged: Map<RecordMethod, List<HealthRecord>>,
        val fetchedByMethod: Map<RecordMethod, Int>,
        val windowFromMs: Long,
        val windowToMs: Long,
    ) {
        val fetched: Int get() = fetchedByMethod.values.sum()
    }

    /** Read all methods for the window, merge with the cache, persist.
     *  Reader errors propagate: the caller (service/activity) decides
     *  whether that's fatal — the cache always holds the last good state. */
    suspend fun sync(
        methods: Set<RecordMethod> = RecordMethod.entries.toSet(),
        reader: suspend (Set<RecordMethod>, Long, Long) -> Map<RecordMethod, List<HealthRecord>>,
    ): SyncResult {
        val now = nowMs()
        val from = windowStart(methods, now)
        val fresh = reader(methods, from, now)

        val merged = HashMap<RecordMethod, List<HealthRecord>>()
        val fetchedBy = HashMap<RecordMethod, Int>()
        var earliest = now

        for (m in methods) {
            val existing = store.load(m).records
            val newRecords = fresh[m] ?: emptyList()
            // LinkedHashMap: cached rows first, fresh rows second — identical
            // keys (method+start) overwrite so the fresh read wins, which is
            // what makes an HC edit (content change, extended sleep end) replace
            // rather than duplicate its stale cached self. Two same-start rows
            // are the same logical record edited; fresh data is authoritative.
            val byKey = LinkedHashMap<String, HealthRecord>(existing.size + newRecords.size)
            for (r in existing) byKey[SyncStore.key(m, r)] = r
            for (r in newRecords) byKey[SyncStore.key(m, r)] = r
            val out = ArrayList(byKey.values)
            fetchedBy[m] = newRecords.size
            merged[m] = out
            store.save(m, now, out) // cursor advances every pass: window was read
            for (r in out) {
                val s = parseMillis(r.start) ?: continue
                if (s < earliest) earliest = s
            }
        }
        return SyncResult(merged, fetchedBy, from, now)
    }

    /** Oldest cursor among the requested methods, else full lookback.
     *  A newer cursor MUST win over the lookback — that's what makes the
     *  second pass incremental instead of another full-history read. */
    private fun windowStart(methods: Set<RecordMethod>, now: Long): Long {
        var oldest: Long? = null
        for (m in methods) {
            val c = store.load(m).cursorMs ?: continue
            if (oldest == null || c < oldest) oldest = c
        }
        return (oldest ?: (now - lookbackMs)) - overlapMs
    }

    companion object {
        /** Re-read window behind the cursor for late writes/edits. */
        const val OVERLAP_MS: Long = 24L * 60 * 60 * 1000
        /** First sync look-back — the engine's HISTORY_DAYS window. */
        const val LOOKBACK_MS: Long = 120L * 24 * 60 * 60 * 1000
    }
}
