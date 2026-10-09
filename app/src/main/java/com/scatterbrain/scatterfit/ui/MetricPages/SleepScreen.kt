package com.scatterbrain.scatterfit.ui.MetricPages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.scatterbrain.scatterfit.ui.MetricSource
import com.scatterbrain.scatterfit.data.RealMetricSources
import com.scatterbrain.scatterfit.ui.SleepMetricSourceStub
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.ui.MetricChartType
import com.scatterbrain.scatterfit.ui.MetricPageScreen
import com.scatterbrain.scatterfit.ui.theme.JudgementGood
import kotlin.math.roundToInt

@Composable
fun SleepScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    metricSource: MetricSource = remember { RealMetricSources.sleep() ?: SleepMetricSourceStub() },
    todayKey: String = remember { todayKey() }
) {
    val latest = remember(metricSource) { metricSource.latest() }
    val latestHoursDecimal = latest?.value ?: 8.22
    val totalMinutes = (latestHoursDecimal * 60.0).roundToInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val heroDuration = "${hours}h ${minutes}m"

    MetricPageScreen(
        title = "Sleep",
        heroValue = heroDuration,
        heroUnit = "last night",
        heroScore = latest?.score ?: 88.0,
        trendText = "+24m",
        trendColor = JudgementGood,
        verdictLabel = "High",
        verdictTone = JudgementGood,
        insightSentence = "7h 52m average this week vs 8h target.",
        chartType = MetricChartType.BAR,
        metricSource = metricSource,
        anchorDayKey = todayKey,
        today = todayKey,
        onBackClick = onBackClick,
        formatValue = { v ->
            if (v != null) {
                val mins = (v * 60.0).roundToInt()
                "${mins / 60}h ${mins % 60}m"
            } else "—"
        },
        modifier = modifier
    )
}
