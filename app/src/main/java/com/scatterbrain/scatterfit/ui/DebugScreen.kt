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
import com.scatterbrain.scatterfit.core.MetricId
import com.scatterbrain.scatterfit.core.MetricRegistry
import com.scatterbrain.scatterfit.core.dayKeyFromIso
import com.scatterbrain.scatterfit.data.DailyRepository
import com.scatterbrain.scatterfit.data.HcReaders
import com.scatterbrain.scatterfit.data.RecordMethod
import com.scatterbrain.scatterfit.data.SleepNight
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

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

private data class MetricValueRow(
    val label: String,
    val valueText: String,
    val dayText: String,
)

private fun formatMetricAge(dayKey: String, today: LocalDate = LocalDate.now()): String {
    val date = runCatching { LocalDate.parse(dayKey) }.getOrNull() ?: return dayKey
    val daysAgo = ChronoUnit.DAYS.between(date, today).toInt()
    val ageStr = when {
        daysAgo == 0 -> "today"
        daysAgo == 1 -> "1d ago"
        daysAgo > 1 -> "${daysAgo}d ago"
        else -> "${-daysAgo}d future"
    }
    return "$dayKey · $ageStr"
}

private fun extractLatestMetric(
    label: String,
    map: Map<String, Double>?,
    formatValue: (Double) -> String,
    today: LocalDate,
): MetricValueRow {
    val nonNullEntries = map ?: emptyMap()
    val maxKey = nonNullEntries.keys.maxOrNull()
    return if (maxKey != null) {
        val v = nonNullEntries[maxKey]!!
        MetricValueRow(
            label = label,
            valueText = formatValue(v),
            dayText = formatMetricAge(maxKey, today)
        )
    } else {
        MetricValueRow(
            label = label,
            valueText = "—",
            dayText = "no records"
        )
    }
}

private fun extractLatestSleep(
    map: Map<String, SleepNight>?,
    today: LocalDate,
): MetricValueRow {
    val maxKey = map?.keys?.maxOrNull()
    val night = maxKey?.let { map[it] }
    return if (maxKey != null && night != null && night.totalMin > 0.0) {
        val mins = night.totalMin.roundToInt()
        MetricValueRow(
            label = "Sleep",
            valueText = "${mins / 60}h ${mins % 60}m",
            dayText = formatMetricAge(maxKey, today)
        )
    } else {
        MetricValueRow(
            label = "Sleep",
            valueText = "—",
            dayText = "no records"
        )
    }
}

private fun extractLatestHr(
    pair: Pair<Double, String>?,
    zone: ZoneId = ZoneId.systemDefault(),
    today: LocalDate,
): MetricValueRow {
    return if (pair != null) {
        val dayKey = dayKeyFromIso(pair.second, zone)
        MetricValueRow(
            label = "Latest Heart Rate",
            valueText = "${pair.first.roundToInt()} bpm",
            dayText = formatMetricAge(dayKey, today)
        )
    } else {
        MetricValueRow(
            label = "Latest Heart Rate",
            valueText = "—",
            dayText = "no records"
        )
    }
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

    val assembledDaily by DailyRepository.daily.collectAsState()
    val todayDate = remember(refreshKey) { LocalDate.now() }
    val todayNumbers = remember(assembledDaily, todayDate) {
        val ad = assembledDaily
        listOf(
            extractLatestMetric(
                label = MetricRegistry.byId(MetricId.STEPS).label,
                map = ad?.steps,
                formatValue = { MetricRegistry.format(MetricRegistry.byId(MetricId.STEPS), it) },
                today = todayDate
            ),
            extractLatestMetric(
                label = MetricRegistry.byId(MetricId.WEIGHT).label,
                map = ad?.weight,
                formatValue = { MetricRegistry.format(MetricRegistry.byId(MetricId.WEIGHT), it) },
                today = todayDate
            ),
            extractLatestMetric(
                label = MetricRegistry.byId(MetricId.CALORIES_EATEN).label,
                map = ad?.caloriesEaten,
                formatValue = { MetricRegistry.format(MetricRegistry.byId(MetricId.CALORIES_EATEN), it) },
                today = todayDate
            ),
            extractLatestMetric(
                label = MetricRegistry.byId(MetricId.PROTEIN).label,
                map = ad?.protein,
                formatValue = { MetricRegistry.format(MetricRegistry.byId(MetricId.PROTEIN), it) },
                today = todayDate
            ),
            extractLatestMetric(
                label = "Carbs",
                map = ad?.carbs,
                formatValue = { "%,d g".format(it.roundToInt()) },
                today = todayDate
            ),
            extractLatestMetric(
                label = "Fat",
                map = ad?.fat,
                formatValue = { "%,d g".format(it.roundToInt()) },
                today = todayDate
            ),
            extractLatestMetric(
                label = MetricRegistry.byId(MetricId.CALORIES_BURNED).label,
                map = ad?.caloriesBurned,
                formatValue = { MetricRegistry.format(MetricRegistry.byId(MetricId.CALORIES_BURNED), it) },
                today = todayDate
            ),
            extractLatestMetric(
                label = MetricRegistry.byId(MetricId.RESTING_HR).label,
                map = ad?.restingHr,
                formatValue = { MetricRegistry.format(MetricRegistry.byId(MetricId.RESTING_HR), it) },
                today = todayDate
            ),
            extractLatestMetric(
                label = MetricRegistry.byId(MetricId.TRAINING_MINUTES).label,
                map = ad?.trainingMinutes,
                formatValue = { MetricRegistry.format(MetricRegistry.byId(MetricId.TRAINING_MINUTES), it) },
                today = todayDate
            ),
            extractLatestMetric(
                label = MetricRegistry.byId(MetricId.ALCOHOL_UNITS).label,
                map = ad?.alcoholUnits,
                formatValue = { MetricRegistry.format(MetricRegistry.byId(MetricId.ALCOHOL_UNITS), it) },
                today = todayDate
            ),
            extractLatestSleep(
                map = ad?.sleep,
                today = todayDate
            ),
            extractLatestHr(
                pair = ad?.latestHeartRate,
                today = todayDate
            )
        )
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
                // Today's numbers panel
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
                        Text(
                            text = "Today's numbers",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextForeground
                        )

                        for (num in todayNumbers) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = num.label,
                                    fontSize = 12.sp,
                                    color = TextForeground,
                                    modifier = Modifier.weight(1f)
                                )
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    Text(
                                        text = num.valueText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextForeground
                                    )
                                    Text(
                                        text = num.dayText,
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
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

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun DebugScreenPreview() {
    com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme {
        DebugScreen(onBack = {})
    }
}
