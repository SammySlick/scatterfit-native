package com.scatterbrain.scatterfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.ui.theme.AppBackground
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.BorderInput
import com.scatterbrain.scatterfit.ui.theme.DarkCardSurface
import com.scatterbrain.scatterfit.ui.theme.JudgementBad
import com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.StateLocked
import com.scatterbrain.scatterfit.ui.theme.TealHighlight
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodLoggerScreen(
    viewModel: FoodTrackerViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackClick)

    val mealsForDay by viewModel.mealsForDay.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val scrollState = rememberScrollState()

    val saveInteractionSource = remember { MutableInteractionSource() }
    val isSavePressed by saveInteractionSource.collectIsPressedAsState()
    val saveScale by animateFloatAsState(if (isSavePressed) 0.96f else 1f, label = "foodSaveScale")
    val haptic = LocalHapticFeedback.current

    var showSaveConfirmation by remember { mutableStateOf(false) }
    var saveConfirmationKey by remember { mutableStateOf(0) }

    LaunchedEffect(saveConfirmationKey) {
        if (showSaveConfirmation) {
            kotlinx.coroutines.delay(2000L)
            showSaveConfirmation = false
        }
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
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextForeground
                    )
                }
                Text(
                    text = "Log Meal",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Presets Section: top 8 frequently logged chips (hidden if empty)
                if (presets.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "FREQUENTLY LOGGED",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.2.sp
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            presets.forEach { preset ->
                                val displayName = preset.name.ifBlank { "Meal" }
                                val kcalStr = if (preset.kcal % 1.0 == 0.0) "${preset.kcal.toInt()} kcal" else "${preset.kcal} kcal"
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(SecondaryFill)
                                        .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(16.dp))
                                        .clickable { viewModel.applyPreset(preset) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "$displayName · $kcalStr",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = TextForeground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Manual Entry Card
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "ADD MEAL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.2.sp
                        )

                        // Meal Name (optional)
                        OutlinedTextField(
                            value = viewModel.mealName,
                            onValueChange = { viewModel.mealName = it },
                            label = { Text("Meal name (optional)", color = TextMuted) },
                            placeholder = { Text("e.g. Chicken wrap", color = TextMuted.copy(alpha = 0.5f)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextForeground,
                                unfocusedTextColor = TextForeground,
                                focusedBorderColor = TealHighlight,
                                unfocusedBorderColor = BorderInput,
                                cursorColor = TealHighlight
                            )
                        )

                        // Calories (required)
                        OutlinedTextField(
                            value = viewModel.kcalInput,
                            onValueChange = { input ->
                                viewModel.kcalInput = input.filter { it.isDigit() || it == '.' }
                            },
                            label = { Text("Calories (kcal) *", color = TextMuted) },
                            placeholder = { Text("Required", color = TextMuted.copy(alpha = 0.5f)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextForeground,
                                unfocusedTextColor = TextForeground,
                                focusedBorderColor = TealHighlight,
                                unfocusedBorderColor = BorderInput,
                                cursorColor = TealHighlight
                            )
                        )

                        // Macros Row: Protein, Carbs, Fat (all optional)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = viewModel.proteinInput,
                                onValueChange = { input ->
                                    viewModel.proteinInput = input.filter { it.isDigit() || it == '.' }
                                },
                                label = { Text("Protein (g)", color = TextMuted, fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextForeground,
                                    unfocusedTextColor = TextForeground,
                                    focusedBorderColor = TealHighlight,
                                    unfocusedBorderColor = BorderInput,
                                    cursorColor = TealHighlight
                                )
                            )

                            OutlinedTextField(
                                value = viewModel.carbsInput,
                                onValueChange = { input ->
                                    viewModel.carbsInput = input.filter { it.isDigit() || it == '.' }
                                },
                                label = { Text("Carbs (g)", color = TextMuted, fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextForeground,
                                    unfocusedTextColor = TextForeground,
                                    focusedBorderColor = TealHighlight,
                                    unfocusedBorderColor = BorderInput,
                                    cursorColor = TealHighlight
                                )
                            )

                            OutlinedTextField(
                                value = viewModel.fatInput,
                                onValueChange = { input ->
                                    viewModel.fatInput = input.filter { it.isDigit() || it == '.' }
                                },
                                label = { Text("Fat (g)", color = TextMuted, fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextForeground,
                                    unfocusedTextColor = TextForeground,
                                    focusedBorderColor = TealHighlight,
                                    unfocusedBorderColor = BorderInput,
                                    cursorColor = TealHighlight
                                )
                            )
                        }

                        // Buttons: Save & Cancel
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = onBackClick,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel", color = TextMuted)
                            }

                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (viewModel.saveCurrentMeal()) {
                                        showSaveConfirmation = true
                                        saveConfirmationKey++
                                    }
                                },
                                enabled = viewModel.kcalInput.isNotBlank(),
                                interactionSource = saveInteractionSource,
                                modifier = Modifier
                                    .weight(1f)
                                    .scale(saveScale),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = TealHighlight,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Save", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (showSaveConfirmation) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "✓ Logged",
                                    color = StateLocked,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Saved Meals for Current Day List
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "SAVED TODAY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.2.sp
                    )

                    if (mealsForDay.isEmpty()) {
                        Text(
                            text = "No meals logged yet",
                            fontSize = 12.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
                        mealsForDay.forEach { meal ->
                            val timeStr = timeFormat.format(Date(meal.timestamp))
                            val macroParts = mutableListOf<String>()
                            meal.protein?.let { macroParts.add("P: ${it.toInt()}g") }
                            meal.carbs?.let { macroParts.add("C: ${it.toInt()}g") }
                            meal.fat?.let { macroParts.add("F: ${it.toInt()}g") }
                            val macroSummary = macroParts.joinToString(" · ")

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                                border = BorderStroke(1.dp, BorderDefault)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = meal.name.ifBlank { "Meal" },
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TextForeground,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = timeStr,
                                                fontSize = 11.sp,
                                                color = TextMuted
                                            )
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "%,d kcal".format(meal.kcal.toInt()),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TealHighlight
                                            )
                                            if (macroSummary.isNotEmpty()) {
                                                Text(
                                                    text = macroSummary,
                                                    fontSize = 11.sp,
                                                    color = TextMuted
                                                )
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = { viewModel.deleteMeal(meal.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Text(
                                            text = "✕",
                                            color = JudgementBad,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
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
fun FoodLoggerEmptyPreview() {
    ScatterFitTheme {
        val vm = FoodTrackerViewModel(initialDayKey = "2026-10-14")
        FoodLoggerScreen(viewModel = vm, onBackClick = {})
    }
}

@Preview(showBackground = true)
@Composable
fun FoodLoggerWithPresetsPreview() {
    ScatterFitTheme {
        val vm = FoodTrackerViewModel(initialDayKey = "2026-10-14").apply {
            inMemoryStorage["2026-10-14"] = mutableListOf(
                LoggedMeal(name = "Chicken wrap", kcal = 450.0, protein = 35.0, carbs = 40.0, fat = 15.0, dayKey = "2026-10-14"),
                LoggedMeal(name = "Greek yogurt & berries", kcal = 220.0, protein = 20.0, carbs = 25.0, fat = 3.0, dayKey = "2026-10-14")
            )
            setDayKey("2026-10-14")
            refreshPresets()
        }
        FoodLoggerScreen(viewModel = vm, onBackClick = {})
    }
}
