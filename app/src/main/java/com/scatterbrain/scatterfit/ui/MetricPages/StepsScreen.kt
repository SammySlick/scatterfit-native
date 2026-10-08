package com.scatterbrain.scatterfit.ui.MetricPages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.scatterbrain.scatterfit.ui.MetricSource
import com.scatterbrain.scatterfit.ui.StepsMetricSourceStub
import com.scatterbrain.scatterfit.core.PaceMode
import com.scatterbrain.scatterfit.core.dailyPaceScore
import com.scatterbrain.scatterfit.core.scoreToColorArgb
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.ui.MetricChartType
import com.scatterbrain.scatterfit.ui.MetricPageScreen
import com.scatterbrain.scatterfit.ui.theme.JudgementGood
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import java.util.Calendar
import java.util.Locale

@Composable
fun StepsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    metricSource: MetricSource = remember { StepsMetricSourceStub() },
    todayKey: String = remember { todayKey() },
    dailyTarget: Int = 10_000
) {
    val latest = remember(metricSource) { metricSource.latest() }
    val currentSteps = (latest?.value ?: 8420.0).toInt()

    val cal = remember { Calendar.getInstance() }
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val elapsedDay = ((hour * 60.0 + minute) / 1440.0).coerceIn(0.01, 1.0)

    val paceScore = remember(currentSteps, dailyTarget, elapsedDay) {
        dailyPaceScore(
            value = currentSteps.toDouble(),
            target = dailyTarget.toDouble(),
            elapsedDay = elapsedDay,
            neutralBelow = 1.0,
            mode = PaceMode.REACH
        )
    }

    val (paceLabel, paceColor) = when {
        paceScore == null -> Pair("—", TextMuted)
        paceScore >= 100.0 -> Pair("On pace", Color(scoreToColorArgb(paceScore)))
        paceScore >= 80.0 -> Pair("Behind pace", Color(scoreToColorArgb(paceScore)))
        else -> Pair("Well behind", Color(scoreToColorArgb(paceScore)))
    }

    val insight = when (paceLabel) {
        "On pace" -> "On pace for %,d daily step target.".format(Locale.US, dailyTarget)
        "Behind pace" -> "Behind pace: %,d steps remaining to reach daily target.".format(Locale.US, (dailyTarget - currentSteps).coerceAtLeast(0))
        "Well behind" -> "Well behind daily pace — pick up steps this afternoon."
        else -> "Log steps today towards your %,d daily target.".format(Locale.US, dailyTarget)
    }

    MetricPageScreen(
        title = "Steps",
        heroValue = "%,d".format(Locale.US, currentSteps),
        heroUnit = "steps",
        heroScore = latest?.score ?: 84.0,
        trendText = "+620",
        trendColor = JudgementGood,
        verdictLabel = paceLabel,
        verdictTone = paceColor,
        insightSentence = insight,
        chartType = MetricChartType.BAR,
        metricSource = metricSource,
        anchorDayKey = todayKey,
        today = todayKey,
        onBackClick = onBackClick,
        formatValue = { v -> if (v != null) "%,d".format(Locale.US, v.toInt()) else "—" },
        modifier = modifier
    )
}
