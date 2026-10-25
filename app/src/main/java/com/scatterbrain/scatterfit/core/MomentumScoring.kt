package com.scatterbrain.scatterfit.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

enum class ScoreShape {
    ONE_SIDED,
    TWO_SIDED,
    PROGRESS,
}

data class MetricScoreInput(
    val value: Double?,
    val target: Double?,
    val progress: ProgressInputs? = null,
)

data class ProgressInputs(
    val current: Double,
    val start: Double,
    val goal: Double,
)

/**
 * Computes the per-metric daily score (0-100) according to its [shape].
 * Returns null when data is missing or degenerate (never a fake 0).
 */
fun dailyMetricScore(
    shape: ScoreShape,
    input: MetricScoreInput,
    twoSidedTolerance: Double = 0.35,
): Double? {
    when (shape) {
        ScoreShape.ONE_SIDED -> {
            val value = input.value ?: return null
            val target = input.target ?: return null
            if (target <= 0.0) return null
            return min(100.0, (value / target) * 100.0)
        }
        ScoreShape.TWO_SIDED -> {
            val value = input.value ?: return null
            val target = input.target ?: return null
            if (target <= 0.0 || twoSidedTolerance <= 0.0) return null
            val diff = abs(value - target)
            val maxDiff = twoSidedTolerance * target
            return max(0.0, 100.0 * (1.0 - (diff / maxDiff)))
        }
        ScoreShape.PROGRESS -> {
            val progress = input.progress ?: return null
            val start = progress.start
            val goal = progress.goal
            if (goal == start) return null

            val current = input.value ?: progress.current
            val progressFraction = (current - start) / (goal - start)
            return if (progressFraction >= 0.0) {
                // Moving towards or past goal: 50 at start, 100 at goal, clamps at 100 past goal
                min(100.0, 50.0 + (50.0 * progressFraction))
            } else {
                // Moving away from goal (regression): floor at 0.0 when regressing by half the goal distance
                max(0.0, 50.0 + (100.0 * progressFraction))
            }
        }
    }
}

/**
 * Computes daily points for a metric according to its [shape].
 * - ONE_SIDED: round(10 × value/target) capped at 20 (target <= 0 -> null)
 * - TWO_SIDED: round(10 × score/100) when score != null else null (max 10)
 * - PROGRESS: null for now (points TBD when check-in round lands)
 */
fun dailyMetricPoints(
    shape: ScoreShape,
    value: Double?,
    target: Double?,
    score: Double?,
): Double? {
    return when (shape) {
        ScoreShape.ONE_SIDED -> {
            if (value == null || target == null || target <= 0.0) null
            else min(20.0, max(0.0, round(10.0 * (value / target))))
        }
        ScoreShape.TWO_SIDED -> {
            if (score == null) null
            else min(10.0, max(0.0, round(10.0 * (score / 100.0))))
        }
        ScoreShape.PROGRESS -> null
    }
}
