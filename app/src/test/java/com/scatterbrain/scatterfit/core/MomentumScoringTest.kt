package com.scatterbrain.scatterfit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MomentumScoringTest {

    // ---------- ONE_SIDED tests ----------

    /**
     * Rule: ONE_SIDED daily score is min(100.0, (value / target) * 100).
     * Score caps at 100.0 when exceeding target.
     * Points: round(10 * value / target) capped at 20.
     */
    @Test
    fun `one-sided score calculates ratio and caps at 100, points cap at 20`() {
        val target = 10000.0

        // 4000 / 10000 = 40.0
        val score4k = dailyMetricScore(ScoreShape.ONE_SIDED, MetricScoreInput(value = 4000.0, target = target))
        assertEquals(40.0, score4k!!, 0.0001)

        // 10000 / 10000 = 100.0
        val score10k = dailyMetricScore(ScoreShape.ONE_SIDED, MetricScoreInput(value = 10000.0, target = target))
        assertEquals(100.0, score10k!!, 0.0001)

        // 12000 / 10000 = 100.0 (caps at 100)
        val score12k = dailyMetricScore(ScoreShape.ONE_SIDED, MetricScoreInput(value = 12000.0, target = target))
        assertEquals(100.0, score12k!!, 0.0001)

        // Points
        assertEquals(4.0, dailyMetricPoints(ScoreShape.ONE_SIDED, value = 4000.0, target = target, score = score4k)!!, 0.0001)
        assertEquals(12.0, dailyMetricPoints(ScoreShape.ONE_SIDED, value = 12000.0, target = target, score = score12k)!!, 0.0001)
        assertEquals(20.0, dailyMetricPoints(ScoreShape.ONE_SIDED, value = 20000.0, target = target, score = 100.0)!!, 0.0001)
        // 25000 is 20001-equivalent exceeding 2x target -> capped at 20
        assertEquals(20.0, dailyMetricPoints(ScoreShape.ONE_SIDED, value = 25000.0, target = target, score = 100.0)!!, 0.0001)
    }

    // ---------- TWO_SIDED tests ----------

    /**
     * Rule: TWO_SIDED scores 100 at value == target, graded LINEARLY to 0 at
     * |value - target| >= tolerance * target.
     * score = max(0, 100 * (1 - |value - target| / (tolerance * target))).
     */
    @Test
    fun `two-sided sleep scoring with tolerance 0_35 grades linearly to 0 at boundaries`() {
        val target = 480.0
        val tol = 0.35
        val maxDiff = tol * target // 168.0

        // At target -> 100.0
        val s480 = dailyMetricScore(ScoreShape.TWO_SIDED, MetricScoreInput(value = 480.0, target = target), tol)
        assertEquals(100.0, s480!!, 0.0001)

        // 528 min (+10% / +48 min): 100 * (1 - 48 / 168) = 100 * (5/7) = 71.4285...
        val s528 = dailyMetricScore(ScoreShape.TWO_SIDED, MetricScoreInput(value = 528.0, target = target), tol)
        val expected528 = 100.0 * (1.0 - 48.0 / maxDiff)
        assertEquals(expected528, s528!!, 0.0001)

        // 648 min (+35% / +168 min): exactly at boundary -> 0.0
        val s648 = dailyMetricScore(ScoreShape.TWO_SIDED, MetricScoreInput(value = 648.0, target = target), tol)
        assertEquals(0.0, s648!!, 0.0001)

        // 650 min (beyond boundary) -> 0.0
        val s650 = dailyMetricScore(ScoreShape.TWO_SIDED, MetricScoreInput(value = 650.0, target = target), tol)
        assertEquals(0.0, s650!!, 0.0001)

        // 312 min (-35% / -168 min): lower boundary -> 0.0
        val s312 = dailyMetricScore(ScoreShape.TWO_SIDED, MetricScoreInput(value = 312.0, target = target), tol)
        assertEquals(0.0, s312!!, 0.0001)

        // 456 min (-5% / -24 min): 100 * (1 - 24 / 168) = 100 * (6/7) = 85.7142...
        val s456 = dailyMetricScore(ScoreShape.TWO_SIDED, MetricScoreInput(value = 456.0, target = target), tol)
        val expected456 = 100.0 * (1.0 - 24.0 / maxDiff)
        assertEquals(expected456, s456!!, 0.0001)
    }

    /**
     * Rule: TWO_SIDED calories scoring:
     * target 2260, tolerance 0.35 (maxDiff = 791.0).
     * value 2000 -> diff 260 -> 100 * (1 - 260 / 791) = 67.1302...
     */
    @Test
    fun `two-sided calories scoring evaluates formula 100 times (1 - diff over tolerance times target)`() {
        val target = 2260.0
        val tol = 0.35
        val maxDiff = 791.0 // 0.35 * 2260.0

        val s2260 = dailyMetricScore(ScoreShape.TWO_SIDED, MetricScoreInput(value = 2260.0, target = target), tol)
        assertEquals(100.0, s2260!!, 0.0001)

        val s2000 = dailyMetricScore(ScoreShape.TWO_SIDED, MetricScoreInput(value = 2000.0, target = target), tol)
        val expected2000 = 100.0 * (1.0 - 260.0 / maxDiff)
        assertEquals(expected2000, s2000!!, 0.0001)
    }

    /**
     * Rule: tolerance parameter works for casual tier (e.g. tolerance = 0.5).
     * On sleep 528 (target 480, maxDiff = 240.0): 100 * (1 - 48 / 240) = 80.0.
     */
    @Test
    fun `two-sided tolerance parameterisation adjusts the linear gradient`() {
        val target = 480.0
        val tol = 0.50
        val s528Casual = dailyMetricScore(ScoreShape.TWO_SIDED, MetricScoreInput(value = 528.0, target = target), tol)
        assertEquals(80.0, s528Casual!!, 0.0001)
    }

    // ---------- PROGRESS tests ----------

    /**
     * Rule: PROGRESS scoring:
     * at start = 50, at goal = 100, past goal = 100 (clamp),
     * regression floors at 0.0 when regressing by half the goal distance.
     * start 92, goal 86 (weight loss, goal distance = 6):
     * current 92 -> 50.0
     * current 89 -> 75.0 (halfway)
     * current 87.5 -> 87.5 (75% to goal)
     * current 86 -> 100.0 (at goal)
     * current 83 -> 100.0 (overshoot clamped)
     * current 95 -> 0.0 (regressed by 3kg = half goal distance, floored at 0)
     */
    @Test
    fun `progress weight scoring handles start, progress, goal, overshoot and regression floor`() {
        val start = 92.0
        val goal = 86.0

        fun scoreFor(current: Double): Double? =
            dailyMetricScore(
                ScoreShape.PROGRESS,
                MetricScoreInput(
                    value = current,
                    target = goal,
                    progress = ProgressInputs(current = current, start = start, goal = goal),
                ),
            )

        // At start -> 50
        assertEquals(50.0, scoreFor(92.0)!!, 0.0001)

        // Halfway to goal (89kg) -> 75
        assertEquals(75.0, scoreFor(89.0)!!, 0.0001)

        // 75% to goal (87.5kg) -> 87.5
        assertEquals(87.5, scoreFor(87.5)!!, 0.0001)

        // At goal (86kg) -> 100
        assertEquals(100.0, scoreFor(86.0)!!, 0.0001)

        // Overshoot past goal (83kg) -> clamps at 100
        assertEquals(100.0, scoreFor(83.0)!!, 0.0001)

        // Regression (95kg, gained 3kg) -> floors at 0
        assertEquals(0.0, scoreFor(95.0)!!, 0.0001)

        // Severe regression (98kg) -> floors at 0
        assertEquals(0.0, scoreFor(98.0)!!, 0.0001)
    }

    /**
     * Rule: PROGRESS supports goal > start (e.g. muscle / weight gain):
     * start 75, goal 80:
     * current 75 -> 50.0
     * current 77.5 -> 75.0
     * current 80 -> 100.0
     * current 82 -> 100.0 (overshoot)
     * current 72.5 -> 0.0 (regressed by 2.5kg = half goal distance)
     */
    @Test
    fun `progress scoring works identically for positive direction weight gain`() {
        val start = 75.0
        val goal = 80.0

        fun scoreFor(current: Double): Double? =
            dailyMetricScore(
                ScoreShape.PROGRESS,
                MetricScoreInput(
                    value = current,
                    target = goal,
                    progress = ProgressInputs(current = current, start = start, goal = goal),
                ),
            )

        assertEquals(50.0, scoreFor(75.0)!!, 0.0001)
        assertEquals(75.0, scoreFor(77.5)!!, 0.0001)
        assertEquals(100.0, scoreFor(80.0)!!, 0.0001)
        assertEquals(100.0, scoreFor(82.0)!!, 0.0001)
        assertEquals(0.0, scoreFor(72.5)!!, 0.0001)
    }

    // ---------- Null handling tests ----------

    /**
     * Rule: Null handling:
     * - null value -> null
     * - ONE_SIDED with null target -> null
     * - TWO_SIDED with null target -> null
     * - target <= 0 -> null
     * - PROGRESS degenerate goal (start == goal) -> null
     * - PROGRESS with null progressInputs -> null
     */
    @Test
    fun `null handling returns null for missing or degenerate data`() {
        // No value -> null
        assertNull(dailyMetricScore(ScoreShape.ONE_SIDED, MetricScoreInput(value = null, target = 10000.0)))
        assertNull(dailyMetricScore(ScoreShape.TWO_SIDED, MetricScoreInput(value = null, target = 480.0)))
        assertNull(dailyMetricScore(ScoreShape.PROGRESS, MetricScoreInput(value = null, target = 86.0, progress = null)))

        // Null target for ONE_SIDED / TWO_SIDED -> null
        assertNull(dailyMetricScore(ScoreShape.ONE_SIDED, MetricScoreInput(value = 5000.0, target = null)))
        assertNull(dailyMetricScore(ScoreShape.TWO_SIDED, MetricScoreInput(value = 480.0, target = null)))

        // Target <= 0 -> null
        assertNull(dailyMetricScore(ScoreShape.ONE_SIDED, MetricScoreInput(value = 5000.0, target = 0.0)))
        assertNull(dailyMetricScore(ScoreShape.ONE_SIDED, MetricScoreInput(value = 5000.0, target = -100.0)))
        assertNull(dailyMetricScore(ScoreShape.TWO_SIDED, MetricScoreInput(value = 480.0, target = 0.0)))
        assertNull(dailyMetricScore(ScoreShape.TWO_SIDED, MetricScoreInput(value = 480.0, target = -480.0)))

        // Degenerate goal (start == goal) -> null
        assertNull(
            dailyMetricScore(
                ScoreShape.PROGRESS,
                MetricScoreInput(
                    value = 85.0,
                    target = 85.0,
                    progress = ProgressInputs(current = 85.0, start = 85.0, goal = 85.0),
                ),
            ),
        )
    }

    // ---------- Points tests ----------

    /**
     * Rule: Points calculation:
     * - PROGRESS -> null (points TBD when check-in round lands)
     * - TWO_SIDED: round(10 * score / 100), max 10
     *   100 -> 10, 88.5 -> 9, null -> null
     */
    @Test
    fun `points calculation produces expected rounded points and null for progress`() {
        // PROGRESS points are null
        assertNull(dailyMetricPoints(ScoreShape.PROGRESS, value = 89.0, target = 86.0, score = 75.0))

        // TWO_SIDED points
        assertEquals(10.0, dailyMetricPoints(ScoreShape.TWO_SIDED, value = 480.0, target = 480.0, score = 100.0)!!, 0.0001)
        assertEquals(9.0, dailyMetricPoints(ScoreShape.TWO_SIDED, value = 2000.0, target = 2260.0, score = 88.5)!!, 0.0001)
        assertEquals(0.0, dailyMetricPoints(ScoreShape.TWO_SIDED, value = 648.0, target = 480.0, score = 0.0)!!, 0.0001)
        assertNull(dailyMetricPoints(ScoreShape.TWO_SIDED, value = null, target = 480.0, score = null))
    }

    // ---------- CHANGE 3: Integration test with weekContributions ----------

    /**
     * Sanity integration test: build a fake week of per-metric scores and
     * verify weekContributions(series, weights, 7) still sums to exactly 100.0
     * with the new score shapes feeding it.
     */
    @Test
    fun `sanity integration - weekContributions shares sum to exactly 100 with new score shapes`() {
        val days = listOf(
            "2026-10-19", "2026-10-20", "2026-10-21",
            "2026-10-22", "2026-10-23", "2026-10-24", "2026-10-25",
        )

        // Steps (ONE_SIDED, target 10000)
        val stepScores = listOf(8000.0, 10000.0, 12000.0, 9500.0, 11000.0, 7500.0, 10500.0).map { v ->
            dailyMetricScore(ScoreShape.ONE_SIDED, MetricScoreInput(value = v, target = 10000.0))
        }

        // Calories (TWO_SIDED, target 2260)
        val calorieScores = listOf(2260.0, 2100.0, 2350.0, 2200.0, 2000.0, 2400.0, 2260.0).map { v ->
            dailyMetricScore(ScoreShape.TWO_SIDED, MetricScoreInput(value = v, target = 2260.0))
        }

        // Protein (ONE_SIDED, target 160)
        val proteinScores = listOf(160.0, 150.0, 170.0, 140.0, 165.0, 155.0, 160.0).map { v ->
            dailyMetricScore(ScoreShape.ONE_SIDED, MetricScoreInput(value = v, target = 160.0))
        }

        // Sleep (TWO_SIDED, target 480)
        val sleepScores = listOf(480.0, 500.0, 460.0, 470.0, 490.0, 520.0, 480.0).map { v ->
            dailyMetricScore(ScoreShape.TWO_SIDED, MetricScoreInput(value = v, target = 480.0))
        }

        // Weight (PROGRESS, start 92, goal 86)
        val weightScores = listOf(92.0, 91.5, 91.0, 90.0, 89.5, 89.0, 88.5).map { v ->
            dailyMetricScore(
                ScoreShape.PROGRESS,
                MetricScoreInput(
                    value = v,
                    target = 86.0,
                    progress = ProgressInputs(current = v, start = 92.0, goal = 86.0),
                ),
            )
        }

        // Exercise (ONE_SIDED, target 45)
        val exerciseScores = listOf(45.0, 30.0, 60.0, 0.0, 45.0, 50.0, 45.0).map { v ->
            dailyMetricScore(ScoreShape.ONE_SIDED, MetricScoreInput(value = v, target = 45.0))
        }

        fun makeSeries(id: GoalId, name: String, scores: List<Double?>): GoalSeries {
            val goalDays = days.zip(scores) { d, s -> GoalDay(d, s) }
            val latest = scores.lastOrNull { it != null } ?: 0.0
            return GoalSeries(id, name, goalDays, latest, "momentum-rework")
        }

        val allSeries = listOf(
            makeSeries(GoalId.STEPS, "Steps", stepScores),
            makeSeries(GoalId.CALORIES, "Calories", calorieScores),
            makeSeries(GoalId.PROTEIN, "Protein", proteinScores),
            makeSeries(GoalId.SLEEP, "Sleep", sleepScores),
            makeSeries(GoalId.WEIGHT, "Weight", weightScores),
            makeSeries(GoalId.EXERCISE, "Exercise", exerciseScores),
        )

        val weights = GOAL_WEIGHTS[GoalPreset.FAT_LOSS]!!
        val contribs = weekContributions(allSeries, weights, 7)

        // All participating series should have non-null shares summing to exactly 100.0
        val totalShare = contribs.sumOf { it.share }
        assertEquals(100.0, totalShare, 0.0001)

        val weekScore = weekScoreFromContributions(contribs)
        org.junit.Assert.assertNotNull(weekScore)
        assertEquals(weekScore!!, contribs.sumOf { it.part }, 0.0001)
    }
}
