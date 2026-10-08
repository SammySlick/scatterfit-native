package com.scatterbrain.scatterfit.ui

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.sync.SyncHub
import com.scatterbrain.scatterfit.ui.theme.DarkCardSurface
import com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme
import com.scatterbrain.scatterfit.ui.theme.TealHighlight

enum class AppTab {
    MY_DAY,
    ODYSSEY,
    ASK
}

@Composable
fun MainAppScreen(
    modifier: Modifier = Modifier,
    onSettingsClick: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(AppTab.MY_DAY) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
            when (selectedTab) {
                AppTab.MY_DAY -> MyDayScreen(
                    onSettingsClick = onSettingsClick
                )
                AppTab.ODYSSEY -> OdysseyScreen(
                    onSettingsClick = onSettingsClick
                )
                AppTab.ASK -> AskScreen()
            }
            }
        }
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
                label = "Today",
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
                label = "Odyssey",
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
                label = "Ask",
                isSelected = selectedTab == AppTab.ASK,
                onClick = { onTabSelected(AppTab.ASK) },
                icon = { tint ->
                    AskChatIcon(
                        modifier = Modifier.size(24.dp),
                        tint = tint,
                        strokeWidthDp = 1.8.dp
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
