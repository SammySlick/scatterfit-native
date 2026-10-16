package com.scatterbrain.scatterfit.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.data.HealthRecord
import com.scatterbrain.scatterfit.sync.SyncHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant
import java.util.UUID

@Serializable
data class LoggedMeal(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val kcal: Double,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val dayKey: String = "",
) {
    fun toHealthRecord(): HealthRecord {
        val startIso = Instant.ofEpochMilli(timestamp).toString()
        val data = buildJsonObject {
            put("name", JsonPrimitive(name.ifBlank { "Meal" }))
            put("meal", JsonPrimitive(name.ifBlank { "Meal" }))
            put("kcal", JsonPrimitive(kcal))
            protein?.let { put("protein", JsonPrimitive(it)) }
            carbs?.let { put("carbs", JsonPrimitive(it)) }
            fat?.let { put("fat", JsonPrimitive(it)) }
        }
        return HealthRecord(
            app = "app",
            start = startIso,
            end = startIso,
            data = data
        )
    }
}

data class MealPreset(
    val name: String,
    val kcal: Double,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val lastUsedTimestamp: Long,
)

fun deriveMealPresets(meals: List<LoggedMeal>, limit: Int = 8): List<MealPreset> {
    return meals
        .sortedByDescending { it.timestamp }
        .distinctBy { Triple(it.name.trim().lowercase(), it.kcal, Triple(it.protein, it.carbs, it.fat)) }
        .take(limit)
        .map {
            MealPreset(
                name = it.name,
                kcal = it.kcal,
                protein = it.protein,
                carbs = it.carbs,
                fat = it.fat,
                lastUsedTimestamp = it.timestamp
            )
        }
}

class FoodStorage(private val context: Context) {
    private val prefs = context.getSharedPreferences("scatterfit_food_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadLoggedMeals(dayKey: String): List<LoggedMeal> {
        val raw = prefs.getString("logged_meals_$dayKey", null) ?: return emptyList()
        return try {
            json.decodeFromString<List<LoggedMeal>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveLoggedMeals(dayKey: String, meals: List<LoggedMeal>) {
        val raw = json.encodeToString(meals)
        prefs.edit().putString("logged_meals_$dayKey", raw).apply()
    }

    fun getMealsForDay(dayKey: String): List<LoggedMeal> = loadLoggedMeals(dayKey)

    fun saveMeal(meal: LoggedMeal) {
        val current = loadLoggedMeals(meal.dayKey).toMutableList()
        val index = current.indexOfFirst { it.id == meal.id }
        if (index >= 0) {
            current[index] = meal
        } else {
            current.add(meal)
        }
        saveLoggedMeals(meal.dayKey, current)
    }

    fun removeMeal(dayKey: String, id: String) {
        val current = loadLoggedMeals(dayKey).filterNot { it.id == id }
        saveLoggedMeals(dayKey, current)
    }

    fun loadAll(): Map<String, List<LoggedMeal>> {
        val result = mutableMapOf<String, List<LoggedMeal>>()
        val allEntries = prefs.all ?: return emptyMap()
        for (key in allEntries.keys) {
            if (key.startsWith("logged_meals_")) {
                val dayKey = key.removePrefix("logged_meals_")
                result[dayKey] = loadLoggedMeals(dayKey)
            }
        }
        return result
    }

    fun getAllMeals(): List<LoggedMeal> {
        return loadAll().values.flatten()
    }

    fun getLoggedDays(): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        val allEntries = prefs.all ?: return emptyMap()
        for (key in allEntries.keys) {
            if (key.startsWith("logged_meals_")) {
                val dayKey = key.removePrefix("logged_meals_")
                val meals = loadLoggedMeals(dayKey)
                if (meals.isNotEmpty()) {
                    result[dayKey] = meals.size
                }
            }
        }
        return result
    }
}

class FoodTrackerViewModel(
    val storage: FoodStorage? = null,
    initialDayKey: String = todayKey(),
) : ViewModel() {
    var activeDayKey: String = initialDayKey
        private set
    val inMemoryStorage = mutableMapOf<String, MutableList<LoggedMeal>>()

    var mealName by mutableStateOf("")
    var kcalInput by mutableStateOf("")
    var proteinInput by mutableStateOf("")
    var carbsInput by mutableStateOf("")
    var fatInput by mutableStateOf("")

    private val _mealsForDay = MutableStateFlow<List<LoggedMeal>>(emptyList())
    val mealsForDay: StateFlow<List<LoggedMeal>> = _mealsForDay.asStateFlow()

    private val _presets = MutableStateFlow<List<MealPreset>>(emptyList())
    val presets: StateFlow<List<MealPreset>> = _presets.asStateFlow()

    init {
        setDayKey(initialDayKey)
        refreshPresets()
    }

    fun setDayKey(dayKey: String) {
        activeDayKey = dayKey
        _mealsForDay.value = (storage?.getMealsForDay(dayKey) ?: inMemoryStorage[dayKey] ?: emptyList())
            .sortedByDescending { it.timestamp }
    }

    fun refreshPresets() {
        val allMeals = storage?.getAllMeals() ?: inMemoryStorage.values.flatten()
        _presets.value = deriveMealPresets(allMeals, limit = 8)
    }

    fun applyPreset(preset: MealPreset) {
        mealName = preset.name
        kcalInput = if (preset.kcal % 1.0 == 0.0) preset.kcal.toInt().toString() else preset.kcal.toString()
        proteinInput = preset.protein?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""
        carbsInput = preset.carbs?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""
        fatInput = preset.fat?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() } ?: ""
    }

    fun saveCurrentMeal(): Boolean {
        val kcal = kcalInput.toDoubleOrNull() ?: return false
        val zone = java.time.ZoneId.systemDefault()
        val timestamp = if (activeDayKey == todayKey(zone)) {
            System.currentTimeMillis()
        } else {
            val date = runCatching { java.time.LocalDate.parse(activeDayKey) }.getOrDefault(java.time.LocalDate.now(zone))
            date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        }
        val meal = LoggedMeal(
            name = mealName.trim(),
            kcal = kcal,
            protein = proteinInput.toDoubleOrNull(),
            carbs = carbsInput.toDoubleOrNull(),
            fat = fatInput.toDoubleOrNull(),
            timestamp = timestamp,
            dayKey = activeDayKey
        )
        if (storage != null) {
            storage.saveMeal(meal)
            syncStorageToPipeline()
        } else {
            val list = inMemoryStorage.getOrPut(activeDayKey) { mutableListOf() }
            list.add(meal)
        }

        mealName = ""
        kcalInput = ""
        proteinInput = ""
        carbsInput = ""
        fatInput = ""

        setDayKey(activeDayKey)
        refreshPresets()
        return true
    }

    fun deleteMeal(id: String) {
        if (storage != null) {
            storage.removeMeal(activeDayKey, id)
            syncStorageToPipeline()
        } else {
            inMemoryStorage[activeDayKey]?.removeAll { it.id == id }
        }
        setDayKey(activeDayKey)
        refreshPresets()
    }

    private fun syncStorageToPipeline() {
        val allMeals = storage?.getAllMeals() ?: emptyList()
        val records = allMeals.map { it.toHealthRecord() }
        SyncHub.updateLocalFoodLog(records)
    }
}
