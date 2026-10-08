package com.scatterbrain.scatterfit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.core.dayBefore
import com.scatterbrain.scatterfit.ui.theme.AppCardSurface
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.BorderInput
import com.scatterbrain.scatterfit.ui.theme.BrandPrimaryLime
import com.scatterbrain.scatterfit.ui.theme.JudgementBad
import com.scatterbrain.scatterfit.ui.theme.JudgementGood
import com.scatterbrain.scatterfit.ui.theme.JudgementWarn
import com.scatterbrain.scatterfit.ui.theme.MutedFill
import com.scatterbrain.scatterfit.ui.theme.OnPrimaryText
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted

@Composable
fun MyDayScreen(
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    // Tasks completion state
    var weeklyCheckInCompleted by remember { mutableStateOf(false) }
    var dailyCalorieTarget by remember { mutableIntStateOf(1800) }
    var checkInCompletedValue by remember { mutableStateOf("Target updated (1,800 kcal)") }

    var weighInCompleted by remember { mutableStateOf(false) }
    var weighInData by remember { mutableStateOf("") }

    var foodLogCompleted by remember { mutableStateOf(false) }
    var foodLogData by remember { mutableStateOf("345 / 1800 kcal") }

    var trainCompleted by remember { mutableStateOf(false) }
    var trainData by remember { mutableStateOf("HIIT 20 minutes") }

    // Steps pedometer state (reading emulated steps source)
    var stepsToday by remember { mutableIntStateOf(8420) }
    val dailyStepTarget = 10_000

    var showWeighInDialog by remember { mutableStateOf(false) }
    var showWeeklyCheckInDialog by remember { mutableStateOf(false) }

    if (showWeighInDialog) {
        WeighInDialog(
            onDismiss = { showWeighInDialog = false },
            onSubmitWeight = { weight, bodyFat ->
                weighInData = "${weight}kg • ${bodyFat}% body fat"
                weighInCompleted = true
            }
        )
    }

    if (showWeeklyCheckInDialog) {
        WeeklyCheckInDialog(
            onDismiss = { showWeeklyCheckInDialog = false },
            onCompleteCheckIn = { newTarget ->
                dailyCalorieTarget = newTarget
                checkInCompletedValue = "Target: %,d kcal".format(newTarget)
                foodLogData = "345 / %,d kcal".format(newTarget)
                weeklyCheckInCompleted = true
            },
            currentTargetKcal = dailyCalorieTarget,
            suggestedTargetKcal = dailyCalorieTarget
        )
    }

    // Day key calendar navigation (using core/DayKeys contract)
    val today = remember { com.scatterbrain.scatterfit.core.todayKey() }
    var currentDayKey by remember { mutableStateOf(today) }

    // Day-keyed persistent drink tracking ViewModel
    val context = androidx.compose.ui.platform.LocalContext.current
    val drinkViewModel = remember {
        DrinkTrackerViewModel(
            storage = DrinkStorage(context),
            initialDayKey = currentDayKey
        )
    }

    LaunchedEffect(currentDayKey) {
        drinkViewModel.setDayKey(currentDayKey)
    }

    val drinkState by drinkViewModel.dailyState.collectAsState()

    LaunchedEffect(Unit) {
        if (drinkState.libraryDrinks.isEmpty()) {
            drinkViewModel.loadLibraryFromAssets(context)
        }
    }
    var showDrinkLoggerScreen by remember { mutableStateOf(false) }
    var activeMetricScreen by remember { mutableStateOf<String?>(null) }

    when (activeMetricScreen) {
        "steps" -> {
            com.scatterbrain.scatterfit.ui.MetricPages.StepsScreen(
                onBackClick = { activeMetricScreen = null }
            )
            return
        }
        "sleep" -> {
            com.scatterbrain.scatterfit.ui.MetricPages.SleepScreen(
                onBackClick = { activeMetricScreen = null }
            )
            return
        }
        "weight" -> {
            com.scatterbrain.scatterfit.ui.MetricPages.WeightScreen(
                onBackClick = { activeMetricScreen = null }
            )
            return
        }
    }

    if (showDrinkLoggerScreen) {
        DrinkLoggerScreen(
            viewModel = drinkViewModel,
            onBackClick = { showDrinkLoggerScreen = false }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        val isFutureDay = currentDayKey > today
        val momentumSource: MomentumSource = remember { TodayFacadeMomentumSource() }

        val isTodayLogged = foodLogCompleted || weighInCompleted || trainCompleted || weeklyCheckInCompleted || drinkState.loggedDrinks.isNotEmpty()
        val streakInfo = remember(momentumSource, today, isTodayLogged) {
            momentumSource.computeStreak(today = today, todayLoggedOverride = isTodayLogged)
        }
        val goalProjection = remember(momentumSource, currentDayKey) {
            momentumSource.computeGoalProjection(currentDayKey)
        }

        // 1. Day Navigation Bar: Calendar Card with merged gear, nav header, date range, streak & logged dots
        DayNavigationBar(
            selectedDayKey = currentDayKey,
            onDaySelected = { selectedKey ->
                currentDayKey = selectedKey
            },
            momentumSource = momentumSource,
            streakInfo = streakInfo,
            onSettingsClick = onSettingsClick,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Today Pulse Card (Consolidates Momentum + Readiness with integrated Goal Header strip)
        TodayPulseCard(
            goalName = goalProjection.goalName,
            projectedTargetText = goalProjection.projectionText,
            activeDayKey = currentDayKey,
            momentumSource = momentumSource,
            sleepHours = 8,
            sleepMinutes = 13,
            rhrText = "RHR warming up",
            onMomentumClick = { /* Navigate to Momentum */ },
            onTrendsClick = { activeMetricScreen = "weight" },
            onSleepClick = { activeMetricScreen = "sleep" },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3. "Today" To-Do Checklist Card (Weekly Check-in & Steps integrated inside!)
        TodayTasksCard(
            weeklyCheckInCompleted = weeklyCheckInCompleted,
            weeklyCheckInCompletedValue = checkInCompletedValue,
            onWeeklyCheckInClick = {
                if (!isFutureDay) {
                    if (!weeklyCheckInCompleted) showWeeklyCheckInDialog = true else weeklyCheckInCompleted = false
                }
            },
            weighInCompleted = weighInCompleted,
            weighInData = weighInData,
            onWeighInClick = {
                if (!isFutureDay) {
                    if (!weighInCompleted) showWeighInDialog = true else weighInCompleted = false
                }
            },
            foodLogCompleted = foodLogCompleted,
            foodLogData = foodLogData,
            onFoodLogClick = { if (!isFutureDay) foodLogCompleted = !foodLogCompleted },
            trainCompleted = trainCompleted,
            trainData = trainData,
            onTrainClick = { if (!isFutureDay) trainCompleted = !trainCompleted },
            stepsToday = if (isFutureDay) 0 else stepsToday,
            dailyStepTarget = dailyStepTarget,
            isFutureDay = isFutureDay,
            subtitleText = "Log anything today to start a streak",
            onStepsClick = { activeMetricScreen = "steps" },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Alcohol Tracker Card at the bottom of the home page (Wired to DrinkLoggerScreen)
        val weeklyUnits = remember(drinkState) { drinkViewModel.getWeeklyUnits() }
        val weeklyDrinkDays = remember(drinkState) { drinkViewModel.getWeeklyDrinkDaysCount() }
        val weeklySpent = remember(drinkState) { drinkViewModel.getWeeklySpent() }

        val quickAddDrinks = remember(drinkState.libraryDrinks, drinkState.loggedDrinks) {
            drinkViewModel.getQuickAddDrinks(6)
        }

        AlcoholTrackerCard(
            totalSpent = String.format(java.util.Locale.US, "£%.2f", weeklySpent),
            alcoholFreeDays = (7 - weeklyDrinkDays).coerceAtLeast(0),
            unitsConsumed = weeklyUnits.toInt(),
            targetDrinkDays = 2,
            quickAddDrinks = quickAddDrinks,
            onQuickAdd = { drink ->
                if (!isFutureDay) {
                    drinkViewModel.logDrink(drink)
                }
            },
            isFutureDay = isFutureDay,
            onClick = { showDrinkLoggerScreen = true },
            modifier = Modifier.fillMaxWidth()
        )

        // Reserving bottom clearance so the last card and its content completely clear the bottom nav bar
        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun AlcoholTrackerCard(
    modifier: Modifier = Modifier,
    totalSpent: String = "£0.00",
    alcoholFreeDays: Int = 1,
    unitsConsumed: Int = 0,
    targetDrinkDays: Int = 2,
    quickAddDrinks: List<DrinkReference> = emptyList(),
    onQuickAdd: (DrinkReference) -> Unit = {},
    isFutureDay: Boolean = false,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppCardSurface),
        border = BorderStroke(1.dp, BorderDefault)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Row 1 (Header): ALCOHOL + log on left, compact summary on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ALCOHOL",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(BrandPrimaryLime.copy(alpha = 0.16f))
                            .border(BorderStroke(1.dp, BrandPrimaryLime.copy(alpha = 0.40f)), RoundedCornerShape(12.dp))
                            .clickable { onClick() }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ log",
                            color = BrandPrimaryLime,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Compact summary replacing 3-col grid and "Total spent": "0 units · 7 AF days (target 2) · £0.00"
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = TextForeground, fontWeight = FontWeight.SemiBold)) {
                            append("$unitsConsumed")
                        }
                        withStyle(SpanStyle(color = TextMuted)) {
                            append(" units · ")
                        }
                        withStyle(SpanStyle(color = TextForeground, fontWeight = FontWeight.SemiBold)) {
                            append("$alcoholFreeDays")
                        }
                        withStyle(SpanStyle(color = TextMuted)) {
                            append(" AF days (target $targetDrinkDays) · ")
                        }
                        withStyle(SpanStyle(color = TextForeground, fontWeight = FontWeight.SemiBold)) {
                            append(totalSpent)
                        }
                    },
                    fontSize = 10.sp
                )
            }

            // Row 2: Quick-Add Chips (single-line with ellipsis at ~120dp, never two text lines)
            if (quickAddDrinks.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickAddDrinks, key = { it.id.ifBlank { it.name + it.serving } }) { drink ->
                        val servingPart = if (drink.serving.isNotBlank() && !drink.name.contains(drink.serving, ignoreCase = true)) {
                            " " + drink.serving.replace(" Bottle", "").replace(" Can", "").replace(" Glass", "")
                        } else ""
                        val chipText = "${drink.name}$servingPart"

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(SecondaryFill)
                                .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(16.dp))
                                .clickable(enabled = !isFutureDay) { onQuickAdd(drink) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = chipText,
                                    color = if (isFutureDay) TextMuted else TextForeground,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 120.dp)
                                )
                                Text(
                                    text = "+",
                                    color = if (isFutureDay) TextMuted else BrandPrimaryLime,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                // Guard: Fresh install / empty favourites & recents
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(SecondaryFill)
                        .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(16.dp))
                        .clickable { onClick() }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Log your first drink →",
                        color = BrandPrimaryLime,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Navigation: subtle "Track all →" hint row at card's bottom edge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick() },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Track all →",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Shared helper for the "bold-carries-data" grammar rule:
 * Builds an AnnotatedString where data phrases/keywords are highlighted with TextForeground + SemiBold,
 * and surrounding narrative is styled with TextMuted + Normal.
 */
fun buildBoldCarriesDataString(
    parts: List<Pair<String, Boolean>>
): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        parts.forEach { (text, isBoldData) ->
            if (isBoldData) {
                withStyle(SpanStyle(color = TextForeground, fontWeight = FontWeight.SemiBold)) {
                    append(text)
                }
            } else {
                withStyle(SpanStyle(color = TextMuted, fontWeight = FontWeight.Normal)) {
                    append(text)
                }
            }
        }
    }
}

fun buildMomentumWatchSentence(caloriesVsAverage: Int): androidx.compose.ui.text.AnnotatedString {
    val sign = if (caloriesVsAverage >= 0) "+$caloriesVsAverage" else "$caloriesVsAverage"
    return buildBoldCarriesDataString(
        listOf(
            Pair("$sign Calories on 7 Day Average. ", true),
            Pair("That's the metric to watch today.", false)
        )
    )
}

fun buildReadinessRecommendationSentence(stateWord: String, targetZone: String): androidx.compose.ui.text.AnnotatedString {
    return buildBoldCarriesDataString(
        listOf(
            Pair("Your body is ", false),
            Pair(stateWord.uppercase(), true),
            Pair(". Push for your ", false),
            Pair(targetZone, true),
            Pair(" targets today.", false)
        )
    )
}

/**
 * Two-line delta block right of the big score number:
 * Line 1: "+12 points" / "−4 points" (11sp SemiBold numeric part with attached sign in Judgement tone, "points" in TextMuted)
 * Line 2: "from yesterday" (9sp TextMuted)
 * 0dp gap (stacked tight, line-height alone separates)
 */
@Composable
fun DeltaPointsBlock(
    delta: Int,
    comparisonText: String = "from yesterday",
    modifier: Modifier = Modifier
) {
    val sign = if (delta >= 0) "+" else "−"
    val absVal = kotlin.math.abs(delta)
    val deltaColor = if (delta >= 0) JudgementGood else JudgementBad

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "$sign$absVal points",
            color = deltaColor,
            fontSize = 12.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            maxLines = 1,
            softWrap = false
        )
        Text(
            text = comparisonText,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            color = TextMuted,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Redesigned "Today pulse" card matching the web mockup:
 * - Integrated top header strip: "DAY <N>" | FAT LOSS | "TARGET •" + pill containing "10.08.26"
 * - Hairline divider
 * - LEFT = MOMENTUM (10sp muted) + High pill, 84 +7 points / dynamic comparison date, watch sentence
 * - RIGHT = READINESS (10sp muted) + Primed pill, 70 +12 points / dynamic comparison date, recommendation
 * - Hairline divider
 * - Bottom strip: centered PulseIcon + 8h 13m sleep last night · RHR warming up
 */
@Composable
fun TodayPulseCard(
    goalName: String = "FAT LOSS",
    projectedTargetText: String = "TARGET • 10.08.26",
    activeDayKey: String = remember { todayKey() },
    momentumSource: MomentumSource = remember { TodayFacadeMomentumSource() },
    sleepHours: Int = 8,
    sleepMinutes: Int = 13,
    rhrText: String = "RHR warming up",
    onMomentumClick: () -> Unit = {},
    onTrendsClick: () -> Unit = {},
    onSleepClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val yesterdayKey = remember(activeDayKey) { dayBefore(activeDayKey) }

    // Dynamic comparison date ("from EEE d MMM", e.g. "from Sun 04 Oct")
    val comparisonDate = remember(activeDayKey) {
        try {
            LocalDate.parse(dayBefore(activeDayKey))
        } catch (_: Exception) {
            LocalDate.now().minusDays(1)
        }
    }
    val comparisonDateText = remember(comparisonDate) {
        "from ${comparisonDate.format(DateTimeFormatter.ofPattern("EEE dd MMM", java.util.Locale.US))}"
    }

    val goalProjection = remember(activeDayKey, momentumSource) {
        momentumSource.computeGoalProjection(activeDayKey)
    }
    val dayNText = goalProjection.elapsedText

    // Target date value for the pill (e.g. "10.08.26")
    val targetDateValue = remember(projectedTargetText) {
        if (projectedTargetText.contains("•")) {
            val parts = projectedTargetText.split("•").map { it.trim() }
            parts.getOrElse(1) { "" }.ifBlank { "" }
        } else {
            projectedTargetText
        }
    }

    // Momentum data
    val momentumTodayRaw = momentumSource.scoreForDay(activeDayKey)
    val momentumYesterdayRaw = momentumSource.scoreForDay(yesterdayKey)
    val momentumScoreInt = momentumTodayRaw?.let { kotlin.math.round(it).toInt() } ?: 0
    val momentumDelta = if (momentumTodayRaw != null && momentumYesterdayRaw != null) {
        momentumScoreInt - kotlin.math.round(momentumYesterdayRaw).toInt()
    } else null
    val calVsAvg = momentumSource.caloriesVsAverageForDay(activeDayKey) ?: 0
    val momentumColor = scoreToJudgementColor(momentumTodayRaw)

    val (momentumVerdictText, momentumVerdictColor) = when {
        momentumTodayRaw == null && activeDayKey > todayKey() -> Pair("No data", TextMuted)
        momentumScoreInt < 40 -> Pair("Low", JudgementBad)
        momentumScoreInt < 70 -> Pair("Steady", JudgementWarn)
        else -> Pair("High", JudgementGood)
    }

    // Readiness data
    val readinessScoreInt = momentumSource.readinessScoreForDay(activeDayKey)
    val readinessDelta = momentumSource.readinessDeltaForDay(activeDayKey)
    val readinessStateWord = momentumSource.readinessStateForDay(activeDayKey)
    val readinessTarget = momentumSource.readinessTargetForDay(activeDayKey)
    val readinessColor = when (readinessStateWord.uppercase()) {
        "PRIMED" -> JudgementGood
        "NORMAL" -> JudgementGood
        "COMPROMISED" -> JudgementWarn
        else -> JudgementGood
    }
    val readinessBadge = readinessStateWord.lowercase().replaceFirstChar { it.uppercase() }

    // Whole numbers rounded for sleep
    val formattedSleep = "${sleepHours}h ${sleepMinutes}m"

    val momentumSentence = remember(calVsAvg) { buildMomentumWatchSentence(calVsAvg) }
    val readinessSentence = remember(readinessStateWord, readinessTarget) {
        buildReadinessRecommendationSentence(readinessStateWord, readinessTarget)
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppCardSurface),
        border = BorderStroke(1.dp, BorderDefault)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Top Header Strip: Left = "DAY <N>" plain label | Centre = "FAT LOSS" | Right = "TARGET •" + pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Left slot: "DAY <N>" single line, plain label, no pill
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.TopStart
                ) {
                    Text(
                        text = dayNText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextMuted,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Centre slot: goal title horizontally centred, vertically aligned with first line
                Box(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = goalName.uppercase(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextForeground,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // Right slot: "TARGET •" on line 1, pill containing date on line 2 (both right-aligned)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "TARGET •",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextMuted,
                        letterSpacing = 0.5.sp,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(BrandPrimaryLime.copy(alpha = 0.16f))
                            .border(
                                BorderStroke(1.dp, BrandPrimaryLime.copy(alpha = 0.40f)),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = targetDateValue,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandPrimaryLime
                        )
                    }
                }
            }

            // Hairline divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MutedFill)
            )

            // 2. Main Two Halves (MOMENTUM | READINESS)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                // LEFT = Momentum
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onMomentumClick() }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Header row: MOMENTUM + Verdict pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MOMENTUM",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        )

                        // Pill top-right
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(momentumVerdictColor.copy(alpha = 0.16f))
                                .border(
                                    BorderStroke(1.dp, momentumVerdictColor.copy(alpha = 0.40f)),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = momentumVerdictText,
                                color = momentumVerdictColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Score row: Big Number + Delta block
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "$momentumScoreInt",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = momentumColor
                        )

                        if (momentumDelta != null) {
                            DeltaPointsBlock(delta = momentumDelta, comparisonText = comparisonDateText)
                        }
                    }

                    // Bold-carries-data watch sentence: 10sp, lineHeight 13sp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 28.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        Text(
                            text = momentumSentence,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Vertical Divider: MutedFill 1dp
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MutedFill)
                )

                // RIGHT = Readiness
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTrendsClick() }
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Header row: READINESS + State pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "READINESS",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        )

                        // State pill top-right
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(readinessColor.copy(alpha = 0.16f))
                                .border(
                                    BorderStroke(1.dp, readinessColor.copy(alpha = 0.40f)),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = readinessBadge,
                                color = readinessColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Score row: Big Number + Delta block
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "$readinessScoreInt",
                            fontSize = 38.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = readinessColor
                        )

                        if (readinessDelta != null) {
                            DeltaPointsBlock(delta = readinessDelta, comparisonText = comparisonDateText)
                        }
                    }

                    // Bold-carries-data recommendation sentence: 10sp, lineHeight 13sp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 28.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        Text(
                            text = readinessSentence,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Divider before bottom strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MutedFill)
            )

            // 3. Bottom strip inside card: horizontally centered
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSleepClick() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                PulseIcon(
                    modifier = Modifier.size(14.dp),
                    tint = TextMuted,
                    strokeWidthDp = 1.5.dp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$formattedSleep sleep last night · $rhrText",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun TodayTasksCard(
    weeklyCheckInCompleted: Boolean,
    onWeeklyCheckInClick: () -> Unit,
    weighInCompleted: Boolean,
    weighInData: String,
    onWeighInClick: () -> Unit,
    foodLogCompleted: Boolean,
    foodLogData: String,
    onFoodLogClick: () -> Unit,
    trainCompleted: Boolean,
    trainData: String,
    onTrainClick: () -> Unit,
    modifier: Modifier = Modifier,
    weeklyCheckInCompletedValue: String = "Target updated (1,800 kcal)",
    stepsToday: Int = 8420,
    dailyStepTarget: Int = 10_000,
    isFutureDay: Boolean = false,
    subtitleText: String? = null,
    onStepsClick: () -> Unit = {}
) {
    Card(
        modifier = modifier,
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
                    text = "Today",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextForeground
                )
                val headerSubtitle = subtitleText ?: if (isFutureDay) "Upcoming day · Logging disabled" else "Log anything today to start a streak"
                Text(
                    text = headerSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFutureDay) BrandPrimaryLime else TextMuted
                )
            }

            // Task 1: Weekly check-in (integrated directly inside To-Do list!)
            MicroTaskRow(
                title = "Weekly check-in",
                hint = if (isFutureDay) "Upcoming" else "Review last week & adjust targets",
                isCompleted = if (isFutureDay) false else weeklyCheckInCompleted,
                completedValue = weeklyCheckInCompletedValue,
                onClick = onWeeklyCheckInClick
            )

            // Task 2: Weigh in
            MicroTaskRow(
                title = "Weigh in",
                hint = if (isFutureDay) "Upcoming" else "Jump on the scales",
                isCompleted = if (isFutureDay) false else weighInCompleted,
                completedValue = weighInData,
                onClick = onWeighInClick
            )

            // Task 3: Log your food
            MicroTaskRow(
                title = "Log your food",
                hint = if (isFutureDay) "Upcoming" else "Fastest win of the day",
                isCompleted = if (isFutureDay) false else foodLogCompleted,
                completedValue = foodLogData,
                onClick = onFoodLogClick
            )

            // Task 4: Train
            MicroTaskRow(
                title = trainData,
                hint = if (isFutureDay) "Upcoming" else "Even a walk counts",
                isCompleted = if (isFutureDay) false else trainCompleted,
                completedValue = "Workout completed",
                onClick = onTrainClick
            )

            // Task 5: Steps pedometer item with horizontal progress bar & pace label
            StepsTaskRow(
                steps = if (isFutureDay) 0 else stepsToday,
                target = dailyStepTarget,
                isFutureDay = isFutureDay,
                onClick = onStepsClick
            )
        }
    }
}

/**
 * Steps pedometer item for Today checklist:
 * - Horizontal progress bar under "Steps" row title: track = MutedFill, fill = BrandPrimaryLime, height ~6dp, rounded-full
 * - Progress bar does not colour by judgement — it fills with lime as steps accumulate, grey when steps = 0
 * - Right-aligned: percentage complete (10sp semibold)
 * - Next to percentage: status label coloured by PACE using dailyPaceScore semantics:
 *   Projected >= target -> JudgementGood "On pace"
 *   Projected 80-99% of target -> JudgementWarn "Behind pace"
 *   Projected < 80% -> JudgementBad "Well behind"
 *   Before 06:00 or with no steps -> TextMuted "—"
 */
@Composable
fun StepsTaskRow(
    steps: Int,
    target: Int = 10_000,
    isFutureDay: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val isCompleted = steps >= target && !isFutureDay
    val borderColor = if (isCompleted) JudgementGood else BorderDefault
    val containerBg = if (isCompleted) JudgementGood.copy(alpha = 0.12f) else MutedFill

    val progress = if (target > 0 && !isFutureDay) (steps.toFloat() / target.toFloat()).coerceIn(0f, 1f) else 0f
    val percent = if (target > 0 && !isFutureDay) ((steps.toDouble() / target.toDouble()) * 100).toInt() else 0

    val cal = remember { java.util.Calendar.getInstance() }
    val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
    val minute = cal.get(java.util.Calendar.MINUTE)
    val elapsedDay = ((hour * 60.0 + minute) / 1440.0).coerceIn(0.01, 1.0)

    val paceScore = remember(steps, target, isFutureDay, elapsedDay) {
        if (isFutureDay) null
        else com.scatterbrain.scatterfit.core.dailyPaceScore(
            value = steps.toDouble(),
            target = target.toDouble(),
            elapsedDay = elapsedDay,
            neutralBelow = 1.0,
            mode = com.scatterbrain.scatterfit.core.PaceMode.REACH
        )
    }

    val (paceLabel, paceColor) = when {
        isFutureDay -> Pair("Upcoming", TextMuted)
        paceScore == null -> Pair("—", TextMuted)
        paceScore >= 100.0 -> Pair("On pace", Color(com.scatterbrain.scatterfit.core.scoreToColorArgb(paceScore)))
        paceScore >= 80.0 -> Pair("Behind pace", Color(com.scatterbrain.scatterfit.core.scoreToColorArgb(paceScore)))
        else -> Pair("Well behind", Color(com.scatterbrain.scatterfit.core.scoreToColorArgb(paceScore)))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(containerBg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Left has circle + "Steps" title, Right has pace label + percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Circle indicator (matching MicroTaskRow)
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (isCompleted) JudgementGood else Color.Transparent)
                            .border(1.5.dp, if (isCompleted) JudgementGood else TextMuted, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Canvas(modifier = Modifier.size(11.dp)) {
                                val w = size.width
                                val h = size.height
                                val path = Path().apply {
                                    moveTo(w * 0.15f, h * 0.50f)
                                    lineTo(w * 0.42f, h * 0.78f)
                                    lineTo(w * 0.85f, h * 0.22f)
                                }
                                drawPath(
                                    path = path,
                                    color = Color.Black,
                                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "Steps",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextForeground
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "%,d / %,d".format(steps, target),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }

                // Right side: status label coloured by PACE next to percentage complete (10sp semibold)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = paceLabel,
                        color = paceColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "$percent%",
                        color = TextForeground,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Horizontal progress bar under "Steps" row title:
            // track = MutedFill, fill = BrandPrimaryLime, height ~6dp, rounded-full
            // Do NOT colour the bar by judgement — the bar is progress (lime fills as steps accumulate); grey only when steps = 0 (no data).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF26282B))
            ) {
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = progress)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(BrandPrimaryLime)
                    )
                }
            }
        }
    }
}

@Composable
fun MicroTaskRow(
    title: String,
    hint: String,
    isCompleted: Boolean,
    completedValue: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isCompleted) JudgementGood else BorderDefault
    val containerBg = if (isCompleted) JudgementGood.copy(alpha = 0.12f) else MutedFill

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(containerBg)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Circle indicator
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isCompleted) JudgementGood else Color.Transparent)
                        .border(1.5.dp, if (isCompleted) JudgementGood else TextMuted, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Canvas(modifier = Modifier.size(11.dp)) {
                            val w = size.width
                            val h = size.height
                            val path = Path().apply {
                                moveTo(w * 0.15f, h * 0.50f)
                                lineTo(w * 0.42f, h * 0.78f)
                                lineTo(w * 0.85f, h * 0.22f)
                            }
                            drawPath(
                                path = path,
                                color = Color.Black,
                                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextForeground
                )
            }

            Text(
                text = if (isCompleted) completedValue else hint,
                style = MaterialTheme.typography.bodySmall,
                color = if (isCompleted) JudgementGood else TextMuted,
                fontWeight = if (isCompleted) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun WeighInDialog(
    onDismiss: () -> Unit,
    onSubmitWeight: (weight: String, bodyFat: String) -> Unit
) {
    var weightInput by remember { mutableStateOf("") }
    var bodyFatInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppCardSurface,
        title = {
            Text("Weigh In", color = TextForeground, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it },
                    label = { Text("Weight (kg)", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextForeground,
                        unfocusedTextColor = TextForeground,
                        focusedBorderColor = BrandPrimaryLime,
                        unfocusedBorderColor = BorderDefault
                    )
                )
                OutlinedTextField(
                    value = bodyFatInput,
                    onValueChange = { bodyFatInput = it },
                    label = { Text("Body Fat % (optional)", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextForeground,
                        unfocusedTextColor = TextForeground,
                        focusedBorderColor = BrandPrimaryLime,
                        unfocusedBorderColor = BorderDefault
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmitWeight(weightInput, bodyFatInput)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimaryLime, contentColor = Color.Black)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

enum class CheckInJudgement(val label: String, val color: Color) {
    GOOD("Good", JudgementGood),
    OKAY("Okay", JudgementWarn),
    POOR("Poor", JudgementBad)
}

@Composable
fun CheckInJudgementPill(
    judgement: CheckInJudgement,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(judgement.color.copy(alpha = 0.15f))
            .border(BorderStroke(1.dp, judgement.color.copy(alpha = 0.35f)), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = judgement.label,
            color = judgement.color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun CheckInMetricRow(
    label: String,
    value: String,
    judgement: CheckInJudgement,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Text(
                text = "•",
                color = TextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$label: $value",
                color = TextForeground,
                fontSize = 14.sp
            )
        }

        CheckInJudgementPill(judgement = judgement)
    }
}

@Composable
fun WeeklyCheckInDialog(
    onDismiss: () -> Unit,
    onCompleteCheckIn: (newTargetKcal: Int) -> Unit,
    dateRangeText: String = run {
        val prevKeys = com.scatterbrain.scatterfit.core.previousWeekKeys("monday")
        if (prevKeys.isNotEmpty()) {
            "Reviewing last week (${prevKeys.first()} – ${prevKeys.last()}):"
        } else {
            "Reviewing last week:"
        }
    },
    weighInsCount: Int = 0,
    foodLogsCount: Int = 5,
    trainingDaysCompleted: Int = 0,
    trainingDaysTarget: Int = 4,
    avgIntakeKcal: Int = 2362,
    currentTargetKcal: Int = 1800,
    suggestedTargetKcal: Int = 1800,
    suggestedActionOverride: String? = null
) {
    var isCustomTargetMode by remember { mutableStateOf(false) }
    var customTargetInput by remember { mutableStateOf(currentTargetKcal.toString()) }

    // Judgement evaluation:
    // 1. Weigh-ins (out of 7): 5-7 Good, 3-4 Okay, 0-2 Poor
    val weighInJudgement = when {
        weighInsCount >= 5 -> CheckInJudgement.GOOD
        weighInsCount in 3..4 -> CheckInJudgement.OKAY
        else -> CheckInJudgement.POOR
    }

    // 2. Food logging (out of 7): 5-7 Good, 3-4 Okay, 0-2 Poor
    val foodLogJudgement = when {
        foodLogsCount >= 5 -> CheckInJudgement.GOOD
        foodLogsCount in 3..4 -> CheckInJudgement.OKAY
        else -> CheckInJudgement.POOR
    }

    // 3. Training days (percentage basis from workout plan)
    val trainingRatio = if (trainingDaysTarget > 0) trainingDaysCompleted.toFloat() / trainingDaysTarget.toFloat() else 0f
    val trainingJudgement = when {
        trainingRatio >= 0.70f -> CheckInJudgement.GOOD
        trainingRatio >= 0.40f -> CheckInJudgement.OKAY
        else -> CheckInJudgement.POOR
    }

    // 4. kCal intake: 7 Day Avg intake (No calorie burn)
    // +/- 5% Good, +/- 6-10% Okay, +/- 11% and over Poor
    val intakeDiffRatio = if (currentTargetKcal > 0) kotlin.math.abs(avgIntakeKcal - currentTargetKcal).toFloat() / currentTargetKcal.toFloat() else 0f
    val intakeJudgement = when {
        intakeDiffRatio <= 0.055f -> CheckInJudgement.GOOD
        intakeDiffRatio <= 0.105f -> CheckInJudgement.OKAY
        else -> CheckInJudgement.POOR
    }

    // Decision: "Suggested action: Maintain/increase/decrease target at 'X'kcals/day"
    val suggestedAction = suggestedActionOverride ?: when {
        suggestedTargetKcal > currentTargetKcal -> "Increase"
        suggestedTargetKcal < currentTargetKcal -> "Decrease"
        else -> "Maintain"
    }
    val suggestedActionText = "Suggested action: $suggestedAction target at %,d kcals/day".format(suggestedTargetKcal)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppCardSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Weekly Check-in Review",
                    color = TextForeground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Text("✕", color = TextMuted, fontSize = 16.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(dateRangeText, color = TextMuted, fontSize = 13.sp)

                // 1. Weigh-ins
                CheckInMetricRow(
                    label = "Weigh-ins",
                    value = "$weighInsCount/7",
                    judgement = weighInJudgement
                )

                // 2. Food logs
                CheckInMetricRow(
                    label = "Food logs",
                    value = "$foodLogsCount/7",
                    judgement = foodLogJudgement
                )

                // 3. Training days
                CheckInMetricRow(
                    label = "Training days",
                    value = "$trainingDaysCompleted/$trainingDaysTarget",
                    judgement = trainingJudgement
                )

                // 4. Avg intake (No calorie burn)
                CheckInMetricRow(
                    label = "Avg intake",
                    value = "%,d kcal".format(avgIntakeKcal),
                    judgement = intakeJudgement
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Decision action banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SecondaryFill)
                        .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = suggestedActionText,
                        color = BrandPrimaryLime,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                }
            }
        },
        confirmButton = {
            if (!isCustomTargetMode) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onCompleteCheckIn(suggestedTargetKcal)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandPrimaryLime,
                            contentColor = OnPrimaryText
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Accept", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            onCompleteCheckIn(currentTargetKcal)
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextForeground
                        ),
                        border = BorderStroke(1.dp, BorderDefault),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Keep Current Target", fontWeight = FontWeight.SemiBold)
                    }

                    TextButton(
                        onClick = { isCustomTargetMode = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Add New Target", color = BrandPrimaryLime, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customTargetInput,
                        onValueChange = { input ->
                            customTargetInput = input.filter { it.isDigit() }
                        },
                        label = { Text("New Target (kcal/day)", color = TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextForeground,
                            unfocusedTextColor = TextForeground,
                            focusedBorderColor = BrandPrimaryLime,
                            unfocusedBorderColor = BorderInput,
                            cursorColor = BrandPrimaryLime
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = { isCustomTargetMode = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel", color = TextMuted)
                        }
                        Button(
                            onClick = {
                                val parsed = customTargetInput.toIntOrNull() ?: currentTargetKcal
                                onCompleteCheckIn(parsed)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandPrimaryLime,
                                contentColor = OnPrimaryText
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Save Target", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        dismissButton = null
    )
}

@Composable
fun WeeklyCheckInDialog(
    onDismiss: () -> Unit,
    onCompleteCheckIn: () -> Unit
) {
    WeeklyCheckInDialog(
        onDismiss = onDismiss,
        onCompleteCheckIn = { _ -> onCompleteCheckIn() }
    )
}

@Preview(showBackground = true)
@Composable
fun MyDayScreenPreview() {
    ScatterFitTheme {
        MyDayScreen()
    }
}
