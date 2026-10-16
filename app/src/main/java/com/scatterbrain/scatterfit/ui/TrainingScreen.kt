package com.scatterbrain.scatterfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

    var routines by remember { mutableStateOf(storage.loadRoutines()) }
    var editingRoutine by remember { mutableStateOf<Routine?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    if (editingRoutine != null || isCreatingNew) {
        RoutineEditorScreen(
            initialRoutine = editingRoutine,
            onSaveRoutine = { saved ->
                storage.upsert(saved)
                routines = storage.loadRoutines()
                editingRoutine = null
                isCreatingNew = false
            },
            onDeleteRoutine = { id ->
                storage.delete(id)
                routines = storage.loadRoutines()
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
                        onEditClick = { editingRoutine = routine }
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

@Composable
private fun RoutineItemCard(
    routine: Routine,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exerciseCountText = when (val c = routine.exercises.size) {
        1 -> "1 exercise"
        else -> "$c exercises"
    }

    TactileCardButton(
        onClick = onEditClick,
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

            Text(
                text = "EDIT",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TealHighlight
            )
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
