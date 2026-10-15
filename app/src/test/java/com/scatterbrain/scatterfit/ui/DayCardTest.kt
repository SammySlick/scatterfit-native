package com.scatterbrain.scatterfit.ui

import com.scatterbrain.scatterfit.core.ScoringSettings
import com.scatterbrain.scatterfit.data.AssembledDaily
import com.scatterbrain.scatterfit.data.SleepNight
import com.scatterbrain.scatterfit.data.ZoneBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class DayCardTest {

    private val today = "2026-10-14"
    private val defaultSettings = ScoringSettings(
        stepTarget = 10000,
        kcalTarget = 2000,
        proteinTarget = 160,
        sleepTargetHours = 8.0,
    )
    private val defaultZone = ZoneId.of("UTC")
    private val defaultBounds = ZoneBounds(130, 145, 160, 175)

    @Test
    fun `all 4 tickable rows passing reports All done`() {
        val daily = AssembledDaily(
            caloriesEaten = mapOf(today to 2000.0), // within 150 kcal green tolerance
            steps = mapOf(today to 10500.0),        // >= 10,000 target
            protein = mapOf(today to 160.0),        // within 20g green tolerance
            sleep = mapOf(today to SleepNight(today, 480.0, 60.0, 90.0, 270.0, 60.0, "${today}T07:00:00Z")), // sleep stays context, not a tick
            trainingMinutes = mapOf(today to 30.0), // > 0m
            zoneBounds = defaultBounds,
            zone = defaultZone
        )

        val status = computeDayCardStatus(daily, defaultSettings, today)
        assertEquals(4, status.completedCount)
        assertEquals("All done ✓", status.headerText)
    }

    @Test
    fun `sleep never counts towards completion count`() {
        // Calories, steps, protein, training all pass -> 4/4 ("All done ✓") even if sleep fails (0h)
        val dailyNoSleep = AssembledDaily(
            caloriesEaten = mapOf(today to 2000.0),
            steps = mapOf(today to 10500.0),
            protein = mapOf(today to 160.0),
            sleep = mapOf(today to SleepNight(today, 0.0, 0.0, 0.0, 0.0, 0.0, "${today}T07:00:00Z")), // 0h sleep
            trainingMinutes = mapOf(today to 30.0),
            zoneBounds = defaultBounds,
            zone = defaultZone
        )
        val statusNoSleep = computeDayCardStatus(dailyNoSleep, defaultSettings, today)
        assertEquals(4, statusNoSleep.completedCount)
        assertEquals("All done ✓", statusNoSleep.headerText)

        // Only sleep passes (8h) while others fail -> 0/4 done
        val dailyOnlySleep = AssembledDaily(
            caloriesEaten = mapOf(today to 0.0),
            steps = mapOf(today to 0.0),
            protein = mapOf(today to 0.0),
            sleep = mapOf(today to SleepNight(today, 480.0, 60.0, 90.0, 270.0, 60.0, "${today}T07:00:00Z")),
            trainingMinutes = mapOf(today to 0.0),
            zoneBounds = defaultBounds,
            zone = defaultZone
        )
        val statusOnlySleep = computeDayCardStatus(dailyOnlySleep, defaultSettings, today)
        assertEquals(0, statusOnlySleep.completedCount)
        assertEquals("0/4 done", statusOnlySleep.headerText)
    }

    @Test
    fun `2 of 4 rows passing reports 2 of 4 done`() {
        val daily = AssembledDaily(
            caloriesEaten = mapOf(today to 2000.0), // PASS (green)
            steps = mapOf(today to 11000.0),        // PASS (green)
            protein = mapOf(today to 50.0),         // FAIL (off target)
            sleep = mapOf(today to SleepNight(today, 500.0, 60.0, 90.0, 290.0, 60.0, "${today}T07:00:00Z")),
            trainingMinutes = mapOf(today to 0.0),  // FAIL (no training)
            zoneBounds = defaultBounds,
            zone = defaultZone
        )

        val status = computeDayCardStatus(daily, defaultSettings, today)
        assertEquals(2, status.completedCount)
        assertEquals("2/4 done", status.headerText)
    }

    @Test
    fun `0 of 4 rows passing reports 0 of 4 done`() {
        val daily = AssembledDaily(
            caloriesEaten = mapOf(today to 3500.0), // FAIL (over by 1500 kcal)
            steps = mapOf(today to 2000.0),         // FAIL (< 7000 floor)
            protein = mapOf(today to 20.0),         // FAIL (way below target)
            sleep = mapOf(today to SleepNight(today, 300.0, 30.0, 50.0, 180.0, 40.0, "${today}T07:00:00Z")),
            trainingMinutes = mapOf(today to 0.0),  // FAIL (0m)
            zoneBounds = defaultBounds,
            zone = defaultZone
        )

        val status = computeDayCardStatus(daily, defaultSettings, today)
        assertEquals(0, status.completedCount)
        assertEquals("0/4 done", status.headerText)
    }

    @Test
    fun `null daily reports 0 of 4 done`() {
        val status = computeDayCardStatus(null, defaultSettings, today)
        assertEquals(0, status.completedCount)
        assertEquals("0/4 done", status.headerText)
    }

    @Test
    fun `alcohol units never count towards completion`() {
        val baseDaily = AssembledDaily(
            caloriesEaten = mapOf(today to 2000.0), // PASS
            steps = mapOf(today to 10000.0),        // PASS
            protein = mapOf(today to 50.0),         // FAIL
            sleep = mapOf(today to SleepNight(today, 360.0, 40.0, 60.0, 200.0, 60.0, "${today}T07:00:00Z")),
            trainingMinutes = mapOf(today to 0.0),  // FAIL
            zoneBounds = defaultBounds,
            zone = defaultZone
        )
        val statusWithoutAlcohol = computeDayCardStatus(baseDaily, defaultSettings, today)
        assertEquals(2, statusWithoutAlcohol.completedCount)
        assertEquals("2/4 done", statusWithoutAlcohol.headerText)

        val dailyWithAlcohol = baseDaily.copy(
            alcoholUnits = mapOf(today to 4.5)
        )
        val statusWithAlcohol = computeDayCardStatus(dailyWithAlcohol, defaultSettings, today)
        assertEquals(2, statusWithAlcohol.completedCount)
        assertEquals("2/4 done", statusWithAlcohol.headerText)
    }

    @Test
    fun `weight never counts towards completion`() {
        val baseDaily = AssembledDaily(
            caloriesEaten = mapOf(today to 2000.0), // PASS
            steps = mapOf(today to 10000.0),        // PASS
            protein = mapOf(today to 160.0),        // PASS
            sleep = mapOf(today to SleepNight(today, 480.0, 60.0, 90.0, 270.0, 60.0, "${today}T07:00:00Z")),
            trainingMinutes = mapOf(today to 30.0), // PASS
            zoneBounds = defaultBounds,
            zone = defaultZone
        )
        val statusWithoutWeight = computeDayCardStatus(baseDaily, defaultSettings, today)
        assertEquals(4, statusWithoutWeight.completedCount)
        assertEquals("All done ✓", statusWithoutWeight.headerText)

        val dailyWithWeight = baseDaily.copy(
            weight = mapOf(today to 76.4, "2026-10-13" to 76.8)
        )
        val statusWithWeight = computeDayCardStatus(dailyWithWeight, defaultSettings, today)
        assertEquals(4, statusWithWeight.completedCount)
        assertEquals("All done ✓", statusWithWeight.headerText)
    }

    @Test
    fun `pressing Save with empty weight input fails validation and does not save`() {
        val emptyResult = validateWeighIn("", "")
        assertFalse(emptyResult.isValid)
        assertEquals("Enter a weight in kg", emptyResult.weightError)
        assertNull(emptyResult.bodyFatError)

        val blankResult = validateWeighIn("   ", "")
        assertFalse(blankResult.isValid)
        assertEquals("Enter a weight in kg", blankResult.weightError)

        val invalidNumberResult = validateWeighIn("abc", "")
        assertFalse(invalidNumberResult.isValid)
        assertEquals("Enter a weight in kg", invalidNumberResult.weightError)

        val tooLowResult = validateWeighIn("35.0", "")
        assertFalse(tooLowResult.isValid)
        assertEquals("Must be 40-200 kg", tooLowResult.weightError)

        val tooHighResult = validateWeighIn("205.0", "")
        assertFalse(tooHighResult.isValid)
        assertEquals("Must be 40-200 kg", tooHighResult.weightError)

        val invalidBodyFat = validateWeighIn("75.0", "75.0")
        assertFalse(invalidBodyFat.isValid)
        assertNull(invalidBodyFat.weightError)
        assertEquals("Must be 0-60 %", invalidBodyFat.bodyFatError)

        val validResult = validateWeighIn("75.4", "18.5")
        assertTrue(validResult.isValid)
        assertNull(validResult.weightError)
        assertNull(validResult.bodyFatError)
    }
}
