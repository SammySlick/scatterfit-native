package com.scatterbrain.scatterfit.core

import java.text.NumberFormat
import java.util.Locale

/**
 * METRIC TARGET CONTEXT — Kotlin port of web `src/lib/metricTargets.ts`.
 * Accepted per Native Foundations: web vitest cases are the acceptance tests.
 *
 * Shared goal verdict and comparison copy. Movement belongs to the
 * prior-period delta, never the verdict (Sam 2026-10-04 21:21: verdicts must
 * NAME their frame and their goal — "0.3 kg from 80 kg" beside a hero reading
 * was unreadable; windowLabel makes the average's window explicit).
 */
enum class MetricId {
    STEPS, WEIGHT, CALORIES_EATEN, PROTEIN, SLEEP,
    TRAINING_MINUTES, RESTING_HR, ALCOHOL_UNITS, CALORIES_BURNED
}

data class MetricTargetContext(
    val verdict: String,
    val comparison: String,
    val action: String?,
)

private val integer = NumberFormat.getIntegerInstance(Locale.US)!! // toLocaleString() equivalent (1,000 grouping)

private fun num(value: Double, decimals: Int): String =
    if (decimals == 0) integer.format(Math.round(value)) else String.format(Locale.US, "%.${decimals}f", value)

fun metricTargetContext(
    metric: MetricId,
    mean: Double?,
    settings: ScoringSettings,
    recent: Boolean = false,
    windowLabel: String = "this week avg",
): MetricTargetContext {
    if (mean == null) {
        return MetricTargetContext("Not enough data", "No readings in this period", null)
    }

    return when (metric) {
        MetricId.STEPS -> {
            val gap = settings.stepTarget - mean
            MetricTargetContext(
                verdict = if (gap <= 0) "On target" else "Below target",
                comparison = "$windowLabel ${num(mean, 0)} vs ${num(settings.stepTarget.toDouble(), 0)} daily target",
                action = if (recent && gap > 0) "${num(gap, 0)} steps would close today’s gap." else null,
            )
        }
        MetricId.WEIGHT -> {
            val gap = mean - settings.weightTarget
            MetricTargetContext(
                verdict = when {
                    Math.abs(gap) <= 0.2 + 1e-9 -> "On target" // epsilon: 80.2-80.0 == 0.20000000000000283 in FP
                    gap > 0 -> "Above target"
                    else -> "Below target"
                },
                comparison = "$windowLabel ${formatWeight(mean, settings.unitSystem)} vs ${formatWeight(settings.weightTarget, settings.unitSystem)} target",
                action = null,
            )
        }
        MetricId.CALORIES_EATEN -> {
            val kcalTarget = settings.kcalTarget
            val gap = mean - kcalTarget
            val onTarget = Math.abs(gap) / Math.max(kcalTarget, 1) <= 0.1
            MetricTargetContext(
                verdict = if (onTarget) "On target" else if (gap < 0) "Below target" else "Above target",
                comparison = "$windowLabel ${num(mean, 0)} vs ${num(kcalTarget.toDouble(), 0)} kcal daily target",
                action = if (recent && !onTarget) "${num(Math.abs(gap), 0)} kcal ${if (gap < 0) "below" else "above"} target." else null,
            )
        }
        MetricId.PROTEIN -> {
            val proteinTarget = settings.proteinTarget
            val gap = proteinTarget - mean
            MetricTargetContext(
                verdict = if (gap <= 0) "On target" else "Below target",
                comparison = "$windowLabel ${num(mean, 0)}g vs ${num(proteinTarget.toDouble(), 0)}g protein target",
                action = if (recent && gap > 0) "${num(gap, 0)}g protein would close today’s gap." else null,
            )
        }
        MetricId.SLEEP -> {
            val gap = mean - settings.sleepTargetHours
            val onTarget = Math.abs(gap) <= 0.5 + 1e-9
            MetricTargetContext(
                verdict = if (onTarget) "On target" else if (gap < 0) "Below target" else "Above target",
                comparison = "avg ${num(mean, 1)}h vs ${num(settings.sleepTargetHours, 1)}h",
                action = if (recent && gap < -0.5) "${Math.round(Math.abs(gap) * 60)} min below target." else null,
            )
        }
        MetricId.TRAINING_MINUTES -> {
            // Sam 2026-10-05 12:10 (zones interview Q6): training minutes are Z3+,
            // so the pace target is the Z3+Z4+Z5 weekly goals (zone tiles keep the
            // per-zone split; the chart's dashed line uses the Z2+ total).
            val weekly = mean * 7
            val z3PlusTarget = settings.z3Target + settings.z4Target + settings.z5Target
            val gap = z3PlusTarget - weekly
            MetricTargetContext(
                verdict = if (gap <= 0) "On pace" else "Below pace",
                comparison = "pace ${num(weekly, 0)} vs ${num(z3PlusTarget.toDouble(), 0)} Z3+ min/week",
                action = if (recent && gap > 0) "${num(gap, 0)} Z3+ min needed to reach weekly pace." else null,
            )
        }
        MetricId.ALCOHOL_UNITS -> {
            val weekly = mean * 7
            val gap = weekly - settings.unitsPerWeek
            MetricTargetContext(
                verdict = if (gap <= 0) "On pace" else "Above pace",
                comparison = "pace ${num(weekly, 1)} vs ${num(settings.unitsPerWeek.toDouble(), 1)} units/week",
                action = if (recent && gap > 0) "${num(gap, 1)} units above weekly pace." else null,
            )
        }
        MetricId.RESTING_HR -> MetricTargetContext("Tracking", "avg ${num(mean, 0)} bpm", null)
        MetricId.CALORIES_BURNED -> MetricTargetContext("Tracking", "avg ${num(mean, 0)} kcal", null)
    }
}
