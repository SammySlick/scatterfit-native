package com.scatterbrain.scatterfit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.core.CHECK_IN_ROWS
import com.scatterbrain.scatterfit.core.CheckInRow
import com.scatterbrain.scatterfit.core.CheckInScale
import com.scatterbrain.scatterfit.core.CheckInSection
import com.scatterbrain.scatterfit.core.dayBefore
import com.scatterbrain.scatterfit.core.formatAverage
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.core.weekKeysFor
import com.scatterbrain.scatterfit.data.DailyRepository
import com.scatterbrain.scatterfit.data.WeeklyCheckInStorage
import com.scatterbrain.scatterfit.ui.theme.AccentTeal
import com.scatterbrain.scatterfit.ui.theme.AppBackground
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private sealed class CheckInDisplayRow {
    data class Manual(val row: CheckInRow) : CheckInDisplayRow()
    data class Auto(
        val id: String,
        val label: String,
        val formatValue: (Double) -> String,
        val getValue: (dayKey: String) -> Double?
    ) : CheckInDisplayRow()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckInScreen(
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val storage = remember(context) { WeeklyCheckInStorage(context) }
    var saveTick by remember { mutableIntStateOf(0) }
    var weekAnchor by remember { mutableStateOf(todayKey()) }
    var editingRow by remember { mutableStateOf<CheckInRow?>(null) }

    val today = remember { todayKey() }
    val days = remember(weekAnchor) { weekKeysFor(weekAnchor) }
    val weekStart = days.first()
    val currentWeekStart = remember(today) { weekKeysFor(today).first() }
    val isCurrentWeek = weekStart == currentWeekStart

    val daily by DailyRepository.daily.collectAsState()
    val checkInRecord = remember(weekStart, saveTick) { storage.load(weekStart) }

    // Navigation helpers
    val onPrevWeek = {
        editingRow = null
        weekAnchor = dayBefore(weekStart)
    }
    val onNextWeek = {
        if (!isCurrentWeek) {
            editingRow = null
            weekAnchor = LocalDate.parse(weekStart).plusDays(7).toString()
        }
    }

    // Auto rows definition
    val autoWeight = remember(daily) {
        CheckInDisplayRow.Auto(
            id = "auto_weight",
            label = "Weight",
            formatValue = { String.format(Locale.US, "%.1f", it) },
            getValue = { daily?.weight?.get(it)?.takeIf { w -> w > 0.0 } }
        )
    }
    val autoCalories = remember(daily) {
        CheckInDisplayRow.Auto(
            id = "auto_calories",
            label = "Calories",
            formatValue = { String.format(Locale.US, "%.0f", it) },
            getValue = { daily?.caloriesEaten?.get(it)?.takeIf { c -> c > 0.0 } }
        )
    }
    val autoProtein = remember(daily) {
        CheckInDisplayRow.Auto(
            id = "auto_protein",
            label = "Protein",
            formatValue = { String.format(Locale.US, "%.0f", it) },
            getValue = { daily?.protein?.get(it)?.takeIf { p -> p > 0.0 } }
        )
    }
    val autoSteps = remember(daily) {
        CheckInDisplayRow.Auto(
            id = "auto_steps",
            label = "Steps",
            formatValue = { String.format(Locale.US, "%.0f", it) },
            getValue = { daily?.steps?.get(it)?.takeIf { s -> s > 0.0 } }
        )
    }

    // Sections mapping
    val sectionsToRender = remember(daily, checkInRecord) {
        listOf(
            CheckInSection.BODY to listOf(
                autoWeight,
                *CHECK_IN_ROWS.filter { it.section == CheckInSection.BODY }.map { CheckInDisplayRow.Manual(it) }.toTypedArray()
            ),
            CheckInSection.BIOFEEDBACK to CHECK_IN_ROWS.filter { it.section == CheckInSection.BIOFEEDBACK }.map { CheckInDisplayRow.Manual(it) },
            CheckInSection.NUTRITION to listOf(
                *CHECK_IN_ROWS.filter { it.section == CheckInSection.NUTRITION }.map { CheckInDisplayRow.Manual(it) }.toTypedArray(),
                autoCalories,
                autoProtein
            ),
            CheckInSection.DIGESTION to CHECK_IN_ROWS.filter { it.section == CheckInSection.DIGESTION }.map { CheckInDisplayRow.Manual(it) },
            CheckInSection.TRAINING to listOf(autoSteps),
            CheckInSection.SUMMARY to CHECK_IN_ROWS.filter { it.section == CheckInSection.SUMMARY }.map { CheckInDisplayRow.Manual(it) }
        ).filter { it.second.isNotEmpty() }
    }

    // Count filled rows
    val filledCount = remember(checkInRecord, daily, days) {
        var count = 0
        for (row in CHECK_IN_ROWS) {
            val hasVal = if (row.perDay) {
                days.any { dayKey -> checkInRecord.perDay[dayKey]?.get(row.id)?.isNotBlank() == true }
            } else {
                checkInRecord.weekly[row.id]?.isNotBlank() == true
            }
            if (hasVal) count++
        }
        if (days.any { (daily?.weight?.get(it) ?: 0.0) > 0.0 }) count++
        if (days.any { (daily?.caloriesEaten?.get(it) ?: 0.0) > 0.0 }) count++
        if (days.any { (daily?.protein?.get(it) ?: 0.0) > 0.0 }) count++
        if (days.any { (daily?.steps?.get(it) ?: 0.0) > 0.0 }) count++
        count
    }
    val rowCount = CHECK_IN_ROWS.size + 3

    val weekStartDate = runCatching { LocalDate.parse(weekStart) }.getOrDefault(LocalDate.now())
    val weekTitle = "Week of " + weekStartDate.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.US))

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // (1) WEEK HEADER
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable { onPrevWeek() },
                contentAlignment = Alignment.Center
            ) {
                ChevronLeftIcon(
                    modifier = Modifier.size(18.dp),
                    tint = TextForeground
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = weekTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextForeground
                )
                Text(
                    text = "$filledCount of $rowCount filled",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable(enabled = !isCurrentWeek) { onNextWeek() },
                contentAlignment = Alignment.Center
            ) {
                ChevronRightIcon(
                    modifier = Modifier.size(18.dp),
                    tint = if (isCurrentWeek) TextMuted else TextForeground
                )
            }
        }

        // (2) DAY LETTER HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                days.forEach { dayKey ->
                    val date = runCatching { LocalDate.parse(dayKey) }.getOrNull()
                    val letter = when (date?.dayOfWeek) {
                        DayOfWeek.MONDAY -> "M"
                        DayOfWeek.TUESDAY -> "T"
                        DayOfWeek.WEDNESDAY -> "W"
                        DayOfWeek.THURSDAY -> "T"
                        DayOfWeek.FRIDAY -> "F"
                        DayOfWeek.SATURDAY -> "S"
                        DayOfWeek.SUNDAY -> "S"
                        else -> ""
                    }
                    val isToday = dayKey == today
                    Box(
                        modifier = Modifier.size(22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = letter,
                            fontSize = 11.sp,
                            color = if (isToday) AccentTeal else TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(56.dp))
        }

        // (CHANGE 4) HINT LINE
        Text(
            text = "Tap a row to enter values",
            fontSize = 11.sp,
            color = TextMuted,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // (3) SECTIONS
        sectionsToRender.forEach { (section, rows) ->
            Text(
                text = section.name.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(top = 20.dp)
            )

            rows.forEach { displayRow ->
                when (displayRow) {
                    is CheckInDisplayRow.Manual -> {
                        val row = displayRow.row
                        val valuesList = if (row.perDay) {
                            days.mapNotNull { checkInRecord.perDay[it]?.get(row.id) }
                        } else {
                            listOfNotNull(checkInRecord.weekly[row.id])
                        }
                        val avg = if (row.scale == CheckInScale.TEXT) {
                            ""
                        } else {
                            formatAverage(row.scale, valuesList) ?: "—"
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clickable { editingRow = row },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = row.label,
                                fontSize = 15.sp,
                                color = TextForeground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                days.forEachIndexed { idx, dayKey ->
                                    val rawVal = if (row.perDay) {
                                        checkInRecord.perDay[dayKey]?.get(row.id)
                                    } else {
                                        if (idx == 6) checkInRecord.weekly[row.id] else null
                                    }
                                    val hasValue = rawVal?.isNotBlank() == true
                                    val isToday = dayKey == today
                                    val borderModifier = when {
                                        isToday -> Modifier.border(1.dp, AccentTeal, RoundedCornerShape(3.dp))
                                        !hasValue -> Modifier.border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(3.dp))
                                        else -> Modifier
                                    }

                                    val cellText = if (hasValue) {
                                        when (row.scale) {
                                            CheckInScale.SCALE_1_5 -> rawVal
                                            CheckInScale.YES_NO -> when (rawVal.lowercase()) {
                                                "true", "yes", "1" -> "Y"
                                                "false", "no", "0" -> "N"
                                                else -> null
                                            }
                                            CheckInScale.NUMBER, CheckInScale.TEXT -> null
                                        }
                                    } else null

                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .then(if (hasValue) Modifier.background(SecondaryFill) else Modifier)
                                            .then(borderModifier),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (cellText != null) {
                                            if (row.scale == CheckInScale.SCALE_1_5) {
                                                Text(
                                                    text = cellText,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = TextForeground,
                                                    textAlign = TextAlign.Center
                                                )
                                            } else if (row.scale == CheckInScale.YES_NO) {
                                                Text(
                                                    text = cellText,
                                                    fontSize = 10.sp,
                                                    color = TextMuted,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Text(
                                text = avg,
                                modifier = Modifier.width(56.dp),
                                textAlign = TextAlign.End,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (avg == "—" || avg.isEmpty()) TextMuted else TextForeground
                            )
                        }
                    }

                    is CheckInDisplayRow.Auto -> {
                        val nonNullValues = days.mapNotNull { displayRow.getValue(it) }
                        val avg = if (nonNullValues.isEmpty()) {
                            "—"
                        } else {
                            displayRow.formatValue(nonNullValues.sum() / nonNullValues.size)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = displayRow.label,
                                fontSize = 15.sp,
                                color = TextForeground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                days.forEach { dayKey ->
                                    val hasValue = displayRow.getValue(dayKey) != null
                                    val isToday = dayKey == today
                                    val borderModifier = when {
                                        isToday -> Modifier.border(1.dp, AccentTeal, RoundedCornerShape(3.dp))
                                        !hasValue -> Modifier.border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(3.dp))
                                        else -> Modifier
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .then(if (hasValue) Modifier.background(SecondaryFill) else Modifier)
                                            .then(borderModifier)
                                    )
                                }
                            }
                            Text(
                                text = avg,
                                modifier = Modifier.width(56.dp),
                                textAlign = TextAlign.End,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (avg == "—") TextMuted else TextForeground
                            )
                        }
                    }
                }
            }
        }
    }

    // (CHANGE 5) ENTRY SHEET
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (editingRow != null) {
        val row = editingRow!!
        ModalBottomSheet(
            onDismissRequest = { editingRow = null },
            sheetState = sheetState,
            containerColor = AppBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = row.label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextForeground
                )

                if (row.perDay) {
                    days.forEach { dayKey ->
                        val date = runCatching { LocalDate.parse(dayKey) }.getOrNull()
                        val dayLabel = date?.format(DateTimeFormatter.ofPattern("EEE d", Locale.US)) ?: dayKey
                        val currentVal = checkInRecord.perDay[dayKey]?.get(row.id)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dayLabel,
                                fontSize = 13.sp,
                                color = TextMuted,
                                modifier = Modifier.width(64.dp)
                            )
                            CheckInControl(
                                scale = row.scale,
                                currentValue = currentVal,
                                onValueChange = { newVal ->
                                    storage.setDayValue(weekStart, dayKey, row.id, newVal)
                                    saveTick++
                                }
                            )
                        }
                    }
                } else {
                    val label = if (row.scale == CheckInScale.TEXT) "This week" else "Measured Sunday"
                    val currentVal = checkInRecord.weekly[row.id]

                    if (row.scale == CheckInScale.TEXT) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                        CheckInControl(
                            scale = row.scale,
                            currentValue = currentVal,
                            onValueChange = { newVal ->
                                storage.setWeeklyValue(weekStart, row.id, newVal)
                                saveTick++
                            }
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                color = TextMuted,
                                modifier = Modifier.width(130.dp)
                            )
                            CheckInControl(
                                scale = row.scale,
                                currentValue = currentVal,
                                onValueChange = { newVal ->
                                    storage.setWeeklyValue(weekStart, row.id, newVal)
                                    saveTick++
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckInControl(
    scale: CheckInScale,
    currentValue: String?,
    onValueChange: (String?) -> Unit
) {
    when (scale) {
        CheckInScale.SCALE_1_5 -> {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..5).forEach { score ->
                    val strScore = score.toString()
                    val isSelected = currentValue == strScore
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) AccentTeal else SecondaryFill)
                            .clickable {
                                val next = if (isSelected) null else strScore
                                onValueChange(next)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strScore,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) AppBackground else TextMuted
                        )
                    }
                }
            }
        }

        CheckInScale.YES_NO -> {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Yes" to "true", "No" to "false").forEach { (label, rawVal) ->
                    val isSelected = currentValue?.equals(rawVal, ignoreCase = true) == true ||
                                     currentValue?.equals(label, ignoreCase = true) == true
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) AccentTeal else SecondaryFill)
                            .clickable {
                                val next = if (isSelected) null else rawVal
                                onValueChange(next)
                            }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isSelected) AppBackground else TextMuted
                        )
                    }
                }
            }
        }

        CheckInScale.NUMBER -> {
            var text by remember(currentValue) { mutableStateOf(currentValue ?: "") }
            OutlinedTextField(
                value = text,
                onValueChange = { newText ->
                    text = newText
                    onValueChange(newText.trim().ifBlank { null })
                },
                modifier = Modifier.width(88.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextForeground,
                    unfocusedTextColor = TextForeground,
                    focusedBorderColor = AccentTeal,
                    unfocusedBorderColor = BorderDefault,
                    focusedContainerColor = SecondaryFill,
                    unfocusedContainerColor = SecondaryFill
                )
            )
        }

        CheckInScale.TEXT -> {
            var text by remember(currentValue) { mutableStateOf(currentValue ?: "") }
            OutlinedTextField(
                value = text,
                onValueChange = { newText ->
                    text = newText
                    onValueChange(newText.ifBlank { null })
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 8,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextForeground,
                    unfocusedTextColor = TextForeground,
                    focusedBorderColor = AccentTeal,
                    unfocusedBorderColor = BorderDefault,
                    focusedContainerColor = SecondaryFill,
                    unfocusedContainerColor = SecondaryFill
                )
            )
        }
    }
}
