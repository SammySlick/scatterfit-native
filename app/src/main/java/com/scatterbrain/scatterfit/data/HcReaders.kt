package com.scatterbrain.scatterfit.data

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

/** The ONLY file allowed to know Health Connect types exist (Foundations:
 *  "never leak HC types past this layer"). One reader per RecordMethod: read
 *  from the SDK, extract bare primitives, hand to HcTranslate — which emits
 *  the gateway wire shape the proven parse pipeline consumes.
 *
 *  Everything testable lives in HcTranslate + LocalRecordConvert (JVM tests).
 *  This file is deliberately dumb: no decisions, no maths, no filtering —
 *  just extraction. */
object HcReaders {

    /** Window is [fromMs, toMs). Note: sleep sessions may START before fromMs —
     *  a session crossing the cursor boundary is read on its start timestamp,
     *  same convention the web's fetch uses (start-gte/lt). Sync cursors are
     *  per method; the caller owns how far back fromMs reaches. */
    suspend fun readAll(
        client: HealthConnectClient,
        fromMs: Long,
        toMs: Long,
        methods: Set<RecordMethod> = RecordMethod.entries.toSet(),
    ): Map<RecordMethod, List<HealthRecord>> {
        val out = HashMap<RecordMethod, List<HealthRecord>>()
        for (m in methods) {
            val list = when (m) {
                RecordMethod.STEPS -> readSteps(client, fromMs, toMs)
                RecordMethod.DISTANCE -> readDistance(client, fromMs, toMs)
                RecordMethod.NUTRITION -> readNutrition(client, fromMs, toMs)
                RecordMethod.FOOD_LOG -> emptyList() // in-app logger, not HC — write path, separate store
                RecordMethod.TOTAL_CALORIES_BURNED -> readTotalCaloriesBurned(client, fromMs, toMs)
                RecordMethod.WEIGHT -> readWeight(client, fromMs, toMs)
                RecordMethod.RESTING_HEART_RATE -> readRestingHeartRate(client, fromMs, toMs)
                RecordMethod.SLEEP_SESSION -> readSleepSession(client, fromMs, toMs)
                RecordMethod.EXERCISE_SESSION -> readExerciseSession(client, fromMs, toMs)
                RecordMethod.BODY_FAT -> readBodyFat(client, fromMs, toMs)
                RecordMethod.HEART_RATE -> readHeartRate(client, fromMs, toMs)
                RecordMethod.BASAL_METABOLIC_RATE -> readBasalMetabolicRate(client, fromMs, toMs)
            }
            if (list.isNotEmpty()) out[m] = list
        }
        return out
    }

    private fun window(fromMs: Long, toMs: Long) =
        TimeRangeFilter.between(Instant.ofEpochMilli(fromMs), Instant.ofEpochMilli(toMs))

    private inline fun <reified T : Record> read(
        client: HealthConnectClient,
        fromMs: Long,
        toMs: Long,
        block: (T) -> HealthRecord,
    ): List<HealthRecord> {
        val out = ArrayList<HealthRecord>()
        var token: String? = null
        do {
            val response = client.readRecords(
                ReadRecordsRequest(window(fromMs, toMs), pageToken = token),
            )
            out.addAll(response.records.map(block))
            token = response.pageToken
        } while (token != null)
        return out
    }

    private fun readSteps(client: HealthConnectClient, fromMs: Long, toMs: Long) =
        read(client, fromMs, toMs) { r: StepsRecord ->
            HcTranslate.steps(r.metadata.dataOrigin.packageName, r.startTime.toEpochMilli(),
                r.endTime.toEpochMilli(), r.count)
        }

    private fun readDistance(client: HealthConnectClient, fromMs: Long, toMs: Long) =
        read(client, fromMs, toMs) { r: DistanceRecord ->
            HcTranslate.distance(r.metadata.dataOrigin.packageName, r.startTime.toEpochMilli(),
                r.endTime.toEpochMilli(), r.distance.inMeters)
        }

    private fun readNutrition(client: HealthConnectClient, fromMs: Long, toMs: Long) =
        read(client, fromMs, toMs) { r: NutritionRecord ->
            HcTranslate.nutrition(r.metadata.dataOrigin.packageName, r.startTime.toEpochMilli(),
                r.endTime.toEpochMilli(), r.name, r.mealType,
                r.energy?.inKilocalories, r.protein?.inGrams,
                r.totalCarbohydrate?.inGrams, r.totalFat?.inGrams)
        }

    private fun readTotalCaloriesBurned(client: HealthConnectClient, fromMs: Long, toMs: Long) =
        read(client, fromMs, toMs) { r: TotalCaloriesBurnedRecord ->
            HcTranslate.totalCaloriesBurned(r.metadata.dataOrigin.packageName, r.startTime.toEpochMilli(),
                r.endTime.toEpochMilli(), r.energy.inKilocalories)
        }

    private fun readWeight(client: HealthConnectClient, fromMs: Long, toMs: Long) =
        read(client, fromMs, toMs) { r: WeightRecord ->
            HcTranslate.weight(r.metadata.dataOrigin.packageName, r.time.toEpochMilli(), r.weight.inKilograms)
        }

    private fun readRestingHeartRate(client: HealthConnectClient, fromMs: Long, toMs: Long) =
        read(client, fromMs, toMs) { r: RestingHeartRateRecord ->
            HcTranslate.restingHeartRate(r.metadata.dataOrigin.packageName, r.time.toEpochMilli(), r.beatsPerMinute)
        }

    private fun readSleepSession(client: HealthConnectClient, fromMs: Long, toMs: Long) =
        read(client, fromMs, toMs) { r: SleepSessionRecord ->
            HcTranslate.sleepSession(r.metadata.dataOrigin.packageName, r.startTime.toEpochMilli(),
                r.endTime.toEpochMilli(), r.title,
                r.stages.map { HcTranslate.StageInput(it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), it.stage) })
        }

    private fun readExerciseSession(client: HealthConnectClient, fromMs: Long, toMs: Long) =
        read(client, fromMs, toMs) { r: ExerciseSessionRecord ->
            HcTranslate.exerciseSession(r.metadata.dataOrigin.packageName, r.startTime.toEpochMilli(),
                r.endTime.toEpochMilli(), r.title, r.exerciseType)
        }

    private fun readBodyFat(client: HealthConnectClient, fromMs: Long, toMs: Long) =
        read(client, fromMs, toMs) { r: BodyFatRecord ->
            HcTranslate.bodyFat(r.metadata.dataOrigin.packageName, r.time.toEpochMilli(), r.percentage.value)
        }

    private fun readHeartRate(client: HealthConnectClient, fromMs: Long, toMs: Long) =
        read(client, fromMs, toMs) { r: HeartRateRecord ->
            HcTranslate.heartRate(r.metadata.dataOrigin.packageName, r.startTime.toEpochMilli(),
                r.endTime.toEpochMilli(), r.samples.map { HcTranslate.SampleInput(it.time.toEpochMilli(), it.beatsPerMinute) })
        }

    private fun readBasalMetabolicRate(client: HealthConnectClient, fromMs: Long, toMs: Long) =
        read(client, fromMs, toMs) { r: BasalMetabolicRateRecord ->
            HcTranslate.basalMetabolicRate(r.metadata.dataOrigin.packageName, r.time.toEpochMilli(),
                r.basalMetabolicRate.inWatts)
        }
}
