package com.scatterbrain.scatterfit.data

import android.content.Context
import android.content.SharedPreferences
import com.scatterbrain.scatterfit.core.GoalPreset
import com.scatterbrain.scatterfit.core.MifflinStJeor
import com.scatterbrain.scatterfit.core.ScoringSettings

class SettingsStorage(private val prefs: SharedPreferences? = null) {
    constructor(context: Context) : this(
        context.getSharedPreferences("scatterfit_settings_prefs", Context.MODE_PRIVATE)
    )

    private var inMemorySettings: ScoringSettings? = null

    fun loadSettings(): ScoringSettings {
        if (prefs == null) {
            return inMemorySettings ?: ScoringSettings(
                carbTargetG = 0,
                fatTargetG = 0,
                heightCm = 0,
                ageYears = 0,
                weightTarget = 0.0
            )
        }
        val kcal = prefs.getInt("kcal_target", 2500)
        val protein = prefs.getInt("protein_target", 160)
        val carbs = if (prefs.contains("carb_target_g")) prefs.getInt("carb_target_g", 0) else 0
        val fat = if (prefs.contains("fat_target_g")) prefs.getInt("fat_target_g", 0) else 0
        val activeGoalStr = prefs.getString("active_goal", null)
        val activeGoal = activeGoalStr?.let {
            try { GoalPreset.valueOf(it) } catch (_: Exception) { GoalPreset.FAT_LOSS }
        } ?: GoalPreset.FAT_LOSS
        val height = if (prefs.contains("height_cm")) prefs.getInt("height_cm", 0) else 0
        val age = if (prefs.contains("age_years")) prefs.getInt("age_years", 0) else 0
        val sexStr = prefs.getString("sex", null)
        val sex = sexStr?.let {
            try { MifflinStJeor.Sex.valueOf(it) } catch (_: Exception) { MifflinStJeor.Sex.MALE }
        } ?: MifflinStJeor.Sex.MALE
        val weightTarget = if (prefs.contains("weight_target_kg")) prefs.getFloat("weight_target_kg", 0f).toDouble() else 0.0

        return ScoringSettings(
            kcalTarget = kcal,
            proteinTarget = protein,
            carbTargetG = carbs,
            fatTargetG = fat,
            activeGoal = activeGoal,
            heightCm = height,
            ageYears = age,
            sex = sex,
            weightTarget = weightTarget
        )
    }

    fun saveGoalTargets(
        kcalTarget: Int,
        proteinTarget: Int,
        carbTargetG: Int,
        fatTargetG: Int,
        activeGoal: GoalPreset,
        heightCm: Int? = null,
        ageYears: Int? = null,
        sex: MifflinStJeor.Sex? = null,
        weightTargetKg: Double? = null,
    ) {
        if (prefs == null) {
            inMemorySettings = ScoringSettings(
                kcalTarget = kcalTarget,
                proteinTarget = proteinTarget,
                carbTargetG = carbTargetG,
                fatTargetG = fatTargetG,
                activeGoal = activeGoal,
                heightCm = heightCm ?: 0,
                ageYears = ageYears ?: 0,
                sex = sex ?: MifflinStJeor.Sex.MALE,
                weightTarget = weightTargetKg ?: 0.0
            )
            return
        }
        val editor = prefs.edit()
        editor.putInt("kcal_target", kcalTarget)
            .putInt("protein_target", proteinTarget)
            .putInt("carb_target_g", carbTargetG)
            .putInt("fat_target_g", fatTargetG)
            .putString("active_goal", activeGoal.name)
        if (heightCm != null) {
            editor.putInt("height_cm", heightCm)
        }
        if (ageYears != null) {
            editor.putInt("age_years", ageYears)
        }
        if (sex != null) {
            editor.putString("sex", sex.name)
        }
        if (weightTargetKg != null && weightTargetKg > 0.0) {
            editor.putFloat("weight_target_kg", weightTargetKg.toFloat())
        } else if (weightTargetKg != null) {
            editor.remove("weight_target_kg")
        }
        editor.apply()
    }
}
