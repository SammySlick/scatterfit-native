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
 *  - weigh-in: derived ONLY. If a weight came from proper scales (Hume etc.),
 *    there is nothing for a manual override to add. Manual entry (weigh-in
 *    dialog, once it writes HC records) is just another source of the same
 *    data — it auto-completes through the same derivation. No parallel state.
 *  - food: derived from FOOD_LOG writes (until the logger exists, it just
 *    shows unticked — no fake ticks)
 *  - weekly check-in: reflective, not measurable — stays manually ticked
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
    ): ChecklistState {
        val empty = ChecklistState(
            stepsToday = null,
            weighInCompleted = false,
            weighInData = null,
            foodLogCompleted = false,
            foodLogData = null,
        )
        if (daily == null) return empty
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
            weighInCompleted = weight != null,
            weighInData = weighInData,
            foodLogCompleted = kcal != null,
            foodLogData = foodData,
        )
    }

    private fun fmt(d: Double): String = if (d == d.toLong().toDouble()) d.toLong().toString() else "%.1f".format(d)
}
