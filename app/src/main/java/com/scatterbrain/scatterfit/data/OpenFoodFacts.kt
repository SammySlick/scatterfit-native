package com.scatterbrain.scatterfit.data

import android.content.Context
import android.util.Log
import com.scatterbrain.scatterfit.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.io.BufferedReader
import java.io.File
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
data class BaseNutrimentsPer100g(
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
)

data class ParsedPortion(
    val amount: Double,
    val unit: String,
)

data class ScaledNutriments(
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)

data class PortionPreset(
    val id: String,
    val label: String,
    val amount: Double,
    val unit: String,
    val portionLabel: String,
)

private val LIQUID_KEYWORDS = listOf(
    "water", "drink", "juice", "milk", "beverage",
    "tea", "coffee", "soda", "cola", "smoothie",
    "shake", "beer", "wine", "cider"
)

fun parseServingSize(servingSize: String?): ParsedPortion? {
    if (servingSize.isNullOrBlank()) return null
    val match = Regex("""(?i)(\d+(?:\.\d+)?)\s*(g|ml|cl|dl|l|kg|oz)\b""").find(servingSize)
    if (match != null) {
        val rawNum = match.groupValues[1].toDoubleOrNull() ?: return null
        val rawUnit = match.groupValues[2].lowercase()
        return when (rawUnit) {
            "kg" -> ParsedPortion(rawNum * 1000.0, "g")
            "l" -> ParsedPortion(rawNum * 1000.0, "ml")
            "cl" -> ParsedPortion(rawNum * 10.0, "ml")
            "dl" -> ParsedPortion(rawNum * 100.0, "ml")
            else -> ParsedPortion(rawNum, rawUnit)
        }
    }
    val numMatch = Regex("""(\d+(?:\.\d+)?)""").find(servingSize)
    if (numMatch != null) {
        val num = numMatch.value.toDoubleOrNull() ?: return null
        return ParsedPortion(num, "g")
    }
    return null
}

fun inferPortionUnit(
    name: String?,
    category: String? = null,
    servingSize: String? = null,
    quantity: String? = null
): String {
    val sizeText = listOfNotNull(servingSize, quantity).joinToString(" ").lowercase()
    if (Regex("""\b(ml|cl|dl|fl\s*oz)\b""").containsMatchIn(sizeText) ||
        Regex("""\d+\s*l\b""").containsMatchIn(sizeText)
    ) {
        return "ml"
    }
    if (Regex("""\b(g|kg)\b""").containsMatchIn(sizeText)) {
        return "g"
    }

    val textToSearch = listOfNotNull(name, category).joinToString(" ").lowercase()
    val isLiquid = LIQUID_KEYWORDS.any { keyword ->
        textToSearch.contains(keyword)
    }

    return if (isLiquid) "ml" else "g"
}

fun computeBasePer100g(
    nutriments: OffNutriments,
    servingQuantity: Double? = null,
    isLiquid: Boolean = false,
): BaseNutrimentsPer100g {
    val sq = if (servingQuantity != null && servingQuantity > 0.0) servingQuantity else 100.0
    val scale = sq / 100.0

    // 1. Serving values
    val kcalServing = when {
        nutriments.energyKcal_serving != null && nutriments.energyKcal_serving > 0.0 -> nutriments.energyKcal_serving
        nutriments.energy_serving != null && nutriments.energy_serving > 0.0 -> nutriments.energy_serving / 4.184
        else -> null
    }
    val protServing = nutriments.proteins_serving?.takeIf { it >= 0.0 }
    val carbsServing = nutriments.carbohydrates_serving?.takeIf { it >= 0.0 }
    val fatServing = nutriments.fat_serving?.takeIf { it >= 0.0 }

    // 2. Raw 100g values
    val rawKcal100 = when {
        nutriments.energyKcal_100g != null && nutriments.energyKcal_100g > 0.0 -> nutriments.energyKcal_100g
        nutriments.energy_100g != null && nutriments.energy_100g > 0.0 -> nutriments.energy_100g / 4.184
        else -> null
    }
    val rawProt100 = nutriments.proteins_100g?.takeIf { it >= 0.0 }
    val rawCarbs100 = nutriments.carbohydrates_100g?.takeIf { it >= 0.0 }
    val rawFat100 = nutriments.fat_100g?.takeIf { it >= 0.0 }

    // Resolve Kcal (sanity check: liquid cannot have per-100ml > per-serving when serving > 100ml)
    val kcal100 = when {
        rawKcal100 != null && kcalServing != null -> {
            if (sq > 100.0 && isLiquid && rawKcal100 > kcalServing * 1.05) {
                debugLog("OFF", "Sanity fallback: per-100ml ($rawKcal100) > per-serving ($kcalServing) for liquid of $sq ml")
                kcalServing / scale
            } else {
                rawKcal100
            }
        }
        rawKcal100 != null -> rawKcal100
        kcalServing != null -> kcalServing / scale
        nutriments.energyKcal != null && nutriments.energyKcal > 0.0 -> {
            if (sq > 100.0 && isLiquid) nutriments.energyKcal / scale else nutriments.energyKcal
        }
        else -> 0.0
    }

    // Resolve Protein
    val prot100 = when {
        rawProt100 != null && protServing != null -> {
            if (sq > 100.0 && isLiquid && rawProt100 > protServing * 1.05) {
                debugLog("OFF", "Sanity fallback: per-100ml protein ($rawProt100) > per-serving ($protServing)")
                protServing / scale
            } else {
                rawProt100
            }
        }
        rawProt100 != null -> rawProt100
        protServing != null -> protServing / scale
        else -> 0.0
    }

    // Resolve Carbs
    val carbs100 = when {
        rawCarbs100 != null && carbsServing != null -> {
            if (sq > 100.0 && isLiquid && rawCarbs100 > carbsServing * 1.05) {
                carbsServing / scale
            } else {
                rawCarbs100
            }
        }
        rawCarbs100 != null -> rawCarbs100
        carbsServing != null -> carbsServing / scale
        else -> 0.0
    }

    // Resolve Fat
    val fat100 = when {
        rawFat100 != null && fatServing != null -> {
            if (sq > 100.0 && isLiquid && rawFat100 > fatServing * 1.05) {
                fatServing / scale
            } else {
                rawFat100
            }
        }
        rawFat100 != null -> rawFat100
        fatServing != null -> fatServing / scale
        else -> 0.0
    }

    return BaseNutrimentsPer100g(
        kcal = kcal100,
        protein = prot100,
        carbs = carbs100,
        fat = fat100
    )
}

fun scaleNutriments(base: BaseNutrimentsPer100g, portionGramsOrMl: Double): ScaledNutriments {
    val factor = portionGramsOrMl / 100.0
    return ScaledNutriments(
        kcal = base.kcal * factor,
        protein = base.protein * factor,
        carbs = base.carbs * factor,
        fat = base.fat * factor
    )
}

fun resolvePortionAmount(enteredAmount: Double, unit: String, servingQuantity: Double): Double {
    return if (unit.equals("servings", ignoreCase = true)) {
        enteredAmount * servingQuantity
    } else {
        enteredAmount
    }
}

fun formatAmount(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(java.util.Locale.US, "%.1f", value)
    }
}

fun derivePortionPresets(product: ProductResult): List<PortionPreset> {
    val unit = product.inferredUnit
    val list = mutableListOf<PortionPreset>()

    // 1. 50
    list.add(
        PortionPreset(
            id = "50",
            label = "50 $unit",
            amount = 50.0,
            unit = unit,
            portionLabel = "50 $unit"
        )
    )

    // 2. 100
    list.add(
        PortionPreset(
            id = "100",
            label = "100 $unit",
            amount = 100.0,
            unit = unit,
            portionLabel = "100 $unit"
        )
    )

    // 3. 250
    list.add(
        PortionPreset(
            id = "250",
            label = "250 $unit",
            amount = 250.0,
            unit = unit,
            portionLabel = "250 $unit"
        )
    )

    // 4. Sane household serving from serving_size (e.g. "2 slices (60g)")
    val parsed = parseServingSize(product.servingSize)
    if (parsed != null && parsed.amount > 0.0) {
        val raw = product.servingSize?.trim() ?: "${formatAmount(parsed.amount)} ${parsed.unit}"
        val alreadyCovered = list.any { kotlin.math.abs(it.amount - parsed.amount) < 0.5 }
        if (!alreadyCovered) {
            list.add(
                PortionPreset(
                    id = "household",
                    label = raw,
                    amount = parsed.amount,
                    unit = parsed.unit,
                    portionLabel = raw
                )
            )
        }
    }

    return list
}

private fun debugLog(tag: String, msg: String) {
    try {
        Log.d(tag, msg)
    } catch (_: Throwable) {
        println("[$tag] $msg")
    }
}

fun levenshteinDistance(s1: String, s2: String): Int {
    val m = s1.length
    val n = s2.length
    val dp = IntArray(n + 1) { it }
    for (i in 1..m) {
        var prev = dp[0]
        dp[0] = i
        for (j in 1..n) {
            val temp = dp[j]
            dp[j] = if (s1[i - 1] == s2[j - 1]) {
                prev
            } else {
                minOf(prev + 1, minOf(dp[j] + 1, dp[j - 1] + 1))
            }
            prev = temp
        }
    }
    return dp[n]
}

fun fuzzyScore(query: String, target: String): Double {
    val qClean = query.trim().lowercase()
    val tClean = target.trim().lowercase()
    if (qClean.isEmpty() || tClean.isEmpty()) return 0.0

    val qTokens = qClean.split(Regex("\\s+")).filter { it.isNotBlank() }
    val stopWords = setOf("and", "the", "a", "of", "with", "in")
    val filteredQTokens = qTokens.filter { it !in stopWords }
    if (filteredQTokens.isEmpty()) return 0.0

    if (tClean.contains(qClean)) {
        return 1.0 + if (tClean.startsWith(qClean)) 0.2 else 0.0
    }

    val tTokens = tClean.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
    if (tTokens.isEmpty()) return 0.0

    var matchedTokensCount = 0
    var totalTokenScore = 0.0

    for (qTok in filteredQTokens) {
        var bestTokenScore = 0.0
        for (tTok in tTokens) {
            if (tTok == qTok) {
                bestTokenScore = maxOf(bestTokenScore, 1.0)
                continue
            }
            if (tTok.contains(qTok) && qTok.length >= 2) {
                bestTokenScore = maxOf(bestTokenScore, 0.85)
                continue
            }
            val maxD = when {
                qTok.length <= 3 -> 1
                qTok.length <= 6 -> 2
                else -> 3
            }
            if (kotlin.math.abs(qTok.length - tTok.length) <= maxD) {
                val d = levenshteinDistance(qTok, tTok)
                if (d <= maxD) {
                    val sim = 1.0 - (d.toDouble() / maxOf(qTok.length, tTok.length))
                    if (sim >= 0.65) {
                        bestTokenScore = maxOf(bestTokenScore, sim)
                    }
                }
            }
        }
        if (bestTokenScore >= 0.65) {
            matchedTokensCount++
            totalTokenScore += bestTokenScore
        }
    }

    if (matchedTokensCount == 0) return 0.0

    return (totalTokenScore / filteredQTokens.size.toDouble()) * (matchedTokensCount.toDouble() / filteredQTokens.size.toDouble()) +
            (0.5 * totalTokenScore / matchedTokensCount)
}

fun <T> filterFuzzy(
    items: List<T>,
    query: String,
    limit: Int = 8,
    nameSelector: (T) -> String?
): List<T> = filterFuzzy(items, query, limit, nameSelector, *emptyArray())

fun <T> filterFuzzy(
    items: List<T>,
    query: String,
    limit: Int = 8,
    nameSelector: (T) -> String?,
    vararg extraSelectors: (T) -> String?
): List<T> {
    val q = query.trim()
    if (q.isBlank()) return emptyList()
    return items
        .mapNotNull { item ->
            val fields = listOfNotNull(nameSelector(item)) + extraSelectors.mapNotNull { it(item) }
            val score = fields.maxOfOrNull { fuzzyScore(q, it) } ?: 0.0
            if (score >= 0.6) item to score else null
        }
        .sortedByDescending { it.second }
        .take(limit)
        .map { it.first }
}

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
    @SerialName("categories")
    val categories: String? = null,
) {
    val servingQuantityGrams: Double?
        get() {
            val prim = servingQuantity as? JsonPrimitive ?: return null
            prim.doubleOrNull?.let { return it }
            prim.content.toDoubleOrNull()?.let { return it }
            return parseServingSize(prim.content)?.amount
        }

    val servingQuantityOrFallback: Double
        get() {
            val parsed = servingQuantityGrams
            if (parsed != null && parsed > 0.0) {
                debugLog("OFF", "Serving size resolved from serving_quantity: $parsed")
                return parsed
            }
            val fromServingSize = parseServingSize(servingSize)?.amount
            if (fromServingSize != null && fromServingSize > 0.0) {
                debugLog("OFF", "Serving size resolved from serving_size: $fromServingSize")
                return fromServingSize
            }
            val fromQuantity = parseServingSize(quantity)?.amount
            if (fromQuantity != null && fromQuantity > 0.0) {
                debugLog("OFF", "Serving size resolved from quantity: $fromQuantity")
                return fromQuantity
            }
            debugLog("OFF", "Serving size resolved to fallback: 100.0")
            return 100.0
        }

    val inferredUnit: String
        get() = inferPortionUnit(productName, categories, servingSize, quantity)

    val basePer100g: BaseNutrimentsPer100g
        get() = computeBasePer100g(nutriments, servingQuantityOrFallback, isLiquid = inferredUnit == "ml")

    val kcalPerServing: Int
        get() {
            if (nutriments.energyKcal_serving != null && nutriments.energyKcal_serving > 0.0) {
                return kotlin.math.round(nutriments.energyKcal_serving).toInt()
            }
            if (nutriments.energy_serving != null && nutriments.energy_serving > 0.0) {
                return kotlin.math.round(nutriments.energy_serving / 4.184).toInt()
            }
            val scale = servingQuantityOrFallback / 100.0
            return kotlin.math.round(basePer100g.kcal * scale).toInt()
        }

    val proteinPerServing: Double
        get() {
            if (nutriments.proteins_serving != null) return nutriments.proteins_serving
            val scale = servingQuantityOrFallback / 100.0
            return basePer100g.protein * scale
        }

    val carbsPerServing: Double
        get() {
            if (nutriments.carbohydrates_serving != null) return nutriments.carbohydrates_serving
            val scale = servingQuantityOrFallback / 100.0
            return basePer100g.carbs * scale
        }

    val fatPerServing: Double
        get() {
            if (nutriments.fat_serving != null) return nutriments.fat_serving
            val scale = servingQuantityOrFallback / 100.0
            return basePer100g.fat * scale
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


@Serializable
data class GenericFoodItem(
    val name: String,
    val unit: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)

object OpenFoodFactsClient {
    private var cachedGenericFoods: List<ProductResult>? = null

    val GENERIC_FOODS: List<ProductResult>
        get() = loadGenericFoods()

    fun parseGenericFoodsJson(jsonText: String): List<ProductResult> {
        return try {
            json.decodeFromString<List<GenericFoodItem>>(jsonText).map { item ->
                val codeSafe = "generic_" + item.name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
                ProductResult(
                    code = codeSafe,
                    productName = item.name,
                    brands = "Generic",
                    categories = "generic",
                    servingSize = "100 ${item.unit}",
                    servingQuantity = JsonPrimitive(100.0),
                    nutriments = OffNutriments(
                        energyKcal_100g = item.kcal,
                        proteins_100g = item.protein,
                        carbohydrates_100g = item.carbs,
                        fat_100g = item.fat,
                    )
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun loadGenericFoods(context: Context? = null): List<ProductResult> {
        val existing = cachedGenericFoods
        if (existing != null) return existing

        val text: String? = if (context != null) {
            try {
                context.resources.openRawResource(R.raw.generic_foods).bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                null
            }
        } else null

        val resolvedText = text ?: run {
            val file = File("src/main/res/raw/generic_foods.json")
            val altFile = File("app/src/main/res/raw/generic_foods.json")
            val target = if (file.exists()) file else altFile
            if (target.exists()) {
                try { target.readText() } catch (_: Exception) { null }
            } else {
                try {
                    OpenFoodFactsClient::class.java.classLoader?.getResourceAsStream("raw/generic_foods.json")?.bufferedReader()?.use { it.readText() }
                } catch (_: Exception) { null }
            }
        }

        val items = if (resolvedText != null) {
            parseGenericFoodsJson(resolvedText)
        } else {
            emptyList()
        }

        if (items.isNotEmpty()) {
            cachedGenericFoods = items
        }
        return items
    }
    private const val BASE_URL = "https://world.openfoodfacts.org"
    private const val FIELDS = "code,product_name,brands,quantity,nutriments,images_url,serving_size,serving_quantity,categories"
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
