package com.scatterbrain.scatterfit.core

import com.scatterbrain.scatterfit.data.GoalHistoryStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GoalCalculatorTest {

    @Test
    fun `known body maths - male 30y 180cm 80kg MODERATE pins exact BMR 1780 and TDEE 2759`() {
        // Mifflin-St Jeor: 10 * 80 + 6.25 * 180 - 5 * 30 + 5
        // = 800 + 1125 - 150 + 5 = 1780.0
        val bmr = MifflinStJeor.calculateBmr(
            weightKg = 80.0,
            heightCm = 180.0,
            ageYears = 30,
            sex = MifflinStJeor.Sex.MALE
        )
        assertEquals(1780.0, bmr, 0.001)

        val tdee = bmr * ActivityLevel.MODERATE.factor
        assertEquals(2759.0, tdee, 0.001)

        // MAINTENANCE:
        // kcalTarget: round(2759 / 10) * 10 = 2760
        // protein: 80 * 1.6 = 128 g (512 kcal)
        // remaining: 2760 - 512 = 2248 kcal
        // carbs (50%): round(2248 * 0.50 / 4) = 281 g
        // fat (50%): round(2248 * 0.50 / 9) = 125 g
        val maintainGoal = computeGoal(
            preset = GoalPreset.MAINTENANCE,
            activityLevel = ActivityLevel.MODERATE,
            weightKg = 80.0,
            heightCm = 180.0,
            ageYears = 30,
            sex = MifflinStJeor.Sex.MALE
        )
        assertEquals(
            ComputedGoal(
                preset = GoalPreset.MAINTENANCE,
                activityLevel = ActivityLevel.MODERATE,
                kcalTarget = 2760,
                proteinTargetG = 128,
                carbTargetG = 281,
                fatTargetG = 125
            ),
            maintainGoal
        )

        // BUILD_MUSCLE:
        // TDEE + 250 = 2759 + 250 = 3009 -> nearest 10: 3010 kcal
        // protein: 80 * 2.0 = 160 g (640 kcal)
        // remaining: 3010 - 640 = 2370 kcal
        // carbs (60%): round(2370 * 0.60 / 4) = 356 g
        // fat (40%): round(2370 * 0.40 / 9) = 105 g
        val buildGoal = computeGoal(
            preset = GoalPreset.BUILD_MUSCLE,
            activityLevel = ActivityLevel.MODERATE,
            weightKg = 80.0,
            heightCm = 180.0,
            ageYears = 30,
            sex = MifflinStJeor.Sex.MALE
        )
        assertEquals(
            ComputedGoal(
                preset = GoalPreset.BUILD_MUSCLE,
                activityLevel = ActivityLevel.MODERATE,
                kcalTarget = 3010,
                proteinTargetG = 160,
                carbTargetG = 356,
                fatTargetG = 105
            ),
            buildGoal
        )

        // FITNESS:
        // TDEE = 2759 -> nearest 10: 2760 kcal
        // protein: 80 * 1.6 = 128 g (512 kcal)
        // remaining: 2760 - 512 = 2248 kcal
        // carbs (50%): 281 g, fat (50%): 125 g
        val fitnessGoal = computeGoal(
            preset = GoalPreset.FITNESS,
            activityLevel = ActivityLevel.MODERATE,
            weightKg = 80.0,
            heightCm = 180.0,
            ageYears = 30,
            sex = MifflinStJeor.Sex.MALE
        )
        assertEquals(
            ComputedGoal(
                preset = GoalPreset.FITNESS,
                activityLevel = ActivityLevel.MODERATE,
                kcalTarget = 2760,
                proteinTargetG = 128,
                carbTargetG = 281,
                fatTargetG = 125
            ),
            fitnessGoal
        )
    }

    @Test
    fun `fat loss on known body yields TDEE minus 500 rounded and plausible macro sum`() {
        // TDEE = 2759.0
        // FAT_LOSS = TDEE - 500 = 2259.0
        // Nearest 10 = 2260 kcal
        // protein: 80 * 2.0 = 160 g (640 kcal)
        // remaining: 2260 - 640 = 1620 kcal
        // carbs (40%): 1620 * 0.40 / 4 = 162 g (648 kcal)
        // fat (60%): 1620 * 0.60 / 9 = 108 g (972 kcal)
        val fatLossGoal = computeGoal(
            preset = GoalPreset.FAT_LOSS,
            activityLevel = ActivityLevel.MODERATE,
            weightKg = 80.0,
            heightCm = 180.0,
            ageYears = 30,
            sex = MifflinStJeor.Sex.MALE
        )
        assertEquals(
            ComputedGoal(
                preset = GoalPreset.FAT_LOSS,
                activityLevel = ActivityLevel.MODERATE,
                kcalTarget = 2260,
                proteinTargetG = 160,
                carbTargetG = 162,
                fatTargetG = 108
            ),
            fatLossGoal
        )

        // Assert macro grams sum plausibly:
        // protein*4 + carbs*4 + fat*9 within rounding tolerance (documented max tolerance: 5 kcal)
        val macroKcalSum = fatLossGoal.proteinTargetG * 4 + fatLossGoal.carbTargetG * 4 + fatLossGoal.fatTargetG * 9
        val delta = abs(macroKcalSum - fatLossGoal.kcalTarget)
        assertTrue("Macro kcal sum ($macroKcalSum) within 5 kcal of kcalTarget (${fatLossGoal.kcalTarget})", delta <= 5)
        assertEquals(2260, macroKcalSum) // exact on this body!
    }

    @Test
    fun `floor - tiny elderly body where TDEE minus 500 is less than BMR clamps to BMR`() {
        // Female, 75y, 145cm, 40kg, SEDENTARY (factor 1.2)
        // BMR = 10*40 + 6.25*145 - 5*75 - 161 = 400 + 906.25 - 375 - 161 = 770.25
        // TDEE = 770.25 * 1.2 = 924.3
        // TDEE - 500 = 424.3, which is far below BMR (770.25)
        val bmr = MifflinStJeor.calculateBmr(
            weightKg = 40.0,
            heightCm = 145.0,
            ageYears = 75,
            sex = MifflinStJeor.Sex.FEMALE
        )
        assertEquals(770.25, bmr, 0.001)

        val goal = computeGoal(
            preset = GoalPreset.FAT_LOSS,
            activityLevel = ActivityLevel.SEDENTARY,
            weightKg = 40.0,
            heightCm = 145.0,
            ageYears = 75,
            sex = MifflinStJeor.Sex.FEMALE
        )

        // Clamped to at least BMR
        assertTrue("kcalTarget (${goal.kcalTarget}) must be >= BMR (${bmr.toInt()})", goal.kcalTarget >= bmr.toInt())
        assertEquals(770, goal.kcalTarget)
    }

    @Test
    fun `female vs male BMR differ by exactly 166`() {
        // Male offset = +5, Female offset = -161 -> 5 - (-161) = 166
        val maleBmr = MifflinStJeor.calculateBmr(
            weightKg = 70.0,
            heightCm = 175.0,
            ageYears = 28,
            sex = MifflinStJeor.Sex.MALE
        )
        val femaleBmr = MifflinStJeor.calculateBmr(
            weightKg = 70.0,
            heightCm = 175.0,
            ageYears = 28,
            sex = MifflinStJeor.Sex.FEMALE
        )
        assertEquals(166.0, maleBmr - femaleBmr, 0.001)
    }

    @Test
    fun `goal history storage ordering contract - append two changes, active is latest, earliest date first`() {
        val storage = GoalHistoryStorage()
        assertTrue(storage.loadHistory().isEmpty())
        assertNull(storage.activeGoal())

        val change1 = GoalChange(
            date = "2026-10-25",
            preset = GoalPreset.FAT_LOSS,
            activityLevel = ActivityLevel.MODERATE,
            computed = computeGoal(GoalPreset.FAT_LOSS, ActivityLevel.MODERATE, 80.0, 180.0, 30),
            startWeightKg = 80.0
        )
        storage.append(change1)

        assertEquals(1, storage.loadHistory().size)
        assertEquals(change1, storage.activeGoal())

        // Append change 2 with an earlier date ("2026-10-20")
        val change2 = GoalChange(
            date = "2026-10-20",
            preset = GoalPreset.MAINTENANCE,
            activityLevel = ActivityLevel.LIGHT,
            computed = computeGoal(GoalPreset.MAINTENANCE, ActivityLevel.LIGHT, 82.0, 180.0, 30),
            startWeightKg = 82.0
        )
        storage.append(change2)

        val history = storage.loadHistory()
        assertEquals(2, history.size)
        // Storage contract: earliest date first ordering
        assertEquals("2026-10-20", history[0].date)
        assertEquals(GoalPreset.MAINTENANCE, history[0].preset)
        assertEquals("2026-10-25", history[1].date)
        assertEquals(GoalPreset.FAT_LOSS, history[1].preset)

        // Storage contract: active goal = latest entry (last in ordered list)
        val active = storage.activeGoal()
        assertNotNull(active)
        assertEquals("2026-10-25", active!!.date)
        assertEquals(GoalPreset.FAT_LOSS, active.preset)
    }
}
