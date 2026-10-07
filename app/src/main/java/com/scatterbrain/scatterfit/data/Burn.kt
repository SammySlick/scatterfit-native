package com.scatterbrain.scatterfit.data

import kotlin.math.roundToInt

/**
 * DERIVED daily burn (calories interview 2026-10-05 23:27, Sam: "Lets use our
 * own"). Burn is no longer the device's number: it is BMR (Mifflin-St Jeor,
 * from personal stats) plus activity computed from that day's HR zone
 * minutes. The Settings activity factor survives only as the FALLBACK for
 * days with no HR zone data.
 *
 * Zone → MET mapping (standard MET values, estimate by design):
 *   Z2 (easy/moderate) 4.5 · Z3 6.5 · Z4 9 · Z5 12
 * kcal/min = MET × 3.5 × kg / 200 (the classic ACSM conversion).
 */
data class BurnConfig(val bmr: Double, val activityFactor: Double, val weightKg: Double)

object Burn {
    val ZONE_MET: Map<String, Double> = mapOf("z2" to 4.5, "z3" to 6.5, "z4" to 9.0, "z5" to 12.0)

    /** kcal burned per minute of a given zone for a given body weight. */
    fun kcalPerMin(zone: String, weightKg: Double): Double = (ZONE_MET.getValue(zone) * 3.5 * weightKg) / 200

    /**
     * One day's burn. Days with ANY HR zone minutes use the derived path
     * (BMR + Σ zone minutes × kcal/min); days without fall back to the
     * settings TDEE (BMR × activity factor), which is a rougher estimate —
     * the UI callout says so explicitly.
     */
    fun derivedBurnKcal(config: BurnConfig, zoneDay: ZoneDay?): Pair<Double, String> {
        val hasZones = zoneDay != null && (zoneDay.z2 > 0 || zoneDay.z3 > 0 || zoneDay.z4 > 0 || zoneDay.z5 > 0)
        if (!hasZones) return (config.bmr * config.activityFactor).roundToInt().toDouble() to "activity-setting"
        val z = zoneDay!!
        val activity =
            z.z2 * kcalPerMin("z2", config.weightKg) +
                z.z3 * kcalPerMin("z3", config.weightKg) +
                z.z4 * kcalPerMin("z4", config.weightKg) +
                z.z5 * kcalPerMin("z5", config.weightKg)
        return (config.bmr + activity).roundToInt().toDouble() to "derived"
    }
}
