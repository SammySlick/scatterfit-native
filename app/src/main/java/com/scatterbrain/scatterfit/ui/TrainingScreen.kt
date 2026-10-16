package com.scatterbrain.scatterfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.ui.theme.AppBackground
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.DarkCardSurface
import com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.TealHighlight
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted

@Composable
fun TrainingScreen(
    storage: RoutineStorage,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackClick)

    val context = LocalContext.current
    var routines by remember { mutableStateOf(storage.loadRoutines()) }
    var viewingRoutine by remember { mutableStateOf<Routine?>(null) }
    var editingRoutine by remember { mutableStateOf<Routine?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    if (editingRoutine != null || isCreatingNew) {
        RoutineEditorScreen(
            initialRoutine = editingRoutine,
            onSaveRoutine = { saved ->
                storage.upsert(saved)
                routines = storage.loadRoutines()
                viewingRoutine = saved
                editingRoutine = null
                isCreatingNew = false
            },
            onDeleteRoutine = { id ->
                storage.delete(id)
                routines = storage.loadRoutines()
                viewingRoutine = null
                editingRoutine = null
                isCreatingNew = false
            },
            onBackClick = {
                editingRoutine = null
                isCreatingNew = false
            }
        )
        return
    }

    if (viewingRoutine != null) {
        RoutineOverviewScreen(
            routine = viewingRoutine!!,
            lookupMuscle = { id -> ExerciseLibrary.lookup(id, context)?.muscle ?: "other" },
            onEditClick = { editingRoutine = viewingRoutine },
            onBackClick = { viewingRoutine = null }
        )
        return
    }

    val scrollState = rememberScrollState()

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
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextForeground
                    )
                }
                Text(
                    text = "Routines",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Empty state card
                if (routines.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                        border = BorderStroke(1.dp, BorderDefault)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Training Programs",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextForeground
                            )
                            Text(
                                text = "Write a program once. Replay it any day.",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                }

                // Routines List
                routines.forEach { routine ->
                    RoutineItemCard(
                        routine = routine,
                        onCardClick = { viewingRoutine = routine },
                        onEditClick = {
                            viewingRoutine = routine
                            editingRoutine = routine
                        }
                    )
                }

                // "+ New routine" Card
                TactileCardButton(
                    onClick = { isCreatingNew = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "+ New routine",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealHighlight
                    )
                }
            }
        }
    }
}

/**
 * Read-only overview screen shown when tapping a routine card.
 * Header: routine name, summary line, muscle bars, exercise cards with formatted sets.
 * Top-right: [Edit] button to edit the routine.
 */
@Composable
fun RoutineOverviewScreen(
    routine: Routine,
    lookupMuscle: (String) -> String,
    onEditClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackClick)
    val scrollState = rememberScrollState()

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
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextForeground
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SecondaryFill)
                        .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(8.dp))
                        .clickable(onClick = onEditClick)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Edit",
                        color = TealHighlight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
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
                // Routine Name Header
                Text(
                    text = routine.name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground
                )

                // Routine Summary Card (Exercises · Sets · Est. Min + Muscle Bars)
                if (routine.exercises.isNotEmpty()) {
                    RoutineSummaryCard(
                        exercises = routine.exercises,
                        lookupMuscle = lookupMuscle
                    )
                }

                // Exercises Read-only Cards
                routine.exercises.forEach { ex ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                        border = BorderStroke(1.dp, BorderDefault)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = ex.displayName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextForeground
                            )

                            // Subtitle: Rest / Tempo
                            val subtitleParts = buildList {
                                if (ex.restSeconds > 0) add("Rest: ${ex.restSeconds}s")
                                if (!ex.tempo.isNullOrBlank()) add("Tempo: ${ex.tempo}")
                            }
                            if (subtitleParts.isNotEmpty()) {
                                Text(
                                    text = subtitleParts.joinToString(" · "),
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }

                            // Exercise-level note
                            if (!ex.note.isNullOrBlank()) {
                                Text(
                                    text = ex.note,
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Sets summary lines
                            val setLines = formatExerciseSetsSummary(ex)
                            setLines.forEach { line ->
                                Text(
                                    text = line,
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
private fun RoutineItemCard(
    routine: Routine,
    onCardClick: () -> Unit,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exerciseCountText = when (val c = routine.exercises.size) {
        1 -> "1 exercise"
        else -> "$c exercises"
    }

    TactileCardButton(
        onClick = onCardClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = routine.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = exerciseCountText,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SecondaryFill)
                    .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(8.dp))
                    .clickable(onClick = onEditClick)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "EDIT",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TealHighlight
                )
            }
        }
    }
}

@Composable
private fun TactileCardButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.70f else 1f,
        animationSpec = if (isPressed) tween(80) else spring(stiffness = Spring.StiffnessHigh),
        label = "tcbAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = if (isPressed) tween(80) else spring(stiffness = Spring.StiffnessHigh),
        label = "tcbScale"
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
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun TrainingScreenPreview() {
    ScatterFitTheme {
        val context = LocalContext.current
        val storage = remember { RoutineStorage(context) }
        TrainingScreen(storage = storage, onBackClick = {})
    }
}
