package com.scatterbrain.scatterfit.data

/**
 * The Today checklist is DERIVED from data, not ticked by events.
 *
 * Rule (settled 2026-10-09 with Sam): a task is complete when the underlying
 * metric data for that day exists — whether it arrived by sync (Health Connect)
 * or by manual entry (weigh-in dialog writes a record; food logger writes
 * FOOD_LOG). One source of truth; auto-complete IS the derivation.
 *
 * Tiering:
 *  - steps: pure indicator — never manually tickable
 *  - weigh-in: derived OR manual write-through (dialog writes a WEIGHT record)
 *  - food: derived from FOOD_LOG writes (until the logger exists, manual)
 *  - weekly check-in: reflective, not measurable — stays manually ticked
 *
 * In-memory-only manual ticks (pre-persistence stopgap) ride alongside via
 * [manualTicks] and OR into the derived state; they die with the process.
 */
data class ChecklistState(
    val stepsToday: Double?,
    val weighInCompleted: Boolean,
    val weighInData: String?,
    val foodLogCompleted: Boolean,
    val foodLogData: String?,
)

object TaskChecklist {

    fun derive(
        daily: AssembledDaily?,
        dayKey: String,
        calorieTarget: Int,
        manualWeighIn: Boolean = false,
        manualFood: Boolean = false,
    ): ChecklistState {
        if (daily == null) {
            return ChecklistState(
                stepsToday = null,
                weighInCompleted = manualWeighIn,
                weighInData = null,
                foodLogCompleted = manualFood,
                foodLogData = null,
            )
        }
        val weight = daily.weight[dayKey]
        val bf = daily.bodyFat[dayKey]
        val weighInData = weight?.let { w ->
            val bfPart = bf?.let { " • ${fmt(it)}% body fat" } ?: ""
            "${fmt(w)}kg$bfPart"
        }
        val kcal = daily.caloriesEaten[dayKey]
        val foodData = kcal?.let { "${fmt(it)} / %,d kcal".format(calorieTarget) }
        return ChecklistState(
            stepsToday = daily.steps[dayKey],
            weighInCompleted = weight != null || manualWeighIn,
            weighInData = weighInData,
            foodLogCompleted = kcal != null || manualFood,
            foodLogData = foodData,
        )
    }

    private fun fmt(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else "%.1f".format(d)
}
