package com.scatterbrain.scatterfit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.scatterbrain.scatterfit.ui.theme.AppBackground
import com.scatterbrain.scatterfit.ui.theme.AppCardSurface
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.BorderInput
import com.scatterbrain.scatterfit.ui.theme.BrandPrimaryLime
import com.scatterbrain.scatterfit.ui.theme.JudgementGood
import com.scatterbrain.scatterfit.ui.theme.MutedFill
import com.scatterbrain.scatterfit.ui.theme.OnPrimaryText
import com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DrinkLoggerScreen(
    viewModel: DrinkTrackerViewModel,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val dailyState by viewModel.dailyState.collectAsState()

    LaunchedEffect(Unit) {
        if (dailyState.libraryDrinks.isEmpty()) {
            viewModel.loadLibraryFromAssets(context)
        }
    }

    DrinkLoggerContent(
        dailyState = dailyState,
        onLogDrink = { viewModel.logDrink(it) },
        onRemoveLoggedDrink = { viewModel.removeLoggedDrink(it) },
        onUpdateDrinkTime = { drink, newTimestamp -> viewModel.updateLoggedDrinkTime(drink, newTimestamp) },
        onDaySelected = { viewModel.setDayKey(it) },
        onClearLogs = { viewModel.clearLogs() },
        onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
        onCategorySelect = { viewModel.onCategorySelected(it) },
        onBackClick = onBackClick,
        modifier = modifier
    )
}

@Composable
fun DrinkLoggerContent(
    dailyState: DailyState,
    onLogDrink: (DrinkReference) -> Unit,
    onRemoveLoggedDrink: (LoggedDrink) -> Unit,
    onUpdateDrinkTime: (LoggedDrink, Long) -> Unit,
    onDaySelected: (String) -> Unit,
    onClearLogs: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null
) {
    var drinkToEditTime by remember { mutableStateOf<LoggedDrink?>(null) }
    val today = remember { com.scatterbrain.scatterfit.core.todayKey() }
    val activeDayTitle = remember(dailyState.dayKey, today) {
        formatDayTitle(dailyState.dayKey, today)
    }
    val isFutureDay = dailyState.dayKey > today

    // Time editor dialog
    if (drinkToEditTime != null) {
        LoggedDrinkTimePickerDialog(
            loggedDrink = drinkToEditTime!!,
            onDismissRequest = { drinkToEditTime = null },
            onConfirmTime = { newTimestamp ->
                onUpdateDrinkTime(drinkToEditTime!!, newTimestamp)
                drinkToEditTime = null
            }
        )
    }

    // Categories for filter strip
    val categories = remember(dailyState.libraryDrinks) {
        listOf("All", "Favorites") + dailyState.libraryDrinks
            .map { it.category.replaceFirstChar { ch -> ch.titlecase() } }
            .filter { it.isNotBlank() }
            .distinct()
    }

    val filteredDrinks = remember(dailyState.libraryDrinks, dailyState.searchQuery, dailyState.selectedCategory) {
        dailyState.libraryDrinks.filter { drink ->
            val matchesQuery = dailyState.searchQuery.isBlank() ||
                    drink.name.contains(dailyState.searchQuery, ignoreCase = true) ||
                    drink.brand.contains(dailyState.searchQuery, ignoreCase = true) ||
                    drink.category.contains(dailyState.searchQuery, ignoreCase = true)

            val matchesCategory = when (dailyState.selectedCategory) {
                "All" -> true
                "Favorites" -> drink.fav
                else -> drink.category.equals(dailyState.selectedCategory, ignoreCase = true)
            }

            matchesQuery && matchesCategory
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppBackground
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // 1. Top Bar Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Title and subtitle column (shrinks to fill remaining width)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onBackClick != null) {
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier
                                    .size(36.dp)
                                    .padding(end = 4.dp)
                            ) {
                                Text("←", color = TextForeground, fontSize = 22.sp)
                            }
                        }
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "Alcohol Tracker",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextForeground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isFutureDay) "$activeDayTitle · Upcoming" else "$activeDayTitle · Tap any drink to log in real-time",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isFutureDay) BrandPrimaryLime else TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Fixed right-aligned slot, max width ~40% of screen, vertically centred against title block
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.40f)
                            .wrapContentWidth(Alignment.End),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        DrinkDayIndicatorPill(
                            isDrinkDay = dailyState.isDrinkDay,
                            score = dailyState.unitsScore
                        )
                    }
                }
            }

            // 2. Weekly Day Picker Strip
            item {
                DrinkDayPickerStrip(
                    activeDayKey = dailyState.dayKey,
                    onDaySelected = onDaySelected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // 3. Real-time Daily Aggregate Totals Card
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    DailyTotalsCard(
                        dailyState = dailyState,
                        onClearLogs = onClearLogs
                    )
                }
            }

            // 4. Recently Logged Drinks (if any) — with clickable rows to edit time
            if (dailyState.loggedDrinks.isNotEmpty()) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        LoggedDrinksSection(
                            loggedDrinks = dailyState.loggedDrinks,
                            onRemoveDrink = onRemoveLoggedDrink,
                            onEditDrinkTime = { drinkToEditTime = it }
                        )
                    }
                }
            }

            // 5. Search and Filter Bar
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = dailyState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search 300+ drinks by name or brand...", color = TextMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextForeground,
                            unfocusedTextColor = TextForeground,
                            focusedBorderColor = BrandPrimaryLime,
                            unfocusedBorderColor = BorderInput,
                            cursorColor = BrandPrimaryLime,
                            focusedContainerColor = AppCardSurface,
                            unfocusedContainerColor = AppCardSurface
                        )
                    )

                    // Category Filter Pills
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(categories) { category ->
                            val isSelected = category.equals(dailyState.selectedCategory, ignoreCase = true)
                            val pillBg = if (isSelected) BrandPrimaryLime else SecondaryFill
                            val pillTextColor = if (isSelected) OnPrimaryText else TextMuted

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(pillBg)
                                    .border(BorderStroke(1.dp, if (isSelected) BrandPrimaryLime else BorderDefault), RoundedCornerShape(16.dp))
                                    .clickable { onCategorySelect(category) }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = category,
                                    color = pillTextColor,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 6. Library Section Title
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isFutureDay) "Standard Drinks Library (${filteredDrinks.size}) · Upcoming day" else "Standard Drinks Library (${filteredDrinks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextForeground
                    )
                }
            }

            // 7. Condensed Single-Column List Rows (~56-64dp per row, hairline divider separated)
            items(filteredDrinks, key = { it.id.ifBlank { it.name + it.serving } }) { drink ->
                DrinkLibraryItemRow(
                    drink = drink,
                    isFutureDay = isFutureDay,
                    onLogClick = { onLogDrink(drink) }
                )
            }
        }
    }
}

/**
 * 7-Day Weekly Day Picker Strip matching the app's visual grammar.
 * Tapping any day switches the active day key in the ViewModel.
 */
@Composable
fun DrinkDayPickerStrip(
    activeDayKey: String,
    onDaySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { com.scatterbrain.scatterfit.core.todayKey() }
    val weekKeys = remember { com.scatterbrain.scatterfit.core.currentWeekKeys("monday") }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        weekKeys.forEach { key ->
            val date = remember(key) { java.time.LocalDate.parse(key) }
            val dayName = remember(key) {
                date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.US).uppercase()
            }
            val dayNo = remember(key) { String.format(Locale.US, "%02d", date.dayOfMonth) }

            val isSelected = key == activeDayKey
            val isToday = key == today
            val isFuture = key > today

            val containerBg = when {
                isSelected -> BrandPrimaryLime.copy(alpha = 0.20f)
                isToday -> AppCardSurface
                else -> MutedFill.copy(alpha = 0.5f)
            }
            val borderColor = when {
                isSelected -> BrandPrimaryLime
                isToday -> BorderInput
                else -> BorderDefault
            }
            val textColor = when {
                isSelected -> BrandPrimaryLime
                isToday -> TextForeground
                isFuture -> TextMuted.copy(alpha = 0.6f)
                else -> TextMuted
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .height(58.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(containerBg)
                    .border(BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor), RoundedCornerShape(8.dp))
                    .clickable { onDaySelected(key) },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = dayName,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        text = dayNo,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) BrandPrimaryLime else if (isFuture) TextMuted else TextForeground
                    )
                    if (isToday) {
                        Text("today", fontSize = 8.sp, color = TextMuted)
                    } else if (isSelected) {
                        Text("●", fontSize = 8.sp, color = BrandPrimaryLime)
                    } else {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

/**
 * Condensed single-column list row for library drinks (~56-64dp height).
 * Matches design tokens:
 * - Left: drink name (14sp semibold), serving (11sp muted), optional brand (10sp muted) above name.
 * - Right: compact stat cluster ("5.3% · 2.8u · 285kcal", with units in TextForeground semibold) and price ("£2.02").
 * - Far right: "+ Log" button (lime, min 36dp touch target) or disabled "Upcoming" state.
 * - Hairline divider (white @ 10% opacity) between rows.
 */
@Composable
fun DrinkLibraryItemRow(
    drink: DrinkReference,
    isFutureDay: Boolean = false,
    onLogClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val showBrand = drink.brand.isNotBlank() &&
            !drink.brand.equals(drink.name, ignoreCase = true) &&
            !drink.name.startsWith(drink.brand, ignoreCase = true)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isFutureDay) { onLogClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Brand (if differs), Name, Serving
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                if (showBrand) {
                    Text(
                        text = drink.brand.uppercase(),
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = drink.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isFutureDay) TextMuted else TextForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = drink.serving.ifBlank { "${drink.volumeMl}ml" },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right side before button: compact stat cluster & price
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(end = 12.dp)
            ) {
                // One line: "5.3% · 2.8u · 285kcal"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${drink.abv}% · ",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                    Text(
                        text = String.format(Locale.US, "%.1fu", drink.units),
                        fontSize = 10.sp,
                        color = if (isFutureDay) TextMuted else TextForeground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = " · ${drink.kcals}kcal",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = String.format(Locale.US, "£%.2f", drink.cost),
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            // Far right: "+ Log" button or "Upcoming" disabled state
            if (isFutureDay) {
                Box(
                    modifier = Modifier
                        .size(width = 68.dp, height = 36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SecondaryFill)
                        .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Upcoming",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 60.dp, height = 36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BrandPrimaryLime)
                        .clickable { onLogClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+ Log",
                        color = OnPrimaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Hairline divider: white @ 10% opacity (BorderDefault)
        HorizontalDivider(
            thickness = 0.5.dp,
            color = BorderDefault,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

/**
 * Drink Day Indicator Pill coloured by web judgement score:
 * - 0 units = JudgementGood (Green, "Not a drink day")
 * - 1+ drinks = JudgementGood/Warn/Bad based on unitsScore vs weekly target (14u)
 * (One pint is not "bad" - it scores ~90 and stays green).
 */
@Composable
fun DrinkDayIndicatorPill(
    isDrinkDay: Boolean,
    score: Double = 100.0,
    modifier: Modifier = Modifier
) {
    val pillColor = if (!isDrinkDay) {
        JudgementGood
    } else {
        scoreToJudgementColor(score)
    }

    // Line 1: state ("Drink Day" / "Not a drink day")
    val stateText = if (isDrinkDay) "Drink Day" else "Not a drink day"

    // Line 2: verdict only when applicable ("On Track" / "Watch" / "Off Track")
    val verdictText = if (isDrinkDay) {
        when {
            score >= 70.0 -> "On Track"
            score >= 40.0 -> "Watch"
            else -> "Off Track"
        }
    } else {
        null
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(pillColor.copy(alpha = 0.16f))
            .border(BorderStroke(1.dp, pillColor.copy(alpha = 0.40f)), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = if (verdictText != null) 5.dp else 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(pillColor)
            )
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stateText,
                    color = pillColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    lineHeight = 12.sp
                )
                if (verdictText != null) {
                    Text(
                        text = verdictText,
                        color = pillColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        lineHeight = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * Card displaying real-time daily totals:
 * Units, Spent, Alcohol Kcals, Drinks Logged.
 */
@Composable
fun DailyTotalsCard(
    dailyState: DailyState,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { com.scatterbrain.scatterfit.core.todayKey() }
    val dayTitle = remember(dailyState.dayKey, today) {
        formatDayTitle(dailyState.dayKey, today).uppercase()
    }
    val cardTitle = if (dailyState.dayKey == today) "TODAY'S TOTALS" else "$dayTitle'S TOTALS"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AppCardSurface),
        border = BorderStroke(1.dp, BorderDefault)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = cardTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    letterSpacing = 1.sp,
                    fontWeight = FontWeight.Bold
                )

                if (dailyState.loggedDrinks.isNotEmpty()) {
                    Text(
                        text = "Reset",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onClearLogs() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Metric 1: Units
                MetricSummaryItem(
                    label = "TOTAL UNITS",
                    value = String.format(Locale.US, "%.1f", dailyState.totalUnits),
                    accentColor = if (dailyState.totalUnits > 0) BrandPrimaryLime else TextForeground,
                    modifier = Modifier.weight(1f)
                )

                // Metric 2: Spent
                MetricSummaryItem(
                    label = "TOTAL COST",
                    value = String.format(Locale.US, "£%.2f", dailyState.totalSpent),
                    accentColor = TextForeground,
                    modifier = Modifier.weight(1f)
                )

                // Metric 3: Kcals
                MetricSummaryItem(
                    label = "ALCOHOL KCAL",
                    value = "${dailyState.totalAlcoholKcals}",
                    accentColor = TextForeground,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun MetricSummaryItem(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accentColor
        )
    }
}

/**
 * Compact section showing logged drinks with timestamp and delete option.
 * Tapping any row opens the time editor dialog.
 */
@Composable
fun LoggedDrinksSection(
    loggedDrinks: List<LoggedDrink>,
    onRemoveDrink: (LoggedDrink) -> Unit,
    onEditDrinkTime: (LoggedDrink) -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SecondaryFill),
        border = BorderStroke(1.dp, BorderDefault)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LOGGED DRINKS (${loggedDrinks.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                Text(
                    text = "Tap row to edit time",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }

            // Strips sorted newest-first by timestamp
            loggedDrinks.forEach { logged ->
                val timeStr = timeFormat.format(Date(logged.timestamp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onEditDrinkTime(logged) }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Clickable Time Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MutedFill)
                                .border(BorderStroke(0.5.dp, BorderDefault), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = timeStr,
                                color = BrandPrimaryLime,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Text(
                            text = logged.drink.name,
                            color = TextForeground,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = String.format(Locale.US, "• %.1fu · %d kcal · £%.2f", logged.drink.units, logged.drink.kcals, logged.drink.cost),
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = { onRemoveDrink(logged) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text("✕", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Material 3 TimePickerDialog with 24-hour format and clamping to Today and Yesterday.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoggedDrinkTimePickerDialog(
    loggedDrink: LoggedDrink,
    onDismissRequest: () -> Unit,
    onConfirmTime: (newTimestamp: Long) -> Unit
) {
    val cal = remember(loggedDrink.timestamp) {
        Calendar.getInstance().apply { timeInMillis = loggedDrink.timestamp }
    }
    val timePickerState = rememberTimePickerState(
        initialHour = cal.get(Calendar.HOUR_OF_DAY),
        initialMinute = cal.get(Calendar.MINUTE),
        is24Hour = true
    )

    val todayStr = remember { com.scatterbrain.scatterfit.core.todayKey() }
    val yesterdayStr = remember { com.scatterbrain.scatterfit.core.dayBefore(todayStr) }
    var selectedDayIsYesterday by remember {
        mutableStateOf(loggedDrink.dayKey == yesterdayStr)
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        containerColor = AppCardSurface,
        title = {
            Column {
                Text(
                    text = "Edit Log Time",
                    color = TextForeground,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = loggedDrink.drink.name,
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Day Selector: Today vs Yesterday
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    val days = listOf("Today" to false, "Yesterday" to true)
                    days.forEach { (label, isYesterday) ->
                        val isSelected = selectedDayIsYesterday == isYesterday
                        val bg = if (isSelected) BrandPrimaryLime else SecondaryFill
                        val textColor = if (isSelected) OnPrimaryText else TextMuted

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(bg)
                                .border(BorderStroke(1.dp, if (isSelected) BrandPrimaryLime else BorderDefault), RoundedCornerShape(16.dp))
                                .clickable { selectedDayIsYesterday = isYesterday }
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                color = textColor,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                        if (!isYesterday) Spacer(modifier = Modifier.width(8.dp))
                    }
                }

                // Material 3 TimePicker (24-hour)
                TimePicker(
                    state = timePickerState,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = SecondaryFill,
                        clockDialSelectedContentColor = OnPrimaryText,
                        clockDialUnselectedContentColor = TextForeground,
                        selectorColor = BrandPrimaryLime,
                        containerColor = AppCardSurface,
                        periodSelectorBorderColor = BorderDefault,
                        periodSelectorSelectedContainerColor = BrandPrimaryLime,
                        periodSelectorUnselectedContainerColor = SecondaryFill,
                        periodSelectorSelectedContentColor = OnPrimaryText,
                        periodSelectorUnselectedContentColor = TextForeground,
                        timeSelectorSelectedContainerColor = BrandPrimaryLime.copy(alpha = 0.2f),
                        timeSelectorUnselectedContainerColor = SecondaryFill,
                        timeSelectorSelectedContentColor = BrandPrimaryLime,
                        timeSelectorUnselectedContentColor = TextForeground
                    )
                )

                // Inline note clamping to today and yesterday
                Text(
                    text = "Can only edit today and yesterday",
                    color = TextMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val zone = ZoneId.systemDefault()
                    val targetDay = if (selectedDayIsYesterday) {
                        LocalDate.now(zone).minusDays(1)
                    } else {
                        LocalDate.now(zone)
                    }
                    val targetDateTime = targetDay.atTime(timePickerState.hour, timePickerState.minute)
                    val newTimestamp = targetDateTime.atZone(zone).toInstant().toEpochMilli()

                    // Clamp to start of yesterday (00:00 yesterday)
                    val startOfYesterday = LocalDate.now(zone).minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                    val finalTimestamp = maxOf(startOfYesterday, newTimestamp)

                    onConfirmTime(finalTimestamp)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandPrimaryLime,
                    contentColor = OnPrimaryText
                )
            ) {
                Text("Save Time", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun DrinkLoggerScreenPreview() {
    val sampleDrinks = listOf(
        DrinkReference(
            name = "Aspall Draught Cyder",
            brand = "Aspall",
            category = "cider",
            serving = "500ml Bottle",
            volumeMl = 500,
            abv = 5.3,
            units = 2.75,
            kcals = 285,
            cost = 2.02,
            fav = true
        ),
        DrinkReference(
            name = "Stella Artois",
            brand = "Stella Artois",
            category = "lager",
            serving = "Pint",
            volumeMl = 568,
            abv = 4.6,
            units = 2.6,
            kcals = 227,
            cost = 4.50,
            fav = true
        ),
        DrinkReference(
            name = "Guinness",
            brand = "Guinness",
            category = "stout",
            serving = "Pint",
            volumeMl = 568,
            abv = 4.2,
            units = 2.3,
            kcals = 210,
            cost = 4.80,
            fav = true
        )
    )

    ScatterFitTheme {
        DrinkLoggerContent(
            dailyState = DailyState(
                totalUnits = 2.75,
                totalAlcoholKcals = 285,
                totalSpent = 2.02,
                isDrinkDay = true,
                libraryDrinks = sampleDrinks,
                loggedDrinks = listOf(
                    LoggedDrink(drink = sampleDrinks.first())
                )
            ),
            onLogDrink = {},
            onRemoveLoggedDrink = {},
            onUpdateDrinkTime = { _, _ -> },
            onDaySelected = {},
            onClearLogs = {},
            onSearchQueryChange = {},
            onCategorySelect = {}
        )
    }
}
