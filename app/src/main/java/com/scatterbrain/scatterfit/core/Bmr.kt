package com.scatterbrain.scatterfit.core

import kotlin.math.roundToInt

/** Mifflin-St Jeor BMR — port of web settings.ts bmrBase/bmrSex. The floor for
 *  any calorie recommendation (a suggestion never lands below resting burn). */
object MifflinStJeor {

    enum class Sex { MALE, FEMALE, NONBINARY, CUSTOM }

    /** custom sex falls back to the explicit bmrFormula setting. */
    fun bmrSex(sex: Sex, bmrFormula: BmrFormula): BmrFormula = when (sex) {
        Sex.MALE -> BmrFormula.MALE
        Sex.FEMALE -> BmrFormula.FEMALE
        else -> bmrFormula
    }

    fun bmrBase(s: ScoringSettings): Int {
        val formula = bmrSex(s.sex, s.bmrFormula)
        val offset = if (formula == BmrFormula.MALE) 5 else -161
        return (10 * s.weightTarget + 6.25 * s.heightCm - 5 * s.ageYears + offset).roundToInt()
    }
}

enum class BmrFormula { MALE, FEMALE }
