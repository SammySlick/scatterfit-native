package com.scatterbrain.scatterfit.data

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord

/** Health Connect permissions for everything the readers consume. Per-type
 *  READ permissions are also declared in AndroidManifest; this is the runtime
 *  request set. READ_HEALTH_DATA_HISTORY is required for anything older than
 *  30 days (first sync reaches back the full retention window). */
object HcPermissions {
    /** Plain permission strings — 1.1.0's getReadPermission returns String. */
    val read: Set<String> = buildSet {
        for (t in listOf(
            StepsRecord::class, DistanceRecord::class, NutritionRecord::class,
            TotalCaloriesBurnedRecord::class, WeightRecord::class,
            RestingHeartRateRecord::class, SleepSessionRecord::class,
            ExerciseSessionRecord::class, BodyFatRecord::class,
            HeartRateRecord::class, BasalMetabolicRateRecord::class,
        )) add(HealthPermission.getReadPermission(t))
        add(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
    }
}
