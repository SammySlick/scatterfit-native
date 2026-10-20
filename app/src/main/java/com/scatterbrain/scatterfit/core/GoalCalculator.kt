package com.scatterbrain.scatterfit.core

import kotlinx.serialization.Serializable
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Goal Layer Step 1 & 3: Core Maths + Goal History Model.
 * Spec: ScatterFit Native/Goal Layer Spec v1.md, Goal Set Flow Spec v1.md
 *
 * DECIDED 2026-10-20:
 * - Mifflin-St Jeor BMR for base burn calculation
 * - Activity multiplier factors: SEDENTARY(1.2), LIGHT(1.375), MODERATE(1.55), ACTIVE(1.725)
 * - Preset adjustments to TDEE:
 *     FAT_LOSS     = TDEE - 500 kcal (unclamped; belowBmr warning flag set if < BMR)
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
    val belowBmr: Boolean = false,
)

@Serializable
data class GoalChange(
    val date: String,          // ISO local date, e.g. "2026-10-20"
    val preset: GoalPreset,
    val activityLevel: ActivityLevel,
    val computed: ComputedGoal,
    val startWeightKg: Double?,
    val isManual: Boolean = false,
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
    // FAT_LOSS = TDEE - 500 (unclamped; belowBmr warning set if below BMR)
    // MAINTENANCE = TDEE
    // BUILD_MUSCLE = TDEE + 250
    // FITNESS = TDEE
    // CUSTOM = TDEE
    val rawKcal = when (preset) {
        GoalPreset.FAT_LOSS -> tdee - 500.0
        GoalPreset.MAINTENANCE -> tdee
        GoalPreset.BUILD_MUSCLE -> tdee + 250.0
        GoalPreset.FITNESS -> tdee
        GoalPreset.CUSTOM -> tdee
    }

    // Round kcalTarget to nearest 10 for readability
    val roundedKcal = (round(rawKcal / 10.0) * 10).toInt().coerceAtLeast(0)
    val isBelowBmr = preset == GoalPreset.FAT_LOSS && (tdee - 500.0 < bmr || roundedKcal < bmr.roundToInt())

    // Protein target: FAT_LOSS = 2.0 g/kg, MAINTENANCE = 1.6, BUILD_MUSCLE = 2.0, FITNESS = 1.6
    val proteinFactor = when (preset) {
        GoalPreset.FAT_LOSS, GoalPreset.BUILD_MUSCLE -> 2.0
        GoalPreset.MAINTENANCE, GoalPreset.FITNESS, GoalPreset.CUSTOM -> 1.6
    }
    val proteinTargetG = round(weightKg * proteinFactor).toInt()

    // Remaining kcal = kcalTarget - (proteinG * 4)
    val proteinKcal = proteinTargetG * 4
    val remainingKcal = (roundedKcal - proteinKcal).coerceAtLeast(0)

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
        kcalTarget = roundedKcal,
        proteinTargetG = proteinTargetG,
        carbTargetG = carbTargetG,
        fatTargetG = fatTargetG,
        belowBmr = isBelowBmr
    )
}

fun computeManualGoal(
    preset: GoalPreset,
    activityLevel: ActivityLevel,
    customKcal: Int,
    customProteinG: Int? = null,
    weightKg: Double? = null,
    bmr: Double? = null,
): ComputedGoal {
    val proteinFactor = when (preset) {
        GoalPreset.FAT_LOSS, GoalPreset.BUILD_MUSCLE -> 2.0
        GoalPreset.MAINTENANCE, GoalPreset.FITNESS, GoalPreset.CUSTOM -> 1.6
    }
    val proteinTargetG = customProteinG ?: weightKg?.let { round(it * proteinFactor).toInt() } ?: 0
    val remainingKcal = (customKcal - (proteinTargetG * 4)).coerceAtLeast(0)

    val (carbShare, fatShare) = when (preset) {
        GoalPreset.FAT_LOSS -> 0.40 to 0.60
        GoalPreset.MAINTENANCE -> 0.50 to 0.50
        GoalPreset.BUILD_MUSCLE -> 0.60 to 0.40
        GoalPreset.FITNESS, GoalPreset.CUSTOM -> 0.50 to 0.50
    }

    val carbTargetG = round(remainingKcal * carbShare / 4.0).toInt()
    val fatTargetG = round(remainingKcal * fatShare / 9.0).toInt()
    val isBelowBmr = if (bmr != null) customKcal < bmr.roundToInt() else false

    return ComputedGoal(
        preset = preset,
        activityLevel = activityLevel,
        kcalTarget = customKcal,
        proteinTargetG = proteinTargetG,
        carbTargetG = carbTargetG,
        fatTargetG = fatTargetG,
        belowBmr = isBelowBmr
    )
}
