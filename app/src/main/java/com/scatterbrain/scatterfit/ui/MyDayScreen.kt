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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.text.input.KeyboardType
import com.scatterbrain.scatterfit.data.EstimatedStepEntry
import com.scatterbrain.scatterfit.data.EstimatedStepsStorage
import com.scatterbrain.scatterfit.data.SettingsStorage
import com.scatterbrain.scatterfit.ui.theme.StateBad
import androidx.compose.ui.res.painterResource
import com.scatterbrain.scatterfit.R
import com.scatterbrain.scatterfit.ui.theme.AccentHot
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
    onOpenFood: (String) -> Unit = {},
    onOpenTraining: () -> Unit = {},
    foodViewModel: FoodTrackerViewModel? = null,
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
    val plannedStorage = remember { PlannedSessionsStorage(context) }
    val routineStorage = remember { RoutineStorage(context) }
    val estimatedStepsStorage = remember { EstimatedStepsStorage(context) }
    val settingsStorage = remember(context) { SettingsStorage(context) }
    val settings = remember(settingsStorage) { settingsStorage.loadSettings() }

    LaunchedEffect(Unit) {
        migrateLegacyTrainingPlanIfPresent(context, plannedStorage)
    }

    val foodVm = foodViewModel ?: remember {
        FoodTrackerViewModel(
            storage = foodStorage,
            savedFoodStore = SavedFoodStore(context),
            initialDayKey = selectedDay
        )
    }

    LaunchedEffect(selectedDay) {
        foodVm.setDayKey(selectedDay)
    }

    val repoDaily by DailyRepository.daily.collectAsState()

    val storedMeals by foodVm.mealsForDay.collectAsState()
    val pipelineMeals = repoDaily?.meals?.get(selectedDay) ?: emptyList()
    val externalHcMeals = remember(pipelineMeals, selectedDay) {
        pipelineMeals
            .filterNot { entry ->
                val src = entry.source.lowercase()
                src == "local" || src == "app" || src.contains("scatterfit")
            }
            .map { it.toLoggedMeal(selectedDay) }
    }
    val dayMeals = remember(storedMeals, externalHcMeals) {
        storedMeals + externalHcMeals
    }
    val consumedKcal = remember(dayMeals) { dayMeals.sumOf { it.kcal } }
    val consumedProtein = remember(dayMeals) { dayMeals.sumOf { it.protein ?: 0.0 } }

    var estimatedStepsRefreshKey by remember { mutableIntStateOf(0) }
    val dayEstimatedEntries = remember(selectedDay, estimatedStepsRefreshKey) {
        estimatedStepsStorage.loadEntries(selectedDay)
    }
    val dayEstimatedSteps = remember(dayEstimatedEntries) {
        dayEstimatedEntries.sumOf { it.steps }
    }
    val allEstimatedTotals = remember(estimatedStepsRefreshKey) {
        estimatedStepsStorage.getAllEstimatedTotals()
    }

    val combinedDaily = remember(repoDaily, allEstimatedTotals) {
        repoDaily?.let { d ->
            if (allEstimatedTotals.isEmpty()) d
            else {
                val mergedSteps = d.steps.toMutableMap()
                allEstimatedTotals.forEach { (day, est) ->
                    if (est > 0) {
                        mergedSteps[day] = (mergedSteps[day] ?: 0.0) + est
                    }
                }
                d.copy(steps = mergedSteps)
            }
        }
    }

    val momentumSource: MomentumSource? = remember(combinedDaily, settings) {
        combinedDaily?.let {
            TodayFacadeMomentumSource(it, settings)
        }
    }

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

    val routines = remember { routineStorage.loadRoutines() }
    val selectedDate = remember(selectedDay) {
        runCatching { LocalDate.parse(selectedDay) }.getOrDefault(LocalDate.now())
    }
    val todayPlan = remember(selectedDate, routines) {
        plannedStorage.getPlanForDate(selectedDate, routines)
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
    var showDrinkLoggerScreen by remember { mutableStateOf(false) }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var showWeighInDialog by remember { mutableStateOf(false) }
    var showAdjustStepsSheet by remember { mutableStateOf(false) }
    var activeMetricScreen by remember { mutableStateOf<String?>(null) }
    var activeMetricId by remember { mutableStateOf<com.scatterbrain.scatterfit.core.MetricId?>(null) }

    val scope = rememberCoroutineScope()

    if (showBarcodeScanner) {
        BarcodeScannerOverlay(
            onBarcodeDetected = { barcode ->
                showBarcodeScanner = false
                scope.launch {
                    val product = com.scatterbrain.scatterfit.data.OpenFoodFactsClient.lookupBarcode(barcode)
                    if (product != null) {
                        foodVm.pendingConfirmProduct = product
                        foodVm.barcodeNotFound = false
                    } else {
                        foodVm.pendingConfirmProduct = null
                        foodVm.barcodeNotFound = true
                    }
                    onOpenFood(selectedDay)
                }
            },
            onDismiss = { showBarcodeScanner = false }
        )
        return
    }

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

    BackHandler(enabled = activeMetricId != null || activeMetricScreen != null || showDrinkLoggerScreen || showAdjustStepsSheet || showBarcodeScanner) {
        when {
            showBarcodeScanner -> showBarcodeScanner = false
            activeMetricId != null -> activeMetricId = null
            activeMetricScreen != null -> activeMetricScreen = null
            showDrinkLoggerScreen -> showDrinkLoggerScreen = false
            showAdjustStepsSheet -> showAdjustStepsSheet = false
        }
    }

    when {
        activeMetricId != null -> {
            MetricScreen(
                initialId = activeMetricId!!,
                onBackClick = { activeMetricId = null },
                settings = settings
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

    if (showAdjustStepsSheet) {
        AdjustStepsSheet(
            initialDate = selectedDay,
            onDismissRequest = { showAdjustStepsSheet = false },
            onSaveEstimate = { steps, note, date ->
                estimatedStepsStorage.saveEntry(
                    EstimatedStepEntry(
                        dayKey = date,
                        steps = steps,
                        note = note?.trim()?.ifBlank { null }
                    )
                )
                estimatedStepsRefreshKey++
                showAdjustStepsSheet = false
            },
            existingEntries = dayEstimatedEntries,
            onDeleteEntry = { id ->
                estimatedStepsStorage.removeEntry(selectedDay, id)
                estimatedStepsRefreshKey++
            }
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
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // TOP BAR: (Streak chip, My Day title, Calendar + Settings icons)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // LEFT: streak chip — flame icon + "N", SecondaryFill pill, 14sp Bold
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(SecondaryFill)
                    .border(BorderStroke(1.dp, BorderDefault), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StreakIcon(
                        modifier = Modifier.size(16.dp),
                        tint = AccentHot
                    )
                    Text(
                        text = "$streak",
                        fontSize = 14.sp,
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
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = TextForeground,
                textAlign = TextAlign.Center
            )

            // RIGHT: Calendar icon button + Settings gear button (18dp icons in 36dp circular targets, 8dp gap)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SecondaryFill)
                        .clickable { showCalendarSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    CalendarIcon(
                        modifier = Modifier.size(18.dp),
                        tint = TextMuted
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SecondaryFill)
                        .clickable { onSettingsClick() },
                    contentAlignment = Alignment.Center
                ) {
                    SettingsIcon(
                        modifier = Modifier.size(18.dp),
                        tint = TextMuted
                    )
                }
            }
        }

        // Day Card (header + rows + value-state colouring)
        DayCard(
            daily = repoDaily,
            dayKey = selectedDay,
            planName = todayPlan,
            drinksCount = drinkState.loggedDrinks.size,
            onMetricClick = { metricId ->
                activeMetricId = metricId
            },
            onDrinksClick = {
                showDrinkLoggerScreen = true
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
            settings = settings,
            consumedKcal = consumedKcal,
            consumedProtein = consumedProtein,
            deviceSteps = repoDaily?.steps?.get(selectedDay),
            estimatedSteps = dayEstimatedSteps,
            onAdjustStepsClick = { showAdjustStepsSheet = true },
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
        )

        // Quick Actions strip (Log food / Scan / Log drink / Log weight)
        QuickActionsStrip(
            onLogFoodClick = { if (!isFutureDay) onOpenFood(selectedDay) },
            onScanFoodClick = { if (!isFutureDay) showBarcodeScanner = true },
            onLogDrinkClick = { if (!isFutureDay) showDrinkLoggerScreen = true },
            onLogWeightClick = { showWeighInDialog = true },
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
        )

        // Tomorrow Card (shown only when viewing today and 19:00–07:59; hidden in past mode)
        var isTomorrowDismissed by remember { mutableStateOf(false) }
        val currentHour = remember(selectedDay) { LocalTime.now().hour }
        val tomorrowVisible = isToday && isTomorrowCardVisible(currentHour) && !isTomorrowDismissed

        if (tomorrowVisible) {
            val tomorrowDate = remember(selectedDay) {
                runCatching { LocalDate.parse(selectedDay).plusDays(1) }.getOrDefault(LocalDate.now().plusDays(1))
            }
            val tomorrowDateText = remember(tomorrowDate) {
                tomorrowDate.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.US))
            }
            val tomorrowPlan = remember(tomorrowDate, routines) {
                plannedStorage.getPlanForDate(tomorrowDate, routines)
            }

            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = BorderStroke(0.dp, Color.Transparent)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = if (tomorrowPlan != null) R.drawable.ic_dumbbell else R.drawable.ic_bed_double),
                        contentDescription = null,
                        tint = if (tomorrowPlan != null) AccentTeal else TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tomorrow · ",
                            color = TextMuted,
                            fontSize = 14.sp,
                            lineHeight = 18.sp
                        )
                        Text(
                            text = tomorrowPlan ?: "Rest day",
                            color = TextForeground,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { isTomorrowDismissed = true }
                            .padding(4.dp)
                    ) {
                        CloseXIcon(
                            modifier = Modifier.size(16.dp),
                            tint = TextMuted
                        )
                    }
                }
            }
        }

        // Syncing indicator
        if (isSyncing) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RefreshIcon(
                    modifier = Modifier.size(12.dp),
                    tint = TextMuted
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Syncing…",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
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
    onLogFoodClick: () -> Unit,
    onScanFoodClick: () -> Unit = {},
    onLogDrinkClick: () -> Unit,
    onLogWeightClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickActionButton(
            label = "Log food",
            icon = { UtensilsIcon(modifier = Modifier.size(18.dp), tint = AccentTeal) },
            onClick = onLogFoodClick,
            modifier = Modifier.weight(1f)
        )

        QuickActionButton(
            label = "Scan",
            icon = { CameraIcon(modifier = Modifier.size(18.dp), tint = AccentTeal) },
            onClick = onScanFoodClick,
            modifier = Modifier.weight(1f)
        )

        QuickActionButton(
            label = "Log drink",
            icon = { GlassWaterIcon(modifier = Modifier.size(18.dp), tint = AccentTeal) },
            onClick = onLogDrinkClick,
            modifier = Modifier.weight(1f)
        )

        QuickActionButton(
            label = "Log weight",
            icon = { ScaleIcon(modifier = Modifier.size(18.dp), tint = AccentTeal) },
            onClick = onLogWeightClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    icon: @Composable () -> Unit,
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
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "qaScale"
    )
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
                this.scaleX = pressScale
                this.scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = BorderStroke(0.dp, Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AccentTeal.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustStepsSheet(
    initialDate: String,
    onDismissRequest: () -> Unit,
    onSaveEstimate: (steps: Int, note: String?, date: String) -> Unit,
    existingEntries: List<EstimatedStepEntry> = emptyList(),
    onDeleteEntry: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var stepsInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    var targetDate by remember { mutableStateOf(initialDate) }
    var stepsError by remember { mutableStateOf<String?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = DarkCardSurface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Adjust Steps (Manual Estimate)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextForeground
            )

            // Date picker field
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("DATE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SecondaryFill)
                        .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(8.dp))
                        .clickable {
                            val parsedDate = runCatching { LocalDate.parse(targetDate) }.getOrDefault(LocalDate.now())
                            android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    targetDate = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                                },
                                parsedDate.year,
                                parsedDate.monthValue - 1,
                                parsedDate.dayOfMonth
                            ).show()
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = targetDate, color = TextForeground, fontSize = 14.sp)
                        Text(text = "Change", color = AccentTeal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Steps number field
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("ESTIMATED STEPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                OutlinedTextField(
                    value = stepsInput,
                    onValueChange = {
                        stepsInput = it.filter { ch -> ch.isDigit() }
                        if (stepsError != null) stepsError = null
                    },
                    placeholder = { Text("e.g. 4000", color = TextMuted) },
                    singleLine = true,
                    isError = stepsError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextForeground,
                        unfocusedTextColor = TextForeground,
                        focusedBorderColor = AccentTeal,
                        unfocusedBorderColor = BorderDefault,
                        focusedContainerColor = SecondaryFill,
                        unfocusedContainerColor = SecondaryFill
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (stepsError != null) {
                    Text(
                        text = stepsError!!,
                        color = StateBad,
                        fontSize = 11.sp
                    )
                }
            }

            // Optional note field
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("NOTE (OPTIONAL)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    placeholder = { Text("e.g. theatre day", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextForeground,
                        unfocusedTextColor = TextForeground,
                        focusedBorderColor = AccentTeal,
                        unfocusedBorderColor = BorderDefault,
                        focusedContainerColor = SecondaryFill,
                        unfocusedContainerColor = SecondaryFill
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Save Button
            Button(
                onClick = {
                    val s = stepsInput.trim().toIntOrNull()
                    if (s == null || s <= 0) {
                        stepsError = "Enter valid step count"
                        return@Button
                    }
                    onSaveEstimate(s, noteInput, targetDate)
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentTeal,
                    contentColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add estimate", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            // Existing entries for this day
            if (existingEntries.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("EXISTING ESTIMATES FOR THIS DAY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted, letterSpacing = 1.sp)
                    existingEntries.forEach { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SecondaryFill)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "+${String.format(Locale.US, "%,d", entry.steps)} steps",
                                    color = TextForeground,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (!entry.note.isNullOrBlank()) {
                                    Text(
                                        text = entry.note,
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            if (onDeleteEntry != null) {
                                TextButton(onClick = { onDeleteEntry(entry.id) }) {
                                    Text("Delete", color = StateBad, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun QuickActionsStripPreview() {
    ScatterFitTheme {
        QuickActionsStrip(
            onLogFoodClick = {},
            onScanFoodClick = {},
            onLogDrinkClick = {},
            onLogWeightClick = {},
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
