package com.scatterbrain.scatterfit.ui

import android.content.Context
import com.scatterbrain.scatterfit.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class ExerciseLibraryItem(
    val id: String,
    val name: String,
    val muscle: String,
    val equipment: String = "other",
)

object ExerciseLibrary {
    private var cachedLibrary: List<ExerciseRef>? = null
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context): List<ExerciseRef> {
        val existing = cachedLibrary
        if (existing != null) return existing

        val items = try {
            val text = context.resources.openRawResource(R.raw.exercise_library).bufferedReader().use { it.readText() }
            json.decodeFromString<List<ExerciseLibraryItem>>(text).map {
                ExerciseRef(
                    id = it.id,
                    name = it.name,
                    muscle = it.muscle,
                    isCustom = false
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
        cachedLibrary = items
        return items
    }

    fun lookup(id: String, context: Context? = null): ExerciseRef? {
        val list = cachedLibrary ?: context?.let { load(it) } ?: emptyList()
        return list.find { it.id == id }
    }

    /**
     * Resolves an exercise by name: if a library exercise matches case-insensitively,
     * returns that library ExerciseRef. Otherwise creates a custom ExerciseRef with muscle="other".
     */
    fun resolveOrCreate(name: String, library: List<ExerciseRef>): ExerciseRef {
        val trimmed = name.trim()
        val match = library.find { it.name.equals(trimmed, ignoreCase = true) }
        if (match != null) return match

        return ExerciseRef(
            id = "custom-${UUID.randomUUID()}",
            name = trimmed,
            muscle = "other",
            isCustom = true
        )
    }
}
