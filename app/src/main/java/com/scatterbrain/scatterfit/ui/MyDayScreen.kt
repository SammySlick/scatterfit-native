package com.scatterbrain.scatterfit.ui

import android.annotation.SuppressLint
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.PlatformTextStyle
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
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.core.dayBefore
import com.scatterbrain.scatterfit.data.Daily
import com.scatterbrain.scatterfit.data.TaskChecklist
import com.scatterbrain.scatterfit.data.Zones
import com.scatterbrain.scatterfit.sync.SyncHub
import com.scatterbrain.scatterfit.ui.theme.AccentHot
import com.scatterbrain.scatterfit.ui.theme.AccentTeal
import com.scatterbrain.scatterfit.ui.theme.momentumBand
import com.scatterbrain.scatterfit.ui.theme.readinessBand
import com.scatterbrain.scatterfit.ui.theme.AppCardSurface
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.BorderInput
import com.scatterbrain.scatterfit.ui.theme.BrandPrimaryLime
import com.scatterbrain.scatterfit.ui.theme.JudgementBad
import com.scatterbrain.scatterfit.ui.theme.JudgementDestructive
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
    val dailyStepTarget = 10_000

    var showWeighInDialog by remember { mutableStateOf(false) }
    var showWeeklyCheckInDialog by remember { mutableStateOf(false) }

    // Train stays manual-only for now (needs EXERCISE_SESSION reads or workout logging)
    var trainCompleted by remember { mutableStateOf(false) }
    var trainData by remember { mutableStateOf("HIIT 20 minutes") }

    // Weigh-in dialog: once it writes a real HC WEIGHT record, the derivation
    // auto-completes the tick. Until then it opens but its submission is a no-op
    // by design — no fake ticks, no parallel state.
    if (showWeighInDialog) {
        WeighInDialog(
            onDismiss = { showWeighInDialog = false },
            onSubmitWeight = { weight, bodyFat ->
                showWeighInDialog = false
                // TODO(weigh-in dialog): write WEIGHT (and BODY_FAT) record via
                // HC write + sync store; the checklist then derives itself.
            }
        )
    }

    if (showWeeklyCheckInDialog) {
        WeeklyCheckInDialog(
            onDismiss = { showWeeklyCheckInDialog = false },
            onCompleteCheckIn = { newTarget ->
                dailyCalorieTarget = newTarget
                checkInCompletedValue = "Target: %,d kcal".format(newTarget)
                weeklyCheckInCompleted = true
            },
            currentTargetKcal = dailyCalorieTarget,
            suggestedTargetKcal = dailyCalorieTarget
        )
    }

    // Day key calendar navigation (using core/DayKeys contract)
    val today = remember { todayKey() }
    var currentDayKey by remember { mutableStateOf(today) }

    // DERIVED checklist: completed-ness comes from the day's data — sync OR a
    // manual record write (weigh-in dialog / food logger). No boolean ticks.
    val checklistRecords = SyncHub.records.collectAsState().value
    val checklistDaily = remember(checklistRecords) {
        checklistRecords?.let {
            Daily.buildDailyDataFromMaps(
                it, Zones.DEFAULT_ZONES, ZoneId.systemDefault(),
                nowMs = System.currentTimeMillis(),
            )
        }
    }
    val checklist = remember(checklistDaily, currentDayKey, dailyCalorieTarget) {
        TaskChecklist.derive(
            daily = checklistDaily,
            dayKey = currentDayKey,
            calorieTarget = dailyCalorieTarget,
        )
    }

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
            .padding(top = 8.dp, bottom = 16.dp)
            .verticalScroll(scrollState)
    ) {
        val isFutureDay = currentDayKey > today
        // Real synced HC records when present; demo month otherwise.
        val hubRecords by SyncHub.records.collectAsState()
        val momentumSource: MomentumSource = remember(hubRecords) { TodayFacadeMomentumSource(hcRecords = hubRecords) }

        val isTodayLogged = checklist.weighInCompleted || checklist.foodLogCompleted || trainCompleted || weeklyCheckInCompleted || drinkState.loggedDrinks.isNotEmpty()
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
            asCard = true,
            onSettingsClick = onSettingsClick,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Today Pulse Card (Consolidates Momentum + Readiness with integrated Goal Header strip)
        TodayPulseCard(
            goalName = goalProjection.goalName,
            targetValueText = goalProjection.targetText,
            currentValueText = goalProjection.currentText,
            activeDayKey = currentDayKey,
            momentumSource = momentumSource,
            sleepHours = momentumSource.sleepMinutesForDay(currentDayKey)?.let { m -> (m / 60).toInt() } ?: 8,
            sleepMinutes = momentumSource.sleepMinutesForDay(currentDayKey)?.let { m -> (m % 60).toInt() } ?: 13,
            rhrText = momentumSource.restingHrForDay(currentDayKey)?.let { "RHR ${it.toInt()}" } ?: "RHR warming up",
            rhrValue = momentumSource.restingHrForDay(currentDayKey)?.let { "${it.toInt()} bpm" } ?: "54 bpm",
            trainDoneToday = trainCompleted,
            stepsPctToday = if (dailyStepTarget > 0) ((checklist.stepsToday ?: 0.0).toInt()) * 100 / dailyStepTarget else 0,
            alcoholUnitsToday = 0, // TODO: wire DrinkTrackerViewModel.getUnitsForDay(currentDayKey) when it exists
            onMomentumClick = { /* Navigate to Momentum */ },
            onTrendsClick = { activeMetricScreen = "weight" },
            onSleepClick = { activeMetricScreen = "sleep" },
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 3. "Today" To-Do Checklist Card (Weekly Check-in & Steps integrated inside!)
        TodayTasksCard(
            weeklyCheckInCompleted = weeklyCheckInCompleted,
            weeklyCheckInCompletedValue = checkInCompletedValue,
            onWeeklyCheckInClick = {
                if (!isFutureDay) {
                    if (!weeklyCheckInCompleted) showWeeklyCheckInDialog = true else weeklyCheckInCompleted = false
                }
            },
            weighInCompleted = checklist.weighInCompleted,
            weighInData = checklist.weighInData ?: "",
            onWeighInClick = {
                if (!isFutureDay && !checklist.weighInCompleted) showWeighInDialog = true
                // No untick: data is the truth. Removing a weigh-in = deleting
                // the record (weigh-in dialog work), never un-ticking a box.
            },
            foodLogCompleted = checklist.foodLogCompleted,
            foodLogData = checklist.foodLogData ?: "",
            onFoodLogClick = { /* no-op until the food logger exists: no fake ticks */ },
            trainCompleted = trainCompleted,
            trainData = trainData,
            onTrainClick = { if (!isFutureDay) trainCompleted = !trainCompleted },
            stepsToday = if (isFutureDay) 0 else (checklist.stepsToday ?: 0.0).toInt(),
            dailyStepTarget = dailyStepTarget,
            isFutureDay = isFutureDay,
            subtitleText = "Log anything today to start a streak",
            onStepsClick = { activeMetricScreen = "steps" },
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
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
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
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
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
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
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    // Colour = direction (MyDay Card Colour Rules §2): fell = warn amber, rose = brand lime,
    // trivial drift (<2 pts) = quiet grey. Red stays reserved for the lever chip (§3).
    val deltaColor = when {
        delta <= -2 -> JudgementWarn
        delta >= 2 -> BrandPrimaryLime
        else -> TextMuted
    }
    val sign = if (delta >= 0) "+" else "−"

    Text(
        text = "$sign${kotlin.math.abs(delta)} points $comparisonText",
        color = deltaColor,
        fontSize = 12.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.SemiBold,
        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
        maxLines = 1,
        softWrap = false,
        modifier = modifier
    )
}

/**
 * Redesigned "Today pulse" card matching the web mockup:
 * - Integrated top header strip: pill + "• TODAY" | {GOAL_NAME} | "TARGET •" + pill
 * - Hairline divider
 * - LEFT = MOMENTUM (10sp muted) + High pill, 84 +7 points / dynamic comparison date, watch sentence
 * - RIGHT = READINESS (10sp muted) + Primed pill, 70 +12 points / dynamic comparison date, recommendation
 * - Hairline divider
 * - Bottom strip: centered PulseIcon + 8h 13m sleep last night · RHR warming up
 */
@Composable
fun TodayPulseCard(
    goalName: String = "FAT LOSS",
    targetValueText: String? = null,
    currentValueText: String? = null,
    activeDayKey: String = remember { todayKey() },
    momentumSource: MomentumSource = remember(SyncHub.records.value) { TodayFacadeMomentumSource(hcRecords = SyncHub.records.value) },
    sleepHours: Int = 8,
    sleepMinutes: Int = 13,
    rhrText: String = "RHR warming up",
    rhrValue: String = "54 bpm",
    trainDoneToday: Boolean = false,
    stepsPctToday: Int = 0,
    alcoholUnitsToday: Int = 0,
    onMomentumClick: () -> Unit = {},
    onTrendsClick: () -> Unit = {},
    onSleepClick: () -> Unit = {},
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val goalProjection = remember(activeDayKey, momentumSource) {
        momentumSource.computeGoalProjection(activeDayKey)
    }
    val effectiveTargetText = targetValueText ?: goalProjection.targetText
    val effectiveCurrentText = currentValueText ?: goalProjection.currentText

    // Momentum data
    val momentumTodayRaw = momentumSource.scoreForDay(activeDayKey)
    val mBand = momentumBand(momentumTodayRaw ?: 0.0)

    // Readiness data
    val readinessStateWord = momentumSource.readinessStateForDay(activeDayKey)
    val rBand = readinessBand(readinessStateWord)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppCardSurface),
        border = BorderStroke(1.dp, BorderDefault)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // SECTION 1 — Goal strip (inside this card)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT: [29.6%] data pill (SecondaryFill bg, white bold) + "TODAY" (TextMuted)
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(SecondaryFill)
                            .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(12.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = effectiveCurrentText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "TODAY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextMuted,
                        letterSpacing = 0.5.sp,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // CENTRE: "GOAL" eyebrow (TextMuted, 8sp) above "FAT LOSS" (white, 15sp Bold)
                Box(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "GOAL",
                            style = LocalTextStyle.current.copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            ),
                            fontSize = 8.sp,
                            lineHeight = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMuted,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = goalName.uppercase(),
                            style = LocalTextStyle.current.copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            ),
                            fontSize = 15.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // RIGHT: "TARGET" (TextMuted) + [17%] pill (teal-tint: AccentTeal at 18% bg, AccentTeal border 1dp, white text)
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)
                ) {
                    Text(
                        text = "TARGET",
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
                            .background(AccentTeal.copy(alpha = 0.18f))
                            .border(BorderStroke(1.dp, AccentTeal), RoundedCornerShape(12.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = effectiveTargetText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Hairline below Section 1
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderDefault)
            )

            // SECTION 2 — Column of two FULL-WIDTH rows, each with horizontal hairlines
            Column(modifier = Modifier.fillMaxWidth()) {
                // ROW A — MOMENTUM
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMomentumClick() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LEFT: weight(1f)
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (momentumTodayRaw == null) TextMuted else mBand.color)
                        )
                        Text(
                            text = "MOMENTUM",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                            color = if (momentumTodayRaw == null) TextMuted else mBand.color,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // CENTRE: unweighted verdict pill (centre-locked by symmetric weights)
                    Box(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (momentumTodayRaw == null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SecondaryFill)
                                    .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "No data",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(mBand.color.copy(alpha = 0.18f))
                                    .border(BorderStroke(1.dp, mBand.color), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = mBand.label,
                                    color = mBand.color,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // RIGHT: weight(1f), end-aligned
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = "View Breakdown ›",
                            fontSize = 10.sp,
                            color = TextMuted,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Hairline between ROW A and ROW B
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(BorderDefault)
                )

                // ROW B — READINESS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSleepClick() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LEFT: weight(1f)
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(rBand.color)
                        )
                        Text(
                            text = "READINESS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                            color = rBand.color,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // CENTRE: unweighted verdict pill (centre-locked by symmetric weights)
                    Box(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(rBand.color.copy(alpha = 0.18f))
                                .border(BorderStroke(1.dp, rBand.color), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = rBand.label,
                                color = rBand.color,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    // RIGHT: weight(1f), end-aligned
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text(
                            text = "View Breakdown ›",
                            fontSize = 10.sp,
                            color = TextMuted,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Hairline divider above Section 3
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BorderDefault)
            )

            // SECTION 3 — Bottom strip inside the card:
            // Heart glyph + "7hr:35m sleep last night · 56 RHR" — TextMuted, 11sp, centred.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSleepClick() }
                    .padding(vertical = 8.dp, horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                HeartPulseIcon(
                    modifier = Modifier.size(13.dp),
                    tint = TextMuted
                )
                Spacer(modifier = Modifier.width(6.dp))
                val sleepText = "${sleepHours}hr:${"%02d".format(sleepMinutes)}m sleep last night"
                val rhrFormatted = if (rhrText.startsWith("RHR", ignoreCase = true)) {
                    val digits = rhrText.filter { it.isDigit() }
                    if (digits.isNotEmpty()) "$digits RHR" else rhrText
                } else rhrText
                Text(
                    text = "$sleepText · $rhrFormatted",
                    fontSize = 11.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
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
            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                title = "Weekly Round Up",
                hint = if (isFutureDay) "Upcoming" else "How are you feeling?",
                isCompleted = !isFutureDay && weeklyCheckInCompleted,
                completedValue = weeklyCheckInCompletedValue,
                onClick = onWeeklyCheckInClick
            )

            // Task 2: Weigh in
            MicroTaskRow(
                title = "Weigh in",
                hint = if (isFutureDay) "Upcoming" else "Jump on the scales",
                isCompleted = !isFutureDay && weighInCompleted,
                completedValue = weighInData,
                onClick = onWeighInClick
            )

            // Task 3: Log your food
            MicroTaskRow(
                title = "Log your food",
                hint = if (isFutureDay) "Upcoming" else "Easiest win of the day",
                isCompleted = !isFutureDay && foodLogCompleted,
                completedValue = foodLogData,
                onClick = onFoodLogClick
            )

            // Task 4: Train
            MicroTaskRow(
                title = trainData,
                hint = if (isFutureDay) "Upcoming" else "It's 20 minutes",
                isCompleted = !isFutureDay && trainCompleted,
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
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier,
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
            .padding(horizontal = 14.dp, vertical = 10.dp)
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
            .padding(horizontal = 14.dp, vertical = 10.dp)
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