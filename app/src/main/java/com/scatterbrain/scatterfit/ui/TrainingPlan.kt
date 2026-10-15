package com.scatterbrain.scatterfit.ui

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate

fun isTomorrowCardVisible(hour: Int): Boolean = hour >= 19 || hour < 8

class TrainingPlanStorage(context: Context) {
    private val prefs = context.getSharedPreferences("scatterfit_plan_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadPlan(): Map<String, String?> {
        val raw = prefs.getString("weekly_plan", null) ?: return emptyMap()
        return try {
            json.decodeFromString<Map<String, String?>>(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun savePlan(plan: Map<String, String?>) {
        val clean = plan.filterValues { !it.isNullOrBlank() }
        prefs.edit().putString("weekly_plan", json.encodeToString(clean)).apply()
    }

    fun getPlanForDay(dayOfWeek: DayOfWeek): String? {
        val plan = loadPlan()
        return plan[dayOfWeek.name]?.takeIf { it.isNotBlank() }
    }

    fun getPlanForDate(date: LocalDate): String? {
        return getPlanForDay(date.dayOfWeek)
    }
}
