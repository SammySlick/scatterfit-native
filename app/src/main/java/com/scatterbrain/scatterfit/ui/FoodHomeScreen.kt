package com.scatterbrain.scatterfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.input.KeyboardType
import com.scatterbrain.scatterfit.data.DailyRepository
import com.scatterbrain.scatterfit.data.OffNutriments
import com.scatterbrain.scatterfit.data.OpenFoodFactsClient
import com.scatterbrain.scatterfit.data.ProductResult
import com.scatterbrain.scatterfit.data.derivePortionPresets
import com.scatterbrain.scatterfit.data.formatAmount
import com.scatterbrain.scatterfit.data.resolvePortionAmount
import com.scatterbrain.scatterfit.data.scaleNutriments
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class MacroCardMode { CONSUMED, REMAINING }

@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
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

    // Full meals set: stored meals + pipeline meals not already represented
    val allCombinedMeals = remember(storedMeals, pipelineMeals, activeDay) {
        if (storedMeals.isEmpty()) {
            pipelineMeals.map { it.toLoggedMeal(activeDay) }
        } else {
            val storedKeys = storedMeals.map { it.name.trim().lowercase() to it.kcal.roundToInt() }.toSet()
            val extra = pipelineMeals
                .filterNot { (it.name.trim().lowercase() to it.kcal.roundToInt()) in storedKeys }
                .map { it.toLoggedMeal(activeDay) }
            storedMeals + extra
        }
    }

    // Pending delete + undo state (5 second timeout)
    var pendingDeletedMeal by remember { mutableStateOf<LoggedMeal?>(null) }
    var undoSecondsRemaining by remember { mutableIntStateOf(5) }

    LaunchedEffect(pendingDeletedMeal) {
        if (pendingDeletedMeal != null) {
            undoSecondsRemaining = 5
            while (undoSecondsRemaining > 0 && pendingDeletedMeal != null) {
                delay(1000L)
                undoSecondsRemaining--
            }
            if (pendingDeletedMeal != null) {
                pendingDeletedMeal = null
            }
        }
    }

    // Display meals
    val displayMeals = remember(allCombinedMeals) {
        allCombinedMeals
    }

    var macroMode by remember { mutableStateOf(MacroCardMode.CONSUMED) }

    // Section expansion: Breakfast expanded by default, others collapsed
    var expandedSections by remember {
        mutableStateOf(setOf("breakfast"))
    }

    // Inline logging / entry mode
    var editingMealId by remember { mutableStateOf<String?>(null) }

    // Search bar & OFF states
    var searchQuery by remember { mutableStateOf("") }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var barcodeNotFound by remember { mutableStateOf(false) }
    var pendingConfirmProduct by remember { mutableStateOf<ProductResult?>(null) }
    var offResults by remember { mutableStateOf<List<ProductResult>>(emptyList()) }
    var isSearchingOff by remember { mutableStateOf(false) }
    var searchHasExecuted by remember { mutableStateOf(false) }

    // Bottom sheet state for the "+" button
    var activeBottomSheetMealType by remember { mutableStateOf<String?>(null) }
    val isBottomSheetOpen = activeBottomSheetMealType != null

    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Debounced text search to Open Food Facts (400ms)
    LaunchedEffect(searchQuery) {
        val q = searchQuery.trim()
        if (q.length >= 2) {
            delay(400L)
            isSearchingOff = true
            offResults = OpenFoodFactsClient.search(q).take(8)
            isSearchingOff = false
            searchHasExecuted = true
        } else {
            offResults = emptyList()
            isSearchingOff = false
            searchHasExecuted = false
        }
    }

    // Full screen camera overlay for barcode scanning
    if (showBarcodeScanner) {
        BarcodeScannerOverlay(
            onBarcodeDetected = { barcode ->
                showBarcodeScanner = false
                scope.launch {
                    val product = OpenFoodFactsClient.lookupBarcode(barcode)
                    if (product != null) {
                        pendingConfirmProduct = product
                        barcodeNotFound = false
                    } else {
                        barcodeNotFound = true
                    }
                    searchQuery = "" // Clear the query field in both cases
                }
            },
            onDismiss = { showBarcodeScanner = false }
        )
        return
    }

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

    // Totals for active day matching DailyRepository & Day Card parity (includes ALL meals, including hidden)
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
                .padding(bottom = 16.dp)
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

                            // Carbs Row (optional / untracked target -> "—", kept visible even at zero)
                            MacroMiniRow(
                                label = "Carbs",
                                currentGrams = totalCarbs,
                                targetText = "—",
                                progressFraction = 0f,
                                fillColor = AccentTeal
                            )

                            // Fat Row (optional / untracked target -> "—", kept visible even at zero)
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

                // Inline Undo Bar for Deletes
                if (pendingDeletedMeal != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = SecondaryFill),
                        border = BorderStroke(1.dp, TealHighlight.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Meal removed (${undoSecondsRemaining}s)",
                                color = TextForeground,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            TextButton(onClick = {
                                pendingDeletedMeal?.let { viewModel.logCustomMeal(it) }
                                pendingDeletedMeal = null
                            }) {
                                Text("Undo", color = TealHighlight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Remove the old Logger UI block completely.
                // 3. "FOOD LOG" section header (Bold, 20sp)
                Text(
                    text = "FOOD LOG",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground,
                    letterSpacing = 1.sp
                )

                // 4. SEARCH BAR POSITION: Moved to bottom sheet

                if (barcodeNotFound) {
                    Text(
                        text = "Barcode not found",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                    )
                }

                // In-place Confirm Sheet for Scanned or Selected OFF Product
                if (pendingConfirmProduct != null) {
                    ConfirmProductCard(
                        product = pendingConfirmProduct!!,
                        defaultMealType = viewModel.mealTypeInput,
                        onConfirm = { mealToLog ->
                            viewModel.logCustomMeal(mealToLog)
                            pendingConfirmProduct = null
                            searchQuery = ""
                        },
                        onCancel = { pendingConfirmProduct = null }
                    )
                }

                // Live search results (local favourites/presets/recipes + Open Food Facts products) moved to bottom sheet

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
                        onToggleExpand = {
                            expandedSections = if (isExpanded) {
                                expandedSections - sectionKey
                            } else {
                                expandedSections + sectionKey
                            }
                        },
                        onAddClick = {
                            viewModel.mealTypeInput = sectionKey
                            editingMealId = null
                            searchQuery = "" // Reset search for new sheet
                            activeBottomSheetMealType = sectionKey
                        },
                        onEditMeal = { mealToEdit ->
                            viewModel.mealTypeInput = mealToEdit.mealType
                            editingMealId = mealToEdit.id
                            
                            val portion = mealToEdit.portionGrams ?: 100.0
                            val ratio = if (portion > 0) 100.0 / portion else 1.0

                            val derivedNutriments = OffNutriments(
                                energyKcal_100g = mealToEdit.kcal * ratio,
                                proteins_100g = mealToEdit.protein?.let { it * ratio },
                                carbohydrates_100g = mealToEdit.carbs?.let { it * ratio },
                                fat_100g = mealToEdit.fat?.let { it * ratio }
                            )

                            pendingConfirmProduct = ProductResult(
                                code = mealToEdit.id,
                                productName = mealToEdit.name,
                                nutriments = derivedNutriments,
                                servingQuantity = kotlinx.serialization.json.JsonPrimitive(portion)
                            )
                        },
                        onDeleteMeal = { mealToDelete ->
                            viewModel.deleteMeal(mealToDelete.id)
                            pendingDeletedMeal = mealToDelete
                        }
                    )
                }

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    if (isBottomSheetOpen) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { activeBottomSheetMealType = null },
            containerColor = DarkCardSurface,
            dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle(color = BorderDefault) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val mealTypeDisplay = activeBottomSheetMealType?.replaceFirstChar { it.uppercase() } ?: "Meal"
                Text(
                    text = "Log $mealTypeDisplay",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground
                )

                SearchBarField(
                    query = searchQuery,
                    onQueryChange = {
                        searchQuery = it
                        barcodeNotFound = false
                    },
                    onCameraClick = {
                        activeBottomSheetMealType = null
                        showBarcodeScanner = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = { /* Quick Log logic to come */ },
                    enabled = false,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SecondaryFill,
                        contentColor = TextMuted,
                        disabledContainerColor = SecondaryFill,
                        disabledContentColor = TextMuted.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Quick Log (Coming Soon)", fontWeight = FontWeight.SemiBold)
                }

                if (searchQuery.isNotBlank()) {
                    LiveFoodSearchResults(
                        query = searchQuery,
                        presets = presets,
                        favourites = favourites,
                        recipes = recipes,
                        offResults = offResults,
                        isSearchingOff = isSearchingOff,
                        searchHasExecuted = searchHasExecuted,
                        onSelectPreset = { preset ->
                            val derivedNutriments = OffNutriments(
                                energyKcal_100g = preset.kcal,
                                proteins_100g = preset.protein,
                                carbohydrates_100g = preset.carbs,
                                fat_100g = preset.fat
                            )
                            pendingConfirmProduct = ProductResult(
                                code = "preset_${preset.name}",
                                productName = preset.name,
                                nutriments = derivedNutriments,
                                servingQuantity = kotlinx.serialization.json.JsonPrimitive(100.0)
                            )
                            activeBottomSheetMealType = null
                            searchQuery = ""
                        },
                        onSelectFavourite = { fav ->
                            viewModel.logFavourite(fav)
                            activeBottomSheetMealType = null
                            searchQuery = ""
                        },
                        onSelectRecipe = { recipe ->
                            viewModel.logRecipePortion(recipe)
                            activeBottomSheetMealType = null
                            searchQuery = ""
                        },
                        onSelectOffProduct = { product ->
                            pendingConfirmProduct = product
                            activeBottomSheetMealType = null
                        }
                    )
                } else {
                    // "Recents"
                    Text("Recents", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextForeground)
                    val recents = presets.filter { it.kcal > 0 } // Very simple fallback to presets for recents
                    if (recents.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            recents.take(5).forEach { p ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SecondaryFill)
                                        .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(12.dp))
                                        .clickable {
                                            val derivedNutriments = OffNutriments(
                                                energyKcal_100g = p.kcal,
                                                proteins_100g = p.protein,
                                                carbohydrates_100g = p.carbs,
                                                fat_100g = p.fat
                                            )
                                            pendingConfirmProduct = ProductResult(
                                                code = "preset_${p.name}",
                                                productName = p.name,
                                                nutriments = derivedNutriments,
                                                servingQuantity = kotlinx.serialization.json.JsonPrimitive(100.0)
                                            )
                                            activeBottomSheetMealType = null
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("${p.name} · ${p.kcal.toInt()} kcal", fontSize = 11.sp, color = TextForeground)
                                }
                            }
                        }
                    } else {
                        Text("No recent foods", fontSize = 12.sp, color = TextMuted)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Generic Foods", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextForeground)
                    Text("Coming next...", fontSize = 12.sp, color = TextMuted)
                }
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

@Composable
private fun MealSectionCard(
    sectionTitle: String,
    isExpanded: Boolean,
    totalKcal: Int,
    totalProtein: Int,
    totalCarbs: Int,
    totalFat: Int,
    meals: List<LoggedMeal>,
    onToggleExpand: () -> Unit,
    onAddClick: () -> Unit,
    onEditMeal: (LoggedMeal) -> Unit,
    onDeleteMeal: (LoggedMeal) -> Unit,
    modifier: Modifier = Modifier,
) {
    val localMeals = meals.filter { it.isLocal }
    val otherAppMeals = meals.filter { !it.isLocal }
    var isOtherAppsExpanded by remember { mutableStateOf(false) }

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
                if (localMeals.isEmpty() && otherAppMeals.isEmpty()) {
                    Text(
                        text = "No $sectionTitle logged yet",
                        fontSize = 11.sp,
                        color = TextMuted,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // LOCAL MEALS: swipe-left to reveal actions
                        localMeals.forEach { meal ->
                            SwipeableMealRow(
                                meal = meal,
                                onEditClick = { onEditMeal(meal) },
                                onDeleteClick = { onDeleteMeal(meal) }
                            )
                        }

                        // HC-SOURCED ENTRIES: Hidden by default with disclosure one-liner
                        if (otherAppMeals.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isOtherAppsExpanded = !isOtherAppsExpanded }
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${otherAppMeals.size} ${if (otherAppMeals.size == 1) "item" else "items"} from other apps (included in totals)",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                                Text(
                                    text = if (isOtherAppsExpanded) "▴" else "▾",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }

                            if (isOtherAppsExpanded) {
                                otherAppMeals.forEach { hcMeal ->
                                    ReadonlyMealRow(meal = hcMeal)
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
fun SwipeableMealRow(
    meal: LoggedMeal,
    onEditClick: (LoggedMeal) -> Unit,
    onDeleteClick: (LoggedMeal) -> Unit,
    modifier: Modifier = Modifier,
) {
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val maxRevealWidth = 140.dp
    val maxRevealPx = with(LocalDensity.current) { maxRevealWidth.toPx() }
    val animatedOffset by animateFloatAsState(targetValue = swipeOffset, label = "swipeOffset")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkCardSurface)
    ) {
        // Underneath: Actions revealed on swipe left
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // EDIT button
            Box(
                modifier = Modifier
                    .size(width = 62.dp, height = 48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(TealHighlight)
                    .clickable {
                        swipeOffset = 0f
                        onEditClick(meal)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Edit", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(6.dp))

            // DELETE button
            Box(
                modifier = Modifier
                    .size(width = 62.dp, height = 48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(StateBad)
                    .clickable {
                        swipeOffset = 0f
                        onDeleteClick(meal)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Delete", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Foreground: The meal card itself, shifts left when swiped
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = animatedOffset }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val newOffset = (swipeOffset + delta).coerceIn(-maxRevealPx, 0f)
                        swipeOffset = newOffset
                    },
                    onDragStopped = {
                        swipeOffset = if (swipeOffset < -maxRevealPx / 2f) -maxRevealPx else 0f
                    }
                )
                .clip(RoundedCornerShape(8.dp))
                .background(SecondaryFill)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            MealItemContent(meal = meal)
        }
    }
}

@Composable
fun ReadonlyMealRow(
    meal: LoggedMeal,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SecondaryFill.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        MealItemContent(meal = meal, tag = "External")
    }
}

@Composable
private fun MealItemContent(
    meal: LoggedMeal,
    tag: String? = null,
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    val timeStr = timeFormat.format(Date(meal.timestamp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = meal.name.ifBlank { "Meal" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!meal.portionLabel.isNullOrBlank()) {
                    Text(
                        text = "· ${meal.portionLabel}",
                        fontSize = 12.sp,
                        color = TextMuted,
                        maxLines = 1
                    )
                }
                if (tag != null) {
                    Text(
                        text = tag,
                        fontSize = 10.sp,
                        color = TextMuted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DarkCardSurface)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
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
}

@Composable
private fun SearchBarField(
    query: String,
    onQueryChange: (String) -> Unit,
    onCameraClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search for a food", color = TextMuted, fontSize = 13.sp) },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = onCameraClick) {
                CameraIcon(modifier = Modifier.size(20.dp), tint = TealHighlight)
            }
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

@Composable
fun ConfirmProductCard(
    product: ProductResult,
    defaultMealType: String,
    onConfirm: (LoggedMeal) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val defaultUnit = remember(product) { product.inferredUnit }
    val servingQuantity = remember(product) { product.servingQuantityOrFallback }
    val presets = remember(product) { derivePortionPresets(product) }

    var amountText by remember(product) {
        mutableStateOf(formatAmount(servingQuantity))
    }
    var selectedUnit by remember(product) {
        mutableStateOf(defaultUnit)
    }
    var activePortionLabel by remember(product) {
        val initialPreset = presets.firstOrNull { abs(it.amount - servingQuantity) < 0.1 }
        mutableStateOf(initialPreset?.portionLabel ?: "${formatAmount(servingQuantity)} $defaultUnit")
    }
    var selectedMealType by remember { mutableStateOf(defaultMealType) }

    val enteredAmount = amountText.toDoubleOrNull() ?: 0.0
    val resolvedGramsOrMl = resolvePortionAmount(enteredAmount, selectedUnit, servingQuantity)
    val scaled = remember(product, resolvedGramsOrMl) {
        scaleNutriments(product.basePer100g, resolvedGramsOrMl)
    }

    val isManualEntry = product.code == "manual_entry" || (product.nutriments.energyKcal_100g == null && product.nutriments.energyKcal == null && product.nutriments.energy_100g == null)
    var manualName by remember(product) { mutableStateOf(product.productName ?: "") }
    var manualKcal by remember(product) { mutableStateOf(if (scaled.kcal > 0) scaled.kcal.roundToInt().toString() else "") }
    var manualProtein by remember(product) { mutableStateOf(if ((scaled.protein ?: 0.0) > 0) scaled.protein?.roundToInt()?.toString() ?: "" else "") }
    var manualCarbs by remember(product) { mutableStateOf(if ((scaled.carbs ?: 0.0) > 0) scaled.carbs?.roundToInt()?.toString() ?: "" else "") }
    var manualFat by remember(product) { mutableStateOf(if ((scaled.fat ?: 0.0) > 0) scaled.fat?.roundToInt()?.toString() ?: "" else "") }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = BorderStroke(1.dp, TealHighlight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isManualEntry) {
                // Direct Entry Mode
                OutlinedTextField(
                    value = manualName,
                    onValueChange = { manualName = it },
                    label = { Text("Name", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextForeground,
                        unfocusedTextColor = TextForeground,
                        focusedBorderColor = TealHighlight,
                        unfocusedBorderColor = BorderDefault,
                        focusedContainerColor = SecondaryFill,
                        unfocusedContainerColor = SecondaryFill
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualKcal,
                        onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*$"""))) manualKcal = it },
                        label = { Text("Kcal", color = TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextForeground,
                            unfocusedTextColor = TextForeground,
                            focusedBorderColor = TealHighlight,
                            unfocusedBorderColor = BorderDefault,
                            focusedContainerColor = SecondaryFill,
                            unfocusedContainerColor = SecondaryFill
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = manualProtein,
                        onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*$"""))) manualProtein = it },
                        label = { Text("Protein (g)", color = TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextForeground,
                            unfocusedTextColor = TextForeground,
                            focusedBorderColor = TealHighlight,
                            unfocusedBorderColor = BorderDefault,
                            focusedContainerColor = SecondaryFill,
                            unfocusedContainerColor = SecondaryFill
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = manualCarbs,
                        onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*$"""))) manualCarbs = it },
                        label = { Text("Carbs (g)", color = TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextForeground,
                            unfocusedTextColor = TextForeground,
                            focusedBorderColor = TealHighlight,
                            unfocusedBorderColor = BorderDefault,
                            focusedContainerColor = SecondaryFill,
                            unfocusedContainerColor = SecondaryFill
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = manualFat,
                        onValueChange = { if (it.isEmpty() || it.matches(Regex("""^\d*$"""))) manualFat = it },
                        label = { Text("Fat (g)", color = TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextForeground,
                            unfocusedTextColor = TextForeground,
                            focusedBorderColor = TealHighlight,
                            unfocusedBorderColor = BorderDefault,
                            focusedContainerColor = SecondaryFill,
                            unfocusedContainerColor = SecondaryFill
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                // Header (Name, Brand, Serving)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = product.productName ?: "Product",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextForeground
                        )
                        product.brands?.takeIf { it.isNotBlank() }?.let {
                            Text(text = it, fontSize = 12.sp, color = TextMuted)
                        }
                        val servingInfo = product.servingSize ?: product.servingQuantityGrams?.let { "${it.toInt()}g" }
                        if (servingInfo != null) {
                            Text(text = "Serving: $servingInfo", fontSize = 11.sp, color = TealHighlight)
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${scaled.kcal.roundToInt()} kcal",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TealHighlight
                        )
                        Text(
                            text = "${scaled.protein.roundToInt()}g protein",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }
                }

                // Macros row (scaled live, rounded to ints)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SecondaryFill)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Text("C: ${scaled.carbs.roundToInt()}g", fontSize = 11.sp, color = TextForeground)
                    Text("F: ${scaled.fat.roundToInt()}g", fontSize = 11.sp, color = TextForeground)
                    Text("P: ${scaled.protein.roundToInt()}g", fontSize = 11.sp, color = TextForeground)
                }

                // PORTION UI: 1. Quick Presets Row
                if (presets.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { preset ->
                            val isPresetActive = (selectedUnit.equals(preset.unit, ignoreCase = true) ||
                                    (selectedUnit.equals("servings", ignoreCase = true) && abs(resolvedGramsOrMl - preset.amount) < 0.1)) &&
                                    abs(resolvedGramsOrMl - preset.amount) < 0.1
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isPresetActive) TealHighlight else SecondaryFill)
                                    .border(
                                        BorderStroke(1.dp, if (isPresetActive) TealHighlight else BorderDefault),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        amountText = formatAmount(preset.amount)
                                        selectedUnit = preset.unit
                                        activePortionLabel = preset.portionLabel
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = preset.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isPresetActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isPresetActive) Color.Black else TextForeground
                                )
                            }
                        }
                    }
                }

                // PORTION UI: 2. Free Entry Row [ amount ] [ g | ml | servings ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { newText ->
                            if (newText.isEmpty() || newText.matches(Regex("""^\d*\.?\d*$"""))) {
                                amountText = newText
                                val amt = newText.toDoubleOrNull() ?: 0.0
                                activePortionLabel = if (selectedUnit.equals("servings", ignoreCase = true)) {
                                    val resolved = amt * servingQuantity
                                    if (amt == 1.0) "${formatAmount(servingQuantity)} $defaultUnit"
                                    else "${formatAmount(amt)} servings (${formatAmount(resolved)} $defaultUnit)"
                                } else {
                                    "${formatAmount(amt)} $selectedUnit"
                                }
                            }
                        },
                        placeholder = { Text("Portion", color = TextMuted, fontSize = 13.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextForeground,
                            unfocusedTextColor = TextForeground,
                            focusedBorderColor = TealHighlight,
                            unfocusedBorderColor = BorderDefault,
                            focusedContainerColor = SecondaryFill,
                            unfocusedContainerColor = SecondaryFill
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    // Compact unit selector: [ g | ml | servings ]
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SecondaryFill)
                            .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(8.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf("g", "ml", "servings").forEach { u ->
                            val isSelected = selectedUnit.equals(u, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) TealHighlight else Color.Transparent)
                                    .clickable {
                                        if (!selectedUnit.equals(u, ignoreCase = true)) {
                                            if (u == "servings") {
                                                val currentAmt = amountText.toDoubleOrNull() ?: servingQuantity
                                                val s = if (servingQuantity > 0.0) currentAmt / servingQuantity else 1.0
                                                amountText = formatAmount(s)
                                            } else if (selectedUnit.equals("servings", ignoreCase = true)) {
                                                val s = amountText.toDoubleOrNull() ?: 1.0
                                                amountText = formatAmount(s * servingQuantity)
                                            }
                                            selectedUnit = u
                                            val amt = amountText.toDoubleOrNull() ?: 0.0
                                            val resolved = if (u == "servings") amt * servingQuantity else amt
                                            activePortionLabel = if (u == "servings") {
                                                if (amt == 1.0) "${formatAmount(servingQuantity)} $defaultUnit"
                                                else "${formatAmount(amt)} servings (${formatAmount(resolved)} $defaultUnit)"
                                            } else {
                                                "${formatAmount(amt)} $u"
                                            }
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = u,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.Black else TextForeground
                                )
                            }
                        }
                    }
                }
            }

            // Meal Type Selector Chips
            val mealTypes = listOf("breakfast", "lunch", "dinner", "snacks")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                mealTypes.forEach { type ->
                    val isSelected = selectedMealType.equals(type, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) TealHighlight else SecondaryFill)
                            .border(
                                BorderStroke(1.dp, if (isSelected) TealHighlight else BorderDefault),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedMealType = type }
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

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel", color = TextMuted)
                }

                Button(
                    onClick = {
                        val meal = if (isManualEntry) {
                            LoggedMeal(
                                name = manualName.ifBlank { "Custom Meal" },
                                kcal = manualKcal.toDoubleOrNull() ?: 0.0,
                                protein = manualProtein.toDoubleOrNull(),
                                carbs = manualCarbs.toDoubleOrNull(),
                                fat = manualFat.toDoubleOrNull(),
                                mealType = selectedMealType,
                                isLocal = true,
                                portionGrams = null,
                                portionLabel = null
                            )
                        } else {
                            val matchedPreset = presets.firstOrNull {
                                abs(it.amount - resolvedGramsOrMl) < 0.1 && selectedUnit.equals(it.unit, ignoreCase = true)
                            }
                            val resolvedLabel = when {
                                selectedUnit.equals("servings", ignoreCase = true) -> {
                                    if (enteredAmount == 1.0) "${formatAmount(servingQuantity)} $defaultUnit"
                                    else "${formatAmount(enteredAmount)} servings (${formatAmount(resolvedGramsOrMl)} $defaultUnit)"
                                }
                                matchedPreset != null -> matchedPreset.portionLabel
                                else -> "${formatAmount(enteredAmount)} $selectedUnit"
                            }

                            LoggedMeal(
                                name = product.displayName,
                                kcal = scaled.kcal,
                                protein = scaled.protein,
                                carbs = scaled.carbs,
                                fat = scaled.fat,
                                mealType = selectedMealType,
                                isLocal = true,
                                portionGrams = resolvedGramsOrMl,
                                portionLabel = resolvedLabel
                            )
                        }
                        onConfirm(meal)
                    },
                    enabled = if (isManualEntry) manualName.isNotBlank() && (manualKcal.toDoubleOrNull() ?: -1.0) >= 0 else resolvedGramsOrMl > 0.0,
                    colors = ButtonDefaults.buttonColors(containerColor = TealHighlight, contentColor = Color.Black),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Confirm", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiveFoodSearchResults(
    query: String,
    presets: List<MealPreset>,
    favourites: List<FavouriteFood>,
    recipes: List<Recipe>,
    offResults: List<ProductResult>,
    isSearchingOff: Boolean,
    searchHasExecuted: Boolean,
    onSelectPreset: (MealPreset) -> Unit,
    onSelectFavourite: (FavouriteFood) -> Unit,
    onSelectRecipe: (Recipe) -> Unit,
    onSelectOffProduct: (ProductResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val q = query.trim().lowercase()
    val genericFoodsList = OpenFoodFactsClient.GENERIC_FOODS

    val matchedPresets = presets.filter { it.name.lowercase().contains(q) }
    val matchedFavourites = favourites.filter { it.name.lowercase().contains(q) }
    val matchedRecipes = recipes.filter { it.name.lowercase().contains(q) }
    
    val genericSearchResults = genericFoodsList.filter { productResult: ProductResult ->
        productResult.productName?.lowercase()?.contains(q) == true
    }

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("MATCHING FOODS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TealHighlight)

            // Local curated items
            if (matchedFavourites.isNotEmpty() || matchedPresets.isNotEmpty() || matchedRecipes.isNotEmpty()) {
                Text("LOCAL FOODS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
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

            if (genericSearchResults.isNotEmpty()) {
                Text("GENERIC FOODS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    genericSearchResults.forEach { product: ProductResult ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectOffProduct(product) },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                            border = BorderStroke(1.dp, BorderDefault)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = product.productName ?: "Product",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextForeground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subtitle = listOfNotNull(
                                        product.brands?.takeIf { it.isNotBlank() },
                                        product.servingSize ?: product.servingQuantityGrams?.let { "${it.toInt()}g" }
                                    ).joinToString(" · ")
                                    if (subtitle.isNotBlank()) {
                                        Text(text = subtitle, fontSize = 11.sp, color = TextMuted, maxLines = 1)
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${product.kcalPerServing} kcal",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TealHighlight
                                    )
                                    Text(
                                        text = "${String.format(Locale.US, "%.1f", product.proteinPerServing)}g P",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Open Food Facts results
            if (isSearchingOff) {
                Text("Searching Open Food Facts…", color = TextMuted, fontSize = 12.sp)
            } else if (offResults.isNotEmpty()) {
                Text("OPEN FOOD FACTS (UK)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    offResults.forEach { product ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectOffProduct(product) },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                            border = BorderStroke(1.dp, BorderDefault)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = product.productName ?: "Product",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextForeground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subtitle = listOfNotNull(
                                        product.brands?.takeIf { it.isNotBlank() },
                                        product.servingSize ?: product.servingQuantityGrams?.let { "${it.toInt()}g" }
                                    ).joinToString(" · ")
                                    if (subtitle.isNotBlank()) {
                                        Text(text = subtitle, fontSize = 11.sp, color = TextMuted, maxLines = 1)
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "${product.kcalPerServing} kcal",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TealHighlight
                                    )
                                    Text(
                                        text = "${String.format(Locale.US, "%.1f", product.proteinPerServing)}g P",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (searchHasExecuted && matchedFavourites.isEmpty() && matchedPresets.isEmpty() && matchedRecipes.isEmpty()) {
                Text("No matches for '$query' — try a brand name", fontSize = 12.sp, color = TextMuted)
            }
        }
    }
}
