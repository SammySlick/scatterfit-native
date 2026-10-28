package com.scatterbrain.scatterfit.data

import android.content.Context
import android.content.SharedPreferences
import com.scatterbrain.scatterfit.core.WorkoutKind
import com.scatterbrain.scatterfit.core.classifyByCadence
import com.scatterbrain.scatterfit.core.dayKey
import com.scatterbrain.scatterfit.core.detectWorkoutCandidates
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.ZoneId

@Serializable
data class WorkoutProposal(
    val id: String,                 // stable uuid
    val dayKey: String,
    val startMs: Long,
    val endMs: Long,
    val durationMin: Int,
    val kind: String,               // "RUN" | "WALK" | "OTHER"
    val proposedType: String,       // "Run" | "Walk" | "Football" | "Workout" — first guess
    val meanHr: Double,
    val peakHr: Double,
    val pctTimeInZ3Plus: Double,
    val cadenceSpm: Double?,
    val createdAtMs: Long,
    val dismissed: Boolean = false,
)

fun mapProposedType(kind: WorkoutKind): String = when (kind) {
    WorkoutKind.RUN -> "Run"
    WorkoutKind.WALK -> "Walk"
    WorkoutKind.OTHER -> "Workout"
}

fun mapProposedType(kind: String): String = when (kind.uppercase()) {
    "RUN" -> "Run"
    "WALK" -> "Walk"
    else -> "Workout"
}

fun hasSignificantOverlap(
    startA: Long,
    endMsA: Long,
    startB: Long,
    endMsB: Long,
    thresholdRatio: Double = 0.5,
): Boolean {
    val durA = endMsA - startA
    val durB = endMsB - startB
    if (durA <= 0 || durB <= 0) return false
    val overlapStart = maxOf(startA, startB)
    val overlapEnd = minOf(endMsA, endMsB)
    val overlapMs = maxOf(0L, overlapEnd - overlapStart)
    if (overlapMs <= 0) return false
    val ratioA = overlapMs.toDouble() / durA.toDouble()
    val ratioB = overlapMs.toDouble() / durB.toDouble()
    return ratioA >= thresholdRatio || ratioB >= thresholdRatio
}

class WorkoutProposalsStorage(
    private val context: Context? = null,
    private val prefs: SharedPreferences? = context?.getSharedPreferences("scatterfit_workout_proposals", Context.MODE_PRIVATE)
) {
    private val inMemory = mutableMapOf<String, String>()
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val PROPOSAL_EXPIRY_DAYS = 7
        const val PREFS_KEY = "workout_proposals"
    }

    fun load(): List<WorkoutProposal> {
        val raw = prefs?.getString(PREFS_KEY, null)
            ?: inMemory[PREFS_KEY]
            ?: return emptyList()
        return try {
            json.decodeFromString<List<WorkoutProposal>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(proposals: List<WorkoutProposal>) {
        val raw = json.encodeToString(proposals)
        if (prefs != null) {
            prefs.edit().putString(PREFS_KEY, raw).apply()
        } else {
            inMemory[PREFS_KEY] = raw
        }
    }

    fun add(proposal: WorkoutProposal) {
        val current = load().toMutableList()
        val index = current.indexOfFirst { it.id == proposal.id }
        if (index >= 0) {
            current[index] = proposal
        } else {
            current.add(proposal)
        }
        save(current)
    }

    fun dismiss(id: String) {
        val current = load()
        val updated = current.map {
            if (it.id == id) it.copy(dismissed = true) else it
        }
        save(updated)
    }

    fun remove(id: String) {
        val current = load()
        val updated = current.filterNot { it.id == id }
        if (updated.size != current.size) {
            save(updated)
        }
    }

    fun pruneExpired(nowMs: Long = System.currentTimeMillis()): List<WorkoutProposal> {
        val expiryMs = PROPOSAL_EXPIRY_DAYS * 24L * 3600L * 1000L
        val threshold = nowMs - expiryMs
        val current = load()
        val kept = current.filter { it.createdAtMs >= threshold }
        if (kept.size != current.size) {
            save(kept)
        }
        return kept
    }
}

object WorkoutProposalDetector {
    suspend fun processSyncProposals(
        hrRecords: List<HealthRecord>,
        stepRecords: List<HealthRecord>,
        exerciseRecords: List<HealthRecord>,
        storage: WorkoutProposalsStorage,
        nowMs: Long = System.currentTimeMillis(),
        bounds: ZoneBounds = Zones.DEFAULT_ZONES,
        zone: ZoneId = ZoneId.systemDefault(),
        rawStepReader: (suspend (fromMs: Long, toMs: Long) -> List<HealthRecord>)? = null,
    ): List<WorkoutProposal> {
        storage.pruneExpired(nowMs)

        val window48h = nowMs - 48L * 3600L * 1000L
        val recentHr = hrRecords.filter { r ->
            (parseMillis(r.start) ?: 0L) >= window48h
        }
        val recentSteps = stepRecords.filter { r ->
            (parseMillis(r.start) ?: 0L) >= window48h
        }
        val recentExercise = exerciseRecords.filter { r ->
            (parseMillis(r.start) ?: 0L) >= window48h
        }

        val hrSamples = Zones.hrSamples(recentHr)
        val stepBuckets = Zones.stepBucketsFromRecords(recentSteps)
        val candidates = detectWorkoutCandidates(hrSamples, bounds, stepBuckets)

        val existingProposals = storage.load()
        val created = mutableListOf<WorkoutProposal>()

        for (candidate in candidates) {
            val start = candidate.features.startMs
            val end = candidate.features.endMs
            val dKey = dayKey(start, zone)

            // 1. Skip if stored proposal already overlaps its span (same day, overlapping by >= 50% of either span)
            val overlapsProposal = existingProposals.any { prop ->
                prop.dayKey == dKey && hasSignificantOverlap(start, end, prop.startMs, prop.endMs, 0.5)
            } || created.any { prop ->
                prop.dayKey == dKey && hasSignificantOverlap(start, end, prop.startMs, prop.endMs, 0.5)
            }
            if (overlapsProposal) continue

            // 2. Skip if an ExerciseSessionRecord in the same 48h window overlaps it by >= 50%
            val overlapsExercise = recentExercise.any { r ->
                val exStart = parseMillis(r.start) ?: return@any false
                val exEnd = parseMillis(r.end) ?: (exStart + candidate.features.durationMin * 60000L)
                hasSignificantOverlap(start, end, exStart, exEnd, 0.5)
            }
            if (overlapsExercise) continue

            // 3. For each candidate, do a bounded raw STEPS read covering [startMs - 5min, endMs + 5min]
            val classified = if (rawStepReader != null) {
                try {
                    val rawFrom = start - 5 * 60000L
                    val rawTo = end + 5 * 60000L
                    val rawRecords = rawStepReader(rawFrom, rawTo)
                    val rawBuckets = Zones.stepBucketsFromRecords(rawRecords)
                    classifyByCadence(candidate, rawBuckets)
                } catch (t: Throwable) {
                    android.util.Log.w("WorkoutDetection", "Raw steps read failed for candidate", t)
                    candidate
                }
            } else {
                candidate
            }

            val proposal = WorkoutProposal(
                id = java.util.UUID.randomUUID().toString(),
                dayKey = dKey,
                startMs = start,
                endMs = end,
                durationMin = classified.features.durationMin,
                kind = classified.kind.name,
                proposedType = mapProposedType(classified.kind),
                meanHr = classified.features.meanHr,
                peakHr = classified.features.peakHr,
                pctTimeInZ3Plus = classified.features.pctTimeInZ3Plus,
                cadenceSpm = classified.features.cadenceSpm,
                createdAtMs = nowMs,
                dismissed = false
            )
            storage.add(proposal)
            created.add(proposal)
        }

        return created
    }
}
