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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.scatterbrain.scatterfit.core.todayKey
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class TrainingTab { PLAN, ROUTINES }

@Composable
fun TrainingScreen(
    storage: RoutineStorage,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBackClick)

    val context = LocalContext.current
    val plannedStorage = remember { PlannedSessionsStorage(context) }
    val loggedStorage = remember { LoggedSessionsStorage(context) }

    var routines by remember { mutableStateOf(storage.loadRoutines()) }
    var plannedSessions by remember { mutableStateOf(plannedStorage.load()) }
    var loggedSessions by remember { mutableStateOf(loggedStorage.loadAll()) }

    var activeTab by remember { mutableStateOf(TrainingTab.PLAN) }

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
                    text = "Training",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground
                )
            }

            // Segmented Control: [PLAN] [ROUTINES] (PLAN is default)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SecondaryFill)
                    .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(10.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (activeTab == TrainingTab.PLAN) TealHighlight else Color.Transparent)
                        .clickable { activeTab = TrainingTab.PLAN }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "PLAN",
                        fontSize = 12.sp,
                        fontWeight = if (activeTab == TrainingTab.PLAN) FontWeight.Bold else FontWeight.Medium,
                        color = if (activeTab == TrainingTab.PLAN) Color.Black else TextMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (activeTab == TrainingTab.ROUTINES) TealHighlight else Color.Transparent)
                        .clickable { activeTab = TrainingTab.ROUTINES }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ROUTINES",
                        fontSize = 12.sp,
                        fontWeight = if (activeTab == TrainingTab.ROUTINES) FontWeight.Bold else FontWeight.Medium,
                        color = if (activeTab == TrainingTab.ROUTINES) Color.Black else TextMuted
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
                if (activeTab == TrainingTab.PLAN) {
                    WeeklyPlannerSection(
                        routines = routines,
                        plannedSessions = plannedSessions,
                        loggedSessions = loggedSessions,
                        onUpsertPlan = { entry ->
                            plannedStorage.upsert(entry)
                            plannedSessions = plannedStorage.load()
                        },
                        onRemovePlan = { dayKey ->
                            plannedStorage.remove(dayKey)
                            plannedSessions = plannedStorage.load()
                        },
                        onAddLoggedSession = { session ->
                            loggedStorage.add(session)
                            loggedSessions = loggedStorage.loadAll()
                        },
                        onRemoveLoggedSession = { id ->
                            loggedStorage.remove(id)
                            loggedSessions = loggedStorage.loadAll()
                        },
                        onViewRoutine = { r -> viewingRoutine = r },
                        onCreateNewRoutine = { isCreatingNew = true }
                    )
                } else {
                    // ROUTINES tab: existing list + "+ New routine" card
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
}

/**
 * PLAN Tab: 7-day week strip (Mon-first), selected day detail, inline routine/session assigner,
 * and recent session log.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeeklyPlannerSection(
    routines: List<Routine>,
    plannedSessions: List<PlannedSessionDay>,
    loggedSessions: List<LoggedSession>,
    onUpsertPlan: (PlannedSessionDay) -> Unit,
    onRemovePlan: (String) -> Unit,
    onAddLoggedSession: (LoggedSession) -> Unit,
    onRemoveLoggedSession: (String) -> Unit,
    onViewRoutine: (Routine) -> Unit,
    onCreateNewRoutine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { todayKey() }
    var currentAnchorDay by remember { mutableStateOf(today) }
    var selectedDayKey by remember { mutableStateOf(today) }

    val weekDays = remember(currentAnchorDay) { weekDaysFor(currentAnchorDay) }

    val selectedPlanned = plannedSessions.find { it.dayKey == selectedDayKey }
    val assignedRoutine = selectedPlanned?.routineId?.let { rid -> routines.find { it.id == rid } }
    val assignedSessionName = selectedPlanned?.sessionName

    val loggedForSelectedDay = loggedSessions.filter { it.dayKey == selectedDayKey }
    val isDayLogged = loggedForSelectedDay.isNotEmpty()

    val haptic = LocalHapticFeedback.current

    // Expandable states for assign UI
    var showRoutinePicker by remember { mutableStateOf(false) }
    var showSessionInput by remember { mutableStateOf(false) }

    var customSessionName by remember { mutableStateOf("") }
    var customSessionDuration by remember { mutableStateOf("") }

    var isEditingSessionName by remember { mutableStateOf(false) }
    var editingSessionNameText by remember(assignedSessionName) { mutableStateOf(assignedSessionName ?: "") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. WEEK STRIP with Chevron Navigation
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
            border = BorderStroke(1.dp, BorderDefault)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            val curr = LocalDate.parse(currentAnchorDay)
                            currentAnchorDay = curr.minusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("‹", fontSize = 20.sp, color = TextForeground, fontWeight = FontWeight.Bold)
                    }

                    // Week Strip 7 Cells
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val weekdayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
                        weekDays.forEachIndexed { idx, dayKey ->
                            val date = LocalDate.parse(dayKey)
                            val isSelected = dayKey == selectedDayKey
                            val isCurrentToday = dayKey == today
                            val hasAssignment = plannedSessions.any {
                                it.dayKey == dayKey && (it.routineId != null || !it.sessionName.isNullOrBlank())
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) SecondaryFill else Color.Transparent)
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (isCurrentToday) TealHighlight else if (isSelected) BorderDefault else Color.Transparent
                                        ),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        selectedDayKey = dayKey
                                        showRoutinePicker = false
                                        showSessionInput = false
                                        isEditingSessionName = false
                                    }
                                    .padding(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = weekdayLetters[idx],
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    fontSize = 13.sp,
                                    color = if (isSelected || isCurrentToday) TextForeground else TextMuted,
                                    fontWeight = if (isSelected || isCurrentToday) FontWeight.Bold else FontWeight.Normal
                                )

                                // Teal dot for assigned routine/session
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(if (hasAssignment) TealHighlight else Color.Transparent)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            val curr = LocalDate.parse(currentAnchorDay)
                            currentAnchorDay = curr.plusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("›", fontSize = 20.sp, color = TextForeground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 2. DAY DETAIL (for selectedDayKey)
        val selectedDateFormatted = remember(selectedDayKey) {
            runCatching {
                LocalDate.parse(selectedDayKey).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.US))
            }.getOrDefault(selectedDayKey)
        }

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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header row: Date + Status / Rest day
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedDateFormatted,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextForeground
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isDayLogged) {
                            Text(
                                text = "✓ Done",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealHighlight
                            )
                        } else if (assignedRoutine == null && assignedSessionName.isNullOrBlank()) {
                            Text(
                                text = "Rest day",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                }

                // ASSIGNED BLOCK
                if (assignedRoutine != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SecondaryFill)
                            .clickable { onViewRoutine(assignedRoutine) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = assignedRoutine.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextForeground
                            )
                            Text(
                                text = "${assignedRoutine.exercises.size} exercises · Tap for details",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!isDayLogged) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(TealHighlight.copy(alpha = 0.15f))
                                        .border(BorderStroke(1.dp, TealHighlight.copy(alpha = 0.4f)), RoundedCornerShape(6.dp))
                                        .clickable {
                                            onAddLoggedSession(
                                                LoggedSession(dayKey = selectedDayKey, name = assignedRoutine.name)
                                            )
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Log it", color = TealHighlight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            TextButton(onClick = { onRemovePlan(selectedDayKey) }) {
                                Text("Remove", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                } else if (!assignedSessionName.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SecondaryFill)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (isEditingSessionName) {
                            OutlinedTextField(
                                value = editingSessionNameText,
                                onValueChange = { editingSessionNameText = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextForeground,
                                    unfocusedTextColor = TextForeground,
                                    focusedBorderColor = TealHighlight,
                                    unfocusedBorderColor = BorderInput
                                )
                            )
                            IconButton(onClick = {
                                isEditingSessionName = false
                                val trimmed = editingSessionNameText.trim()
                                if (trimmed.isNotBlank()) {
                                    onUpsertPlan(
                                        selectedPlanned?.copy(sessionName = trimmed)
                                            ?: PlannedSessionDay(dayKey = selectedDayKey, sessionName = trimmed)
                                    )
                                }
                            }) {
                                Text("✓", color = TealHighlight, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { isEditingSessionName = true }
                            ) {
                                Text(
                                    text = assignedSessionName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextForeground
                                )
                                Text(
                                    text = "Standalone Session · Tap name to edit",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!isDayLogged) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(TealHighlight.copy(alpha = 0.15f))
                                        .border(BorderStroke(1.dp, TealHighlight.copy(alpha = 0.4f)), RoundedCornerShape(6.dp))
                                        .clickable {
                                            onAddLoggedSession(
                                                LoggedSession(dayKey = selectedDayKey, name = assignedSessionName)
                                            )
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text("Log it", color = TealHighlight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            TextButton(onClick = { onRemovePlan(selectedDayKey) }) {
                                Text("Remove", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                } else {
                    // Empty day -> Two inline assign buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SecondaryFill)
                                .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(8.dp))
                                .clickable {
                                    showRoutinePicker = !showRoutinePicker
                                    showSessionInput = false
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+ Routine", color = TealHighlight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SecondaryFill)
                                .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(8.dp))
                                .clickable {
                                    showSessionInput = !showSessionInput
                                    showRoutinePicker = false
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+ Session", color = TealHighlight, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                // Expandable Section: Pick Routine
                if (showRoutinePicker) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SecondaryFill)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Select a routine for $selectedDateFormatted:", fontSize = 12.sp, color = TextMuted)

                        routines.forEach { r ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkCardSurface)
                                    .clickable {
                                        onUpsertPlan(PlannedSessionDay(dayKey = selectedDayKey, routineId = r.id))
                                        showRoutinePicker = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(r.name, color = TextForeground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("${r.exercises.size} ex", color = TextMuted, fontSize = 11.sp)
                            }
                        }

                        // [+ New routine] row at end
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(DarkCardSurface)
                                .clickable {
                                    showRoutinePicker = false
                                    onCreateNewRoutine()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("+ New routine…", color = TealHighlight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Expandable Section: New Standalone Session
                if (showSessionInput) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SecondaryFill)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Plan a session (Running, Golf, Football…):", fontSize = 12.sp, color = TextMuted)

                        OutlinedTextField(
                            value = customSessionName,
                            onValueChange = { customSessionName = it },
                            placeholder = { Text("Session name (e.g. Running)", color = TextMuted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextForeground,
                                unfocusedTextColor = TextForeground,
                                focusedBorderColor = TealHighlight,
                                unfocusedBorderColor = BorderInput
                            )
                        )

                        OutlinedTextField(
                            value = customSessionDuration,
                            onValueChange = { customSessionDuration = it.filter { ch -> ch.isDigit() } },
                            placeholder = { Text("Duration mins (optional)", color = TextMuted) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextForeground,
                                unfocusedTextColor = TextForeground,
                                focusedBorderColor = TealHighlight,
                                unfocusedBorderColor = BorderInput
                            )
                        )

                        Button(
                            onClick = {
                                val trimmed = customSessionName.trim()
                                if (trimmed.isNotBlank()) {
                                    val dur = customSessionDuration.toIntOrNull()
                                    val note = dur?.let { "$it mins" }
                                    onUpsertPlan(
                                        PlannedSessionDay(dayKey = selectedDayKey, sessionName = trimmed, note = note)
                                    )
                                    customSessionName = ""
                                    customSessionDuration = ""
                                    showSessionInput = false
                                }
                            },
                            enabled = customSessionName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = TealHighlight, contentColor = Color.Black),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Save Planned Session", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. RECENT LOG Strip at bottom of PLAN tab (last 5 LoggedSessions, newest first)
        val recentLogs = remember(loggedSessions) { loggedSessions.take(5) }
        if (recentLogs.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "RECENT LOGS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )

                recentLogs.forEach { logItem ->
                    val logDateFormatted = runCatching {
                        LocalDate.parse(logItem.dayKey).format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.US))
                    }.getOrDefault(logItem.dayKey)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onRemoveLoggedSession(logItem.id)
                                }
                            ),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                        border = BorderStroke(1.dp, BorderDefault)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = logItem.name,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextForeground
                                )
                                Text(
                                    text = logDateFormatted,
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (logItem.durationMinutes != null && logItem.durationMinutes > 0) {
                                    Text(
                                        text = "${logItem.durationMinutes} mins",
                                        fontSize = 12.sp,
                                        color = TextMuted
                                    )
                                }
                                Text("✓", color = TealHighlight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
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
                                if (ex.restSeconds > 0) add("Rest: ${ex.restSeconds} secs")
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
