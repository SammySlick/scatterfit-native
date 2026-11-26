package com.scatterbrain.scatterfit.ui

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * AXIS MATHS — pure, so the numbers on a chart can be tested without a canvas.
 *
 * Sam, 2026-11-26: "Numbers on the X and Y of the graphs." The charts hid both
 * axes and put the units in a legend underneath, because a colour with no label
 * means nothing. The cost was that you could not read a value off the picture at
 * all — only the height, relative to nothing.
 *
 * The labels have to be legible, which means FEW of them and on round numbers.
 * 0/1,000/2,000 is readable; 0/967/1,934 is not. So the axis is snapped to a
 * "nice" step (1, 2 or 5 times a power of ten) and the plot area grows outward
 * to the nearest tick, rather than a tick being forced inside the data range and
 * the top of the line being clipped.
 */

/**
 * Tick values spanning [min]..[max] on 1/2/5 x 10^n steps.
 *
 * The returned list always brackets the input: `first() <= min` and
 * `last() >= max` whenever the range is non-degenerate, so drawing to these
 * bounds can never clip a real reading.
 */
fun niceAxisTicks(min: Double, max: Double, count: Int = 4): List<Double> {
    if (!min.isFinite() || !max.isFinite() || count < 2) return emptyList()
    val lo = minOf(min, max)
    val hi = maxOf(min, max)
    // A flat series (every weigh-in identical) has no range to divide. One tick
    // at that value beats a divide-by-zero and beats inventing a fake spread.
    if (hi - lo < 1e-9) return listOf(roundClean(lo, 0))
    val step = niceStep((hi - lo) / (count - 1))
    val start = floor(lo / step) * step
    val decimals = decimalsFor(step)
    val out = ArrayList<Double>(count + 2)
    var i = 0
    while (out.size < count + 2) {
        val v = roundClean(start + i * step, decimals)
        if (v > hi + step * 1e-6) break
        out.add(v)
        i++
    }
    // Guard the contract: if rounding left the top tick below the data, extend.
    if (out.isEmpty() || out.last() < hi) {
        out.add(roundClean(if (out.isEmpty()) hi else out.last() + step, decimals))
    }
    return out
}

/** The nearest 1/2/5 x 10^n step at or above [raw]. */
fun niceStep(raw: Double): Double {
    if (!raw.isFinite() || raw <= 0.0) return 1.0
    val exp = floor(log10(raw))
    val base = 10.0.pow(exp)
    val f = raw / base
    val mult = when {
        f <= 1.0 -> 1.0
        f <= 2.0 -> 2.0
        f <= 5.0 -> 5.0
        else -> 10.0
    }
    return mult * base
}

/**
 * Short axis label: "10k" not "10,000", because a 34dp gutter cannot hold
 * four digits plus a comma at a legible size.
 */
fun shortAxisNumber(value: Double): String {
    if (!value.isFinite()) return ""
    val magnitude = abs(value)
    return when {
        magnitude >= 1_000_000 -> trimZeros(value / 1_000_000) + "m"
        magnitude >= 1_000 -> trimZeros(value / 1_000) + "k"
        magnitude >= 10 -> value.roundToInt().toString()
        magnitude == 0.0 -> "0"
        else -> trimZeros(value)
    }
}

private fun trimZeros(v: Double): String {
    val oneDp = (Math.round(v * 10.0) / 10.0)
    return if (oneDp == Math.floor(oneDp) && abs(oneDp) < 1e15) {
        oneDp.toLong().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", oneDp)
    }
}

private fun decimalsFor(step: Double): Int {
    if (step >= 1.0) return 0
    return (-floor(log10(step))).toInt() + 1
}

private fun roundClean(v: Double, decimals: Int): Double =
    if (decimals <= 0) v.roundToInt().toDouble()
    else String.format(java.util.Locale.US, "%.${decimals}f", v).toDouble()

/**
 * Which day indexes get an X label: always the first and last, evenly spaced
 * between. More than ~4 labels on a 360dp-wide plot collide into a grey bar.
 */
fun axisLabelIndexes(n: Int, maxLabels: Int = 4): List<Int> {
    if (n <= 0) return emptyList()
    if (maxLabels < 2) return listOf(0)
    if (n <= maxLabels) return (0 until n).toList()
    val out = LinkedHashSet<Int>()
    for (k in 0 until maxLabels) {
        out.add(((n - 1).toDouble() * k / (maxLabels - 1)).roundToInt())
    }
    return out.sorted()
}
