package com.scatterbrain.scatterfit.data

import android.content.Context
import android.util.Log
import com.scatterbrain.scatterfit.sync.SyncHub
import com.scatterbrain.scatterfit.ui.SessionRecord
import com.scatterbrain.scatterfit.ui.SessionRecordsStorage
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

fun isDailyEquivalent(current: AssembledDaily?, candidate: AssembledDaily?): Boolean {
    if (current === candidate) return true
    if (current == null || candidate == null) return false

    // Compare dayKeys cheaply
    if (current.caloriesEaten.keys != candidate.caloriesEaten.keys) return false
    if (current.steps.keys != candidate.steps.keys) return false
    if (current.protein.keys != candidate.protein.keys) return false
    if (current.trainingMinutes.keys != candidate.trainingMinutes.keys) return false

    // Compare per-day values
    if (current.caloriesEaten != candidate.caloriesEaten) return false
    if (current.steps != candidate.steps) return false
    if (current.protein != candidate.protein) return false
    if (current.trainingMinutes != candidate.trainingMinutes) return false

    // Compare sleep totals per day
    val currentSleep = current.sleep.mapValues { it.value.totalMin }
    val candidateSleep = candidate.sleep.mapValues { it.value.totalMin }
    if (currentSleep != candidateSleep) return false

    return true
}

object DailyRepository {
    private val _daily = MutableStateFlow<AssembledDaily?>(null)
    val daily: StateFlow<AssembledDaily?> = _daily.asStateFlow()

    private var storage: DailySnapshotStorage? = null
    private var sessionRecordsStorage: SessionRecordsStorage? = null
    private var writeJob: Job? = null

    fun start(context: Context, scope: CoroutineScope) {
        val s = DailySnapshotStorage(context.applicationContext)
        val srs = SessionRecordsStorage(context.applicationContext)
        this.storage = s
        this.sessionRecordsStorage = srs
        start(scope, s, srs)
    }

    fun start(
        scope: CoroutineScope,
        snapshotStorage: DailySnapshotStorage? = storage,
        sessionRecordsStorage: SessionRecordsStorage? = this.sessionRecordsStorage,
    ) {
        this.storage = snapshotStorage
        this.sessionRecordsStorage = sessionRecordsStorage
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
                        val d = Daily.buildDailyDataFromMaps(
                            it,
                            Zones.DEFAULT_ZONES,
                            ZoneId.systemDefault(),
                            nowMs = System.currentTimeMillis(),
                        )
                        sessionRecordsStorage?.let { srs ->
                            Daily.mergeSessionRecordsIntoDaily(d, srs.loadAll())
                        } ?: d
                    }
                }
                if (newDaily != null) {
                    val currentDaily = _daily.value
                    if (currentDaily != null && isDailyEquivalent(currentDaily, newDaily)) {
                        Log.d("ScatterFitSync", "DailyRepository: incoming daily equivalent to current snapshot — skipping republish")
                    } else {
                        _daily.value = newDaily
                        if (!firstDailyPublished) {
                            firstDailyPublished = true
                            val elapsed = System.currentTimeMillis() - startMs
                            Log.d("ScatterFitSync", "first daily from records in ${elapsed}ms")
                        }
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

        // Final settle write: when sync finishes (syncing transitions to false), ensure final write lands
        scope.launch {
            SyncHub.syncing.collect { isSyncing ->
                if (!isSyncing && snapshotStorage != null) {
                    val settled = _daily.value
                    if (settled != null) {
                        writeJob?.cancel()
                        writeJob = scope.launch(Dispatchers.IO) {
                            delay(3000L)
                            snapshotStorage.save(settled)
                        }
                    }
                }
            }
        }
    }

    fun notifySessionRecorded(record: SessionRecord) {
        val current = _daily.value ?: return
        _daily.value = Daily.mergeSessionRecordsIntoDaily(current, listOf(record))
    }
}
