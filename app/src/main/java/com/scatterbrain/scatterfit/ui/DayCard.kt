package com.scatterbrain.scatterfit.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.core.MetricId
import com.scatterbrain.scatterfit.core.ScoringSettings
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
import com.scatterbrain.scatterfit.ui.theme.StateBad
import com.scatterbrain.scatterfit.ui.theme.StateLocked
import com.scatterbrain.scatterfit.ui.theme.TealHighlight
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

data class DayCardTicks(
    val completedCount: Int,
    val totalTarget: Int, // 4 on training days, 3 on rest days
    val hasWeightBonus: Boolean,
) {
    val counterText: String = "$completedCount / $totalTarget"
}

fun computeDayCardTicks(
    daily: AssembledDaily?,
    settings: ScoringSettings = ScoringSettings(),
    todayKey: String,
    isTrainingDay: Boolean,
): DayCardTicks {
    val totalM = if (isTrainingDay) 4 else 3
    if (daily == null) {
        return DayCardTicks(completedCount = 0, totalTarget = totalM, hasWeightBonus = false)
    }

    val calVal = daily.caloriesEaten[todayKey] ?: 0.0
    // Ticked if calories in band (or logged within goal)
    val calTicked = calVal > 0.0 && calVal <= (settings.kcalTarget + settings.tolerances.kcalGreen)

    val stepVal = daily.steps[todayKey] ?: 0.0
    val stepTicked = stepVal >= settings.stepTarget

    val protVal = daily.protein[todayKey] ?: 0.0
    val protTicked = protVal >= settings.proteinTarget

    val trainMins = daily.trainingMinutes[todayKey] ?: 0.0
    // Training days only! Rest days never auto-tick training.
    val trainTicked = isTrainingDay && trainMins > 0.0

    val weightVal = daily.weight[todayKey]
    val hasWeightToday = weightVal != null && weightVal > 0.0

    val baseCount = listOf(calTicked, stepTicked, protTicked).count { it } + if (trainTicked) 1 else 0
    val completedCount = (baseCount + if (hasWeightToday) 1 else 0).coerceAtLeast(0)

    return DayCardTicks(
        completedCount = completedCount,
        totalTarget = totalM,
        hasWeightBonus = hasWeightToday
    )
}

enum class MetricBarColor {
    TEAL,
    GREEN,
    ORANGE,
    RED
}

fun computeCaloriesBarColor(caloriesEaten: Double?, target: Int, greenTolerance: Int = 150): MetricBarColor {
    if (caloriesEaten == null || caloriesEaten <= 0.0 || target <= 0) return MetricBarColor.TEAL
    val overKcal = caloriesEaten - target
    val maxOrangeOverKcal = target * 0.10
    return when {
        overKcal > maxOrangeOverKcal + 0.0001 -> MetricBarColor.RED
        caloriesEaten > target + greenTolerance -> MetricBarColor.ORANGE
        caloriesEaten >= target - greenTolerance -> MetricBarColor.GREEN
        else -> MetricBarColor.TEAL
    }
}

fun computeStepsBarColor(steps: Double?, target: Int): MetricBarColor {
    if (steps == null || steps <= 0.0) return MetricBarColor.TEAL
    return if (steps >= target) MetricBarColor.GREEN else MetricBarColor.TEAL
}

fun computeProteinBarColor(protein: Double?, target: Int): MetricBarColor {
    if (protein == null || protein <= 0.0) return MetricBarColor.TEAL
    return if (protein >= target) MetricBarColor.GREEN else MetricBarColor.TEAL
}

fun isDrinksSectionVisible(drinksCount: Int, units: Double): Boolean {
    return drinksCount > 0 || units > 0.0
}

fun getBarColor(colorType: MetricBarColor): Color = when (colorType) {
    MetricBarColor.TEAL -> AccentTeal
    MetricBarColor.GREEN -> StateLocked
    MetricBarColor.ORANGE -> JudgementWarn
    MetricBarColor.RED -> StateBad
}

@Composable
fun DayCard(
    daily: AssembledDaily?,
    modifier: Modifier = Modifier,
    dayKey: String = remember { todayKey() },
    settings: ScoringSettings = remember { ScoringSettings() },
    planName: String? = null,
    drinksCount: Int? = null,
    onMetricClick: (MetricId) -> Unit = {},
    onPlanEditorClick: (() -> Unit)? = null,
    onWeighInClick: (() -> Unit)? = null,
    onResetToToday: (() -> Unit)? = null,
) {
    val isTrainingDay = planName != null && !planName.equals("Rest day", ignoreCase = true)
    val ticks = remember(daily, settings, dayKey, isTrainingDay) {
        computeDayCardTicks(daily, settings, dayKey, isTrainingDay)
    }

    val isCurrentToday = dayKey == remember { todayKey() }

    // Hero calculations: when nothing eaten, shows kcalTarget (not 0); over goal -> "N kcal over T" (number Bold red StateBad)
    val kcalTarget = settings.kcalTarget
    val kcalEaten = daily?.caloriesEaten?.get(dayKey)?.roundToInt() ?: 0
    val isOverGoal = kcalEaten > kcalTarget
    val overKcal = (kcalEaten - kcalTarget).coerceAtLeast(0)
    val remainingKcal = (kcalTarget - kcalEaten).coerceAtLeast(0)

    val heroNumberText = if (isOverGoal) {
        String.format(Locale.US, "%,d", overKcal)
    } else {
        String.format(Locale.US, "%,d", remainingKcal)
    }
    val heroNumberColor = if (isOverGoal) StateBad else TextForeground
    val heroCaptionText = if (isOverGoal) {
        "kcal over %,d".format(Locale.US, kcalTarget)
    } else {
        "kcal left of %,d".format(Locale.US, kcalTarget)
    }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // PART 1 — HERO BLOCK
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Left: Big kcal number + caption
                Column {
                    Text(
                        text = heroNumberText,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = heroNumberColor,
                        lineHeight = 60.sp
                    )
                    Text(
                        text = heroCaptionText,
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }

                // Right: Ticked fraction + Back to today if past/future
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
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

                    Text(
                        text = ticks.counterText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealHighlight
                    )
                    Text(
                        text = "ticked",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            // PART 2 — BARS REPLACE RINGS (Calories, Steps, Protein rows)

            // 1. Calories row
            val calRaw = daily?.caloriesEaten?.get(dayKey)
            val calFraction = if (calRaw != null && kcalTarget > 0) (calRaw.toFloat() / kcalTarget.toFloat()).coerceIn(0f, 1f) else 0f
            val calColor = getBarColor(computeCaloriesBarColor(calRaw, kcalTarget, settings.tolerances.kcalGreen))
            DayCardBarRow(
                icon = { FlameIcon(modifier = Modifier.size(18.dp), tint = AccentTeal) },
                label = "Calories",
                currentText = String.format(Locale.US, "%,d", calRaw?.roundToInt() ?: 0),
                targetText = " of %,d kcal".format(Locale.US, kcalTarget),
                progressFraction = calFraction,
                fillColor = calColor,
                onClick = { onMetricClick(MetricId.CALORIES_EATEN) }
            )

            // 2. Steps row
            val stepRaw = daily?.steps?.get(dayKey)
            val stepTarget = settings.stepTarget
            val stepFraction = if (stepRaw != null && stepTarget > 0) (stepRaw.toFloat() / stepTarget.toFloat()).coerceIn(0f, 1f) else 0f
            val stepColor = getBarColor(computeStepsBarColor(stepRaw, stepTarget))
            DayCardBarRow(
                icon = { FootstepsIcon(modifier = Modifier.size(18.dp), tint = AccentTeal) },
                label = "Steps",
                currentText = String.format(Locale.US, "%,d", stepRaw?.roundToInt() ?: 0),
                targetText = " of %,d steps".format(Locale.US, stepTarget),
                progressFraction = stepFraction,
                fillColor = stepColor,
                onClick = { onMetricClick(MetricId.STEPS) }
            )

            // 3. Protein row
            val protRaw = daily?.protein?.get(dayKey)
            val protTarget = settings.proteinTarget
            val protFraction = if (protRaw != null && protTarget > 0) (protRaw.toFloat() / protTarget.toFloat()).coerceIn(0f, 1f) else 0f
            val protColor = getBarColor(computeProteinBarColor(protRaw, protTarget))
            DayCardBarRow(
                icon = { ProteinIcon(modifier = Modifier.size(18.dp), tint = AccentTeal) },
                label = "Protein",
                currentText = String.format(Locale.US, "%,d", protRaw?.roundToInt() ?: 0),
                targetText = " of %,d g".format(Locale.US, protTarget),
                progressFraction = protFraction,
                fillColor = protColor,
                onClick = { onMetricClick(MetricId.PROTEIN) }
            )

            // PART 3 — TRAINING ROW
            DayCardTrainingRow(
                isTrainingDay = isTrainingDay,
                planName = planName,
                onClick = onPlanEditorClick
            )

            // PART 4 — CONTEXT CARDS (two side-by-side)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sleep Card
                val sleepNight = daily?.sleep?.get(dayKey)
                val hasSleepData = sleepNight != null && sleepNight.totalMin > 0
                val sleepPrimary = if (hasSleepData) {
                    val mins = sleepNight.totalMin.roundToInt()
                    "${mins / 60}h ${mins % 60}m"
                } else {
                    "—"
                }
                val sleepSecondary = if (hasSleepData) "last night" else "syncs overnight"

                DayCardContextCard(
                    modifier = Modifier.weight(1f),
                    icon = { MoonIcon(modifier = Modifier.size(18.dp), tint = AccentTeal) },
                    primaryText = sleepPrimary,
                    secondaryText = sleepSecondary,
                    showDot = false,
                    onClick = { onMetricClick(MetricId.SLEEP) }
                )

                // Weight Card
                val todayWeight = daily?.weight?.get(dayKey)
                val todayBf = daily?.bodyFat?.get(dayKey)
                val hasWeightToday = todayWeight != null && todayWeight > 0.0

                val weightPrimary = if (hasWeightToday) "%.1f kg".format(Locale.US, todayWeight) else "No weigh-in yet"
                val weightSecondary = if (hasWeightToday && todayBf != null && todayBf > 0.0) {
                    "%.1f%% body fat".format(Locale.US, todayBf)
                } else if (hasWeightToday) {
                    "Logged"
                } else {
                    "Tap to weigh in"
                }

                DayCardContextCard(
                    modifier = Modifier.weight(1f),
                    icon = { ScaleIcon(modifier = Modifier.size(18.dp), tint = AccentTeal) },
                    primaryText = weightPrimary,
                    secondaryText = weightSecondary,
                    showDot = hasWeightToday,
                    onClick = onWeighInClick
                )
            }

            // PART 5 — DRINKS SECTION (hidden until >= 1 drink logged today)
            val todayUnits = daily?.alcoholUnits?.get(dayKey) ?: 0.0
            val todayKcal = daily?.alcoholKcal?.get(dayKey) ?: 0.0
            val todayDrinksCount = drinksCount ?: if (todayUnits > 0.0) kotlin.math.ceil(todayUnits).toInt() else 0

            if (isDrinksSectionVisible(todayDrinksCount, todayUnits)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMetricClick(MetricId.ALCOHOL_UNITS) }
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "DRINKS TODAY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DrinksStatChip(
                            modifier = Modifier.weight(1f),
                            value = "$todayDrinksCount",
                            label = "DRINKS"
                        )
                        DrinksStatChip(
                            modifier = Modifier.weight(1f),
                            value = if (todayUnits % 1.0 == 0.0) "${todayUnits.toInt()}" else "%.1f".format(Locale.US, todayUnits),
                            label = "UNITS"
                        )
                        DrinksStatChip(
                            modifier = Modifier.weight(1f),
                            value = String.format(Locale.US, "%,d", todayKcal.roundToInt()),
                            label = "KCAL"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCardBarRow(
    icon: @Composable () -> Unit,
    label: String,
    currentText: String,
    targetText: String,
    progressFraction: Float,
    fillColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.70f else 1f,
        animationSpec = tween(80),
        label = "rowAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                icon()
                Text(
                    text = label,
                    fontSize = 13.sp,
                    color = TextForeground,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = TextForeground, fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                        append(currentText)
                    }
                    withStyle(SpanStyle(color = TextMuted, fontWeight = FontWeight.Normal, fontSize = 13.sp)) {
                        append(targetText)
                    }
                },
                textAlign = TextAlign.End
            )
        }

        // 6dp Progress Bar with 8% white track and minimum 2dp visible fill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = progressFraction.coerceIn(0f, 1f))
                    .widthIn(min = 2.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(fillColor)
            )
        }
    }
}

@Composable
private fun DayCardTrainingRow(
    isTrainingDay: Boolean,
    planName: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (isPressed && isTrainingDay) 0.70f else 1f,
        animationSpec = tween(80),
        label = "trainingRowAlpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .graphicsLayer { this.alpha = alpha }
            .then(
                if (isTrainingDay && onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isTrainingDay) {
                DashedCircleIcon(
                    modifier = Modifier.size(20.dp),
                    tint = AccentTeal
                )
            } else {
                BedIcon(
                    modifier = Modifier.size(20.dp),
                    tint = TextMuted
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = if (isTrainingDay) (planName ?: "Training") else "Rest day",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isTrainingDay) "Tap to log your session" else "No session planned",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        }

        if (isTrainingDay) {
            ChevronRightIcon(
                modifier = Modifier.size(14.dp),
                tint = TextMuted
            )
        }
    }
}

@Composable
private fun DayCardContextCard(
    icon: @Composable () -> Unit,
    primaryText: String,
    secondaryText: String,
    showDot: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.70f else 1f,
        animationSpec = tween(80),
        label = "contextCardAlpha"
    )

    Card(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SecondaryFill),
        border = BorderStroke(1.dp, BorderDefault)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(AccentTeal)
                        .align(Alignment.TopEnd)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                icon()
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = primaryText,
                    fontSize = if (primaryText.length > 8) 14.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                Text(
                    text = secondaryText,
                    fontSize = 11.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DrinksStatChip(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(SecondaryFill)
            .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(8.dp))
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextForeground
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                letterSpacing = 0.5.sp
            )
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
            alcoholKcal = mapOf(today to 140.0),
            weight = mapOf("2026-10-12" to 76.4),
            zoneBounds = ZoneBounds(130, 145, 160, 175),
            zone = ZoneId.of("UTC")
        )
        DayCard(
            daily = mockDaily,
            dayKey = today,
            planName = "Hypertrophy Push"
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
            bodyFat = mapOf(today to 18.2),
            zoneBounds = ZoneBounds(130, 145, 160, 175),
            zone = ZoneId.of("UTC")
        )
        DayCard(
            daily = mockDaily,
            dayKey = today,
            planName = "Rest day"
        )
    }
}
