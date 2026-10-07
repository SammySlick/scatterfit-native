package com.scatterbrain.scatterfit.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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

    /** Any Map with scalar values -> JsonObject; nested maps recurse. Numbers
     *  keep double precision (web JSON has no int/float distinction). */
    fun toJsonObject(map: Map<*, *>): JsonObject = buildJsonObject {
        for ((k, v) in map) {
            val key = k.toString()
            when (v) {
                null -> {}
                is Map<*, *> -> put(key, toJsonObject(v))
                is List<*> -> put(key, JsonPrimitive(v.joinToString(",", "[", "]") { item -> item?.toString() ?: "" }))
                is Number -> put(key, JsonPrimitive(v.toDouble()))
                is Boolean -> put(key, v)
                else -> put(key, v.toString())
            }
        }
    }

    /** One record. Note: the SOURCE keeps its app string in [LocalRecord.app];
     *  readers that own a dataOrigin map put it in the data themselves (the web's
     *  hcgateway records carry it inside `data`), which is why the converted
     *  record only fills HealthRecord.app and the pipeline's clean() sees the
     *  right thing either way. */
    fun convert(method: RecordMethod, r: LocalRecord): HealthRecord {
        val data = r.data?.let { toJsonObject(it) }
        val enriched = if (r.app != null && data != null && data["app"] == null)
            JsonObject(data + mapOf("app" to JsonPrimitive(r.app))) else data
        return HealthRecord(app = r.app, start = r.start, end = r.end, data = enriched)
    }

    fun convertAll(method: RecordMethod, list: List<LocalRecord>): List<HealthRecord> =
        list.map { convert(method, it) }
}
