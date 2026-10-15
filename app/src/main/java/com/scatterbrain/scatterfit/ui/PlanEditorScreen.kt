package com.scatterbrain.scatterfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.ui.theme.AccentTeal
import com.scatterbrain.scatterfit.ui.theme.AppBackground
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.BorderInput
import com.scatterbrain.scatterfit.ui.theme.DarkCardSurface
import com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme
import com.scatterbrain.scatterfit.ui.theme.TealHighlight
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun PlanEditorScreen(
    storage: TrainingPlanStorage,
    onPlanSaved: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackClick)

    val initialPlan = remember { storage.loadPlan() }
    val planState = remember {
        mutableStateMapOf<String, String>().apply {
            DayOfWeek.entries.forEach { day ->
                put(day.name, initialPlan[day.name] ?: "")
            }
        }
    }

    val scrollState = rememberScrollState()

    val saveInteractionSource = remember { MutableInteractionSource() }
    val isSavePressed by saveInteractionSource.collectIsPressedAsState()
    val saveScale by animateFloatAsState(if (isSavePressed) 0.96f else 1f, label = "planSaveScale")
    val haptic = LocalHapticFeedback.current

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
                    text = "Weekly Plan",
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DayOfWeek.entries.forEach { day ->
                    val dayName = day.getDisplayName(TextStyle.FULL, Locale.US)
                    val textVal = planState[day.name] ?: ""
                    val isRestDay = textVal.isBlank()

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                        border = BorderStroke(1.dp, BorderDefault)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = dayName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextForeground
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Rest day",
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    )
                                    Checkbox(
                                        checked = isRestDay,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                planState[day.name] = ""
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = AccentTeal,
                                            uncheckedColor = TextMuted,
                                            checkmarkColor = Color.Black
                                        ),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = textVal,
                                onValueChange = { planState[day.name] = it },
                                placeholder = { Text("Session name (e.g. Upper A)", color = TextMuted.copy(alpha = 0.5f)) },
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
                        }
                    }
                }

                // Buttons: Save & Cancel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
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
                            storage.savePlan(planState)
                            onPlanSaved()
                        },
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
                        Text("Save Plan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PlanEditorScreenPreview() {
    ScatterFitTheme {
        val context = androidx.compose.ui.platform.LocalContext.current
        val storage = remember { TrainingPlanStorage(context) }
        PlanEditorScreen(storage = storage, onPlanSaved = {}, onBackClick = {})
    }
}
