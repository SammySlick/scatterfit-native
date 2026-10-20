package com.scatterbrain.scatterfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.graphicsLayer
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
import com.scatterbrain.scatterfit.ui.theme.StateBad
import com.scatterbrain.scatterfit.ui.theme.StateLocked
import com.scatterbrain.scatterfit.ui.theme.TealHighlight
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun FoodLoggerScreen(
    viewModel: FoodTrackerViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mealsForDay by viewModel.mealsForDay.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val recipes by viewModel.recipes.collectAsState()
    val favourites by viewModel.favourites.collectAsState()

    val scrollState = rememberScrollState()

    var isEditingRecipe by remember { mutableStateOf(false) }
    var recipeBeingEdited by remember { mutableStateOf<Recipe?>(null) }
    var manageMenuRecipeId by remember { mutableStateOf<String?>(null) }

    var saveAsFavouriteCheckbox by remember { mutableStateOf(false) }

    val saveInteractionSource = remember { MutableInteractionSource() }
    val isSavePressed by saveInteractionSource.collectIsPressedAsState()
    val saveAlpha by animateFloatAsState(
        targetValue = if (isSavePressed) 0.70f else 1f,
        animationSpec = if (isSavePressed) tween(80) else spring(stiffness = Spring.StiffnessHigh),
        label = "foodSaveAlpha"
    )
    val saveScale by animateFloatAsState(
        targetValue = if (isSavePressed) 0.96f else 1f,
        animationSpec = if (isSavePressed) tween(80) else spring(stiffness = Spring.StiffnessHigh),
        label = "foodSaveScale"
    )
    val haptic = LocalHapticFeedback.current

    var showSaveConfirmation by remember { mutableStateOf(false) }
    var saveConfirmationKey by remember { mutableStateOf(0) }

    LaunchedEffect(saveConfirmationKey) {
        if (showSaveConfirmation) {
            kotlinx.coroutines.delay(2000L)
            showSaveConfirmation = false
        }
    }

    BackHandler {
        if (isEditingRecipe) {
            isEditingRecipe = false
            recipeBeingEdited = null
        } else {
            onBackClick()
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
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (isEditingRecipe) {
                        isEditingRecipe = false
                        recipeBeingEdited = null
                    } else {
                        onBackClick()
                    }
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextForeground
                    )
                }
                Text(
                    text = if (isEditingRecipe) {
                        if (recipeBeingEdited != null) "Edit Meal Recipe" else "New Meal Recipe"
                    } else "Log Meal",
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
                if (isEditingRecipe) {
                    // PART 3 — RECIPE EDITOR SECTION
                    RecipeEditorCard(
                        initialRecipe = recipeBeingEdited,
                        onSaveRecipe = { recipeToSave ->
                            viewModel.savedFoodStore?.upsertRecipe(recipeToSave)
                            viewModel.refreshSavedFoods()
                            isEditingRecipe = false
                            recipeBeingEdited = null
                        },
                        onCancel = {
                            isEditingRecipe = false
                            recipeBeingEdited = null
                        }
                    )
                } else {
                    // 1. RECENTS (Existing derived presets)
                    if (presets.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "RECENTS",
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

                    // 2. FAVOURITES SECTION
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "FAVOURITES",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 1.2.sp
                        )
                        if (favourites.isEmpty()) {
                            Text(
                                text = "Star foods you log often — they'll live here",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                favourites.forEach { fav ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(SecondaryFill)
                                            .border(BorderStroke(1.dp, TealHighlight.copy(alpha = 0.5f)), RoundedCornerShape(16.dp))
                                            .clickable {
                                                viewModel.logFavourite(fav)
                                                showSaveConfirmation = true
                                                saveConfirmationKey++
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text("★", color = TealHighlight, fontSize = 11.sp)
                                            Text(
                                                text = "${fav.name} · ${fav.kcalPerServing} kcal",
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
                    }

                    // 3. SAVED MEALS (Recipes + Pool)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SAVED MEALS",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                letterSpacing = 1.2.sp
                            )
                            TextButton(onClick = {
                                recipeBeingEdited = null
                                isEditingRecipe = true
                            }) {
                                Text("+ New Recipe", color = TealHighlight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (recipes.isEmpty()) {
                            Text(
                                text = "No saved recipes yet. Create batch meals to track portions.",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        } else {
                            recipes.forEach { recipe ->
                                val isMenuOpen = manageMenuRecipeId == recipe.id

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                viewModel.logRecipePortion(recipe)
                                                showSaveConfirmation = true
                                                saveConfirmationKey++
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                manageMenuRecipeId = if (isMenuOpen) null else recipe.id
                                            }
                                        ),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                                    border = BorderStroke(1.dp, BorderDefault)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = recipe.name,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextForeground,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            // Pool Badge
                                            if (recipe.portionsInPool > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(TealHighlight.copy(alpha = 0.15f))
                                                        .border(BorderStroke(1.dp, TealHighlight.copy(alpha = 0.4f)), RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "×${recipe.portionsInPool} left",
                                                        color = TealHighlight,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(SecondaryFill)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Pool empty",
                                                        color = TextMuted,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }

                                        // Macros Line
                                        val macroDetails = buildList {
                                            add("${recipe.perPortionKcal} kcal")
                                            add("${recipe.perPortionProtein}g protein")
                                            recipe.perPortionCarbs?.let { add("${it}g carbs") }
                                            recipe.perPortionFat?.let { add("${it}g fat") }
                                            recipe.portionGrams?.let { add("${it}g") }
                                        }.joinToString(" · ")

                                        Text(
                                            text = macroDetails,
                                            fontSize = 11.sp,
                                            color = TextMuted
                                        )

                                        if (!recipe.note.isNullOrBlank()) {
                                            Text(
                                                text = recipe.note,
                                                fontSize = 11.sp,
                                                color = TextMuted.copy(alpha = 0.8f)
                                            )
                                        }

                                        // Inline Expandable Management Menu (Edit / Refill pool / Delete)
                                        if (isMenuOpen) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                TextButton(onClick = {
                                                    recipeBeingEdited = recipe
                                                    isEditingRecipe = true
                                                    manageMenuRecipeId = null
                                                }) {
                                                    Text("Edit", color = TealHighlight, fontSize = 12.sp)
                                                }

                                                TextButton(onClick = {
                                                    val updated = recipe.copy(portionsInPool = recipe.portionsInPool + 1)
                                                    viewModel.savedFoodStore?.upsertRecipe(updated)
                                                    viewModel.refreshSavedFoods()
                                                }) {
                                                    Text("+1 Portion", color = TextForeground, fontSize = 12.sp)
                                                }

                                                TextButton(onClick = {
                                                    viewModel.savedFoodStore?.deleteRecipe(recipe.id)
                                                    viewModel.refreshSavedFoods()
                                                    manageMenuRecipeId = null
                                                }) {
                                                    Text("Delete", color = StateBad, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. MANUAL ENTRY CARD (below everything)
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

                            // Meal Type Selector Chips: Breakfast / Lunch / Dinner / Snacks
                            val mealTypes = listOf("breakfast", "lunch", "dinner", "snacks")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                mealTypes.forEach { type ->
                                    val isSelected = viewModel.mealTypeInput.equals(type, ignoreCase = true)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) TealHighlight else SecondaryFill)
                                            .border(
                                                BorderStroke(1.dp, if (isSelected) TealHighlight else BorderDefault),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { viewModel.mealTypeInput = type }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = type.replaceFirstChar { it.uppercase() },
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.Black else TextForeground
                                        )
                                    }
                                }
                            }

                            // Meal Name
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

                            // Calories
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

                            // Macros Row
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

                            // "★ Save as favourite" checkbox & "Save as meal" action
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { saveAsFavouriteCheckbox = !saveAsFavouriteCheckbox }
                                ) {
                                    Checkbox(
                                        checked = saveAsFavouriteCheckbox,
                                        onCheckedChange = { saveAsFavouriteCheckbox = it },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = TealHighlight,
                                            checkmarkColor = Color.Black
                                        )
                                    )
                                    Text("★ Save as favourite", color = TextForeground, fontSize = 12.sp)
                                }

                                TextButton(
                                    onClick = {
                                        val kcal = viewModel.kcalInput.toDoubleOrNull()
                                        if (kcal != null) {
                                            recipeBeingEdited = Recipe(
                                                name = viewModel.mealName.trim().ifBlank { "New Meal" },
                                                perPortionKcal = kcal.toInt(),
                                                perPortionProtein = viewModel.proteinInput.toDoubleOrNull()?.toInt() ?: 0,
                                                perPortionCarbs = viewModel.carbsInput.toDoubleOrNull()?.toInt(),
                                                perPortionFat = viewModel.fatInput.toDoubleOrNull()?.toInt()
                                            )
                                            isEditingRecipe = true
                                        }
                                    },
                                    enabled = viewModel.kcalInput.isNotBlank()
                                ) {
                                    Text("Save as meal →", color = if (viewModel.kcalInput.isNotBlank()) TealHighlight else TextMuted, fontSize = 12.sp)
                                }
                            }

                            // Save & Cancel Buttons
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
                                        if (viewModel.saveCurrentMeal(saveAsFavourite = saveAsFavouriteCheckbox)) {
                                            saveAsFavouriteCheckbox = false
                                            showSaveConfirmation = true
                                            saveConfirmationKey++
                                        }
                                    },
                                    enabled = viewModel.kcalInput.isNotBlank(),
                                    interactionSource = saveInteractionSource,
                                    modifier = Modifier
                                        .weight(1f)
                                        .graphicsLayer {
                                            this.alpha = saveAlpha
                                            this.scaleX = saveScale
                                            this.scaleY = saveScale
                                        },
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

                    // 5. Saved Meals for Current Day List
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
                                                if (!meal.portionLabel.isNullOrBlank()) {
                                                    Text(
                                                        text = "· ${meal.portionLabel}",
                                                        fontSize = 12.sp,
                                                        color = TextMuted,
                                                        maxLines = 1
                                                    )
                                                }
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
                                            modifier = Modifier.size(28.dp)
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
}

/**
 * PART 3 — RECIPE EDITOR (Inline in the same screen)
 */
@Composable
fun RecipeEditorCard(
    initialRecipe: Recipe?,
    onSaveRecipe: (Recipe) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(initialRecipe?.name ?: "") }
    var kcal by remember { mutableStateOf(initialRecipe?.perPortionKcal?.toString() ?: "") }
    var protein by remember { mutableStateOf(initialRecipe?.perPortionProtein?.toString() ?: "") }
    var carbs by remember { mutableStateOf(initialRecipe?.perPortionCarbs?.toString() ?: "") }
    var fat by remember { mutableStateOf(initialRecipe?.perPortionFat?.toString() ?: "") }
    var grams by remember { mutableStateOf(initialRecipe?.portionGrams?.toString() ?: "") }
    var note by remember { mutableStateOf(initialRecipe?.note ?: "") }
    var portionsInPool by remember { mutableIntStateOf(initialRecipe?.portionsInPool ?: 0) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = BorderStroke(1.dp, BorderDefault)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (initialRecipe != null) "EDIT RECIPE" else "NEW RECIPE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.2.sp
            )

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Recipe name *", color = TextMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextForeground,
                    unfocusedTextColor = TextForeground,
                    focusedBorderColor = TealHighlight,
                    unfocusedBorderColor = BorderInput
                )
            )

            // Kcal & Protein
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = kcal,
                    onValueChange = { kcal = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Kcal / portion *", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextForeground,
                        unfocusedTextColor = TextForeground,
                        focusedBorderColor = TealHighlight,
                        unfocusedBorderColor = BorderInput
                    )
                )

                OutlinedTextField(
                    value = protein,
                    onValueChange = { protein = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Protein (g) *", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextForeground,
                        unfocusedTextColor = TextForeground,
                        focusedBorderColor = TealHighlight,
                        unfocusedBorderColor = BorderInput
                    )
                )
            }

            // Optional Carbs, Fat, Grams
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = carbs,
                    onValueChange = { carbs = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Carbs (g)", color = TextMuted, fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextForeground,
                        unfocusedTextColor = TextForeground,
                        focusedBorderColor = TealHighlight,
                        unfocusedBorderColor = BorderInput
                    )
                )

                OutlinedTextField(
                    value = fat,
                    onValueChange = { fat = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Fat (g)", color = TextMuted, fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextForeground,
                        unfocusedTextColor = TextForeground,
                        focusedBorderColor = TealHighlight,
                        unfocusedBorderColor = BorderInput
                    )
                )

                OutlinedTextField(
                    value = grams,
                    onValueChange = { grams = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Grams", color = TextMuted, fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextForeground,
                        unfocusedTextColor = TextForeground,
                        focusedBorderColor = TealHighlight,
                        unfocusedBorderColor = BorderInput
                    )
                )
            }

            // Note
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)", color = TextMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextForeground,
                    unfocusedTextColor = TextForeground,
                    focusedBorderColor = TealHighlight,
                    unfocusedBorderColor = BorderInput
                )
            )

            // POOL row: "Cooked a batch?" -> inline stepper: portions count (0-99)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SecondaryFill)
                    .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Cooked a batch?", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextForeground)
                    Text("Pool portions available", fontSize = 11.sp, color = TextMuted)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkCardSurface)
                            .clickable { portionsInPool = (portionsInPool - 1).coerceAtLeast(0) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("−", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextForeground)
                    }

                    Text(
                        text = "$portionsInPool",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (portionsInPool > 0) TealHighlight else TextMuted
                    )

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(DarkCardSurface)
                            .clickable { portionsInPool = (portionsInPool + 1).coerceAtMost(99) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextForeground)
                    }
                }
            }

            // Buttons: Save & Cancel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel", color = TextMuted)
                }

                Button(
                    onClick = {
                        val parsedKcal = kcal.toIntOrNull() ?: return@Button
                        val parsedProtein = protein.toIntOrNull() ?: return@Button
                        val recipe = Recipe(
                            id = initialRecipe?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim().ifBlank { "Recipe" },
                            perPortionKcal = parsedKcal,
                            perPortionProtein = parsedProtein,
                            perPortionCarbs = carbs.toIntOrNull(),
                            perPortionFat = fat.toIntOrNull(),
                            portionGrams = grams.toIntOrNull(),
                            portionsInPool = portionsInPool,
                            note = note.trim().ifEmpty { null },
                            createdAt = initialRecipe?.createdAt ?: System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                        onSaveRecipe(recipe)
                    },
                    enabled = name.isNotBlank() && kcal.isNotBlank() && protein.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TealHighlight,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Recipe", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FoodLoggerScreenPreview() {
    ScatterFitTheme {
        val vm = FoodTrackerViewModel(initialDayKey = "2026-10-14")
        FoodLoggerScreen(
            viewModel = vm,
            onBackClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun FoodLoggerScreenWithPresetsPreview() {
    ScatterFitTheme {
        val vm = FoodTrackerViewModel(initialDayKey = "2026-10-14").apply {
            applyPreset(
                MealPreset(
                    name = "Protein Oatmeal",
                    kcal = 420.0,
                    protein = 32.0,
                    carbs = 54.0,
                    fat = 8.0,
                    lastUsedTimestamp = System.currentTimeMillis()
                )
            )
        }
        FoodLoggerScreen(
            viewModel = vm,
            onBackClick = {}
        )
    }
}
