package com.scatterbrain.scatterfit.ui

import androidx.compose.ui.graphics.Color
import com.scatterbrain.scatterfit.core.dayBefore
import com.scatterbrain.scatterfit.core.scoreToColorArgb
import com.scatterbrain.scatterfit.core.todayKey
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun scoreToJudgementColor(score: Double?): Color = Color(scoreToColorArgb(score))

fun formatDayKeyTitle(key: String, today: String = todayKey()): String {
    if (key == today) return "Today"
    if (key == dayBefore(today)) return "Yesterday"
    val date = LocalDate.parse(key, DateTimeFormatter.ISO_LOCAL_DATE)
    return date.format(DateTimeFormatter.ofPattern("EEE, dd", Locale.US))
}

fun formatDayTitle(key: String, today: String = todayKey()): String = formatDayKeyTitle(key, today)

fun formatCursorDate(cursorMs: Long?, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
    if (cursorMs == null) return "no cursor yet"
    val instant = java.time.Instant.ofEpochMilli(cursorMs)
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US).withZone(zone)
    return formatter.format(instant)
}

