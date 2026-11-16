package com.scatterbrain.scatterfit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scatterbrain.scatterfit.sync.SyncHub
import com.scatterbrain.scatterfit.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How old the data on this screen is, and a way to refresh it.
 *
 * Background sync means the app is usually showing data it did not just fetch,
 * so silence is wrong: a screen with no age on it claims to be live. Tapping
 * syncs on demand, because "wait up to two hours" is not an answer when you are
 * standing in a café wanting to log lunch against today's real numbers.
 */
@Composable
internal fun SyncAgeLabel(
    isSyncing: Boolean,
    hasData: Boolean,
    modifier: Modifier = Modifier,
) {
    // Re-reads the clock every 30s so "Updated 2m ago" doesn't quietly become a
    // lie while the screen sits open.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            tick++
        }
    }

    val lastSync = remember(tick, isSyncing) { SyncHub.getLastSyncTimestamp() }
    val now = remember(tick, isSyncing) { System.currentTimeMillis() }
    val scope = rememberCoroutineScope()

    val text = syncAgeText(
        isSyncing = isSyncing,
        hasData = hasData,
        lastSyncMs = lastSync,
        nowMs = now,
    )

    val onTap: () -> Unit = {
        if (!isSyncing) scope.launch { SyncHub.syncToday() }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable(enabled = !isSyncing, onClick = onTap),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSyncing) {
            RefreshIcon(modifier = Modifier.size(12.dp), tint = TextMuted)
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            fontSize = 11.sp,
            color = TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The label's words, kept pure so the states can be tested without a device:
 * a sync must never claim to be up to date, and a tick must not reset it.
 */
internal fun syncAgeText(
    isSyncing: Boolean,
    hasData: Boolean,
    lastSyncMs: Long,
    nowMs: Long,
): String = when {
    isSyncing && !hasData -> "Syncing…"
    isSyncing -> "Updating…"
    lastSyncMs <= 0L -> "Not synced yet · tap to sync"
    else -> "Updated ${formatAge(nowMs - lastSyncMs)} · tap to sync"
}
