package com.scatterbrain.scatterfit.core

/**
 * Momentum contributions — per-metric share of the week's momentum score.
 * Port of the web app's weekContributions (L audit, commit 6480fe3, 2026-10-06):
 *
 *  - ONLY series with data (a non-null score somewhere in the window) enter
 *    the denominator — untracked metrics never drag or dilute the total.
 *  - Zero-weight series are excluded entirely (mirrors weightedDayScore:
 *    soft series never lift the score).
 *  - Each part = weight × window-mean-score, renormalised by the total
 *    weight of participating series. Parts sum EXACTLY to the renormalised
 *    week score — same numbers, sliced per metric.
 *
 * The share (per cent of the week score) is the spider-graph axis length;
 * the mean is its colour source. Both are supplied; the UI never recomputes.
 */
data class MomentumContribution(
    val id: GoalId,
    val name: String,
    /** Mean non-null daily score across the window (0-100). */
    val mean: Double,
    /** Days in the window that actually had a score. */
    val loggedDays: Int,
    /** Share of the week score, 0-100. All shares sum to 100.0. */
    val share: Double,
    /** Points contributed to the week score (= share × weekScore / 100). */
    val part: Double,
)

fun weekContributions(
    series: List<GoalSeries>,
    weights: Map<GoalId, Double>,
    days: Int,
): List<MomentumContribution> {
    if (series.isEmpty()) return emptyList()
    val window = series.first().days.takeLast(days).map { it.day }
    val participating = series.filter { s ->
        val w = weights[s.id] ?: 0.0
        w > 0.0 && s.days.any { it.day in window && it.score != null }
    }
    val totalW = participating.sumOf { weights[it.id] ?: 0.0 }
    if (totalW <= 0.0) return emptyList()
    val parts = participating.map { s ->
        val scored = s.days.filter { it.day in window && it.score != null }
        val mean = scored.mapNotNull { it.score }.average()
        Triple(s, mean, (weights[s.id] ?: 0.0) * mean / totalW)
    }
    val sum = parts.sumOf { it.third }
    return parts.map { (s, mean, part) ->
        MomentumContribution(
            id = s.id,
            name = s.name,
            mean = mean,
            loggedDays = scoredCount(s, window),
            share = if (sum > 0) part / sum * 100.0 else 0.0,
            part = part,
        )
    }
}

private fun scoredCount(s: GoalSeries, window: List<String>): Int =
    s.days.count { it.day in window && it.score != null }

/** The renormalised week score the parts sum to — same rule as
 *  weightedDayScore, applied to window means. Exposed so the UI can label
 *  the total and tests can pin the sum property. */
fun weekScoreFromContributions(parts: List<MomentumContribution>): Double? =
    if (parts.isEmpty()) null else parts.sumOf { it.part }
