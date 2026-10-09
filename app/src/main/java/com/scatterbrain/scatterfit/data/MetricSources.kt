package com.scatterbrain.scatterfit.data

import com.scatterbrain.scatterfit.data.Daily
import com.scatterbrain.scatterfit.data.RecordMethod
import com.scatterbrain.scatterfit.sync.SyncHub
import com.scatterbrain.scatterfit.sync.SyncStore
import com.scatterbrain.scatterfit.ui.MetricPoint
import com.scatterbrain.scatterfit.ui.MetricSource
import java.time.ZoneId

/**
 * REAL METRIC SOURCES — replace the demo stubs with the sync cache.
 *
 * Built on the same Daily assembler the Today screen uses, so metric pages
 * and hero numbers can never disagree about what a day was. Picks read the
 * assembled day maps; the source walks back up to HISTORY_DAYS looking for
 * the newest non-null value (missing days are gaps, not zeros).
 *
 * The registry drives everything: forMetric(id) reads the def's methods and
 * value function. There is no per-metric plumbing left to forget.
 */
class StoreDailySource(
    private val ad: AssembledDaily,
    private val pick: (AssembledDaily, String) -> Double?,
    private val zone: ZoneId,
    private val nowMs: Long,
) : MetricSource {
    override fun series(days: List<String>): List<MetricPoint> =
        days.map { key -> MetricPoint(key, pick(ad, key)) }

    override fun latest(): MetricPoint? {
        for (i in 0 until Daily.HISTORY_DAYS) {
            val key = Daily.daysAgoKey(i, zone, nowMs)
            val value = pick(ad, key)
            if (value != null) return MetricPoint(key, value)
        }
        return null
    }

    companion object {
        /** Assemble the daily maps from whatever the sync cache holds. Methods
         *  not in [methods] stay empty, so a page only pays for what it reads. */
        fun fromStore(
            store: SyncStore,
            zone: ZoneId = ZoneId.systemDefault(),
            nowMs: Long = System.currentTimeMillis(),
            methods: List<RecordMethod>,
            pick: (AssembledDaily, String) -> Double?,
        ): StoreDailySource {
            val records = methods.associateWith { store.load(it).records }
            val ad = Daily.buildDailyDataFromMaps(
                records = records,
                zones = ZoneBounds(0, 0, 0, 0),
                zone = zone,
                nowMs = nowMs,
            )
            return StoreDailySource(ad, pick, zone, nowMs)
        }
    }
}

/**
 * Production sources — the registry is the routing table. Returns null when
 * the store isn't attached yet (cold start before first sync); the UI layer
 * decides what to show then (demo stubs stay available to it — data never
 * imports ui).
 */
object RealMetricSources {
    fun forMetric(
        id: com.scatterbrain.scatterfit.core.MetricId,
        store: SyncStore? = SyncHub.store,
        zone: ZoneId = ZoneId.systemDefault(),
        nowMs: Long = System.currentTimeMillis(),
    ): MetricSource? = store?.let { s ->
        val def = com.scatterbrain.scatterfit.core.MetricRegistry.byId(id)
        StoreDailySource.fromStore(s, zone, nowMs, def.methods, def.valueFrom)
    }
}
