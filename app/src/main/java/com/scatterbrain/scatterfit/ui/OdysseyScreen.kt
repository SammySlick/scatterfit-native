package com.scatterbrain.scatterfit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.core.GOAL_WEIGHTS
import com.scatterbrain.scatterfit.core.GoalId
import com.scatterbrain.scatterfit.core.GoalPreset
import com.scatterbrain.scatterfit.core.ScoreShape
import com.scatterbrain.scatterfit.core.dailyMetricPoints
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.core.weekScoreFromContributions
import com.scatterbrain.scatterfit.ui.theme.AccentTeal
import com.scatterbrain.scatterfit.ui.theme.AppBackground
import com.scatterbrain.scatterfit.ui.theme.AppCardSurface
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.DarkCardSurface
import com.scatterbrain.scatterfit.ui.theme.OnPrimaryText
import com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import com.scatterbrain.scatterfit.ui.theme.momentumBand
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class SpokeItem(
    val id: GoalId,
    val name: String,
    val weight: Double,
    val mean: Double?,
    val loggedDays: Int,
    val share: Double,
    val windowMean: Double?,
    val participatingDays: Int,
    val todayScore: Double?,
    val todayPoints: Double?,
)

@Composable
fun OdysseyScreen(
    modifier: Modifier = Modifier,
    momentumSource: MomentumSource? = null,
    onSettingsClick: () -> Unit = {}
) {
    val source: MomentumSource = remember(momentumSource) {
        momentumSource ?: TodayFacadeMomentumSource()
    }
    val scrollState = rememberScrollState()
    var selectedWindowDays by remember { mutableIntStateOf(7) }

    val today = remember { todayKey() }
    val weights = remember(source.scoringSettings.activeGoal) {
        GOAL_WEIGHTS[source.scoringSettings.activeGoal] ?: GOAL_WEIGHTS[GoalPreset.FAT_LOSS]!!
    }
    val contribs = remember(source, selectedWindowDays, today) {
        source.contributions(selectedWindowDays, today)
    }
    val contribMap = remember(contribs) { contribs.associateBy { it.id } }

    val spokeItems = remember(weights, contribMap, selectedWindowDays, today) {
        weights.filter { it.value > 0.0 }.map { (goalId, weight) ->
            val contrib = contribMap[goalId]
            val windowMean = source.metricMeanOverWindow(goalId, today, selectedWindowDays)
            val participatingDays = source.metricParticipatingDaysOverWindow(goalId, today, selectedWindowDays)
            val dailyScore = source.metricScoreForDay(goalId, today)
            val shape = when (goalId) {
                GoalId.STEPS, GoalId.PROTEIN, GoalId.EXERCISE -> ScoreShape.ONE_SIDED
                GoalId.CALORIES, GoalId.SLEEP -> ScoreShape.TWO_SIDED
                GoalId.WEIGHT, GoalId.BODY_FAT -> ScoreShape.PROGRESS
                else -> ScoreShape.ONE_SIDED
            }
            val points = dailyMetricPoints(shape, value = null, target = null, score = dailyScore)
            val name = when (goalId) {
                GoalId.STEPS -> "Steps"
                GoalId.CALORIES -> "Calories"
                GoalId.PROTEIN -> "Protein"
                GoalId.SLEEP -> "Sleep"
                GoalId.EXERCISE -> "Exercise"
                GoalId.WEIGHT -> "Weight"
                GoalId.BODY_FAT -> "Body fat"
                GoalId.UNITS -> "Alcohol units"
                GoalId.ALCOHOL -> "Alcohol"
                GoalId.Z4PLUS -> "Z4+ Training"
                GoalId.RHR_TREND -> "RHR Trend"
            }
            SpokeItem(
                id = goalId,
                name = name,
                weight = weight,
                mean = contrib?.mean ?: windowMean,
                loggedDays = contrib?.loggedDays ?: participatingDays,
                share = contrib?.share ?: 0.0,
                windowMean = windowMean,
                participatingDays = participatingDays,
                todayScore = dailyScore,
                todayPoints = points,
            )
        }
    }

    val overallScore = remember(contribs, source, today) {
        weekScoreFromContributions(contribs) ?: source.momentumForDay(today)
    }
    val band = momentumBand(overallScore ?: 0.0)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Top Header — Matrix
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Matrix",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = TextForeground
            )
            IconButton(onClick = onSettingsClick) {
                Text(
                    text = "⚙",
                    fontSize = 22.sp,
                    color = TextForeground
                )
            }
        }

        // Momentum Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
            border = BorderStroke(1.dp, BorderDefault)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MOMENTUM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (overallScore != null) "${overallScore.roundToInt()}" else "—",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (overallScore != null) band.color else TextMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(band.color.copy(alpha = 0.15f))
                        .border(BorderStroke(1.dp, band.color), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = band.label,
                        color = band.color,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Spider Graph Card with 7d / 14d / 30d Window Selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AppCardSurface),
            border = BorderStroke(1.dp, BorderDefault)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CONTRIBUTIONS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )

                    // 7d / 14d / 30d Toggle
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(7 to "7d", 14 to "14d", 30 to "30d").forEach { (days, label) ->
                            val isSelected = selectedWindowDays == days
                            val bg = if (isSelected) AccentTeal else SecondaryFill
                            val textColor = if (isSelected) OnPrimaryText else TextMuted
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bg)
                                    .clickable { selectedWindowDays = days }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = textColor,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // Spider Graph Canvas with Ghost Axes
                MatrixSpiderGraph(
                    spokes = spokeItems,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Metric Rows Header
        Text(
            text = "METRICS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Per-Metric Rows
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            spokeItems.forEach { item ->
                MetricScoreRow(item = item)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun MatrixSpiderGraph(
    spokes: List<SpokeItem>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier) {
        val n = spokes.size
        if (n == 0) return@Canvas

        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = (size.minDimension / 2f - 34.dp.toPx()).coerceAtLeast(10f)

        // 1. Concentric guide polygons (25%, 50%, 75%, 100%)
        val ringFractions = listOf(0.25f, 0.50f, 0.75f, 1.0f)
        ringFractions.forEach { frac ->
            val ringPath = Path()
            spokes.forEachIndexed { i, _ ->
                val angle = -Math.PI / 2.0 + (2.0 * Math.PI * i / n)
                val x = center.x + radius * frac * cos(angle).toFloat()
                val y = center.y + radius * frac * sin(angle).toFloat()
                if (i == 0) ringPath.moveTo(x, y) else ringPath.lineTo(x, y)
            }
            ringPath.close()
            drawPath(
                path = ringPath,
                color = BorderDefault.copy(alpha = 0.35f),
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // 2. Spokes (Hollow/Ghost axis for metrics WITHOUT data, solid for metrics WITH data)
        val activePoints = mutableListOf<Offset>()
        val activeColors = mutableListOf<Color>()

        spokes.forEachIndexed { i, spoke ->
            val angle = -Math.PI / 2.0 + (2.0 * Math.PI * i / n)
            val cosA = cos(angle).toFloat()
            val sinA = sin(angle).toFloat()
            val spokeEnd = Offset(center.x + radius * cosA, center.y + radius * sinA)

            val hasData = spoke.loggedDays > 0 && spoke.mean != null

            if (!hasData) {
                // Ghost axis: BorderDefault stroke, no fill, never failing color
                drawLine(
                    color = BorderDefault,
                    start = center,
                    end = spokeEnd,
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
            } else {
                // Active spoke line
                drawLine(
                    color = BorderDefault.copy(alpha = 0.6f),
                    start = center,
                    end = spokeEnd,
                    strokeWidth = 1.dp.toPx()
                )

                // Active point position
                val f = (spoke.mean / 100.0).coerceIn(0.1, 1.0).toFloat()
                val pt = Offset(center.x + radius * f * cosA, center.y + radius * f * sinA)
                activePoints.add(pt)
                activeColors.add(momentumBand(spoke.mean).color)
            }

            // Spoke Name Label outside radius
            val labelPos = Offset(center.x + (radius + 18.dp.toPx()) * cosA, center.y + (radius + 18.dp.toPx()) * sinA)
            val textColor = if (hasData) TextForeground else TextMuted
            val measured = textMeasurer.measure(
                text = spoke.name,
                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = textColor)
            )
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(labelPos.x - measured.size.width / 2f, labelPos.y - measured.size.height / 2f)
            )
        }

        // 3. Filled polygon for active spokes
        if (activePoints.size >= 3) {
            val polyPath = Path().apply {
                activePoints.forEachIndexed { idx, pt ->
                    if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                }
                close()
            }
            drawPath(polyPath, color = AccentTeal.copy(alpha = 0.20f), style = Fill)
            drawPath(
                path = polyPath,
                color = AccentTeal,
                style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round)
            )
        }

        // 4. Vertex dots for active spokes
        activePoints.forEachIndexed { idx, pt ->
            drawCircle(color = activeColors[idx], radius = 4.dp.toPx(), center = pt)
        }
    }
}

@Composable
fun MetricScoreRow(
    item: SpokeItem,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppCardSurface),
        border = BorderStroke(1.dp, BorderDefault)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.name.uppercase(),
                modifier = Modifier.weight(1f, fill = false),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val isCalOrSleep = item.id == GoalId.CALORIES || item.id == GoalId.SLEEP
            if (isCalOrSleep && item.participatingDays < 3) {
                // "Not enough logged days" state for calories/sleep: NEUTRAL GREY (TextMuted), never red
                Text(
                    text = "Not enough logged days",
                    fontSize = 12.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Normal
                )
            } else if (item.windowMean != null) {
                val scoreInt = item.windowMean.roundToInt()
                val band = momentumBand(item.windowMean)
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (item.todayPoints != null) {
                            Text(
                                text = "+${item.todayPoints.roundToInt()} pts",
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(band.color.copy(alpha = 0.15f))
                                .border(BorderStroke(1.dp, band.color), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "$scoreInt%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = band.color
                            )
                        }
                    }
                    Text(
                        text = "mean of ${item.participatingDays} ${if (item.participatingDays == 1) "day" else "days"}",
                        fontSize = 10.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Normal
                    )
                }
            } else {
                Text(
                    text = "No data",
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun OdysseyScreenPreview() {
    ScatterFitTheme {
        OdysseyScreen()
    }
}
