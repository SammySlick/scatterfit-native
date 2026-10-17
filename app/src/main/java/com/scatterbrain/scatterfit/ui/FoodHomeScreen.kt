package com.scatterbrain.scatterfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.scatterbrain.scatterfit.data.DailyRepository
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.core.ScoringSettings
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.ui.theme.AccentTeal
import com.scatterbrain.scatterfit.ui.theme.AppBackground
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.BorderInput
import com.scatterbrain.scatterfit.ui.theme.DarkCardSurface
import com.scatterbrain.scatterfit.ui.theme.JudgementWarn
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.StateBad
import com.scatterbrain.scatterfit.ui.theme.StateLocked
import com.scatterbrain.scatterfit.ui.theme.TealHighlight
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class MacroCardMode { CONSUMED, REMAINING }

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun FoodHomeScreen(
    viewModel: FoodTrackerViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    settings: ScoringSettings = remember { ScoringSettings() },
) {
    BackHandler(onBack = onBackClick)

    val storedMeals by viewModel.mealsForDay.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val recipes by viewModel.recipes.collectAsState()
    val favourites by viewModel.favourites.collectAsState()
    val repoDaily by DailyRepository.daily.collectAsState()

    val activeDay = viewModel.activeDayKey
    val today = remember { todayKey() }

    val pipelineMeals = repoDaily?.meals?.get(activeDay) ?: emptyList()

    val displayMeals = remember(storedMeals, pipelineMeals, activeDay) {
        if (storedMeals.isNotEmpty()) {
            storedMeals
        } else if (pipelineMeals.isNotEmpty()) {
            pipelineMeals.map { it.toLoggedMeal(activeDay) }
        } else {
            emptyList()
        }
    }

    var macroMode by remember { mutableStateOf(MacroCardMode.CONSUMED) }

    // Section expansion: Breakfast expanded by default, others collapsed
    var expandedSections by remember {
        mutableStateOf(setOf("breakfast"))
    }

    // Inline logging / entry mode
    var isLoggerOpen by remember { mutableStateOf(false) }

    // Search bar state
    var searchQuery by remember { mutableStateOf("") }

    var itemPendingDeleteId by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    // Date formatting for Day Header: e.g. "Today, Oct 17"
    val dateHeaderLabel = remember(activeDay, today) {
        runCatching {
            val date = LocalDate.parse(activeDay)
            val todayDate = LocalDate.parse(today)
            val formatted = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
            when (date) {
                todayDate -> "Today, $formatted"
                todayDate.minusDays(1) -> "Yesterday, $formatted"
                todayDate.plusDays(1) -> "Tomorrow, $formatted"
                else -> "${date.format(DateTimeFormatter.ofPattern("EEE", Locale.US))}, $formatted"
            }
        }.getOrDefault(activeDay)
    }

    // Totals for active day matching DailyRepository & Day Card parity
    val calFromDaily = repoDaily?.caloriesEaten?.get(activeDay)
    val totalKcal = (calFromDaily ?: displayMeals.sumOf { it.kcal }).roundToInt()

    val protFromDaily = repoDaily?.protein?.get(activeDay)
    val totalProtein = (protFromDaily ?: displayMeals.sumOf { it.protein ?: 0.0 }).roundToInt()

    val carbsFromDaily = repoDaily?.carbs?.get(activeDay)
    val totalCarbs = (carbsFromDaily ?: displayMeals.sumOf { it.carbs ?: 0.0 }).roundToInt()

    val fatFromDaily = repoDaily?.fat?.get(activeDay)
    val totalFat = (fatFromDaily ?: displayMeals.sumOf { it.fat ?: 0.0 }).roundToInt()

    val kcalTarget = settings.kcalTarget
    val proteinTarget = settings.proteinTarget

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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextForeground
                    )
                }
                Text(
                    text = "Food",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground
                )
                // Spacer for balanced center alignment
                Spacer(modifier = Modifier.size(48.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. DAY HEADER: "< Today, Oct 17 >"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val prevDay = runCatching {
                                LocalDate.parse(activeDay).minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
                            }.getOrDefault(activeDay)
                            viewModel.setDayKey(prevDay)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text("‹", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextForeground)
                    }

                    Text(
                        text = dateHeaderLabel,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextForeground
                    )

                    IconButton(
                        onClick = {
                            val nextDay = runCatching {
                                LocalDate.parse(activeDay).plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
                            }.getOrDefault(activeDay)
                            viewModel.setDayKey(nextDay)
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text("›", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextForeground)
                    }
                }

                // 2. MACRO CARD (the BWS hero)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                    border = BorderStroke(1.dp, BorderDefault)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Toggle: CONSUMED | REMAINING
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SecondaryFill)
                                .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(8.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (macroMode == MacroCardMode.CONSUMED) TealHighlight else Color.Transparent)
                                    .clickable { macroMode = MacroCardMode.CONSUMED }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "CONSUMED",
                                    fontSize = 11.sp,
                                    fontWeight = if (macroMode == MacroCardMode.CONSUMED) FontWeight.Bold else FontWeight.Medium,
                                    color = if (macroMode == MacroCardMode.CONSUMED) Color.Black else TextMuted
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (macroMode == MacroCardMode.REMAINING) TealHighlight else Color.Transparent)
                                    .clickable { macroMode = MacroCardMode.REMAINING }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "REMAINING",
                                    fontSize = 11.sp,
                                    fontWeight = if (macroMode == MacroCardMode.REMAINING) FontWeight.Bold else FontWeight.Medium,
                                    color = if (macroMode == MacroCardMode.REMAINING) Color.Black else TextMuted
                                )
                            }
                        }

                        // Big Kcal Display + 6dp Progress Bar
                        val isRemaining = macroMode == MacroCardMode.REMAINING
                        val remainingKcal = (kcalTarget - totalKcal).coerceAtLeast(0)
                        val isOverGoal = totalKcal > kcalTarget
                        val overKcal = (totalKcal - kcalTarget).coerceAtLeast(0)

                        // When consumed > target in REMAINING mode: hero reads "620 kcal over 2,500" (number Bold red StateBad, caption muted)
                        val isOverHero = isRemaining && isOverGoal

                        val bigKcalText = if (isOverHero) {
                            String.format(Locale.US, "%,d", overKcal)
                        } else if (isRemaining) {
                            String.format(Locale.US, "%,d", remainingKcal)
                        } else {
                            String.format(Locale.US, "%,d", totalKcal)
                        }

                        val numberColor = if (isOverHero) StateBad else TextForeground

                        val captionText = if (isOverHero) {
                            "kcal over %,d".format(Locale.US, kcalTarget)
                        } else {
                            "of %,d kcal".format(Locale.US, kcalTarget)
                        }

                        val barFillFraction = if (isRemaining) {
                            if (isOverGoal) 1f else if (kcalTarget > 0) (remainingKcal.toFloat() / kcalTarget.toFloat()).coerceIn(0f, 1f) else 0f
                        } else {
                            if (kcalTarget > 0) (totalKcal.toFloat() / kcalTarget.toFloat()).coerceIn(0f, 1f) else 0f
                        }

                        // The kcal bar turns RED (StateBad) when consumed exceeds target by > 10% of target; between 100% and 110% stays orange (JudgementWarn).
                        val barFillColor = if (isOverGoal) {
                            getBarColor(computeCaloriesBarColor(totalKcal.toDouble(), kcalTarget, settings.tolerances.kcalGreen))
                        } else {
                            AccentTeal
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Text(
                                    text = bigKcalText,
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = numberColor,
                                    lineHeight = 52.sp
                                )
                                Text(
                                    text = captionText,
                                    fontSize = 13.sp,
                                    color = TextMuted,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }

                            // 6dp progress bar on 8% white track
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = barFillFraction)
                                        .widthIn(min = 2.dp)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(barFillColor)
                                )
                            }
                        }

                        // Three macro mini-rows: Protein / Carbs / Fat
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Protein Row
                            val protColor = if (totalProtein >= proteinTarget) StateLocked else AccentTeal
                            val protFraction = if (proteinTarget > 0) (totalProtein.toFloat() / proteinTarget.toFloat()).coerceIn(0f, 1f) else 0f
                            MacroMiniRow(
                                label = "Protein",
                                currentGrams = totalProtein,
                                targetText = "$proteinTarget" + "g+",
                                progressFraction = protFraction,
                                fillColor = protColor
                            )

                            // Carbs Row (optional / untracked target -> "—")
                            MacroMiniRow(
                                label = "Carbs",
                                currentGrams = totalCarbs,
                                targetText = "—",
                                progressFraction = 0f,
                                fillColor = AccentTeal
                            )

                            // Fat Row (optional / untracked target -> "—")
                            MacroMiniRow(
                                label = "Fat",
                                currentGrams = totalFat,
                                targetText = "—",
                                progressFraction = 0f,
                                fillColor = AccentTeal
                            )
                        }
                    }
                }

                // If Logger is toggled open -> show the full FoodLoggerScreen content inline
                if (isLoggerOpen) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SecondaryFill),
                        border = BorderStroke(1.dp, BorderDefault)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LOG MEAL (${viewModel.mealTypeInput.uppercase()})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TealHighlight
                                )
                                TextButton(onClick = { isLoggerOpen = false }) {
                                    Text("✕ Close", color = TextMuted, fontSize = 12.sp)
                                }
                            }

                            // Presets chips if any
                            if (presets.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    presets.take(4).forEach { p ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(DarkCardSurface)
                                                .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(12.dp))
                                                .clickable { viewModel.applyPreset(p) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("${p.name} · ${p.kcal.toInt()} kcal", fontSize = 10.sp, color = TextForeground)
                                        }
                                    }
                                }
                            }

                            // Manual input fields
                            OutlinedTextField(
                                value = viewModel.mealName,
                                onValueChange = { viewModel.mealName = it },
                                label = { Text("Meal name (optional)", color = TextMuted) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextForeground,
                                    unfocusedTextColor = TextForeground,
                                    focusedBorderColor = TealHighlight,
                                    unfocusedBorderColor = BorderInput
                                )
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = viewModel.kcalInput,
                                    onValueChange = { input -> viewModel.kcalInput = input.filter { it.isDigit() || it == '.' } },
                                    label = { Text("Calories *", color = TextMuted) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextForeground,
                                        unfocusedTextColor = TextForeground,
                                        focusedBorderColor = TealHighlight,
                                        unfocusedBorderColor = BorderInput
                                    )
                                )

                                OutlinedTextField(
                                    value = viewModel.proteinInput,
                                    onValueChange = { input -> viewModel.proteinInput = input.filter { it.isDigit() || it == '.' } },
                                    label = { Text("Protein (g)", color = TextMuted) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = TextForeground,
                                        unfocusedTextColor = TextForeground,
                                        focusedBorderColor = TealHighlight,
                                        unfocusedBorderColor = BorderInput
                                    )
                                )
                            }

                            Button(
                                onClick = {
                                    if (viewModel.saveCurrentMeal()) {
                                        isLoggerOpen = false
                                    }
                                },
                                enabled = viewModel.kcalInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = TealHighlight, contentColor = Color.Black),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Save Meal", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 3. "FOOD LOG" section header (Bold, 20sp)
                Text(
                    text = "FOOD LOG",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground,
                    letterSpacing = 1.sp
                )

                // 4. SEARCH BAR POSITION: moved between "FOOD LOG" header and the Breakfast section card
                SearchBarField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth()
                )

                // If user activated Search with query -> show live search results right below search bar
                if (searchQuery.isNotBlank()) {
                    LiveFoodSearchResults(
                        query = searchQuery,
                        presets = presets,
                        favourites = favourites,
                        recipes = recipes,
                        onSelectPreset = { preset ->
                            viewModel.applyPreset(preset)
                            isLoggerOpen = true
                            searchQuery = ""
                        },
                        onSelectFavourite = { fav ->
                            viewModel.logFavourite(fav)
                            searchQuery = ""
                        },
                        onSelectRecipe = { recipe ->
                            viewModel.logRecipePortion(recipe)
                            searchQuery = ""
                        }
                    )
                }

                // Meal Sections: BREAKFAST / LUNCH / DINNER / SNACKS
                val sections = listOf("breakfast", "lunch", "dinner", "snacks")

                sections.forEach { sectionKey ->
                    val isExpanded = expandedSections.contains(sectionKey)
                    val sectionMeals = displayMeals.filter { it.mealType.equals(sectionKey, ignoreCase = true) }

                    val sectionKcal = sectionMeals.sumOf { it.kcal }.roundToInt()
                    val sectionProtein = sectionMeals.sumOf { it.protein ?: 0.0 }.roundToInt()
                    val sectionCarbs = sectionMeals.sumOf { it.carbs ?: 0.0 }.roundToInt()
                    val sectionFat = sectionMeals.sumOf { it.fat ?: 0.0 }.roundToInt()

                    MealSectionCard(
                        sectionTitle = sectionKey.uppercase(),
                        isExpanded = isExpanded,
                        totalKcal = sectionKcal,
                        totalProtein = sectionProtein,
                        totalCarbs = sectionCarbs,
                        totalFat = sectionFat,
                        meals = sectionMeals,
                        itemPendingDeleteId = itemPendingDeleteId,
                        onToggleExpand = {
                            expandedSections = if (isExpanded) {
                                expandedSections - sectionKey
                            } else {
                                expandedSections + sectionKey
                            }
                        },
                        onAddClick = {
                            viewModel.mealTypeInput = sectionKey
                            isLoggerOpen = true
                        },
                        onItemLongClick = { mealId ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            itemPendingDeleteId = if (itemPendingDeleteId == mealId) null else mealId
                        },
                        onDeleteMeal = { mealId ->
                            viewModel.deleteMeal(mealId)
                            itemPendingDeleteId = null
                        }
                    )
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun MacroMiniRow(
    label: String,
    currentGrams: Int,
    targetText: String,
    progressFraction: Float,
    fillColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 12.sp, color = TextForeground, fontWeight = FontWeight.Medium)
            Text(
                text = if (targetText == "—") "$currentGrams / —" else "$currentGrams / $targetText",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextForeground
            )
        }

        // 4dp bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            if (progressFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progressFraction.coerceIn(0f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(fillColor)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MealSectionCard(
    sectionTitle: String,
    isExpanded: Boolean,
    totalKcal: Int,
    totalProtein: Int,
    totalCarbs: Int,
    totalFat: Int,
    meals: List<LoggedMeal>,
    itemPendingDeleteId: String?,
    onToggleExpand: () -> Unit,
    onAddClick: () -> Unit,
    onItemLongClick: (String) -> Unit,
    onDeleteMeal: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
            // Section Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = sectionTitle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextForeground
                    )

                    // Section summary chips inline: e.g. "312 kcal" teal chip + P/C/F chips
                    if (totalKcal > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(TealHighlight.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$totalKcal kcal",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealHighlight
                            )
                        }

                        if (totalProtein > 0) {
                            Text("P: ${totalProtein}g", fontSize = 10.sp, color = TextMuted)
                        }
                        if (totalCarbs > 0) {
                            Text("C: ${totalCarbs}g", fontSize = 10.sp, color = TextMuted)
                        }
                        if (totalFat > 0) {
                            Text("F: ${totalFat}g", fontSize = 10.sp, color = TextMuted)
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // "+" button to add meal to this section
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SecondaryFill)
                            .clickable { onAddClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TealHighlight)
                    }

                    // Chevron expand/collapse
                    Text(
                        text = if (isExpanded) "▴" else "▾",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }

            // Expanded Meal Items inside
            if (isExpanded) {
                if (meals.isEmpty()) {
                    Text(
                        text = "No $sectionTitle logged yet",
                        fontSize = 11.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        meals.forEach { meal ->
                            val isPendingDelete = itemPendingDeleteId == meal.id
                            val timeStr = timeFormat.format(Date(meal.timestamp))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SecondaryFill)
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = { onItemLongClick(meal.id) }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = meal.name.ifBlank { "Meal" },
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextForeground,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = timeStr,
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${meal.kcal.toInt()} kcal",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextForeground
                                        )
                                        meal.protein?.let {
                                            Text(
                                                text = "${it.toInt()}g P",
                                                fontSize = 10.sp,
                                                color = TealHighlight
                                            )
                                        }
                                    }
                                }

                                // Inline expandable delete confirmation
                                if (isPendingDelete) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = { onDeleteMeal(meal.id) }) {
                                            Text("Delete meal", color = StateBad, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

@Composable
private fun SearchBarField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search for a food", color = TextMuted, fontSize = 13.sp) },
        singleLine = true,
        trailingIcon = {
            // Camera barcode icon (outline, rendered inert this phase)
            CameraIcon(modifier = Modifier.size(20.dp), tint = TextMuted)
        },
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextForeground,
            unfocusedTextColor = TextForeground,
            focusedBorderColor = TealHighlight,
            unfocusedBorderColor = BorderInput,
            focusedContainerColor = SecondaryFill,
            unfocusedContainerColor = SecondaryFill
        ),
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveFoodSearchResults(
    query: String,
    presets: List<MealPreset>,
    favourites: List<FavouriteFood>,
    recipes: List<Recipe>,
    onSelectPreset: (MealPreset) -> Unit,
    onSelectFavourite: (FavouriteFood) -> Unit,
    onSelectRecipe: (Recipe) -> Unit,
    modifier: Modifier = Modifier,
) {
    val q = query.trim().lowercase()

    val matchedPresets = presets.filter { it.name.lowercase().contains(q) }
    val matchedFavourites = favourites.filter { it.name.lowercase().contains(q) }
    val matchedRecipes = recipes.filter { it.name.lowercase().contains(q) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SecondaryFill),
        border = BorderStroke(1.dp, TealHighlight.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("MATCHING FOODS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TealHighlight)

            if (matchedPresets.isEmpty() && matchedFavourites.isEmpty() && matchedRecipes.isEmpty()) {
                Text("No matching foods found", fontSize = 12.sp, color = TextMuted)
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    matchedFavourites.forEach { fav ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkCardSurface)
                                .border(BorderStroke(1.dp, TealHighlight), RoundedCornerShape(12.dp))
                                .clickable { onSelectFavourite(fav) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("★ ${fav.name} · ${fav.kcalPerServing} kcal", fontSize = 11.sp, color = TextForeground)
                        }
                    }

                    matchedPresets.forEach { p ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkCardSurface)
                                .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(12.dp))
                                .clickable { onSelectPreset(p) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("${p.name} · ${p.kcal.toInt()} kcal", fontSize = 11.sp, color = TextForeground)
                        }
                    }

                    matchedRecipes.forEach { r ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkCardSurface)
                                .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(12.dp))
                                .clickable { onSelectRecipe(r) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("${r.name} · ${r.perPortionKcal} kcal", fontSize = 11.sp, color = TealHighlight)
                        }
                    }
                }
            }
        }
    }
}
