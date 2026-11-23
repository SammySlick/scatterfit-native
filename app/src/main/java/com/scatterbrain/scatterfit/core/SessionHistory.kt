package com.scatterbrain.scatterfit.core

import com.scatterbrain.scatterfit.ui.LoggedExerciseInSession
import com.scatterbrain.scatterfit.ui.LoggedSetInSession
import com.scatterbrain.scatterfit.ui.SessionRecord
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Reading a finished session back.
 *
 * All pure: records in, strings and counts out. The screens that draw this live
 * in the UI layer and must not re-derive any of it — two implementations of
 * "what does this session add up to" is how the breakdown screen ended up
 * rendering Sleep twice from two different code paths.
 *
 * A note on tone, because it shapes the output: this is a screen for a man
 * eating 2,100 kcal a day. A held weight is STRONGER per kilo (see the
 * deficit-perspective rule), so "held" is neutral. Nothing here returns a
 * judgement — the UI decides colour, and reds never appear.
 */

/** One month's sessions, newest month first, sessions within it newest first. */
data class MonthGroup(
    /** "2026-11" */
    val key: String,
    /** "NOVEMBER" — uppercase section label, as used everywhere else. */
    val label: String,
    val records: List<SessionRecord>,
)

/** A session's headline numbers. Nullable fields are genuinely absent, not zero. */
data class SessionTotals(
    val sessionCount: Int,
    val volumeKg: Double,
    val pbCount: Int,
)

data class WeekTotals(
    val sessionCount: Int,
    val volumeKg: Double,
    val pbCount: Int,
)

/** How a top-set weight compares with the last time this routine was done. */
sealed interface LoadDelta {
    /** Top set went up. Size in kg. */
    data class Up(val kg: Double) : LoadDelta
    /** Top set stayed the same (within 0.01 kg). Never a bad thing. */
    data object Held : LoadDelta
    /** Top set went down. Neutral, never amber, never red. */
    data class Down(val kg: Double) : LoadDelta
}

// ---------------------------------------------------------------- grouping

private const val M = "MMMMM" // placeholder to avoid locale surprises below

/**
 * Groups records into month buckets, newest month first, newest session first
 * inside each. A month with no sessions never appears — an empty "OCTOBER"
 * heading would read as a month you failed.
 */
internal fun groupRecordsByMonth(records: List<SessionRecord>): List<MonthGroup> {
    if (records.isEmpty()) return emptyList()
    return records
        .filter { it.dayKey.isNotBlank() }
        .groupBy { it.dayKey.take(7) } // yyyy-MM
        .toSortedMap(compareByDescending { it })
        .map { (monthKey, monthRecords) ->
            MonthGroup(
                key = monthKey,
                label = monthLabel(monthKey),
                records = monthRecords.sortedWith(
                    compareByDescending<SessionRecord> { it.dayKey }.thenByDescending { it.startedAt }
                ),
            )
        }
}

/** "2026-11" -> "NOVEMBER". Falls back to the raw key if it isn't a month. */
internal fun monthLabel(monthKey: String): String = try {
    YearMonth.parse(monthKey).month.getDisplayName(TextStyle.FULL, Locale.UK).uppercase(Locale.UK)
} catch (_: Exception) {
    monthKey
}

// ---------------------------------------------------------------- counting

/** Sets in one exercise that were actually marked done. */
private fun LoggedExerciseInSession.doneSets(): List<LoggedSetInSession> = sets.filter { it.done }

/** Total done sets across the session. */
internal fun doneSetCount(record: SessionRecord): Int =
    record.exercises.sumOf { it.doneSets().size }

/** Exercises with at least one done set — a listed exercise you skipped isn't "done". */
internal fun doneExerciseCount(record: SessionRecord): Int =
    record.exercises.count { it.doneSets().isNotEmpty() }

/**
 * Volume in kg: weight x reps for every done set that has a weight. Uses reps actually
 * performed, falling back to repsMin then repsMax for sets logged before actual reps were
 * recorded. Kept identical to calculateTotalVolumeKg in the summary screen so the two can
 * never disagree.
 */
internal fun volumeKg(record: SessionRecord): Double =
    record.exercises.sumOf { ex ->
        ex.doneSets().sumOf { s ->
            val w = s.weightKg ?: return@sumOf 0.0
            val reps = (s.actualReps ?: s.repsMin ?: s.repsMax ?: 0)
            w * reps
        }
    }

/** Seconds under tension for timed sets, done only. */
internal fun timeUnderTensionSeconds(record: SessionRecord): Int =
    record.exercises.sumOf { ex -> ex.doneSets().sumOf { it.timeSeconds ?: 0 } }

// ---------------------------------------------------------------- copy

/** 8,420 — grouped, no decimals; 12,480.5 would be noise on a card. */
internal fun formatCount(n: Int): String = String.format(Locale.UK, "%,d", n)

/** 8,420.0 -> "8,420 kg". Keeps one decimal only when it means something. */
internal fun formatVolumeKg(kg: Double): String {
    val rounded = kg.roundToInt()
    return if (abs(kg - rounded) < 0.05) "${formatCount(rounded)} kg"
    else String.format(Locale.UK, "%,.1f kg", kg)
}

/** "22.5 kg" / "22 kg" — never "22.0 kg". Matches formatKg in the summary screen. */
internal fun formatWeightKg(kg: Double): String =
    if (abs(kg - kg.roundToInt()) < 0.05) "${kg.roundToInt()} kg"
    else "${(kg * 10).roundToInt() / 10.0} kg"

/** "0:45" for a timed set. */
internal fun formatSetTime(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/** "8m 40s" for a TUT total. */
internal fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (s == 0) "${m}m" else "${m}m ${s}s"
}

private fun plural(n: Int, singular: String, plural: String = "${singular}s") =
    "$n ${if (n == 1) singular else plural}"

/**
 * The one-line summary under a session name: "58 min · 26 sets · 8,420 kg".
 *
 * Rules that the tests hold: zero values are omitted rather than printed as
 * "0 sets" (a session with no sets recorded shouldn't advertise the fact on
 * every row), a timed-only session shows TUT instead of volume, and singular
 * counts read "1 set".
 */
internal fun sessionSummaryLine(record: SessionRecord): String {
    val parts = mutableListOf<String>()
    record.durationMinutes?.takeIf { it > 0 }?.let { parts += "$it min" }

    val sets = doneSetCount(record)
    if (sets > 0) parts += plural(sets, "set")

    val volume = volumeKg(record)
    val tut = timeUnderTensionSeconds(record)
    // Volume first if there is any: it's the number people compare. A session
    // that is genuinely timed-only (no weighted sets at all) shows TUT.
    if (volume > 0.0) {
        parts += formatVolumeKg(volume)
    } else if (tut > 0) {
        parts += formatDuration(tut) + " TUT"
    }
    return parts.joinToString(" · ").ifEmpty { "No sets logged" }
}

// ---------------------------------------------------------------- muscle groups

/**
 * Done sets per muscle group. Sorted by count desc, with "other" always last:
 * unmatched exercises are custom ones, and they should read as intentional
 * rather than as the headline.
 */
internal fun setsByMuscleGroup(
    record: SessionRecord,
    muscleFor: (exerciseId: String) -> String?,
): List<Pair<String, Int>> {
    val counts = LinkedHashMap<String, Int>()
    for (ex in record.exercises) {
        val done = ex.doneSets().size
        if (done == 0) continue
        val muscle = muscleFor(ex.exerciseId)?.trim()?.takeIf { it.isNotEmpty() } ?: "other"
        val key = muscle.lowercase(Locale.UK)
        counts[key] = (counts[key] ?: 0) + done
    }
    return counts.entries
        .sortedWith(compareBy({ it.key == "other" }, { -it.value }, { it.key }))
        .map { it.key to it.value }
}

/** "Chest 9 · Arms 12 · Back 3 · Other 2" — title-cased, one wrapped line. */
internal fun muscleGroupLine(groups: List<Pair<String, Int>>): String =
    groups.joinToString(" · ") { (muscle, n) ->
        "${muscle.replaceFirstChar { it.uppercase(Locale.UK) }} $n"
    }

// ---------------------------------------------------------------- deltas

private const val EPSILON = 0.01

/** Heaviest done set in an exercise, or null when nothing weighted was done. */
private fun topSetKg(ex: LoggedExerciseInSession): Double? =
    ex.doneSets().mapNotNull { it.weightKg }.maxOrNull()

/** Heaviest done working weight across the session. */
internal fun topSetKg(record: SessionRecord): Double? =
    record.exercises.mapNotNull { topSetKg(it) }.maxOrNull()

/**
 * The most recent earlier session of the same routine. Null when this record
 * has no routine (a one-off can't be compared to anything) or it's the first
 * time the routine was done.
 */
internal fun previousRecordFor(
    record: SessionRecord,
    all: List<SessionRecord>,
): SessionRecord? {
    val routineId = record.routineId ?: return null
    return all
        .asSequence()
        .filter { it.id != record.id }
        .filter { it.routineId == routineId }
        .filter { it.dayKey < record.dayKey }
        .maxWithOrNull(compareBy({ it.dayKey }, { it.startedAt }))
}

/**
 * Top-set movement vs the previous session of the same routine.
 *
 * [Held] is neutral by design: holding a weight while bodyweight falls is
 * getting stronger per kilo, so it must never render as a plateau. [Down] is
 * neutral too — this screen has no business watching anything.
 */
internal fun exerciseLoadDelta(
    record: SessionRecord,
    previous: SessionRecord?,
): LoadDelta? {
    if (previous == null) return null
    val now = topSetKg(record) ?: return null
    val before = topSetKg(previous) ?: return null
    val diff = now - before
    return when {
        abs(diff) < EPSILON -> LoadDelta.Held
        diff > 0 -> LoadDelta.Up((diff * 10).roundToInt() / 10.0)
        else -> LoadDelta.Down((abs(diff) * 10).roundToInt() / 10.0)
    }
}

/** Arrow copy for a per-exercise card: "↑ 2.5 kg", "held", "↓ 2.5 kg". */
internal fun loadDeltaLabel(delta: LoadDelta?): String? = when (delta) {
    null -> null
    LoadDelta.Held -> "held"
    is LoadDelta.Up -> "↑ ${formatWeightKg(delta.kg)}"
    is LoadDelta.Down -> "↓ ${formatWeightKg(delta.kg)}"
}

/**
 * Per-exercise delta: each exercise is compared against the same exercise in
 * the previous session of this routine, not against the session as a whole.
 */
internal fun exerciseDeltas(
    record: SessionRecord,
    previous: SessionRecord?,
): Map<String, LoadDelta?> {
    if (previous == null) return emptyMap()
    val previousByName = previous.exercises.associateBy { it.exerciseId }
    return record.exercises.associate { ex ->
        val now = topSetKg(ex)
        val before = previousByName[ex.exerciseId]?.let { topSetKg(it) }
        val delta = if (now == null || before == null) null else {
            val diff = now - before
            when {
                abs(diff) < EPSILON -> LoadDelta.Held
                diff > 0 -> LoadDelta.Up((diff * 10).roundToInt() / 10.0)
                else -> LoadDelta.Down((abs(diff) * 10).roundToInt() / 10.0)
            }
        }
        ex.exerciseId to delta
    }
}

// ---------------------------------------------------------------- weeks

/**
 * Totals for the calendar week containing [weekStart] (a local Monday-ish key;
 * the caller decides, this just needs seven days from it). Not a trailing
 * seven: "this week" on a Wednesday should mean Mon-Wed, and the app's
 * ConsistencySummary bug is exactly what happens when those two get confused.
 */
internal fun weeklyTotals(
    records: List<SessionRecord>,
    weekStart: String,
): WeekTotals {
    val start = runCatching { LocalDate.parse(weekStart) }.getOrNull()
        ?: return WeekTotals(0, 0.0, 0)
    val end = start.plusDays(6)
    val inWeek = records.filter {
        val d = runCatching { LocalDate.parse(it.dayKey) }.getOrNull() ?: return@filter false
        !d.isBefore(start) && !d.isAfter(end)
    }
    return WeekTotals(
        sessionCount = inWeek.size,
        volumeKg = inWeek.sumOf { volumeKg(it) },
        pbCount = inWeek.sumOf { r ->
            // A PB needs history to be a PB. Against an empty store every set
            // is a "first time logged", which would make the summary line
            // claim seven PBs in a week with one session.
            val earlier = records.filter { it.dayKey < r.dayKey }
            if (earlier.isEmpty()) 0
            else com.scatterbrain.scatterfit.ui.detectPersonalBests(r, earlier).size
        },
    )
}

/** "4 sessions · 12,480 kg · 2 PBs" — quiet line under the THIS WEEK header. */
internal fun weeklySummaryLine(totals: WeekTotals): String {
    if (totals.sessionCount == 0) return "No sessions this week"
    val parts = mutableListOf(plural(totals.sessionCount, "session"))
    if (totals.volumeKg > 0.0) parts += formatVolumeKg(totals.volumeKg)
    if (totals.pbCount > 0) parts += plural(totals.pbCount, "PB")
    return parts.joinToString(" · ")
}
