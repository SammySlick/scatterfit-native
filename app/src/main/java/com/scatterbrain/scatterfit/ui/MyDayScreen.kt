package com.scatterbrain.scatterfit.ui

import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.data.DailyRepository
import com.scatterbrain.scatterfit.sync.SyncHub
import com.scatterbrain.scatterfit.ui.theme.AccentTeal
import com.scatterbrain.scatterfit.ui.theme.AppCardSurface
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.DarkCardSurface
import com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.StateLocked
import com.scatterbrain.scatterfit.ui.theme.StateSlip
import com.scatterbrain.scatterfit.ui.theme.TealHighlight
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MyDayScreen(
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit = {},
    onOpenTraining: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    val today = remember { todayKey() }
    var selectedDay by remember { mutableStateOf(today) }
    val isFutureDay = selectedDay > today
    val isToday = selectedDay == today

    val context = LocalContext.current
    val foodStorage = remember { FoodStorage(context) }
    val drinkStorage = remember { DrinkStorage(context) }
    val weightStorage = remember { WeightStorage(context) }
    val planStorage = remember { TrainingPlanStorage(context) }

    val repoDaily by DailyRepository.daily.collectAsState()
    val momentumSource: MomentumSource? = remember(repoDaily) {
        repoDaily?.let {
            TodayFacadeMomentumSource(it)
        }
    }

    // Day-keyed persistent drink tracking ViewModel
    val drinkViewModel = remember {
        DrinkTrackerViewModel(
            storage = drinkStorage,
            initialDayKey = selectedDay
        )
    }

    LaunchedEffect(selectedDay) {
        drinkViewModel.setDayKey(selectedDay)
    }

    val drinkState by drinkViewModel.dailyState.collectAsState()

    LaunchedEffect(Unit) {
        if (drinkState.libraryDrinks.isEmpty()) {
            drinkViewModel.loadLibraryFromAssets(context)
        }
    }

    val foodViewModel = remember {
        FoodTrackerViewModel(
            storage = foodStorage,
            initialDayKey = selectedDay
        )
    }

    LaunchedEffect(selectedDay) {
        foodViewModel.setDayKey(selectedDay)
    }

    var planVersion by remember { mutableIntStateOf(0) }
    val selectedDate = remember(selectedDay) {
        runCatching { LocalDate.parse(selectedDay) }.getOrDefault(LocalDate.now())
    }
    val todayPlan = remember(selectedDate, planVersion) {
        planStorage.getPlanForDate(selectedDate)
    }

    val streak = remember(repoDaily, selectedDay) {
        computeStreak(
            foodByDay = foodStorage.getLoggedDays(),
            drinksByDay = drinkStorage.getLoggedDays(),
            weightsByDay = weightStorage.getLoggedDays(),
            todayKey = today
        )
    }

    var showCalendarSheet by remember { mutableStateOf(false) }
    var showFoodLoggerScreen by remember { mutableStateOf(false) }
    var showDrinkLoggerScreen by remember { mutableStateOf(false) }
    var showWeighInDialog by remember { mutableStateOf(false) }
    var activeMetricScreen by remember { mutableStateOf<String?>(null) }
    var activeMetricId by remember { mutableStateOf<com.scatterbrain.scatterfit.core.MetricId?>(null) }

    if (showWeighInDialog) {
        WeighInDialog(
            onDismiss = { showWeighInDialog = false },
            onSubmitWeight = { weight, bodyFat ->
                showWeighInDialog = false
                val kg = weight.trim().toDoubleOrNull() ?: return@WeighInDialog
                if (kg < 40.0 || kg > 200.0) return@WeighInDialog
                val bf = bodyFat.trim().toDoubleOrNull()
                    ?.takeIf { it > 0.0 && it <= 60.0 }
                val zone = ZoneId.systemDefault()
                val timestamp = if (selectedDay == todayKey(zone)) {
                    System.currentTimeMillis()
                } else {
                    val date = runCatching { LocalDate.parse(selectedDay) }.getOrDefault(LocalDate.now(zone))
                    date.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
                }
                weightStorage.saveWeight(
                    LoggedWeight(kg = kg, bodyFatPct = bf, timestamp = timestamp, dayKey = selectedDay)
                )
                SyncHub.updateLocalWeights(weightStorage.loadAll().flatMap { it.toHealthRecords() })
            }
        )
    }

    var showWorkoutHint by remember { mutableStateOf(false) }
    LaunchedEffect(showWorkoutHint) {
        if (showWorkoutHint) {
            delay(2000L)
            showWorkoutHint = false
        }
    }

    BackHandler(enabled = activeMetricId != null || activeMetricScreen != null || showDrinkLoggerScreen || showFoodLoggerScreen) {
        when {
            activeMetricId != null -> activeMetricId = null
            activeMetricScreen != null -> activeMetricScreen = null
            showDrinkLoggerScreen -> showDrinkLoggerScreen = false
            showFoodLoggerScreen -> showFoodLoggerScreen = false
        }
    }

    when {
        activeMetricId != null -> {
            MetricScreen(
                initialId = activeMetricId!!,
                onBackClick = { activeMetricId = null }
            )
            return
        }
        activeMetricScreen == "steps" -> {
            com.scatterbrain.scatterfit.ui.MetricPages.StepsScreen(
                onBackClick = { activeMetricScreen = null }
            )
            return
        }
        activeMetricScreen == "sleep" -> {
            com.scatterbrain.scatterfit.ui.MetricPages.SleepScreen(
                onBackClick = { activeMetricScreen = null }
            )
            return
        }
        activeMetricScreen == "weight" -> {
            com.scatterbrain.scatterfit.ui.MetricPages.WeightScreen(
                onBackClick = { activeMetricScreen = null }
            )
            return
        }
        activeMetricScreen == "breakdown" -> {
            if (momentumSource != null) {
                ConsistencyBreakdownScreen(
                    momentumSource = momentumSource,
                    dayKey = selectedDay,
                    onBackClick = { activeMetricScreen = null },
                    onMetricClick = { activeMetricId = it }
                )
            }
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

    if (showFoodLoggerScreen) {
        FoodLoggerScreen(
            viewModel = foodViewModel,
            onBackClick = { showFoodLoggerScreen = false }
        )
        return
    }

    if (showCalendarSheet) {
        val allLoggedDays = remember(repoDaily, selectedDay) {
            foodStorage.getLoggedDays().keys + drinkStorage.getLoggedDays().keys + weightStorage.getLoggedDays()
        }
        CalendarSheet(
            selectedDayKey = selectedDay,
            todayKey = today,
            loggedDays = allLoggedDays,
            onDaySelected = { selectedDay = it },
            onDismissRequest = { showCalendarSheet = false }
        )
    }

    val isSyncing by SyncHub.syncing.collectAsState()

    LaunchedEffect(Unit) {
        SyncHub.syncToday()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // TOP BAR: statusBarsPadding (Streak chip, My Day title, Calendar + Settings icons)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // LEFT: streak chip — flame icon + "N", SecondaryFill pill, 12sp Bold
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(SecondaryFill)
                    .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StreakIcon(
                        modifier = Modifier.size(14.dp),
                        tint = TealHighlight
                    )
                    Text(
                        text = "$streak",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextForeground
                    )
                }
            }

            // CENTRE: "My Day" title (or formatted date when viewing a past day)
            val centerTitle = if (isToday) {
                "My Day"
            } else {
                runCatching {
                    LocalDate.parse(selectedDay).format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.US))
                }.getOrDefault(selectedDay)
            }
            Text(
                text = centerTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextForeground,
                textAlign = TextAlign.Center
            )

            // RIGHT: Calendar icon button + Settings gear button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { showCalendarSheet = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    CalendarIcon(
                        modifier = Modifier.size(20.dp),
                        tint = TextForeground
                    )
                }
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    SettingsIcon(
                        modifier = Modifier.size(20.dp),
                        tint = AccentTeal
                    )
                }
            }
        }

        // Day Card (header + rows + value-state colouring)
        DayCard(
            daily = repoDaily,
            dayKey = selectedDay,
            planName = todayPlan,
            onMetricClick = { metricId ->
                activeMetricId = metricId
            },
            onPlanEditorClick = onOpenTraining,
            onWeighInClick = {
                val hasWeight = repoDaily?.weight?.get(selectedDay)?.let { it > 0.0 } == true
                if (hasWeight) {
                    Log.d("ScatterFitSync", "weigh-in row tapped -> open WeightScreen")
                    activeMetricScreen = "weight"
                } else {
                    Log.d("ScatterFitSync", "weigh-in row tapped -> open WeighInDialog")
                    showWeighInDialog = true
                }
            },
            onResetToToday = { selectedDay = today },
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
        )

        // Quick Actions strip (Log meal / Log drink / Log workout)
        QuickActionsStrip(
            onLogMealClick = { if (!isFutureDay) showFoodLoggerScreen = true },
            onLogDrinkClick = { if (!isFutureDay) showDrinkLoggerScreen = true },
            onLogWorkoutClick = onOpenTraining,
            workoutHintVisible = showWorkoutHint,
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
        )

        // Tomorrow Card (shown only when viewing today and 19:00–07:59; hidden in past mode)
        val currentHour = remember(selectedDay) { LocalTime.now().hour }
        val tomorrowVisible = isToday && isTomorrowCardVisible(currentHour)

        if (tomorrowVisible) {
            val tomorrowDate = remember(selectedDay) {
                runCatching { LocalDate.parse(selectedDay).plusDays(1) }.getOrDefault(LocalDate.now().plusDays(1))
            }
            val tomorrowDateText = remember(tomorrowDate) {
                tomorrowDate.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.US))
            }
            val tomorrowPlan = remember(tomorrowDate, planVersion) {
                planStorage.getPlanForDate(tomorrowDate)
            }

            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = BorderStroke(1.dp, BorderDefault)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tomorrow",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextForeground
                        )
                        Text(
                            text = tomorrowDateText,
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    if (tomorrowPlan != null) {
                        Text(
                            text = tomorrowPlan,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TealHighlight
                        )
                    } else {
                        Text(
                            text = "Rest day",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        // Syncing indicator
        if (isSyncing) {
            Text(
                text = "Syncing…",
                fontSize = 10.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
fun WeighInDialog(
    onDismiss: () -> Unit,
    onSubmitWeight: (weight: String, bodyFat: String) -> Unit
) {
    var weightInput by remember { mutableStateOf("") }
    var bodyFatInput by remember { mutableStateOf("") }
    var weightError by remember { mutableStateOf<String?>(null) }
    var bodyFatError by remember { mutableStateOf<String?>(null) }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.70f else 1f,
        animationSpec = if (isPressed) tween(80) else spring(stiffness = Spring.StiffnessHigh),
        label = "weighInSaveAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = if (isPressed) tween(80) else spring(stiffness = Spring.StiffnessHigh),
        label = "weighInSaveScale"
    )
    val haptic = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppCardSurface,
        title = {
            Text("Weigh In", color = TextForeground, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = {
                            weightInput = it
                            if (weightError != null) weightError = null
                        },
                        label = { Text("Weight (kg)", color = TextMuted) },
                        singleLine = true,
                        isError = weightError != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextForeground,
                            unfocusedTextColor = TextForeground,
                            focusedBorderColor = AccentTeal,
                            unfocusedBorderColor = BorderDefault
                        )
                    )
                    if (weightError != null) {
                        Text(
                            text = weightError!!,
                            color = Color(0xFFE57373),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    OutlinedTextField(
                        value = bodyFatInput,
                        onValueChange = {
                            bodyFatInput = it
                            if (bodyFatError != null) bodyFatError = null
                        },
                        label = { Text("Body Fat % (optional)", color = TextMuted) },
                        singleLine = true,
                        isError = bodyFatError != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextForeground,
                            unfocusedTextColor = TextForeground,
                            focusedBorderColor = AccentTeal,
                            unfocusedBorderColor = BorderDefault
                        )
                    )
                    if (bodyFatError != null) {
                        Text(
                            text = bodyFatError!!,
                            color = Color(0xFFE57373),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val validation = validateWeighIn(weightInput, bodyFatInput)
                    if (!validation.isValid) {
                        weightError = validation.weightError
                        bodyFatError = validation.bodyFatError
                        return@Button
                    }
                    onSubmitWeight(weightInput, bodyFatInput)
                    onDismiss()
                },
                interactionSource = interactionSource,
                modifier = Modifier.graphicsLayer {
                    this.alpha = alpha
                    this.scaleX = scale
                    this.scaleY = scale
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal, contentColor = Color.Black)
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
    // Colour = direction: fell = StateSlip #F29D1E, rose = StateLocked #44B65E,
    // under 2 pts = TextMuted #92979E, per Colour System v2. Never red.
    val deltaColor = when {
        delta <= -2 -> StateSlip
        delta >= 2 -> StateLocked
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

@Composable
fun QuickActionsStrip(
    onLogMealClick: () -> Unit,
    onLogDrinkClick: () -> Unit,
    onLogWorkoutClick: () -> Unit,
    modifier: Modifier = Modifier,
    workoutHintVisible: Boolean = false,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickActionButton(
                label = "Log meal",
                onClick = onLogMealClick,
                modifier = Modifier.weight(1f)
            )

            QuickActionButton(
                label = "Log drink",
                onClick = onLogDrinkClick,
                modifier = Modifier.weight(1f)
            )

            QuickActionButton(
                label = "Log workout",
                onClick = onLogWorkoutClick,
                modifier = Modifier.weight(1f)
            )
        }

        if (workoutHintVisible) {
            // TODO: Lifting section coming in the redesign spec
            Text(
                text = "Lifting section coming",
                fontSize = 11.sp,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.70f else 1f,
        animationSpec = if (isPressed) tween(80) else spring(stiffness = Spring.StiffnessHigh),
        label = "qaAlpha"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = if (isPressed) tween(80) else spring(stiffness = Spring.StiffnessHigh),
        label = "qaScale"
    )
    val bounceScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = modifier
            .heightIn(min = 48.dp)
            .graphicsLayer {
                val totalScale = pressScale * bounceScale.value
                this.alpha = alpha
                this.scaleX = totalScale
                this.scaleY = totalScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        bounceScale.snapTo(1f)
                        bounceScale.animateTo(1.06f, tween(100))
                        bounceScale.animateTo(1f, spring(stiffness = Spring.StiffnessHigh))
                    }
                    onClick()
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = BorderStroke(1.dp, BorderDefault)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextForeground,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun QuickActionsStripPreview() {
    ScatterFitTheme {
        QuickActionsStrip(
            onLogMealClick = {},
            onLogDrinkClick = {},
            onLogWorkoutClick = {},
            workoutHintVisible = true,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MyDayScreenPreview() {
    ScatterFitTheme {
        MyDayScreen()
    }
}
