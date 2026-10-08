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
