package com.scatterbrain.scatterfit.ui

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class Recipe(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val perPortionKcal: Int,
    val perPortionProtein: Int,
    val perPortionCarbs: Int? = null,
    val perPortionFat: Int? = null,
    val portionGrams: Int? = null,
    val portionsInPool: Int = 0,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class FavouriteFood(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kcalPerServing: Int,
    val proteinPerServing: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
private data class SavedFoodBlob(
    val recipes: List<Recipe> = emptyList(),
    val favourites: List<FavouriteFood> = emptyList(),
)

class SavedFoodStore(
    private val prefsProvider: () -> String?,
    private val prefsWriter: (String) -> Unit,
) {
    constructor(context: Context) : this(
        prefsProvider = {
            context.getSharedPreferences("scatterfit_food_prefs", Context.MODE_PRIVATE)
                .getString("saved_foods", null)
        },
        prefsWriter = { jsonStr ->
            context.getSharedPreferences("scatterfit_food_prefs", Context.MODE_PRIVATE)
                .edit().putString("saved_foods", jsonStr).apply()
        }
    )

    private val json = Json { ignoreUnknownKeys = true }

    private fun loadBlob(): SavedFoodBlob {
        val raw = prefsProvider() ?: return SavedFoodBlob()
        return try {
            json.decodeFromString<SavedFoodBlob>(raw)
        } catch (_: Exception) {
            SavedFoodBlob()
        }
    }

    private fun saveBlob(blob: SavedFoodBlob) {
        prefsWriter(json.encodeToString(blob))
    }

    // --- Recipes API ---

    fun allRecipes(): List<Recipe> {
        return loadBlob().recipes
    }

    fun getRecipe(id: String): Recipe? {
        return allRecipes().find { it.id == id }
    }

    fun upsertRecipe(recipe: Recipe) {
        val blob = loadBlob()
        val current = blob.recipes.toMutableList()
        val index = current.indexOfFirst { it.id == recipe.id }
        if (index >= 0) {
            current[index] = recipe
        } else {
            current.add(recipe)
        }
        saveBlob(blob.copy(recipes = current))
    }

    fun deleteRecipe(id: String) {
        val blob = loadBlob()
        val filtered = blob.recipes.filterNot { it.id == id }
        saveBlob(blob.copy(recipes = filtered))
    }

    fun decrementPool(id: String): Recipe? {
        val blob = loadBlob()
        val current = blob.recipes.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return null
        val recipe = current[index]
        val newPool = (recipe.portionsInPool - 1).coerceAtLeast(0)
        val updated = recipe.copy(portionsInPool = newPool, updatedAt = System.currentTimeMillis())
        current[index] = updated
        saveBlob(blob.copy(recipes = current))
        return updated
    }

    // --- Favourites API ---

    fun allFavourites(): List<FavouriteFood> {
        return loadBlob().favourites
    }

    fun isFavourite(name: String): Boolean {
        val clean = name.trim().lowercase()
        return allFavourites().any { it.name.trim().lowercase() == clean }
    }

    fun toggleFavourite(favourite: FavouriteFood) {
        val blob = loadBlob()
        val current = blob.favourites.toMutableList()
        val clean = favourite.name.trim().lowercase()
        val existingIndex = current.indexOfFirst { it.name.trim().lowercase() == clean || it.id == favourite.id }
        if (existingIndex >= 0) {
            current.removeAt(existingIndex)
        } else {
            current.add(favourite)
        }
        saveBlob(blob.copy(favourites = current))
    }
}
