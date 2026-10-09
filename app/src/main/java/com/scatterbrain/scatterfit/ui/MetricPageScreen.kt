package com.scatterbrain.scatterfit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.core.lastNDayKeys
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.ui.theme.AppCardSurface
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.BrandPrimaryLime
import com.scatterbrain.scatterfit.ui.theme.JudgementBad
import com.scatterbrain.scatterfit.ui.theme.JudgementGood
import com.scatterbrain.scatterfit.ui.theme.JudgementWarn
import com.scatterbrain.scatterfit.ui.theme.MutedFill
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted

enum class MetricChartType { BAR, LINE }

/**
 * Reusable Metric Detail Screen Template.
 */
@Composable
fun MetricPageScreen(
    title: String,
    heroValue: String,
    heroUnit: String,
    heroScore: Double? = null,
    trendText: String? = null,
    trendColor: Color = JudgementGood,
    verdictLabel: String? = null,
    verdictTone: Color? = null,
    insightSentence: String,
    chartType: MetricChartType = MetricChartType.BAR,
    metricSource: MetricSource,
    anchorDayKey: String = remember { todayKey() },
    today: String = remember { todayKey() },
    /** Optional slot between header and hero — metric-to-metric nav, special sections. */
    topSlot: (@Composable () -> Unit)? = null,
    onBackClick: () -> Unit,
    formatValue: (Double?) -> String = { it?.toString() ?: "—" },
    showList: Boolean = true,
    modifier: Modifier = Modifier
) {
    var selectedPeriod by remember { mutableStateOf(MetricPeriod.PERIOD_30D) }

    // Fetch day keys strictly from DayKeys helpers based on selected period
    val dayKeys = remember(selectedPeriod, anchorDayKey) {
        lastNDayKeys(selectedPeriod.days, endKey = anchorDayKey)
    }

    // Load series from MetricSource contract
    val points = remember(dayKeys, metricSource) {
        metricSource.series(dayKeys)
    }

    // Verdict pill: auto-compute from heroScore if not explicitly provided
    val (pillText, pillColor) = remember(heroScore, verdictLabel, verdictTone) {
        if (verdictLabel != null && verdictTone != null) {
            Pair(verdictLabel, verdictTone)
        } else if (heroScore != null) {
            when {
                heroScore < 40.0 -> Pair("Low", JudgementBad)
                heroScore < 70.0 -> Pair("Steady", JudgementWarn)
                else -> Pair("High", JudgementGood)
            }
        } else {
            Pair("No data", TextMuted)
        }
    }

    val valueColor = if (heroScore != null) scoreToJudgementColor(heroScore) else TextForeground
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header row: back chevron (36dp) + metric title (16sp semibold TextForeground)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.size(36.dp)
            ) {
                Text(
                    text = "‹",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground
                )
            }

            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextForeground
            )
        }

        // 1b. Optional top slot (chip row / special section, Sam 2026-10-10: special sections sit ABOVE the chart)
        topSlot?.invoke()

        // 2. Hero block: value + unit + verdict pill + delta context line
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = AppCardSurface),
            border = BorderStroke(1.dp, BorderDefault)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = heroValue,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = valueColor
                        )
                        Text(
                            text = heroUnit,
                            fontSize = 11.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    // Verdict pill
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (pillText == "No data") MutedFill else pillColor.copy(alpha = 0.16f))
                            .border(
                                BorderStroke(1.dp, if (pillText == "No data") BorderDefault else pillColor.copy(alpha = 0.40f)),
                                CircleShape
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = pillText,
                            color = pillColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Trend line merged: "−4.4 from last week"
                if (!trendText.isNullOrBlank()) {
                    val deltaFormatted = trendText
                        .replace("↘", "")
                        .replace("↗", "")
                        .replace("-", "−")
                        .trim()
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = trendColor, fontWeight = FontWeight.SemiBold)) {
                                append(deltaFormatted)
                            }
                            withStyle(SpanStyle(color = TextMuted)) {
                                append(" from last week")
                            }
                        },
                        style = TextStyle(
                            fontSize = 10.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 3. Period selector chips: 7D / 30D / 90D
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricPeriod.entries.forEach { period ->
                val isSelected = period == selectedPeriod
                val chipBg = if (isSelected) BrandPrimaryLime.copy(alpha = 0.16f) else MutedFill
                val chipBorder = if (isSelected) BrandPrimaryLime else BorderDefault
                val chipText = if (isSelected) BrandPrimaryLime else TextForeground

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(chipBg)
                        .border(BorderStroke(1.dp, chipBorder), RoundedCornerShape(16.dp))
                        .clickable { selectedPeriod = period }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = period.label,
                        color = chipText,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        // 4. Chart area (~180dp tall)
        MetricChartCard(
            points = points,
            chartType = chartType,
            formatValue = formatValue,
            today = today
        )

        // 5. Insight slot: 13sp foreground semibold call-to-action sentence
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = AppCardSurface),
            border = BorderStroke(1.dp, BorderDefault)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(BrandPrimaryLime)
                )
                Text(
                    text = insightSentence,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextForeground,
                    lineHeight = 18.sp
                )
            }
        }

        // 6. List slot: optional vertical list of the most recent 7 daily values
        if (showList && points.isNotEmpty()) {
            val recentPoints = remember(points) { points.takeLast(7).reversed() }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AppCardSurface),
                border = BorderStroke(1.dp, BorderDefault)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "RECENT DAYS",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    )

                    recentPoints.forEach { point ->
                        val dayTitle = formatDayKeyTitle(point.dayKey, today)
                        val dotColor = scoreToJudgementColor(point.score)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Text(
                                    text = dayTitle,
                                    fontSize = 13.sp,
                                    color = TextForeground,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Text(
                                text = formatValue(point.value),
                                fontSize = 13.sp,
                                color = TextForeground,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Metric Chart Component:
 * - ~180dp tall
 * - Y-axis: max/min reference lines (MutedFill 1dp)
 * - Bars: rounded 4dp tops, BrandPrimaryLime 60% alpha; selected bar full opacity + tooltip
 * - Line: 2dp BrandPrimaryLime; sparse null points rendered as gaps
 */
@Composable
private fun MetricChartCard(
    points: List<MetricPoint>,
    chartType: MetricChartType,
    formatValue: (Double?) -> String,
    today: String,
    modifier: Modifier = Modifier
) {
    var selectedIndex by remember(points) { mutableIntStateOf(-1) }

    val nonNullValues = points.mapNotNull { it.value }
    val maxVal = if (nonNullValues.isNotEmpty()) nonNullValues.maxOrNull() ?: 100.0 else 100.0
    val minVal = if (chartType == MetricChartType.LINE && nonNullValues.isNotEmpty()) {
        (nonNullValues.minOrNull() ?: 0.0) * 0.98
    } else 0.0

    val range = (maxVal - minVal).coerceAtLeast(1.0)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppCardSurface),
        border = BorderStroke(1.dp, BorderDefault)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Tooltip header on tap
            if (selectedIndex in points.indices) {
                val pt = points[selectedIndex]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDayKeyTitle(pt.dayKey, today),
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    Text(
                        text = formatValue(pt.value),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandPrimaryLime
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .pointerInput(points) {
                        detectTapGestures { offset ->
                            val n = points.size
                            if (n > 0) {
                                val colWidth = size.width / n
                                val index = (offset.x / colWidth).toInt().coerceIn(0, n - 1)
                                selectedIndex = index
                            }
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val n = points.size

                // Reference lines: max and min (MutedFill 1dp)
                drawLine(
                    color = MutedFill,
                    start = Offset(0f, 0f),
                    end = Offset(w, 0f),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = MutedFill,
                    start = Offset(0f, h),
                    end = Offset(w, h),
                    strokeWidth = 1.dp.toPx()
                )

                if (n == 0) return@Canvas

                if (chartType == MetricChartType.BAR) {
                    val slotWidth = w / n
                    val barWidth = (slotWidth * 0.70f).coerceIn(4f, 24f)

                    points.forEachIndexed { i, pt ->
                        val v = pt.value ?: 0.0
                        val barHeight = ((v - minVal) / range).toFloat().coerceIn(0f, 1f) * (h - 8.dp.toPx())
                        val x = i * slotWidth + (slotWidth - barWidth) / 2f
                        val y = h - barHeight
                        val isSelected = i == selectedIndex

                        val barColor = if (isSelected) BrandPrimaryLime else BrandPrimaryLime.copy(alpha = 0.60f)

                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight.coerceAtLeast(2.dp.toPx())),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                } else {
                    // LINE chart: sparse data -> gaps where value is null
                    val slotWidth = if (n > 1) w / (n - 1) else w

                    // Draw connecting segments where adjacent points are both non-null
                    for (i in 0 until n - 1) {
                        val p1 = points[i]
                        val p2 = points[i + 1]
                        if (p1.value != null && p2.value != null) {
                            val x1 = i * slotWidth
                            val y1 = h - (((p1.value - minVal) / range).toFloat().coerceIn(0f, 1f) * (h - 16.dp.toPx()) + 8.dp.toPx())
                            val x2 = (i + 1) * slotWidth
                            val y2 = h - (((p2.value - minVal) / range).toFloat().coerceIn(0f, 1f) * (h - 16.dp.toPx()) + 8.dp.toPx())

                            drawLine(
                                color = BrandPrimaryLime,
                                start = Offset(x1, y1),
                                end = Offset(x2, y2),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // Draw data dots
                    points.forEachIndexed { i, pt ->
                        if (pt.value != null) {
                            val x = if (n > 1) i * slotWidth else w / 2f
                            val y = h - (((pt.value - minVal) / range).toFloat().coerceIn(0f, 1f) * (h - 16.dp.toPx()) + 8.dp.toPx())
                            val isSelected = i == selectedIndex

                            drawCircle(
                                color = if (isSelected) TextForeground else BrandPrimaryLime,
                                radius = if (isSelected) 5.dp.toPx() else 3.dp.toPx(),
                                center = Offset(x, y)
                            )
                        }
                    }
                }
            }
        }
    }
}
