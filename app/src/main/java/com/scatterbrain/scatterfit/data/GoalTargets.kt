package com.scatterbrain.scatterfit.data

import com.scatterbrain.scatterfit.core.GoalId
import com.scatterbrain.scatterfit.core.GoalPreset
import com.scatterbrain.scatterfit.core.UnitSystem
import com.scatterbrain.scatterfit.core.formatWeight
import java.util.Locale
import kotlin.math.roundToInt

/** Primary metric per GoalPreset:
 *  FAT_LOSS -> BODY_FAT, MAINTENANCE -> WEIGHT, BUILD_MUSCLE -> WEIGHT,
 *  FITNESS -> RESTING_HEART_RATE (RHR_TREND) (fallback WEIGHT). */
fun primaryMetricFor(preset: GoalPreset): GoalId = when (preset) {
    GoalPreset.FAT_LOSS -> GoalId.BODY_FAT
    GoalPreset.MAINTENANCE -> GoalId.WEIGHT
    GoalPreset.BUILD_MUSCLE -> GoalId.WEIGHT
    GoalPreset.FITNESS -> GoalId.RHR_TREND
    else -> GoalId.WEIGHT
}

data class GoalTargets(val targets: Map<GoalId, Double>)

/** Demo defaults (BODY_FAT 17.0, WEIGHT 84.0, RESTING_HEART_RATE 52.0)
 *  with a load()/save() seam for the future goals screen. */
object GoalTargetsStore {
    val DEFAULT_TARGETS = GoalTargets(
        mapOf(
            GoalId.BODY_FAT to 17.0,
            GoalId.WEIGHT to 84.0,
            GoalId.RHR_TREND to 52.0,
        )
    )

    private var currentTargets: GoalTargets = DEFAULT_TARGETS

    fun load(): GoalTargets = currentTargets

    fun save(targets: GoalTargets) {
        currentTargets = targets
    }

    fun reset() {
        currentTargets = DEFAULT_TARGETS
    }

    fun targetFor(metric: GoalId): Double? = load().targets[metric]

    fun targetFor(preset: GoalPreset): Double? = targetFor(primaryMetricFor(preset))
}

/** Unit-aware formatting:
 *  BODY_FAT -> "26.3%", WEIGHT -> formatWeight(kg) (UnitSystem-aware),
 *  RESTING_HEART_RATE (RHR_TREND) -> "52 bpm". */
fun formatGoalValue(
    value: Double?,
    metric: GoalId,
    unitSystem: UnitSystem = UnitSystem.METRIC,
): String {
    if (value == null) return "—"
    return when (metric) {
        GoalId.BODY_FAT -> "${"%.1f".format(Locale.US, value)}%"
        GoalId.WEIGHT -> formatWeight(value, unitSystem)
        GoalId.RHR_TREND -> "${value.roundToInt()} bpm"
        else -> "%.1f".format(Locale.US, value)
    }
}

/** Pure function returning the latest value for the primary metric from the day's records.
 *  Empty / no valid data -> null. */
fun currentPrimaryMetricValue(records: List<HealthRecord>, metric: GoalId): Double? {
    if (records.isEmpty()) return null
    return when (metric) {
        GoalId.BODY_FAT -> {
            records
                .filter { !Records.isBlockedApp(it) && (it.app ?: "") !in Normalise.IGNORED_WEIGHT_APPS }
                .mapNotNull { r ->
                    val v = Normalise.num(r.data?.get("percentage"))
                    if (v != null && v > 0 && v <= 60) {
                        val t = parseMillis(r.start) ?: 0L
                        t to v
                    } else null
                }
                .maxByOrNull { it.first }
                ?.second
        }
        GoalId.WEIGHT -> {
            records
                .filter { !Records.isBlockedApp(it) && (it.app ?: "") !in Normalise.IGNORED_WEIGHT_APPS }
                .mapNotNull { r ->
                    val v = Normalise.normaliseWeightKg(r.data)
                    if (v != null && v in 40.0..200.0) {
                        val t = parseMillis(r.start) ?: 0L
                        t to v
                    } else null
                }
                .maxByOrNull { it.first }
                ?.second
        }
        GoalId.RHR_TREND -> {
            records
                .filter { !Records.isBlockedApp(it) }
                .mapNotNull { r ->
                    val v = Normalise.num(r.data?.get("beatsPerMinute") ?: r.data?.get("bpm") ?: r.data?.get("value"))
                    if (v != null && v > 0) {
                        val t = parseMillis(r.start) ?: 0L
                        t to v
                    } else null
                }
                .maxByOrNull { it.first }
                ?.second
        }
        else -> null
    }
}

fun currentPrimaryMetricValue(records: List<HealthRecord>, preset: GoalPreset): Double? =
    currentPrimaryMetricValue(records, primaryMetricFor(preset))
