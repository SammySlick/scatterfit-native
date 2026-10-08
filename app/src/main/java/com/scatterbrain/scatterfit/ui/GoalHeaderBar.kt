package com.scatterbrain.scatterfit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.ui.theme.JudgementGood
import com.scatterbrain.scatterfit.ui.theme.JudgementWarn
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted

/**
 * GOAL HEADER BAR:
 * - LEFT: elapsed progress ("WEEK 1 OF 18", or "DAY 3" if under 7 days).
 * - CENTRE: goal name in big bold ("FAT LOSS").
 * - RIGHT: live projection ("ON TRACK · 8 AUG" in green, or "PROJECTED · 14 SEP" in amber).
 */
@Composable
fun GoalHeaderBar(
    goalName: String = "FAT LOSS",
    elapsedText: String = "WEEK 1 OF 18",
    projectionText: String = "ON TRACK · 8 AUG",
    isOnTrack: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // LEFT slot: elapsed progress
        Text(
            text = elapsedText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
            letterSpacing = 0.5.sp
        )

        // CENTRE slot: goal name in big bold
        Text(
            text = goalName.uppercase(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = TextForeground,
            letterSpacing = 1.sp
        )

        // RIGHT slot: LIVE projection
        val projectionColor = if (isOnTrack) JudgementGood else JudgementWarn
        Text(
            text = projectionText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = projectionColor,
            letterSpacing = 0.5.sp
        )
    }
}
