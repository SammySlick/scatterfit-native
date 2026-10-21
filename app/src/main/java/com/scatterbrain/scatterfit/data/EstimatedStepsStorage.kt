package com.scatterbrain.scatterfit.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class EstimatedStepEntry(
    val id: String = UUID.randomUUID().toString(),
    val dayKey: String,
    val steps: Int,
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

class EstimatedStepsStorage(private val prefs: SharedPreferences? = null) {
    constructor(context: Context) : this(
        context.getSharedPreferences("scatterfit_estimated_steps_prefs", Context.MODE_PRIVATE)
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val inMemory = mutableMapOf<String, MutableList<EstimatedStepEntry>>()

    fun loadEntries(dayKey: String): List<EstimatedStepEntry> {
        if (prefs == null) {
            return inMemory[dayKey]?.toList() ?: emptyList()
        }
        val raw = prefs.getString("estimated_steps_$dayKey", null) ?: return emptyList()
        return try {
            json.decodeFromString<List<EstimatedStepEntry>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveEntry(entry: EstimatedStepEntry) {
        if (prefs == null) {
            val list = inMemory.getOrPut(entry.dayKey) { mutableListOf() }
            val idx = list.indexOfFirst { it.id == entry.id }
            if (idx >= 0) list[idx] = entry else list.add(entry)
            return
        }
        val current = loadEntries(entry.dayKey).toMutableList()
        val index = current.indexOfFirst { it.id == entry.id }
        if (index >= 0) {
            current[index] = entry
        } else {
            current.add(entry)
        }
        val raw = json.encodeToString(current)
        prefs.edit().putString("estimated_steps_${entry.dayKey}", raw).apply()
    }

    fun removeEntry(dayKey: String, id: String) {
        if (prefs == null) {
            inMemory[dayKey]?.removeAll { it.id == id }
            return
        }
        val current = loadEntries(dayKey).filterNot { it.id == id }
        val raw = json.encodeToString(current)
        prefs.edit().putString("estimated_steps_$dayKey", raw).apply()
    }

    fun getTotalEstimatedSteps(dayKey: String): Int {
        return loadEntries(dayKey).sumOf { it.steps }
    }

    fun loadAll(): Map<String, List<EstimatedStepEntry>> {
        if (prefs == null) {
            return inMemory.mapValues { it.value.toList() }
        }
        val result = mutableMapOf<String, List<EstimatedStepEntry>>()
        val allEntries = prefs.all ?: return emptyMap()
        for (key in allEntries.keys) {
            if (key.startsWith("estimated_steps_")) {
                val dayKey = key.removePrefix("estimated_steps_")
                result[dayKey] = loadEntries(dayKey)
            }
        }
        return result
    }

    fun getAllEstimatedTotals(): Map<String, Int> {
        return loadAll().mapValues { (_, list) -> list.sumOf { it.steps } }
    }
}
