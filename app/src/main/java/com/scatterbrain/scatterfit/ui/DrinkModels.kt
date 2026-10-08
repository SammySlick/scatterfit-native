package com.scatterbrain.scatterfit.ui

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class DrinkReference(
    val id: String = "",
    val name: String = "",
    val brand: String = "",
    val serving: String = "",
    @SerialName("volume_ml") val volumeMl: Int = 0,
    val abv: Double = 0.0,
    val units: Double = 0.0,
    @SerialName("kcal") val kcals: Int = 0,
    @SerialName("price_gbp") val cost: Double = 0.0,
    val fav: Boolean = false,
    val category: String = ""
)

@Serializable
data class LoggedDrink(
    val id: String = UUID.randomUUID().toString(),
    val drink: DrinkReference,
    val timestamp: Long = 0L,
    val dayKey: String = ""
)

class DrinkStorage(private val context: Context) {
    private val prefs = context.getSharedPreferences("scatterfit_drinks_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadLoggedDrinks(dayKey: String): List<LoggedDrink> {
        val raw = prefs.getString("logged_drinks_$dayKey", null) ?: return emptyList()
        return try {
            json.decodeFromString<List<LoggedDrink>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveLoggedDrinks(dayKey: String, drinks: List<LoggedDrink>) {
        val raw = json.encodeToString(drinks)
        prefs.edit().putString("logged_drinks_$dayKey", raw).apply()
    }

    fun getDrinksForDay(dayKey: String): List<LoggedDrink> = loadLoggedDrinks(dayKey)

    fun saveDrink(loggedDrink: LoggedDrink) {
        val current = loadLoggedDrinks(loggedDrink.dayKey).toMutableList()
        val index = current.indexOfFirst { it.id == loggedDrink.id }
        if (index >= 0) {
            current[index] = loggedDrink
        } else {
            current.add(loggedDrink)
        }
        saveLoggedDrinks(loggedDrink.dayKey, current)
    }

    fun removeDrink(dayKey: String, id: String) {
        val current = loadLoggedDrinks(dayKey).filterNot { it.id == id }
        saveLoggedDrinks(dayKey, current)
    }

    fun clearDay(dayKey: String): List<LoggedDrink> {
        prefs.edit().remove("logged_drinks_$dayKey").apply()
        return emptyList()
    }

    fun loadAll(): Map<String, List<LoggedDrink>> {
        val result = mutableMapOf<String, List<LoggedDrink>>()
        val allEntries = prefs.all
        for (key in allEntries.keys) {
            if (key.startsWith("logged_drinks_")) {
                val dayKey = key.removePrefix("logged_drinks_")
                result[dayKey] = loadLoggedDrinks(dayKey)
            }
        }
        return result
    }

    fun getWeeklyUnits(keys: List<String>): Double {
        return keys.sumOf { k -> loadLoggedDrinks(k).sumOf { it.drink.units } }
    }

    fun getDrinkDaysCount(keys: List<String>): Int {
        return keys.count { k -> loadLoggedDrinks(k).isNotEmpty() }
    }

    fun getWeeklySpent(keys: List<String>): Double {
        return keys.sumOf { k -> loadLoggedDrinks(k).sumOf { it.drink.cost } }
    }

    fun getWeeklyAlcoholKcal(keys: List<String>): Int {
        return keys.sumOf { k -> loadLoggedDrinks(k).sumOf { it.drink.kcals } }
    }
}

object DrinkLibraryParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseJson(jsonString: String): List<DrinkReference> {
        return try {
            json.decodeFromString<List<DrinkReference>>(jsonString)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun loadFromAssets(context: Context, filename: String = "drinks-library.json"): List<DrinkReference> {
        return try {
            val content = context.assets.open(filename).bufferedReader().use { it.readText() }
            parseJson(content)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
