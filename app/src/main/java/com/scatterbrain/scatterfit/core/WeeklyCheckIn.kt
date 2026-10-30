package com.scatterbrain.scatterfit.core

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class CheckInSection { BODY, BIOFEEDBACK, NUTRITION, DIGESTION, TRAINING, SLEEP, SUMMARY }
enum class CheckInScale { SCALE_1_5, YES_NO, NUMBER, TEXT }

data class CheckInRow(
    val id: String,
    val label: String,
    val section: CheckInSection,
    val scale: CheckInScale,
    val perDay: Boolean,          // true = one value per day; false = once for the week
    val noteHint: String? = null, // shown when the row is YES_NO and the answer is "no"
)

val CHECK_IN_ROWS: List<CheckInRow> = listOf(
    // BIOFEEDBACK (all perDay = true)
    CheckInRow("energy", "Energy", CheckInSection.BIOFEEDBACK, CheckInScale.SCALE_1_5, perDay = true),
    CheckInRow("stress", "Stress", CheckInSection.BIOFEEDBACK, CheckInScale.SCALE_1_5, perDay = true),
    CheckInRow("hunger", "Hunger & appetite", CheckInSection.BIOFEEDBACK, CheckInScale.SCALE_1_5, perDay = true),
    CheckInRow("performance", "Strength & performance", CheckInSection.BIOFEEDBACK, CheckInScale.SCALE_1_5, perDay = true),
    CheckInRow("readiness", "Readiness & motivation", CheckInSection.BIOFEEDBACK, CheckInScale.SCALE_1_5, perDay = true),
    CheckInRow("stimulants", "Stimulants", CheckInSection.BIOFEEDBACK, CheckInScale.YES_NO, perDay = true),

    // DIGESTION (perDay = true)
    CheckInRow("doms", "Muscle soreness (DOMS)", CheckInSection.DIGESTION, CheckInScale.YES_NO, perDay = true, noteHint = "Where, and how bad?"),
    CheckInRow("illness", "Signs of illness", CheckInSection.DIGESTION, CheckInScale.YES_NO, perDay = true, noteHint = "Symptoms?"),
    CheckInRow("digestion", "Digestive stress", CheckInSection.DIGESTION, CheckInScale.YES_NO, perDay = true, noteHint = "What happened?"),

    // NUTRITION (perDay = true)
    CheckInRow("adherence", "Adhered to the diet?", CheckInSection.NUTRITION, CheckInScale.YES_NO, perDay = true, noteHint = "If not, why?"),

    // BODY (perDay = false, all NUMBER, all measured Sunday)
    CheckInRow("circ_umbilicus", "Umbilicus", CheckInSection.BODY, CheckInScale.NUMBER, perDay = false),
    CheckInRow("circ_hip", "Hip", CheckInSection.BODY, CheckInScale.NUMBER, perDay = false),
    CheckInRow("circ_upper_thigh", "Upper thigh", CheckInSection.BODY, CheckInScale.NUMBER, perDay = false),
    CheckInRow("circ_mid_thigh", "Mid thigh", CheckInSection.BODY, CheckInScale.NUMBER, perDay = false),
    CheckInRow("circ_chest", "Chest", CheckInSection.BODY, CheckInScale.NUMBER, perDay = false),
    CheckInRow("circ_upper_arm", "Upper arm", CheckInSection.BODY, CheckInScale.NUMBER, perDay = false),
    CheckInRow("circ_shoulders", "Shoulders", CheckInSection.BODY, CheckInScale.NUMBER, perDay = false),

    // SUMMARY (perDay = false)
    CheckInRow("overview", "Weekly overview", CheckInSection.SUMMARY, CheckInScale.TEXT, perDay = false),
)

/** Mean over days that HAVE a value. Never divides by 7. */
fun averageOf(values: List<Double?>): Double? {
    val present = values.filterNotNull()
    if (present.isEmpty()) return null
    return present.sum() / present.size
}

/** Circular mean of minute-of-day values (0..1439), so 23:50 and 00:10 average
 *  to midnight rather than noon. Returns null on empty input. */
fun circularMeanMinutes(values: List<Int>): Int? {
    if (values.isEmpty()) return null
    var sinSum = 0.0
    var cosSum = 0.0
    for (m in values) {
        val angle = 2.0 * Math.PI * (m % 1440) / 1440.0
        sinSum += sin(angle)
        cosSum += cos(angle)
    }
    var meanAngle = atan2(sinSum, cosSum)
    if (meanAngle < 0.0) {
        meanAngle += 2.0 * Math.PI
    }
    return ((meanAngle * 1440.0) / (2.0 * Math.PI)).roundToInt() % 1440
}

/** Count of "yes" answers among non-null entries. */
fun countYes(values: List<Boolean?>): Int =
    values.count { it == true }

/** Score a single 1-5 entry: returns null unless the value parses to 1..5.
 *  Out-of-range values are REJECTED, never clamped. */
fun parseScaleValue(raw: String?): Int? {
    val n = raw?.trim()?.toIntOrNull() ?: return null
    return if (n in 1..5) n else null
}

/** Display string for a row's average, by scale:
 *   SCALE_1_5 -> one decimal, e.g. "3.4"     (both decimals always: "4.0")
 *   YES_NO    -> "N of M", e.g. "3 of 7"
 *   NUMBER    -> one decimal, e.g. "97.5"
 *   TEXT      -> never averaged; return null
 *  No participating days -> "—" (em dash, U+2014). */
fun formatAverage(scale: CheckInScale, values: List<String>): String? = when (scale) {
    CheckInScale.TEXT -> null
    CheckInScale.SCALE_1_5 -> {
        val parsed = values.mapNotNull { parseScaleValue(it)?.toDouble() }
        if (parsed.isEmpty()) {
            "—"
        } else {
            val avg = averageOf(parsed) ?: return "—"
            String.format(Locale.US, "%.1f", avg)
        }
    }
    CheckInScale.YES_NO -> {
        val parsed = values.mapNotNull { v ->
            when (v.trim().lowercase()) {
                "true", "yes", "1" -> true
                "false", "no", "0" -> false
                else -> null
            }
        }
        if (parsed.isEmpty()) {
            "—"
        } else {
            val yes = countYes(parsed)
            "$yes of ${parsed.size}"
        }
    }
    CheckInScale.NUMBER -> {
        val parsed = values.mapNotNull { it.trim().toDoubleOrNull() }
        if (parsed.isEmpty()) {
            "—"
        } else {
            val avg = averageOf(parsed) ?: return "—"
            String.format(Locale.US, "%.1f", avg)
        }
    }
}
