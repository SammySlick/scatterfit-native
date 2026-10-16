package com.scatterbrain.scatterfit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.ui.theme.AccentTeal
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.DarkCardSurface
import com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.TealHighlight
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSheet(
    selectedDayKey: String,
    todayKey: String = remember { todayKey() },
    loggedDays: Set<String> = emptySet(),
    onDaySelected: (String) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val selectedDate = remember(selectedDayKey) {
        runCatching { LocalDate.parse(selectedDayKey) }.getOrDefault(LocalDate.now())
    }
    var currentMonth by remember { mutableStateOf(YearMonth.from(selectedDate)) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = DarkCardSurface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Month Navigation Row (‹ Month yyyy ›)
            val monthTitle = remember(currentMonth) {
                currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { currentMonth = currentMonth.minusMonths(1) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("‹", fontSize = 22.sp, color = TextForeground, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(SecondaryFill)
                        .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = monthTitle,
                        color = TextForeground,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(
                    onClick = { currentMonth = currentMonth.plusMonths(1) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Text("›", fontSize = 22.sp, color = TextForeground, fontWeight = FontWeight.Bold)
                }
            }

            // Days of Week Header (M, T, W, T, F, S, S, 9sp TextMuted)
            val dayHeaders = listOf("M", "T", "W", "T", "F", "S", "S")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                dayHeaders.forEach { header ->
                    Box(
                        modifier = Modifier.size(36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = header,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Month Grid Weeks
            val gridWeeks = remember(currentMonth) {
                val firstOfMonth = currentMonth.atDay(1)
                val firstMonday = firstOfMonth.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val lastOfMonth = currentMonth.atEndOfMonth()
                val lastSunday = lastOfMonth.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))

                val days = mutableListOf<LocalDate>()
                var curr = firstMonday
                while (!curr.isAfter(lastSunday)) {
                    days.add(curr)
                    curr = curr.plusDays(1)
                }
                days.chunked(7)
            }

            gridWeeks.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    week.forEach { date ->
                        val isCurrentMonth = YearMonth.from(date) == currentMonth
                        val dayKey = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                        val isSelected = dayKey == selectedDayKey
                        val isToday = dayKey == todayKey
                        val isFuture = dayKey > todayKey
                        val hasLogs = loggedDays.contains(dayKey)

                        if (isCurrentMonth) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .then(
                                            when {
                                                isSelected -> Modifier
                                                    .background(TealHighlight)
                                                    .border(BorderStroke(1.5.dp, TealHighlight), CircleShape)
                                                isToday -> Modifier
                                                    .border(BorderStroke(1.5.dp, TealHighlight), CircleShape)
                                                else -> Modifier
                                            }
                                        )
                                        .clickable(enabled = !isFuture) {
                                            onDaySelected(dayKey)
                                            onDismissRequest()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = date.dayOfMonth.toString(),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSelected -> Color.Black
                                            isFuture -> TextMuted.copy(alpha = 0.35f)
                                            else -> TextForeground
                                        },
                                        textAlign = TextAlign.Center
                                    )
                                }

                                // Tiny dot under logged day
                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .clip(CircleShape)
                                        .background(if (hasLogs && !isSelected) AccentTeal else Color.Transparent)
                                )
                            }
                        } else {
                            // Outside current month
                            Box(
                                modifier = Modifier.size(38.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    fontSize = 11.sp,
                                    color = TextMuted.copy(alpha = 0.2f),
                                    textAlign = TextAlign.Center
                                )
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
fun CalendarSheetPreview() {
    ScatterFitTheme {
        CalendarSheet(
            selectedDayKey = "2026-10-14",
            todayKey = "2026-10-14",
            loggedDays = setOf("2026-10-13", "2026-10-14"),
            onDaySelected = {},
            onDismissRequest = {}
        )
    }
}
