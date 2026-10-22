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
import androidx.compose.runtime.remember
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
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val goalHistoryStorage = remember(context) { GoalHistoryStorage(context) }
    val activeGoal = remember(goalHistoryStorage) { goalHistoryStorage.activeGoal() }
    val settingsStorage = remember(context) { SettingsStorage(context) }
    val settings = remember(settingsStorage) { settingsStorage.loadSettings() }

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
                // TODO: bind to ScoringSettings + settings UI
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
                            SettingRow(
                                label = "Calories target",
                                value = String.format(Locale.US, "%,d kcal", settings.kcalTarget)
                            )
                            SettingRow(
                                label = "Steps target",
                                value = String.format(Locale.US, "%,d", settings.stepTarget)
                            )
                            SettingRow(
                                label = "Protein target",
                                value = "${settings.proteinTarget} g"
                            )
                            SettingRow(
                                label = "Carbs target",
                                value = if (settings.carbTargetG > 0) "${settings.carbTargetG} g" else "—"
                            )
                            SettingRow(
                                label = "Fat target",
                                value = if (settings.fatTargetG > 0) "${settings.fatTargetG} g" else "—"
                            )
                            SettingRow(label = "Sleep target", value = "8h")
                            SettingRow(label = "Alcohol", value = "14 units/week")
                            SettingRow(
                                label = "Zone 2",
                                value = "90 min/week · Zone 3: 45 · Zone 4: 30 · Zone 5: 15"
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
