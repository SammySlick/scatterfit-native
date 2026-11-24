package com.scatterbrain.scatterfit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.core.groupRecordsByMonth
import com.scatterbrain.scatterfit.core.sessionSummaryLine
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.core.weekKeysFor
import com.scatterbrain.scatterfit.core.weeklySummaryLine
import com.scatterbrain.scatterfit.core.weeklyTotals
import com.scatterbrain.scatterfit.ui.theme.BorderDefault
import com.scatterbrain.scatterfit.ui.theme.DarkCardSurface
import com.scatterbrain.scatterfit.ui.theme.JudgementGood
import com.scatterbrain.scatterfit.ui.theme.SecondaryFill
import com.scatterbrain.scatterfit.ui.theme.TextForeground
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Newest sessions are useful; several hundred are not. */
private const val HISTORY_LIMIT = 100

/**
 * HISTORY tab: every finished session, newest month first.
 *
 * Reads SessionRecord (which carries the exercises), never LoggedSession — that
 * one has no exercise detail, so a list built from it could not show what a
 * session actually adds up to.
 *
 * No horizontal padding here: the parent content column already supplies 16dp.
 */
@Composable
fun SessionHistoryList(
    records: List<SessionRecord>,
    onOpenSession: (SessionRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val capped = remember(records) {
        records
            .filter { it.dayKey.isNotBlank() }
            .sortedWith(compareByDescending<SessionRecord> { it.dayKey }.thenByDescending { it.startedAt })
            .take(HISTORY_LIMIT)
    }

    val groups = remember(capped) { groupRecordsByMonth(capped) }

    val weekLine = remember(capped) {
        val start = weekKeysFor(todayKey()).first()
        weeklySummaryLine(weeklyTotals(capped, start))
    }

    // A PB needs earlier sessions to be a PB. Computed once per list rather than
    // per card, so a screen with 100 rows doesn't re-walk the history 100 times.
    val pbRecordIds = remember(capped) {
        capped.mapNotNull { record ->
            val earlier = capped.filter { it.dayKey < record.dayKey || (it.dayKey == record.dayKey && it.startedAt < record.startedAt) }
            if (earlier.isEmpty()) null
            else if (detectPersonalBests(record, earlier).isNotEmpty()) record.id else null
        }.toSet()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = weekLine,
            fontSize = 12.sp,
            color = TextMuted,
        )

        if (capped.isEmpty()) {
            Text(
                text = "No sessions yet. Log one and it lands here.",
                fontSize = 13.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 24.dp),
            )
            return@Column
        }

        groups.forEach { group ->
            Text(
                text = group.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 10.dp),
            )

            group.records.forEach { record ->
                SessionHistoryCard(
                    record = record,
                    hasPb = pbRecordIds.contains(record.id),
                    onClick = { onOpenSession(record) },
                )
            }
        }
    }
}

private val historyDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK)

private fun historyDateLabel(dayKey: String): String = try {
    LocalDate.parse(dayKey).format(historyDateFormatter)
} catch (_: Exception) {
    dayKey
}

@Composable
private fun SessionHistoryCard(
    record: SessionRecord,
    hasPb: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCardSurface)
            .border(BorderStroke(1.dp, BorderDefault), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = historyDateLabel(record.dayKey),
                fontSize = 12.sp,
                color = TextMuted,
            )
            if (hasPb) {
                Text(
                    text = "NEW PB",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = JudgementGood,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(SecondaryFill)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Text(
            text = record.name,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = sessionSummaryLine(record),
            fontSize = 12.sp,
            color = TextMuted,
        )
    }
}
