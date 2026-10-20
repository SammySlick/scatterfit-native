package com.scatterbrain.scatterfit.data

import android.content.Context
import android.content.SharedPreferences
import com.scatterbrain.scatterfit.core.GoalPreset
import com.scatterbrain.scatterfit.core.ScoringSettings

class SettingsStorage(private val prefs: SharedPreferences? = null) {
    constructor(context: Context) : this(
        context.getSharedPreferences("scatterfit_settings_prefs", Context.MODE_PRIVATE)
    )

    fun loadSettings(): ScoringSettings {
        if (prefs == null) return ScoringSettings()
        val kcal = prefs.getInt("kcal_target", 2500)
        val protein = prefs.getInt("protein_target", 160)
        val carbs = prefs.getInt("carb_target_g", 200)
        val fat = prefs.getInt("fat_target_g", 70)
        val activeGoalStr = prefs.getString("active_goal", null)
        val activeGoal = activeGoalStr?.let {
            try { GoalPreset.valueOf(it) } catch (_: Exception) { GoalPreset.FAT_LOSS }
        } ?: GoalPreset.FAT_LOSS
        return ScoringSettings(
            kcalTarget = kcal,
            proteinTarget = protein,
            carbTargetG = carbs,
            fatTargetG = fat,
            activeGoal = activeGoal
        )
    }

    fun saveGoalTargets(
        kcalTarget: Int,
        proteinTarget: Int,
        carbTargetG: Int,
        fatTargetG: Int,
        activeGoal: GoalPreset
    ) {
        prefs?.edit()
            ?.putInt("kcal_target", kcalTarget)
            ?.putInt("protein_target", proteinTarget)
            ?.putInt("carb_target_g", carbTargetG)
            ?.putInt("fat_target_g", fatTargetG)
            ?.putString("active_goal", activeGoal.name)
            ?.apply()
    }
}
