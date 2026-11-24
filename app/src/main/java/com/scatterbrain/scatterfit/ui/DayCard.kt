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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.scatterbrain.scatterfit.core.MetricId
import com.scatterbrain.scatterfit.core.ScoringSettings
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.data.AssembledDaily
import com.scatterbrain.scatterfit.data.SettingsStorage
import com.scatterbrain.scatterfit.data.SleepNight
import com.scatterbrain.scatterfit.data.ZoneBounds
import com.scatterbrain.scatterfit.ui.theme.AccentTeal
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.ChartBlue
import com.scatterbrain.scatterfit.ui.theme.DarkCardSurface
import com.scatterbrain.scatterfit.ui.theme.JudgementWarn
import com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.StateBad
import com.scatterbrain.scatterfit.ui.theme.StateLocked
import com.scatterbrain.scatterfit.ui.theme.TealHighlight
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import com.scatterbrain.scatterfit.ui.theme.Track
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

data class DayCardTicks(
    val completedCount: Int,
    val totalTarget: Int, // 5 on training days, 4 on rest days
) {
    val counterText: String = "$completedCount / $totalTarget"
}

fun computeDayCardTicks(
    daily: AssembledDaily?,
    settings: ScoringSettings = SettingsStorage().loadSettings(),
    todayKey: String,
    isTrainingDay: Boolean,
    consumedKcal: Double? = null,
    consumedProtein: Double? = null,
    totalSteps: Double? = null,
): DayCardTicks {
    val totalM = if (isTrainingDay) 5 else 4
    if (daily == null && consumedKcal == null && consumedProtein == null && totalSteps == null) {
        return DayCardTicks(completedCount = 0, totalTarget = totalM)
    }

    val calVal = (daily?.caloriesEaten?.get(todayKey) ?: 0.0)
    // Ticked if calories in band (or logged within goal)
    val calCompleted = calVal > 0.0 && calVal <= (settings.kcalTarget + settings.tolerances.kcalGreen)

    val stepVal = totalSteps ?: daily?.steps?.get(todayKey) ?: 0.0
    val stepCompleted = stepVal >= settings.stepTarget

    val protVal = consumedProtein ?: daily?.protein?.get(todayKey) ?: 0.0
    val protCompleted = protVal >= settings.proteinTarget

    val weightVal = daily?.weight?.get(todayKey)
    val weightCompleted = weightVal != null && weightVal > 0.0

    val trainMins = daily?.trainingMinutes?.get(todayKey) ?: 0.0
    // Training days only! Rest days never auto-tick training.
    val trainCompleted = isTrainingDay && trainMins > 0.0

    val baseCount = listOf(calCompleted, stepCompleted, protCompleted, weightCompleted).count { it }
    val totalCount = baseCount + if (trainCompleted) 1 else 0
    val completedCount = totalCount.coerceIn(0, totalM)

    return DayCardTicks(
        completedCount = completedCount,
        totalTarget = totalM
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
    settings: ScoringSettings? = null,
    planName: String? = null,
    drinksCount: Int? = null,
    onMetricClick: (MetricId) -> Unit = {},
    onPlanEditorClick: (() -> Unit)? = null,
    onWeighInClick: (() -> Unit)? = null,
    onDrinksClick: (() -> Unit)? = null,
    onResetToToday: (() -> Unit)? = null,
    consumedKcal: Double? = null,
    consumedProtein: Double? = null,
    // Null means "no meal today carries macro data", which is different from 0 g.
    // The pill shows an em dash for null and "0 g" for a real zero.
    consumedCarbs: Double? = null,
    consumedFat: Double? = null,
    deviceSteps: Double? = null,
    estimatedSteps: Int = 0,
    onAdjustStepsClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val effectiveSettings = remember(settings) {
        settings ?: SettingsStorage(context).loadSettings()
    }

    val isTrainingDay = planName != null && !planName.equals("Rest day", ignoreCase = true)
    val deviceStepsVal = deviceSteps ?: daily?.steps?.get(dayKey) ?: 0.0
    val totalSteps = deviceStepsVal + estimatedSteps.toDouble()

    val ticks = remember(daily, effectiveSettings, dayKey, isTrainingDay, consumedKcal, consumedProtein, totalSteps) {
        computeDayCardTicks(
            daily = daily,
            settings = effectiveSettings,
            todayKey = dayKey,
            isTrainingDay = isTrainingDay,
            consumedKcal = consumedKcal,
            consumedProtein = consumedProtein,
            totalSteps = totalSteps
        )
    }

    val isCurrentToday = dayKey == remember { todayKey() }

    // Hero calculations. Eaten now includes alcohol, so the hero agrees with the
    // food total rather than quietly excluding a drink.
    val kcalTarget = effectiveSettings.kcalTarget
    val alcoholKcal = daily?.alcoholKcal?.get(dayKey) ?: 0.0
    val foodKcal = consumedKcal
        ?: ((daily?.caloriesEaten?.get(dayKey) ?: 0.0) - alcoholKcal).coerceAtLeast(0.0)
    val totalKcal = foodKcal + alcoholKcal
    val kcalEaten = totalKcal.roundToInt()
    val isOverGoal = kcalTarget > 0 && kcalEaten > kcalTarget

    // HERO, 2026-11-24: "big n/n calories eaten Vs calorie target" (Sam).
    // Before this the card showed kcal-remaining as the hero AND kcal-eaten in a
    // Calories row underneath, so 1,934 appeared twice and 2,000 twice on one
    // screen. The hero owns calories outright now; the row is gone.
    val heroEatenText = String.format(Locale.US, "%,d", kcalEaten)
    val heroTargetText = if (kcalTarget > 0) " / " + String.format(Locale.US, "%,d", kcalTarget) else ""
    val heroCaptionText = calorieHeroCaption(kcalEaten, kcalTarget)
    // Amber on the caption only, and only when actually over. The big numbers stay
    // foreground: colouring the eaten figure red for a 20 kcal overshoot would make
    // the card shout at Sam over a rounding error.
    val heroCaptionColor = if (isOverGoal) JudgementWarn else TextMuted

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = BorderStroke(0.dp, Color.Transparent)
    ) {
        Column {
            // HERO TOP: eaten / target, the ticked counter, and Back to today
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // Left: "eaten / target" + caption. Weighted so the tick counter
                    // on the right cannot be pushed off a narrow screen by a long
                    // calorie figure - the counter is the thing that would vanish.
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = heroEatenText,
                                fontSize = 56.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextForeground,
                                lineHeight = 56.sp
                            )
                            if (heroTargetText.isNotEmpty()) {
                                Text(
                                    text = heroTargetText,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextMuted,
                                    lineHeight = 28.sp,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                        }
                        Text(
                            text = heroCaptionText,
                            fontSize = 12.sp,
                            color = heroCaptionColor
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
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextForeground
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${ticks.completedCount} of ${ticks.totalTarget}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )
                        }
                        Text(
                            text = "done",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // CONTINUOUS METRIC ROWS (Steps) + MACRO PILLS
                //
                // The Calories row was removed 2026-11-24: the hero now shows eaten /
                // target, so the row was printing the same two numbers again. Sam's
                // complaint was "too many numbers... hard to parse", and the row was
                // literally the hero in lower case.
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // 1. Steps row — keeps its bar. A step count is an accumulation
                    // you finish, so a bar genuinely answers "how far through the day
                    // am I".
                    val stepRaw = totalSteps
                    val stepTarget = effectiveSettings.stepTarget
                    val stepFraction = if (stepRaw > 0 && stepTarget > 0) (stepRaw.toFloat() / stepTarget.toFloat()).coerceIn(0f, 1f) else 0f
                    val stepColor = getBarColor(computeStepsBarColor(stepRaw, stepTarget))
                    DayCardBarRow(
                        icon = { FootstepsIcon(modifier = Modifier.size(14.dp), tint = TextMuted) },
                        label = "Steps",
                        currentText = String.format(Locale.US, "%,d", totalSteps.roundToInt()),
                        targetText = "of %,d".format(Locale.US, stepTarget),
                        progressFraction = stepFraction,
                        fillColor = stepColor,
                        breakdownText = if (estimatedSteps > 0) {
                            "${String.format(Locale.US, "%,d", deviceStepsVal.roundToInt())} device + ${String.format(Locale.US, "%,d", estimatedSteps)} estimated"
                        } else null,
                        trailingAffordance = if (onAdjustStepsClick != null) {
                            {
                                Text(
                                    text = "Adjust steps",
                                    color = AccentTeal,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { onAdjustStepsClick() }
                                )
                            }
                        } else null,
                        onClick = { onMetricClick(MetricId.STEPS) }
                    )

                    // 2. MACRO PILLS (Protein / Carbs / Fat)
                    //
                    // Sam, 2026-11-24: "I like the different coloured pills for the
                    // Protein, Carbs and Fats in BWS." Pills rather than three more
                    // rows because a macro is a category, not a sequence — rows made
                    // the card read as a list of chores. Colour here is a *category*
                    // cue, never a verdict: nothing turns red for being over a macro,
                    // same as the Progress report never judges direction.
                    val protRaw = consumedProtein ?: daily?.protein?.get(dayKey) ?: 0.0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MacroPill(
                            modifier = Modifier.weight(1f),
                            label = "Protein",
                            value = macroPillValue(protRaw, effectiveSettings.proteinTarget),
                            accent = AccentTeal,
                            onClick = { onMetricClick(MetricId.PROTEIN) }
                        )
                        MacroPill(
                            modifier = Modifier.weight(1f),
                            label = "Carbs",
                            value = macroPillValue(consumedCarbs, effectiveSettings.carbTargetG),
                            accent = ChartBlue,
                            onClick = { onMetricClick(MetricId.CALORIES_EATEN) }
                        )
                        MacroPill(
                            modifier = Modifier.weight(1f),
                            label = "Fat",
                            value = macroPillValue(consumedFat, effectiveSettings.fatTargetG),
                            accent = JudgementWarn,
                            onClick = { onMetricClick(MetricId.CALORIES_EATEN) }
                        )
                    }
                }
            }

            HorizontalDivider(color = BorderDefault, thickness = 1.dp)

            // PART 3 — TRAINING ROW
            DayCardTrainingRow(
                isTrainingDay = isTrainingDay,
                planName = planName,
                onClick = onPlanEditorClick
            )

            HorizontalDivider(color = BorderDefault, thickness = 1.dp)

            // PART 4 — CONTEXT CARDS (two side-by-side)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
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
                    icon = { MoonIcon(modifier = Modifier.size(14.dp), tint = TextMuted) },
                    primaryText = sleepPrimary,
                    secondaryText = sleepSecondary,
                    showDot = hasSleepData,
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
                    icon = { ScaleIcon(modifier = Modifier.size(14.dp), tint = TextMuted) },
                    primaryText = weightPrimary,
                    // Up/down against the last weigh-in. Deliberately muted, not red or
                    // green: weight rising is correct in a bulk and wrong in a cut, so
                    // the card reports the direction and lets Sam judge it. Same rule as
                    // the Progress report - direction is not judgement.
                    primarySuffix = weightDeltaLabel(
                        weightDeltaVsPrevious(dayKey, daily?.weight ?: emptyMap())
                    ),
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
                HorizontalDivider(color = BorderDefault, thickness = 1.dp)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDrinksClick?.invoke() ?: onMetricClick(MetricId.ALCOHOL_UNITS) }
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WineIcon(modifier = Modifier.size(16.dp), tint = TextMuted)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DRINKS TODAY",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.5.sp
                        )
                    }

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

/**
 * The line under the calorie hero: eaten, target, and what that leaves.
 *
 * Sam, 2026-11-24: "Maybe a big 'n/n' calories eaten Vs calorie target?" The hero
 * now shows eaten / target, so this caption carries the one derived number that is
 * actually actionable and nothing else. Deliberately three numbers total (eaten,
 * target, remaining) rather than the six the card used to show, where 1,934 and
 * 2,000 each appeared twice.
 */
fun calorieHeroCaption(eaten: Int, target: Int): String {
    if (target <= 0) return "kcal eaten"
    val remaining = target - eaten
    return when {
        remaining > 0 -> "kcal eaten · %,d left".format(Locale.US, remaining)
        remaining == 0 -> "kcal eaten · on target"
        else -> "kcal eaten · %,d over".format(Locale.US, -remaining)
    }
}

/**
 * What a macro pill says. "92 / 160 g" when there is a target, just "92 g" when
 * there is not, and "—" when nothing has been logged with macro data at all.
 *
 * The em dash matters: a card full of meals logged before macro capture existed
 * would otherwise claim 0 g of carbs, which reads as a fact about Sam's diet
 * rather than a fact about the data.
 */
fun macroPillValue(consumed: Double?, target: Int): String {
    val grams = consumed?.roundToInt()
    return when {
        grams == null -> if (target > 0) "— / %,d g".format(Locale.US, target) else "—"
        target > 0 -> "%,d / %,d g".format(Locale.US, grams, target)
        else -> "%,d g".format(Locale.US, grams)
    }
}

/**
 * Change in weight against the most recent earlier weigh-in.
 *
 * "From yday" was the ask, but Sam skips weigh-ins, so taking literally-yesterday
 * would leave the chevron blank on the days he most wants it. The most recent
 * earlier reading is used instead — and null when the difference rounds to zero,
 * so nothing shows a chevron claiming "up 0.0".
 */
fun weightDeltaVsPrevious(dayKey: String, weights: Map<String, Double>): Double? {
    val today = weights[dayKey] ?: return null
    if (today <= 0.0) return null
    val previous = weights.entries
        .filter { it.key < dayKey && it.value > 0.0 }
        .maxByOrNull { it.key }
        ?.value ?: return null
    val delta = today - previous
    // Rounded to the precision the card displays; anything smaller is not a move.
    return if (Math.abs(delta) < 0.05) null else delta
}

/** "▼ 0.4" / "▲ 1.2", or null when there is nothing to report. */
fun weightDeltaLabel(delta: Double?): String? {
    if (delta == null) return null
    val arrow = if (delta > 0) "▲" else "▼"
    return "%s %.1f".format(Locale.US, arrow, Math.abs(delta))
}

@Composable
private fun MacroPill(
    label: String,
    value: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.65f else 1f,
        animationSpec = tween(80),
        label = "macroPillAlpha"
    )

    Column(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.13f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = accent
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
    secondaryFraction: Float = 0f,
    secondaryColor: Color = TextMuted,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    breakdownText: String? = null,
    trailingAffordance: (@Composable () -> Unit)? = null,
    // Steps keep their bar: a step count is an accumulation you finish, so a bar
    // genuinely answers "how far through the day am I". Calories and protein do not
    // — a half-full calorie bar at 6pm reads as a verdict on a target you may not
    // even be chasing, and two of them side by side turn Today into a scoreboard.
    // Those rows show the two numbers and what's left, and nothing else.
    showBar: Boolean = true,
    quietNote: String? = null,
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
                    fontWeight = FontWeight.SemiBold
                )
                if (trailingAffordance != null) {
                    trailingAffordance()
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = currentText,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = targetText,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    color = TextMuted
                )
            }
        }

        if (showBar) {
            Spacer(modifier = Modifier.height(8.dp))

            // 6dp Progress Bar with Track token
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Track)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    val rawPrimary = progressFraction.coerceAtLeast(0f)
                    val rawSecondary = secondaryFraction.coerceAtLeast(0f)
                    val total = rawPrimary + rawSecondary
                    val scale = if (total > 1f) 1f / total else 1f
                    val primary = if (rawPrimary > 0f) maxOf(rawPrimary * scale, 0.02f) else 0f
                    val secondary = if (rawSecondary > 0f) maxOf(rawSecondary * scale, 0.02f) else 0f
                    val rest = (1f - primary - secondary).coerceAtLeast(0f)
                    if (primary > 0f) Box(Modifier.fillMaxHeight().weight(primary).background(fillColor))
                    if (secondary > 0f) Box(Modifier.fillMaxHeight().weight(secondary).background(secondaryColor))
                    if (rest > 0f) Box(Modifier.fillMaxHeight().weight(rest))
                }
            }
        }

        val note = listOfNotNull(quietNote, breakdownText).joinToString("  ·  ").ifEmpty { null }
        if (note != null) {
            Spacer(modifier = Modifier.height(if (showBar) 4.dp else 3.dp))
            Text(
                text = note,
                fontSize = 12.sp,
                color = TextMuted,
                fontWeight = FontWeight.Normal
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
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isTrainingDay) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SecondaryFill),
                    contentAlignment = Alignment.Center
                ) {
                    DashedCircleIcon(
                        modifier = Modifier.size(40.dp),
                        tint = TextMuted.copy(alpha = 0.5f)
                    )
                    DumbbellIcon(
                        modifier = Modifier.size(16.dp),
                        tint = TextMuted
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SecondaryFill),
                    contentAlignment = Alignment.Center
                ) {
                    BedIcon(
                        modifier = Modifier.size(16.dp),
                        tint = TextMuted
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    text = if (isTrainingDay) (planName ?: "Training") else "Rest day",
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isTrainingDay) "Tap to log your session" else "No session planned",
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    color = TextMuted
                )
            }
        }

        if (isTrainingDay) {
            ChevronRightIcon(
                modifier = Modifier.size(16.dp),
                tint = TextMuted.copy(alpha = 0.6f)
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
    // Optional muted suffix after the primary figure, used for the weight up/down
    // chevron ("\u25bc 0.4"). A plain String, not a composable slot: the chevron is
    // only ever one styled run, and a slot here bought nothing but inference risk.
    primarySuffix: String? = null,
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SecondaryFill),
        border = BorderStroke(0.dp, Color.Transparent)
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

            Column {
                icon()
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = primaryText,
                        fontSize = if (primaryText.length > 8) 14.sp else 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                    if (primarySuffix != null) {
                        Text(
                            text = primarySuffix,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMuted,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 4.dp, bottom = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = secondaryText,
                    fontSize = 12.sp,
                    lineHeight = 13.sp,
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
            .clip(RoundedCornerShape(12.dp))
            .background(SecondaryFill)
            .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(12.dp))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextForeground
            )
            Text(
                text = label.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                letterSpacing = 1.sp
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
