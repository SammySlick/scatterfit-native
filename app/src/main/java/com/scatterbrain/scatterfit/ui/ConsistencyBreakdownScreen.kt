package com.scatterbrain.scatterfit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.core.dayBefore
import com.scatterbrain.scatterfit.core.lastNDayKeys
import com.scatterbrain.scatterfit.ui.theme.AccentTeal
import com.scatterbrain.scatterfit.ui.theme.AppBackground
import com.scatterbrain.scatterfit.ui.theme.AppCardSurface
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme
import com.scatterbrain.scatterfit.ui.theme.StateLocked
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
) {
    val scrollState = rememberScrollState()

    // Current momentum score & band
    val momentumScoreRaw = momentumSource.momentumForDay(dayKey)
    val momentumScoreInt = momentumScoreRaw?.let { kotlin.math.round(it).toInt() }
    val mBand = momentumBand(momentumScoreRaw ?: 0.0)

    val yesterdayKey = remember(dayKey) { dayBefore(dayKey) }
    val yesterdayScore = momentumSource.momentumForDay(yesterdayKey)?.let { kotlin.math.round(it).toInt() }
    val delta = if (momentumScoreInt != null && yesterdayScore != null) momentumScoreInt - yesterdayScore else null

    // 7-day window ending at dayKey
    val sevenDays = remember(dayKey) { lastNDayKeys(7, dayKey) }

    // Consistency series from source
    val seriesList = remember(momentumSource, dayKey) {
        momentumSource.consistencySeries(dayKey)
    }

    // Gate empty state on whether ANY series has ANY non-null score in the 7-day window
    val hasAnyData = remember(seriesList, sevenDays) {
        seriesList.any { series ->
            val dayMap = series.days.associate { it.day to it.score }
            sevenDays.any { dayMap[it] != null }
        }
    }

    // Sort weakest-first: series with zero non-null scores sort to the bottom;
    // measured series sort by average ascending; ties broken by latest.
    val sortedSeries = remember(seriesList, sevenDays) {
        seriesList.sortedWith(
            compareBy<com.scatterbrain.scatterfit.core.GoalSeries> { series ->
                val dayMap = series.days.associate { it.day to it.score }
                val scores = sevenDays.mapNotNull { dayMap[it] }
                if (scores.isEmpty()) 1 else 0
            }.thenBy { series ->
                val dayMap = series.days.associate { it.day to it.score }
                val scores = sevenDays.mapNotNull { dayMap[it] }
                if (scores.isNotEmpty()) scores.average() else Double.MAX_VALUE
            }.thenBy { it.latest ?: 0.0 }
        )
    }

    val titleText = if (momentumScoreRaw == null) "No data yet — Breakdown" else "${mBand.label} — Breakdown"
    val pillLabel = if (momentumScoreRaw == null) "No data yet" else mBand.label
    val pillColor = if (momentumScoreRaw == null) TextMuted else mBand.color

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(16.dp)
            .verticalScroll(scrollState),
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
                text = titleText,
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

        // 3. METRIC ROWS + Special rows, or Empty state
        if (!hasAnyData) {
            Text(
                text = "Log something to see consistency.",
                fontSize = 13.sp,
                color = TextMuted
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Metric series rows
                sortedSeries.forEach { series ->
                    val dayMap = remember(series) { series.days.associate { it.day to it.score } }
                    val squares = sevenDays.map { k -> dayMap[k]?.let { momentumBand(it).color } }
                    val nonNullCount = sevenDays.count { dayMap[it] != null }

                    ConsistencyRow(
                        name = series.name,
                        squares = squares,
                        countText = "$nonNullCount/7"
                    )
                }

                // 4. SLEEP ROW (special) — wired through ScoringSettings.sleepTargetHours * 60
                val sleepTargetMinutes = momentumSource.sleepTargetHours * 60.0
                val sleepSquares = sevenDays.map { k ->
                    momentumSource.sleepMinutesForDay(k)?.let { mins ->
                        if (mins >= sleepTargetMinutes) StateLocked else StateSlip
                    }
                }
                val sleepNightsMet = sevenDays.count { (momentumSource.sleepMinutesForDay(it) ?: 0.0) >= sleepTargetMinutes }

                ConsistencyRow(
                    name = "Sleep",
                    squares = sleepSquares,
                    countText = "$sleepNightsMet/7"
                )

                // 5. ZONE ROW (special) — filled when z2 >= 20 min/day
                val zoneSquares = sevenDays.map { k ->
                    momentumSource.zone2MinutesForDay(k)?.let { z2 ->
                        if (z2 >= 20.0) StateLocked else StateSlip
                    }
                }
                val z2DaysMet = sevenDays.count { (momentumSource.zone2MinutesForDay(it) ?: 0.0) >= 20.0 }

                ConsistencyRow(
                    name = "Zone 2 Minutes",
                    squares = zoneSquares,
                    countText = "$z2DaysMet/7"
                )
            }
        }
    }
}

/**
 * Consolidated consistency row: Card frame with 1dp AccentTeal border (12dp radius),
 * left metric title, centre 7-day mini-grid of 14dp squares, and right consistency count.
 */
@Composable
private fun ConsistencyRow(
    name: String,
    squares: List<Color?>,
    countText: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppCardSurface),
        border = BorderStroke(1.dp, AccentTeal)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // LEFT: metric name
            Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextForeground,
                modifier = Modifier.weight(1f)
            )

            // CENTRE: 7-DAY MINI-GRID
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                squares.forEach { color ->
                    if (color != null) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(color.copy(alpha = 0.9f))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(3.dp))
                        )
                    }
                }
            }

            // RIGHT: consistency count (e.g. "5/7")
            Box(
                modifier = Modifier.weight(0.4f),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = countText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
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
