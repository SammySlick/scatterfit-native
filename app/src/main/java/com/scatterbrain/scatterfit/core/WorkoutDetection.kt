package com.scatterbrain.scatterfit.core

import com.scatterbrain.scatterfit.data.ZoneBounds
import kotlin.math.sqrt

const val GAP_TOLERANCE_MIN = 5
const val MIN_SESSION_MIN = 10
const val REST_END_MIN = 15
const val MERGE_GAP_MIN = 30
const val MERGE_MIN_SEGMENT_MIN = 25
const val MAX_SESSION_MIN = 150

data class WorkoutFeatures(
    val startMs: Long,
    val endMs: Long,
    val durationMin: Int,
    val meanHr: Double,
    val peakHr: Double,
    val pctTimeInZ3Plus: Double,   // 0.0..1.0
    val cadenceSpm: Double?,       // steps per minute when steps+distance provided
    val hrSpikiness: Double,       // stddev of per-minute mean HR, bpm
)

enum class WorkoutKind { RUN, WALK, OTHER }

data class WorkoutCandidate(
    val features: WorkoutFeatures,
    val kind: WorkoutKind,
)

private data class RawSpan(
    val startMs: Long,
    val endMs: Long,
    val meetsMinSegmentFloor: Boolean = true,
    val mergeBlocked: Boolean = false,
)

fun detectWorkoutCandidates(
    hrSamples: List<Pair<Long, Double>>,
    bounds: ZoneBounds,
    stepBuckets: List<Pair<Long, Int>> = emptyList(),  // epochMinuteStart -> steps
    mergeGapMin: Int = MERGE_GAP_MIN,
): List<WorkoutCandidate> {
    if (hrSamples.isEmpty()) return emptyList()

    val sortedSamples = hrSamples.sortedBy { it.first }
    val minTimeMs = sortedSamples.first().first
    val maxTimeMs = sortedSamples.last().first

    val minMinute = minTimeMs / 60000L
    val maxMinute = maxTimeMs / 60000L

    // 1. Build per-minute HR series. Hold each sample forward at most GAP_TOLERANCE_MIN minutes.
    // For each minute, determine its covered mean bpm (or null if uncovered).
    val minuteCoverage = mutableMapOf<Long, Double>()
    for (m in minMinute..maxMinute) {
        val minuteStartMs = m * 60000L
        val minuteEndMs = minuteStartMs + 60000L

        val inMinute = sortedSamples.filter { it.first in minuteStartMs until minuteEndMs }
        if (inMinute.isNotEmpty()) {
            minuteCoverage[m] = inMinute.map { it.second }.average()
        } else {
            val lastSample = sortedSamples.lastOrNull { it.first < minuteStartMs }
            if (lastSample != null && (minuteStartMs - lastSample.first) < GAP_TOLERANCE_MIN * 60000L) {
                minuteCoverage[m] = lastSample.second
            }
        }
    }

    // 2. Classify each minute: IN_ZONE if bpm >= bounds.z2, else BELOW
    fun isInZone(m: Long): Boolean {
        val bpm = minuteCoverage[m] ?: return false
        return bpm >= bounds.z2
    }

    // 3. Find raw candidate spans:
    // Candidate start = first minute where following MIN_SESSION_MIN (10) are ALL IN_ZONE.
    // Candidate end = end of last IN_ZONE minute before REST_END_MIN (15) consecutive BELOW/uncovered minutes.
    val rawSpans = mutableListOf<RawSpan>()
    var inCandidate = false
    var candidateStartMin = 0L
    var lastInZoneMin = 0L
    var consecutiveBelow = 0

    var m = minMinute
    while (m <= maxMinute) {
        if (!inCandidate) {
            // Check if following MIN_SESSION_MIN minutes are ALL IN_ZONE
            var allInZone = true
            for (offset in 0 until MIN_SESSION_MIN) {
                if (!isInZone(m + offset)) {
                    allInZone = false
                    break
                }
            }
            if (allInZone) {
                inCandidate = true
                candidateStartMin = m
                lastInZoneMin = m + MIN_SESSION_MIN - 1
                consecutiveBelow = 0
                m += MIN_SESSION_MIN
                continue
            } else {
                m++
            }
        } else {
            if (isInZone(m)) {
                lastInZoneMin = m
                consecutiveBelow = 0
            } else {
                consecutiveBelow++
                if (consecutiveBelow >= REST_END_MIN) {
                    val endMs = (lastInZoneMin + 1) * 60000L
                    val startMs = candidateStartMin * 60000L
                    rawSpans.add(RawSpan(startMs, endMs))
                    inCandidate = false
                    consecutiveBelow = 0
                    m++
                    continue
                }
            }
            m++
        }
    }

    // Trailing BELOW minutes at the end of the series do not end a session unless they reach 15.
    // When the series ends while still in candidate, close at lastInZoneMin.
    if (inCandidate) {
        val endMs = (lastInZoneMin + 1) * 60000L
        val startMs = candidateStartMin * 60000L
        rawSpans.add(RawSpan(startMs, endMs))
    }

    // 4. Discard candidates shorter than 10 minutes, and evaluate whether each meets the MERGE_MIN_SEGMENT_MIN floor
    val qualifiedSpans = rawSpans.mapNotNull { span ->
        val durationMin = ((span.endMs - span.startMs) / 60000L).toInt()
        if (durationMin >= MIN_SESSION_MIN) {
            RawSpan(
                startMs = span.startMs,
                endMs = span.endMs,
                meetsMinSegmentFloor = durationMin >= MERGE_MIN_SEGMENT_MIN
            )
        } else null
    }.toMutableList()

    if (qualifiedSpans.isEmpty()) return emptyList()

    // 5. MERGE: candidates separated by a gap <= mergeGapMin merge into one (start of first, end of last),
    // provided BOTH original candidates are at least MERGE_MIN_SEGMENT_MIN long.
    // Repeat until no more merges. Reject any merged candidate longer than MAX_SESSION_MIN (=150).
    var mergedSpans = qualifiedSpans
    var changed = true
    while (changed) {
        changed = false
        val nextList = mutableListOf<RawSpan>()
        var i = 0
        while (i < mergedSpans.size) {
            val current = mergedSpans[i]
            if (i + 1 < mergedSpans.size) {
                val next = mergedSpans[i + 1]
                val gapMin = ((next.startMs - current.endMs) / 60000L).toInt()
                if (gapMin <= mergeGapMin && current.meetsMinSegmentFloor && next.meetsMinSegmentFloor && !current.mergeBlocked && !next.mergeBlocked) {
                    val candidateMerged = RawSpan(current.startMs, next.endMs, meetsMinSegmentFloor = true)
                    val mergedDuration = ((candidateMerged.endMs - candidateMerged.startMs) / 60000L).toInt()
                    if (mergedDuration <= MAX_SESSION_MIN) {
                        nextList.add(candidateMerged)
                        i += 2
                        changed = true
                        continue
                    } else {
                        // When the union would exceed MAX_SESSION_MIN: add both spans to nextList unchanged
                        // except with mergeBlocked = true, advance i by 2, set changed = true.
                        nextList.add(current.copy(mergeBlocked = true))
                        nextList.add(next.copy(mergeBlocked = true))
                        i += 2
                        changed = true
                        continue
                    }
                }
            }
            nextList.add(current)
            i++
        }
        mergedSpans = nextList
    }

    // Filter out any spans exceeding MAX_SESSION_MIN
    val finalSpans = mergedSpans.filter { span ->
        val durationMin = ((span.endMs - span.startMs) / 60000L).toInt()
        durationMin <= MAX_SESSION_MIN
    }

    // 6 & 7. Compute features and classify
    return finalSpans.map { span ->
        val startMs = span.startMs
        val endMs = span.endMs
        val durationMin = ((endMs - startMs) / 60000L).toInt()

        val spanSamples = sortedSamples.filter { it.first in startMs until endMs }
        val meanHr = if (spanSamples.isNotEmpty()) spanSamples.map { it.second }.average() else 0.0
        val peakHr = if (spanSamples.isNotEmpty()) spanSamples.maxOf { it.second } else 0.0

        // Per-minute stats in span
        val startMin = startMs / 60000L
        val endMin = endMs / 60000L
        val coveredMinutes = (startMin until endMin).mapNotNull { minuteCoverage[it] }

        val pctTimeInZ3Plus = if (coveredMinutes.isNotEmpty()) {
            coveredMinutes.count { it >= bounds.z3 }.toDouble() / coveredMinutes.size
        } else 0.0

        val hrSpikiness = if (coveredMinutes.size > 1) {
            val avg = coveredMinutes.average()
            val variance = coveredMinutes.map { (it - avg) * (it - avg) }.average()
            sqrt(variance)
        } else 0.0

        val initialCandidate = WorkoutCandidate(
            features = WorkoutFeatures(
                startMs = startMs,
                endMs = endMs,
                durationMin = durationMin,
                meanHr = meanHr,
                peakHr = peakHr,
                pctTimeInZ3Plus = pctTimeInZ3Plus,
                cadenceSpm = null,
                hrSpikiness = hrSpikiness
            ),
            kind = WorkoutKind.OTHER
        )

        classifyByCadence(initialCandidate, stepBuckets)
    }
}

fun classifyByCadence(
    candidate: WorkoutCandidate,
    stepBuckets: List<Pair<Long, Int>>
): WorkoutCandidate {
    val durationMin = candidate.features.durationMin
    val startMs = candidate.features.startMs
    val endMs = candidate.features.endMs

    val cadenceSpm = if (stepBuckets.isNotEmpty()) {
        val stepsInSpan = stepBuckets
            .filter { it.first in startMs until endMs }
            .sumOf { it.second }
        if (durationMin > 0) stepsInSpan.toDouble() / durationMin else 0.0
    } else null

    val kind = when {
        cadenceSpm == null -> WorkoutKind.OTHER
        cadenceSpm >= 130.0 -> WorkoutKind.RUN
        cadenceSpm in 60.0..129.9999 -> WorkoutKind.WALK
        else -> WorkoutKind.OTHER
    }

    return candidate.copy(
        features = candidate.features.copy(cadenceSpm = cadenceSpm),
        kind = kind
    )
}
