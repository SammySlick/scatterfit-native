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
    val store: SyncStore,
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
     *  whether that's fatal — the cache always holds the last good state.
     *
     *  Per-slice commits: the reader reports each completed slice via
     *  [onSlice]; the engine merges and persists immediately, advancing the
     *  method's cursor to the slice end. A sync killed mid-read (provider
     *  wedge, rate-limit, process death) therefore RESUMES from the last
     *  committed slice instead of re-reading the whole history from page 1 —
     *  live failure mode 2026-10-09: every restart re-read 120 days of
     *  throttled 36-row pages and burned the quota again. */
    suspend fun sync(
        methods: Set<RecordMethod> = RecordMethod.entries.toSet(),
        reader: suspend (
            Set<RecordMethod>,
            Long,
            Long,
            suspend (RecordMethod, Long, List<HealthRecord>) -> Unit,
        ) -> Map<RecordMethod, List<HealthRecord>>,
    ): SyncResult {
        val now = nowMs()
        val from = windowStart(methods, now)

        val merged = HashMap<RecordMethod, List<HealthRecord>>()
        val fetchedBy = HashMap<RecordMethod, Int>()
        var earliest = now

        // Per-method merge maps: cached rows first, fresh rows second —
        // identical keys (method+start) overwrite so the fresh read wins, which
        // is what makes an HC edit (content change, extended sleep end) replace
        // rather than duplicate its stale cached self. Two same-start rows are
        // the same logical record edited; fresh data is authoritative.
        val byKey = HashMap<RecordMethod, LinkedHashMap<String, HealthRecord>>()
        val sliceCount = HashMap<RecordMethod, Int>()
        val attempted = HashMap<RecordMethod, Boolean>() // reader touched this method at all
        for (m in methods) {
            val existing = store.load(m).records
            val map = LinkedHashMap<String, HealthRecord>(existing.size + 64)
            for (r in existing) map[SyncStore.key(m, r)] = r
            byKey[m] = map
            fetchedBy[m] = 0
        }

        val onSlice: suspend (RecordMethod, Long, List<HealthRecord>) -> Unit = { m, sliceCursor, sliceRecords ->
            attempted[m] = true
            val map = byKey.getValue(m)
            for (r in sliceRecords) map[SyncStore.key(m, r)] = r
            val out = pruneFor(m, ArrayList(map.values), now)
            merged[m] = out
            sliceCount[m] = (sliceCount[m] ?: 0) + sliceRecords.size
            fetchedBy[m] = sliceCount[m] ?: 0
            store.save(m, sliceCursor, out) // cursor advances per slice: killed sync resumes
        }

        val fresh = reader(methods, from, now, onSlice)

        for (m in methods) {
            val newRecords = fresh[m] ?: emptyList()
            // Cursor-freeze protocol: a method the reader never attempted (no
            // slice callback, not in the return map) was NOT read this pass —
            // e.g. aggregation found no origins — so freeze its cursor and
            // surface the cached state; the next sync retries the window.
            // A method that WAS attempted but found nothing advances normally.
            if (attempted[m] != true && newRecords.isEmpty()) {
                merged[m] = ArrayList(byKey.getValue(m).values)
                continue
            }
            val map = byKey.getValue(m)
            for (r in newRecords) map[SyncStore.key(m, r)] = r
            val out = pruneFor(m, ArrayList(map.values), now)
            // Fake/test readers that ignore the callback report their whole
            // batch in the return map; count that. Slice-reporting readers are
            // already counted (their return map is the same records).
            if ((sliceCount[m] ?: 0) == 0) fetchedBy[m] = newRecords.size
            merged[m] = out
            store.save(m, now, out) // final cursor: window was fully read
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

    /** HEART_RATE raw records: the cache holds recent samples only (web parity
     *  — the web never persists raw HR, it fetches ~2 days on demand). Pruned
     *  before every save so the store stays bounded; records with an
     *  unparseable start are kept (never delete what you can't date). */
    private fun pruneFor(m: RecordMethod, list: List<HealthRecord>, now: Long): List<HealthRecord> {
        if (m != RecordMethod.HEART_RATE) return list
        val cutoff = now - HR_PRUNE_MS
        return list.filter { (parseMillis(it.start) ?: cutoff) >= cutoff }
    }

    companion object {
        /** Re-read window behind the cursor for late writes/edits. */
        const val OVERLAP_MS: Long = 24L * 60 * 60 * 1000
        /** First sync look-back — the engine's HISTORY_DAYS window. */
        const val LOOKBACK_MS: Long = 120L * 24 * 60 * 60 * 1000
        /** HR raw records older than this leave the cache on the next save. */
        const val HR_PRUNE_MS: Long = 7L * 24 * 60 * 60 * 1000
    }
}
