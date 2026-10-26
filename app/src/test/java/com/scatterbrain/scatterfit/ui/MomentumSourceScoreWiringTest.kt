package com.scatterbrain.scatterfit.ui

import com.scatterbrain.scatterfit.core.GoalId
import com.scatterbrain.scatterfit.core.GoalPreset
import com.scatterbrain.scatterfit.core.ScoringSettings
import com.scatterbrain.scatterfit.data.AssembledDaily
import com.scatterbrain.scatterfit.data.SleepNight
import com.scatterbrain.scatterfit.data.ZoneBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class MomentumSourceScoreWiringTest {

    private val testDay = "2026-10-25"
    private val zone = ZoneId.of("UTC")
    private val fixedClock = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), zone)
    private val defaultBounds = ZoneBounds(130, 145, 160, 175)

    /**
     * Rule: TWO_SIDED calories scoring: a day off-target scores graded, not 0.
     * With target 2260 and eaten 2000, diff is 260.
     * Under 0.35 tolerance, maxDiff = 791.0.
     * Score = 100 * (1 - 260 / 791) = 67.1302... (not 0.0!).
     */
    @Test
    fun `a two-sided calories day off-target scores graded (not 0)`() {
        val settings = ScoringSettings(
            kcalTarget = 2260,
            activeGoal = GoalPreset.FAT_LOSS
        )
        val daily = AssembledDaily(
            caloriesEaten = mapOf(testDay to 2000.0),
            zoneBounds = defaultBounds,
            zone = zone
        )
        val source = TodayFacadeMomentumSource(
            daily = daily,
            settings = settings,
            zone = zone,
            clock = fixedClock
        )

        val score = source.metricScoreForDay(GoalId.CALORIES, testDay)
        assertNotNull("Score should not be null", score)
        assertTrue("Score must be greater than 0", score!! > 0.0)
        val expected = 100.0 * (1.0 - 260.0 / (0.35 * 2260.0))
        assertEquals(expected, score, 0.0001)
    }

    /**
     * Rule: PROGRESS weight scoring:
     * start 92kg (earliest record), goal 86kg (weightTarget).
     * current 89kg (halfway to goal) scores exactly 75.0.
     */
    @Test
    fun `a progress day at 89kg vs 92-86 goal scores 75`() {
        val settings = ScoringSettings(
            weightTarget = 86.0,
            activeGoal = GoalPreset.FAT_LOSS
        )
        val daily = AssembledDaily(
            weight = mapOf(
                "2026-10-01" to 92.0, // earliest historical weight = start
                testDay to 89.0        // current measurement for testDay
            ),
            zoneBounds = defaultBounds,
            zone = zone
        )
        val source = TodayFacadeMomentumSource(
            daily = daily,
            settings = settings,
            zone = zone,
            clock = fixedClock
        )

        val score = source.metricScoreForDay(GoalId.WEIGHT, testDay)
        assertNotNull("Weight score should not be null", score)
        assertEquals(75.0, score!!, 0.0001)
    }

    /**
     * Rule: Untracked metrics never drag or dilute momentum.
     * In FAT_LOSS preset: BODY_FAT weight = 10.0, CALORIES weight = 8.0.
     * When only CALORIES has data (e.g. 2260 on target -> score 100.0),
     * and BODY_FAT has no measurement, BODY_FAT is excluded from both
     * numerator and denominator. Momentum equals 100.0 (not dragged to 44.4).
     */
    @Test
    fun `a day with no BF measurement doesn't drag the denominator`() {
        val settings = ScoringSettings(
            kcalTarget = 2260,
            activeGoal = GoalPreset.FAT_LOSS
        )
        // Only calories logged on testDay, no bodyFat measurement
        val daily = AssembledDaily(
            caloriesEaten = mapOf(testDay to 2260.0),
            bodyFat = emptyMap(),
            zoneBounds = defaultBounds,
            zone = zone
        )
        val source = TodayFacadeMomentumSource(
            daily = daily,
            settings = settings,
            zone = zone,
            clock = fixedClock
        )

        assertNull("BF score must be null on unmeasured day", source.metricScoreForDay(GoalId.BODY_FAT, testDay))
        assertEquals(100.0, source.metricScoreForDay(GoalId.CALORIES, testDay)!!, 0.0001)

        val momentum = source.momentumForDay(testDay)
        assertNotNull("Day momentum should not be null", momentum)
        assertEquals(100.0, momentum!!, 0.0001)
    }

    /**
     * Rule: TWO_SIDED sleep scoring is graded (not null or 0).
     * With 8.0h target (480 min) and 456 min slept (-5%), score is 85.71...
     */
    @Test
    fun `a two-sided sleep night off-target scores graded`() {
        val settings = ScoringSettings(
            sleepTargetHours = 8.0,
            activeGoal = GoalPreset.MAINTENANCE
        )
        val daily = AssembledDaily(
            sleep = mapOf(testDay to SleepNight(testDay, 456.0, 60.0, 90.0, 246.0, 60.0, "${testDay}T07:00:00Z")),
            zoneBounds = defaultBounds,
            zone = zone
        )
        val source = TodayFacadeMomentumSource(
            daily = daily,
            settings = settings,
            zone = zone,
            clock = fixedClock
        )

        val score = source.metricScoreForDay(GoalId.SLEEP, testDay)
        assertNotNull("Sleep score should not be null", score)
        val expected = 100.0 * (1.0 - 24.0 / (0.35 * 480.0))
        assertEquals(expected, score!!, 0.0001)
    }

    /**
     * Rule: contributions reports loggedDays for tracked metrics, and excludes
     * untracked metrics (which become ghost axes on the spider graph).
     */
    @Test
    fun `contributions reports zero logged days for untracked metrics for ghost axes`() {
        val settings = ScoringSettings(
            activeGoal = GoalPreset.FAT_LOSS
        )
        // Only calories logged across the week (CALORIES weight = 8.0 in FAT_LOSS)
        val daily = AssembledDaily(
            caloriesEaten = mapOf(testDay to 2260.0),
            zoneBounds = defaultBounds,
            zone = zone
        )
        val source = TodayFacadeMomentumSource(
            daily = daily,
            settings = settings,
            zone = zone,
            clock = fixedClock
        )

        val contribs = source.contributions(7, testDay)
        val calContrib = contribs.find { it.id == GoalId.CALORIES }
        val bfContrib = contribs.find { it.id == GoalId.BODY_FAT }

        // Calories has data
        assertNotNull(calContrib)
        assertEquals(1, calContrib!!.loggedDays)

        // Body fat had no data in the 7-day window -> not in participating contributions
        assertNull("BF has no participating contribution in window", bfContrib)
    }

    /**
     * Rule (a): metricMeanOverWindow excludes null days in window and averages
     * participating days only.
     */
    @Test
    fun `mean over window excludes null days`() {
        val settings = ScoringSettings(stepTarget = 10000)
        // 3 days logged in 7-day window ending on testDay (2026-10-25):
        // 2026-10-25: 8000 -> 80.0
        // 2026-10-24: 10000 -> 100.0
        // 2026-10-23: 12000 -> 100.0 (capped)
        // 2026-10-22, 21, 20, 19: null (unlogged)
        val daily = AssembledDaily(
            steps = mapOf(
                "2026-10-25" to 8000.0,
                "2026-10-24" to 10000.0,
                "2026-10-23" to 12000.0
            ),
            zoneBounds = defaultBounds,
            zone = zone
        )
        val source = TodayFacadeMomentumSource(
            daily = daily,
            settings = settings,
            zone = zone,
            clock = fixedClock
        )

        val mean = source.metricMeanOverWindow(GoalId.STEPS, testDay, 7)
        assertNotNull(mean)
        val expectedMean = (80.0 + 100.0 + 100.0) / 3.0
        assertEquals(expectedMean, mean!!, 0.0001)

        val participating = source.metricParticipatingDaysOverWindow(GoalId.STEPS, testDay, 7)
        assertEquals(3, participating)
    }

    /**
     * Rule (b): all-null window returns null (row shows No data state).
     */
    @Test
    fun `all-null window returns null`() {
        val settings = ScoringSettings(stepTarget = 10000)
        val daily = AssembledDaily(
            steps = emptyMap(),
            zoneBounds = defaultBounds,
            zone = zone
        )
        val source = TodayFacadeMomentumSource(
            daily = daily,
            settings = settings,
            zone = zone,
            clock = fixedClock
        )

        val mean = source.metricMeanOverWindow(GoalId.STEPS, testDay, 7)
        assertNull("All-null window must return null", mean)

        val participating = source.metricParticipatingDaysOverWindow(GoalId.STEPS, testDay, 7)
        assertEquals(0, participating)
    }

    /**
     * Rule (c): window 14 and 30 use the right span.
     */
    @Test
    fun `window 14 and 30 use the right span`() {
        val settings = ScoringSettings(stepTarget = 10000)
        // testDay is 2026-10-25
        // 10 days ago is 2026-10-15 (within 14d and 30d, but NOT 7d)
        // 20 days ago is 2026-10-05 (within 30d, but NOT 14d)
        val daily = AssembledDaily(
            steps = mapOf(
                "2026-10-15" to 10000.0, // score 100.0
                "2026-10-05" to 6000.0   // score 60.0
            ),
            zoneBounds = defaultBounds,
            zone = zone
        )
        val source = TodayFacadeMomentumSource(
            daily = daily,
            settings = settings,
            zone = zone,
            clock = fixedClock
        )

        // 7-day window (2026-10-19 to 2026-10-25): neither entry included
        assertNull("7d window has no data", source.metricMeanOverWindow(GoalId.STEPS, testDay, 7))
        assertEquals(0, source.metricParticipatingDaysOverWindow(GoalId.STEPS, testDay, 7))

        // 14-day window (2026-10-12 to 2026-10-25): includes 2026-10-15 only
        val mean14 = source.metricMeanOverWindow(GoalId.STEPS, testDay, 14)
        assertNotNull(mean14)
        assertEquals(100.0, mean14!!, 0.0001)
        assertEquals(1, source.metricParticipatingDaysOverWindow(GoalId.STEPS, testDay, 14))

        // 30-day window (2026-09-26 to 2026-10-25): includes both 2026-10-15 and 2026-10-05
        val mean30 = source.metricMeanOverWindow(GoalId.STEPS, testDay, 30)
        assertNotNull(mean30)
        val expected30 = (100.0 + 60.0) / 2.0 // 80.0
        assertEquals(expected30, mean30!!, 0.0001)
        assertEquals(2, source.metricParticipatingDaysOverWindow(GoalId.STEPS, testDay, 30))
    }
}
