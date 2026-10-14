package com.scatterbrain.scatterfit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.core.ConsistencySection
import com.scatterbrain.scatterfit.core.ConsistencySummary
import com.scatterbrain.scatterfit.core.GoalId
import com.scatterbrain.scatterfit.core.MetricId
import com.scatterbrain.scatterfit.core.SquareBand
import com.scatterbrain.scatterfit.core.dayBefore
import com.scatterbrain.scatterfit.ui.theme.AppBackground
import com.scatterbrain.scatterfit.ui.theme.AppCardSurface
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme
import com.scatterbrain.scatterfit.ui.theme.StateBad
import com.scatterbrain.scatterfit.ui.theme.StateLocked
import com.scatterbrain.scatterfit.ui.theme.StateRhythm
import com.scatterbrain.scatterfit.ui.theme.StateSlip
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import com.scatterbrain.scatterfit.ui.theme.momentumBand

@Composable
fun ConsistencyBreakdownScreen(
    momentumSource: MomentumSource,
    dayKey: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMetricClick: (MetricId) -> Unit = {},
) {
    val scrollState = rememberScrollState()

    // Current momentum score & band
    val momentumScoreRaw = momentumSource.momentumForDay(dayKey)
    val momentumScoreInt = momentumScoreRaw?.let { kotlin.math.round(it).toInt() }
    val mBand = momentumBand(momentumScoreRaw ?: 0.0)

    val yesterdayKey = remember(dayKey) { dayBefore(dayKey) }
    val yesterdayScore = momentumSource.momentumForDay(yesterdayKey)?.let { kotlin.math.round(it).toInt() }
    val delta = if (momentumScoreInt != null && yesterdayScore != null) momentumScoreInt - yesterdayScore else null

    // AAG consistency summaries from the momentum source
    val summaries = remember(momentumSource, dayKey) {
        momentumSource.consistencySummaries(dayKey)
    }

    val isAllUnlogged = summaries.isEmpty() || summaries.all { it.section == null || it.unlogged }

    val pillLabel = if (momentumScoreRaw == null) "No data yet" else mBand.label
    val pillColor = if (momentumScoreRaw == null) TextMuted else mBand.color

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(16.dp)
            .verticalScroll(scrollState)
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. TOP BAR: reuse the metric-page chrome pattern exactly
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(36.dp)
            ) {
                ChevronLeftIcon(
                    modifier = Modifier.size(16.dp),
                    tint = TextForeground
                )
            }

            Text(
                text = "Momentum — Breakdown",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextForeground
            )
        }

        // 2. HEADER BLOCK: the band verdict pill + DeltaPointsBlock
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .border(BorderStroke(1.dp, pillColor), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = pillLabel,
                    color = pillColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            if (delta != null) {
                DeltaPointsBlock(delta = delta)
            }
        }

        // 3. BODY: Sections in fixed order, followed by unlogged metrics
        if (isAllUnlogged) {
            Text(
                text = "Log something to see consistency.",
                fontSize = 13.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val sections = listOf(
                    Triple(ConsistencySection.NEED_WORK, "NEED WORK", StateSlip),
                    Triple(ConsistencySection.GOING_STEADY, "GOING STEADY", StateRhythm),
                    Triple(ConsistencySection.GOOD_WORK, "GOOD WORK", StateLocked),
                )

                sections.forEach { (sec, label, underlineColor) ->
                    val sectionSummaries = summaries.filter { it.section == sec }
                    if (sectionSummaries.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = label,
                                color = TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(underlineColor)
                            )
                        }
                        sectionSummaries.forEach { summary ->
                            ConsistencyRow(summary = summary, onMetricClick = onMetricClick)
                        }
                    }
                }

                // Metrics with section == null (unlogged) render after ALL sections, with NO header
                val unloggedSummaries = summaries.filter { it.section == null || it.unlogged }
                unloggedSummaries.forEach { summary ->
                    ConsistencyRow(summary = summary, onMetricClick = onMetricClick)
                }
            }
        }
    }
}

/**
 * Metric row matching Sam's mockup geometry:
 * AppCardSurface, 14dp padding, 12dp radius, NO border.
 * - Tier 1: title + 12dp chevron on left, 7 squares on right (14dp, 3dp gap, RoundedCornerShape(3.dp)).
 *   Square color comes from sq.band: GREEN -> StateLocked, ORANGE -> StateSlip, RED -> StateBad, GREY -> outline.
 * - Tier 2: todayLine (11sp, TextMuted, right-aligned).
 * - Tier 3: sentence (14sp, TextForeground) on left, rightEdge (13sp Bold, TextForeground) right-aligned on same line.
 * - Vertical: 8dp between tiers inside each card.
 */
private fun GoalId.toMetricId(): MetricId? = when (this) {
    GoalId.STEPS -> MetricId.STEPS
    GoalId.CALORIES -> MetricId.CALORIES_EATEN
    GoalId.PROTEIN -> MetricId.PROTEIN
    GoalId.SLEEP -> MetricId.SLEEP
    GoalId.EXERCISE -> MetricId.TRAINING_MINUTES
    GoalId.WEIGHT, GoalId.BODY_FAT -> MetricId.WEIGHT
    GoalId.ALCOHOL, GoalId.UNITS -> MetricId.ALCOHOL_UNITS
    GoalId.RHR_TREND -> MetricId.RESTING_HR
    GoalId.Z4PLUS -> MetricId.TRAINING_MINUTES
}

@Composable
private fun ConsistencyRow(
    summary: ConsistencySummary,
    modifier: Modifier = Modifier,
    onMetricClick: (MetricId) -> Unit = {},
) {
    val metricId = summary.id?.toMetricId()
    val isClickable = metricId != null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (metricId != null) Modifier.clickable { onMetricClick(metricId) } else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppCardSurface),
        border = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Tier 1: title + 12dp chevron (left), squares row (right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = summary.name.uppercase(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ChevronRightIcon(
                        modifier = Modifier
                            .size(12.dp)
                            .then(if (metricId != null) Modifier.clickable { onMetricClick(metricId) } else Modifier),
                        tint = if (isClickable) TextMuted else TextMuted.copy(alpha = 0.3f)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    summary.squares.forEach { sq ->
                        when (sq.band) {
                            SquareBand.GREEN -> Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(StateLocked)
                            )
                            SquareBand.ORANGE -> Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(StateSlip)
                            )
                            SquareBand.RED -> Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(StateBad)
                            )
                            SquareBand.GREY -> Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(3.dp))
                            )
                        }
                    }
                }
            }

            // Tier 2: todayLine (under squares, right-aligned)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = summary.todayLine,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextMuted,
                    textAlign = TextAlign.End,
                    maxLines = 1
                )
            }

            // Tier 3: sentence (left) and rightEdge (right-aligned) on the same line
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = summary.sentence,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextMuted,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = summary.rightEdge,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ConsistencyBreakdownScreenPreview() {
    ScatterFitTheme {
        ConsistencyBreakdownScreen(
            momentumSource = TodayFacadeMomentumSource(),
            dayKey = com.scatterbrain.scatterfit.core.todayKey(),
            onBackClick = {}
        )
    }
}
