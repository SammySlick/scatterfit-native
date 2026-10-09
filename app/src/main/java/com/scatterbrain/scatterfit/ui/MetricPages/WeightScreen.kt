package com.scatterbrain.scatterfit.ui.MetricPages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.scatterbrain.scatterfit.ui.MetricSource
import com.scatterbrain.scatterfit.ui.WeightMetricSourceStub
import com.scatterbrain.scatterfit.data.RealMetricSources
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.ui.MetricChartType
import com.scatterbrain.scatterfit.ui.MetricPageScreen
import com.scatterbrain.scatterfit.ui.theme.JudgementGood
import java.util.Locale

@Composable
fun WeightScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    metricSource: MetricSource = remember { RealMetricSources.weight() ?: WeightMetricSourceStub() },
    todayKey: String = remember { todayKey() }
) {
    val latest = remember(metricSource) { metricSource.latest() }
    val currentWeight = latest?.value

    MetricPageScreen(
        title = "Weight",
        heroValue = if (currentWeight != null) String.format(Locale.US, "%.1f", currentWeight) else "—",
        heroUnit = if (currentWeight != null) "kg" else "",
        heroScore = null, // raw metric, no judgement score
        trendText = "−0.4kg",
        trendColor = JudgementGood,
        verdictLabel = "Steady",
        verdictTone = JudgementGood,
        insightSentence = "−0.4kg this week · on track for fat loss target.",
        chartType = MetricChartType.LINE, // Line chart for sparse weight data with gaps
        metricSource = metricSource,
        anchorDayKey = todayKey,
        today = todayKey,
        onBackClick = onBackClick,
        formatValue = { v -> if (v != null) String.format(Locale.US, "%.1f kg", v) else "—" },
        modifier = modifier
    )
}
