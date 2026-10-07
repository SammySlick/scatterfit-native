package com.scatterbrain.scatterfit.core

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * METRIC AXES — Kotlin port of web `src/lib/analysis/metrics.ts` axis helpers.
 * Accepted per Native Foundations: web vitest cases are the acceptance tests.
 *
 * These feed the metric pages' charts (Compose Canvas in the spike). Values
 * and policy per metric live with the UI config; only the maths is core.
 */

/** Uniform round-number axis for "starts at zero" charts. Picks a step from
 *  the 1/2/2.5/5 ladder so the top of the axis is a clean ceiling just above
 *  the data, with explicit ticks so charts can't invent odd ones. */
fun niceZeroAxis(values: List<Double?>, target: Double? = null): Pair<List<Double>, List<Double>> {
    val present = (values + target).filterNotNull().filter { it.isFinite() }
    val maxValue = if (present.isEmpty()) 1.0 else present.max()
    val ceil = maxValue * 1.08
    val pow = 10.0.pow(floor(log10(ceil / 4)))
    var step = pow
    for (m in listOf(1.0, 2.0, 2.5, 5.0, 10.0)) {
        if (m * pow >= ceil / 4) { step = m * pow; break }
    }
    val top = max(step, ceil(ceil / step) * step)
    val ticks = ArrayList<Double>()
    var v = 0.0
    while (v <= top + step / 1000) {
        ticks.add(kotlin.math.round(v * 1000.0) / 1000.0)
        v += step
    }
    return listOf(0.0, top) to ticks
}

fun metricAxisDomain(
    values: List<Double?>,
    policy: AxisPolicy,
    target: Double? = null,
): Pair<Double, Double> {
    val present = (values + target).filterNotNull().filter { it.isFinite() }
    if (present.isEmpty()) return if (policy == AxisPolicy.ZERO) 0.0 to 1.0 else 0.0 to 100.0
    val min = present.min()
    val max = present.max()
    val span = max(max - min, max(kotlin.math.abs(max) * 0.02, 1.0))
    return when (policy) {
        AxisPolicy.ZERO -> 0.0 to max(1.0, ceil(max * 1.1))
        AxisPolicy.RESTING_HR -> {
            val floorV = min(50.0, floor(min - 5))
            floorV to ceil(max + max(5.0, (max - floorV) * 0.25))
        }
        AxisPolicy.SYMMETRIC -> {
            val padding = max(span * 0.12, 0.5)
            floor((min - padding) * 10) / 10.0 to ceil((max + padding) * 10) / 10.0
        }
        AxisPolicy.TIGHT -> {
            val padding = max(span * 0.08, 0.2)
            floor((min - padding) * 10) / 10.0 to ceil((max + padding) * 10) / 10.0
        }
        AxisPolicy.AUTO -> {
            val padding = max(span * 0.1, 1.0)
            floor(min - padding) to ceil(max + padding)
        }
    }
}

enum class AxisPolicy { ZERO, RESTING_HR, TIGHT, SYMMETRIC, AUTO }
