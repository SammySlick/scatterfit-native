package com.scatterbrain.scatterfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.scatterbrain.scatterfit.data.SettingsStorage
import com.scatterbrain.scatterfit.sync.SyncHub
import com.scatterbrain.scatterfit.ui.theme.AppBackground
import com.scatterbrain.scatterfit.ui.theme.DarkCardSurface
import com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme
import com.scatterbrain.scatterfit.ui.theme.TealHighlight
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import kotlinx.coroutines.delay

enum class AppTab {
    MY_DAY,
    ODYSSEY,
    FOOD,
    TRAINING,
    CHECKIN
}

@Composable
fun MainAppScreen(
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val foodStorage = remember { FoodStorage(context) }
    val savedFoodStore = remember { SavedFoodStore(context) }
    val settingsStorage = remember { SettingsStorage(context) }

    // Ships with routines imported from Sam's Hevy account; runs once per install.
    LaunchedEffect(Unit) { StarterRoutines.seedIfNeeded(context, RoutineStorage(context)) }

    val foodViewModel = remember {
        FoodTrackerViewModel(
            storage = foodStorage,
            savedFoodStore = savedFoodStore
        )
    }

    val repoDaily by com.scatterbrain.scatterfit.data.DailyRepository.daily.collectAsState()
    val momentumSource: MomentumSource = remember(repoDaily) {
        val s = settingsStorage.loadSettings()
        repoDaily?.let { TodayFacadeMomentumSource(it, s) } ?: TodayFacadeMomentumSource()
    }

    var selectedTab by remember { mutableStateOf(AppTab.MY_DAY) }

    // The in-progress workout, read here so its banner can follow him across every tab.
    // TrainingScreen owns the session itself; this is only the on-disk copy, used to show
    // what's running and to ask Training to reopen it.
    val draftStorage = remember { ActiveSessionStorage(context) }
    var activeDraft by remember { mutableStateOf(draftStorage.load()) }
    var bannerClock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var resumeRequest by remember { mutableIntStateOf(0) }
    var isPlayerOpen by remember { mutableStateOf(false) }

    // Coming back to the app is the moment the draft can have changed underneath us.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        activeDraft = draftStorage.load()
        bannerClock = System.currentTimeMillis()
    }

    // Switching tabs is how a session started in Training becomes visible everywhere else,
    // and how finishing one makes the banner disappear.
    LaunchedEffect(selectedTab) {
        activeDraft = draftStorage.load()
        bannerClock = System.currentTimeMillis()
    }

    // "12 min in" goes stale otherwise, and the player mirrors its draft every 2 seconds.
    LaunchedEffect(activeDraft?.id) {
        if (activeDraft == null) return@LaunchedEffect
        while (true) {
            delay(20_000L)
            bannerClock = System.currentTimeMillis()
            activeDraft = draftStorage.load()
        }
    }

    BackHandler(enabled = selectedTab != AppTab.MY_DAY) {
        selectedTab = AppTab.MY_DAY
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppBackground,
        bottomBar = {
            BottomNavBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val hcStatus by SyncHub.status.collectAsState()
            if (hcStatus.isNotBlank()) {
                Text(
                    text = hcStatus,
                    color = TealHighlight,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkCardSurface)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }

            // A workout you left running shouldn't be something you have to remember.
            val runningDraft = activeDraft
            if (runningDraft != null && !isPlayerOpen && !isDraftUntouched(runningDraft)) {
                ActiveWorkoutBanner(
                    draft = runningDraft,
                    nowMillis = bannerClock,
                    onResume = {
                        selectedTab = AppTab.TRAINING
                        resumeRequest++
                    }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
            when (selectedTab) {
                AppTab.MY_DAY -> MyDayScreen(
                    onSettingsClick = onSettingsClick,
                    onOpenFood = { dayKey ->
                        foodViewModel.setDayKey(dayKey)
                        selectedTab = AppTab.FOOD
                    },
                    onOpenTraining = { selectedTab = AppTab.TRAINING },
                    foodViewModel = foodViewModel
                )
                AppTab.ODYSSEY -> OdysseyScreen(
                    momentumSource = momentumSource,
                    onSettingsClick = onSettingsClick
                )
                AppTab.FOOD -> {
                    FoodHomeScreen(
                        viewModel = foodViewModel,
                        onBackClick = { selectedTab = AppTab.MY_DAY },
                        settings = settingsStorage.loadSettings()
                    )
                }
                AppTab.TRAINING -> {
                    val routineStorage = remember { RoutineStorage(context) }
                    TrainingScreen(
                        storage = routineStorage,
                        onBackClick = { selectedTab = AppTab.MY_DAY },
                        resumeRequest = resumeRequest,
                        onResumeRequestHandled = { resumeRequest = 0 },
                        onPlayerOpenChanged = { isPlayerOpen = it }
                    )
                }
                AppTab.CHECKIN -> CheckInScreen()
            }
            }
        }
    }
}

/**
 * The "you have a workout running" strip.
 *
 * Sits above the tab content so it's on every tab, and shows how long the session has been
 * going rather than just that one exists — a banner reading "workout in progress" for six
 * hours because he forgot to finish it is worse than none.
 */
@Composable
fun ActiveWorkoutBanner(
    draft: ActiveSessionDraft,
    nowMillis: Long,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkCardSurface)
            .clickable(onClick = onResume)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(TealHighlight)
        )
        Spacer(modifier = Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = draft.title,
                color = TextForeground,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = draftProgressLabel(draft, nowMillis),
                color = TextMuted,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
        Text(
            text = "Resume",
            color = TealHighlight,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun BottomNavBar(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkCardSurface)
            .navigationBarsPadding()
    ) {
        // Divider line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF2C2F33))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                label = "My Day",
                isSelected = selectedTab == AppTab.MY_DAY,
                onClick = { onTabSelected(AppTab.MY_DAY) },
                icon = { tint ->
                    PulseIcon(
                        modifier = Modifier.size(24.dp),
                        tint = tint,
                        strokeWidthDp = 2.dp
                    )
                }
            )

            BottomNavItem(
                label = "Matrix",
                isSelected = selectedTab == AppTab.ODYSSEY,
                onClick = { onTabSelected(AppTab.ODYSSEY) },
                icon = { tint ->
                    CompassIcon(
                        modifier = Modifier.size(24.dp),
                        tint = tint,
                        strokeWidthDp = 1.8.dp
                    )
                }
            )

            BottomNavItem(
                label = "Food",
                isSelected = selectedTab == AppTab.FOOD,
                onClick = { onTabSelected(AppTab.FOOD) },
                icon = { tint ->
                    AppleIcon(
                        modifier = Modifier.size(24.dp),
                        tint = tint,
                        strokeWidthDp = 1.8.dp
                    )
                }
            )

            BottomNavItem(
                label = "Training",
                isSelected = selectedTab == AppTab.TRAINING,
                onClick = { onTabSelected(AppTab.TRAINING) },
                icon = { tint ->
                    DumbbellIcon(
                        modifier = Modifier.size(24.dp),
                        tint = tint,
                        strokeWidthDp = 2.dp
                    )
                }
            )

            BottomNavItem(
                label = "Check-in",
                isSelected = selectedTab == AppTab.CHECKIN,
                onClick = { onTabSelected(AppTab.CHECKIN) },
                icon = { tint ->
                    CheckInIcon(
                        modifier = Modifier.size(24.dp),
                        tint = tint
                    )
                }
            )
        }
    }
}

@Composable
fun BottomNavItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = TealHighlight
    val inactiveColor = Color(0xFF8E8E93)
    val color = if (isSelected) activeColor else inactiveColor

    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon(color)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = color
        )
    }
}

@Composable
fun AskScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0E0F10)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Ask ScatterFit Assistant",
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MainAppScreenPreview() {
    ScatterFitTheme {
        MainAppScreen()
    }
}
