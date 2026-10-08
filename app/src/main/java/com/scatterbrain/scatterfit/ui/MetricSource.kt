package com.scatterbrain.scatterfit.ui

import com.scatterbrain.scatterfit.core.todayKey

interface MetricSource {
    fun series(days: List<String>): List<MetricPoint>
    fun latest(): MetricPoint?
}

data class MetricPoint(
    val dayKey: String,
    val value: Double?,
    val score: Double? = null
)

enum class MetricPeriod(val days: Int, val label: String) {
    PERIOD_7D(7, "7D"),
    PERIOD_30D(30, "30D"),
    PERIOD_90D(90, "90D")
}

fun sliceMetricPoints(points: List<MetricPoint>, period: MetricPeriod): List<MetricPoint> {
    return if (points.size <= period.days) points else points.takeLast(period.days)
}

class StepsMetricSourceStub(
    private val today: String = todayKey()
) : MetricSource {
    override fun series(days: List<String>): List<MetricPoint> {
        val baseSteps = listOf(8420.0, 9150.0, 7800.0, 10420.0, 6900.0, 8900.0, 10100.0)
        return days.mapIndexed { index, dayKey ->
            val value = baseSteps[index % baseSteps.size] + ((index * 137) % 1500) - 750
            val score = ((value / 10000.0) * 100.0).coerceIn(0.0, 100.0)
            MetricPoint(dayKey = dayKey, value = value, score = score)
        }
    }

    override fun latest(): MetricPoint {
        return MetricPoint(dayKey = today, value = 8420.0, score = 84.0)
    }
}

class SleepMetricSourceStub(
    private val today: String = todayKey()
) : MetricSource {
    override fun series(days: List<String>): List<MetricPoint> {
        val baseHours = listOf(8.22, 7.85, 8.0, 6.9, 7.5, 8.4, 7.75)
        return days.mapIndexed { index, dayKey ->
            val value = baseHours[index % baseHours.size]
            val score = ((value / 8.0) * 85.0).coerceIn(40.0, 100.0)
            MetricPoint(dayKey = dayKey, value = value, score = score)
        }
    }

    override fun latest(): MetricPoint {
        return MetricPoint(dayKey = today, value = 8.22, score = 88.0)
    }
}

class WeightMetricSourceStub(
    private val today: String = todayKey()
) : MetricSource {
    override fun series(days: List<String>): List<MetricPoint> {
        return days.mapIndexed { index, dayKey ->
            if (index % 3 == 0 || dayKey == today) {
                val value = 85.8 - (index * 0.015).coerceAtMost(0.6)
                MetricPoint(dayKey = dayKey, value = value, score = null)
            } else {
                MetricPoint(dayKey = dayKey, value = null, score = null)
            }
        }
    }

    override fun latest(): MetricPoint {
        return MetricPoint(dayKey = today, value = 85.4, score = null)
    }
}
