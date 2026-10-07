package com.scatterbrain.scatterfit.data

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Health Connect record values -> the gateway's wire shape, in pure Kotlin.
 *
 *  The web's parse pipeline consumes hcgateway records: HC's own JSON layout
 *  (`data.weight.inKilograms`, `data.stages[].stage` as a bare name like
 *  "LIGHT", `data.exerciseType` as the raw int). The readers' whole job is to
 *  emit that SAME shape from the Health Connect SDK, so the proven parse path
 *  runs untouched. Every field here is pinned by HcTranslateTest against
 *  web_records.json — bit-identical or it fails.
 *
 *  The Android half (HcReaders) only extracts primitives from SDK records and
 *  calls in here; no HC type survives past that one file. */
object HcTranslate {

    private val UTC: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

    /** Gateway shape: always UTC with exactly 3-digit millis ("...T01:04:07.867Z"). */
    fun iso(ms: Long): String = UTC.format(Instant.ofEpochMilli(ms))

    // -- Sleep stages (SleepSessionRecord.StageType constants, connect-client 1.1.0) --
    fun stageName(stageType: Int): String = when (stageType) {
        0 -> "UNKNOWN"; 1 -> "AWAKE"; 2 -> "SLEEPING"; 3 -> "OUT_OF_BED"
        4 -> "LIGHT"; 5 -> "DEEP"; 6 -> "REM"; 7 -> "AWAKE_IN_BED"
        else -> "UNKNOWN"
    }

    // -- Meal types (MealType constants, connect-client 1.1.0) --
    fun mealName(mealType: Int): String = when (mealType) {
        1 -> "breakfast"; 2 -> "lunch"; 3 -> "dinner"; 4 -> "snack"
        else -> "unknown"
    }

    /** Emit integral values as ints (the gateway's JSON keeps 3361, 137 as ints;
     *  fractional values stay doubles — 80.93). */
    internal fun num(x: Double): Any = if (x == x.toLong().toDouble()) x.toLong() else x

    fun steps(app: String, startMs: Long, endMs: Long, count: Long): HealthRecord =
        LocalRecordConvert.convert(
            RecordMethod.STEPS,
            LocalRecord(app = app, start = iso(startMs), end = iso(endMs),
                data = mapOf("count" to count)),
        )

    fun distance(app: String, startMs: Long, endMs: Long, meters: Double): HealthRecord =
        LocalRecordConvert.convert(
            RecordMethod.DISTANCE,
            LocalRecord(app = app, start = iso(startMs), end = iso(endMs),
                data = mapOf("distance" to mapOf("inMeters" to num(meters)))),
        )

    fun totalCaloriesBurned(app: String, startMs: Long, endMs: Long, kcal: Double): HealthRecord =
        LocalRecordConvert.convert(
            RecordMethod.TOTAL_CALORIES_BURNED,
            LocalRecord(app = app, start = iso(startMs), end = iso(endMs),
                data = mapOf("energy" to mapOf("inKilocalories" to num(kcal)))),
        )

    fun weight(app: String, ms: Long, kg: Double): HealthRecord =
        LocalRecordConvert.convert(
            RecordMethod.WEIGHT,
            LocalRecord(app = app, start = iso(ms), end = iso(ms),
                data = mapOf("weight" to mapOf("inKilograms" to num(kg)))),
        )

    fun restingHeartRate(app: String, ms: Long, bpm: Long): HealthRecord =
        LocalRecordConvert.convert(
            RecordMethod.RESTING_HEART_RATE,
            LocalRecord(app = app, start = iso(ms), end = iso(ms),
                data = mapOf("beatsPerMinute" to bpm)),
        )

    fun bodyFat(app: String, ms: Long, percentage: Double): HealthRecord =
        LocalRecordConvert.convert(
            RecordMethod.BODY_FAT,
            LocalRecord(app = app, start = iso(ms), end = iso(ms),
                data = mapOf("percentage" to num(percentage))),
        )

    data class StageInput(val startMs: Long, val endMs: Long, val stageType: Int)

    fun sleepSession(app: String, startMs: Long, endMs: Long, title: String?, stages: List<StageInput>): HealthRecord {
        val data = HashMap<String, Any?>()
        if (title != null) data["title"] = title
        data["stages"] = stages.map { s ->
            mapOf("startTime" to iso(s.startMs), "endTime" to iso(s.endMs), "stage" to stageName(s.stageType))
        }
        return LocalRecordConvert.convert(
            RecordMethod.SLEEP_SESSION,
            LocalRecord(app = app, start = iso(startMs), end = iso(endMs), data = data),
        )
    }

    fun exerciseSession(app: String, startMs: Long, endMs: Long, title: String?, exerciseType: Int): HealthRecord {
        val data = HashMap<String, Any?>()
        if (title != null) data["title"] = title
        data["exerciseType"] = exerciseType
        return LocalRecordConvert.convert(
            RecordMethod.EXERCISE_SESSION,
            LocalRecord(app = app, start = iso(startMs), end = iso(endMs), data = data),
        )
    }

    data class SampleInput(val timeMs: Long, val bpm: Long)

    fun heartRate(app: String, startMs: Long, endMs: Long, samples: List<SampleInput>): HealthRecord {
        val data = mapOf(
            "samples" to samples.map { s ->
                mapOf("time" to iso(s.timeMs), "beatsPerMinute" to s.bpm)
            },
        )
        return LocalRecordConvert.convert(
            RecordMethod.HEART_RATE,
            LocalRecord(app = app, start = iso(startMs), end = iso(endMs), data = data),
        )
    }

    /** Optional fields (name, meal, macros) only when the app wrote them — the
     *  converter drops nulls, matching the gateway's sparse records. */
    fun nutrition(app: String, startMs: Long, endMs: Long, name: String?, mealType: Int,
                  kcal: Double?, proteinG: Double?, carbG: Double?, fatG: Double?): HealthRecord {
        val data = HashMap<String, Any?>()
        if (name != null) data["name"] = name
        data["mealType"] = mealName(mealType)
        if (kcal != null) data["energy"] = mapOf("inKilocalories" to num(kcal))
        if (proteinG != null) data["protein"] = mapOf("inGrams" to num(proteinG))
        if (carbG != null) data["carbohydrates"] = mapOf("inGrams" to num(carbG))
        if (fatG != null) data["fat"] = mapOf("inGrams" to num(fatG))
        return LocalRecordConvert.convert(
            RecordMethod.NUTRITION,
            LocalRecord(app = app, start = iso(startMs), end = iso(endMs), data = data),
        )
    }

    /** Unused by the web parse (BMR comes from settings, Mifflin-St Jeor) —
     *  kept for parity with RECORD_METHODS. Wire shape: Power in watts. */
    fun basalMetabolicRate(app: String, ms: Long, watts: Double): HealthRecord =
        LocalRecordConvert.convert(
            RecordMethod.BASAL_METABOLIC_RATE,
            LocalRecord(app = app, start = iso(ms), end = iso(ms),
                data = mapOf("basalMetabolicRate" to mapOf("inWatts" to num(watts)))),
        )
}
