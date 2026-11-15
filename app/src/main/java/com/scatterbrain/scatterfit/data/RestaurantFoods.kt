package com.scatterbrain.scatterfit.data

import android.content.Context
import com.scatterbrain.scatterfit.R
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * Bundled UK chain-menu nutrition. Open Food Facts is a crowdsourced packaged-grocery
 * database: restaurant items have no barcode, so they are largely absent (brand kfc = 94
 * products, almost none with kcal; brand mcdonalds = 7). Without this library the calorie
 * search for "kfc zinger" returned Lays and Walkers crisps that merely mention KFC.
 *
 * Figures come from the chains' own published tables (KFC UK and Greggs PDFs, plus a
 * maintained UK chain-nutrition dataset for the rest). Each row was validated so that
 * kcal roughly reconciles with 4*protein + 4*carbs + 9*fat.
 */
@Serializable
data class RestaurantFoodItem(
    val brand: String,
    val name: String,
    val serving_label: String = "1 serving",
    val serving_grams: Double = 100.0,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)

object RestaurantFoods {

    private var cached: List<RestaurantFoodItem>? = null
    private var cachedProducts: List<ProductResult>? = null

    private val fileCandidates = listOf(
        "src/main/res/raw/restaurant_foods.json",
        "app/src/main/res/raw/restaurant_foods.json",
    )

    fun parse(jsonText: String): List<RestaurantFoodItem> = try {
        OpenFoodFactsClient.json.decodeFromString<List<RestaurantFoodItem>>(jsonText)
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * Loads the library. Mirrors OpenFoodFactsClient.loadGenericFoods: works with a real
     * Context on device, and falls back to the repo path / classpath in unit tests.
     */
    fun load(context: Context? = null): List<RestaurantFoodItem> {
        cached?.let { return it }

        val fromContext: String? = if (context != null) {
            try {
                context.resources.openRawResource(R.raw.restaurant_foods)
                    .bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                null
            }
        } else null

        val text = fromContext ?: run {
            val local = fileCandidates.map { File(it) }.firstOrNull { it.exists() }
            if (local != null) {
                try { local.readText() } catch (_: Exception) { null }
            } else {
                try {
                    RestaurantFoods::class.java.classLoader
                        ?.getResourceAsStream("raw/restaurant_foods.json")
                        ?.bufferedReader()?.use { it.readText() }
                } catch (_: Exception) { null }
            }
        }

        val items = if (text != null) parse(text) else emptyList()
        if (items.isNotEmpty()) cached = items
        return items
    }

    fun allProducts(context: Context? = null): List<ProductResult> {
        cachedProducts?.let { return it }
        val products = load(context).map { it.toProductResult() }
        if (products.isNotEmpty()) cachedProducts = products
        return products
    }

    fun clearCache() {
        cached = null
        cachedProducts = null
    }
}

/**
 * Converts a chain-menu row into the ProductResult the existing search and confirm card
 * already understand. Macros are supplied both per-serving and per-100g so
 * [computeBasePer100g] resolves to the same numbers whichever branch it takes, and the
 * portion the card opens with is exactly one menu item.
 */
fun RestaurantFoodItem.toProductResult(): ProductResult {
    val grams = if (serving_grams > 0.0) serving_grams else 100.0
    val scaleTo100 = 100.0 / grams
    val codeSafe = "restaurant_" +
        "$brand $name".lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')

    return ProductResult(
        code = codeSafe,
        productName = name,
        brands = brand,
        categories = "restaurant",
        // A plain "<n> g" string keeps servingSize parseable; the human label lives in brands.
        servingSize = "${formatGrams(grams)} g",
        servingQuantity = JsonPrimitive(grams),
        nutriments = OffNutriments(
            energyKcal_100g = kcal * scaleTo100,
            proteins_100g = protein * scaleTo100,
            carbohydrates_100g = carbs * scaleTo100,
            fat_100g = fat * scaleTo100,
            energyKcal_serving = kcal,
            proteins_serving = protein,
            carbohydrates_serving = carbs,
            fat_serving = fat,
        ),
    )
}

private fun formatGrams(grams: Double): String =
    if (grams == grams.toLong().toDouble()) grams.toLong().toString()
    else String.format("%.1f", grams)
