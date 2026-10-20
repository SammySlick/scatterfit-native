package com.scatterbrain.scatterfit.core

import kotlinx.serialization.Serializable
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Goal Layer Step 1: Core Maths + Goal History Model.
 * Spec: ScatterFit Native/Goal Layer Spec v1.md
 *
 * DECIDED 2026-10-20:
 * - Mifflin-St Jeor BMR for base burn calculation
 * - Activity multiplier factors: SEDENTARY(1.2), LIGHT(1.375), MODERATE(1.55), ACTIVE(1.725)
 * - Preset adjustments to TDEE:
 *     FAT_LOSS     = TDEE - 500 kcal, floor: never below BMR (clamped to BMR)
 *     MAINTENANCE  = TDEE
 *     BUILD_MUSCLE = TDEE + 250 kcal
 *     FITNESS      = TDEE
 * - Protein targets: FAT_LOSS = 2.0 g/kg, MAINTENANCE = 1.6 g/kg, BUILD_MUSCLE = 2.0 g/kg, FITNESS = 1.6 g/kg
 * - Remaining kcal = kcalTarget - (proteinG * 4)
 * - Remaining kcal split by preset ratio:
 *     FAT_LOSS:     carbs 40% / fat 60%
 *     MAINTENANCE:  carbs 50% / fat 50%
 *     BUILD_MUSCLE: carbs 60% / fat 40%
 *     FITNESS:      carbs 50% / fat 50%
 * - Kcal target rounded to nearest 10 for readability.
 */

@Serializable
enum class ActivityLevel(val factor: Double) {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    ACTIVE(1.725)
}

@Serializable
data class ComputedGoal(
    val preset: GoalPreset,
    val activityLevel: ActivityLevel,
    val kcalTarget: Int,
    val proteinTargetG: Int,
    val carbTargetG: Int,
    val fatTargetG: Int,
)

@Serializable
data class GoalChange(
    val date: String,          // ISO local date, e.g. "2026-10-20"
    val preset: GoalPreset,
    val activityLevel: ActivityLevel,
    val computed: ComputedGoal,
    val startWeightKg: Double?,
)

fun computeGoal(
    preset: GoalPreset,
    activityLevel: ActivityLevel,
    weightKg: Double,
    heightCm: Double,
    ageYears: Int,
    sex: MifflinStJeor.Sex = MifflinStJeor.Sex.MALE,
): ComputedGoal {
    val bmr = MifflinStJeor.calculateBmr(weightKg, heightCm, ageYears, sex)
    val tdee = bmr * activityLevel.factor

    // Preset adjustment (DECIDED 2026-10-20):
    // FAT_LOSS = TDEE - 500 (floor: never below BMR - if result would drop below BMR, clamp to BMR)
    // MAINTENANCE = TDEE
    // BUILD_MUSCLE = TDEE + 250
    // FITNESS = TDEE
    // CUSTOM = TDEE
    val rawKcal = when (preset) {
        GoalPreset.FAT_LOSS -> (tdee - 500.0).coerceAtLeast(bmr)
        GoalPreset.MAINTENANCE -> tdee
        GoalPreset.BUILD_MUSCLE -> tdee + 250.0
        GoalPreset.FITNESS -> tdee
        GoalPreset.CUSTOM -> tdee
    }

    // Round kcalTarget to nearest 10 for readability, floor at BMR for FAT_LOSS
    val roundedKcal = (round(rawKcal / 10.0) * 10).toInt()
    val kcalTarget = if (preset == GoalPreset.FAT_LOSS) {
        roundedKcal.coerceAtLeast(bmr.roundToInt())
    } else {
        roundedKcal
    }

    // Protein target: FAT_LOSS = 2.0 g/kg, MAINTENANCE = 1.6, BUILD_MUSCLE = 2.0, FITNESS = 1.6
    val proteinFactor = when (preset) {
        GoalPreset.FAT_LOSS, GoalPreset.BUILD_MUSCLE -> 2.0
        GoalPreset.MAINTENANCE, GoalPreset.FITNESS, GoalPreset.CUSTOM -> 1.6
    }
    val proteinTargetG = round(weightKg * proteinFactor).toInt()

    // Remaining kcal = kcalTarget - (proteinG * 4)
    val proteinKcal = proteinTargetG * 4
    val remainingKcal = (kcalTarget - proteinKcal).coerceAtLeast(0)

    // Split by preset ratio (constants, DECIDED 2026-10-20):
    // FAT_LOSS:     carbs 40% / fat 60%
    // MAINTENANCE:  carbs 50% / fat 50%
    // BUILD_MUSCLE: carbs 60% / fat 40%
    // FITNESS:      carbs 50% / fat 50%
    val (carbShare, fatShare) = when (preset) {
        GoalPreset.FAT_LOSS -> 0.40 to 0.60
        GoalPreset.MAINTENANCE -> 0.50 to 0.50
        GoalPreset.BUILD_MUSCLE -> 0.60 to 0.40
        GoalPreset.FITNESS, GoalPreset.CUSTOM -> 0.50 to 0.50
    }

    val carbTargetG = round(remainingKcal * carbShare / 4.0).toInt()
    val fatTargetG = round(remainingKcal * fatShare / 9.0).toInt()

    return ComputedGoal(
        preset = preset,
        activityLevel = activityLevel,
        kcalTarget = kcalTarget,
        proteinTargetG = proteinTargetG,
        carbTargetG = carbTargetG,
        fatTargetG = fatTargetG
    )
}
