package com.scatterbrain.scatterfit.ui

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.scatterbrain.scatterfit.ui.theme.StateLocked
import com.scatterbrain.scatterfit.ui.theme.StateSlip
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted

@Composable
fun MyDayScreen(
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit = {}
) {
    val scrollState = rememberScrollState()

    val today = remember { todayKey() }
    var currentDayKey by remember { mutableStateOf(today) }

    val repoDaily by DailyRepository.daily.collectAsState()
    val momentumSource: MomentumSource? = remember(repoDaily) {
        repoDaily?.let {
            TodayFacadeMomentumSource(it)
        }
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

    val foodViewModel = remember {
        FoodTrackerViewModel(
            storage = FoodStorage(context),
            initialDayKey = currentDayKey
        )
    }

    LaunchedEffect(currentDayKey) {
        foodViewModel.setDayKey(currentDayKey)
    }

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
                // Real write: local LoggedWeight -> WEIGHT record -> pipeline.
                // Same merge rules as scale data (per app per day, last wins,
                // Hume preferred). Sanity gates mirror Records.clean:
                // 40-200 kg, 0-60% body fat.
                val kg = weight.trim().toDoubleOrNull() ?: return@WeighInDialog
                if (kg < 40.0 || kg > 200.0) return@WeighInDialog
                val bf = bodyFat.trim().toDoubleOrNull()
                    ?.takeIf { it > 0.0 && it <= 60.0 }
                val storage = WeightStorage(context)
                storage.saveWeight(
                    LoggedWeight(kg = kg, bodyFatPct = bf, dayKey = currentDayKey)
                )
                SyncHub.updateLocalWeights(storage.loadAll().map { it.toHealthRecord() })
            }
        )
    }

    var showWorkoutHint by remember { mutableStateOf(false) }
    LaunchedEffect(showWorkoutHint) {
        if (showWorkoutHint) {
            kotlinx.coroutines.delay(2000L)
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
                    dayKey = currentDayKey,
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

    val isSyncing by SyncHub.syncing.collectAsState()

    LaunchedEffect(Unit) {
        SyncHub.syncToday()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 8.dp, bottom = 16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Day Card (header + rows + value-state colouring)
        DayCard(
            daily = repoDaily,
            dayKey = currentDayKey,
            onMetricClick = { metricId ->
                activeMetricId = metricId
            },
            onWeighInClick = { showWeighInDialog = true },
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
        )

        // Quick Actions strip (Log meal / Log drink / Log workout)
        QuickActionsStrip(
            onLogMealClick = { showFoodLoggerScreen = true },
            onLogDrinkClick = { showDrinkLoggerScreen = true },
            onLogWorkoutClick = { showWorkoutHint = true },
            workoutHintVisible = showWorkoutHint,
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()
        )

        // Syncing indicator from batch 3
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
                        focusedBorderColor = AccentTeal,
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
                        focusedBorderColor = AccentTeal,
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
    Card(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
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
