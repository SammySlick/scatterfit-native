package com.scatterbrain.scatterfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.core.MetricDef
import com.scatterbrain.scatterfit.core.MetricId
import com.scatterbrain.scatterfit.core.MetricRegistry
import com.scatterbrain.scatterfit.core.ScoringSettings
import com.scatterbrain.scatterfit.core.lastNDayKeys
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.data.DailyRepository
import com.scatterbrain.scatterfit.data.SettingsStorage
import com.scatterbrain.scatterfit.data.StoreDailySource
import com.scatterbrain.scatterfit.ui.theme.DomainBody
import com.scatterbrain.scatterfit.ui.theme.DomainDrink
import com.scatterbrain.scatterfit.ui.theme.DomainFuel
import com.scatterbrain.scatterfit.ui.theme.DomainMove
import com.scatterbrain.scatterfit.ui.theme.DomainSleep
import com.scatterbrain.scatterfit.ui.theme.DomainTrain
import com.scatterbrain.scatterfit.ui.theme.MutedFill
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import kotlin.math.roundToInt
import java.util.Locale

/**
 * METRIX — every metric as its own chart, in one scroll.
 *
 * Sam, 2026-11-26: "I just want a list of the metric charts", with "numbers on
 * the X and Y of the graphs" and a way to tap a point and read it. Ported from
 * the web app's Trends page (routes/odyssey.trends.tsx), which is the version he
 * approved: per-metric chart, numbered axes, dashed target line, tap to read a day.
 *
 * The page is deliberately a LIST and not a pager. The pager version could only
 * show one metric at a time, so comparing steps against sleep meant navigating
 * and remembering — the exact thing a chart is supposed to save you from.
 */
@Composable
fun MetrixScreen(
    onOpenProgressReport: () -> Unit,
    onOpenConsistency: () -> Unit,
    modifier: Modifier = Modifier,
    today: String = remember { todayKey() },
    settings: ScoringSettings? = null,
) {
    val context = LocalContext.current
    val effectiveSettings = remember(settings, context) {
        settings ?: SettingsStorage(context).loadSettings()
    }
    val repoDaily by DailyRepository.daily.collectAsState()
    var period by remember { mutableStateOf(MetricPeriod.PERIOD_30D) }
    var openMetric by remember { mutableStateOf<MetricId?>(null) }
    val dayKeys = remember(period, today) { lastNDayKeys(period.days, endKey = today) }

    // One metric page, opened from its chart. Backing out returns to the list,
    // which is where you were — not to My Day.
    val opened = openMetric
    if (opened != null) {
        MetricScreen(initialId = opened, onBackClick = { openMetric = null }, modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(com.scatterbrain.scatterfit.ui.theme.AppBackground),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Metrix",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground
                )
                Text(
                    text = "Every metric, one scroll. Tap a chart for a day's reading.",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricPeriod.entries.forEach { option ->
                    val isSelected = option == period
                    Text(
                        text = option.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) TextForeground else TextMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isSelected) MutedFill else Color.Transparent)
                            .clickable { period = option }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        items(MetricRegistry.TABS, key = { it.id.name }) { def ->
            val points = remember(def, repoDaily, dayKeys) {
                repoDaily?.let { daily ->
                    StoreDailySource.fromDaily(daily, def.valueFrom).series(dayKeys)
                } ?: dayKeys.map { MetricPoint(it, null) }
            }
            val latest = points.lastOrNull { it.value != null }?.value

            MetricChartCard(
                points = points,
                chartType = if (def.chart) MetricChartType.LINE else MetricChartType.BAR,
                formatValue = { value -> formatMetricValue(def, value) },
                today = today,
                targetValue = chartTargetFor(def.id, effectiveSettings),
                chartHeight = 118.dp,
                seriesColor = metricDomainColor(def.id),
                header = { MetricChartHeader(def = def, latest = latest, onOpen = { openMetric = def.id }) },
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FooterLink("Progress report", onOpenProgressReport)
                FooterLink("Consistency breakdown", onOpenConsistency)
            }
        }
    }
}

@Composable
private fun MetricChartHeader(def: MetricDef, latest: Double?, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = def.label.uppercase(Locale.US),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = metricDomainColor(def.id)
            )
            Text(
                text = formatMetricValue(def, latest),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (latest == null) TextMuted else TextForeground
            )
        }
        Text(text = "\u203A", fontSize = 18.sp, color = TextMuted)
    }
}

@Composable
private fun FooterLink(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = TextMuted,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp)
    )
}

/**
 * A metric's value as text, formatted once so the list and the full page agree.
 * Sleep is hours everywhere in the data model and "7h 30m" everywhere a human
 * reads it — a chart axis in decimal hours is fine, a headline is not.
 */
internal fun formatMetricValue(def: MetricDef, value: Double?): String {
    if (value == null) return "\u2014"
    if (def.id == MetricId.SLEEP) {
        val mins = (value * 60.0).roundToInt()
        return "${mins / 60}h ${mins % 60}m"
    }
    return String.format(
        Locale.US,
        "%.${def.decimals}f%s",
        value,
        if (def.unit.isEmpty()) "" else " ${def.unit}"
    )
}

/**
 * The dashed target line for a metric, in the metric's own daily units.
 *
 * Alcohol and training targets are stored per WEEK, so they are divided by
 * seven — a weekly target drawn straight onto a daily axis would sit seven times
 * too high and make every day look compliant.
 */
internal fun chartTargetFor(id: MetricId, settings: ScoringSettings): Double? = when (id) {
    MetricId.STEPS -> settings.stepTarget.toDouble()
    MetricId.CALORIES_EATEN -> settings.kcalTarget.toDouble()
    MetricId.PROTEIN -> settings.proteinTarget.toDouble()
    MetricId.SLEEP -> settings.sleepTargetHours
    MetricId.WEIGHT -> settings.weightTarget.takeIf { it > 0.0 }
    MetricId.ALCOHOL_UNITS -> settings.unitsPerWeek / 7.0
    MetricId.TRAINING_MINUTES ->
        (settings.z2Target + settings.z3Target + settings.z4Target + settings.z5Target) / 7.0
    MetricId.RESTING_HR, MetricId.CALORIES_BURNED -> null
}

/** The domain hue for a metric, so a chart keeps the colour of its subject. */
internal fun metricDomainColor(id: MetricId): Color = when (id) {
    MetricId.STEPS -> DomainMove
    MetricId.SLEEP -> DomainSleep
    MetricId.WEIGHT -> DomainBody
    MetricId.CALORIES_EATEN, MetricId.PROTEIN, MetricId.CALORIES_BURNED -> DomainFuel
    MetricId.ALCOHOL_UNITS -> DomainDrink
    MetricId.TRAINING_MINUTES -> DomainTrain
    MetricId.RESTING_HR -> DomainTrain
}
