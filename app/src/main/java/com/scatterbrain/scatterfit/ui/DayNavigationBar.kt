package com.scatterbrain.scatterfit.ui

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.core.scoreToColorArgb
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.ui.theme.AppCardSurface
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.BrandPrimaryLime
import com.scatterbrain.scatterfit.ui.theme.JudgementWarn
import com.scatterbrain.scatterfit.ui.theme.OnPrimaryText
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Dual-mode day-navigation bar:
 * 1. WEEK STRIP (default): Gear far left, "< This week >" + "6 – 12 Oct" centred, streak counter far right (🔥 N).
 * 2. MONTH GRID (expanded): Calendar grid with month pill ("October 2026") and 9sp weekday initials.
 */
@Composable
fun DayNavigationBar(
    selectedDayKey: String,
    onDaySelected: (String) -> Unit,
    momentumSource: MomentumSource,
    modifier: Modifier = Modifier,
    initialExpanded: Boolean = false,
    asCard: Boolean = false,
    streakInfo: StreakInfo = StreakInfo(0, isTodayLogged = false, isAtRisk = false),
    onSettingsClick: () -> Unit = {}
) {
    val today = remember { todayKey() }
    var isExpanded by remember { mutableStateOf(initialExpanded) }

    // Week anchor tracks the Monday of the currently viewed week
    val selectedDate = remember(selectedDayKey) {
        try { LocalDate.parse(selectedDayKey) } catch (_: Exception) { LocalDate.now() }
    }
    var weekAnchorDate by remember {
        mutableStateOf(selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    }

    // Month anchor tracks the currently viewed month
    var monthAnchor by remember {
        mutableStateOf(YearMonth.from(selectedDate))
    }

    // Keep week/month anchors in sync with selected day changes
    LaunchedEffect(selectedDayKey) {
        val parsed = try { LocalDate.parse(selectedDayKey) } catch (_: Exception) { LocalDate.now() }
        weekAnchorDate = parsed.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        monthAnchor = YearMonth.from(parsed)
    }

    val dragGestureModifier = Modifier.draggable(
        orientation = Orientation.Vertical,
        state = rememberDraggableState { delta ->
            if (delta > 15 && !isExpanded) {
                isExpanded = true
            } else if (delta < -15 && isExpanded) {
                isExpanded = false
            }
        }
    )

    val content = @Composable {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isExpanded) {
                // Mode 1: Week Strip
                WeekStripContent(
                    weekStartMonday = weekAnchorDate,
                    selectedDayKey = selectedDayKey,
                    todayKey = today,
                    momentumSource = momentumSource,
                    streakInfo = streakInfo,
                    onSettingsClick = onSettingsClick,
                    onPrevWeek = { weekAnchorDate = weekAnchorDate.minusWeeks(1) },
                    onNextWeek = { weekAnchorDate = weekAnchorDate.plusWeeks(1) },
                    onDayClick = onDaySelected
                )
            } else {
                // Mode 2: Month Grid
                MonthGridContent(
                    currentMonth = monthAnchor,
                    selectedDayKey = selectedDayKey,
                    todayKey = today,
                    momentumSource = momentumSource,
                    streakInfo = streakInfo,
                    onSettingsClick = onSettingsClick,
                    onPrevMonth = { monthAnchor = monthAnchor.minusMonths(1) },
                    onNextMonth = { monthAnchor = monthAnchor.plusMonths(1) },
                    onDayClick = onDaySelected
                )
            }

            // Visible pull-down indicator: 24dp touch zone, 32x4dp pill rendered in subtle grey ~40% alpha
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clickable { isExpanded = !isExpanded },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.40f))
                )
            }
        }
    }

    if (asCard) {
        CardSurfaceBox(
            modifier = modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(280))
                .then(dragGestureModifier)
        ) {
            content()
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(280))
                .then(dragGestureModifier)
        ) {
            content()
        }
    }
}

@Composable
private fun CardSurfaceBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AppCardSurface)
            .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(14.dp))
    ) {
        content()
    }
}

/**
 * Week Strip:
 * - Header row: gear far left, "< This week >" + "6 – 12 Oct" centred, streak counter far right (🔥 N).
 * - Roomy headroom (12dp) between header and rings.
 * - Weekday initials + rings + logging dot under each logged day.
 */
@Composable
private fun WeekStripContent(
    weekStartMonday: LocalDate,
    selectedDayKey: String,
    todayKey: String,
    momentumSource: MomentumSource,
    streakInfo: StreakInfo,
    onSettingsClick: () -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onDayClick: (String) -> Unit
) {
    val todayDate = remember(todayKey) {
        try { LocalDate.parse(todayKey) } catch (_: Exception) { LocalDate.now() }
    }
    val currentWeekMonday = remember(todayDate) {
        todayDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

    val weekLabel = remember(weekStartMonday, currentWeekMonday) {
        when (weekStartMonday) {
            currentWeekMonday -> "This week"
            currentWeekMonday.minusWeeks(1) -> "Last week"
            currentWeekMonday.plusWeeks(1) -> "Next week"
            else -> {
                val end = weekStartMonday.plusDays(6)
                if (weekStartMonday.month == end.month) {
                    "${weekStartMonday.dayOfMonth} – ${end.dayOfMonth} ${end.format(DateTimeFormatter.ofPattern("MMM", Locale.US))}"
                } else {
                    "${weekStartMonday.format(DateTimeFormatter.ofPattern("d MMM", Locale.US))} – ${end.format(DateTimeFormatter.ofPattern("d MMM", Locale.US))}"
                }
            }
        }
    }

    // Date range under the week nav: "6 – 12 Oct" (Mon–Sun of displayed week)
    val dateRangeText = remember(weekStartMonday) {
        val sun = weekStartMonday.plusDays(6)
        if (weekStartMonday.month == sun.month) {
            "${weekStartMonday.dayOfMonth} – ${sun.dayOfMonth} ${sun.format(DateTimeFormatter.ofPattern("MMM", Locale.US))}"
        } else {
            "${weekStartMonday.dayOfMonth} ${weekStartMonday.format(DateTimeFormatter.ofPattern("MMM", Locale.US))} – ${sun.dayOfMonth} ${sun.format(DateTimeFormatter.ofPattern("MMM", Locale.US))}"
        }
    }

    // Muted when next week would extend past today + 7
    val canGoNextWeek = remember(weekStartMonday, todayDate) {
        !weekStartMonday.plusWeeks(1).isAfter(todayDate.plusDays(7))
    }

    val weekDays = remember(weekStartMonday) {
        (0L..6L).map { weekStartMonday.plusDays(it) }
    }
    val weekdayInitials = listOf("M", "T", "W", "T", "F", "S", "S")

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // New header row: Gear far left, Nav + Date range centred, Streak counter far right
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Far Left: Gear icon
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(36.dp)
            ) {
                SettingsIcon(
                    modifier = Modifier.size(20.dp),
                    tint = TextForeground
                )
            }

            // Centre: "< This week >" + date range under it
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = onPrevWeek,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text(
                            text = "‹",
                            fontSize = 20.sp,
                            color = TextForeground,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = weekLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextForeground
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = { if (canGoNextWeek) onNextWeek() },
                        enabled = canGoNextWeek,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text(
                            text = "›",
                            fontSize = 20.sp,
                            color = if (canGoNextWeek) TextForeground else TextMuted.copy(alpha = 0.3f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Date range under week nav: "6 – 12 Oct", muted grey, small
                Text(
                    text = dateRangeText,
                    fontSize = 10.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Normal
                )
            }

            // Far Right: Streak counter (🔥 N)
            if (streakInfo.count > 0) {
                if (streakInfo.isAtRisk) {
                    // Dimmed / amber with subtle at-risk border
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, JudgementWarn.copy(alpha = 0.5f)), RoundedCornerShape(12.dp))
                            .background(JudgementWarn.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            StreakIcon(
                                modifier = Modifier.size(13.dp),
                                tint = JudgementWarn
                            )
                            Text(
                                text = "${streakInfo.count}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = JudgementWarn
                            )
                        }
                    }
                } else {
                    // Solid lime streak
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(BrandPrimaryLime.copy(alpha = 0.16f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            StreakIcon(
                                modifier = Modifier.size(13.dp),
                                tint = BrandPrimaryLime
                            )
                            Text(
                                text = "${streakInfo.count}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrandPrimaryLime
                            )
                        }
                    }
                }
            } else {
                // Balance the gear on the left with empty spacer
                Spacer(modifier = Modifier.size(36.dp))
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Weekday initials + 7 Day circles + logged dot in columns
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val circleSize = if (maxWidth >= 420.dp) 44.dp else 40.dp
            val strokeWidth = 3.dp

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                weekDays.forEachIndexed { index, date ->
                    val dayKey = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val isSelected = dayKey == selectedDayKey
                    val isToday = dayKey == todayKey
                    val isFuture = dayKey > todayKey
                    val score = momentumSource.momentumForDay(dayKey)
                    val initial = weekdayInitials.getOrElse(index) { "M" }
                    val isLogged = momentumSource.isDayLogged(dayKey)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = initial,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )

                        DayMomentumCircle(
                            date = date,
                            isSelected = isSelected,
                            isToday = isToday,
                            isFuture = isFuture,
                            momentumScore = score,
                            circleSize = circleSize,
                            strokeWidth = strokeWidth,
                            onClick = { onDayClick(dayKey) }
                        )

                        // Small dot under each day that was fully logged
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(if (isLogged) BrandPrimaryLime else Color.Transparent)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Month Grid: Month pill ("October 2026") with ‹ ›, M-T-W-T-F-S-S headers (9sp TextMuted), and calendar grid.
 */
@Composable
private fun MonthGridContent(
    currentMonth: YearMonth,
    selectedDayKey: String,
    todayKey: String,
    momentumSource: MomentumSource,
    streakInfo: StreakInfo,
    onSettingsClick: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (String) -> Unit
) {
    val monthTitle = remember(currentMonth) {
        currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US))
    }

    // Build grid days (Monday-first, full weeks spanning the month)
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Month Header Row with Gear, Month Pill, and Streak
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.size(36.dp)
            ) {
                SettingsIcon(modifier = Modifier.size(20.dp), tint = TextForeground)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onPrevMonth,
                    modifier = Modifier.size(28.dp)
                ) {
                    Text("‹", fontSize = 20.sp, color = TextForeground, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(SecondaryFill)
                        .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = monthTitle,
                        color = TextForeground,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                IconButton(
                    onClick = onNextMonth,
                    modifier = Modifier.size(28.dp)
                ) {
                    Text("›", fontSize = 20.sp, color = TextForeground, fontWeight = FontWeight.Bold)
                }
            }

            if (streakInfo.count > 0) {
                Text(
                    text = "🔥 ${streakInfo.count}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (streakInfo.isAtRisk) JudgementWarn else BrandPrimaryLime
                )
            } else {
                Spacer(modifier = Modifier.size(36.dp))
            }
        }

        // Days of Week Header (Monday-first, 9sp TextMuted)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            val dayHeaders = listOf("M", "T", "W", "T", "F", "S", "S")
            dayHeaders.forEach { header ->
                Box(
                    modifier = Modifier.size(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = header,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Month Calendar Grid (weeks)
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
                    val score = if (isCurrentMonth) momentumSource.momentumForDay(dayKey) else null
                    val isLogged = if (isCurrentMonth) momentumSource.isDayLogged(dayKey) else false

                    if (isCurrentMonth) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            DayMomentumCircle(
                                date = date,
                                isSelected = isSelected,
                                isToday = isToday,
                                isFuture = isFuture,
                                momentumScore = score,
                                circleSize = 36.dp,
                                strokeWidth = 2.5.dp,
                                onClick = { onDayClick(dayKey) }
                            )

                            // Small dot under logged day
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .clip(CircleShape)
                                    .background(if (isLogged) BrandPrimaryLime else Color.Transparent)
                            )
                        }
                    } else {
                        // Days outside current month rendered faint
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    color = TextMuted.copy(alpha = 0.25f),
                                    textAlign = TextAlign.Center,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                ),
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual momentum day circle:
 * - Ring stroke ~3dp (week strip) / ~2.5dp (month grid)
 * - Ring coloured by scoreToColorArgb(momentumScore) (muted when score is null)
 * - Selected: solid filled lime + black text
 * - Today: lime ring + lime-tinted fill
 * - Future: muted ring, faint text (alpha 0.4), same optical baseline
 * - Day numbers optically centred via Box(contentAlignment = Alignment.Center) with includeFontPadding = false
 */
@Composable
fun DayMomentumCircle(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    momentumScore: Double?,
    circleSize: Dp = 44.dp,
    strokeWidth: Dp = 3.dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ringColor = when {
        isSelected -> BrandPrimaryLime
        isToday -> BrandPrimaryLime
        isFuture -> BorderDefault
        momentumScore != null -> Color(scoreToColorArgb(momentumScore))
        else -> BorderDefault
    }

    val fillColor = when {
        isSelected -> BrandPrimaryLime
        isToday -> BrandPrimaryLime.copy(alpha = 0.15f)
        momentumScore != null -> ringColor.copy(alpha = 0.10f)
        else -> Color.Transparent
    }

    val textColor = when {
        isSelected -> OnPrimaryText // black text on filled lime
        isToday -> BrandPrimaryLime
        isFuture -> TextMuted.copy(alpha = 0.40f)
        else -> TextForeground
    }

    Box(
        modifier = modifier
            .size(circleSize)
            .clip(CircleShape)
            .background(fillColor)
            .border(
                BorderStroke(strokeWidth, ringColor),
                CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = TextStyle(
                color = textColor,
                fontSize = if (circleSize >= 42.dp) 14.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            ),
            modifier = Modifier.align(Alignment.Center)
        )
    }
}
