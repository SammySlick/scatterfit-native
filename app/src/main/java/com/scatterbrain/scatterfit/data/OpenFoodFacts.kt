package com.scatterbrain.scatterfit.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Serializable
data class OffNutriments(
    val energy_100g: Double? = null,
    @SerialName("energy-kcal_100g")
    val energyKcal_100g: Double? = null,
    @SerialName("energy-kcal")
    val energyKcal: Double? = null,

    val energy_serving: Double? = null,
    @SerialName("energy-kcal_serving")
    val energyKcal_serving: Double? = null,

    val proteins_100g: Double? = null,
    val proteins_serving: Double? = null,

    val carbohydrates_100g: Double? = null,
    val carbohydrates_serving: Double? = null,

    val fat_100g: Double? = null,
    val fat_serving: Double? = null,
)

@Serializable
data class ProductResult(
    val code: String = "",
    @SerialName("product_name")
    val productName: String? = null,
    val brands: String? = null,
    val quantity: String? = null,
    val nutriments: OffNutriments = OffNutriments(),
    @SerialName("serving_size")
    val servingSize: String? = null,
    @SerialName("serving_quantity")
    val servingQuantity: JsonElement? = null,
) {
    val servingQuantityGrams: Double?
        get() {
            val prim = servingQuantity as? JsonPrimitive ?: return null
            return prim.doubleOrNull ?: prim.content.toDoubleOrNull()
        }

    val kcalPerServing: Int
        get() {
            if (nutriments.energyKcal_serving != null && nutriments.energyKcal_serving > 0.0) {
                return kotlin.math.round(nutriments.energyKcal_serving).toInt()
            }
            if (nutriments.energy_serving != null && nutriments.energy_serving > 0.0) {
                return kotlin.math.round(nutriments.energy_serving / 4.184).toInt()
            }
            val scale = (servingQuantityGrams ?: 100.0) / 100.0
            if (nutriments.energyKcal_100g != null && nutriments.energyKcal_100g > 0.0) {
                return kotlin.math.round(nutriments.energyKcal_100g * scale).toInt()
            }
            if (nutriments.energyKcal != null && nutriments.energyKcal > 0.0) {
                return kotlin.math.round(nutriments.energyKcal * scale).toInt()
            }
            if (nutriments.energy_100g != null && nutriments.energy_100g > 0.0) {
                return kotlin.math.round((nutriments.energy_100g / 4.184) * scale).toInt()
            }
            return 0
        }

    val proteinPerServing: Double
        get() {
            if (nutriments.proteins_serving != null) return nutriments.proteins_serving
            val scale = (servingQuantityGrams ?: 100.0) / 100.0
            return (nutriments.proteins_100g ?: 0.0) * scale
        }

    val carbsPerServing: Double
        get() {
            if (nutriments.carbohydrates_serving != null) return nutriments.carbohydrates_serving
            val scale = (servingQuantityGrams ?: 100.0) / 100.0
            return (nutriments.carbohydrates_100g ?: 0.0) * scale
        }

    val fatPerServing: Double
        get() {
            if (nutriments.fat_serving != null) return nutriments.fat_serving
            val scale = (servingQuantityGrams ?: 100.0) / 100.0
            return (nutriments.fat_100g ?: 0.0) * scale
        }

    val displayName: String
        get() = buildString {
            append(productName?.takeIf { it.isNotBlank() } ?: "Unknown Product")
            brands?.takeIf { it.isNotBlank() }?.let { append(" ($it)") }
        }
}

@Serializable
data class SearchResponse(
    val count: Int = 0,
    val products: List<ProductResult> = emptyList(),
)

@Serializable
data class BarcodeResponse(
    val code: String = "",
    val product: ProductResult? = null,
    val status: Int = 0,
)

object OpenFoodFactsClient {
    private const val BASE_URL = "https://world.openfoodfacts.org"
    private const val FIELDS = "code,product_name,brands,quantity,nutriments,images_url,serving_size,serving_quantity"
    private const val USER_AGENT = "ScatterFit - Android - Version 1.0"

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val queryCache = mutableMapOf<String, List<ProductResult>>()
    private val barcodeCache = mutableMapOf<String, ProductResult?>()

    fun parseSearchJson(rawJson: String): List<ProductResult>? {
        return try {
            json.decodeFromString<SearchResponse>(rawJson).products
        } catch (_: Exception) {
            null
        }
    }

    fun parseBarcodeJson(rawJson: String): ProductResult? {
        return try {
            json.decodeFromString<BarcodeResponse>(rawJson).product
        } catch (_: Exception) {
            null
        }
    }

    suspend fun search(query: String): List<ProductResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()
        val cached = queryCache[q.lowercase()]
        if (cached != null) return@withContext cached

        val encoded = URLEncoder.encode(q, "UTF-8")
        val urlStr = "$BASE_URL/cgi/search.pl?search_terms=$encoded&search_simple=1&action=process&json=1&page_size=20&fields=$FIELDS"
        val responseText = executeGet(urlStr) ?: return@withContext emptyList()
        val results = parseSearchJson(responseText) ?: emptyList()
        val filtered = results.filter { !it.productName.isNullOrBlank() }
        queryCache[q.lowercase()] = filtered
        filtered
    }

    suspend fun lookupBarcode(barcode: String): ProductResult? = withContext(Dispatchers.IO) {
        val b = barcode.trim()
        if (b.isBlank()) return@withContext null
        if (barcodeCache.containsKey(b)) return@withContext barcodeCache[b]

        val urlStr = "$BASE_URL/api/v2/product/$b.json?fields=$FIELDS"
        val responseText = executeGet(urlStr) ?: return@withContext null
        val result = parseBarcodeJson(responseText)
        barcodeCache[b] = result
        result
    }

    private fun executeGet(urlStr: String): String? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")

            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { reader ->
                    reader.readText()
                }
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun clearCache() {
        queryCache.clear()
        barcodeCache.clear()
    }
}
