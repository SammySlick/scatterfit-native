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
import androidx.compose.ui.tooling.preview.Preview
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.scatterbrain.scatterfit.core.DayRows
import com.scatterbrain.scatterfit.core.MovingAverage
import com.scatterbrain.scatterfit.core.lastNDayKeys
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.ui.theme.AccentTeal
import com.scatterbrain.scatterfit.ui.theme.AppCardSurface
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.ChartBlue
import com.scatterbrain.scatterfit.ui.theme.JudgementBad
import com.scatterbrain.scatterfit.ui.theme.JudgementGood
import com.scatterbrain.scatterfit.ui.theme.JudgementWarn
import com.scatterbrain.scatterfit.ui.theme.MutedFill
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted

enum class MetricChartType { BAR, LINE }

/**
 * A second measure drawn on the same chart with its OWN y-scale.
 *
 * Two series on one chart only works if each keeps its own axis: 84 kg and
 * 17% have no shared unit, so a shared scale would flatten the percentage
 * line into a flat line at the bottom. Both axes are hidden; the legend
 * carries the colour and the tooltip carries both numbers.
 */
data class SecondarySeries(
    val label: String,
    val unit: String,
    val source: MetricSource,
    val decimals: Int = 1,
    val color: Color = ChartBlue,
)

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
    /** Optional second measure on the same chart, with its own axis. */
    secondary: SecondarySeries? = null,
    /** Optional second number under the hero (e.g. body fat under weight). */
    heroSubLine: String? = null,
    /** Name of the primary series for the legend (e.g. "Weight"). */
    primaryLabel: String? = null,
    /** Draw a trailing 7-day moving average over the primary series. */
    showAverage: Boolean = true,
    anchorDayKey: String = remember { todayKey() },
    today: String = remember { todayKey() },
    /** Optional slot between header and hero — metric-to-metric nav, special sections. */
    topSlot: (@Composable () -> Unit)? = null,
    onBackClick: () -> Unit,
    formatValue: (Double?) -> String = { it?.toString() ?: "—" },
    isUnloggedToday: Boolean = false,
    lastLoggedText: String? = null,
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
                if (isUnloggedToday) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Nothing logged today",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMuted
                            )

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
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (!lastLoggedText.isNullOrBlank()) {
                            Text(
                                text = lastLoggedText,
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                } else {
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
                                fontSize = 12.sp,
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (!heroSubLine.isNullOrBlank()) {
                    Text(
                        text = heroSubLine,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
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
                            fontSize = 12.sp,
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
                val chipBg = if (isSelected) AccentTeal.copy(alpha = 0.16f) else MutedFill
                val chipBorder = if (isSelected) AccentTeal else BorderDefault
                val chipText = if (isSelected) AccentTeal else TextForeground

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
        val secondaryPoints = remember(secondary, dayKeys) {
            secondary?.let { sec ->
                SecondaryPoints(
                    label = sec.label,
                    unit = sec.unit,
                    points = sec.source.series(dayKeys),
                    color = sec.color,
                    format = { v ->
                        v?.let { String.format(java.util.Locale.US, "%.${sec.decimals}f", it) + (if (sec.unit.isEmpty()) "" else " " + sec.unit) }
                            ?: "\u2014"
                    },
                )
            }
        }

        val average = remember(points, showAverage) {
            if (!showAverage) emptyList() else {
                val values = points.map { it.value }
                if (!MovingAverage.hasEnough(values)) emptyList()
                else MovingAverage.rollingPoints(
                    points = points,
                    key = { it.dayKey },
                    value = { it.value },
                ).map { it.value }
            }
        }

        MetricChartCard(
            points = points,
            chartType = chartType,
            formatValue = formatValue,
            today = today,
            secondary = secondaryPoints,
            average = average,
            primaryLabel = primaryLabel,
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
                        .background(AccentTeal)
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

        // 6. Recent days. One row per DAY, not per reading: a skipped day is
        //    shown as a skipped day, not deleted. Sam, 2026-11-18.
        if (showList && dayKeys.isNotEmpty()) {
            val rows = DayRows.build(
                dayKeys = dayKeys,
                values = points.map { it.value },
                averages = average,
            )

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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RECENT DAYS",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                        val logged = DayRows.loggedCount(rows)
                        Text(
                            text = if (logged == 0) "no readings" else "$logged of ${rows.size} logged",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }

                    rows.forEach { row ->
                        val dayTitle = formatDayKeyTitle(row.dayKey, today)
                        val hasValue = row.value != null

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
                                        .background(if (hasValue) AccentTeal else MutedFill)
                                )
                                Text(
                                    text = dayTitle,
                                    fontSize = 13.sp,
                                    color = if (hasValue) TextForeground else TextMuted,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (hasValue && row.average != null) {
                                    Text(
                                        text = "avg " + formatValue(row.average),
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    )
                                }
                                Text(
                                    text = if (hasValue) formatValue(row.value) else "no reading",
                                    fontSize = 13.sp,
                                    color = if (hasValue) TextForeground else TextMuted,
                                    fontWeight = if (hasValue) FontWeight.SemiBold else FontWeight.Normal
                                )
                            }
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
/** Second series resolved to the same day keys, with its own formatter. */
internal data class SecondaryPoints(
    val label: String,
    val unit: String,
    val points: List<MetricPoint>,
    val color: Color,
    val format: (Double?) -> String,
)

/** Scale for one series: nulls ignored, so a gap never drags the axis to zero. */
private fun scaleOf(values: List<Double>, line: Boolean): Pair<Double, Double> {
    if (values.isEmpty()) return 0.0 to 100.0
    val max = values.max()
    val min = values.min()
    return if (line) (min * 0.98) to max else 0.0 to max
}

/**
 * Draws one connected series, bridging missing days.
 *
 * Sam's rule (2026-11-18): every dot connects. A gap in the readings is a gap
 * in the *measurements*, not a break in the trend, so leaving a hole in the
 * line made three weigh-ins look like three unrelated events. Measured
 * segments are solid; segments that cross a missing day are dashed, so the eye
 * is not told a reading existed when it did not.
 */
private fun DrawScope.drawConnectedSeries(
    n: Int,
    valueAt: (Int) -> Double?,
    yOf: (Double) -> Float,
    color: Color,
    strokeWidth: Float,
    dashBridges: Boolean = true,
    bridgeColor: Color = color,
    /** Plot bounds — the Y-axis gutter means the series no longer spans the
     *  full canvas width. */
    left: Float = 0f,
    right: Float = size.width,
) {
    if (n == 0) return
    val plotW = (right - left).coerceAtLeast(1f)
    val slotWidth = if (n > 1) plotW / (n - 1) else plotW
    fun xOf(i: Int): Float = if (n > 1) left + i * slotWidth else left + plotW / 2f

    val real = (0 until n).filter { valueAt(it) != null }
    if (real.isEmpty()) return
    if (real.size == 1) {
        drawCircle(color = color, radius = 2.5.dp.toPx(), center = Offset(xOf(real[0]), yOf(valueAt(real[0])!!)))
        return
    }
    for (k in 0 until real.size - 1) {
        val a = real[k]
        val b = real[k + 1]
        val bridged = b - a > 1
        drawLine(
            color = if (bridged) bridgeColor else color,
            start = Offset(xOf(a), yOf(valueAt(a)!!)),
            end = Offset(xOf(b), yOf(valueAt(b)!!)),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
            pathEffect = if (bridged && dashBridges) {
                PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f)
            } else null,
        )
    }
}

@Composable
internal fun MetricChartCard(
    points: List<MetricPoint>,
    chartType: MetricChartType,
    formatValue: (Double?) -> String,
    today: String,
    secondary: SecondaryPoints? = null,
    average: List<Double?> = emptyList(),
    primaryLabel: String? = null,
    /** Dashed reference line at the metric's target, and a "target" label on it. */
    targetValue: Double? = null,
    chartHeight: Dp = 140.dp,
    /** Short X label for a day key. Passed in so a 7-day chart can use weekdays. */
    xLabel: (String) -> String = ::shortAxisDay,
    modifier: Modifier = Modifier,
    /** When set, the whole card opens the metric's full page. */
    onClick: (() -> Unit)? = null,
    /** Line/bar colour. The Metrix list passes the metric's domain hue; the
     *  full page keeps the teal so one metric does not change colour when you
     *  open it. */
    seriesColor: Color = AccentTeal,
    /** Title block above the chart — used by the Metrix list for name + value. */
    header: (@Composable () -> Unit)? = null,
) {
    var selectedIndex by remember(points) { mutableIntStateOf(-1) }

    val nonNullValues = points.mapNotNull { it.value }
    val (dataMin, dataMax) = scaleOf(nonNullValues, chartType == MetricChartType.LINE)
    // The axis must also fit the target line. A target above every reading is
    // the normal case while you are working toward it, and clamped to the data
    // it would silently drop out of the plot.
    val axisMin = targetValue?.let { minOf(dataMin, it) } ?: dataMin
    val axisMax = targetValue?.let { maxOf(dataMax, it) } ?: dataMax
    val ticks = remember(axisMin, axisMax) { niceAxisTicks(axisMin, axisMax) }
    val plotMin = ticks.firstOrNull() ?: axisMin
    val plotMax = ticks.lastOrNull() ?: axisMax
    val range = (plotMax - plotMin).coerceAtLeast(0.1)

    // Secondary gets its own scale — kg and % share no unit.
    val secValues = secondary?.points?.mapNotNull { it.value } ?: emptyList()
    val (secMin, secMax) = scaleOf(secValues, line = true)
    val secRange = (secMax - secMin).coerceAtLeast(0.1)

    val hasAverage = average.size == points.size && average.any { it != null }
    val latestAverage = average.lastOrNull { it != null }

    val measurer = rememberTextMeasurer()
    // 10sp floor: the legibility pass cut sub-12sp UI text, and an axis label
    // you have to lean in for is not a label.
    val axisStyle = remember { TextStyle(fontSize = 10.sp, color = TextMuted) }

    val yGutter = 34.dp
    val xBand = 16.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppCardSurface),
        border = BorderStroke(1.dp, BorderDefault)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            header?.invoke()

            // Tooltip header on tap — both measures for the same day.
            if (selectedIndex in points.indices) {
                val pt = points[selectedIndex]
                val secPt = secondary?.points?.getOrNull(selectedIndex)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDayKeyTitle(pt.dayKey, today),
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = formatValue(pt.value),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = seriesColor
                        )
                        if (secondary != null && secPt != null) {
                            Text(
                                text = secondary.format(secPt.value),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = secondary.color
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .pointerInput(points) {
                        detectTapGestures { offset ->
                            val n = points.size
                            if (n > 0) {
                                val gutter = yGutter.toPx()
                                val plotW = (size.width - gutter - 6.dp.toPx()).coerceAtLeast(1f)
                                val rel = ((offset.x - gutter) / plotW).coerceIn(0f, 1f)
                                selectedIndex = when {
                                    n == 1 -> 0
                                    chartType == MetricChartType.BAR -> (rel * n).toInt().coerceIn(0, n - 1)
                                    else -> (rel * (n - 1)).roundToInt().coerceIn(0, n - 1)
                                }
                            }
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val n = points.size

                val plotLeft = yGutter.toPx()
                val plotRight = w - 6.dp.toPx()
                val plotW = (plotRight - plotLeft).coerceAtLeast(1f)
                val plotTop = 8.dp.toPx()
                val plotBottom = h - xBand.toPx()
                val plotH = (plotBottom - plotTop).coerceAtLeast(1f)

                fun yOf(v: Double): Float =
                    plotBottom - (((v - plotMin) / range).toFloat().coerceIn(0f, 1f) * plotH)

                fun xOfLine(i: Int): Float =
                    if (n > 1) plotLeft + i * (plotW / (n - 1)) else plotLeft + plotW / 2f

                fun xOfBar(i: Int): Float = plotLeft + (i + 0.5f) * (plotW / n)

                // Y gridlines and their numbers. Ticks are snapped to round
                // steps, so these read as a scale rather than as noise.
                ticks.forEach { t ->
                    val y = yOf(t)
                    if (y in plotTop..plotBottom) {
                        drawLine(
                            color = MutedFill,
                            start = Offset(plotLeft, y),
                            end = Offset(plotRight, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    val layout = measurer.measure(AnnotatedString(shortAxisNumber(t)), axisStyle)
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            x = (plotLeft - 6.dp.toPx() - layout.size.width).coerceAtLeast(0f),
                            y = y - layout.size.height / 2f
                        )
                    )
                }

                // Baseline: the one line that is always the floor of the plot.
                drawLine(
                    color = MutedFill,
                    start = Offset(plotLeft, plotBottom),
                    end = Offset(plotRight, plotBottom),
                    strokeWidth = 1.dp.toPx()
                )

                if (n == 0) return@Canvas

                // Where the finger landed. Drawn under the series so it reads as
                // a guide, not as data.
                if (selectedIndex in 0 until n) {
                    val sx = if (chartType == MetricChartType.BAR) xOfBar(selectedIndex) else xOfLine(selectedIndex)
                    drawLine(
                        color = TextMuted,
                        start = Offset(sx, plotTop),
                        end = Offset(sx, plotBottom),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Target line: dashed and labelled on the line itself, so it
                // needs no legend entry to be understood.
                targetValue?.let { t ->
                    if (t in plotMin..plotMax) {
                        val ty = yOf(t)
                        drawLine(
                            color = TextMuted,
                            start = Offset(plotLeft, ty),
                            end = Offset(plotRight, ty),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f
                            )
                        )
                        val layout = measurer.measure(AnnotatedString("target"), axisStyle)
                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(
                                x = plotRight - layout.size.width,
                                y = (ty - layout.size.height - 1.dp.toPx()).coerceAtLeast(0f)
                            )
                        )
                    }
                }

                if (chartType == MetricChartType.BAR) {
                    val slotWidth = plotW / n
                    val barWidth = (slotWidth * 0.70f).coerceIn(4f, 24f)

                    points.forEachIndexed { i, pt ->
                        val v = pt.value ?: return@forEachIndexed // never plot a missing day as 0
                        val top = yOf(v)
                        val barHeight = (plotBottom - top).coerceAtLeast(2.dp.toPx())
                        val x = plotLeft + i * slotWidth + (slotWidth - barWidth) / 2f
                        val isSelected = i == selectedIndex

                        drawRoundRect(
                            color = if (isSelected) seriesColor else seriesColor.copy(alpha = 0.60f),
                            topLeft = Offset(x, plotBottom - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                } else {
                    // Secondary line first, behind: own scale, own colour.
                    secondary?.let { sec ->
                        drawConnectedSeries(
                            n = n,
                            valueAt = { i -> sec.points.getOrNull(i)?.value },
                            yOf = { v ->
                                plotBottom - (((v - secMin) / secRange).toFloat().coerceIn(0f, 1f) * plotH)
                            },
                            color = sec.color,
                            strokeWidth = 2.dp.toPx(),
                            left = plotLeft,
                            right = plotRight,
                        )
                    }

                    // Primary: every real point connects; dashed across holes.
                    drawConnectedSeries(
                        n = n,
                        valueAt = { i -> points.getOrNull(i)?.value },
                        yOf = yOf,
                        color = seriesColor,
                        strokeWidth = 2.dp.toPx(),
                        left = plotLeft,
                        right = plotRight,
                    )
                }

                // 7-day average: same scale as the primary, neutral grey so it
                // reads as a summary and never as a warning (deficit rule).
                if (hasAverage) {
                    drawConnectedSeries(
                        n = n,
                        valueAt = { i -> average.getOrNull(i) },
                        yOf = yOf,
                        color = AverageLine,
                        strokeWidth = 1.5.dp.toPx(),
                        dashBridges = false,
                        left = plotLeft,
                        right = plotRight,
                    )
                }

                // Dots on top: a dot means "there is a reading here". The dashes
                // between two dots mean "there is not".
                if (chartType != MetricChartType.BAR) {
                    points.forEachIndexed { i, pt ->
                        if (pt.value != null) {
                            val isSelected = i == selectedIndex
                            drawCircle(
                                color = if (isSelected) TextForeground else seriesColor,
                                radius = if (isSelected) 5.dp.toPx() else 3.dp.toPx(),
                                center = Offset(xOfLine(i), yOf(pt.value))
                            )
                        }
                    }
                }

                // X labels: first and last always, evenly spaced between. Clamped
                // inside the canvas so the last one cannot be cut in half.
                axisLabelIndexes(n).forEach { i ->
                    val layout = measurer.measure(AnnotatedString(xLabel(points[i].dayKey)), axisStyle)
                    val centre = if (chartType == MetricChartType.BAR) xOfBar(i) else xOfLine(i)
                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(
                            x = (centre - layout.size.width / 2f).coerceIn(0f, (w - layout.size.width).coerceAtLeast(0f)),
                            y = plotBottom + 3.dp.toPx()
                        )
                    )
                }
            }

            // Legend. Units live on the Y axis now, so this names the series
            // rather than substituting for the axis.
            val legendPrimary = primaryLabel ?: secondaryPrimaryLabel(points, formatValue)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(color = seriesColor, label = legendPrimary)
                secondary?.let { sec -> LegendDot(color = sec.color, label = sec.label) }
                if (hasAverage && latestAverage != null) {
                    LegendDot(
                        color = AverageLine,
                        label = "7-day avg " + formatValue(latestAverage),
                    )
                }
            }
        }
    }
}

/** Neutral, not a judgement colour: an average is a description, not a verdict. */
private val AverageLine = TextForeground.copy(alpha = 0.45f)

/** The primary series has no name of its own in the chart — the page title is
 *  "Body Comp", so the lime line is the weight. Falls back to the latest value
 *  so the legend reads like a figure key rather than a second title. */
private fun secondaryPrimaryLabel(points: List<MetricPoint>, formatValue: (Double?) -> String): String {
    val latest = points.lastOrNull { it.value != null }?.value
    return if (latest != null) formatValue(latest) else "Weight"
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(modifier = Modifier.size(6.dp)) {
            drawCircle(color = color, radius = size.minDimension / 2f)
        }
        Text(text = label, fontSize = 12.sp, color = TextMuted)
    }
}

@Preview(showBackground = true)
@Composable
fun MetricPageScreenPreview() {
    com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme {
        MetricPageScreen(
            title = "Steps",
            heroValue = "8,420",
            heroUnit = "steps",
            heroScore = 85.0,
            insightSentence = "Pacing well ahead of your 7-day average.",
            chartType = MetricChartType.BAR,
            metricSource = EmptyMetricSource(),
            onBackClick = {}
        )
    }
}
