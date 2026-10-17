package com.scatterbrain.scatterfit.sync

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import com.scatterbrain.scatterfit.data.HealthRecord
import com.scatterbrain.scatterfit.data.HcReaders
import com.scatterbrain.scatterfit.data.RecordMethod
import com.scatterbrain.scatterfit.ui.DrinkStorage
import com.scatterbrain.scatterfit.ui.toHealthRecord
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Application-scoped sync state. Holds the merged record map (the exact
 *  shape Daily.buildDailyDataFromMaps consumes — demo and HC data share it)
 *  and owns the one real engine instance. UI reads [records]; MainActivity
 *  triggers [syncNow] after permissions land. */
object SyncHub {

    private const val TAG = "ScatterFitSync"

    var store: SyncStore? = null
        private set
    private var engine: SyncEngine? = null
    private var client: HealthConnectClient? = null
    private var initJob: Job? = null

    suspend fun awaitInit() {
        initJob?.join()
    }

    private val _records = MutableStateFlow<Map<RecordMethod, List<HealthRecord>>?>(null)
    val records: StateFlow<Map<RecordMethod, List<HealthRecord>>?> = _records.asStateFlow()

    private var localFood: List<HealthRecord> = emptyList()
    private var lastHcFood: List<HealthRecord> = emptyList()
    private var localWeights: List<HealthRecord> = emptyList()
    private var lastHcWeights: List<HealthRecord> = emptyList()
    private var localDrinks: List<HealthRecord> = emptyList()
    private var lastHcAlcohol: List<HealthRecord> = emptyList()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** Why sync isn't running (HC missing/outdated/stale) — surfaced on Today.
     *  Cleared on any successful sync. */
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    fun setStatus(msg: String) { _status.value = msg }

    private fun publishFood(snapshot: Map<RecordMethod, List<HealthRecord>>): Map<RecordMethod, List<HealthRecord>> {
        if (RecordMethod.FOOD_LOG in snapshot) lastHcFood = snapshot[RecordMethod.FOOD_LOG]!!
        if (RecordMethod.WEIGHT in snapshot) lastHcWeights = snapshot[RecordMethod.WEIGHT]!!
        if (RecordMethod.ALCOHOL in snapshot) lastHcAlcohol = snapshot[RecordMethod.ALCOHOL]!!
        if (localFood.isEmpty() && lastHcFood.isEmpty() && localWeights.isEmpty() && lastHcWeights.isEmpty() && localDrinks.isEmpty() && lastHcAlcohol.isEmpty()) return snapshot
        val out = snapshot.toMutableMap()
        if (localFood.isNotEmpty() || lastHcFood.isNotEmpty()) {
            out[RecordMethod.FOOD_LOG] = localFood + (snapshot[RecordMethod.FOOD_LOG] ?: emptyList())
        }
        if (localWeights.isNotEmpty() || lastHcWeights.isNotEmpty()) {
            out[RecordMethod.WEIGHT] = localWeights + (snapshot[RecordMethod.WEIGHT] ?: emptyList())
        }
        if (localDrinks.isNotEmpty() || lastHcAlcohol.isNotEmpty()) {
            out[RecordMethod.ALCOHOL] = localDrinks + (snapshot[RecordMethod.ALCOHOL] ?: emptyList())
        }
        return out
    }

    private var syncPrefs: android.content.SharedPreferences? = null
    private var inMemoryLastSync: Long = 0L

    fun getLastSyncTimestamp(): Long {
        return syncPrefs?.getLong("last_sync_timestamp", 0L) ?: inMemoryLastSync
    }

    fun setLastSyncTimestamp(ts: Long) {
        inMemoryLastSync = ts
        syncPrefs?.edit()?.putLong("last_sync_timestamp", ts)?.apply()
    }

    fun shouldRunFullSync(nowMs: Long = System.currentTimeMillis()): Boolean {
        val lastSync = getLastSyncTimestamp()
        if (lastSync <= 0L) return true
        val elapsed = nowMs - lastSync
        return elapsed > 24 * 3600 * 1000L // > 24h
    }

    fun init(context: Context, scope: CoroutineScope) {
        if (initJob != null) return
        val appContext = context.applicationContext
        syncPrefs = appContext.getSharedPreferences("scatterfit_sync_prefs", Context.MODE_PRIVATE)
        store = SyncStore(File(appContext.filesDir, "hc-sync"))
        engine = SyncEngine(store!!, nowMs = { System.currentTimeMillis() })
        try {
            client = HealthConnectClient.getOrCreate(appContext)
        } catch (_: Exception) {
            client = null // HC unavailable (no provider); UI falls back to demo
        }
        initJob = scope.launch(Dispatchers.IO) {
            // Cold start: load the cache so UI has data before the first sync.
            if (_records.value == null) {
                val loaded = HashMap<RecordMethod, List<HealthRecord>>()
                for (m in RecordMethod.entries) {
                    val s = store!!.load(m)
                    if (s.records.isNotEmpty()) loaded[m] = s.records
                }
                val foodStorage = com.scatterbrain.scatterfit.ui.FoodStorage(appContext)
                localFood = foodStorage.getAllMeals().map { it.toHealthRecord() }
                val weightStorage = com.scatterbrain.scatterfit.ui.WeightStorage(appContext)
                localWeights = weightStorage.loadAll().flatMap { it.toHealthRecords() }
                val drinkStorage = com.scatterbrain.scatterfit.ui.DrinkStorage(appContext)
                localDrinks = drinkStorage.loadAll().values.flatten().map { it.toHealthRecord() }
                if (loaded.isNotEmpty() || localFood.isNotEmpty() || localWeights.isNotEmpty() || localDrinks.isNotEmpty()) {
                    _records.value = publishFood(loaded)
                }
            }
        }
    }

    fun updateLocalFoodLog(foodRecords: List<HealthRecord>) {
        localFood = foodRecords
        val snapshot = _records.value ?: emptyMap()
        val out = snapshot.toMutableMap()
        // lastHcFood = the HC-only view maintained by publishFood — rebuild
        // the bucket from it, never from the current bucket (which contains
        // the OLD local list and would duplicate).
        out[RecordMethod.FOOD_LOG] = foodRecords + lastHcFood
        _records.value = out
    }

    fun updateLocalDrinks(drinkRecords: List<HealthRecord>) {
        localDrinks = drinkRecords
        val snapshot = _records.value ?: emptyMap()
        val out = snapshot.toMutableMap()
        // Same discipline as food/weights: rebuild the ALCOHOL bucket from the
        // HC-only view, never from the current bucket (which contains the OLD
        // local list and would duplicate).
        out[RecordMethod.ALCOHOL] = drinkRecords + lastHcAlcohol
        _records.value = out
    }

    fun updateLocalWeights(weightRecords: List<HealthRecord>) {
        localWeights = weightRecords
        val snapshot = _records.value ?: emptyMap()
        val out = snapshot.toMutableMap()
        // Same discipline as updateLocalFoodLog: rebuild the WEIGHT bucket from
        // the HC-only view, never from the current bucket (which contains the
        // OLD local list and would duplicate).
        out[RecordMethod.WEIGHT] = weightRecords + lastHcWeights
        _records.value = out
    }

    /** Fast incremental sync: reads only today's window (00:00 local today to now) without backfilling 120 days. */
    suspend fun syncToday(
        methods: Set<RecordMethod> = RecordMethod.entries.toSet(),
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): SyncEngine.SyncResult? {
        val startOfToday = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        return syncNow(methods = methods, windowFromOverride = startOfToday)
    }

    /** Run one incremental sync against Health Connect. Safe to call from
     *  anywhere (service, activity) — concurrency-guarded, idempotent. */
    suspend fun syncNow(
        methods: Set<RecordMethod> = RecordMethod.entries.toSet(),
        windowFromOverride: Long? = null,
        forceFull: Boolean = false,
    ): SyncEngine.SyncResult? {
        awaitInit()
        val e = engine ?: return null
        val c = client ?: return null
        if (!_syncing.compareAndSet(false, true)) { Log.d(TAG, "syncNow: already running, skip"); return null }

        val now = System.currentTimeMillis()
        val effectiveWindowFrom = if (windowFromOverride != null) {
            windowFromOverride
        } else if (!forceFull && !shouldRunFullSync(now)) {
            now - 2 * 86_400_000L // Incremental 2-day window
        } else {
            null // Full 120d sync
        }

        Log.d(TAG, "syncNow: STARTING engine sync (effectiveWindowFrom=$effectiveWindowFrom, forceFull=$forceFull)")
        return try {
            val result = e.sync(
                methods = methods,
                windowFromOverride = effectiveWindowFrom,
                reader = { ms, fromMs, toMs, onSlice ->
                    var sliceN = 0
                    val read = HcReaders.readAll(c, fromMs, toMs, ms) { m, sliceEnd, sliceRecords ->
                        onSlice(m, sliceEnd, sliceRecords)
                        sliceN++
                        Log.d(TAG, "slice ${sliceN} committed: ${m.name} rows=${sliceRecords.size} cursor=${sliceEnd}")
                    }
                    read.forEach { (method, rows) ->
                        Log.d(TAG, "sync read: ${method.name} rows=${rows.size} window=${fromMs}..${toMs}")
                    }
                    Log.d(TAG, "sync read: methods=${ms.size} totalRows=${read.values.sumOf { it.size }}")
                    read
                },
                // Stream each slice into the UI the moment it commits — the
                // Today screen updates WHILE the sync runs, not after.
                onProgress = { snapshot -> _records.value = publishFood(snapshot) },
            )
            Log.d(TAG, "sync done: mergedMethods=${result.merged.size} mergedRows=${result.merged.values.sumOf { it.size }}")
            setLastSyncTimestamp(now)
            _records.value = publishFood(result.merged)
            _lastError.value = null
            _status.value = if (result.merged.isEmpty()) {
                // Sync ran, read everything it was allowed to — and got nothing.
                // Almost always: no source app is writing to Health Connect.
                "Sync ran: 0 records from Health Connect. Open Health Connect > Data and access and check your fitness app is sharing data."
            } else ""
            result
        } catch (t: Throwable) {
            Log.e(TAG, "syncNow: FAILED", t)
            _lastError.value = t.message ?: t.javaClass.simpleName
            null
        } finally {
            _syncing.value = false
        }
    }
}
