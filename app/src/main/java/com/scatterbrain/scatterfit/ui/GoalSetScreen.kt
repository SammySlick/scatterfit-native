package com.scatterbrain.scatterfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.core.ActivityLevel
import com.scatterbrain.scatterfit.core.GoalChange
import com.scatterbrain.scatterfit.core.GoalPreset
import com.scatterbrain.scatterfit.core.MifflinStJeor
import com.scatterbrain.scatterfit.core.computeGoal
import com.scatterbrain.scatterfit.core.computeManualGoal
import com.scatterbrain.scatterfit.core.goalLabel
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.data.DailyRepository
import com.scatterbrain.scatterfit.data.GoalHistoryStorage
import com.scatterbrain.scatterfit.data.SettingsStorage
import com.scatterbrain.scatterfit.ui.theme.AccentTeal
import com.scatterbrain.scatterfit.ui.theme.AppBackground
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.BorderInput
import com.scatterbrain.scatterfit.ui.theme.DarkCardSurface
import com.scatterbrain.scatterfit.ui.theme.JudgementWarn
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun GoalSetScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val settingsStorage = remember(context) { SettingsStorage(context) }
    val goalHistoryStorage = remember(context) { GoalHistoryStorage(context) }
    val settings = remember(settingsStorage) { settingsStorage.loadSettings() }

    // Resolve current weight from latest recorded weight or fallback to target
    val weightStorage = remember(context) { WeightStorage(context) }
    val latestRecordedWeight = remember(weightStorage) {
        weightStorage.loadAll().maxByOrNull { it.timestamp }?.kg
            ?: DailyRepository.daily.value?.weight?.let { map ->
                val maxDay = map.keys.maxOrNull()
                maxDay?.let { map[it] }
            }
    }

    val hasRecordedWeight = latestRecordedWeight != null && latestRecordedWeight > 0.0
    val effectiveWeight = latestRecordedWeight ?: settings.weightTarget
    val hasMissingBodyData = settings.heightCm <= 0 || settings.ageYears <= 0 || !hasRecordedWeight

    var selectedPreset by remember { mutableStateOf(settings.activeGoal) }
    var selectedActivity by remember { mutableStateOf(ActivityLevel.MODERATE) }

    var isManualExpanded by remember { mutableStateOf(false) }
    var manualKcalInput by remember { mutableStateOf("") }
    var manualProteinInput by remember { mutableStateOf("") }

    val bmr = remember(effectiveWeight, settings.heightCm, settings.ageYears, settings.sex) {
        MifflinStJeor.calculateBmr(
            weightKg = effectiveWeight,
            heightCm = settings.heightCm.toDouble(),
            ageYears = settings.ageYears,
            sex = settings.sex
        )
    }

    val tdee = bmr * selectedActivity.factor

    val customKcal = manualKcalInput.toIntOrNull()
    val customProtein = manualProteinInput.toIntOrNull()
    val isManualActive = isManualExpanded && customKcal != null && customKcal > 0

    val computedGoal = remember(
        selectedPreset,
        selectedActivity,
        effectiveWeight,
        settings,
        isManualActive,
        customKcal,
        customProtein,
        bmr
    ) {
        if (isManualActive && customKcal != null) {
            computeManualGoal(
                preset = selectedPreset,
                activityLevel = selectedActivity,
                customKcal = customKcal,
                customProteinG = customProtein,
                weightKg = effectiveWeight,
                bmr = bmr
            )
        } else {
            computeGoal(
                preset = selectedPreset,
                activityLevel = selectedActivity,
                weightKg = effectiveWeight,
                heightCm = settings.heightCm.toDouble(),
                ageYears = settings.ageYears,
                sex = settings.sex
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextForeground
                    )
                }
                Text(
                    text = "Set Goal",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground
                )
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // BLOCK 1: Preset Cards
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "CHOOSE YOUR GOAL",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.2.sp
                    )

                    val presets = listOf(
                        GoalPreset.FAT_LOSS to "Eats 500 kcal off daily burn to lose body fat steadily.",
                        GoalPreset.MAINTENANCE to "Matches your daily burn to keep your weight steady.",
                        GoalPreset.BUILD_MUSCLE to "Adds 250 kcal for lean muscle growth with regular training.",
                        GoalPreset.FITNESS to "Fuels performance and recovery at your natural daily burn."
                    )

                    presets.forEach { (preset, effectDescription) ->
                        val isSelected = selectedPreset == preset
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPreset = preset },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                            border = if (isSelected) BorderStroke(1.5.dp, AccentTeal) else BorderStroke(1.dp, BorderDefault)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = goalLabel[preset] ?: preset.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextForeground
                                )
                                Text(
                                    text = effectDescription,
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }

                // BLOCK 2: Activity Level Rows
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "ACTIVITY LEVEL",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.2.sp
                    )

                    val activities = listOf(
                        ActivityLevel.SEDENTARY to ("Sedentary" to "Desk job, little intentional daily movement"),
                        ActivityLevel.LIGHT to ("Light" to "1–3 light workouts or 7,000+ daily steps"),
                        ActivityLevel.MODERATE to ("Moderate" to "3–5 workouts a week, active throughout the day"),
                        ActivityLevel.ACTIVE to ("Active" to "Daily hard training or physical job")
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                        border = BorderStroke(1.dp, BorderDefault)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            activities.forEach { (level, desc) ->
                                val isSelected = selectedActivity == level
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedActivity = level }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = desc.first,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) TextForeground else TextMuted
                                        )
                                        Text(
                                            text = desc.second,
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )
                                    }
                                    if (isSelected) {
                                        Text(
                                            text = "Selected",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentTeal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // BLOCK 3: Live Preview
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "TARGET PREVIEW",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.2.sp
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
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
                            // Big Number with Unit
                            Text(
                                text = String.format(Locale.US, "%,d kcal / day", computedGoal.kcalTarget),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextForeground
                            )

                            // Derivation Line
                            val burnFormatted = String.format(Locale.US, "%,d", tdee.roundToInt())
                            val bmrFormatted = String.format(Locale.US, "%,d", bmr.roundToInt())
                            val weightInt = effectiveWeight.roundToInt()
                            val activityStr = selectedActivity.name.lowercase()

                            val bodyDesc = "$weightInt kg, ${settings.heightCm} cm, ${settings.ageYears} y, $activityStr activity"
                            val derivationBase = "Your body burns about $burnFormatted kcal a day (Mifflin-St Jeor, $bodyDesc)"
                            val derivationClause = when (selectedPreset) {
                                GoalPreset.FAT_LOSS -> " — fat loss eats 500 off that."
                                GoalPreset.BUILD_MUSCLE -> " — adds 250 kcal to build muscle."
                                GoalPreset.MAINTENANCE, GoalPreset.FITNESS, GoalPreset.CUSTOM -> "."
                            }

                            Text(
                                text = derivationBase + derivationClause,
                                fontSize = 12.sp,
                                color = TextMuted
                            )

                            // Macro Row
                            Text(
                                text = "Protein ${computedGoal.proteinTargetG}g · Carbs ${computedGoal.carbTargetG}g · Fat ${computedGoal.fatTargetG}g",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextMuted
                            )

                            // Below-BMR Warning
                            if (computedGoal.belowBmr) {
                                Text(
                                    text = "That's below your resting burn ($bmrFormatted kcal) — you can set it, but know what you're choosing.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = JudgementWarn
                                )
                            }

                            // Missing Body Data Hint
                            if (hasMissingBodyData) {
                                Text(
                                    text = "Add height, age and sex in Settings for a real estimate",
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }

                            // Manual Override Trigger
                            TextButton(
                                onClick = { isManualExpanded = !isManualExpanded },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = if (isManualExpanded) "Use calculated numbers" else "Use my own numbers",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentTeal
                                )
                            }

                            // Expanded Manual Fields
                            if (isManualExpanded) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = manualKcalInput,
                                            onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*$"""))) manualKcalInput = it },
                                            label = { Text("Kcal target", color = TextMuted) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = TextForeground,
                                                unfocusedTextColor = TextForeground,
                                                focusedBorderColor = AccentTeal,
                                                unfocusedBorderColor = BorderInput,
                                                focusedContainerColor = SecondaryFill,
                                                unfocusedContainerColor = SecondaryFill
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = manualProteinInput,
                                            onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*$"""))) manualProteinInput = it },
                                            label = { Text("Protein (g)", color = TextMuted) },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = TextForeground,
                                                unfocusedTextColor = TextForeground,
                                                focusedBorderColor = AccentTeal,
                                                unfocusedBorderColor = BorderInput,
                                                focusedContainerColor = SecondaryFill,
                                                unfocusedContainerColor = SecondaryFill
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Fixed Bottom Save Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = {
                        val change = GoalChange(
                            date = todayKey(),
                            preset = selectedPreset,
                            activityLevel = selectedActivity,
                            computed = computedGoal,
                            startWeightKg = latestRecordedWeight,
                            isManual = isManualActive
                        )
                        goalHistoryStorage.append(change)
                        settingsStorage.saveGoalTargets(
                            kcalTarget = computedGoal.kcalTarget,
                            proteinTarget = computedGoal.proteinTargetG,
                            carbTargetG = computedGoal.carbTargetG,
                            fatTargetG = computedGoal.fatTargetG,
                            activeGoal = selectedPreset
                        )
                        onBack()
                    },
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentTeal,
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "Set goal",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
