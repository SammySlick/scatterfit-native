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

    private val inMemoryMap = mutableMapOf<String, Any>()
    private var inMemorySettings: ScoringSettings? = null

    fun loadSettings(): ScoringSettings {
        if (prefs == null) {
            return inMemorySettings ?: ScoringSettings(
                carbTargetG = (inMemoryMap["carb_target_g"] as? Int) ?: 0,
                fatTargetG = (inMemoryMap["fat_target_g"] as? Int) ?: 0,
                heightCm = (inMemoryMap["height_cm"] as? Int) ?: 0,
                ageYears = (inMemoryMap["age_years"] as? Int) ?: 0,
                weightTarget = (inMemoryMap["weight_target_kg"] as? Double) ?: 0.0,
                stepTarget = (inMemoryMap["step_target"] as? Int) ?: 10000,
                sleepTargetHours = (inMemoryMap["sleep_target_hours"] as? Double) ?: 8.0,
                unitsPerWeek = (inMemoryMap["units_per_week"] as? Int) ?: 14,
                z2Target = (inMemoryMap["z2_target"] as? Int) ?: 90,
                z3Target = (inMemoryMap["z3_target"] as? Int) ?: 45,
                z4Target = (inMemoryMap["z4_target"] as? Int) ?: 30,
                z5Target = (inMemoryMap["z5_target"] as? Int) ?: 15,
                exerciseTargetPerWeek = (inMemoryMap["exercise_target_per_week"] as? Int) ?: 180
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

        val stepTarget = prefs.getInt("step_target", 10000)
        val sleepTarget = prefs.getFloat("sleep_target_hours", 8.0f).toDouble()
        val units = prefs.getInt("units_per_week", 14)
        val z2 = prefs.getInt("z2_target", 90)
        val z3 = prefs.getInt("z3_target", 45)
        val z4 = prefs.getInt("z4_target", 30)
        val z5 = prefs.getInt("z5_target", 15)
        val exerciseTarget = prefs.getInt("exercise_target_per_week", 180)

        return ScoringSettings(
            kcalTarget = kcal,
            proteinTarget = protein,
            carbTargetG = carbs,
            fatTargetG = fat,
            activeGoal = activeGoal,
            heightCm = height,
            ageYears = age,
            sex = sex,
            weightTarget = weightTarget,
            stepTarget = stepTarget,
            sleepTargetHours = sleepTarget,
            unitsPerWeek = units,
            z2Target = z2,
            z3Target = z3,
            z4Target = z4,
            z5Target = z5,
            exerciseTargetPerWeek = exerciseTarget
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

    fun saveStepTarget(target: Int) {
        if (prefs == null) {
            inMemoryMap["step_target"] = target
            inMemorySettings = loadSettings().copy(stepTarget = target)
            return
        }
        prefs.edit().putInt("step_target", target).apply()
    }

    fun saveSleepTarget(hours: Double) {
        if (prefs == null) {
            inMemoryMap["sleep_target_hours"] = hours
            inMemorySettings = loadSettings().copy(sleepTargetHours = hours)
            return
        }
        prefs.edit().putFloat("sleep_target_hours", hours.toFloat()).apply()
    }

    fun saveAlcoholTarget(unitsPerWeek: Int) {
        if (prefs == null) {
            inMemoryMap["units_per_week"] = unitsPerWeek
            inMemorySettings = loadSettings().copy(unitsPerWeek = unitsPerWeek)
            return
        }
        prefs.edit().putInt("units_per_week", unitsPerWeek).apply()
    }

    fun saveExerciseTargets(
        timesPerWeek: Int,
        isSimple: Boolean,
        z2: Int,
        z3: Int,
        z4: Int,
        z5: Int
    ) {
        val totalMin = z2 + z3 + z4 + z5
        if (prefs == null) {
            inMemoryMap["exercise_times_per_week"] = timesPerWeek
            inMemoryMap["exercise_mode_simple"] = isSimple
            inMemoryMap["z2_target"] = z2
            inMemoryMap["z3_target"] = z3
            inMemoryMap["z4_target"] = z4
            inMemoryMap["z5_target"] = z5
            inMemoryMap["exercise_target_per_week"] = totalMin
            inMemorySettings = loadSettings().copy(
                z2Target = z2,
                z3Target = z3,
                z4Target = z4,
                z5Target = z5,
                exerciseTargetPerWeek = totalMin
            )
            return
        }
        prefs.edit()
            .putInt("exercise_times_per_week", timesPerWeek)
            .putBoolean("exercise_mode_simple", isSimple)
            .putInt("z2_target", z2)
            .putInt("z3_target", z3)
            .putInt("z4_target", z4)
            .putInt("z5_target", z5)
            .putInt("exercise_target_per_week", totalMin)
            .apply()
    }

    fun saveCaloriesMacroTargets(
        kcal: Int,
        protein: Int,
        carbs: Int,
        fat: Int,
        isModeA: Boolean,
        proteinPerLb: Double? = null,
        pPct: Int? = null,
        cPct: Int? = null,
        fPct: Int? = null,
    ) {
        if (prefs == null) {
            inMemoryMap["calories_mode_a"] = isModeA
            proteinPerLb?.let { inMemoryMap["protein_per_lb"] = it }
            pPct?.let { inMemoryMap["macro_split_p"] = it }
            cPct?.let { inMemoryMap["macro_split_c"] = it }
            fPct?.let { inMemoryMap["macro_split_f"] = it }
            inMemoryMap["kcal_target"] = kcal
            inMemoryMap["protein_target"] = protein
            inMemoryMap["carb_target_g"] = carbs
            inMemoryMap["fat_target_g"] = fat
            inMemorySettings = loadSettings().copy(
                kcalTarget = kcal,
                proteinTarget = protein,
                carbTargetG = carbs,
                fatTargetG = fat
            )
            return
        }
        val editor = prefs.edit()
            .putInt("kcal_target", kcal)
            .putInt("protein_target", protein)
            .putInt("carb_target_g", carbs)
            .putInt("fat_target_g", fat)
            .putBoolean("calories_mode_a", isModeA)
        if (proteinPerLb != null) {
            editor.putFloat("protein_per_lb", proteinPerLb.toFloat())
        }
        if (pPct != null) editor.putInt("macro_split_p", pPct)
        if (cPct != null) editor.putInt("macro_split_c", cPct)
        if (fPct != null) editor.putInt("macro_split_f", fPct)
        editor.apply()
    }

    fun isCaloriesModeA(): Boolean {
        if (prefs == null) return (inMemoryMap["calories_mode_a"] as? Boolean) ?: true
        return prefs.getBoolean("calories_mode_a", true)
    }

    fun setCaloriesModeA(value: Boolean) {
        if (prefs == null) { inMemoryMap["calories_mode_a"] = value; return }
        prefs.edit().putBoolean("calories_mode_a", value).apply()
    }

    fun getProteinPerLb(): Double {
        if (prefs == null) return (inMemoryMap["protein_per_lb"] as? Double) ?: 1.0
        return prefs.getFloat("protein_per_lb", 1.0f).toDouble()
    }

    fun setProteinPerLb(value: Double) {
        if (prefs == null) { inMemoryMap["protein_per_lb"] = value; return }
        prefs.edit().putFloat("protein_per_lb", value.toFloat()).apply()
    }

    fun getMacroSplitPercentages(): Triple<Int, Int, Int> {
        if (prefs == null) {
            val p = (inMemoryMap["macro_split_p"] as? Int) ?: 30
            val c = (inMemoryMap["macro_split_c"] as? Int) ?: 40
            val f = (inMemoryMap["macro_split_f"] as? Int) ?: 30
            return Triple(p, c, f)
        }
        val p = prefs.getInt("macro_split_p", 30)
        val c = prefs.getInt("macro_split_c", 40)
        val f = prefs.getInt("macro_split_f", 30)
        return Triple(p, c, f)
    }

    fun setMacroSplitPercentages(p: Int, c: Int, f: Int) {
        if (prefs == null) {
            inMemoryMap["macro_split_p"] = p
            inMemoryMap["macro_split_c"] = c
            inMemoryMap["macro_split_f"] = f
            return
        }
        prefs.edit()
            .putInt("macro_split_p", p)
            .putInt("macro_split_c", c)
            .putInt("macro_split_f", f)
            .apply()
    }

    fun isExerciseModeSimple(): Boolean {
        if (prefs == null) return (inMemoryMap["exercise_mode_simple"] as? Boolean) ?: true
        return prefs.getBoolean("exercise_mode_simple", true)
    }

    fun setExerciseModeSimple(value: Boolean) {
        if (prefs == null) { inMemoryMap["exercise_mode_simple"] = value; return }
        prefs.edit().putBoolean("exercise_mode_simple", value).apply()
    }

    fun getExerciseTimesPerWeek(): Int {
        if (prefs == null) return (inMemoryMap["exercise_times_per_week"] as? Int) ?: 4
        return prefs.getInt("exercise_times_per_week", 4)
    }

    fun setExerciseTimesPerWeek(value: Int) {
        if (prefs == null) { inMemoryMap["exercise_times_per_week"] = value; return }
        prefs.edit().putInt("exercise_times_per_week", value).apply()
    }
}
