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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.ui.theme.AccentTeal
import com.scatterbrain.scatterfit.ui.theme.AppBackground
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.scatterbrain.scatterfit.data.WorkoutProposal
import com.scatterbrain.scatterfit.data.WorkoutProposalsStorage
import com.scatterbrain.scatterfit.ui.theme.AppCardSurface
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.BorderInput
import com.scatterbrain.scatterfit.ui.theme.DarkCardSurface
import com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.TealHighlight
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

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
    val proposalsStorage = remember { WorkoutProposalsStorage(context) }

    var routines by remember { mutableStateOf(storage.loadRoutines()) }
    var plannedSessions by remember { mutableStateOf(plannedStorage.load()) }
    var loggedSessions by remember { mutableStateOf(loggedStorage.loadAll()) }
    var proposals by remember { mutableStateOf(proposalsStorage.load().filterNot { it.dismissed }) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        proposals = proposalsStorage.load().filterNot { it.dismissed }
    }

    var activeTab by remember { mutableStateOf(TrainingTab.PLAN) }

    var viewingRoutine by remember { mutableStateOf<Routine?>(null) }
    var editingRoutine by remember { mutableStateOf<Routine?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    val sessionRecordsStorage = remember { SessionRecordsStorage(context) }
    var activePlayingRoutine by remember { mutableStateOf<Routine?>(null) }
    var activePlayingSessionName by remember { mutableStateOf<String?>(null) }
    var activePlayingDayKey by remember { mutableStateOf(todayKey()) }
    var sessionConfirmationMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sessionConfirmationMessage) {
        if (sessionConfirmationMessage != null) {
            delay(3000L)
            sessionConfirmationMessage = null
        }
    }

    if (activePlayingRoutine != null || activePlayingSessionName != null) {
        SessionPlayerScreen(
            routine = activePlayingRoutine,
            sessionName = activePlayingSessionName,
            dayKey = activePlayingDayKey,
            sessionRecordsStorage = sessionRecordsStorage,
            loggedSessionsStorage = loggedStorage,
            onFinish = { record ->
                activePlayingRoutine = null
                activePlayingSessionName = null
                loggedSessions = loggedStorage.loadAll()
                sessionConfirmationMessage = "Session logged!"
            },
            onAbandon = {
                activePlayingRoutine = null
                activePlayingSessionName = null
            }
        )
        return
    }

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
            onStartWorkout = { r ->
                activePlayingRoutine = r
                activePlayingSessionName = null
                activePlayingDayKey = todayKey()
            },
            onEditClick = { editingRoutine = viewingRoutine },
            onBackClick = { viewingRoutine = null }
        )
        return
    }

    val scrollState = rememberScrollState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
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
                        proposals = proposals,
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
                        onConfirmProposal = { proposal, chosenType ->
                            loggedStorage.add(
                                LoggedSession(
                                    dayKey = proposal.dayKey,
                                    name = chosenType,
                                    durationMinutes = proposal.durationMin
                                )
                            )
                            proposalsStorage.remove(proposal.id)
                            loggedSessions = loggedStorage.loadAll()
                            proposals = proposalsStorage.load().filterNot { it.dismissed }
                        },
                        onIgnoreProposal = { proposal ->
                            proposalsStorage.dismiss(proposal.id)
                            proposals = proposalsStorage.load().filterNot { it.dismissed }
                        },
                        onViewRoutine = { r -> viewingRoutine = r },
                        onCreateNewRoutine = { isCreatingNew = true },
                        onStartWorkout = { r, sName, dKey ->
                            activePlayingRoutine = r
                            activePlayingSessionName = sName
                            activePlayingDayKey = dKey
                        }
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
 * PLAN Tab: Seven-row week view (Mon..Sun).
 * Week navigation chevrons above the rows.
 * Tapping a row expands it IN PLACE (accordion, only one open at a time).
 * Shows assign controls [+ Routine] / [+ Session], or existing assignment with [Remove] & "Log it".
 * Recent log strip at bottom.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeeklyPlannerSection(
    routines: List<Routine>,
    plannedSessions: List<PlannedSessionDay>,
    loggedSessions: List<LoggedSession>,
    proposals: List<WorkoutProposal> = emptyList(),
    onUpsertPlan: (PlannedSessionDay) -> Unit,
    onRemovePlan: (String) -> Unit,
    onAddLoggedSession: (LoggedSession) -> Unit,
    onRemoveLoggedSession: (String) -> Unit,
    onConfirmProposal: (WorkoutProposal, String) -> Unit = { _, _ -> },
    onIgnoreProposal: (WorkoutProposal) -> Unit = {},
    onViewRoutine: (Routine) -> Unit,
    onCreateNewRoutine: () -> Unit,
    onStartWorkout: (Routine?, String?, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { todayKey() }
    var currentAnchorDay by remember { mutableStateOf(today) }
    var expandedDayKey by remember { mutableStateOf<String?>(null) }

    val weekDays = remember(currentAnchorDay) { weekDaysFor(currentAnchorDay) }

    val haptic = LocalHapticFeedback.current

    // Inline assign UI state per expanded row
    var showRoutinePicker by remember { mutableStateOf(false) }
    var showSessionInput by remember { mutableStateOf(false) }

    var customSessionName by remember { mutableStateOf("") }
    var customSessionDuration by remember { mutableStateOf("") }

    var isEditingSessionName by remember { mutableStateOf(false) }
    var editingSessionNameText by remember { mutableStateOf("") }

    // Week navigation header label: e.g. "13 – 19 Oct"
    val weekLabel = remember(weekDays) {
        runCatching {
            val first = LocalDate.parse(weekDays.first())
            val last = LocalDate.parse(weekDays.last())
            val startStr = "${first.dayOfMonth}"
            val endStr = last.format(DateTimeFormatter.ofPattern("d MMM", Locale.US))
            "$startStr – $endStr"
        }.getOrDefault("Week")
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Week Navigation Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = {
                    val curr = LocalDate.parse(currentAnchorDay)
                    currentAnchorDay = curr.minusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE)
                    expandedDayKey = null
                },
                modifier = Modifier.size(32.dp)
            ) {
                Text("‹", fontSize = 22.sp, color = TextForeground, fontWeight = FontWeight.Bold)
            }

            Text(
                text = weekLabel,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextForeground
            )

            IconButton(
                onClick = {
                    val curr = LocalDate.parse(currentAnchorDay)
                    currentAnchorDay = curr.plusDays(7).format(DateTimeFormatter.ISO_LOCAL_DATE)
                    expandedDayKey = null
                },
                modifier = Modifier.size(32.dp)
            ) {
                Text("›", fontSize = 22.sp, color = TextForeground, fontWeight = FontWeight.Bold)
            }
        }

        // 7-ROW WEEK VIEW (Mon..Sun)
        weekDays.forEach { dayKey ->
            val date = LocalDate.parse(dayKey)
            val dayNumber = date.dayOfMonth.toString()
            val weekdayAbbrev = date.format(DateTimeFormatter.ofPattern("EEE", Locale.US))
            val isCurrentToday = dayKey == today
            val isExpanded = expandedDayKey == dayKey

            val planned = plannedSessions.find { it.dayKey == dayKey }
            val routine = planned?.routineId?.let { rid -> routines.find { it.id == rid } }
            val sessionName = planned?.sessionName
            val isAssigned = routine != null || !sessionName.isNullOrBlank()
            val assignedTitle = routine?.name ?: sessionName ?: "Rest day"

            val isLogged = loggedSessions.any { it.dayKey == dayKey }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        if (isExpanded) {
                            expandedDayKey = null
                            showRoutinePicker = false
                            showSessionInput = false
                            isEditingSessionName = false
                        } else {
                            expandedDayKey = dayKey
                            showRoutinePicker = false
                            showSessionInput = false
                            isEditingSessionName = false
                            editingSessionNameText = sessionName ?: ""
                        }
                    },
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
                border = BorderStroke(
                    1.dp,
                    if (isCurrentToday) TealHighlight else BorderDefault
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row Header (Day badge, title, logged tick, chevron)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Day Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isCurrentToday) TealHighlight.copy(alpha = 0.15f) else SecondaryFill)
                                    .border(
                                        BorderStroke(1.dp, if (isCurrentToday) TealHighlight else BorderDefault),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = weekdayAbbrev,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isCurrentToday) TealHighlight else TextMuted
                                    )
                                    Text(
                                        text = dayNumber,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrentToday) TealHighlight else TextForeground
                                    )
                                }
                            }

                            // Assignment Title
                            Text(
                                text = assignedTitle,
                                fontSize = 13.sp,
                                fontWeight = if (isAssigned) FontWeight.Bold else FontWeight.Normal,
                                color = if (isAssigned) TextForeground else TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isLogged) {
                                Text("✓", color = TealHighlight, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text(
                                text = if (isExpanded) "▴" else "▾",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Accordion Expanded Content
                    if (isExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (routine != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TextButton(onClick = { onViewRoutine(routine) }) {
                                        Text("View Routine →", color = TealHighlight, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(TealHighlight)
                                                .clickable { onStartWorkout(routine, null, dayKey) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("Start", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        if (!isLogged) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(TealHighlight.copy(alpha = 0.15f))
                                                    .border(BorderStroke(1.dp, TealHighlight.copy(alpha = 0.4f)), RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        onAddLoggedSession(LoggedSession(dayKey = dayKey, name = routine.name))
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("Log it", color = TealHighlight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        TextButton(onClick = { onRemovePlan(dayKey) }) {
                                            Text("Remove", color = TextMuted, fontSize = 11.sp)
                                        }
                                    }
                                }
                            } else if (!sessionName.isNullOrBlank()) {
                                if (isEditingSessionName) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
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
                                                    planned?.copy(sessionName = trimmed)
                                                        ?: PlannedSessionDay(dayKey = dayKey, sessionName = trimmed)
                                                )
                                            }
                                        }) {
                                            Text("✓", color = TealHighlight, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        TextButton(onClick = { isEditingSessionName = true }) {
                                            Text("Edit Name", color = TealHighlight, fontSize = 12.sp)
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(TealHighlight)
                                                    .clickable { onStartWorkout(null, sessionName, dayKey) }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("Start", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }

                                            if (!isLogged) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(TealHighlight.copy(alpha = 0.15f))
                                                        .border(BorderStroke(1.dp, TealHighlight.copy(alpha = 0.4f)), RoundedCornerShape(6.dp))
                                                        .clickable {
                                                            onAddLoggedSession(LoggedSession(dayKey = dayKey, name = sessionName))
                                                        }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Log it", color = TealHighlight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            TextButton(onClick = { onRemovePlan(dayKey) }) {
                                                Text("Remove", color = TextMuted, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Rest day -> assign controls
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SecondaryFill)
                                            .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(6.dp))
                                            .clickable {
                                                showRoutinePicker = !showRoutinePicker
                                                showSessionInput = false
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("+ Routine", color = TealHighlight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SecondaryFill)
                                            .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(6.dp))
                                            .clickable {
                                                showSessionInput = !showSessionInput
                                                showRoutinePicker = false
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("+ Session", color = TealHighlight, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                if (showRoutinePicker) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SecondaryFill)
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        routines.forEach { r ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(DarkCardSurface)
                                                    .clickable {
                                                        onUpsertPlan(PlannedSessionDay(dayKey = dayKey, routineId = r.id))
                                                        showRoutinePicker = false
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = r.name,
                                                    modifier = Modifier.weight(1f, fill = false),
                                                    color = TextForeground,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${r.exercises.size} ex",
                                                    color = TextMuted,
                                                    fontSize = 10.sp,
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(DarkCardSurface)
                                                .clickable {
                                                    showRoutinePicker = false
                                                    onCreateNewRoutine()
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text("+ New routine…", color = TealHighlight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                if (showSessionInput) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(SecondaryFill)
                                            .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = customSessionName,
                                            onValueChange = { customSessionName = it },
                                            placeholder = { Text("Session (e.g. Running)", color = TextMuted) },
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
                                                    onUpsertPlan(PlannedSessionDay(dayKey = dayKey, sessionName = trimmed, note = note))
                                                    customSessionName = ""
                                                    customSessionDuration = ""
                                                    showSessionInput = false
                                                }
                                            },
                                            enabled = customSessionName.isNotBlank(),
                                            colors = ButtonDefaults.buttonColors(containerColor = TealHighlight, contentColor = Color.Black),
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("Save Planned Session", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Detected Sessions Card
        if (proposals.isNotEmpty()) {
            DetectedSessionsCard(
                proposals = proposals,
                onConfirm = onConfirmProposal,
                onIgnore = onIgnoreProposal,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Recent Logs Strip
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
    onStartWorkout: (Routine) -> Unit = {},
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

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(TealHighlight)
                            .clickable(onClick = { onStartWorkout(routine) })
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Start workout",
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
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
                                if (ex.restSeconds > 0) add("Rest: ${formatClockTime(ex.restSeconds)}")
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

@Composable
private fun DetectedSessionsCard(
    proposals: List<WorkoutProposal>,
    onConfirm: (WorkoutProposal, String) -> Unit,
    onIgnore: (WorkoutProposal) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppCardSurface),
        border = BorderStroke(1.dp, BorderDefault)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "DETECTED SESSIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp
            )

            proposals.forEachIndexed { index, proposal ->
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BorderDefault)
                    )
                }

                DetectedSessionRow(
                    proposal = proposal,
                    onConfirm = onConfirm,
                    onIgnore = onIgnore
                )
            }
        }
    }
}

@Composable
private fun DetectedSessionRow(
    proposal: WorkoutProposal,
    onConfirm: (WorkoutProposal, String) -> Unit,
    onIgnore: (WorkoutProposal) -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle = remember(proposal) {
        val zdt = Instant.ofEpochMilli(proposal.startMs).atZone(ZoneId.systemDefault())
        val datePart = zdt.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.US))
        val timePart = zdt.format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
        "$datePart · $timePart · ${proposal.durationMin} min · avg ${proposal.meanHr.roundToInt()} bpm · peak ${proposal.peakHr.roundToInt()}"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = proposal.proposedType,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = TextForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 1. CONFIRM
            TextButton(
                onClick = { onConfirm(proposal, proposal.proposedType) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Confirm",
                    color = AccentTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 2. CHANGE
            var changeMenuOpen by remember { mutableStateOf(false) }
            Box {
                TextButton(
                    onClick = { changeMenuOpen = true },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Change",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                DropdownMenu(
                    expanded = changeMenuOpen,
                    onDismissRequest = { changeMenuOpen = false },
                    modifier = Modifier.background(DarkCardSurface)
                ) {
                    val sportOptions = listOf("Run", "Walk", "Football", "Boxing", "HIIT", "Workout")
                    sportOptions.forEach { sport ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = sport,
                                    color = if (sport == proposal.proposedType) AccentTeal else TextForeground,
                                    fontSize = 13.sp,
                                    fontWeight = if (sport == proposal.proposedType) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                changeMenuOpen = false
                                onConfirm(proposal, sport)
                            }
                        )
                    }
                }
            }

            // 3. IGNORE
            TextButton(
                onClick = { onIgnore(proposal) },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "Ignore",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
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
