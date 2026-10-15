package com.scatterbrain.scatterfit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.core.GoalId
import com.scatterbrain.scatterfit.core.MetricId
import com.scatterbrain.scatterfit.core.ScoringSettings
import com.scatterbrain.scatterfit.core.SquareBand
import com.scatterbrain.scatterfit.core.squareBand
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.data.AssembledDaily
import com.scatterbrain.scatterfit.data.SleepNight
import com.scatterbrain.scatterfit.data.ZoneBounds
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.DarkCardSurface
import com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme
import com.scatterbrain.scatterfit.ui.theme.StateBad
import com.scatterbrain.scatterfit.ui.theme.StateLocked
import com.scatterbrain.scatterfit.ui.theme.StateSlip
import com.scatterbrain.scatterfit.ui.theme.TealHighlight
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

data class DayCardStatus(
    val caloriesPassed: Boolean,
    val stepsPassed: Boolean,
    val proteinPassed: Boolean,
    val sleepPassed: Boolean,
    val trainingPassed: Boolean,
) {
    val completedCount: Int = listOf(
        caloriesPassed,
        stepsPassed,
        proteinPassed,
        sleepPassed,
        trainingPassed
    ).count { it }

    val headerText: String = if (completedCount == 5) "All done ✓" else "$completedCount/5 done"
}

fun computeDayCardStatus(
    daily: AssembledDaily?,
    settings: ScoringSettings = ScoringSettings(),
    todayKey: String,
): DayCardStatus {
    if (daily == null) {
        return DayCardStatus(
            caloriesPassed = false,
            stepsPassed = false,
            proteinPassed = false,
            sleepPassed = false,
            trainingPassed = false,
        )
    }

    val calBand = squareBand(GoalId.CALORIES, daily.caloriesEaten[todayKey], settings)
    val stepBand = squareBand(GoalId.STEPS, daily.steps[todayKey], settings)
    val protBand = squareBand(GoalId.PROTEIN, daily.protein[todayKey], settings)
    val sleepBand = squareBand(GoalId.SLEEP, daily.sleep[todayKey]?.totalMin, settings)
    val trainBand = squareBand(GoalId.EXERCISE, daily.trainingMinutes[todayKey], settings)

    return DayCardStatus(
        caloriesPassed = calBand == SquareBand.GREEN,
        stepsPassed = stepBand == SquareBand.GREEN,
        proteinPassed = protBand == SquareBand.GREEN,
        sleepPassed = sleepBand == SquareBand.GREEN,
        trainingPassed = trainBand == SquareBand.GREEN,
    )
}

@Composable
fun DayCard(
    daily: AssembledDaily?,
    modifier: Modifier = Modifier,
    dayKey: String = remember { todayKey() },
    settings: ScoringSettings = remember { ScoringSettings() },
    onMetricClick: (MetricId) -> Unit = {},
) {
    val status = remember(daily, settings, dayKey) {
        computeDayCardStatus(daily, settings, dayKey)
    }

    val dateFormatted = remember(dayKey) {
        runCatching {
            val date = LocalDate.parse(dayKey)
            date.format(DateTimeFormatter.ofPattern("EEEE d MMM", Locale.US))
        }.getOrDefault(dayKey)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardSurface),
        border = BorderStroke(1.dp, BorderDefault)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header row: "5/5 done" (or "All done ✓") on left, today's date on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = status.headerText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextForeground
                )
                Text(
                    text = dateFormatted,
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }

            // 1. Calories row
            val calVal = daily?.caloriesEaten?.get(dayKey)?.roundToInt()
            val calTarget = settings.kcalTarget
            val calText = if (calVal != null && calVal > 0) {
                val diff = calTarget - calVal
                if (diff >= 0) {
                    "%,d / %,d kcal · %,d left".format(calVal, calTarget, diff)
                } else {
                    "%,d / %,d kcal · %,d over".format(calVal, calTarget, -diff)
                }
            } else {
                "— / %,d kcal".format(calTarget)
            }
            val calBand = squareBand(GoalId.CALORIES, daily?.caloriesEaten?.get(dayKey), settings)
            DayCardRow(
                label = "Calories",
                valueText = calText,
                band = calBand,
                onMetricClick = { onMetricClick(MetricId.CALORIES_EATEN) }
            )

            // 2. Steps row
            val stepVal = daily?.steps?.get(dayKey)?.roundToInt()
            val stepTarget = settings.stepTarget
            val stepText = if (stepVal != null && stepVal > 0) {
                val toGo = (stepTarget - stepVal).coerceAtLeast(0)
                if (toGo > 0) {
                    "%,d / %,d · %,d to go".format(stepVal, stepTarget, toGo)
                } else {
                    "%,d / %,d".format(stepVal, stepTarget)
                }
            } else {
                "— / %,d".format(stepTarget)
            }
            val stepBand = squareBand(GoalId.STEPS, daily?.steps?.get(dayKey), settings)
            DayCardRow(
                label = "Steps",
                valueText = stepText,
                band = stepBand,
                onMetricClick = { onMetricClick(MetricId.STEPS) }
            )

            // 3. Protein row
            val protVal = daily?.protein?.get(dayKey)?.roundToInt()
            val protTarget = settings.proteinTarget
            val protText = if (protVal != null && protVal > 0) {
                "%,d / %,d g".format(protVal, protTarget)
            } else {
                "— / %,d g".format(protTarget)
            }
            val protBand = squareBand(GoalId.PROTEIN, daily?.protein?.get(dayKey), settings)
            DayCardRow(
                label = "Protein",
                valueText = protText,
                band = protBand,
                onMetricClick = { onMetricClick(MetricId.PROTEIN) }
            )

            // 4. Sleep row (Morning vs Evening framing)
            val isMorning = remember {
                val zone = daily?.zone ?: ZoneId.systemDefault()
                LocalTime.now(zone).hour < 12
            }
            val sleepLabel = if (isMorning) "Sleep" else "Sleep (tonight's job)"
            val sleepNight = daily?.sleep?.get(dayKey)
            val sleepTargetH = settings.sleepTargetHours.roundToInt()
            val sleepText = if (sleepNight != null && sleepNight.totalMin > 0) {
                val mins = sleepNight.totalMin.roundToInt()
                "${mins / 60}h ${mins % 60}m"
            } else {
                "— / ${sleepTargetH}h"
            }
            val sleepBand = squareBand(GoalId.SLEEP, sleepNight?.totalMin, settings)
            DayCardRow(
                label = sleepLabel,
                valueText = sleepText,
                band = sleepBand,
                onMetricClick = { onMetricClick(MetricId.SLEEP) }
            )

            // 5. Training row
            val zoneDay = daily?.hrZones?.get(dayKey)
            val zoneParts = mutableListOf<String>()
            if (zoneDay != null) {
                if (zoneDay.z2 >= 1.0) zoneParts.add("Z2 ${zoneDay.z2.roundToInt()}m")
                if (zoneDay.z3 >= 1.0) zoneParts.add("Z3 ${zoneDay.z3.roundToInt()}m")
                if (zoneDay.z4 >= 1.0) zoneParts.add("Z4 ${zoneDay.z4.roundToInt()}m")
                if (zoneDay.z5 >= 1.0) zoneParts.add("Z5 ${zoneDay.z5.roundToInt()}m")
            }
            val trainText = if (zoneParts.isNotEmpty()) zoneParts.joinToString(" · ") else "—"
            val trainBand = squareBand(GoalId.EXERCISE, daily?.trainingMinutes?.get(dayKey), settings)
            DayCardRow(
                label = "Training",
                valueText = trainText,
                band = trainBand,
                onMetricClick = null // NO chevron yet
            )

            // 6. Alcohol row (Conditional: absent if null or 0)
            val todayUnits = daily?.alcoholUnits?.get(dayKey) ?: 0.0
            if (todayUnits > 0.0) {
                val monday = runCatching {
                    LocalDate.parse(dayKey).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                }.getOrNull()
                val weekKeys = if (monday != null) {
                    (0 until 7).map { monday.plusDays(it.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE) }
                } else emptyList()
                val weekUnits = weekKeys.sumOf { daily?.alcoholUnits?.get(it) ?: 0.0 }
                val alcText = "%.1fu today · %.1fu this week".format(todayUnits, weekUnits)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Alcohol",
                        fontSize = 12.sp,
                        color = TextForeground,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = alcText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextForeground,
                        textAlign = TextAlign.End
                    )
                }
            }

            // 7. Weight row (shows last weight value + day, appends "· weigh in?" if nothing today)
            val todayWeight = daily?.weight?.get(dayKey)
            val latestWeightEntry = daily?.weight?.filterValues { it > 0.0 }?.entries?.maxByOrNull { it.key }
            val weightAnnotated = buildAnnotatedString {
                if (todayWeight != null && todayWeight > 0.0) {
                    val dayShort = runCatching {
                        LocalDate.parse(dayKey).format(DateTimeFormatter.ofPattern("EEE", Locale.US))
                    }.getOrDefault("")
                    withStyle(SpanStyle(color = TextForeground, fontWeight = FontWeight.Bold)) {
                        append("%.1f kg · %s".format(todayWeight, dayShort))
                    }
                } else if (latestWeightEntry != null && latestWeightEntry.value > 0.0) {
                    val dayShort = runCatching {
                        LocalDate.parse(latestWeightEntry.key).format(DateTimeFormatter.ofPattern("EEE", Locale.US))
                    }.getOrDefault("")
                    withStyle(SpanStyle(color = TextForeground, fontWeight = FontWeight.Bold)) {
                        append("%.1f kg · last: %s".format(latestWeightEntry.value, dayShort))
                    }
                    withStyle(SpanStyle(color = TealHighlight, fontWeight = FontWeight.SemiBold)) {
                        append(" · weigh in?")
                    }
                } else {
                    withStyle(SpanStyle(color = TealHighlight, fontWeight = FontWeight.SemiBold)) {
                        append("weigh in?")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Weight",
                    fontSize = 12.sp,
                    color = TextForeground,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = weightAnnotated,
                    fontSize = 12.sp,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun DayCardRow(
    label: String,
    valueText: String,
    band: SquareBand,
    onMetricClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onMetricClick != null) Modifier.clickable(onClick = onMetricClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextForeground,
            modifier = Modifier.weight(1f, fill = false)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = valueText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextForeground,
                textAlign = TextAlign.End
            )
            ToleranceSquares(band = band)
            if (onMetricClick != null) {
                ChevronRightIcon(
                    modifier = Modifier.size(12.dp),
                    tint = TextMuted
                )
            }
        }
    }
}

@Composable
fun ToleranceSquares(
    band: SquareBand,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val filledCount = when (band) {
            SquareBand.GREEN -> 3
            SquareBand.ORANGE -> 2
            SquareBand.RED -> 1
            SquareBand.GREY -> 0
        }
        val fillColor = when (band) {
            SquareBand.GREEN -> StateLocked
            SquareBand.ORANGE -> StateSlip
            SquareBand.RED -> StateBad
            SquareBand.GREY -> Color.Transparent
        }
        for (i in 0 until 3) {
            if (i < filledCount) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(fillColor)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DayCardEmptyPreview() {
    ScatterFitTheme {
        DayCard(
            daily = null,
            dayKey = "2026-10-14"
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DayCardPartialPreview() {
    ScatterFitTheme {
        val today = "2026-10-14"
        val mockDaily = AssembledDaily(
            steps = mapOf(today to 8400.0),
            caloriesEaten = mapOf(today to 1950.0),
            protein = mapOf(today to 92.0),
            sleep = mapOf(today to SleepNight(today, 460.0, 60.0, 90.0, 260.0, 50.0, "${today}T07:00:00Z")),
            trainingMinutes = mapOf(today to 0.0),
            alcoholUnits = mapOf(today to 2.0),
            weight = mapOf("2026-10-12" to 76.4),
            zoneBounds = ZoneBounds(130, 145, 160, 175),
            zone = ZoneId.of("UTC")
        )
        DayCard(
            daily = mockDaily,
            dayKey = today
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DayCardCompletePreview() {
    ScatterFitTheme {
        val today = "2026-10-14"
        val mockDaily = AssembledDaily(
            steps = mapOf(today to 10500.0),
            caloriesEaten = mapOf(today to 2000.0),
            protein = mapOf(today to 160.0),
            sleep = mapOf(today to SleepNight(today, 490.0, 80.0, 100.0, 260.0, 50.0, "${today}T07:00:00Z")),
            trainingMinutes = mapOf(today to 45.0),
            weight = mapOf(today to 76.4),
            zoneBounds = ZoneBounds(130, 145, 160, 175),
            zone = ZoneId.of("UTC")
        )
        DayCard(
            daily = mockDaily,
            dayKey = today
        )
    }
}
