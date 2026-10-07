package com.scatterbrain.scatterfit.data

import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime

/** String content of a JSON primitive, else null. */
fun JsonElement?.jsonPrimOrNull(): String? = (this as? JsonPrimitive)?.takeIf { !it.isString || it.contentOrNull != null }?.contentOrNull

fun JsonElement?.jsonStringOrNull(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

fun JsonElement?.jsonObjOrNull(): JsonObject? = this as? JsonObject

fun JsonElement?.jsonArrOrNull(): JsonArray? = this as? JsonArray

/** Millis for any of the ISO shapes the web pipes through Date.parse:
 *  instants with offsets, bare dates (UTC midnight), local date-times (UTC). */
fun parseMillis(iso: String?): Long? {
    if (iso == null) return null
    return try {
        Instant.parse(iso).toEpochMilli()
    } catch (_: Exception) {
        try {
            OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(iso).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            } catch (_: Exception) {
                try {
                    LocalDate.parse(iso).atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
}
