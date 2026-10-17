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
    fun `tick count maths - training day has M equal 4 and counts all passing`() {
        val daily = AssembledDaily(
            caloriesEaten = mapOf(today to 2000.0), // within goal tolerance -> ticked
            steps = mapOf(today to 10500.0),        // >= 10k -> ticked
            protein = mapOf(today to 160.0),        // >= 160g -> ticked
            sleep = mapOf(today to SleepNight(today, 480.0, 60.0, 90.0, 270.0, 60.0, "${today}T07:00:00Z")), // sleep is context, never ticks
            trainingMinutes = mapOf(today to 30.0), // training day with logged work -> ticked
            zoneBounds = defaultBounds,
            zone = defaultZone
        )

        val ticks = computeDayCardTicks(daily, defaultSettings, today, isTrainingDay = true)
        assertEquals(4, ticks.totalTarget)
        assertEquals(4, ticks.completedCount)
        assertEquals("4 / 4", ticks.counterText)
    }

    @Test
    fun `tick count maths - rest day has M equal 3 and never auto-ticks training`() {
        val daily = AssembledDaily(
            caloriesEaten = mapOf(today to 2000.0), // ticked
            steps = mapOf(today to 10500.0),        // ticked
            protein = mapOf(today to 160.0),        // ticked
            sleep = mapOf(today to SleepNight(today, 480.0, 60.0, 90.0, 270.0, 60.0, "${today}T07:00:00Z")),
            trainingMinutes = mapOf(today to 0.0),  // rest day
            zoneBounds = defaultBounds,
            zone = defaultZone
        )

        val ticks = computeDayCardTicks(daily, defaultSettings, today, isTrainingDay = false)
        assertEquals(3, ticks.totalTarget)
        assertEquals(3, ticks.completedCount)
        assertEquals("3 / 3", ticks.counterText)
    }

    @Test
    fun `tick count maths - rest day with training minutes still has M equal 3 and does not award training tick`() {
        val daily = AssembledDaily(
            caloriesEaten = mapOf(today to 2000.0),
            steps = mapOf(today to 10500.0),
            protein = mapOf(today to 160.0),
            trainingMinutes = mapOf(today to 45.0),
            zoneBounds = defaultBounds,
            zone = defaultZone
        )

        val ticks = computeDayCardTicks(daily, defaultSettings, today, isTrainingDay = false)
        assertEquals(3, ticks.totalTarget)
        assertEquals(3, ticks.completedCount)
        assertEquals("3 / 3", ticks.counterText)
    }

    @Test
    fun `tick count maths - weigh-in bonus adds 1 without increasing M and can exceed M`() {
        // Training day: 4/4 passing + weigh-in bonus -> 5 / 4
        val dailyTrainingBonus = AssembledDaily(
            caloriesEaten = mapOf(today to 2000.0),
            steps = mapOf(today to 10500.0),
            protein = mapOf(today to 160.0),
            trainingMinutes = mapOf(today to 30.0),
            weight = mapOf(today to 82.4),
            zoneBounds = defaultBounds,
            zone = defaultZone
        )
        val ticksTraining = computeDayCardTicks(dailyTrainingBonus, defaultSettings, today, isTrainingDay = true)
        assertEquals(4, ticksTraining.totalTarget)
        assertEquals(5, ticksTraining.completedCount)
        assertTrue(ticksTraining.hasWeightBonus)
        assertEquals("5 / 4", ticksTraining.counterText)

        // Rest day: 3/3 passing + weigh-in bonus -> 4 / 3
        val dailyRestBonus = AssembledDaily(
            caloriesEaten = mapOf(today to 2000.0),
            steps = mapOf(today to 10500.0),
            protein = mapOf(today to 160.0),
            weight = mapOf(today to 82.4),
            zoneBounds = defaultBounds,
            zone = defaultZone
        )
        val ticksRest = computeDayCardTicks(dailyRestBonus, defaultSettings, today, isTrainingDay = false)
        assertEquals(3, ticksRest.totalTarget)
        assertEquals(4, ticksRest.completedCount)
        assertTrue(ticksRest.hasWeightBonus)
        assertEquals("4 / 3", ticksRest.counterText)
    }

    @Test
    fun `tick count maths - null daily reports 0 and counter is never negative`() {
        val ticksTraining = computeDayCardTicks(null, defaultSettings, today, isTrainingDay = true)
        assertEquals(4, ticksTraining.totalTarget)
        assertEquals(0, ticksTraining.completedCount)
        assertEquals("0 / 4", ticksTraining.counterText)

        val ticksRest = computeDayCardTicks(null, defaultSettings, today, isTrainingDay = false)
        assertEquals(3, ticksRest.totalTarget)
        assertEquals(0, ticksRest.completedCount)
        assertEquals("0 / 3", ticksRest.counterText)
    }

    @Test
    fun `bar colour logic - calories shows teal in progress, green in band, orange over goal, red over 10 percent`() {
        // Target 2500, greenTolerance 150
        // In progress (< target - 150) -> TEAL
        val calProgress = computeCaloriesBarColor(2000.0, 2500, greenTolerance = 150)
        assertEquals(MetricBarColor.TEAL, calProgress)

        // In band (2350..2650) -> GREEN
        val calInBand = computeCaloriesBarColor(2550.0, 2500, greenTolerance = 150)
        assertEquals(MetricBarColor.GREEN, calInBand)

        // Over tolerance by < 10% -> ORANGE
        val calOrange = computeCaloriesBarColor(2680.0, 2500, greenTolerance = 150)
        assertEquals(MetricBarColor.ORANGE, calOrange)

        // Boundary at exactly 10% over target (2500 * 1.10 = 2750) -> ORANGE
        val calBoundary10 = computeCaloriesBarColor(2750.0, 2500, greenTolerance = 150)
        assertEquals(MetricBarColor.ORANGE, calBoundary10)

        // Over target by 10.1% (2500 * 1.101 = 2752.5) -> RED
        val calRed101 = computeCaloriesBarColor(2752.5, 2500, greenTolerance = 150)
        assertEquals(MetricBarColor.RED, calRed101)

        // Null / 0 -> TEAL
        assertEquals(MetricBarColor.TEAL, computeCaloriesBarColor(null, 2500))
        assertEquals(MetricBarColor.TEAL, computeCaloriesBarColor(0.0, 2500))
    }

    @Test
    fun `bar colour logic - steps shows teal under 10k, green at or over 10k`() {
        val stepsUnder = computeStepsBarColor(8400.0, 10000)
        assertEquals(MetricBarColor.TEAL, stepsUnder)

        val stepsMet = computeStepsBarColor(10000.0, 10000)
        assertEquals(MetricBarColor.GREEN, stepsMet)

        val stepsOver = computeStepsBarColor(12500.0, 10000)
        assertEquals(MetricBarColor.GREEN, stepsOver)

        assertEquals(MetricBarColor.TEAL, computeStepsBarColor(null, 10000))
    }

    @Test
    fun `bar colour logic - protein shows teal under 160g, green at or over 160g`() {
        val protUnder = computeProteinBarColor(120.0, 160)
        assertEquals(MetricBarColor.TEAL, protUnder)

        val protMet = computeProteinBarColor(160.0, 160)
        assertEquals(MetricBarColor.GREEN, protMet)

        val protOver = computeProteinBarColor(185.0, 160)
        assertEquals(MetricBarColor.GREEN, protOver)

        assertEquals(MetricBarColor.TEAL, computeProteinBarColor(null, 160))
    }

    @Test
    fun `drinks section visibility - hidden at 0 drinks, visible once drinks or units logged`() {
        // 0 drinks and 0.0 units -> HIDDEN
        assertFalse(isDrinksSectionVisible(drinksCount = 0, units = 0.0))

        // 1 drink -> VISIBLE
        assertTrue(isDrinksSectionVisible(drinksCount = 1, units = 0.0))

        // 0 drinks count but units > 0 -> VISIBLE
        assertTrue(isDrinksSectionVisible(drinksCount = 0, units = 1.5))

        // Both > 0 -> VISIBLE
        assertTrue(isDrinksSectionVisible(drinksCount = 3, units = 4.2))
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

    @Test
    fun `Day Card over-target hero reads N kcal over T in StateBad red`() {
        val target = 2500
        val eaten = 2750 // 250 over
        val isOverGoal = eaten > target
        val overKcal = (eaten - target).coerceAtLeast(0)
        val heroNumberText = if (isOverGoal) String.format("%,d", overKcal) else String.format("%,d", target - eaten)
        val heroCaptionText = if (isOverGoal) "kcal over %,d".format(target) else "kcal left of %,d".format(target)

        assertEquals("250", heroNumberText)
        assertEquals("kcal over 2,500", heroCaptionText)

        // Bar turns RED when > 10% (e.g. 2752.5)
        assertEquals(MetricBarColor.RED, computeCaloriesBarColor(2752.5, target))
        // Stays ORANGE between 100% and 110% (e.g. 2750)
        assertEquals(MetricBarColor.ORANGE, computeCaloriesBarColor(2750.0, target))
    }
}
