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

    // --- Today card redesign, 2026-11-24 -------------------------------------
    // Sam: "There's now too many numbers... Maybe a big 'n/n' calories eaten Vs
    // calorie target? I like the different coloured pills for the Protein, Carbs
    // and Fats in BWS. Weight with a small up or down chevron so show if up or
    // down from yday?"
    //
    // The chevron itself was removed the same evening: "No up or down chevrons." The
    // delta helpers went with it rather than sitting unused - a function nobody calls
    // is how DashboardScreen survived a redesign.
    //
    // The row-voice notes that this replaces (kcalRemainingNote/proteinRemainingNote)
    // are gone with their rows: the hero and the pills now carry those numbers, and
    // keeping two ways of phrasing "how much is left" is how the card got noisy.

    // --- Target shown as the band it already was, 2026-11-25 ---------------
    // Sam: "Band. For sure. Better to have a range as a target than a fixed number
    // or you can end up obsessing over it."
    //
    // These replace the three "N left / on target / N over" caption tests that stood
    // here. That wording was the bug: it judged against a point while squareBand()
    // judged the same day against target +- kcalGreen.

    @Test
    fun `band label prints the tolerance range the app judges against`() {
        assertEquals("1,850\u20132,150 kcal", calorieBandLabel(2_000, 150))
    }

    @Test
    fun `band label has no range without a target`() {
        assertNull(calorieBandLabel(0, 150))
    }

    @Test
    fun `band label collapses a zero tolerance to one value rather than 2000-2000`() {
        assertEquals("2,000 kcal", calorieBandLabel(2_000, 0))
    }

    @Test
    fun `band label never prints a negative floor`() {
        // A 100 kcal target with a 150 band would otherwise read "-50-250 kcal".
        assertEquals("0\u2013250 kcal", calorieBandLabel(100, 150))
    }

    @Test
    fun `caption calls a day inside the band in range, not over`() {
        // The case that started this: 2,098 against 2,000 is 98 out, which is inside a
        // 150 band, and the old caption called it "98 over" while the square went green.
        assertEquals("in range", calorieHeroCaption(2_098, 2_000, 150))
        assertEquals("in range", calorieHeroCaption(2_000, 2_000, 150))
        assertEquals("in range", calorieHeroCaption(1_850, 2_000, 150))
        assertEquals("in range", calorieHeroCaption(2_150, 2_000, 150))
    }

    @Test
    fun `caption measures how far outside the band, not how far from the target`() {
        // 2,300 is 300 over the target but only 150 over the band's ceiling.
        assertEquals("150 over range", calorieHeroCaption(2_300, 2_000, 150))
        assertEquals("200 below range", calorieHeroCaption(1_650, 2_000, 150))
    }

    @Test
    fun `caption does not call an empty day a failure`() {
        assertEquals("nothing logged yet", calorieHeroCaption(0, 2_000, 150))
    }

    @Test
    fun `caption does not invent a band with no target`() {
        assertEquals("kcal eaten", calorieHeroCaption(1_240, 0, 150))
    }

    @Test
    fun `macro pill shows eaten and target when there is a target`() {
        assertEquals("92 / 160 g", macroPillValue(92.4, 160))
    }

    @Test
    fun `macro pill shows eaten alone when there is no target`() {
        assertEquals("92 g", macroPillValue(92.0, 0))
    }

    @Test
    fun `macro pill distinguishes no data from zero grams`() {
        // No meal logged with carbs is not the same as eating no carbs.
        assertEquals("\u2014 / 250 g", macroPillValue(null, 250))
        assertEquals("\u2014", macroPillValue(null, 0))
        assertEquals("0 / 250 g", macroPillValue(0.0, 250))
    }

    @Test
    fun `sleep context line reads deep sleep and pads the minutes`() {
        // 1h 4m, not "1h 4m deep" with a stray space, and not "64m".
        assertEquals("1h 04m deep", sleepDeepLabel(64.0))
        assertEquals("0h 07m deep", sleepDeepLabel(7.2))
    }

    @Test
    fun `sleep context line is silent rather than claiming zero deep sleep`() {
        // No stage data from the source is not the same as no deep sleep.
        assertNull(sleepDeepLabel(0.0))
        assertNull(sleepDeepLabel(-1.0))
    }

}