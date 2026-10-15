package com.scatterbrain.scatterfit.data

import android.content.Context
import android.util.Log
import com.scatterbrain.scatterfit.sync.SyncHub
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object DailyRepository {
    private val _daily = MutableStateFlow<AssembledDaily?>(null)
    val daily: StateFlow<AssembledDaily?> = _daily.asStateFlow()

    private var storage: DailySnapshotStorage? = null
    private var writeJob: Job? = null

    fun start(context: Context, scope: CoroutineScope) {
        val s = DailySnapshotStorage(context.applicationContext)
        this.storage = s
        start(scope, s)
    }

    fun start(scope: CoroutineScope, snapshotStorage: DailySnapshotStorage? = storage) {
        this.storage = snapshotStorage
        val startMs = System.currentTimeMillis()

        // BEFORE the collect, load the snapshot on IO and if present, decode and publish it as _daily immediately
        scope.launch(Dispatchers.IO) {
            val t0 = System.currentTimeMillis()
            val cached = snapshotStorage?.load()
            val loadMs = System.currentTimeMillis() - t0
            Log.d("ScatterFitSync", "snapshot loaded in ${loadMs}ms (${if (cached != null) "present" else "absent"})")
            if (cached != null && _daily.value == null) {
                _daily.value = cached
            }
        }

        var firstDailyPublished = false
        scope.launch {
            SyncHub.records.collect { records ->
                val newDaily = withContext(Dispatchers.Default) {
                    records?.let {
                        Daily.buildDailyDataFromMaps(
                            it,
                            Zones.DEFAULT_ZONES,
                            ZoneId.systemDefault(),
                            nowMs = System.currentTimeMillis(),
                        )
                    }
                }
                if (newDaily != null) {
                    _daily.value = newDaily
                    if (!firstDailyPublished) {
                        firstDailyPublished = true
                        val elapsed = System.currentTimeMillis() - startMs
                        Log.d("ScatterFitSync", "first daily from records in ${elapsed}ms")
                    }
                }

                // Debounced write: only write when no new emission has arrived for 3s.
                // Skip the write while SyncHub.syncing is true; write final settled state when non-syncing emission lands.
                if (newDaily != null && snapshotStorage != null) {
                    writeJob?.cancel()
                    if (!SyncHub.syncing.value) {
                        writeJob = scope.launch(Dispatchers.IO) {
                            delay(3000L)
                            snapshotStorage.save(newDaily)
                        }
                    }
                }
            }
        }
    }
}
