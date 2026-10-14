package com.scatterbrain.scatterfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.scatterbrain.scatterfit.core.MetricId
import com.scatterbrain.scatterfit.core.MetricRegistry
import com.scatterbrain.scatterfit.core.metricTargetContext
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.data.DailyRepository
import com.scatterbrain.scatterfit.data.StoreDailySource
import com.scatterbrain.scatterfit.ui.theme.BrandPrimaryLime
import com.scatterbrain.scatterfit.ui.theme.MutedFill
import com.scatterbrain.scatterfit.ui.theme.OnPrimaryText
import com.scatterbrain.scatterfit.ui.theme.StateLocked
import com.scatterbrain.scatterfit.ui.theme.StateSlip
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * GENERIC METRIC SCREEN — every metric page renders through this, configured
 * by the registry (core/MetricRegistry.kt). No per-metric screens any more;
 * adding a page = adding a MetricDef entry.
 *
 * Layout per Sam's decisions 2026-10-10 00:10:
 *   header -> chip row (metric-to-metric nav) -> hero -> [special section]
 *   -> chart -> list. Special section slot is above the chart (matches web).
 */

/** Honest empty: every day is null — the page renders real "—" everywhere. */
class EmptyMetricSource : MetricSource {
    override fun series(days: List<String>): List<MetricPoint> = days.map { MetricPoint(it, null) }
    override fun latest(): MetricPoint? = null
}

@Composable
fun MetricScreen(
    initialId: MetricId,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    todayKey: String = remember { todayKey() },
) {
    var currentId by remember { mutableStateOf(initialId) }
    val def = remember(currentId) { MetricRegistry.byId(currentId) }
    val repoDaily by DailyRepository.daily.collectAsState()
    val source: MetricSource = remember(currentId, repoDaily) {
        repoDaily?.let { ad ->
            StoreDailySource.fromDaily(ad, def.valueFrom)
        } ?: EmptyMetricSource()
    }

    // Hero: newest value in the last HISTORY_DAYS window.
    val latest = remember(source) { source.latest() }
    val heroText = remember(def, latest) {
        if (currentId == MetricId.SLEEP) {
            latest?.value?.let { v ->
                val mins = (v * 60.0).roundToInt()
                "${mins / 60}h ${mins % 60}m"
            } ?: "—"
        } else MetricRegistry.format(def, latest?.value)
    }

    // Verdict/comparison from the ported targets engine, on the same demo
    // decision layer the Today facade uses until the goals editor exists.
    val context = remember(def, source) {
        val zone = ZoneId.systemDefault()
        val nowMs = System.currentTimeMillis()
        val days = (0 until 7).map { com.scatterbrain.scatterfit.data.Daily.daysAgoKey(it, zone, nowMs) }
        val mean7 = source.series(days).mapNotNull { it.value }.takeIf { it.isNotEmpty() }?.average()
        metricTargetContext(currentId, mean7, com.scatterbrain.scatterfit.core.ScoringSettings(activeGoal = com.scatterbrain.scatterfit.core.GoalPreset.FAT_LOSS, kcalTarget = 2270))
    }

    MetricPageScreen(
        title = def.label,
        heroValue = heroText,
        heroUnit = def.unit.ifEmpty { when (currentId) { MetricId.SLEEP -> "last night"; MetricId.STEPS -> "yesterday"; else -> "" } },
        heroScore = latest?.score,
        verdictLabel = context.verdict.takeIf { it != "Not enough data" },
        verdictTone = verdictTone(context.verdict),
        insightSentence = buildString {
            append(context.comparison)
            context.action?.let { append("  "); append(it) }
        },
        chartType = if (def.chart) MetricChartType.LINE else MetricChartType.BAR,
        metricSource = source,
        anchorDayKey = todayKey,
        today = todayKey,
        onBackClick = onBackClick,
        formatValue = { v ->
            when {
                v == null -> "—"
                currentId == MetricId.SLEEP -> {
                    val mins = (v * 60.0).roundToInt()
                    "${mins / 60}h ${mins % 60}m"
                }
                else -> MetricRegistry.format(def, v)
            }
        },
        topSlot = { MetricChipRow(currentId, onPick = { currentId = it }) },
        modifier = modifier,
    )
}

/** Horizontal chip row — Sam's decision (a): metric-to-metric navigation. */
@Composable
private fun MetricChipRow(active: MetricId, onPick: (MetricId) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MetricRegistry.TABS.forEach { def ->
            val selected = def.id == active
            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .background(if (selected) BrandPrimaryLime else MutedFill, RoundedCornerShape(50))
                    .clickable { onPick(def.id) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = def.label,
                    color = if (selected) OnPrimaryText else TextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun MetricScreenPreview() {
    com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme {
        MetricScreen(
            initialId = MetricId.STEPS,
            onBackClick = {}
        )
    }
}

/** Verdict pill tone: green only for "On target"; everything else orange.
 *  Red is reserved for genuinely bad — a slightly-off week is a slip, not a fail.
 *  (Mirrors the tolerance-band grammar on the breakdown screen.) */
fun verdictTone(verdict: String): Color = when (verdict) {
    "On target" -> StateLocked
    else -> StateSlip
}
