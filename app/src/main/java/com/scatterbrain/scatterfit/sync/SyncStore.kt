package com.scatterbrain.scatterfit.sync

import com.scatterbrain.scatterfit.data.HealthRecord
import com.scatterbrain.scatterfit.data.RecordMethod
import com.scatterbrain.scatterfit.data.jsonPrimOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File

/** File-backed cache of read records + per-method sync cursors.
 *  Foundations: sync/ holds "incremental cursors per record type". The cache
 *  is the gateway-wire JSON shape (HealthRecord as {app, start, end, data}) —
 *  the same shape HcTranslate emits, so the files are human-inspectable and
 *  the parse pipeline round-trips them unchanged. One file per method under
 *  [dir]/<METHOD>.json: {"cursor": <ms>, "records": [...]}.
 *
 *  Pure File IO on an injected [File] — no Android Context, JVM-testable. */
class SyncStore(private val dir: File) {

    data class MethodState(val cursorMs: Long?, val records: List<HealthRecord>)

    init {
        dir.mkdirs()
    }

    fun load(method: RecordMethod): MethodState {
        val f = file(method)
        if (!f.exists()) return MethodState(null, emptyList())
        return try {
            val obj = Json.parseToJsonElement(f.readText()).jsonObject
            val cursor = obj["cursor"]?.jsonPrimOrNull()?.toLongOrNull()
            val records = obj["records"]?.jsonArray?.mapNotNull { el ->
                (el as? JsonObject)?.let(::toRecord)
            } ?: emptyList()
            MethodState(cursor, records)
        } catch (_: Exception) {
            MethodState(null, emptyList()) // corrupt cache = cold start, not a crash
        }
    }

    fun save(method: RecordMethod, cursorMs: Long, records: List<HealthRecord>) {
        val obj = JsonObject(
            mapOf("cursor" to JsonPrimitive(cursorMs), "records" to JsonArray(records.map { toJson(it) })),
        )
        val f = file(method)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(obj.toString())
        if (!tmp.renameTo(f)) {
            f.writeText(obj.toString()) // atomic rename can fail across FS boundaries
        }
    }

    private fun file(method: RecordMethod) = File(dir, "${method.name}.json")

    private fun toRecord(o: JsonObject): HealthRecord? {
        val start = o["start"]?.jsonPrimOrNull() ?: return null
        return HealthRecord(
            app = o["app"]?.jsonPrimOrNull(),
            start = start,
            end = o["end"]?.jsonPrimOrNull(),
            data = o["data"] as? JsonObject,
        )
    }

    companion object {
        /** Wire shape exactly as the gateway stores it: app at record level,
         *  data payload only (record shape pinned by web hcgateway.ts). */
        fun toJson(r: HealthRecord): JsonObject = JsonObject(
            buildMap {
                r.app?.let { put("app", JsonPrimitive(it)) }
                put("start", JsonPrimitive(r.start))
                r.end?.let { put("end", JsonPrimitive(it)) }
                r.data?.let { put("data", it) }
            },
        )

        /** Identity for dedupe: method + start + end. Two re-reads of the same
         *  physical record share it; an edit (new content, extended end) also
         *  shares it, so the fresh read must win over the stale cached row —
         *  the engine inserts new records last so they overwrite on collision. */
        fun key(method: RecordMethod, r: HealthRecord): String = "$method|${r.start}|${r.end ?: ""}"
    }
}
