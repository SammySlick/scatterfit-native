package scatterfit.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Port of src/lib/health.ts date helpers (web). Tests are the acceptance
 * criteria: local-day keying, never UTC. Sleep sessions cross midnight, so
 * a session's day is the day its START falls in, in the DEVICE's zone.
 * Do not "simplify" this into UTC — that is the trap the web app avoided.
 */
object DayKeys {

    /** web: dayKey(d) — formats the instant in the given zone as YYYY-MM-DD. */
    fun dayKey(instant: Instant, zone: ZoneId): String =
        LocalDate.ofInstant(instant, zone).toString()

    /** web: todayKey() */
    fun todayKey(now: Instant, zone: ZoneId): String = dayKey(now, zone)

    /** web: lastNDayKeys(n, endKey?) — end-inclusive window, clock-independent. */
    fun lastNDayKeys(
        n: Int,
        zone: ZoneId,
        now: Instant = Instant.now(),
        endKey: String? = null,
    ): List<String> {
        val end = if (endKey != null) LocalDate.parse(endKey)
                  else LocalDate.ofInstant(now, zone)
        return (n - 1 downTo 0).map { end.minusDays(it.toLong()).toString() }
    }

    /** web: mondayOffset(key) — 0 = Monday … 6 = Sunday. */
    fun mondayOffset(key: String): Int =
        (LocalDate.parse(key).dayOfWeek.value + 6) % 7

    /** web: weekStartIndex(keys, i, mode) */
    fun weekStartIndex(keys: List<String>, i: Int, rolling: Boolean): Int {
        if (i !in keys.indices) return 0
        val back = if (rolling) 6 else mondayOffset(keys[i])
        return maxOf(0, i - back)
    }

    /** web: currentWeekKeys(mode) — Mon->today, or the last 7 days. */
    fun currentWeekKeys(
        rolling: Boolean,
        zone: ZoneId,
        now: Instant = Instant.now(),
    ): List<String> {
        val last = lastNDayKeys(7, zone, now)
        return if (rolling) last
        else last.takeLast(mondayOffset(last.last()) + 1)
    }

    /** web: previousWeekKeys(mode) — the full week before the current one. */
    fun previousWeekKeys(
        rolling: Boolean,
        zone: ZoneId,
        now: Instant = Instant.now(),
    ): List<String> =
        if (rolling) lastNDayKeys(14, zone, now).take(7)
        else lastNDayKeys(mondayOffset(todayKey(now, zone)) + 8, zone, now).take(7)
}
