package com.scatterbrain.scatterfit.core

import com.scatterbrain.scatterfit.data.AssembledDaily
import com.scatterbrain.scatterfit.data.RecordMethod

/**
 * METRIC REGISTRY — one entry per metric; the SINGLE source of truth for what
 * a metric page shows. Mirrors the web's METRICS[] in lib/analysis/metrics.ts.
 *
 * Adding a metric page = adding an entry here + (if it needs history data)
 * the sync methods it reads. Never a new screen.
 *
 * IDs come from MetricTargets.MetricId (same enum — verdicts and registry
 * always agree on what a metric is).
 */
data class MetricDef(
    val id: MetricId,
    val label: String,
    val unit: String,
    /** true = line, false = bar (Boolean keeps core UI-free; ui maps to MetricChartType) */
    val chart: Boolean,
    /** false = merged as a note on another page, no tab of its own (calories burned) */
    val hasTab: Boolean = true,
    val rolling: Boolean,
    val decimals: Int,
    val axisPolicy: AxisPolicy,
    /** Sync cache methods this metric's history comes from. */
    val methods: List<RecordMethod>,
    /** Extract this metric's value for one day from the assembled daily data. */
    val valueFrom: (AssembledDaily, String) -> Double?,
)

object MetricRegistry {

    val ALL: List<MetricDef> = listOf(
        MetricDef(
            id = MetricId.STEPS, label = "Steps", unit = "", chart = true,
            rolling = true, decimals = 0, axisPolicy = AxisPolicy.ZERO,
            methods = listOf(RecordMethod.STEPS),
            valueFrom = { d, k -> d.steps[k] },
        ),
        MetricDef(
            id = MetricId.WEIGHT, label = "Body Comp", unit = "kg", chart = true,
            rolling = true, decimals = 1, axisPolicy = AxisPolicy.TIGHT,
            methods = listOf(RecordMethod.WEIGHT, RecordMethod.BODY_FAT),
            valueFrom = { d, k -> d.weight[k] },
        ),
        MetricDef(
            id = MetricId.CALORIES_EATEN, label = "Calories", unit = "kcal", chart = false,
            rolling = true, decimals = 0, axisPolicy = AxisPolicy.ZERO,
            methods = listOf(RecordMethod.NUTRITION),
            valueFrom = { d, k -> d.caloriesEaten[k] },
        ),
        MetricDef(
            id = MetricId.PROTEIN, label = "Macros", unit = "g", chart = false,
            rolling = true, decimals = 0, axisPolicy = AxisPolicy.ZERO,
            methods = listOf(RecordMethod.NUTRITION),
            valueFrom = { d, k -> d.protein[k] },
        ),
        MetricDef(
            id = MetricId.ALCOHOL_UNITS, label = "Alcohol", unit = "units", chart = false,
            rolling = false, decimals = 1, axisPolicy = AxisPolicy.ZERO,
            methods = listOf(RecordMethod.NUTRITION),
            valueFrom = { d, k -> d.alcoholUnits[k] },
        ),
        MetricDef(
            id = MetricId.SLEEP, label = "Sleep", unit = "h", chart = false,
            rolling = true, decimals = 1, axisPolicy = AxisPolicy.ZERO,
            methods = listOf(RecordMethod.SLEEP_SESSION),
            valueFrom = { d, k -> d.sleep[k]?.let { it.totalMin / 60.0 } },
        ),
        MetricDef(
            id = MetricId.TRAINING_MINUTES, label = "HR Zones", unit = "min", chart = false,
            rolling = false, decimals = 0, axisPolicy = AxisPolicy.ZERO,
            methods = listOf(RecordMethod.HEART_RATE),
            valueFrom = { d, k -> d.trainingMinutes[k] },
        ),
        MetricDef(
            id = MetricId.RESTING_HR, label = "Resting HR", unit = "bpm", chart = true,
            rolling = false, decimals = 0, axisPolicy = AxisPolicy.RESTING_HR,
            methods = listOf(RecordMethod.RESTING_HEART_RATE),
            valueFrom = { d, k -> d.restingHr[k] },
        ),
        MetricDef(
            id = MetricId.CALORIES_BURNED, label = "Calories burned", unit = "kcal", chart = false,
            hasTab = false, // no tab — merged into the Calories page as the "Burned (est.)" note (web order note)
            rolling = true, decimals = 0, axisPolicy = AxisPolicy.ZERO,
            methods = listOf(RecordMethod.TOTAL_CALORIES_BURNED),
            valueFrom = { d, k -> d.caloriesBurned[k] },
        ),
    )

    fun byId(id: MetricId): MetricDef = ALL.first { it.id == id }

    /** The tabbed metrics, in registry (web) order — drives the chip row. */
    val TABS: List<MetricDef> = ALL.filter { it.hasTab }

    /**
     * Formats a value for hero/list per the registry entry.
     * Null -> em-dash (honest empty — no fake numbers).
     */
    fun format(def: MetricDef, value: Double?): String =
        value?.let { String.format("%.${def.decimals}f%s%s", it, if (def.unit.isEmpty()) "" else " ", def.unit) } ?: "—"
}
