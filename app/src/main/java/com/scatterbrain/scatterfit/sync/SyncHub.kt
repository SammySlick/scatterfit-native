package com.scatterbrain.scatterfit.sync

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import com.scatterbrain.scatterfit.data.HealthRecord
import com.scatterbrain.scatterfit.data.HcReaders
import com.scatterbrain.scatterfit.data.RecordMethod
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Application-scoped sync state. Holds the merged record map (the exact
 *  shape Daily.buildDailyDataFromMaps consumes — demo and HC data share it)
 *  and owns the one real engine instance. UI reads [records]; MainActivity
 *  triggers [syncNow] after permissions land. */
object SyncHub {

    private const val TAG = "ScatterFitSync"

    private var store: SyncStore? = null
    private var engine: SyncEngine? = null
    private var client: HealthConnectClient? = null

    private val _records = MutableStateFlow<Map<RecordMethod, List<HealthRecord>>?>(null)
    val records: StateFlow<Map<RecordMethod, List<HealthRecord>>?> = _records.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** Why sync isn't running (HC missing/outdated/stale) — surfaced on Today.
     *  Cleared on any successful sync. */
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    fun setStatus(msg: String) { _status.value = msg }

    fun init(context: Context) {
        if (store != null) return
        val appContext = context.applicationContext
        store = SyncStore(File(appContext.filesDir, "hc-sync"))
        engine = SyncEngine(store!!, nowMs = { System.currentTimeMillis() })
        try {
            client = HealthConnectClient.getOrCreate(appContext)
        } catch (_: Exception) {
            client = null // HC unavailable (no provider); UI falls back to demo
        }
        // Cold start: load the cache so UI has data before the first sync.
        if (_records.value == null) {
            val loaded = HashMap<RecordMethod, List<HealthRecord>>()
            for (m in RecordMethod.entries) {
                val s = store!!.load(m)
                if (s.records.isNotEmpty()) loaded[m] = s.records
            }
            if (loaded.isNotEmpty()) _records.value = loaded
        }
    }

    /** Run one incremental sync against Health Connect. Safe to call from
     *  anywhere (service, activity) — concurrency-guarded, idempotent. */
    suspend fun syncNow(methods: Set<RecordMethod> = RecordMethod.entries.toSet()): SyncEngine.SyncResult? {
        val e = engine ?: return null
        val c = client ?: return null
        if (!_syncing.compareAndSet(false, true)) return null
        return try {
            val result = e.sync(reader = { ms, fromMs, toMs ->
                val read = HcReaders.readAll(c, fromMs, toMs, ms)
                read.forEach { (method, rows) ->
                    Log.d(TAG, "sync read: ${method.name} rows=${rows.size} window=${fromMs}..${toMs}")
                }
                Log.d(TAG, "sync read: methods=${ms.size} totalRows=${read.values.sumOf { it.size }}")
                read
            })
            Log.d(TAG, "sync done: mergedMethods=${result.merged.size} mergedRows=${result.merged.values.sumOf { it.size }}")
            _records.value = result.merged
            _lastError.value = null
            _status.value = if (result.merged.isEmpty()) {
                // Sync ran, read everything it was allowed to — and got nothing.
                // Almost always: no source app is writing to Health Connect.
                "Sync ran: 0 records from Health Connect. Open Health Connect > Data and access and check your fitness app is sharing data."
            } else ""
            result
        } catch (t: Throwable) {
            _lastError.value = t.message ?: t.javaClass.simpleName
            null
        } finally {
            _syncing.value = false
        }
    }
}
