package com.scatterbrain.scatterfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.core.daysLogged
import com.scatterbrain.scatterfit.core.formatCount
import com.scatterbrain.scatterfit.core.formatVolumeKg
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.core.trainingWindow
import com.scatterbrain.scatterfit.core.trendSentence
import com.scatterbrain.scatterfit.core.trendWindow
import com.scatterbrain.scatterfit.data.DailyRepository
import com.scatterbrain.scatterfit.ui.theme.AccentTeal
import com.scatterbrain.scatterfit.ui.theme.AppBackground
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.DomainBody
import com.scatterbrain.scatterfit.ui.theme.DomainMove
import com.scatterbrain.scatterfit.ui.theme.DomainSleep
import com.scatterbrain.scatterfit.ui.theme.DomainTrain
import com.scatterbrain.scatterfit.ui.theme.NeutralAttend
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import java.util.Locale

/**
 * The "how is this actually going" page.
 *
 * Sam, 2026-11-24: BWS "feels more like a diary and less like a data board",
 * and he wants that feel with a proper progress report on top of it. So this
 * screen is deliberately quiet: one big number at the top, then a small number
 * of reading-back rows, no grid, no judgements, no amber.
 *
 * Every figure here is computed in core/ProgressReport.kt, which is pure and
 * tested. This file only formats. That is on purpose — two implementations of
 * "what does this add up to" is how the breakdown screen ended up rendering
 * Sleep twice.
 */
@Composable
fun ProgressReportScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val daily by DailyRepository.daily.collectAsState()
    var windowDays by remember { mutableStateOf(30) }

    val today = remember { todayKey(java.time.ZoneId.systemDefault()) }
    val records = remember(windowDays) {
        SessionRecordsStorage(context).loadAll()
    }

    val weight = daily?.weight.orEmpty()
    val bodyFat = daily?.bodyFat.orEmpty()
    val steps = daily?.steps.orEmpty()
    val sleep = daily?.sleep.orEmpty().mapValues { it.value.totalMin }

    val weightWindow = remember(weight, windowDays, today) { trendWindow(weight, today, windowDays) }
    val fatWindow = remember(bodyFat, windowDays, today) { trendWindow(bodyFat, today, windowDays) }
    val stepWindow = remember(steps, windowDays, today) { trendWindow(steps, today, windowDays) }
    val sleepWindow = remember(sleep, windowDays, today) { trendWindow(sleep, today, windowDays) }
    val training = remember(records, windowDays, today) { trainingWindow(records, today, windowDays) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextForeground)
                }
                Text(
                    text = "Progress",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            WindowPicker(windowDays = windowDays, onChange = { windowDays = it })

            Spacer(Modifier.height(24.dp))

            // The hero. One number, large, with a plain sentence under it.
            val latestWeight = weightWindow.lastValue
            if (latestWeight != null) {
                Text(
                    text = String.format(Locale.UK, "%.1f", latestWeight),
                    fontSize = 56.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextForeground,
                )
                Text(
                    text = "kg",
                    fontSize = 13.sp,
                    color = TextMuted,
                )
                Spacer(Modifier.height(6.dp))
                val sentence = trendSentence("Weight", weightWindow, "kg", decimals = 1)
                Text(
                    text = sentence ?: "Not enough weigh-ins to show a trend yet",
                    fontSize = 14.sp,
                    color = if (sentence != null) NeutralAttend else TextMuted,
                )
            } else {
                Text(
                    text = "No weigh-ins in the last $windowDays days",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextForeground,
                )
                Text(
                    text = "Log a weight and this page fills in.",
                    fontSize = 14.sp,
                    color = TextMuted,
                )
            }

            Spacer(Modifier.height(28.dp))

            SectionHeading("Body", DomainBody)

            StatRow(
                label = "Body fat",
                value = fatWindow.lastValue?.let { String.format(Locale.UK, "%.1f%%", it) } ?: "—",
                sub = trendSentence("Body fat", fatWindow, "%", decimals = 1, steadyBand = 0.4)
                    ?: coverage(bodyFat, today, windowDays),
            )

            StatRow(
                label = "Weight readings",
                value = "${weightWindow.readings} of $windowDays days",
                sub = if (weightWindow.readings >= windowDays / 2) null
                else "Sparse. A trend needs most days to mean anything.",
            )

            Spacer(Modifier.height(24.dp))

            // Sleep sits above Steps on purpose: it is the thing Sam checks daily and
            // the thing the night's data actually costs him (a bad night is visible in
            // weight and training the next day). Steps are a count-up you glance at.
            SectionHeading("Sleep", DomainSleep)

            StatRow(
                label = "Nightly average",
                value = sleepWindow.average?.let { formatSleep(it) } ?: "—",
                sub = trendSentence("Sleep", sleepWindow, " min", decimals = 0, steadyBand = 15.0)
                    ?: coverage(sleep, today, windowDays),
            )

            StatRow(
                label = "Nights recorded",
                value = "${sleepWindow.readings} of $windowDays",
                sub = if (sleepWindow.readings < windowDays / 2) "Sparse. The band needs to be worn overnight." else null,
            )

            Spacer(Modifier.height(24.dp))

            SectionHeading("Activity", DomainMove)

            StatRow(
                label = "Steps, daily average",
                value = stepWindow.average?.let { formatCount(it.toInt()) } ?: "—",
                sub = if (stepWindow.readings > 0) "across ${stepWindow.readings} days with data" else coverage(steps, today, windowDays),
            )

            Spacer(Modifier.height(24.dp))

            SectionHeading("Training", DomainTrain)

            if (training.isEmpty) {
                StatRow(
                    label = "Sessions",
                    value = "None",
                    sub = "No finished sessions in the last $windowDays days.",
                )
            } else {
                StatRow(
                    label = "Sessions",
                    value = "${training.sessions}",
                    sub = "${training.totalSets} sets · ${formatVolumeKg(training.totalVolumeKg)} lifted",
                )
                StatRow(
                    label = "Personal bests",
                    value = if (training.pbs == 0) "None" else "${training.pbs}",
                    sub = if (training.pbs == 0) "Heaviest set unchanged this window." else "Heaviest set beaten.",
                )
            }

            Spacer(Modifier.height(40.dp))

            Text(
                text = "Averaged over days that have a reading. A day with nothing logged is not counted as zero.",
                fontSize = 12.sp,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 32.dp),
            )
        }
    }
}

@Composable
private fun WindowPicker(windowDays: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SecondaryFill)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(30 to "30 days", 90 to "90 days").forEach { (days, label) ->
            val selected = windowDays == days
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) AppBackground else Color.Transparent)
                    .clickable { onChange(days) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) AccentTeal else TextMuted,
                )
            }
        }
    }
}

/**
 * Section label. Sentence case and no letter spacing on purpose: the app had 38
 * all-caps letterspaced labels, which is a Bloomberg-terminal convention and is
 * a large part of why it read as a data board rather than a diary.
 */
@Composable
private fun SectionHeading(text: String, accent: Color) {
    // A coloured rule beside the heading, and the heading in its own hue. The
    // page is otherwise one long column of hairline-separated rows, so without
    // this the only landmark is a word.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
    }
}

/**
 * A row, not a card. The app had 117 Card() calls and 54 outlined borders; when
 * everything is a panel, nothing has priority. Rows separated by a hairline read
 * as a list of facts, which is what a diary page is.
 */
@Composable
private fun StatRow(label: String, value: String, sub: String? = null) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontSize = 15.sp,
                color = TextMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextForeground,
            )
        }
        if (sub != null) {
            Text(
                text = sub,
                fontSize = 13.sp,
                color = TextMuted,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(BorderDefault),
        )
    }
}

private fun coverage(values: Map<String, Double>, today: String, windowDays: Int): String {
    val n = daysLogged(values, today, windowDays)
    return if (n == 0) "Nothing recorded in the last $windowDays days."
    else "$n of $windowDays days have a reading."
}

/** 452.0 minutes -> "7h 32m". Not formatDuration(), which is for set times. */
private fun formatSleep(minutes: Double): String {
    val total = minutes.toInt()
    val h = total / 60
    val m = total % 60
    return if (h == 0) "${m}m" else "${h}h ${m}m"
}
