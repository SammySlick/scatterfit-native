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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.scatterbrain.scatterfit.ui.theme.TealHighlight
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import java.util.UUID

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
    var exerciseTargetIndex by remember { mutableIntStateOf(-1) } // -1 = append new

    var editingSetExerciseIndex by remember { mutableIntStateOf(-1) }
    var editingSetIndex by remember { mutableIntStateOf(-1) }

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
                                tint = Color(0xFFE57373)
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
                        color = Color(0xFFE57373),
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

                // Exercises List
                exercises.forEachIndexed { exIndex, ex ->
                    ExerciseRowCard(
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
                        onAddSet = {
                            val lastSet = ex.sets.lastOrNull()
                            val newSet = PlannedSet(
                                type = "normal",
                                weightKg = lastSet?.weightKg,
                                reps = lastSet?.reps
                            )
                            exercises[exIndex] = ex.copy(sets = ex.sets + newSet)
                        },
                        onSetClick = { setIndex ->
                            editingSetExerciseIndex = exIndex
                            editingSetIndex = setIndex
                        },
                        onSetLongClick = { setIndex ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val updatedSets = ex.sets.toMutableList()
                            if (setIndex in updatedSets.indices) {
                                updatedSets.removeAt(setIndex)
                                exercises[exIndex] = ex.copy(sets = updatedSets)
                            }
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
                    sets = listOf(PlannedSet(type = "normal", weightKg = null, reps = null))
                )
                if (exerciseTargetIndex >= 0 && exerciseTargetIndex in exercises.indices) {
                    exercises[exerciseTargetIndex] = newEx
                } else {
                    exercises.add(newEx)
                }
                showExercisePicker = false
            },
            onDismissRequest = { showExercisePicker = false }
        )
    }

    // Edit Set Dialog
    if (editingSetExerciseIndex in exercises.indices && editingSetIndex in exercises[editingSetExerciseIndex].sets.indices) {
        val targetEx = exercises[editingSetExerciseIndex]
        val targetSet = targetEx.sets[editingSetIndex]

        EditSetDialog(
            set = targetSet,
            setNumber = editingSetIndex + 1,
            onDismiss = {
                editingSetExerciseIndex = -1
                editingSetIndex = -1
            },
            onSave = { updatedSet ->
                val updatedSets = targetEx.sets.toMutableList()
                updatedSets[editingSetIndex] = updatedSet
                exercises[editingSetExerciseIndex] = targetEx.copy(sets = updatedSets)
                editingSetExerciseIndex = -1
                editingSetIndex = -1
            },
            onDelete = {
                val updatedSets = targetEx.sets.toMutableList()
                updatedSets.removeAt(editingSetIndex)
                exercises[editingSetExerciseIndex] = targetEx.copy(sets = updatedSets)
                editingSetExerciseIndex = -1
                editingSetIndex = -1
            }
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373), contentColor = Color.Black)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExerciseRowCard(
    exercise: RoutineExercise,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDeleteExercise: () -> Unit,
    onAddSet: () -> Unit,
    onSetClick: (Int) -> Unit,
    onSetLongClick: (Int) -> Unit,
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Exercise Header (Title, Reorder Arrows, Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = exercise.exerciseName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

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

            // Sets Chips Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                items(exercise.sets.size) { setIndex ->
                    val set = exercise.sets[setIndex]
                    val typePrefix = when (set.type) {
                        "warmup" -> "W: "
                        "failure" -> "F: "
                        else -> ""
                    }
                    val weightStr = set.weightKg?.let { if (it % 1.0 == 0.0) "${it.toInt()}kg" else "${it}kg" } ?: "BW"
                    val repsStr = set.reps?.let { "${it}r" } ?: "reps"
                    val label = "$typePrefix$repsStr @ $weightStr"

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SecondaryFill)
                            .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(8.dp))
                            .combinedClickable(
                                onClick = { onSetClick(setIndex) },
                                onLongClick = { onSetLongClick(setIndex) }
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = when (set.type) {
                                "warmup" -> AccentTeal
                                "failure" -> Color(0xFFE57373)
                                else -> TextForeground
                            }
                        )
                    }
                }

                item {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Transparent)
                            .border(BorderStroke(1.dp, TealHighlight.copy(alpha = 0.5f)), RoundedCornerShape(8.dp))
                            .clickable(onClick = onAddSet)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("+ Set", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealHighlight)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditSetDialog(
    set: PlannedSet,
    setNumber: Int,
    onDismiss: () -> Unit,
    onSave: (PlannedSet) -> Unit,
    onDelete: () -> Unit,
) {
    var weightInput by remember { mutableStateOf(set.weightKg?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: "") }
    var repsInput by remember { mutableStateOf(set.reps?.toString() ?: "") }
    var currentType by remember { mutableStateOf(set.type) } // normal -> warmup -> failure

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkCardSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Set $setNumber", color = TextForeground, fontWeight = FontWeight.Bold)
                TextButton(
                    onClick = {
                        // Cycle type: normal -> warmup -> failure -> normal
                        currentType = when (currentType) {
                            "normal" -> "warmup"
                            "warmup" -> "failure"
                            else -> "normal"
                        }
                    }
                ) {
                    Text(
                        text = currentType.uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = when (currentType) {
                            "warmup" -> AccentTeal
                            "failure" -> Color(0xFFE57373)
                            else -> TextMuted
                        }
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = { weightInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Weight (kg, optional)", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextForeground,
                        unfocusedTextColor = TextForeground,
                        focusedBorderColor = TealHighlight,
                        unfocusedBorderColor = BorderInput
                    )
                )

                OutlinedTextField(
                    value = repsInput,
                    onValueChange = { repsInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Reps (optional)", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextForeground,
                        unfocusedTextColor = TextForeground,
                        focusedBorderColor = TealHighlight,
                        unfocusedBorderColor = BorderInput
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val w = weightInput.toDoubleOrNull()
                    val r = repsInput.toIntOrNull()
                    onSave(PlannedSet(type = currentType, weightKg = w, reps = r))
                },
                colors = ButtonDefaults.buttonColors(containerColor = TealHighlight, contentColor = Color.Black)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text("Delete Set", color = Color(0xFFE57373))
            }
        }
    )
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
