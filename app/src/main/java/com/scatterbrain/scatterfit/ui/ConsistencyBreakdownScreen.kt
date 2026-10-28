package com.scatterbrain.scatterfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ConsistencyBreakdownScreen(
    momentumSource: MomentumSource,
    dayKey: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    onMetricClick: (MetricId) -> Unit = {},
    initialScrollPosition: Int = 0,
    onScrollPositionChanged: (Int) -> Unit = {},
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        if (initialScrollPosition > 0) {
            snapshotFlow { scrollState.maxValue }.first { it >= initialScrollPosition }
            scrollState.scrollTo(initialScrollPosition)
        }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { scrollState.value }
            .filter { scrollState.maxValue > 0 }
            .collect { onScrollPositionChanged(it) }
    }

    val handleBack = {
        onScrollPositionChanged(0)
        onBackClick()
    }

    BackHandler(onBack = handleBack)

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

    val pillText = if (momentumScoreRaw == null) "No data yet" else "$momentumScoreInt ${mBand.label}"
    val pillColor = if (momentumScoreRaw == null) TextMuted else mBand.color

    // Accordion state with seed guard: expands the first card once data lands, unless user already toggled
    var userToggledCards by rememberSaveable { mutableStateOf(false) }
    var expandedSections by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(summaries) {
        if (!userToggledCards && expandedSections.isEmpty()) {
            summaries.firstOrNull()?.let { expandedSections = setOf(it.name) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. TOP BAR: reuse the metric-page chrome pattern exactly
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = handleBack,
                modifier = Modifier.size(36.dp)
            ) {
                ChevronLeftIcon(
                    modifier = Modifier.size(16.dp),
                    tint = TextForeground
                )
            }

            Column {
                Text(
                    text = "Momentum — Breakdown",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextForeground
                )
                val today = remember { com.scatterbrain.scatterfit.core.todayKey() }
                val dayLabel = remember(dayKey, today) {
                    if (dayKey == today) {
                        "Today"
                    } else {
                        runCatching {
                            val date = LocalDate.parse(dayKey)
                            date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.US))
                        }.getOrDefault(dayKey)
                    }
                }
                Text(
                    text = dayLabel,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }

        // 2. HEADER INFO BLOCK: pill (score number + band name) and delta sentence grouped in AppCardSurface card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AppCardSurface),
            border = BorderStroke(1.dp, BorderDefault)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .border(BorderStroke(1.dp, pillColor), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = pillText,
                        color = pillColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                if (delta != null) {
                    DeltaPointsBlock(
                        delta = delta,
                        comparisonText = "this week vs yesterday"
                    )
                }
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
                            val cardId = summary.name
                            val isExpanded = expandedSections.contains(cardId)
                            ConsistencyRow(
                                summary = summary,
                                isExpanded = isExpanded,
                                onToggleExpand = {
                                    userToggledCards = true
                                    expandedSections = if (isExpanded) {
                                        expandedSections - cardId
                                    } else {
                                        expandedSections + cardId
                                    }
                                },
                                onMetricClick = onMetricClick
                            )
                        }
                    }
                }

                // Metrics with section == null (unlogged) render after ALL sections, with NO header
                val unloggedSummaries = summaries.filter { it.section == null || it.unlogged }
                unloggedSummaries.forEach { summary ->
                    val cardId = summary.name
                    val isExpanded = expandedSections.contains(cardId)
                    ConsistencyRow(
                        summary = summary,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            userToggledCards = true
                            expandedSections = if (isExpanded) {
                                expandedSections - cardId
                            } else {
                                expandedSections + cardId
                            }
                        },
                        onMetricClick = onMetricClick
                    )
                }
            }
        }
    }
}

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

/**
 * Metric row matching Sam's mockup geometry:
 * AppCardSurface, 14dp padding, 12dp radius, NO border.
 * Accordion:
 * - COLLAPSED: Tier 1 only (title + chevron, squares, rightEdge).
 * - EXPANDED: Tier 1 (title + rotated chevron, squares), Tier 2 (todayLine), Tier 3 (sentence + rightEdge).
 * - Row body tap toggles expand/collapse; chevron tap opens metric page when summary.id != null.
 */
@Composable
private fun ConsistencyRow(
    summary: ConsistencySummary,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
    onMetricClick: (MetricId) -> Unit = {},
) {
    val metricId = summary.id?.toMetricId()
    val isMetricClickable = metricId != null

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 225),
        label = "chevronRotation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppCardSurface),
        border = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(durationMillis = 225))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Tier 1: title + chevron (left), squares row (middle), rightEdge (right when collapsed)
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
                            .rotate(chevronRotation)
                            .then(
                                if (isMetricClickable) {
                                    Modifier.clickable { onMetricClick(metricId) }
                                } else {
                                    Modifier
                                }
                            ),
                        tint = if (isMetricClickable) TextMuted else TextMuted.copy(alpha = 0.3f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Squares (7 calendar-week squares, Mon..Sun)
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

                    // When collapsed, rightEdge sits on the right of Tier 1
                    if (!isExpanded) {
                        Text(
                            text = summary.rightEdge,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextForeground,
                            maxLines = 1,
                            textAlign = TextAlign.End
                        )
                    }
                }
            }

            if (isExpanded) {
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
