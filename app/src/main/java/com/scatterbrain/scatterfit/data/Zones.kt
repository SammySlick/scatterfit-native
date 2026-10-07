package com.scatterbrain.scatterfit.data

import com.scatterbrain.scatterfit.core.dayKeyFromIso
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneId

/** HR series + HR zone minutes. Port of web src/lib/zones.ts. */
data class ZoneBounds(val z2: Int, val z3: Int, val z4: Int, val z5: Int)

data class ZoneDay(
    val exerciseMinutes: Double,
    val z2: Double,
    val z3: Double,
    val z4: Double,
    val z5: Double,
    /** Sam 2026-10-05 12:10: the day had ≥20 min of CONTINUOUS Z3+ HR —
     *  qualifies the day as a training day even without a session record. */
    val z3Run20: Boolean,
    /** Total minutes covered by HR samples (any bpm). */
    val sampleCoverage: Double,
    /** Median spacing between HR samples that day, in minutes. */
    val medianGapMin: Double?,
)

object Zones {
    val DEFAULT_ZONES = ZoneBounds(130, 145, 160, 175)
    const val MIN_HR_COVERAGE_MIN = 30.0
    const val HR_MAX_QUALIFY_BPM = 160

    fun hrSamples(records: List<HealthRecord>): List<Pair<Long, Double>> {
        val map = LinkedHashMap<Long, Double>()
        var sawSampleList = false
        fun readSampleList(list: JsonElement?) {
            val arr = list as? JsonArray ?: return
            sawSampleList = true
            for (s in arr) {
                val o = s as? JsonObject ?: continue
                val t = parseMillis((o["time"] ?: o["startTime"])?.jsonStringOrNull()) ?: continue
                val v = Normalise.num(o["beatsPerMinute"] ?: o["bpm"]) ?: continue
                if (v > 20 && v < 250) map[t] = v
            }
        }
        for (r in records) {
            val d = r.data ?: continue
            // Server shape is doubly nested (data.data.samples); some records may
            // carry samples directly on data instead.
            readSampleList(d["samples"])
            readSampleList((d["data"] as? JsonObject)?.get("samples"))
        }
        if (!sawSampleList) {
            // Flat fallback: a single bpm on the record itself.
            for (r in records) {
                val d = r.data ?: continue
                val t = parseMillis(r.start) ?: continue
                val v = Normalise.num(d["beatsPerMinute"] ?: d["bpm"]) ?: continue
                if (v > 20 && v < 250) map[t] = v
            }
        }
        return map.entries.sortedBy { it.key }.map { it.key to it.value }
    }

    /** Evenly preserve the first/last point while capping UI-only series. */
    fun <T> downsamplePoints(points: List<T>, limit: Int = 300): List<T> {
        if (points.size <= limit || limit < 2) return points
        val last = points.size - 1
        return (0 until limit).map { points[Math.round(it.toDouble() * last / (limit - 1)).toInt()] }
    }

    private class Acc {
        var exerciseMinutes = 0.0
        var z2 = 0.0; var z3 = 0.0; var z4 = 0.0; var z5 = 0.0
        var z3Run20 = false
        var sampleCoverage = 0.0
    }

    /**
     * Per local day zone breakdown. Each sample covers forward to the next
     * sample, capped at 5 min (last sample: 1 min). Time at/above z2 counts
     * (Sam 2026-10-05 12:10: ONE minute of in-zone time counts); time inside
     * runs goes to the sample's zone. Covered time splits across local midnight.
     */
    fun zoneBreakdownByDay(records: List<HealthRecord>, zones: ZoneBounds, zone: ZoneId): Map<String, ZoneDay> {
        val samples = hrSamples(records)
        val days = LinkedHashMap<String, Acc>()
        val gaps = HashMap<String, MutableList<Double>>()
        fun day(k: String): Acc = days.getOrPut(k) { Acc() }
        fun zoneOf(v: Double) = when {
            v >= zones.z5 -> "z5"; v >= zones.z4 -> "z4"; v >= zones.z3 -> "z3"; else -> "z2"
        }
        /** Add [a,b) to per-day buckets, splitting at local midnight. */
        fun spread(a: Long, b: Long, fn: (Acc, Double) -> Unit) {
            var t = a
            while (t < b) {
                val d = Instant.ofEpochMilli(t).atZone(zone)
                val next = d.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = minOf(b, next)
                fn(day(dayKeyFromIso(iso(t), zone)), (end - t) / 60000.0)
                t = end
            }
        }
        class Run { var start = 0L; var end = 0L; var v = 0.0 }
        var run = mutableListOf<Run>()
        fun flush() {
            if (run.isNotEmpty() && (run.last().end - run.first().start) / 60000.0 >= 1) {
                for (s in run) {
                    val z = zoneOf(s.v)
                    spread(s.start, s.end) { d, m ->
                        when (z) {
                            "z2" -> { d.z2 += m; d.exerciseMinutes += m }
                            "z3" -> { d.z3 += m; d.exerciseMinutes += m }
                            "z4" -> { d.z4 += m; d.exerciseMinutes += m }
                            else -> { d.z5 += m; d.exerciseMinutes += m }
                        }
                    }
                }
            }
            run = mutableListOf()
        }
        class Run3 { var start = 0L; var end = 0L }
        var run3 = mutableListOf<Run3>()
        fun flush3() {
            if (run3.isNotEmpty() && (run3.last().end - run3.first().start) / 60000.0 >= 20) {
                spread(run3.first().start, run3.last().end) { d, _ -> d.z3Run20 = true }
            }
            run3 = mutableListOf()
        }

        for (i in samples.indices) {
            val (t, v) = samples[i]
            val next = samples.getOrNull(i + 1)?.first
            val end = t + minOf(next?.minus(t) ?: 60000L, 5 * 60000L)
            spread(t, end) { d, m -> d.sampleCoverage += m }
            if (next != null) gaps.getOrPut(dayKeyFromIso(iso(t), zone)) { mutableListOf() }.add((next - t) / 60000.0)
            if (v >= zones.z2) {
                val last = run.lastOrNull()
                if (last != null && last.end < t) flush()
                run.add(Run().apply { start = t; this.end = end; this.v = v })
            } else flush()
            if (v >= zones.z3) {
                val last3 = run3.lastOrNull()
                if (last3 != null && last3.end < t) flush3()
                run3.add(Run3().apply { start = t; this.end = end })
            } else flush3()
        }
        flush()
        flush3()

        val out = LinkedHashMap<String, ZoneDay>()
        for ((k, a) in days) {
            val g = gaps[k]?.sorted()
            val r2 = a.z2.round0(); val r3 = a.z3.round0(); val r4 = a.z4.round0(); val r5 = a.z5.round0()
            // L audit 2026-10-05 12:52: derive exerciseMinutes from the ROUNDED
            // zones — every counted minute belongs to exactly one zone, so the
            // sum is always self-consistent.
            out[k] = ZoneDay(
                exerciseMinutes = r2 + r3 + r4 + r5,
                z2 = r2, z3 = r3, z4 = r4, z5 = r5,
                z3Run20 = a.z3Run20,
                sampleCoverage = a.sampleCoverage.round0(),
                medianGapMin = g?.getOrNull(g.size / 2),
            )
        }
        return out
    }

    private fun Double.round0() = Math.round(this).toDouble()

    /** A TRAINING DAY: a workout logged by another app (exercise session) or
     *  ≥20 min of continuous Z3+ HR. Training MINUTES are the day's Z3+ total;
     *  Z2-only days never count; a session with no HR is a real zero (not no-data). */
    fun trainingMinutesFromZones(
        zonesByDay: Map<String, ZoneDay>,
        exerciseRecords: List<HealthRecord>,
        zone: ZoneId,
    ): Map<String, Double> {
        val out = LinkedHashMap<String, Double>()
        val days = zonesByDay.keys.toMutableSet()
        for (r in exerciseRecords) {
            if (Sleep.minutesBetween(r.start, r.end) <= 0) continue
            days.add(dayKeyFromIso(r.start, zone))
        }
        for (k in days.sorted()) {
            val d = zonesByDay[k]
            if (d == null) { out[k] = 0.0; continue } // session day with no HR — a real zero
            if (!d.z3Run20) continue
            out[k] = d.z3 + d.z4 + d.z5
        }
        return out
    }

    /** Max HR is the highest recorded sample — but only once the data qualifies:
     *  ≥7 sample-days AND at least one high-intensity sample (≥160 bpm). */
    fun maxHrFromRecords(records: List<HealthRecord>, zone: ZoneId): Pair<Double?, Boolean> {
        val samples = hrSamples(records)
        if (samples.isEmpty()) return null to false
        val maxHr = samples.maxOf { it.second }
        val sampleDays = samples.map { dayKeyFromIso(iso(it.first), zone) }.toSet().size
        val qualified = sampleDays >= 7 && maxHr >= HR_MAX_QUALIFY_BPM
        return maxHr to qualified
    }
}

/** Always 3-digit millis, matching JS toISOString. */
fun iso(ms: Long): String =
    Instant.ofEpochMilli(ms).toString().let {
        if (it.length >= 20 && it[19] == '.') it else it.replace("Z", ".000Z")
    }
