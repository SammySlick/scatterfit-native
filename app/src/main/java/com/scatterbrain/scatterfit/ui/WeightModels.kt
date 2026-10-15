package com.scatterbrain.scatterfit.ui

import android.content.Context
import com.scatterbrain.scatterfit.data.HealthRecord
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant
import java.util.UUID

/** A manual weigh-in from the WeighInDialog. Stored locally (same pattern as
 *  LoggedMeal/FoodStorage) and injected into the pipeline as a HEALTH WEIGHT
 *  record so Daily.weight / bodyFat pick it up with the same merge rules as
 *  scale data (per app per day, LAST reading wins, Hume preferred). */
@Serializable
data class LoggedWeight(
    val id: String = UUID.randomUUID().toString(),
    val kg: Double,
    val bodyFatPct: Double? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val dayKey: String = "",
) {
    fun toHealthRecord(): HealthRecord {
        val startIso = Instant.ofEpochMilli(timestamp).toString()
        val data = buildJsonObject {
            put("weight", JsonPrimitive(kg))
            bodyFatPct?.let { put("percentage", JsonPrimitive(it)) }
            put("name", JsonPrimitive("Weigh-in"))
        }
        return HealthRecord(app = "app", start = startIso, end = startIso, data = data)
    }
}

class WeightStorage(private val context: Context) {
    private val prefs = context.getSharedPreferences("scatterfit_weight_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun saveWeight(entry: LoggedWeight) {
        val current = loadAll().toMutableList()
        current.removeAll { it.id == entry.id }
        current.add(entry)
        prefs.edit().putString("logged_weights", json.encodeToString(current)).apply()
    }

    fun removeWeight(id: String) {
        val current = loadAll().filterNot { it.id == id }
        prefs.edit().putString("logged_weights", json.encodeToString(current)).apply()
    }

    fun loadAll(): List<LoggedWeight> {
        val raw = prefs.getString("logged_weights", null) ?: return emptyList()
        return try {
            json.decodeFromString<List<LoggedWeight>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
