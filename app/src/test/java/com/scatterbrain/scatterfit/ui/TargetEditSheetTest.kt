package com.scatterbrain.scatterfit.ui

import com.scatterbrain.scatterfit.data.SettingsStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetEditSheetTest {

    @Test
    fun `macro percentage split sum validation rejects 99 and 101, accepts 100`() {
        assertTrue(validateMacroSplit(30, 40, 30))
        assertTrue(validateMacroSplit(25, 50, 25))
        assertTrue(validateMacroSplit(20, 60, 20))

        assertFalse(validateMacroSplit(30, 40, 29)) // 99
        assertFalse(validateMacroSplit(30, 40, 31)) // 101
        assertFalse(validateMacroSplit(0, 0, 0))     // 0
        assertFalse(validateMacroSplit(50, 50, 50)) // 150
    }

    @Test
    fun `g per lb clamp bounds enforces 1_0 to 2_0 clamp`() {
        assertEquals(1.0, clampProteinPerLb(0.5), 0.001)
        assertEquals(1.0, clampProteinPerLb(0.99), 0.001)
        assertEquals(1.0, clampProteinPerLb(1.0), 0.001)
        assertEquals(1.25, clampProteinPerLb(1.25), 0.001)
        assertEquals(1.8, clampProteinPerLb(1.8), 0.001)
        assertEquals(2.0, clampProteinPerLb(2.0), 0.001)
        assertEquals(2.0, clampProteinPerLb(2.5), 0.001)
        assertEquals(2.0, clampProteinPerLb(10.0), 0.001)

        // Sam's 92kg -> 203lb floor = 203g protein at 1.0 g/lb
        assertEquals(203, deriveProteinGramsFromLb(92.0, 1.0))
        // 92kg at 1.2 g/lb = 243g
        assertEquals(243, deriveProteinGramsFromLb(92.0, 1.2))
    }

    @Test
    fun `derived-gram maths computes kcal times pct divided by caloric density`() {
        // 2000 kcal with 30% Protein (4 kcal/g), 40% Carbs (4 kcal/g), 30% Fat (9 kcal/g)
        // Protein: 2000 * 0.30 / 4 = 150g
        // Carbs: 2000 * 0.40 / 4 = 200g
        // Fat: 2000 * 0.30 / 9 = 66.67 -> 67g
        val (p, c, f) = deriveMacroGramsFromSplit(2000, 30, 40, 30)
        assertEquals(150, p)
        assertEquals(200, c)
        assertEquals(67, f)

        // Exercise Zone Split: N = 4 times/week x 45 min = 180 min total
        // Zone 2 = 60% (108m), Zone 3 = 25% (45m), Zone 4 = 10% (18m), Zone 5 = 5% (9m)
        val exercise = deriveExerciseZoneMinutes(timesPerWeek = 4, sessionDurationMin = 45)
        assertEquals(180, exercise.totalMin)
        assertEquals(108, exercise.z2)
        assertEquals(45, exercise.z3)
        assertEquals(18, exercise.z4)
        assertEquals(9, exercise.z5)
    }

    @Test
    fun `SettingsStorage round-trip for last-used-mode flags and individual target edits`() {
        val storage = SettingsStorage()

        // Default mode flags
        assertTrue(storage.isCaloriesModeA())
        assertTrue(storage.isExerciseModeSimple())

        // Toggle mode flags and verify round-trip
        storage.setCaloriesModeA(false)
        storage.setExerciseModeSimple(false)
        assertFalse(storage.isCaloriesModeA())
        assertFalse(storage.isExerciseModeSimple())

        storage.setCaloriesModeA(true)
        storage.setExerciseModeSimple(true)
        assertTrue(storage.isCaloriesModeA())
        assertTrue(storage.isExerciseModeSimple())

        // Save and verify steps target
        storage.saveStepTarget(12500)
        assertEquals(12500, storage.loadSettings().stepTarget)

        // Save and verify sleep target
        storage.saveSleepTarget(7.5)
        assertEquals(7.5, storage.loadSettings().sleepTargetHours, 0.001)

        // Save and verify alcohol target
        storage.saveAlcoholTarget(8)
        assertEquals(8, storage.loadSettings().unitsPerWeek)

        // Save and verify exercise targets in simple mode
        storage.saveExerciseTargets(timesPerWeek = 5, isSimple = true, z2 = 135, z3 = 56, z4 = 23, z5 = 11)
        val loadedExercise = storage.loadSettings()
        assertEquals(225, loadedExercise.exerciseTargetPerWeek)
        assertEquals(135, loadedExercise.z2Target)
        assertEquals(56, loadedExercise.z3Target)
        assertEquals(23, loadedExercise.z4Target)
        assertEquals(11, loadedExercise.z5Target)
        assertTrue(storage.isExerciseModeSimple())
        assertEquals(5, storage.getExerciseTimesPerWeek())

        // Save and verify calories and macros
        storage.saveCaloriesMacroTargets(
            kcal = 1800,
            protein = 160,
            carbs = 180,
            fat = 49,
            isModeA = false,
            pPct = 35,
            cPct = 40,
            fPct = 25
        )
        val loadedCal = storage.loadSettings()
        assertEquals(1800, loadedCal.kcalTarget)
        assertEquals(160, loadedCal.proteinTarget)
        assertEquals(180, loadedCal.carbTargetG)
        assertEquals(49, loadedCal.fatTargetG)
        assertFalse(storage.isCaloriesModeA())
        assertEquals(Triple(35, 40, 25), storage.getMacroSplitPercentages())
    }
}
