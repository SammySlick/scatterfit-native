package com.scatterbrain.scatterfit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.scatterbrain.scatterfit.data.HcReaders
import com.scatterbrain.scatterfit.data.RecordMethod
import com.scatterbrain.scatterfit.sync.SyncEngine
import com.scatterbrain.scatterfit.sync.SyncHub
import com.scatterbrain.scatterfit.sync.SyncStore
import com.scatterbrain.scatterfit.ui.theme.AppBackground
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.DarkCardSurface
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.TealHighlight
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted

data class MethodDebugInfo(
    val method: RecordMethod,
    val recordCount: Int,
    val cursorText: String,
    val progressText: String,
    val progressFraction: Float,
)

fun computeMethodDebugInfo(
    method: RecordMethod,
    state: SyncStore.MethodState,
    nowMs: Long = System.currentTimeMillis(),
): MethodDebugInfo {
    val recordCount = state.records.size
    val cursorText = formatCursorDate(state.cursorMs)
    val targetMs = if (method == RecordMethod.HEART_RATE) HcReaders.HR_RAW_WINDOW_MS else SyncEngine.LOOKBACK_MS
    val dayMs = 24L * 60 * 60 * 1000
    val targetDays = (targetMs / dayMs).toInt()
    val days = if (state.cursorMs == null) {
        0
    } else {
        val lookbackStart = nowMs - targetMs
        val diffMs = state.cursorMs - lookbackStart
        (diffMs / dayMs).toInt().coerceIn(0, targetDays)
    }
    val progressText = "$days/$targetDays days backfilled"
    val progressFraction = if (targetDays > 0) (days.toFloat() / targetDays.toFloat()).coerceIn(0f, 1f) else 0f
    return MethodDebugInfo(
        method = method,
        recordCount = recordCount,
        cursorText = cursorText,
        progressText = progressText,
        progressFraction = progressFraction
    )
}

fun formatSdkStatus(status: Int): String = when (status) {
    HealthConnectClient.SDK_AVAILABLE -> "SDK_AVAILABLE"
    HealthConnectClient.SDK_UNAVAILABLE -> "SDK_UNAVAILABLE"
    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED"
    else -> "UNKNOWN ($status)"
}

@Composable
fun DebugScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var refreshKey by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refreshKey++
    }

    val methodInfos = remember(refreshKey) {
        val store = SyncHub.store
        val now = System.currentTimeMillis()
        RecordMethod.entries.map { method ->
            val state = store?.load(method) ?: SyncStore.MethodState(null, emptyList())
            computeMethodDebugInfo(method, state, now)
        }
    }

    val sdkStatusText = remember(refreshKey, context) {
        try {
            formatSdkStatus(HealthConnectClient.getSdkStatus(context))
        } catch (e: Exception) {
            "ERROR: ${e.message ?: e.javaClass.simpleName}"
        }
    }

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
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextForeground
                    )
                }
                Text(
                    text = "Debug",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground
                )
            }

            // Live banner text from SyncHub.status
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

            // Diagnostic header info: sdkStatus line and Refresh button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "sdkStatus: $sdkStatusText",
                    fontSize = 12.sp,
                    color = TextMuted
                )
                TextButton(onClick = { refreshKey++ }) {
                    Text(
                        text = "Refresh",
                        color = TealHighlight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Scrollable method cards
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (info in methodInfos) {
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
                                text = info.method.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextForeground
                            )
                            Text(
                                text = "${info.recordCount} records",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                            Text(
                                text = "Cursor: ${info.cursorText}",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                            Text(
                                text = info.progressText,
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                            LinearProgressIndicator(
                                progress = { info.progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp),
                                color = TealHighlight,
                                trackColor = SecondaryFill,
                                gapSize = 0.dp,
                                drawStopIndicator = {}
                            )
                        }
                    }
                }
            }
        }
    }
}
