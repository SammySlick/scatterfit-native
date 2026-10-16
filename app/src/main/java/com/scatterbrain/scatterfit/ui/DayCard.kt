package com.scatterbrain.scatterfit.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.core.GoalId
import com.scatterbrain.scatterfit.core.MetricId
import com.scatterbrain.scatterfit.core.ScoringSettings
import com.scatterbrain.scatterfit.core.SquareBand
import com.scatterbrain.scatterfit.core.squareBand
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.data.AssembledDaily
import com.scatterbrain.scatterfit.data.SleepNight
import com.scatterbrain.scatterfit.data.ZoneBounds
import com.scatterbrain.scatterfit.ui.theme.AccentTeal
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.DarkCardSurface
import com.scatterbrain.scatterfit.ui.theme.JudgementWarn
import com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.StateLocked
import com.scatterbrain.scatterfit.ui.theme.TealHighlight
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class DayCardStatus(
    val caloriesPassed: Boolean,
    val stepsPassed: Boolean,
    val proteinPassed: Boolean,
    val sleepPassed: Boolean,
    val trainingPassed: Boolean,
) {
    val completedCount: Int = listOf(
        caloriesPassed,
        stepsPassed,
        proteinPassed,
        trainingPassed,
    ).count { it }

    val headerText: String = if (completedCount == 4) "All done ✓" else "$completedCount/4 done"
}

enum class RingState {
    DONE,        // Full teal circle + 10dp white checkmark centred inside
    IN_PROGRESS, // Teal arc, sweep = progress fraction (0..1)
    OUT_OF_BAND, // JudgementWarn orange arc (fraction 0..1), no checkmark
    NO_DATA      // Track only, empty
}

data class DayRingModel(
    val state: RingState,
    val fraction: Float,
)

fun computeDayRingModel(
    metricId: GoalId,
    value: Double?,
    settings: ScoringSettings,
): DayRingModel {
    if (value == null || value <= 0.0) {
        return DayRingModel(RingState.NO_DATA, 0f)
    }

    return when (metricId) {
        GoalId.CALORIES -> {
            val target = settings.kcalTarget.toDouble()
            val fraction = (value / target).toFloat().coerceIn(0f, 1f)
            val d = abs(value - target)
            when {
                d <= settings.tolerances.kcalGreen -> DayRingModel(RingState.DONE, 1f)
                value > target + settings.tolerances.kcalGreen -> DayRingModel(RingState.OUT_OF_BAND, fraction)
                else -> DayRingModel(RingState.IN_PROGRESS, fraction)
            }
        }
        GoalId.STEPS -> {
            val target = settings.stepTarget.toDouble()
            val fraction = (value / target).toFloat().coerceIn(0f, 1f)
            if (value >= target) {
                DayRingModel(RingState.DONE, 1f)
            } else {
                DayRingModel(RingState.IN_PROGRESS, fraction)
            }
        }
        GoalId.PROTEIN -> {
            val target = settings.proteinTarget.toDouble()
            val fraction = (value / target).toFloat().coerceIn(0f, 1f)
            if (value >= target || abs(value - target) <= settings.tolerances.proteinGreenUnder) {
                DayRingModel(RingState.DONE, 1f)
            } else {
                DayRingModel(RingState.OUT_OF_BAND, fraction)
            }
        }
        GoalId.SLEEP -> {
            val target = settings.sleepTargetHours * 60.0
            val fraction = (value / target).toFloat().coerceIn(0f, 1f)
            if (value >= target) {
                DayRingModel(RingState.DONE, 1f)
            } else {
                DayRingModel(RingState.OUT_OF_BAND, fraction)
            }
        }
        GoalId.EXERCISE -> {
            if (value > 0.0) {
                DayRingModel(RingState.DONE, 1f)
            } else {
                DayRingModel(RingState.NO_DATA, 0f)
            }
        }
        else -> DayRingModel(RingState.NO_DATA, 0f)
    }
}

fun computeDayCardStatus(
    daily: AssembledDaily?,
    settings: ScoringSettings = ScoringSettings(),
    todayKey: String,
): DayCardStatus {
    if (daily == null) {
        return DayCardStatus(
            caloriesPassed = false,
            stepsPassed = false,
            proteinPassed = false,
            sleepPassed = false,
            trainingPassed = false,
        )
    }

    val calBand = squareBand(GoalId.CALORIES, daily.caloriesEaten[todayKey], settings)
    val stepBand = squareBand(GoalId.STEPS, daily.steps[todayKey], settings)
    val protBand = squareBand(GoalId.PROTEIN, daily.protein[todayKey], settings)
    val sleepBand = squareBand(GoalId.SLEEP, daily.sleep[todayKey]?.totalMin, settings)
    val trainBand = squareBand(GoalId.EXERCISE, daily.trainingMinutes[todayKey], settings)

    return DayCardStatus(
        caloriesPassed = calBand == SquareBand.GREEN,
        stepsPassed = stepBand == SquareBand.GREEN,
        proteinPassed = protBand == SquareBand.GREEN,
        sleepPassed = sleepBand == SquareBand.GREEN,
        trainingPassed = trainBand == SquareBand.GREEN,
    )
}

@Composable
fun DayRing(
    model: DayRingModel,
    modifier: Modifier = Modifier,
) {
    val animatedFraction by animateFloatAsState(
        targetValue = model.fraction,
        animationSpec = tween(durationMillis = 300),
        label = "ringSweep"
    )

    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(24.dp)) {
            val strokeWidthPx = 3.dp.toPx()
            val arcSize = Size(size.width - strokeWidthPx, size.height - strokeWidthPx)
            val arcTopLeft = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f)
            val centerOffset = Offset(size.width / 2f, size.height / 2f)
            val radius = (size.minDimension - strokeWidthPx) / 2f

            // 1. Subtle circle track
            drawCircle(
                color = BorderDefault,
                radius = radius,
                center = centerOffset,
                style = Stroke(width = strokeWidthPx)
            )

            // 2. Arc based on state
            when (model.state) {
                RingState.DONE -> {
                    drawArc(
                        color = TealHighlight,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                }
                RingState.IN_PROGRESS -> {
                    if (animatedFraction > 0f) {
                        drawArc(
                            color = TealHighlight,
                            startAngle = -90f,
                            sweepAngle = animatedFraction * 360f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                        )
                    }
                }
                RingState.OUT_OF_BAND -> {
                    if (animatedFraction > 0f) {
                        drawArc(
                            color = JudgementWarn,
                            startAngle = -90f,
                            sweepAngle = animatedFraction * 360f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                        )
                    }
                }
                RingState.NO_DATA -> { /* track only */ }
            }

            // 3. 10dp white checkmark when DONE
            if (model.state == RingState.DONE) {
                val checkSize = 10.dp.toPx()
                val left = centerOffset.x - checkSize / 2f
                val top = centerOffset.y - checkSize / 2f
                val path = Path().apply {
                    moveTo(left + checkSize * 0.15f, top + checkSize * 0.50f)
                    lineTo(left + checkSize * 0.42f, top + checkSize * 0.78f)
                    lineTo(left + checkSize * 0.85f, top + checkSize * 0.22f)
                }
                drawPath(
                    path = path,
                    color = Color.White,
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}

@Composable
fun DayCard(
    daily: AssembledDaily?,
    modifier: Modifier = Modifier,
    dayKey: String = remember { todayKey() },
    settings: ScoringSettings = remember { ScoringSettings() },
    planName: String? = null,
    onMetricClick: (MetricId) -> Unit = {},
    onPlanEditorClick: (() -> Unit)? = null,
    onWeighInClick: (() -> Unit)? = null,
    onResetToToday: (() -> Unit)? = null,
) {
    val status = remember(daily, settings, dayKey) {
        computeDayCardStatus(daily, settings, dayKey)
    }

    val dateFormatted = remember(dayKey) {
        runCatching {
            val date = LocalDate.parse(dayKey)
            date.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.US))
        }.getOrDefault(dayKey)
    }

    val isCurrentToday = dayKey == remember { todayKey() }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = BorderStroke(1.dp, BorderDefault)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header row: "N/4 done" (or "All done ✓") on left, date + "Back to today" chip on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = status.headerText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = dateFormatted,
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    if (!isCurrentToday && onResetToToday != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(SecondaryFill)
                                .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(12.dp))
                                .clickable(onClick = onResetToToday)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Back to today",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextForeground
                            )
                        }
                    }
                }
            }

            // 1. Calories row
            val calRaw = daily?.caloriesEaten?.get(dayKey)
            val calVal = calRaw?.roundToInt()
            val calTarget = settings.kcalTarget
            val calText = if (calVal != null && calVal > 0) {
                val diff = calTarget - calVal
                if (diff >= 0) {
                    "%,d / %,d kcal · %,d left".format(calVal, calTarget, diff)
                } else {
                    "%,d / %,d kcal · %,d over".format(calVal, calTarget, -diff)
                }
            } else {
                "— / %,d kcal".format(calTarget)
            }
            val calRingModel = remember(calRaw, settings) {
                computeDayRingModel(GoalId.CALORIES, calRaw, settings)
            }
            DayCardRow(
                label = "Calories",
                valueText = calText,
                ringContent = { DayRing(calRingModel) },
                valueColor = if (status.caloriesPassed) StateLocked else TextForeground,
                onMetricClick = { onMetricClick(MetricId.CALORIES_EATEN) }
            )

            // 2. Steps row
            val stepRaw = daily?.steps?.get(dayKey)
            val stepVal = stepRaw?.roundToInt()
            val stepTarget = settings.stepTarget
            val stepText = if (stepVal != null && stepVal > 0) {
                val toGo = (stepTarget - stepVal).coerceAtLeast(0)
                if (toGo > 0) {
                    "%,d / %,d · %,d to go".format(stepVal, stepTarget, toGo)
                } else {
                    "%,d / %,d".format(stepVal, stepTarget)
                }
            } else {
                "— / %,d".format(stepTarget)
            }
            val stepRingModel = remember(stepRaw, settings) {
                computeDayRingModel(GoalId.STEPS, stepRaw, settings)
            }
            DayCardRow(
                label = "Steps",
                valueText = stepText,
                ringContent = { DayRing(stepRingModel) },
                valueColor = if (status.stepsPassed) StateLocked else TextForeground,
                onMetricClick = { onMetricClick(MetricId.STEPS) }
            )

            // 3. Protein row
            val protRaw = daily?.protein?.get(dayKey)
            val protVal = protRaw?.roundToInt()
            val protTarget = settings.proteinTarget
            val protText = if (protVal != null && protVal > 0) {
                "%,d / %,d g".format(protVal, protTarget)
            } else {
                "— / %,d g".format(protTarget)
            }
            val protRingModel = remember(protRaw, settings) {
                computeDayRingModel(GoalId.PROTEIN, protRaw, settings)
            }
            DayCardRow(
                label = "Protein",
                valueText = protText,
                ringContent = { DayRing(protRingModel) },
                valueColor = if (status.proteinPassed) StateLocked else TextForeground,
                onMetricClick = { onMetricClick(MetricId.PROTEIN) }
            )

            // 4. Sleep row (Morning vs Evening framing)
            val isMorning = remember {
                val zone = daily?.zone ?: ZoneId.systemDefault()
                LocalTime.now(zone).hour < 12
            }
            val sleepLabel = if (isMorning) "Sleep" else "Sleep (tonight's job)"
            val sleepNight = daily?.sleep?.get(dayKey)
            val sleepTargetH = settings.sleepTargetHours.roundToInt()
            val sleepText = if (sleepNight != null && sleepNight.totalMin > 0) {
                val mins = sleepNight.totalMin.roundToInt()
                "${mins / 60}h ${mins % 60}m"
            } else {
                "— / ${sleepTargetH}h"
            }
            val sleepRingModel = remember(sleepNight, settings) {
                computeDayRingModel(GoalId.SLEEP, sleepNight?.totalMin, settings)
            }
            DayCardRow(
                label = sleepLabel,
                valueText = sleepText,
                ringContent = { DayRing(sleepRingModel) },
                valueColor = if (status.sleepPassed) StateLocked else TextForeground,
                onMetricClick = { onMetricClick(MetricId.SLEEP) }
            )

            // 5. Training row (plan name + secondary minutes line)
            val trainRaw = daily?.trainingMinutes?.get(dayKey)
            val trainMins = trainRaw?.roundToInt() ?: 0
            val trainSubtitle = if (trainMins > 0) "$trainMins min today" else null
            val planTitle = planName ?: "Rest day"
            val trainRingModel = remember(trainRaw, settings) {
                computeDayRingModel(GoalId.EXERCISE, trainRaw, settings)
            }
            DayCardRow(
                label = "Training",
                valueText = planTitle,
                valueSecondaryText = trainSubtitle,
                ringContent = { DayRing(trainRingModel) },
                valueColor = if (status.trainingPassed) StateLocked else TextForeground,
                onMetricClick = onPlanEditorClick
            )

            // 6. Alcohol row (Conditional: absent if null or 0; tap opens MetricId.ALCOHOL_UNITS; 6dp dot when data)
            val todayUnits = daily?.alcoholUnits?.get(dayKey) ?: 0.0
            if (todayUnits > 0.0) {
                val monday = runCatching {
                    LocalDate.parse(dayKey).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                }.getOrNull()
                val weekKeys = if (monday != null) {
                    (0 until 7).map { monday.plusDays(it.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE) }
                } else emptyList()
                val weekUnits = weekKeys.sumOf { daily?.alcoholUnits?.get(it) ?: 0.0 }
                val alcText = "%.1fu today · %.1fu this week".format(todayUnits, weekUnits)

                DayCardRow(
                    label = "Alcohol",
                    valueText = alcText,
                    ringContent = {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AccentTeal)
                        )
                    },
                    valueColor = TextForeground,
                    onMetricClick = { onMetricClick(MetricId.ALCOHOL_UNITS) }
                )
            }

            // 7. Weight row (shows weight + body fat if present; 6dp dot when data today)
            val todayWeight = daily?.weight?.get(dayKey)
            val todayBf = daily?.bodyFat?.get(dayKey)
            val latestWeightEntry = daily?.weight?.filterValues { it > 0.0 }?.entries?.maxByOrNull { it.key }
            val latestBf = if (latestWeightEntry != null) daily?.bodyFat?.get(latestWeightEntry.key) else null
            val hasWeightToday = todayWeight != null && todayWeight > 0.0

            val weightAnnotated = buildAnnotatedString {
                if (hasWeightToday) {
                    val dayShort = runCatching {
                        LocalDate.parse(dayKey).format(DateTimeFormatter.ofPattern("EEE", Locale.US))
                    }.getOrDefault("")
                    val valText = if (todayBf != null && todayBf > 0.0) {
                        "%.1f kg · %.1f%% · %s".format(todayWeight, todayBf, dayShort)
                    } else {
                        "%.1f kg · %s".format(todayWeight, dayShort)
                    }
                    withStyle(SpanStyle(color = TextForeground, fontWeight = FontWeight.Bold)) {
                        append(valText)
                    }
                } else if (latestWeightEntry != null && latestWeightEntry.value > 0.0) {
                    val dayShort = runCatching {
                        LocalDate.parse(latestWeightEntry.key).format(DateTimeFormatter.ofPattern("EEE", Locale.US))
                    }.getOrDefault("")
                    val valText = if (latestBf != null && latestBf > 0.0) {
                        "%.1f kg · %.1f%% · last: %s".format(latestWeightEntry.value, latestBf, dayShort)
                    } else {
                        "%.1f kg · last: %s".format(latestWeightEntry.value, dayShort)
                    }
                    withStyle(SpanStyle(color = TextForeground, fontWeight = FontWeight.Bold)) {
                        append(valText)
                    }
                    withStyle(SpanStyle(color = TealHighlight, fontWeight = FontWeight.SemiBold)) {
                        append(" · weigh in?")
                    }
                } else {
                    withStyle(SpanStyle(color = TealHighlight, fontWeight = FontWeight.SemiBold)) {
                        append("weigh in?")
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .then(if (onWeighInClick != null) Modifier.clickable {
                        android.util.Log.d("ScatterFitSync", "weigh-in row tapped")
                        onWeighInClick()
                    } else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasWeightToday) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(AccentTeal)
                        )
                    }
                    Text(
                        text = "Weight",
                        fontSize = 12.sp,
                        color = TextForeground,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Text(
                    text = weightAnnotated,
                    fontSize = 12.sp,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun DayCardRow(
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
    ringContent: (@Composable () -> Unit)? = null,
    valueSecondaryText: String? = null,
    valueColor: Color = TextForeground,
    onMetricClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .then(if (onMetricClick != null) Modifier.clickable(onClick = onMetricClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (ringContent != null) {
                ringContent()
            }
            Text(
                text = label,
                fontSize = 12.sp,
                color = TextForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = valueText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = valueColor,
                    textAlign = TextAlign.End
                )
                if (valueSecondaryText != null) {
                    Text(
                        text = valueSecondaryText,
                        fontSize = 11.sp,
                        color = TextMuted,
                        textAlign = TextAlign.End
                    )
                }
            }
            if (onMetricClick != null) {
                ChevronRightIcon(
                    modifier = Modifier.size(12.dp),
                    tint = TextMuted
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DayCardEmptyPreview() {
    ScatterFitTheme {
        DayCard(
            daily = null,
            dayKey = "2026-10-14"
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DayCardPartialPreview() {
    ScatterFitTheme {
        val today = "2026-10-14"
        val mockDaily = AssembledDaily(
            steps = mapOf(today to 8400.0),
            caloriesEaten = mapOf(today to 1950.0),
            protein = mapOf(today to 92.0),
            sleep = mapOf(today to SleepNight(today, 460.0, 60.0, 90.0, 260.0, 50.0, "${today}T07:00:00Z")),
            trainingMinutes = mapOf(today to 0.0),
            alcoholUnits = mapOf(today to 2.0),
            weight = mapOf("2026-10-12" to 76.4),
            zoneBounds = ZoneBounds(130, 145, 160, 175),
            zone = ZoneId.of("UTC")
        )
        DayCard(
            daily = mockDaily,
            dayKey = today
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DayCardCompletePreview() {
    ScatterFitTheme {
        val today = "2026-10-14"
        val mockDaily = AssembledDaily(
            steps = mapOf(today to 10500.0),
            caloriesEaten = mapOf(today to 2000.0),
            protein = mapOf(today to 160.0),
            sleep = mapOf(today to SleepNight(today, 490.0, 80.0, 100.0, 260.0, 50.0, "${today}T07:00:00Z")),
            trainingMinutes = mapOf(today to 45.0),
            weight = mapOf(today to 76.4),
            zoneBounds = ZoneBounds(130, 145, 160, 175),
            zone = ZoneId.of("UTC")
        )
        DayCard(
            daily = mockDaily,
            dayKey = today
        )
    }
}
