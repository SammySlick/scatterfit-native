package com.scatterbrain.scatterfit.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.Instant

/**
 * DAY & WEEK KEYS — Kotlin port of web `src/lib/daykeys.ts` (consolidation
 * round 2026-10-06). Accepted per Native Foundations: web vitest cases are the
 * acceptance tests; green in JUnit means ported.
 *
 * Local-timezone arithmetic only: every helper builds dates in a passed zone
 * (default = device zone) so a UTC round-trip can never shift or skip a day.
 * The web version achieves this with local-noon Date objects; Kotlin gets
 * LocalDate natively, which is DST-immune by construction.
 *
 * Keys are always `YYYY-MM-DD` strings compared lexically.
 */
typealias WeekMode = String // "monday" | "rolling"

/** Local-date key for an instant in a zone. Web: `dayKey(date)` via en-CA formatting. */
fun dayKey(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    LocalDate.ofInstant(Instant.ofEpochMilli(epochMillis), zone).toString()

/** Local-date key for a date string parsed at a zone — mirrors web dayKey(String). */
fun dayKeyFromIso(iso: String, zone: ZoneId = ZoneId.systemDefault()): String =
    LocalDate.parse(iso).toString()

/** Today, per the device clock in the device zone. The ONLY wall-clock read in
 *  the codebase — everything else derives windows from a passed anchor. */
fun todayKey(zone: ZoneId = ZoneId.systemDefault()): String =
    LocalDate.now(zone).toString()

/** The last [n] day keys ending at [endKey] (defaults to today). */
fun lastNDayKeys(n: Int, endKey: String? = null, zone: ZoneId = ZoneId.systemDefault()): List<String> {
    val end = endKey?.let { LocalDate.parse(it) } ?: LocalDate.now(zone)
    return (n - 1 downTo 0).map { end.minusDays(it.toLong()).toString() }
}

private fun mondayOffset(key: String): Int =
    LocalDate.parse(key).dayOfWeek.value - DayOfWeek.MONDAY.value // Mon=0 .. Sun=6

/** Start index of the week containing keys[i] (keys are consecutive days). */
fun weekStartIndex(keys: List<String>, i: Int, mode: WeekMode): Int {
    val back = if (mode == "rolling") 6 else mondayOffset(keys.getOrElse(i) { "" })
    return maxOf(0, i - back)
}

/** Days of the current week up to today (Mon→today, or the last 7 days). */
fun currentWeekKeys(mode: WeekMode, zone: ZoneId = ZoneId.systemDefault()): List<String> {
    val last = lastNDayKeys(7, zone = zone)
    return if (mode == "rolling") last
    else last.takeLast(mondayOffset(last.lastOrNull() ?: todayKey(zone)) + 1)
}

/** The week (per mode) containing [anchor], ending at [anchor]. Monday mode
 *  returns the calendar week even when the anchor is mid-week (future days
 *  included) — callers filter with `<= anchor` when needed. */
fun weekKeysEndingAt(anchor: String, mode: WeekMode): List<String> {
    val a = LocalDate.parse(anchor)
    val n = if (mode == "rolling") 7 else mondayOffset(anchor) + 1
    return (n - 1 downTo 0).map { a.minusDays(it.toLong()).toString() }
}

/** The day before [key], computed in local date arithmetic — no UTC round-trip,
 *  so a BST/GMT boundary can never skip a day (L audit 2026-10-06, blocker). */
fun dayBefore(key: String): String = LocalDate.parse(key).minusDays(1).toString()

/** The week before the one containing [anchor] (defaults to today). Anchored so
 *  callers/tests can pin the clock. */
fun previousWeekKeys(mode: WeekMode, anchor: String? = null, zone: ZoneId = ZoneId.systemDefault()): List<String> {
    val today = anchor ?: todayKey(zone)
    val current = weekKeysEndingAt(today, mode)
    return weekKeysEndingAt(dayBefore(current.firstOrNull() ?: today), mode)
}

/** The FULL calendar week (Mon→Sun) containing [anchor]. Unlike
 *  weekKeysEndingAt this never truncates at the anchor: alcohol tiles need the
 *  upcoming days of the week too. */
fun weekKeysFor(anchor: String): List<String> {
    val a = LocalDate.parse(anchor)
    val offset = mondayOffset(anchor)
    val before = (offset downTo 0).map { a.minusDays(it.toLong()).toString() }
    val after = (1..(6 - offset)).map { a.plusDays(it.toLong()).toString() }
    return before + after
}

/** Day key [n] days after (or before, negative) a day key. Local-day
 *  arithmetic, never UTC. */
fun addDaysKey(key: String, n: Int): String =
    LocalDate.parse(key).plusDays(n.toLong()).toString()
