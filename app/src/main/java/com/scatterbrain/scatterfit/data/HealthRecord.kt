package com.scatterbrain.scatterfit.data

import kotlinx.serialization.json.JsonObject

/** One Health Connect / hcgateway record. `data` is the raw JSON body,
 *  exactly as the web's hcgateway.ts stores it. Port of web HealthRecord. */
data class HealthRecord(
    val app: String? = null,
    val start: String,
    val end: String? = null,
    val data: JsonObject? = null,
)

/** Record fetch types (web types.ts RECORD_METHODS). */
enum class RecordMethod {
    STEPS, DISTANCE, NUTRITION, FOOD_LOG, TOTAL_CALORIES_BURNED, WEIGHT,
    RESTING_HEART_RATE, SLEEP_SESSION, EXERCISE_SESSION, BODY_FAT, HEART_RATE, BASAL_METABOLIC_RATE
}

/** Single data-hygiene gate every metric reads through: drops blocked apps
 *  for all record types, plus physiological sanity guards per type.
 *  Port of web records.ts cleanRecords (the cache/fetch parts are app-level). */
object Records {
    // Records from any app/data origin containing this are never trusted.
    private val BLOCKED_APP_PATTERN = Regex("myzone", RegexOption.IGNORE_CASE)
    val HR_LEGACY_CUTOFF_MS: Long = 1788480000000L // 2026-09-05T00:00:00Z

    fun isBlockedApp(r: HealthRecord): Boolean {
        val d = r.data?.get("dataOrigin")?.jsonPrimOrNull() ?: r.data?.get("app")?.jsonPrimOrNull()
        return BLOCKED_APP_PATTERN.containsMatchIn(r.app ?: "") ||
            (d is String && BLOCKED_APP_PATTERN.containsMatchIn(d))
    }

    fun clean(method: RecordMethod, list: List<HealthRecord>?): List<HealthRecord> = (list ?: emptyList()).filter { r ->
        if (isBlockedApp(r)) return@filter false
        val start = parseMillis(r.start)
        when (method) {
            RecordMethod.HEART_RATE -> start != null && start >= HR_LEGACY_CUTOFF_MS
            RecordMethod.BODY_FAT -> {
                val v = Normalise.num(r.data?.get("percentage"))
                v != null && v > 0 && v <= 60
            }
            RecordMethod.WEIGHT -> {
                val v = Normalise.normaliseWeightKg(r.data)
                v != null && v >= 40 && v <= 200
            }
            else -> true
        }
    }
}
