package com.scatterbrain.scatterfit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.core.GOAL_WEIGHTS
import com.scatterbrain.scatterfit.core.GoalId
import com.scatterbrain.scatterfit.core.GoalPreset
import com.scatterbrain.scatterfit.ui.theme.AccentTeal
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.OnPrimaryText
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import com.scatterbrain.scatterfit.ui.theme.momentumBand
import kotlin.math.cos
import kotlin.math.sin

/**
 * One axis of the spider: the metric, its weight in the active goal, and its
 * mean score over the window.
 *
 * Only the fields the graph itself reads are kept. The old Matrix page's rows
 * also wanted `share`, `windowMean`, `participatingDays` and the day's points;
 * those rows are gone, so carrying them here would be dead weight pretending to
 * be a model.
 */
data class SpokeItem(
    val id: GoalId,
    val name: String,
    val weight: Double,
    /** Mean over the window, or null when the metric is untracked. */
    val mean: Double?,
    /** Days in the window with a reading. 0 means ghost axis. */
    val loggedDays: Int,
)

private fun spokeName(goalId: GoalId): String = when (goalId) {
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

/**
 * One spoke per weighted goal, measured over [windowDays] ending on [anchorDay].
 *
 * Extracted from the old Matrix page so the Weekly Check-in can draw the same
 * graph. The rule that matters and must not be lost in the move: a metric with
 * no data comes back with `mean == null` and `loggedDays == 0`, never `mean = 0`.
 * The canvas turns that into a dashed ghost axis. Untracked must never look like
 * failing (Momentum Rework Spec v1, 2026-10-25).
 */
fun buildSpokeItems(
    source: MomentumSource,
    windowDays: Int,
    anchorDay: String,
): List<SpokeItem> {
    val weights = GOAL_WEIGHTS[source.scoringSettings.activeGoal]
        ?: GOAL_WEIGHTS[GoalPreset.FAT_LOSS]!!
    val contribMap = source.contributions(windowDays, anchorDay).associateBy { it.id }

    return weights.filter { it.value > 0.0 }.map { (goalId, weight) ->
        val contrib = contribMap[goalId]
        SpokeItem(
            id = goalId,
            name = spokeName(goalId),
            weight = weight,
            // Never coerce these to 0.0: the canvas reads null + loggedDays == 0
            // as "draw a dashed ghost axis", and 0.0 as "this metric is scoring
            // zero", which is the one thing the spec forbids for an untracked metric.
            mean = contrib?.mean ?: source.metricMeanOverWindow(goalId, anchorDay, windowDays),
            loggedDays = contrib?.loggedDays
                ?: source.metricParticipatingDaysOverWindow(goalId, anchorDay, windowDays),
        )
    }
}

/** The 7d / 14d / 30d window pills that sit beside the graph's heading. */
@Composable
fun MomentumWindowSelector(
    selectedDays: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(7 to "7d", 14 to "14d", 30 to "30d").forEach { (days, label) ->
            val isSelected = selectedDays == days
            val bg = if (isSelected) AccentTeal else SecondaryFill
            val textColor = if (isSelected) OnPrimaryText else TextMuted
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .clickable { onSelect(days) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = label,
                    color = textColor,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Spider graph across every metric the active goal weights.
 *
 * Drawing code is unchanged from the Matrix page it was lifted out of — it was
 * reviewed and approved there, so only the name moved. Hollow dashed axes and
 * muted labels mark metrics with no data; nothing is drawn in a failing colour
 * for an untracked metric.
 */
@Composable
fun MomentumSpiderGraph(
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
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = textColor)
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
