package com.scatterbrain.scatterfit.data

import com.scatterbrain.scatterfit.sync.SyncHub
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object DailyRepository {
    private val _daily = MutableStateFlow<AssembledDaily?>(null)
    val daily: StateFlow<AssembledDaily?> = _daily.asStateFlow()

    fun start(scope: CoroutineScope) {
        scope.launch {
            SyncHub.records.collect { records ->
                _daily.value = withContext(Dispatchers.Default) {
                    records?.let {
                        Daily.buildDailyDataFromMaps(
                            it,
                            Zones.DEFAULT_ZONES,
                            ZoneId.systemDefault(),
                            nowMs = System.currentTimeMillis(),
                        )
                    }
                }
            }
        }
    }
}
