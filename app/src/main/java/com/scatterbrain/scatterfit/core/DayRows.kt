package com.scatterbrain.scatterfit.core

/**
 * Rows for the "RECENT DAYS" list under a metric chart.
 *
 * The list is built from the DAY KEYS, not from the readings. Sam's report
 * (2026-11-18): "if I skip a day or two it doesn't show the days and just
 * leaves a blank section". A day with no reading is still a day — it is the
 * evidence of the gap — so every day in the window gets a row and a null
 * value renders as "no reading" rather than the row disappearing.
 *
 * Newest day first, because that is the day you are looking at.
 */
object DayRows {

    data class Row(
        val dayKey: String,
        val value: Double?,
        val average: Double?,
    )

    const val DEFAULT_COUNT = 7

    /**
     * @param dayKeys window keys, oldest first (as [lastNDayKeys] returns them).
     * @param values same length as [dayKeys]; null where nothing was recorded.
     * @param averages same length as [dayKeys]; the moving average, if any.
     */
    fun build(
        dayKeys: List<String>,
        values: List<Double?>,
        averages: List<Double?> = emptyList(),
        count: Int = DEFAULT_COUNT,
    ): List<Row> {
        if (dayKeys.isEmpty()) return emptyList()
        val from = (dayKeys.size - count.coerceAtLeast(1)).coerceAtLeast(0)
        return (dayKeys.size - 1 downTo from).map { i ->
            Row(
                dayKey = dayKeys[i],
                value = values.getOrNull(i),
                average = averages.getOrNull(i),
            )
        }
    }

    /** How many of the rows actually carry a reading. */
    fun loggedCount(rows: List<Row>): Int = rows.count { it.value != null }
}
