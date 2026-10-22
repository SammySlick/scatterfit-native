package com.scatterbrain.scatterfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.core.goalLabel
import com.scatterbrain.scatterfit.data.GoalHistoryStorage
import com.scatterbrain.scatterfit.data.SettingsStorage
import com.scatterbrain.scatterfit.ui.theme.AppBackground
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.DarkCardSurface
import com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenGoalSet: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var activeEditSheet by remember { mutableStateOf<TargetEditType?>(null) }
    var settingsRefreshKey by remember { mutableIntStateOf(0) }

    BackHandler(enabled = true) {
        if (activeEditSheet != null) {
            activeEditSheet = null
        } else {
            onBack()
        }
    }

    val context = LocalContext.current
    val goalHistoryStorage = remember(context) { GoalHistoryStorage(context) }
    val activeGoal = remember(goalHistoryStorage) { goalHistoryStorage.activeGoal() }
    val settingsStorage = remember(context) { SettingsStorage(context) }
    val settings = remember(settingsStorage, settingsRefreshKey) { settingsStorage.loadSettings() }

    val weightStorage = remember(context) { WeightStorage(context) }
    val latestRecordedWeight = remember(weightStorage) {
        weightStorage.loadAll().maxByOrNull { it.timestamp }?.kg
            ?: com.scatterbrain.scatterfit.data.DailyRepository.daily.value?.weight?.let { map ->
                val maxDay = map.keys.maxOrNull()
                maxDay?.let { map[it] }
            }
    }
    val effectiveWeight = when {
        latestRecordedWeight != null && latestRecordedWeight > 0.0 -> latestRecordedWeight
        settings.weightTarget > 0.0 -> settings.weightTarget
        else -> 80.0
    }

    val goalValueText = if (activeGoal != null) {
        val label = goalLabel[activeGoal.preset] ?: activeGoal.preset.name
        val formattedDate = try {
            LocalDate.parse(activeGoal.date).format(DateTimeFormatter.ofPattern("d MMM", Locale.US))
        } catch (_: Exception) {
            activeGoal.date
        }
        "$label · since $formattedDate"
    } else {
        "Not set"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppBackground
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
                    text = "Settings",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground
                )
            }

            // Scrollable Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // SECTION: Goal (TOP of Settings)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "GOAL",
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
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SettingRow(
                                label = "Goal",
                                value = goalValueText,
                                isClickable = true,
                                onClick = onOpenGoalSet
                            )
                        }
                    }
                }

                // SECTION: Targets
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "TARGETS",
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
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val caloriesSub = if (settings.carbTargetG > 0 && settings.fatTargetG > 0) {
                                String.format(
                                    Locale.US,
                                    "%,d kcal · P %dg / C %dg / F %dg",
                                    settings.kcalTarget,
                                    settings.proteinTarget,
                                    settings.carbTargetG,
                                    settings.fatTargetG
                                )
                            } else {
                                String.format(Locale.US, "%,d kcal · P %dg", settings.kcalTarget, settings.proteinTarget)
                            }
                            SettingRow(
                                label = "Calories",
                                value = caloriesSub,
                                isClickable = true,
                                onClick = { activeEditSheet = TargetEditType.CALORIES }
                            )
                            SettingRow(
                                label = "Steps target",
                                value = String.format(Locale.US, "%,d", settings.stepTarget),
                                isClickable = true,
                                onClick = { activeEditSheet = TargetEditType.STEPS }
                            )
                            SettingRow(
                                label = "Sleep target",
                                value = if (settings.sleepTargetHours % 1.0 == 0.0) "${settings.sleepTargetHours.toInt()}h" else "%.1fh".format(Locale.US, settings.sleepTargetHours),
                                isClickable = true,
                                onClick = { activeEditSheet = TargetEditType.SLEEP }
                            )
                            SettingRow(
                                label = "Alcohol",
                                value = "${settings.unitsPerWeek} units/week",
                                isClickable = true,
                                onClick = { activeEditSheet = TargetEditType.ALCOHOL }
                            )
                            val exerciseValue = if (settingsStorage.isExerciseModeSimple()) {
                                "${settingsStorage.getExerciseTimesPerWeek()}× / week · ${settings.exerciseTargetPerWeek} min"
                            } else {
                                "${settings.exerciseTargetPerWeek} min/week · Z2 ${settings.z2Target} / Z3 ${settings.z3Target} / Z4 ${settings.z4Target} / Z5 ${settings.z5Target}"
                            }
                            SettingRow(
                                label = "Exercise",
                                value = exerciseValue,
                                isClickable = true,
                                onClick = { activeEditSheet = TargetEditType.EXERCISE }
                            )
                        }
                    }
                }

                // SECTION: Body (Read-only)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "BODY",
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
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SettingRow(
                                label = "Height",
                                value = if (settings.heightCm > 0) "${settings.heightCm} cm" else "Not set",
                                isClickable = true,
                                onClick = onOpenGoalSet
                            )
                            SettingRow(
                                label = "Age",
                                value = if (settings.ageYears > 0) "${settings.ageYears} y" else "Not set",
                                isClickable = true,
                                onClick = onOpenGoalSet
                            )
                            SettingRow(
                                label = "Sex",
                                value = settings.sex.name.lowercase().replaceFirstChar { it.uppercase() },
                                isClickable = true,
                                onClick = onOpenGoalSet
                            )
                            SettingRow(
                                label = "Target weight",
                                value = if (settings.weightTarget > 0.0) {
                                    if (settings.weightTarget % 1.0 == 0.0) "${settings.weightTarget.toInt()} kg"
                                    else "%.1f kg".format(Locale.US, settings.weightTarget)
                                } else "Not set",
                                isClickable = true,
                                onClick = onOpenGoalSet
                            )
                        }
                    }
                }

                // SECTION: Appearance
                // TODO: bind to theme settings when theme switching is built
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "APPEARANCE",
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
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SettingRow(label = "Theme", value = "Dark (fixed)")
                        }
                    }
                }

                // SECTION: Account
                // TODO: bind to login / account infrastructure when built
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ACCOUNT",
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
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SettingRow(label = "Signed in", value = "(local)")
                            SettingRow(label = "Sync", value = "Health Connect")
                        }
                    }
                }

                // SECTION: Data
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "DATA",
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
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SettingRow(
                                label = "Debug",
                                value = "",
                                isClickable = true,
                                onClick = onOpenDebug
                            )
                        }
                    }
                    Text(
                        text = "All data stays on this device. Health Connect reads only — nothing is shared.",
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }

    if (activeEditSheet != null) {
        TargetEditSheet(
            editType = activeEditSheet!!,
            settingsStorage = settingsStorage,
            currentSettings = settings,
            effectiveWeightKg = effectiveWeight,
            onDismissRequest = { activeEditSheet = null },
            onSaveSuccess = {
                settingsRefreshKey++
                activeEditSheet = null
            }
        )
    }
}

@Composable
private fun SettingRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    isClickable: Boolean = false,
    onClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isClickable) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextForeground,
            modifier = Modifier.weight(1f, fill = false)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (value.isNotBlank()) {
                Text(
                    text = value,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
            if (isClickable) {
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
fun SettingsScreenPreview() {
    ScatterFitTheme {
        SettingsScreen(
            onBack = {},
            onOpenDebug = {}
        )
    }
}
