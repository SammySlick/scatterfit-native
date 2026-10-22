package com.scatterbrain.scatterfit.ui

import com.scatterbrain.scatterfit.core.ActivityLevel
import com.scatterbrain.scatterfit.core.GoalPreset
import com.scatterbrain.scatterfit.core.MifflinStJeor
import com.scatterbrain.scatterfit.core.computeGoal
import com.scatterbrain.scatterfit.data.SettingsStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class GoalSetTruthTest {

    @Test
    fun `computeGoal with real body data 92kg 172cm 36y male Sedentary FAT_LOSS matches exact targets`() {
        // Sam's real numbers: 92kg / 172cm / 36y / Male / Sedentary / Fat Loss
        val bmr = MifflinStJeor.calculateBmr(
            weightKg = 92.0,
            heightCm = 172.0,
            ageYears = 36,
            sex = MifflinStJeor.Sex.MALE
        )
        // BMR = 10*92 + 6.25*172 - 5*36 + 5 = 920 + 1075 - 180 + 5 = 1820
        assertEquals(1820.0, bmr, 0.001)

        val tdee = bmr * ActivityLevel.SEDENTARY.factor
        // TDEE = 1820 * 1.2 = 2184
        assertEquals(2184.0, tdee, 0.001)

        val computed = computeGoal(
            preset = GoalPreset.FAT_LOSS,
            activityLevel = ActivityLevel.SEDENTARY,
            weightKg = 92.0,
            heightCm = 172.0,
            ageYears = 36,
            sex = MifflinStJeor.Sex.MALE
        )

        // Fat Loss deficit = 500 kcal -> 2184 - 500 = 1684 raw kcal, rounded to nearest 10 = 1680 kcal
        assertEquals(1684.0, tdee - 500.0, 0.001)
        assertEquals(1680, computed.kcalTarget)
        // Protein: 2.0g/kg * 92kg = 184g
        assertEquals(184, computed.proteinTargetG)
    }

    @Test
    fun `projection maths - flat deficit and natural date formatting`() {
        val anchor = LocalDate.of(2026, 10, 22)

        // Mid-month: Jan 15 -> "around mid-January"
        val midMonthDate = LocalDate.of(2027, 1, 15)
        assertEquals("around mid-January", formatProjectionDate(midMonthDate, anchor))

        // Early month: Feb 5 -> "around 5 Feb"
        val earlyMonthDate = LocalDate.of(2027, 2, 5)
        assertEquals("around 5 Feb", formatProjectionDate(earlyMonthDate, anchor))

        // Late month: Jan 25 -> "late January"
        val lateMonthDate = LocalDate.of(2027, 1, 25)
        assertEquals("late January", formatProjectionDate(lateMonthDate, anchor))

        // Sentence generation with 92kg -> 86kg at 1,680 kcal/day (TDEE ~2225, deficit ~545 kcal/day -> 85 days -> Jan 15)
        val sentence = computeWeightProjectionSentence(
            currentWeightKg = 92.0,
            targetWeightKg = 86.0,
            kcalTarget = 1680,
            tdee = 2225.0,
            anchorDate = anchor
        )
        assertNotNull(sentence)
        assertEquals("At 1,680 kcal/day you'd reach 86 kg around mid-January", sentence)
    }

    @Test
    fun `projection maths - returns null when target weight is unset or reached`() {
        val anchor = LocalDate.of(2026, 10, 22)

        // Unset target weight
        assertNull(computeWeightProjectionSentence(92.0, null, 1680, 2184.0, anchor))
        assertNull(computeWeightProjectionSentence(92.0, 0.0, 1680, 2184.0, anchor))

        // Already at target weight
        assertNull(computeWeightProjectionSentence(92.0, 92.0, 1680, 2184.0, anchor))

        // No deficit when trying to lose weight
        assertNull(computeWeightProjectionSentence(92.0, 85.0, 2200, 2184.0, anchor))
    }

    @Test
    fun `SettingsStorage round-trip for heightCm, ageYears, sex, weightTargetKg`() {
        val storage = SettingsStorage()

        // Unconfigured returns 0 / defaults
        val initial = storage.loadSettings()
        assertEquals(0, initial.heightCm)
        assertEquals(0, initial.ageYears)
        assertEquals(MifflinStJeor.Sex.MALE, initial.sex)
        assertEquals(0.0, initial.weightTarget, 0.001)

        // Save real body data
        storage.saveGoalTargets(
            kcalTarget = 1684,
            proteinTarget = 184,
            carbTargetG = 185,
            fatTargetG = 56,
            activeGoal = GoalPreset.FAT_LOSS,
            heightCm = 172,
            ageYears = 36,
            sex = MifflinStJeor.Sex.MALE,
            weightTargetKg = 86.0
        )

        val loaded = storage.loadSettings()
        assertEquals(1684, loaded.kcalTarget)
        assertEquals(184, loaded.proteinTarget)
        assertEquals(185, loaded.carbTargetG)
        assertEquals(56, loaded.fatTargetG)
        assertEquals(GoalPreset.FAT_LOSS, loaded.activeGoal)
        assertEquals(172, loaded.heightCm)
        assertEquals(36, loaded.ageYears)
        assertEquals(MifflinStJeor.Sex.MALE, loaded.sex)
        assertEquals(86.0, loaded.weightTarget, 0.001)
    }
}
