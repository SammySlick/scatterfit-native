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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.core.dayBefore
import com.scatterbrain.scatterfit.core.lastNDayKeys
import com.scatterbrain.scatterfit.ui.theme.AppBackground
import com.scatterbrain.scatterfit.ui.theme.AppCardSurface
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
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

    // Sort weakest-first: average of non-null scores ascending; ties by latest
    val sortedSeries = remember(seriesList, sevenDays) {
        seriesList.sortedWith(
            compareBy<com.scatterbrain.scatterfit.core.GoalSeries> { series ->
                val dayMap = series.days.associate { it.day to it.score }
                val scores = sevenDays.mapNotNull { dayMap[it] }
                if (scores.isNotEmpty()) scores.average() else 0.0
            }.thenBy { it.latest ?: 0.0 }
        )
    }

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
                text = "${mBand.label} — Breakdown",
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
                    .border(BorderStroke(1.dp, mBand.color), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = mBand.label,
                    color = mBand.color,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            if (delta != null) {
                DeltaPointsBlock(delta = delta)
            }
        }

        // 3. METRIC ROWS + Special rows
        if (seriesList.isEmpty()) {
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
                sortedSeries.forEach { series ->
                    val dayMap = remember(series) { series.days.associate { it.day to it.score } }
                    val nonNullCount = sevenDays.count { dayMap[it] != null }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AppCardSurface),
                        border = BorderStroke(1.dp, BorderDefault)
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
                                text = series.name,
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
                                sevenDays.forEach { k ->
                                    val score = dayMap[k]
                                    if (score != null) {
                                        val color = momentumBand(score).color
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
                                    text = "$nonNullCount/7",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }

                // 4. SLEEP ROW (special)
                val sleepTargetMinutes = 480.0 // 8 hours
                val sleepNightsMet = sevenDays.count { (momentumSource.sleepMinutesForDay(it) ?: 0.0) >= sleepTargetMinutes }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppCardSurface),
                    border = BorderStroke(1.dp, BorderDefault)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Sleep",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextForeground,
                            modifier = Modifier.weight(1f)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            sevenDays.forEach { k ->
                                val mins = momentumSource.sleepMinutesForDay(k)
                                if (mins != null) {
                                    val color = if (mins >= sleepTargetMinutes) StateLocked else StateSlip
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

                        Box(
                            modifier = Modifier.weight(0.4f),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = "$sleepNightsMet/7",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )
                        }
                    }
                }

                // 5. ZONE ROW (special)
                val z2DaysMet = sevenDays.count { (momentumSource.zone2MinutesForDay(it) ?: 0.0) >= 20.0 }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppCardSurface),
                    border = BorderStroke(1.dp, BorderDefault)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Zone 2 Minutes",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextForeground,
                            modifier = Modifier.weight(1f)
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            sevenDays.forEach { k ->
                                val z2 = momentumSource.zone2MinutesForDay(k)
                                if (z2 != null) {
                                    val color = if (z2 >= 20.0) StateLocked else StateSlip
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

                        Box(
                            modifier = Modifier.weight(0.4f),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Text(
                                text = "$z2DaysMet/7",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}
