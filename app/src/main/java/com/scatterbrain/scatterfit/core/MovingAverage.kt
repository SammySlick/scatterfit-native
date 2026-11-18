package com.scatterbrain.scatterfit.core

/**
 * Trailing moving average over a day series that has holes in it.
 *
 * Two rules, both learned the hard way:
 *
 * 1. A missing day contributes NOTHING — it is not a zero. Averaging a 7-day
 *    window with three holes as if the missing days were 0 would report a
 *    weekly average a third of the truth, which is worse than showing nothing.
 *
 * 2. A window needs a minimum number of real readings before it reports. Two
 *    weigh-ins 6 days apart are not a "7-day average"; they are two readings
 *    wearing a disguise. [minPoints] defaults to 3.
 *
 * Trailing (not centred): the average at day N is what you could have known on
 * day N, so the line never shows a value that a past-you couldn't have seen.
 */
object MovingAverage {

    const val DEFAULT_WINDOW = 7
    const val DEFAULT_MIN_POINTS = 3

    /**
     * @param values one entry per day, null where nothing was recorded.
     * @return same length as [values]; null wherever the window held fewer
     *         than [minPoints] real readings.
     */
    fun rolling(
        values: List<Double?>,
        window: Int = DEFAULT_WINDOW,
        minPoints: Int = DEFAULT_MIN_POINTS,
    ): List<Double?> {
        val w = window.coerceAtLeast(1)
        val need = minPoints.coerceIn(1, w)
        val out = ArrayList<Double?>(values.size)
        for (i in values.indices) {
            var sum = 0.0
            var count = 0
            val from = (i - w + 1).coerceAtLeast(0)
            for (j in from..i) {
                val v = values[j] ?: continue
                sum += v
                count++
            }
            out.add(if (count >= need) sum / count else null)
        }
        return out
    }

    /** Convenience for a series of points: same day keys, averaged values. */
    fun <T> rollingPoints(
        points: List<T>,
        key: (T) -> String,
        value: (T) -> Double?,
        window: Int = DEFAULT_WINDOW,
        minPoints: Int = DEFAULT_MIN_POINTS,
    ): List<MovingAveragePoint> {
        val avg = rolling(points.map(value), window, minPoints)
        return points.mapIndexed { i, p -> MovingAveragePoint(key(p), avg[i]) }
    }

    /** True when the series has enough real readings to average at all. */
    fun hasEnough(values: List<Double?>, minPoints: Int = DEFAULT_MIN_POINTS): Boolean =
        values.count { it != null } >= minPoints
}

data class MovingAveragePoint(val dayKey: String, val value: Double?)
