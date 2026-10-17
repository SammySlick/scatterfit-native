package com.scatterbrain.scatterfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.ui.theme.AccentTeal
import com.scatterbrain.scatterfit.ui.theme.AppBackground
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.DarkCardSurface
import com.scatterbrain.scatterfit.ui.theme.JudgementWarn
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.StateBad
import com.scatterbrain.scatterfit.ui.theme.StateLocked
import com.scatterbrain.scatterfit.ui.theme.TealHighlight
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import java.util.Locale

data class PersonalBest(
    val exerciseName: String,
    val newMaxWeightKg: Double,
    val previousMaxWeightKg: Double?,
)

fun detectPersonalBests(
    currentSession: SessionRecord,
    previousHistory: List<SessionRecord>,
): List<PersonalBest> {
    val pbs = mutableListOf<PersonalBest>()

    for (exercise in currentSession.exercises) {
        val currentMax = exercise.sets
            .filter { it.done && it.weightKg != null }
            .mapNotNull { it.weightKg }
            .maxOrNull() ?: 0.0

        if (currentMax <= 0.0) continue

        val priorDoneSets = previousHistory
            .filter { it.id != currentSession.id }
            .flatMap { it.exercises }
            .filter { it.exerciseId == exercise.exerciseId }
            .flatMap { it.sets }
            .filter { it.done && it.weightKg != null }

        val priorMax = priorDoneSets.mapNotNull { it.weightKg }.maxOrNull()

        if (priorMax == null) {
            // First time logged with weight -> PB
            pbs.add(
                PersonalBest(
                    exerciseName = exercise.exerciseName,
                    newMaxWeightKg = currentMax,
                    previousMaxWeightKg = null
                )
            )
        } else if (currentMax > priorMax) {
            // New PB strictly higher than prior max
            pbs.add(
                PersonalBest(
                    exerciseName = exercise.exerciseName,
                    newMaxWeightKg = currentMax,
                    previousMaxWeightKg = priorMax
                )
            )
        }
    }

    return pbs
}

fun calculateTotalVolumeKg(session: SessionRecord): Double {
    return session.exercises.sumOf { ex ->
        ex.sets.filter { it.done && it.weightKg != null }.sumOf { s ->
            val weight = s.weightKg ?: 0.0
            val reps = (s.repsMin ?: s.repsMax ?: 0).toDouble()
            weight * reps
        }
    }
}

fun calculateTotalTimeUnderTensionSeconds(session: SessionRecord): Int {
    return session.exercises.sumOf { ex ->
        ex.sets.filter { it.done && it.timeSeconds != null }.sumOf { s ->
            s.timeSeconds ?: 0
        }
    }
}

fun isTimedOnlySession(session: SessionRecord): Boolean {
    val hasVolume = session.exercises.any { ex ->
        ex.sets.any { it.done && (it.weightKg != null || it.repsMin != null || it.repsMax != null) }
    }
    val hasTime = session.exercises.any { ex ->
        ex.sets.any { it.done && it.timeSeconds != null }
    }
    return !hasVolume && hasTime
}

fun calculateAverageDifficulty(session: SessionRecord): String? {
    val diffs = session.exercises.flatMap { it.sets }
        .filter { it.done && !it.difficulty.isNullOrBlank() }
        .mapNotNull { it.difficulty?.lowercase() }

    if (diffs.isEmpty()) return null
    val mode = diffs.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: return null
    return when (mode) {
        "easy" -> "EASY"
        "medium" -> "MED"
        "hard" -> "HARD"
        else -> mode.uppercase()
    }
}

@Composable
fun WorkoutSummaryScreen(
    record: SessionRecord,
    previousHistory: List<SessionRecord>,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDone)
    val scrollState = rememberScrollState()

    val pbs = remember(record, previousHistory) {
        detectPersonalBests(record, previousHistory)
    }

    val totalVolumeKg = remember(record) { calculateTotalVolumeKg(record) }
    val isTimedOnly = remember(record) { isTimedOnlySession(record) }
    val timeUnderTensionSecs = remember(record) { calculateTotalTimeUnderTensionSeconds(record) }

    val elapsedSeconds = remember(record) {
        if (record.endedAt != null && record.endedAt > record.startedAt) {
            ((record.endedAt - record.startedAt) / 1000L).toInt()
        } else {
            (record.durationMinutes ?: 1) * 60
        }
    }

    val totalDoneSets = remember(record) {
        record.exercises.sumOf { ex -> ex.sets.count { it.done } }
    }

    val avgDiff = remember(record) { calculateAverageDifficulty(record) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header with big teal checkmark
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(TealHighlight.copy(alpha = 0.15f))
                        .border(BorderStroke(2.dp, TealHighlight), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✓",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealHighlight
                    )
                }

                Text(
                    text = "Session complete",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = record.name,
                    fontSize = 14.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }

            // Big Hero Stat Card: Total Volume or Time Under Tension
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = BorderStroke(1.dp, BorderDefault)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isTimedOnly) {
                        Text(
                            text = formatClockTime(timeUnderTensionSecs),
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextForeground,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "total time under tension",
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                    } else {
                        val formattedVolume = if (totalVolumeKg % 1.0 == 0.0) {
                            String.format(Locale.US, "%,d kg", totalVolumeKg.toInt())
                        } else {
                            String.format(Locale.US, "%,.1f kg", totalVolumeKg)
                        }
                        Text(
                            text = formattedVolume,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextForeground
                        )
                        Text(
                            text = "total volume lifted",
                            fontSize = 13.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            // Stat Row: Duration · Sets · Exercises · Avg Diff
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = BorderStroke(1.dp, BorderDefault)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Duration
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatClockTime(elapsedSeconds),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextForeground
                        )
                        Text("duration", fontSize = 11.sp, color = TextMuted)
                    }

                    // Sets
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$totalDoneSets",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextForeground
                        )
                        Text(if (totalDoneSets == 1) "set" else "sets", fontSize = 11.sp, color = TextMuted)
                    }

                    // Exercises
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${record.exercises.size}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextForeground
                        )
                        Text(if (record.exercises.size == 1) "exercise" else "exercises", fontSize = 11.sp, color = TextMuted)
                    }

                    // Avg Difficulty (if any set had difficulty)
                    if (avgDiff != null) {
                        val diffColor = when (avgDiff) {
                            "EASY" -> StateLocked
                            "MED" -> JudgementWarn
                            "HARD" -> StateBad
                            else -> TextForeground
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = avgDiff,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = diffColor
                            )
                            Text("difficulty", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }
            }

            // PERSONAL BESTS section
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "PERSONAL BESTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )

                if (pbs.isNotEmpty()) {
                    pbs.forEach { pb ->
                        val weightStr = if (pb.newMaxWeightKg % 1.0 == 0.0) "${pb.newMaxWeightKg.toInt()} kg" else "${pb.newMaxWeightKg} kg"
                        val prevStr = if (pb.previousMaxWeightKg != null) {
                            val pw = if (pb.previousMaxWeightKg % 1.0 == 0.0) "${pb.previousMaxWeightKg.toInt()} kg" else "${pb.previousMaxWeightKg} kg"
                            "was $pw"
                        } else {
                            "first time logged"
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                            border = BorderStroke(1.dp, BorderDefault)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "${pb.exerciseName} — $weightStr",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextForeground
                                    )
                                    Text(
                                        text = prevStr,
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(TealHighlight.copy(alpha = 0.15f))
                                        .border(BorderStroke(1.dp, TealHighlight.copy(alpha = 0.4f)), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "NEW PB",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TealHighlight
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                        border = BorderStroke(1.dp, BorderDefault)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No PBs this time — they'll come",
                                fontSize = 13.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer: [Done] button
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = TealHighlight, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Done",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}
