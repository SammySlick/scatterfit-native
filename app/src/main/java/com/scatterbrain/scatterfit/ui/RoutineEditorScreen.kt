package com.scatterbrain.scatterfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.ui.theme.AccentTeal
import com.scatterbrain.scatterfit.ui.theme.AppBackground
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.BorderInput
import com.scatterbrain.scatterfit.ui.theme.DarkCardSurface
import com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.StateBad
import com.scatterbrain.scatterfit.ui.theme.TealHighlight
import com.scatterbrain.scatterfit.ui.theme.TextFaint
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import java.util.UUID
import kotlin.math.roundToInt

fun calculateRoutineEstimateMinutes(exercises: List<RoutineExercise>): Int {
    if (exercises.isEmpty()) return 0
    var totalSeconds = 0
    for (ex in exercises) {
        val sets = ex.sets.size
        if (sets == 0) continue
        val rest = if (ex.restSeconds > 0) ex.restSeconds else 60
        val setDuration = if (ex.isTimed) {
            ex.sets.mapNotNull { it.timeSecondsMin }.average().takeIf { !it.isNaN() }?.toInt() ?: 40
        } else {
            40
        }
        totalSeconds += sets * setDuration + (sets - 1).coerceAtLeast(0) * rest
    }
    val totalMin = totalSeconds / 60.0
    return (kotlin.math.round(totalMin / 5.0) * 5).toInt().coerceAtLeast(5)
}

fun calculateSetsByMuscle(
    exercises: List<RoutineExercise>,
    lookupMuscle: (String) -> String = { "other" },
): Map<String, Int> {
    val counts = mutableMapOf<String, Int>()
    for (ex in exercises) {
        val muscle = lookupMuscle(ex.exerciseId).lowercase()
        counts[muscle] = (counts[muscle] ?: 0) + ex.sets.size
    }
    return counts
}

private val REST_DURATIONS = listOf(0, 30, 45, 60, 90, 120, 180)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineEditorScreen(
    initialRoutine: Routine? = null,
    onSaveRoutine: (Routine) -> Unit,
    onDeleteRoutine: ((String) -> Unit)? = null,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackClick)

    val context = LocalContext.current
    val library = remember { ExerciseLibrary.load(context) }

    var routineName by remember { mutableStateOf(initialRoutine?.name ?: "New routine") }
    val exercises = remember {
        mutableStateListOf<RoutineExercise>().apply {
            if (initialRoutine != null) {
                addAll(initialRoutine.exercises)
            }
        }
    }

    var showExercisePicker by remember { mutableStateOf(false) }
    var exerciseTargetIndex by remember { mutableIntStateOf(-1) }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()

    // Tactile Save Button State
    val saveInteractionSource = remember { MutableInteractionSource() }
    val isSavePressed by saveInteractionSource.collectIsPressedAsState()
    val saveAlpha by animateFloatAsState(
        targetValue = if (isSavePressed) 0.70f else 1f,
        animationSpec = if (isSavePressed) tween(80) else spring(stiffness = Spring.StiffnessHigh),
        label = "saveAlpha"
    )
    val saveScale by animateFloatAsState(
        targetValue = if (isSavePressed) 0.96f else 1f,
        animationSpec = if (isSavePressed) tween(80) else spring(stiffness = Spring.StiffnessHigh),
        label = "saveScale"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextForeground
                        )
                    }
                    Text(
                        text = if (initialRoutine == null) "New Routine" else "Edit Routine",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextForeground
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (initialRoutine != null && onDeleteRoutine != null) {
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete routine",
                                tint = StateBad
                            )
                        }
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val cleanName = routineName.trim()
                            if (cleanName.isBlank()) {
                                validationError = "Routine needs a name"
                                return@Button
                            }
                            if (exercises.isEmpty() || exercises.any { it.sets.isEmpty() }) {
                                validationError = "Routine needs >= 1 exercise with >= 1 set"
                                return@Button
                            }

                            val routineToSave = Routine(
                                id = initialRoutine?.id ?: UUID.randomUUID().toString(),
                                name = cleanName,
                                exercises = exercises.toList(),
                                createdAt = initialRoutine?.createdAt ?: System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            )
                            onSaveRoutine(routineToSave)
                        },
                        interactionSource = saveInteractionSource,
                        modifier = Modifier
                            .graphicsLayer {
                                this.alpha = saveAlpha
                                this.scaleX = saveScale
                                this.scaleY = saveScale
                            },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TealHighlight,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        color = StateBad,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Routine Name Field
                OutlinedTextField(
                    value = routineName,
                    onValueChange = {
                        routineName = it
                        validationError = null
                    },
                    label = { Text("Routine Name", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextForeground,
                        unfocusedTextColor = TextForeground,
                        focusedBorderColor = TealHighlight,
                        unfocusedBorderColor = BorderInput,
                        cursorColor = TealHighlight
                    )
                )

                // Exercises List (Hevy-style Cards with in-place editing)
                exercises.forEachIndexed { exIndex, ex ->
                    HevyExerciseCard(
                        exercise = ex,
                        canMoveUp = exIndex > 0,
                        canMoveDown = exIndex < exercises.size - 1,
                        onMoveUp = {
                            val item = exercises.removeAt(exIndex)
                            exercises.add(exIndex - 1, item)
                        },
                        onMoveDown = {
                            val item = exercises.removeAt(exIndex)
                            exercises.add(exIndex + 1, item)
                        },
                        onDeleteExercise = {
                            exercises.removeAt(exIndex)
                        },
                        onUpdateExercise = { updated ->
                            exercises[exIndex] = updated
                        }
                    )
                }

                // "+ Add Exercise" Button
                TactileButton(
                    onClick = {
                        exerciseTargetIndex = -1
                        showExercisePicker = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Add Exercise", color = TealHighlight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                // PART 3: Routine Summary Block (Exercises n · Total Sets n · Est X min + Muscle Bars)
                if (exercises.isNotEmpty()) {
                    RoutineSummaryCard(
                        exercises = exercises,
                        lookupMuscle = { id -> library.find { it.id == id }?.muscle ?: "other" }
                    )
                }
            }
        }
    }

    // Exercise Picker Sheet
    if (showExercisePicker) {
        ExercisePickerSheet(
            library = library,
            onExerciseSelected = { selectedRef ->
                val newEx = RoutineExercise(
                    exerciseId = selectedRef.id,
                    exerciseName = selectedRef.name,
                    sets = listOf(PlannedSet(type = "normal", weightKg = null, repsMin = null, repsMax = null))
                )
                if (exerciseTargetIndex in exercises.indices) {
                    exercises[exerciseTargetIndex] = newEx
                } else {
                    exercises.add(newEx)
                }
                showExercisePicker = false
            },
            onDismissRequest = { showExercisePicker = false }
        )
    }

    // Confirm Delete Routine Dialog
    if (showDeleteConfirmDialog && initialRoutine != null && onDeleteRoutine != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = DarkCardSurface,
            title = { Text("Delete Routine", color = TextForeground, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${initialRoutine.name}'?", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDeleteRoutine(initialRoutine.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StateBad, contentColor = Color.Black)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}

/**
 * Hevy-style Exercise Card with in-place text editing, sets table (SET | KG | REPS/TIME),
 * inline rest, note, tempo, and time mode toggle chips.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HevyExerciseCard(
    exercise: RoutineExercise,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDeleteExercise: () -> Unit,
    onUpdateExercise: (RoutineExercise) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isEditingName by remember { mutableStateOf(false) }
    var nameInput by remember(exercise.displayName) { mutableStateOf(exercise.displayName) }

    var isEditingNote by remember { mutableStateOf(false) }
    var noteInput by remember(exercise.note) { mutableStateOf(exercise.note ?: "") }

    var isEditingTempo by remember { mutableStateOf(false) }
    val initialTempoParts = remember(exercise.tempo) { parseTempo(exercise.tempo) }
    var tempoE by remember(exercise.tempo) { mutableStateOf(initialTempoParts[0]) }
    var tempoP1 by remember(exercise.tempo) { mutableStateOf(initialTempoParts[1]) }
    var tempoC by remember(exercise.tempo) { mutableStateOf(initialTempoParts[2]) }
    var tempoP2 by remember(exercise.tempo) { mutableStateOf(initialTempoParts[3]) }

    val haptic = LocalHapticFeedback.current

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
            // 1. HEADER: Name (in-place editable) + Reorder arrows + Delete '×'
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isEditingName) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { nameInput = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextForeground,
                                unfocusedTextColor = TextForeground,
                                focusedBorderColor = TealHighlight,
                                unfocusedBorderColor = BorderInput
                            )
                        )
                        IconButton(
                            onClick = {
                                isEditingName = false
                                val trimmed = nameInput.trim()
                                if (trimmed.isNotBlank()) {
                                    onUpdateExercise(exercise.copy(customName = trimmed))
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("✓", color = TealHighlight, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                } else {
                    Text(
                        text = exercise.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextForeground,
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                nameInput = exercise.displayName
                                isEditingName = true
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canMoveUp) {
                        IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                            Text("▲", fontSize = 12.sp, color = TextMuted)
                        }
                    }
                    if (canMoveDown) {
                        IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                            Text("▼", fontSize = 12.sp, color = TextMuted)
                        }
                    }
                    IconButton(onClick = onDeleteExercise, modifier = Modifier.size(28.dp)) {
                        Text("✕", fontSize = 14.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // 2. SUB-ROW: [Rest] + [Note] + [Tempo] + [Time] chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rest chip (tap cycles 0 -> 30 -> 45 -> 60 -> 90 -> 120 -> 180 -> 0)
                val restLabel = if (exercise.restSeconds == 0) "Rest: OFF" else "Rest: ${exercise.restSeconds}s"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SecondaryFill)
                        .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(8.dp))
                        .clickable {
                            val currentIndex = REST_DURATIONS.indexOf(exercise.restSeconds)
                            val nextIndex = if (currentIndex < 0 || currentIndex == REST_DURATIONS.size - 1) 0 else currentIndex + 1
                            onUpdateExercise(exercise.copy(restSeconds = REST_DURATIONS[nextIndex]))
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "⏱ $restLabel",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (exercise.restSeconds > 0) TealHighlight else TextMuted
                    )
                }

                // Note chip
                val hasNote = !exercise.note.isNullOrBlank()
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SecondaryFill)
                        .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(8.dp))
                        .clickable { isEditingNote = !isEditingNote }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (hasNote) "Note" else "+ Note",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (hasNote) TealHighlight else TextMuted
                    )
                }

                // Tempo chip
                val hasTempo = !exercise.tempo.isNullOrBlank()
                val tempoChipLabel = if (hasTempo) "Tempo: ${exercise.tempo}" else "+ Tempo"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SecondaryFill)
                        .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(8.dp))
                        .clickable { isEditingTempo = !isEditingTempo }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = tempoChipLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (hasTempo) TealHighlight else TextMuted
                    )
                }

                // Time mode toggle chip
                val isTimed = exercise.isTimed
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isTimed) AccentTeal.copy(alpha = 0.2f) else SecondaryFill)
                        .border(BorderStroke(1.dp, if (isTimed) AccentTeal else BorderDefault), RoundedCornerShape(8.dp))
                        .clickable {
                            onUpdateExercise(exercise.copy(isTimed = !exercise.isTimed))
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isTimed) "Time: ON" else "Time",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isTimed) TealHighlight else TextMuted
                    )
                }

                // Time unit toggle chip (shown when isTimed is true)
                if (isTimed) {
                    val unitLabel = exercise.timeUnit.ifBlank { "sec" }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SecondaryFill)
                            .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(8.dp))
                            .clickable {
                                val nextUnit = when (exercise.timeUnit.lowercase()) {
                                    "sec" -> "min"
                                    "min" -> "hour"
                                    else -> "sec"
                                }
                                onUpdateExercise(exercise.copy(timeUnit = nextUnit))
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = unitLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TealHighlight
                        )
                    }
                }
            }

            // Inline multi-line note editor (if toggled open)
            if (isEditingNote) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        placeholder = { Text("Exercise note (e.g. 2-0-2-1, 10-12 reps)", color = TextMuted.copy(alpha = 0.5f)) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextForeground,
                            unfocusedTextColor = TextForeground,
                            focusedBorderColor = TealHighlight,
                            unfocusedBorderColor = BorderInput
                        )
                    )
                    IconButton(
                        onClick = {
                            isEditingNote = false
                            onUpdateExercise(exercise.copy(note = noteInput.trim().ifEmpty { null }))
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("✓", color = TealHighlight, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            // Inline Tempo editor (if toggled open): 4 small numeric fields labelled E - P - C - P
            if (isEditingTempo) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SecondaryFill)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Tempo:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)

                    listOf(
                        Triple("E", tempoE) { v: String -> tempoE = v },
                        Triple("P", tempoP1) { v: String -> tempoP1 = v },
                        Triple("C", tempoC) { v: String -> tempoC = v },
                        Triple("P", tempoP2) { v: String -> tempoP2 = v }
                    ).forEach { (lbl, currVal, updateVal) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(lbl, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                            Box(
                                modifier = Modifier
                                    .size(width = 34.dp, height = 30.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(DarkCardSurface)
                                    .border(BorderStroke(1.dp, BorderInput), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicTextField(
                                    value = currVal,
                                    onValueChange = { input ->
                                        updateVal(input.filter { it.isDigit() }.take(2))
                                    },
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(
                                        color = TextForeground,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    cursorBrush = SolidColor(TealHighlight),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            isEditingTempo = false
                            val formatted = formatTempo(tempoE, tempoP1, tempoC, tempoP2)
                            onUpdateExercise(exercise.copy(tempo = formatted))
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text("✓", color = TealHighlight, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            // 3. NOTES & TEMPO DISPLAY: Muted clipboard lines if set
            val displayLines = buildList {
                if (!exercise.note.isNullOrBlank()) add(exercise.note)
                if (!exercise.tempo.isNullOrBlank() && !isEditingTempo) add("Tempo: ${exercise.tempo}")
            }
            if (displayLines.isNotEmpty() && !isEditingNote) {
                Text(
                    text = displayLines.joinToString(" · "),
                    fontSize = 11.sp,
                    color = TextMuted,
                    modifier = Modifier.clickable {
                        noteInput = exercise.note ?: ""
                        isEditingNote = true
                    }
                )
            }

            // 4. SETS TABLE: Header row (SET | KG | REPS/TIME) - NO DIFFICULTY, NO DONE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SET", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                Text("KG", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text(
                    if (exercise.isTimed) "TIME (${exercise.timeUnit})" else "REPS",
                    fontSize = 10.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1.5f),
                    textAlign = TextAlign.Center
                )
            }

            // Sets rows (SET | KG | REPS (min/max) or TIME (min/max))
            exercise.sets.forEachIndexed { setIndex, set ->
                var kgText by remember(set.weightKg) {
                    mutableStateOf(set.weightKg?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "")
                }
                var repsMinText by remember(set.repsMin) {
                    mutableStateOf(set.repsMin?.toString() ?: "")
                }
                var repsMaxText by remember(set.repsMax) {
                    mutableStateOf(set.repsMax?.toString() ?: "")
                }
                var timeMinText by remember(set.timeSecondsMin) {
                    mutableStateOf(set.timeSecondsMin?.toString() ?: "")
                }
                var timeMaxText by remember(set.timeSecondsMax) {
                    mutableStateOf(set.timeSecondsMax?.toString() ?: "")
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val updated = exercise.sets.toMutableList()
                                updated.removeAt(setIndex)
                                onUpdateExercise(exercise.copy(sets = updated))
                            }
                        )
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Set # column (tap cycles normal / warmup / failure)
                    val setBadge = when (set.type) {
                        "warmup" -> "W"
                        "failure" -> "F"
                        else -> "${setIndex + 1}"
                    }
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .clickable {
                                val nextType = when (set.type) {
                                    "normal" -> "warmup"
                                    "warmup" -> "failure"
                                    else -> "normal"
                                }
                                val updated = exercise.sets.toMutableList()
                                updated[setIndex] = set.copy(type = nextType)
                                onUpdateExercise(exercise.copy(sets = updated))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = setBadge,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (set.type) {
                                "warmup" -> AccentTeal
                                "failure" -> StateBad
                                else -> TextMuted
                            }
                        )
                    }

                    // KG Input
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SecondaryFill)
                            .border(BorderStroke(1.dp, BorderInput), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicTextField(
                            value = kgText,
                            onValueChange = { input ->
                                val clean = input.filter { ch -> ch.isDigit() || ch == '.' }
                                kgText = clean
                                val updated = exercise.sets.toMutableList()
                                updated[setIndex] = set.copy(weightKg = clean.toDoubleOrNull())
                                onUpdateExercise(exercise.copy(sets = updated))
                            },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                color = TextForeground,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            cursorBrush = SolidColor(TealHighlight),
                            decorationBox = { innerTextField ->
                                if (kgText.isEmpty()) {
                                    Text(
                                        text = "No Weight",
                                        color = TextFaint,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                innerTextField()
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // REPS or TIME Range Inputs (min required, max optional)
                    Box(
                        modifier = Modifier
                            .weight(1.5f)
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(SecondaryFill)
                            .border(BorderStroke(1.dp, BorderInput), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!exercise.isTimed) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                BasicTextField(
                                    value = repsMinText,
                                    onValueChange = { input ->
                                        val clean = input.filter { it.isDigit() }
                                        repsMinText = clean
                                        val updated = exercise.sets.toMutableList()
                                        updated[setIndex] = set.copy(repsMin = clean.toIntOrNull())
                                        onUpdateExercise(exercise.copy(sets = updated))
                                    },
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(
                                        color = TextForeground,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    cursorBrush = SolidColor(TealHighlight),
                                    decorationBox = { innerTextField ->
                                        if (repsMinText.isEmpty()) {
                                            Text("Min", color = TextFaint, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                        }
                                        innerTextField()
                                    },
                                    modifier = Modifier.width(36.dp)
                                )
                                Text("–", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 2.dp))
                                BasicTextField(
                                    value = repsMaxText,
                                    onValueChange = { input ->
                                        val clean = input.filter { it.isDigit() }
                                        repsMaxText = clean
                                        val updated = exercise.sets.toMutableList()
                                        updated[setIndex] = set.copy(repsMax = clean.toIntOrNull())
                                        onUpdateExercise(exercise.copy(sets = updated))
                                    },
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(
                                        color = if (repsMaxText.isNotBlank()) TextForeground else TextMuted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    cursorBrush = SolidColor(TealHighlight),
                                    decorationBox = { innerTextField ->
                                        if (repsMaxText.isEmpty()) {
                                            Text("Max", color = TextFaint, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                        }
                                        innerTextField()
                                    },
                                    modifier = Modifier.width(36.dp)
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                BasicTextField(
                                    value = timeMinText,
                                    onValueChange = { input ->
                                        val clean = input.filter { it.isDigit() || it == '.' }
                                        timeMinText = clean
                                        val num = clean.toDoubleOrNull()
                                        val seconds = when (exercise.timeUnit.lowercase()) {
                                            "min" -> num?.let { (it * 60.0).roundToInt() }
                                            "hour" -> num?.let { (it * 3600.0).roundToInt() }
                                            else -> num?.toInt()
                                        }
                                        val updated = exercise.sets.toMutableList()
                                        updated[setIndex] = set.copy(timeSecondsMin = seconds)
                                        onUpdateExercise(exercise.copy(sets = updated))
                                    },
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(
                                        color = TextForeground,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    cursorBrush = SolidColor(TealHighlight),
                                    decorationBox = { innerTextField ->
                                        if (timeMinText.isEmpty()) {
                                            Text("Min", color = TextFaint, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                        }
                                        innerTextField()
                                    },
                                    modifier = Modifier.width(36.dp)
                                )
                                Text("–", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 2.dp))
                                BasicTextField(
                                    value = timeMaxText,
                                    onValueChange = { input ->
                                        val clean = input.filter { it.isDigit() || it == '.' }
                                        timeMaxText = clean
                                        val num = clean.toDoubleOrNull()
                                        val seconds = when (exercise.timeUnit.lowercase()) {
                                            "min" -> num?.let { (it * 60.0).roundToInt() }
                                            "hour" -> num?.let { (it * 3600.0).roundToInt() }
                                            else -> num?.toInt()
                                        }
                                        val updated = exercise.sets.toMutableList()
                                        updated[setIndex] = set.copy(timeSecondsMax = seconds)
                                        onUpdateExercise(exercise.copy(sets = updated))
                                    },
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(
                                        color = if (timeMaxText.isNotBlank()) TextForeground else TextMuted,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    cursorBrush = SolidColor(TealHighlight),
                                    decorationBox = { innerTextField ->
                                        if (timeMaxText.isEmpty()) {
                                            Text("Max", color = TextFaint, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                        }
                                        innerTextField()
                                    },
                                    modifier = Modifier.width(36.dp)
                                )
                                Text(timeUnitSuffix(exercise.timeUnit), color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 2.dp))
                            }
                        }
                    }
                }
            }

            // 5. ROW ACTIONS: "+ Add Set" full-width button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SecondaryFill)
                    .clickable {
                        val lastSet = exercise.sets.lastOrNull()
                        val newSet = PlannedSet(
                            type = "normal",
                            weightKg = lastSet?.weightKg,
                            repsMin = lastSet?.repsMin,
                            repsMax = lastSet?.repsMax,
                            timeSecondsMin = lastSet?.timeSecondsMin,
                            timeSecondsMax = lastSet?.timeSecondsMax
                        )
                        onUpdateExercise(exercise.copy(sets = exercise.sets + newSet))
                    }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("+ Add Set", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealHighlight)
            }
        }
    }
}

/**
 * Routine Summary Card showing Exercises count, Total Sets, Estimated Duration, and Muscle Bars.
 */
@Composable
fun RoutineSummaryCard(
    exercises: List<RoutineExercise>,
    lookupMuscle: (String) -> String,
    modifier: Modifier = Modifier,
) {
    val totalSets = exercises.sumOf { it.sets.size }
    val estMin = calculateRoutineEstimateMinutes(exercises)
    val setsByMuscle = calculateSetsByMuscle(exercises, lookupMuscle)
    val maxSetsInMuscle = setsByMuscle.values.maxOrNull() ?: 1

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
            // Header summary row
            Text(
                text = "Exercises ${exercises.size} · Total Sets $totalSets · Est. ${estMin}min",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextForeground
            )

            // Horizontal Muscle Bars
            if (setsByMuscle.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    setsByMuscle.entries.sortedByDescending { it.value }.forEach { (muscle, count) ->
                        val fraction = (count.toFloat() / maxSetsInMuscle.toFloat()).coerceIn(0.05f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = muscle.uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "$count ${if (count == 1) "set" else "sets"}",
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape)
                                    .background(SecondaryFill)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction = fraction)
                                        .height(6.dp)
                                        .clip(CircleShape)
                                        .background(TealHighlight)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePickerSheet(
    library: List<ExerciseRef>,
    onExerciseSelected: (ExerciseRef) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var customNameInput by remember { mutableStateOf("") }

    val filtered = remember(library, searchQuery) {
        if (searchQuery.isBlank()) {
            library
        } else {
            library.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
        }
    }

    val muscleGroups = remember(filtered) {
        filtered.groupBy { it.muscle.lowercase() }
    }

    val orderedMuscles = listOf("chest", "back", "shoulders", "biceps", "triceps", "legs", "glutes", "core", "fullbody", "other")

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = DarkCardSurface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Select Exercise", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextForeground)

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search exercises...", color = TextMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextForeground,
                    unfocusedTextColor = TextForeground,
                    focusedBorderColor = TealHighlight,
                    unfocusedBorderColor = BorderInput
                )
            )

            // Custom Exercise Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = customNameInput,
                    onValueChange = { customNameInput = it },
                    placeholder = { Text("+ Custom exercise name", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextForeground,
                        unfocusedTextColor = TextForeground,
                        focusedBorderColor = TealHighlight,
                        unfocusedBorderColor = BorderInput
                    )
                )

                Button(
                    onClick = {
                        if (customNameInput.isNotBlank()) {
                            val resolved = ExerciseLibrary.resolveOrCreate(customNameInput, library)
                            onExerciseSelected(resolved)
                        }
                    },
                    enabled = customNameInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = TealHighlight, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Add", fontWeight = FontWeight.Bold)
                }
            }

            // Grouped Library List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                orderedMuscles.forEach { muscle ->
                    val exercisesInMuscle = muscleGroups[muscle] ?: emptyList()
                    if (exercisesInMuscle.isNotEmpty()) {
                        item {
                            Text(
                                text = muscle.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted,
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                            )
                        }

                        items(exercisesInMuscle, key = { it.id }) { ex ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SecondaryFill)
                                    .clickable { onExerciseSelected(ex) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = ex.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextForeground
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TactileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.70f else 1f,
        animationSpec = if (isPressed) tween(80) else spring(stiffness = Spring.StiffnessHigh),
        label = "tactileAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = if (isPressed) tween(80) else spring(stiffness = Spring.StiffnessHigh),
        label = "tactileScale"
    )
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = modifier
            .graphicsLayer {
                this.alpha = alpha
                this.scaleX = scale
                this.scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = BorderStroke(1.dp, BorderDefault)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RoutineEditorScreenPreview() {
    ScatterFitTheme {
        RoutineEditorScreen(
            onSaveRoutine = {},
            onBackClick = {}
        )
    }
}
