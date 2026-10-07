package com.scatterbrain.scatterfit.data

import com.scatterbrain.scatterfit.core.dayKeyFromIso
import kotlinx.serialization.json.*
import java.time.ZoneId

/** Unit normalisation, source naming + per-origin aggregation.
 *  Port of web src/lib/normalise.ts. */
object Normalise {
    val IGNORED_WEIGHT_APPS = setOf("com.myzone.myzoneble")
    const val TRUSTED_WEIGHT_APP = "com.elink.fittrackhealth.pro"

    fun num(v: JsonElement?): Double? {
        if (v is JsonPrimitive) {
            if (v.isString) {
                if (v.content.isBlank()) return null
                return v.content.toDoubleOrNull()
            }
            return v.doubleOrNull
        }
        if (v is JsonObject) {
            for (k in listOf("value", "inGrams", "inKilocalories", "inCalories", "count")) {
                if (k in v) return num(v[k])
            }
        }
        return null
    }

    /** Mass nutrients are objects like { inGrams, ... } — always read inGrams. */
    fun grams(v: JsonElement?): Double? {
        if (v is JsonObject && "inGrams" in v) return num(v["inGrams"])
        return num(v)
    }

    fun unitOf(data: JsonObject?): String? {
        val u = data?.get("unit") ?: data?.get("energyUnit") ?: data?.get("units")
        return (u as? JsonPrimitive)?.takeIf { it.isString }?.content?.lowercase()
    }

    fun normaliseWeightKg(data: JsonObject?): Double? {
        val w = data?.get("weight")
        if (w is JsonObject && "inKilograms" in w) return num(w["inKilograms"])
        val raw = num(data?.get("weight") ?: data?.get("value") ?: data?.get("mass")) ?: return null
        return if (raw > 300) raw / 1000 else raw
    }

    fun normaliseEnergyKcal(data: JsonObject?): Double? {
        for (key in listOf("energy", "calories", "value")) {
            val v = data?.get(key)
            if (v is JsonObject) {
                val kcal = num(v["inKilocalories"] ?: v["inCalories"])
                if (kcal != null) return kcal
            }
        }
        val raw = num(data?.get("energy") ?: data?.get("calories") ?: data?.get("value")) ?: return null
        val unit = unitOf(data) ?: return raw
        return when {
            unit.contains("kilojoule") || unit == "kj" -> raw / 4.184
            unit.contains("kilocal") || unit == "kcal" -> raw
            unit.contains("cal") -> raw / 1000
            else -> raw
        }
    }

    fun normaliseDistanceM(d: JsonObject?): Double? {
        val x = d?.get("distance") ?: d
        when (x) {
            is JsonPrimitive -> return x.doubleOrNull
            is JsonObject -> {
                num(x["inMeters"] ?: x["inMetres"])?.let { return it }
                num(x["inKilometers"])?.let { return it * 1000 }
            }
            else -> {}
        }
        return null
    }

    private val SOURCE_NAMES = mapOf(
        "com.elink.fittrackhealth.pro" to "Hume",
        "com.sec.android.app.shealth" to "Samsung Health",
        "com.google.android.apps.fitness" to "Google Fit",
    )

    fun sourceName(app: String?): String {
        if (app == null) return "Unknown"
        return SOURCE_NAMES[app] ?: app
    }

    /** Device/app that actually recorded the data (Health Connect data origin).
     *  Candidates: data.dataOrigin / metadata.dataOrigin.packageName / data.app / r.app. */
    fun originOf(r: HealthRecord): String {
        val d = r.data ?: JsonObject(emptyMap())
        val meta = (d["metadata"] as? JsonObject)
        val mo = meta?.get("dataOrigin")
        val cands = listOf(
            d["dataOrigin"],
            (mo as? JsonObject)?.get("packageName") ?: mo,
            d["app"],
            JsonPrimitive(r.app ?: ""),
        )
        for (c in cands) {
            if (c is JsonPrimitive) {
                if (c.content.isNotEmpty()) return c.content
            } else if (c is JsonObject) {
                val pn = (c["packageName"] as? JsonPrimitive)?.content
                if (pn != null) return pn
            }
        }
        return "unknown"
    }

    /** Per-day, per-origin totals (identical windows within an origin deduped). */
    fun perOriginDaily(
        records: List<HealthRecord>,
        zone: ZoneId,
        read: (HealthRecord) -> Double?,
    ): Map<String, Map<String, Double>> {
        val out = LinkedHashMap<String, MutableMap<String, Double>>()
        val seen = HashSet<String>()
        for (r in records) {
            val v = read(r) ?: continue
            if (!v.isFinite()) continue
            val origin = originOf(r)
            val sig = "$origin|${r.start}|${r.end}"
            if (!seen.add(sig)) continue
            val k = dayKeyFromIso(r.start, zone)
            val bySrc = out.getOrPut(k) { LinkedHashMap() }
            bySrc[origin] = (bySrc[origin] ?: 0.0) + v
        }
        return out
    }

    /** Max across origins per day (never the sum — devices double-report). */
    fun maxAcrossOrigins(
        records: List<HealthRecord>,
        zone: ZoneId,
        read: (HealthRecord) -> Double?,
    ): Pair<Map<String, Double>, Map<String, String>> {
        val value = LinkedHashMap<String, Double>()
        val source = LinkedHashMap<String, String>()
        for ((k, bySrc) in perOriginDaily(records, zone, read)) {
            var best = ""
            var max = -1.0
            for ((app, total) in bySrc) if (total > max) { max = total; best = app }
            value[k] = max
            source[k] = best
        }
        return value to source
    }
}
