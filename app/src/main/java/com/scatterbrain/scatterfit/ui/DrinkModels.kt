package com.scatterbrain.scatterfit.ui

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

fun isValidDrinkUnits(units: Double): Boolean =
    units.isFinite() && units > 0.0 && units < 100.0

fun isValidDrinkKcals(kcals: Int): Boolean =
    kcals in 0 until 5000

fun isValidDrinkCost(cost: Double): Boolean =
    cost.isFinite() && cost >= 0.0 && cost < 1000.0

fun isValidDrinkTimestamp(timestamp: Long): Boolean =
    timestamp > 0L

fun isValidDrinkDetails(units: Double, kcals: Int, cost: Double): Boolean =
    isValidDrinkUnits(units) && isValidDrinkKcals(kcals) && isValidDrinkCost(cost)

fun isValidDrinkDetails(drink: DrinkReference): Boolean =
    isValidDrinkDetails(drink.units, drink.kcals, drink.cost)

fun LoggedDrink.isValid(): Boolean =
    isValidDrinkDetails(drink) && isValidDrinkTimestamp(timestamp)

/**
 * Validates a [LoggedDrink]. If all fields are within bounds, returns [logged] unchanged.
 * If any numeric field is invalid, attempts to field-repair from [libraryLookup] when [logged.drink.id] resolves.
 * If invalid fields cannot be repaired or if timestamp <= 0, returns null (dropped).
 */
fun sanitizeLoggedDrink(
    logged: LoggedDrink,
    libraryLookup: (String) -> DrinkReference? = { null }
): LoggedDrink? {
    if (logged.isValid()) return logged
    if (!isValidDrinkTimestamp(logged.timestamp)) return null

    val lib = if (logged.drink.id.isNotBlank()) libraryLookup(logged.drink.id) else null
    val units = if (isValidDrinkUnits(logged.drink.units)) {
        logged.drink.units
    } else {
        lib?.units?.takeIf { isValidDrinkUnits(it) } ?: return null
    }

    val kcals = if (isValidDrinkKcals(logged.drink.kcals)) {
        logged.drink.kcals
    } else {
        lib?.kcals?.takeIf { isValidDrinkKcals(it) } ?: return null
    }

    val cost = if (isValidDrinkCost(logged.drink.cost)) {
        logged.drink.cost
    } else {
        lib?.cost?.takeIf { isValidDrinkCost(it) } ?: return null
    }

    val repairedDrink = logged.drink.copy(
        units = units,
        kcals = kcals,
        cost = cost,
        name = logged.drink.name.ifBlank { lib?.name ?: "" }
    )
    val repaired = logged.copy(drink = repairedDrink)
    return if (repaired.isValid()) repaired else null
}

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
    val category: String = "",
    val aliases: List<String> = emptyList()
)

@Serializable
data class LoggedDrink(
    val id: String = UUID.randomUUID().toString(),
    val drink: DrinkReference,
    val timestamp: Long = 0L,
    val dayKey: String = ""
)

/** One logged drink -> one ALCOHOL record for the pipeline. Same local-record
 *  convention as meals and weigh-ins: app="app", real timestamps, flat data.
 *  kcal rides in the record so Daily can count it into calories eaten. */
fun LoggedDrink.toHealthRecord(): com.scatterbrain.scatterfit.data.HealthRecord {
    val startIso = java.time.Instant.ofEpochMilli(timestamp).toString()
    return com.scatterbrain.scatterfit.data.HealthRecord(
        app = "app", start = startIso, end = startIso,
        data = kotlinx.serialization.json.buildJsonObject {
            put("name", kotlinx.serialization.json.JsonPrimitive(drink.name.ifEmpty { "Drink" }))
            put("units", kotlinx.serialization.json.JsonPrimitive(drink.units))
            put("kcal", kotlinx.serialization.json.JsonPrimitive(drink.kcals))
            put("kind", kotlinx.serialization.json.JsonPrimitive("alcohol"))
        },
    )
}

class DrinkStorage(
    private val context: Context? = null,
    private val prefs: SharedPreferences? = context?.getSharedPreferences("scatterfit_drinks_prefs", Context.MODE_PRIVATE),
    private var libraryLookup: ((String) -> DrinkReference?)? = null
) {
    constructor(context: Context) : this(
        context = context,
        prefs = context.getSharedPreferences("scatterfit_drinks_prefs", Context.MODE_PRIVATE),
        libraryLookup = null
    )

    private val inMemory = mutableMapOf<String, String>()
    private val inMemoryLibrary = mutableMapOf<String, DrinkReference>()

    private val json = Json {
        ignoreUnknownKeys = true
        allowSpecialFloatingPointValues = true
    }

    private val libraryCache: Map<String, DrinkReference> by lazy {
        if (context != null) {
            DrinkLibraryParser.loadFromAssets(context).associateBy { it.id }
        } else {
            emptyMap()
        }
    }

    fun setLibrary(drinks: List<DrinkReference>) {
        inMemoryLibrary.clear()
        drinks.associateByTo(inMemoryLibrary) { it.id }
    }

    fun setLibraryLookup(lookup: (String) -> DrinkReference?) {
        libraryLookup = lookup
    }

    fun resolveLibraryDrink(id: String): DrinkReference? {
        if (id.isBlank()) return null
        return libraryLookup?.invoke(id)
            ?: inMemoryLibrary[id]
            ?: libraryCache[id]
    }

    fun loadLoggedDrinks(dayKey: String): List<LoggedDrink> {
        val raw = prefs?.getString("logged_drinks_$dayKey", null)
            ?: inMemory["logged_drinks_$dayKey"]
            ?: return emptyList()
        val list = try {
            json.decodeFromString<List<LoggedDrink>>(raw)
        } catch (_: Exception) {
            return emptyList()
        }
        val sanitized = list.mapNotNull { sanitizeLoggedDrink(it, ::resolveLibraryDrink) }
        if (sanitized != list) {
            saveLoggedDrinks(dayKey, sanitized)
        }
        return sanitized
    }

    fun saveLoggedDrinks(dayKey: String, drinks: List<LoggedDrink>) {
        val sanitized = drinks.mapNotNull { sanitizeLoggedDrink(it, ::resolveLibraryDrink) }
        val raw = json.encodeToString(sanitized)
        if (prefs != null) {
            prefs.edit().putString("logged_drinks_$dayKey", raw).apply()
        } else {
            inMemory["logged_drinks_$dayKey"] = raw
        }
    }

    fun getDrinksForDay(dayKey: String): List<LoggedDrink> = loadLoggedDrinks(dayKey)

    fun saveDrink(loggedDrink: LoggedDrink) {
        val sanitized = sanitizeLoggedDrink(loggedDrink, ::resolveLibraryDrink) ?: return
        val current = loadLoggedDrinks(sanitized.dayKey).toMutableList()
        val index = current.indexOfFirst { it.id == sanitized.id }
        if (index >= 0) {
            current[index] = sanitized
        } else {
            current.add(sanitized)
        }
        saveLoggedDrinks(sanitized.dayKey, current)
    }

    fun removeDrink(dayKey: String, id: String) {
        val current = loadLoggedDrinks(dayKey).filterNot { it.id == id }
        saveLoggedDrinks(dayKey, current)
    }

    fun clearDay(dayKey: String): List<LoggedDrink> {
        if (prefs != null) {
            prefs.edit().remove("logged_drinks_$dayKey").apply()
        } else {
            inMemory.remove("logged_drinks_$dayKey")
        }
        return emptyList()
    }

    fun loadAll(): Map<String, List<LoggedDrink>> {
        val result = mutableMapOf<String, List<LoggedDrink>>()
        // In preview/layoutlib environments, prefs.all can be null
        val allKeys = prefs?.all?.keys ?: inMemory.keys
        for (key in allKeys) {
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

    fun getLoggedDays(): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        val allKeys = prefs?.all?.keys ?: inMemory.keys
        for (key in allKeys) {
            if (key.startsWith("logged_drinks_")) {
                val dayKey = key.removePrefix("logged_drinks_")
                val drinks = loadLoggedDrinks(dayKey)
                if (drinks.isNotEmpty()) {
                    result[dayKey] = drinks.size
                }
            }
        }
        return result
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
