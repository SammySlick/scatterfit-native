package com.scatterbrain.scatterfit.data

import com.scatterbrain.scatterfit.core.dayKeyFromIso
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneId

/** Sleep night merging — dedupe identical sessions, union overlaps, never sum.
 *  Port of web src/lib/sleep.ts. */
enum class SleepStage { AWAKE, REM, LIGHT, DEEP }

data class SleepStageInterval(val start: String, val end: String, val stage: SleepStage)

data class SleepSlice(
    val start: String,
    val end: String,
    val totalMin: Double,
    val deepMin: Double,
    val remMin: Double,
    val lightMin: Double,
    val awakeMin: Double,
    val hasStages: Boolean,
    val stages: List<SleepStageInterval>,
)

data class MergedNight(
    val start: String,
    val end: String,
    val totalMin: Double,
    val deepMin: Double,
    val remMin: Double,
    val lightMin: Double,
    val awakeMin: Double,
    val hasStages: Boolean,
    val stages: List<SleepStageInterval>,
    /** Local date the night started (bedtime date; 00:00–06:00 starts belong to the previous date). */
    val night: String,
    /** Raw deduped sessions in this night. */
    val sessions: List<SleepSlice>,
    /** True when the merged total exceeded 13 h: render sessions separately. */
    val split: Boolean,
)

object Sleep {
    val MAX_NIGHT_MIN = 13 * 60.0

    fun minutesBetween(a: String?, b: String?): Double {
        val x = parseMillis(a) ?: return 0.0
        val y = parseMillis(b) ?: return 0.0
        return maxOf(0.0, (y - x) / 60000.0)
    }

    private fun iso(ms: Long): String =
        Instant.ofEpochMilli(ms).toString().let {
            // Always 3-digit millis, matching JS toISOString for stable sorting.
            if (it.length >= 20 && it[19] == '.') it
            else it.replace("Z", ".000Z")
        }

    private fun unionMin(ivs: List<Pair<Long, Long>>): Double {
        val s = ivs.filter { (a, b) -> b > a }.sortedBy { it.first }
        var total = 0L
        var curA = 0L; var curB = 0L; var has = false
        for ((a, b) in s) {
            if (!has || a > curB) {
                if (has) total += curB - curA
                curA = a; curB = b; has = true
            } else curB = maxOf(curB, b)
        }
        if (has) total += curB - curA
        return total / 60000.0
    }

    private val SLEEP_STAGE_CODES = mapOf(
        1 to "AWAKE", 2 to "SLEEPING", 3 to "OUT_OF_BED", 4 to "LIGHT", 5 to "DEEP", 6 to "REM", 7 to "AWAKE",
    )

    private fun stageName(raw: JsonElement?): String {
        val n = (raw as? JsonPrimitive)?.intOrNull
        if (n != null) return SLEEP_STAGE_CODES[n] ?: "UNKNOWN"
        val s = (raw as? JsonPrimitive)?.contentOrNull ?: return "UNKNOWN"
        return if (Regex("^\\d+$").matches(s)) SLEEP_STAGE_CODES[s.toInt()] ?: "UNKNOWN" else s.uppercase()
    }

    private class RawSession(val start: Long, val end: Long, val stages: List<Triple<Long, Long, String>>)

    private fun sliceOf(sessions: List<RawSession>): SleepSlice {
        val start = sessions.minOf { it.start }
        val end = sessions.maxOf { it.end }
        val hasStages = sessions.any { it.stages.isNotEmpty() }
        val by = mutableMapOf(SleepStage.DEEP to mutableListOf<Pair<Long, Long>>(), SleepStage.REM to mutableListOf(), SleepStage.LIGHT to mutableListOf(), SleepStage.AWAKE to mutableListOf())
        val seen = HashSet<String>()
        val ordered = mutableListOf<Triple<Long, Long, SleepStage>>()
        for (s in sessions) {
            for ((rawA, rawB, rawStage) in s.stages) {
                val a = maxOf(rawA, s.start)
                val b = minOf(rawB, s.end)
                if (b <= a) continue
                val stage = when (rawStage) {
                    "AWAKE", "OUT_OF_BED" -> SleepStage.AWAKE
                    "DEEP" -> SleepStage.DEEP
                    "REM" -> SleepStage.REM
                    else -> SleepStage.LIGHT
                }
                val id = "$a|$b|$stage"
                if (!seen.add(id)) continue
                by.getValue(stage).add(a to b)
                ordered.add(Triple(a, b, stage))
            }
        }
        val stages = ordered.sortedWith(compareBy({ it.first }, { it.second }))
            .map { SleepStageInterval(iso(it.first), iso(it.second), it.third) }
        return if (hasStages) {
            val deepMin = unionMin(by.getValue(SleepStage.DEEP))
            val remMin = unionMin(by.getValue(SleepStage.REM))
            val awakeMin = unionMin(by.getValue(SleepStage.AWAKE))
            val asleep = unionMin(
                by.getValue(SleepStage.DEEP) + by.getValue(SleepStage.REM) + by.getValue(SleepStage.LIGHT),
            )
            SleepSlice(iso(start), iso(end), asleep, deepMin, remMin, maxOf(0.0, asleep - deepMin - remMin), awakeMin, hasStages, stages)
        } else {
            val total = unionMin(sessions.map { it.start to it.end })
            SleepSlice(iso(start), iso(end), total, 0.0, 0.0, total, 0.0, false, stages)
        }
    }

    /**
     * Group sleep records into nights (local start 18:00–06:00; naps excluded),
     * dedupe identical sessions/stages, and union overlaps — never sum them.
     * Nights whose merged total exceeds 13 h are flagged `split` (a merge bug).
     */
    fun buildSleepNights(records: List<HealthRecord>, zone: ZoneId): List<MergedNight> {
        val groups = LinkedHashMap<String, LinkedHashMap<String, RawSession>>()
        for (r in records) {
            val a = parseMillis(r.start) ?: continue
            val b = parseMillis(r.end) ?: continue
            if (b <= a) continue
            val hour = Instant.ofEpochMilli(a).atZone(zone).hour
            if (hour >= 6 && hour < 18) continue // nap
            val night = dayKeyFromIso(iso(if (hour < 6) a - 12 * 3600000L else a), zone)
            val d = r.data ?: JsonObject(emptyMap())
            val inner = (d["data"] as? JsonObject)
            val rawStages = ((d["stages"] ?: inner?.get("stages")) as? JsonArray) ?: JsonArray(emptyList())
            val stages = rawStages.mapNotNull { s ->
                val o = s as? JsonObject ?: return@mapNotNull null
                val sa = parseMillis((o["startTime"] ?: o["start"])?.jsonStringOrNull()) ?: return@mapNotNull null
                val sb = parseMillis((o["endTime"] ?: o["end"])?.jsonStringOrNull()) ?: return@mapNotNull null
                if (sb <= sa) null else Triple(sa, sb, stageName(o["stage"]))
            }
            val g = groups.getOrPut(night) { LinkedHashMap() }
            val id = "$a|$b"
            val prev = g[id]
            if (prev == null || stages.size > prev.stages.size) g[id] = RawSession(a, b, stages)
        }
        val out = mutableListOf<MergedNight>()
        for ((night, g) in groups) {
            val sessions = g.values.sortedBy { it.start }
            val merged = sliceOf(sessions)
            val split = merged.totalMin > MAX_NIGHT_MIN
            out.add(MergedNight(merged.start, merged.end, merged.totalMin, merged.deepMin, merged.remMin, merged.lightMin, merged.awakeMin, merged.hasStages, merged.stages, night, sessions.map { sliceOf(listOf(it)) }, split))
        }
        return out.sortedBy { it.night }
    }
}
