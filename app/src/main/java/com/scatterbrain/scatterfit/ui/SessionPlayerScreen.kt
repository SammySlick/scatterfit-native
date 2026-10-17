package com.scatterbrain.scatterfit.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.data.DailyRepository
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
import com.scatterbrain.scatterfit.ui.theme.TextFaint
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import kotlinx.coroutines.delay
import java.util.UUID

data class RestTimerState(
    val totalSeconds: Int = 0,
    val remainingSeconds: Int = 0,
    val isRunning: Boolean = false,
    val isExpired: Boolean = false,
)

fun startRestTimer(seconds: Int): RestTimerState {
    if (seconds <= 0) return RestTimerState()
    return RestTimerState(
        totalSeconds = seconds,
        remainingSeconds = seconds,
        isRunning = true,
        isExpired = false
    )
}

fun tickRestTimer(current: RestTimerState): RestTimerState {
    if (!current.isRunning || current.remainingSeconds <= 0) return current
    val next = current.remainingSeconds - 1
    return if (next <= 0) {
        current.copy(remainingSeconds = 0, isRunning = false, isExpired = true)
    } else {
        current.copy(remainingSeconds = next)
    }
}

class PlayerSetState(
    val setIndex: Int,
    initialWeightKg: Double?,
    initialRepsMin: Int?,
    initialRepsMax: Int?,
    initialTimeSeconds: Int?,
    initialTimeUnit: String,
    initialDifficulty: String?,
) {
    var weightKg by mutableStateOf(initialWeightKg)
    var repsMin by mutableStateOf(initialRepsMin)
    var repsMax by mutableStateOf(initialRepsMax)
    var timeSeconds by mutableStateOf(initialTimeSeconds)
    var timeUnit by mutableStateOf(initialTimeUnit)
    var difficulty by mutableStateOf(initialDifficulty)
    var isDone by mutableStateOf(false)

    // Stopwatch hold count-up for timed sets
    var isHolding by mutableStateOf(false)
    var holdSeconds by mutableIntStateOf(0)
}

class PlayerExerciseState(
    val exerciseId: String,
    val exerciseName: String,
    val restSeconds: Int,
    val isTimed: Boolean,
    val sets: List<PlayerSetState>,
)

@Composable
fun SessionPlayerScreen(
    routine: Routine? = null,
    sessionName: String? = null,
    dayKey: String = remember { todayKey() },
    sessionRecordsStorage: SessionRecordsStorage,
    loggedSessionsStorage: LoggedSessionsStorage,
    onFinish: (SessionRecord) -> Unit,
    onAbandon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Keep screen on during workout
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val workoutTitle = routine?.name ?: sessionName ?: "Workout"
    val startedAt = remember { System.currentTimeMillis() }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            elapsedSeconds = (System.currentTimeMillis() - startedAt) / 1000L
        }
    }

    // Convert planned exercises and sets to mutable player states
    val exerciseStates = remember(routine) {
        if (routine != null) {
            routine.exercises.map { ex ->
                PlayerExerciseState(
                    exerciseId = ex.exerciseId,
                    exerciseName = ex.displayName,
                    restSeconds = ex.restSeconds,
                    isTimed = ex.isTimed,
                    sets = ex.sets.mapIndexed { idx, s ->
                        PlayerSetState(
                            setIndex = idx,
                            initialWeightKg = s.weightKg,
                            initialRepsMin = s.repsMin,
                            initialRepsMax = s.repsMax,
                            initialTimeSeconds = s.timeSeconds,
                            initialTimeUnit = s.timeUnit,
                            initialDifficulty = null
                        )
                    }
                )
            }
        } else {
            // Standalone session: single exercise wrapper
            listOf(
                PlayerExerciseState(
                    exerciseId = "standalone-${UUID.randomUUID()}",
                    exerciseName = workoutTitle,
                    restSeconds = 0,
                    isTimed = false,
                    sets = listOf(
                        PlayerSetState(0, null, null, null, null, "sec", null)
                    )
                )
            )
        }
    }

    // Rest timer state
    var restTimer by remember { mutableStateOf(RestTimerState()) }
    var restDoneBannerVisible by remember { mutableStateOf(false) }

    LaunchedEffect(restTimer.isRunning) {
        if (restTimer.isRunning) {
            while (restTimer.isRunning && restTimer.remainingSeconds > 0) {
                delay(1000L)
                restTimer = tickRestTimer(restTimer)
            }
            if (restTimer.isExpired) {
                // Vibrate once on rest done
                try {
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    if (vibrator != null && vibrator.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(300L)
                        }
                    }
                } catch (_: Exception) {}

                restDoneBannerVisible = true
                delay(3000L)
                restDoneBannerVisible = false
                restTimer = RestTimerState()
            }
        }
    }

    var showAbandonConfirm by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    BackHandler {
        if (!showAbandonConfirm) showAbandonConfirm = true else onAbandon()
    }

    val previousHistory = remember { sessionRecordsStorage.loadAll() }
    var finishedSessionRecord by remember { mutableStateOf<SessionRecord?>(null) }

    if (finishedSessionRecord != null) {
        WorkoutSummaryScreen(
            record = finishedSessionRecord!!,
            previousHistory = previousHistory,
            onDone = { onFinish(finishedSessionRecord!!) }
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppBackground
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Top Bar / Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = workoutTitle,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextForeground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatClockTime(elapsedSeconds.toInt()),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TealHighlight
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showAbandonConfirm = true }) {
                            Text("Abandon", color = StateBad, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val durationMins = (elapsedSeconds / 60L).toInt().coerceAtLeast(1)
                                val finishedRecord = SessionRecord(
                                    dayKey = dayKey,
                                    routineId = routine?.id,
                                    name = workoutTitle,
                                    durationMinutes = durationMins,
                                    startedAt = startedAt,
                                    endedAt = System.currentTimeMillis(),
                                    exercises = exerciseStates.map { ex ->
                                        LoggedExerciseInSession(
                                            exerciseId = ex.exerciseId,
                                            exerciseName = ex.exerciseName,
                                            sets = ex.sets.map { s ->
                                                LoggedSetInSession(
                                                    weightKg = s.weightKg,
                                                    repsMin = s.repsMin,
                                                    repsMax = s.repsMax,
                                                    timeSeconds = s.timeSeconds,
                                                    difficulty = s.difficulty,
                                                    done = s.isDone
                                                )
                                            }
                                        )
                                    }
                                )
                                sessionRecordsStorage.add(finishedRecord)
                                loggedSessionsStorage.add(
                                    LoggedSession(dayKey = dayKey, name = workoutTitle, durationMinutes = durationMins)
                                )
                                DailyRepository.notifySessionRecorded(finishedRecord)
                                finishedSessionRecord = finishedRecord
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TealHighlight, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Finish", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Inline Abandon Confirm Bar
                if (showAbandonConfirm) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SecondaryFill)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Abandon workout?", color = TextForeground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showAbandonConfirm = false }) {
                                Text("No", color = TextMuted)
                            }
                            TextButton(onClick = onAbandon) {
                                Text("Yes, abandon", color = StateBad, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Scrollable Exercises List
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    exerciseStates.forEach { exState ->
                        val previousExercise = remember(exState.exerciseId) {
                            sessionRecordsStorage.latestForExercise(exState.exerciseId)
                        }

                        PlayerExerciseCard(
                            exerciseState = exState,
                            previousExercise = previousExercise,
                            restTimer = restTimer,
                            onSetDone = { set ->
                                set.isDone = true
                                if (exState.restSeconds > 0) {
                                    restTimer = startRestTimer(exState.restSeconds)
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(72.dp))
                }
            }

            // Floating Rest Timer Pill
            if (restTimer.isRunning || restDoneBannerVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (restDoneBannerVisible) TealHighlight else SecondaryFill)
                        .border(BorderStroke(1.dp, TealHighlight), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (restDoneBannerVisible) "Rest done!" else "Rest: ${formatClockTime(restTimer.remainingSeconds)}",
                        color = if (restDoneBannerVisible) Color.Black else TextForeground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerExerciseCard(
    exerciseState: PlayerExerciseState,
    previousExercise: LoggedExerciseInSession?,
    restTimer: RestTimerState,
    onSetDone: (PlayerSetState) -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Exercise Title + Rest Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = exerciseState.exerciseName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (exerciseState.restSeconds > 0) {
                    val restText = if (restTimer.isRunning) {
                        "Rest ${formatClockTime(restTimer.remainingSeconds)}"
                    } else {
                        "Rest: ${formatClockTime(exerciseState.restSeconds)}"
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SecondaryFill)
                            .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "⏱ $restText",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (restTimer.isRunning) TealHighlight else TextMuted
                        )
                    }
                }
            }

            // Sets Table Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SET", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
                Text("PREV", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("KG", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.9f), textAlign = TextAlign.Center)
                Text(
                    if (exerciseState.isTimed) "TIME" else "REPS",
                    fontSize = 10.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1.1f),
                    textAlign = TextAlign.Center
                )
                Text("DIFF", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
                Text("✓", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
            }

            // Sets Table Rows
            exerciseState.sets.forEachIndexed { setIdx, set ->
                val prevSummary = formatPreviousSetSummary(previousExercise, setIdx)

                var kgText by remember(set.weightKg) {
                    mutableStateOf(set.weightKg?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "")
                }
                var targetText by remember(set.repsMin, set.repsMax, set.timeSeconds, set.timeUnit) {
                    mutableStateOf(
                        if (exerciseState.isTimed) {
                            formatTimeEntry(set.timeSeconds, set.timeUnit)
                        } else {
                            formatRepsTargetEntry(set.repsMin, set.repsMax)
                        }
                    )
                }

                // Stopwatch hold timer for timed sets
                LaunchedEffect(set.isHolding) {
                    if (set.isHolding) {
                        while (set.isHolding) {
                            delay(1000L)
                            set.holdSeconds++
                            set.timeSeconds = set.holdSeconds
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Set #
                    Text(
                        text = "${setIdx + 1}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        modifier = Modifier.width(32.dp),
                        textAlign = TextAlign.Center
                    )

                    // PREV column
                    Text(
                        text = prevSummary,
                        fontSize = 11.sp,
                        color = TextFaint,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // KG Input
                    Box(
                        modifier = Modifier
                            .weight(0.9f)
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SecondaryFill)
                            .border(BorderStroke(1.dp, BorderInput), RoundedCornerShape(6.dp))
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicTextField(
                            value = kgText,
                            onValueChange = { input ->
                                val clean = input.filter { ch -> ch.isDigit() || ch == '.' }
                                kgText = clean
                                set.weightKg = clean.toDoubleOrNull()
                            },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                color = TextForeground,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            cursorBrush = SolidColor(TealHighlight),
                            decorationBox = { inner ->
                                if (kgText.isEmpty()) {
                                    Text("—", color = TextFaint, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                }
                                inner()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // REPS or TIME Input
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SecondaryFill)
                            .border(BorderStroke(1.dp, BorderInput), RoundedCornerShape(6.dp))
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (exerciseState.isTimed) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                BasicTextField(
                                    value = if (set.isHolding) formatClockTime(set.holdSeconds) else targetText,
                                    onValueChange = { input ->
                                        targetText = input
                                        set.timeSeconds = parseTimeEntry(input, set.timeUnit)
                                    },
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(
                                        color = TextForeground,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = if (set.timeUnit == "sec") KeyboardType.Number else KeyboardType.Text),
                                    cursorBrush = SolidColor(TealHighlight),
                                    decorationBox = { inner ->
                                        if (targetText.isEmpty() && !set.isHolding) {
                                            Text("00:30", color = TextFaint, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                        }
                                        inner()
                                    },
                                    modifier = Modifier.weight(1f)
                                )

                                // Small Hold button
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (set.isHolding) StateBad else TealHighlight.copy(alpha = 0.2f))
                                        .clickable { set.isHolding = !set.isHolding }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(if (set.isHolding) "Stop" else "Hold", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (set.isHolding) Color.White else TealHighlight)
                                }
                            }
                        } else {
                            BasicTextField(
                                value = targetText,
                                onValueChange = { input ->
                                    targetText = input
                                    val parsed = parseTargetField(input)
                                    set.repsMin = parsed?.first
                                    set.repsMax = parsed?.second
                                },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    color = TextForeground,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                cursorBrush = SolidColor(TealHighlight),
                                decorationBox = { inner ->
                                    if (targetText.isEmpty()) {
                                        Text("8-12", color = TextFaint, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    }
                                    inner()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // DIFF Chip: Empty -> Easy -> Med -> Hard -> Empty
                    val diffColor = when (set.difficulty?.lowercase()) {
                        "easy" -> StateLocked
                        "medium" -> JudgementWarn
                        "hard" -> StateBad
                        else -> TextMuted
                    }
                    val diffLabel = when (set.difficulty?.lowercase()) {
                        "easy" -> "EASY"
                        "medium" -> "MED"
                        "hard" -> "HARD"
                        else -> "—"
                    }
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (set.difficulty != null) diffColor.copy(alpha = 0.15f) else SecondaryFill)
                            .border(BorderStroke(1.dp, if (set.difficulty != null) diffColor else BorderDefault), RoundedCornerShape(6.dp))
                            .clickable {
                                set.difficulty = cycleDifficulty(set.difficulty)
                            }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = diffLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = diffColor, textAlign = TextAlign.Center)
                    }

                    // Done ✓ checkbox
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (set.isDone) TealHighlight else SecondaryFill)
                                .border(BorderStroke(1.dp, if (set.isDone) TealHighlight else BorderDefault), RoundedCornerShape(6.dp))
                                .clickable {
                                    if (!set.isDone) {
                                        onSetDone(set)
                                    } else {
                                        set.isDone = false
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✓", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (set.isDone) Color.Black else TextMuted.copy(alpha = 0.35f))
                        }
                    }
                }
            }
        }
    }
}
