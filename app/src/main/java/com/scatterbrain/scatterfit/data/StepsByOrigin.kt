package com.scatterbrain.scatterfit.data

import java.time.ZoneId
import kotlin.math.roundToLong

/** Today's steps split by the app that wrote them.
 *
 *  Exists for one question: "Hume says I walked 8k, the app says 1.5k". A total
 *  answers nothing — you need to see which origins are in Health Connect at all,
 *  what each one claims for the day, and which one won. Live 2026-11-23: Hume and
 *  Samsung both wrote daily step records, the dedupe key was method+start, so
 *  Samsung's record overwrote Hume's in the cache before the max-across-origins
 *  rule ever ran. The total was Samsung's number, not the max of both.
 *
 *  Per-origin totals use the SAME rule as the daily pipeline (identical windows
 *  within an origin deduped, summed) so this panel can't disagree with the
 *  number on the Today screen. */
/** One origin's claim for the day, plus whether it won (was the max). */
data class StepsOriginRow(
    val app: String,
    val name: String,
    val steps: Long,
    val won: Boolean,
)

fun stepsByOriginForDay(
    records: List<HealthRecord>,
    zone: ZoneId,
    dayKey: String,
): List<StepsOriginRow> {
    val perDay = Normalise.perOriginDaily(records, zone) { r ->
        Normalise.num(r.data?.get("count"))
    }
    val bySrc = perDay[dayKey] ?: return emptyList()
    val winner = bySrc.maxByOrNull { it.value }?.key
    return bySrc.entries
        .sortedByDescending { it.value }
        .map { (app, total) ->
            StepsOriginRow(
                app = app,
                name = sourceName(app),
                steps = total.roundToLong(),
                won = app == winner,
            )
        }
}
