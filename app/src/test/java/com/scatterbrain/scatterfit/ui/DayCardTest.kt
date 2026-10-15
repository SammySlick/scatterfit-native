package com.scatterbrain.scatterfit.ui

import com.scatterbrain.scatterfit.core.ScoringSettings
import com.scatterbrain.scatterfit.data.AssembledDaily
import com.scatterbrain.scatterfit.data.SleepNight
import com.scatterbrain.scatterfit.data.ZoneBounds
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class DayCardTest {

    private val today = "2026-10-14"
    private val defaultSettings = ScoringSettings(
        stepTarget = 10000,
        kcalTarget = 2000,
        proteinTarget = 160,
        sleepTargetHours = 8.0
    )
    private val defaultZone = ZoneId.of("UTC")
    private val defaultBounds = ZoneBounds(130, 145, 160, 175)

    @Test
    fun `all 5 tickable rows passing reports All done`() {
        val daily = AssembledDaily(
            caloriesEaten = mapOf(today to 2000.0), // within 150 kcal green tolerance
            steps = mapOf(today to 10500.0),        // >= 10,000 target
            protein = mapOf(today to 160.0),        // within 20g green tolerance
            sleep = mapOf(today to SleepNight(today, 480.0, 60.0, 90.0, 270.0, 60.0, "${today}T07:00:00Z")), // >= 480m target
            trainingMinutes = mapOf(today to 30.0), // > 0m
            zoneBounds = defaultBounds,
            zone = defaultZone
        )

        val status = computeDayCardStatus(daily, defaultSettings, today)
        assertEquals(5, status.completedCount)
        assertEquals("All done ✓", status.headerText)
    }

    @Test
    fun `3 of 5 rows passing reports 3 of 5 done`() {
        val daily = AssembledDaily(
            caloriesEaten = mapOf(today to 2000.0), // PASS (green)
            steps = mapOf(today to 11000.0),        // PASS (green)
            protein = mapOf(today to 50.0),         // FAIL (off target)
            sleep = mapOf(today to SleepNight(today, 500.0, 60.0, 90.0, 290.0, 60.0, "${today}T07:00:00Z")), // PASS (green)
            trainingMinutes = mapOf(today to 0.0),  // FAIL (no training)
            zoneBounds = defaultBounds,
            zone = defaultZone
        )

        val status = computeDayCardStatus(daily, defaultSettings, today)
        assertEquals(3, status.completedCount)
        assertEquals("3/5 done", status.headerText)
    }

    @Test
    fun `0 of 5 rows passing reports 0 of 5 done`() {
        val daily = AssembledDaily(
            caloriesEaten = mapOf(today to 3500.0), // FAIL (over by 1500 kcal)
            steps = mapOf(today to 2000.0),         // FAIL (< 7000 floor)
            protein = mapOf(today to 20.0),         // FAIL (way below target)
            sleep = mapOf(today to SleepNight(today, 300.0, 30.0, 50.0, 180.0, 40.0, "${today}T07:00:00Z")), // FAIL (< 7h)
            trainingMinutes = mapOf(today to 0.0),  // FAIL (0m)
            zoneBounds = defaultBounds,
            zone = defaultZone
        )

        val status = computeDayCardStatus(daily, defaultSettings, today)
        assertEquals(0, status.completedCount)
        assertEquals("0/5 done", status.headerText)
    }

    @Test
    fun `null daily reports 0 of 5 done`() {
        val status = computeDayCardStatus(null, defaultSettings, today)
        assertEquals(0, status.completedCount)
        assertEquals("0/5 done", status.headerText)
    }

    @Test
    fun `alcohol units never count towards completion`() {
        // Start with 2 of 5 passing (Calories and Steps)
        val baseDaily = AssembledDaily(
            caloriesEaten = mapOf(today to 2000.0), // PASS
            steps = mapOf(today to 10000.0),        // PASS
            protein = mapOf(today to 50.0),         // FAIL
            sleep = mapOf(today to SleepNight(today, 360.0, 40.0, 60.0, 200.0, 60.0, "${today}T07:00:00Z")), // FAIL
            trainingMinutes = mapOf(today to 0.0),  // FAIL
            zoneBounds = defaultBounds,
            zone = defaultZone
        )
        val statusWithoutAlcohol = computeDayCardStatus(baseDaily, defaultSettings, today)
        assertEquals(2, statusWithoutAlcohol.completedCount)
        assertEquals("2/5 done", statusWithoutAlcohol.headerText)

        // Adding alcohol records (logged 0, logged 2, logged 10) must never change completion
        val dailyWithAlcohol = baseDaily.copy(
            alcoholUnits = mapOf(today to 4.5)
        )
        val statusWithAlcohol = computeDayCardStatus(dailyWithAlcohol, defaultSettings, today)
        assertEquals(2, statusWithAlcohol.completedCount)
        assertEquals("2/5 done", statusWithAlcohol.headerText)
    }

    @Test
    fun `weight never counts towards completion`() {
        // Start with 4 of 5 passing
        val baseDaily = AssembledDaily(
            caloriesEaten = mapOf(today to 2000.0), // PASS
            steps = mapOf(today to 10000.0),        // PASS
            protein = mapOf(today to 160.0),        // PASS
            sleep = mapOf(today to SleepNight(today, 480.0, 60.0, 90.0, 270.0, 60.0, "${today}T07:00:00Z")), // PASS
            trainingMinutes = mapOf(today to 0.0),  // FAIL
            zoneBounds = defaultBounds,
            zone = defaultZone
        )
        val statusWithoutWeight = computeDayCardStatus(baseDaily, defaultSettings, today)
        assertEquals(4, statusWithoutWeight.completedCount)
        assertEquals("4/5 done", statusWithoutWeight.headerText)

        // Adding weight today or past days must never change completion count
        val dailyWithWeight = baseDaily.copy(
            weight = mapOf(today to 76.4, "2026-10-13" to 76.8)
        )
        val statusWithWeight = computeDayCardStatus(dailyWithWeight, defaultSettings, today)
        assertEquals(4, statusWithWeight.completedCount)
        assertEquals("4/5 done", statusWithWeight.headerText)
    }
}
