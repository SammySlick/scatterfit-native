package com.scatterbrain.scatterfit.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** LocalRecord -> HealthRecord converter (Foundations: the Health Connect readers
 *  and demo mode emit the SAME record shape into ONE parse pipeline).
 *
 *  A LocalRecord carries plain Maps (no kotlinx-serialization knowledge needed at
 *  the source); this converter lifts the numbers into a JsonObject with the same
 *  nesting the web writes. JSON object values that are maps recurse; lists and
 *  scalars become primitives. Method is implied by the caller's bucket. */
object LocalRecordConvert {

    /** Any Map with scalar values -> JsonObject; nested maps AND lists recurse
     *  (sleep records carry a `stages` array of objects, HR carries `samples` —
     *  flattening those to strings broke every downstream parse). */
    fun toJsonObject(map: Map<*, *>): JsonObject = buildJsonObject {
        for ((k, v) in map) if (v != null) put(k.toString(), toJsonValue(v)) // nulls omitted, like the web's record serialisation
    }

    private fun toJsonValue(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is Map<*, *> -> toJsonObject(v)
        is List<*> -> buildJsonArray { v.forEach { add(toJsonValue(it)) } }
        is Int -> JsonPrimitive(v) // web records keep ints as ints (beatsPerMinute: 137) — don't widen to doubles
        is Long -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v.toDouble())
        is Boolean -> JsonPrimitive(v)
        else -> JsonPrimitive(v.toString())
    }

    /** One record. `app` is a record-level field in the gateway shape (the web
     *  interface puts it beside start/end, never inside data); `data` passes
     *  through verbatim. */
    fun convert(method: RecordMethod, r: LocalRecord): HealthRecord {
        return HealthRecord(app = r.app, start = r.start, end = r.end, data = r.data?.let { toJsonObject(it) })
    }

    fun convertAll(method: RecordMethod, list: List<LocalRecord>): List<HealthRecord> =
        list.map { convert(method, it) }
}
