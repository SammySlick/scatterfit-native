package com.scatterbrain.scatterfit.data

import com.scatterbrain.scatterfit.ui.toProductResult
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsTest {

    private val cannedSearchJson = """
    {
      "count": 2,
      "products": [
        {
          "code": "5000128000001",
          "product_name": "Crunchy Peanut Butter",
          "brands": "Whole Earth",
          "quantity": "454g",
          "serving_size": "30g",
          "serving_quantity": 30.0,
          "nutriments": {
            "energy_100g": 2500.0,
            "energy-kcal_100g": 600.0,
            "energy-kcal_serving": 180.0,
            "proteins_100g": 26.0,
            "proteins_serving": 8.0,
            "carbohydrates_100g": 12.0,
            "carbohydrates_serving": 3.6,
            "fat_100g": 50.0,
            "fat_serving": 15.0
          }
        },
        {
          "code": "5000128000002",
          "product_name": "Greek Style Yogurt",
          "brands": "Yeo Valley",
          "serving_quantity": "150",
          "nutriments": {
            "energy_100g": 418.4,
            "proteins_100g": 10.0,
            "carbohydrates_100g": 4.0,
            "fat_100g": 5.0
          }
        }
      ]
    }
    """.trimIndent()

    private val cannedBarcodeJson = """
    {
      "code": "5000128000001",
      "status": 1,
      "status_verbose": "product found",
      "product": {
        "code": "5000128000001",
        "product_name": "Crunchy Peanut Butter",
        "brands": "Whole Earth",
        "serving_size": "30g",
        "serving_quantity": 30.0,
        "nutriments": {
          "energy-kcal_serving": 180.0,
          "proteins_serving": 8.0
        }
      }
    }
    """.trimIndent()

    @Test
    fun `parseSearchJson parses canned JSON into ProductResult list`() {
        val products = OpenFoodFactsClient.parseSearchJson(cannedSearchJson)
        assertNotNull(products)
        assertEquals(2, products!!.size)

        val p1 = products[0]
        assertEquals("5000128000001", p1.code)
        assertEquals("Crunchy Peanut Butter", p1.productName)
        assertEquals("Whole Earth", p1.brands)
        assertEquals("Crunchy Peanut Butter (Whole Earth)", p1.displayName)
        assertEquals(30.0, p1.servingQuantityGrams!!, 0.001)

        val p2 = products[1]
        assertEquals("Greek Style Yogurt", p2.productName)
        assertEquals(150.0, p2.servingQuantityGrams!!, 0.001)
    }

    @Test
    fun `parseBarcodeJson parses canned barcode response into ProductResult`() {
        val product = OpenFoodFactsClient.parseBarcodeJson(cannedBarcodeJson)
        assertNotNull(product)
        assertEquals("5000128000001", product!!.code)
        assertEquals("Crunchy Peanut Butter", product.productName)
        assertEquals(180, product.kcalPerServing)
        assertEquals(8.0, product.proteinPerServing, 0.001)
    }

    @Test
    fun `serving values preferred over 100g calculation when both exist`() {
        val product = ProductResult(
            code = "p1",
            productName = "Protein Bar",
            servingQuantity = JsonPrimitive(50.0),
            nutriments = OffNutriments(
                // 100g values: 400 kcal, 30g protein -> for 50g would be 200 kcal, 15g protein
                energyKcal_100g = 400.0,
                proteins_100g = 30.0,
                // Direct serving values: 220 kcal, 18g protein
                energyKcal_serving = 220.0,
                proteins_serving = 18.0
            )
        )

        // Serving values MUST win per anti-garbage rule
        assertEquals(220, product.kcalPerServing)
        assertEquals(18.0, product.proteinPerServing, 0.001)
    }

    @Test
    fun `kcal conversion scales 100g values when serving fields absent`() {
        val product = ProductResult(
            code = "p2",
            productName = "Oats",
            servingQuantity = JsonPrimitive(40.0), // 40g serving
            nutriments = OffNutriments(
                energyKcal_100g = 370.0,
                proteins_100g = 12.0,
                carbohydrates_100g = 60.0,
                fat_100g = 8.0
            )
        )

        // 370 * 0.4 = 148 kcal
        assertEquals(148, product.kcalPerServing)
        // 12 * 0.4 = 4.8g protein
        assertEquals(4.8, product.proteinPerServing, 0.001)
        // 60 * 0.4 = 24.0g carbs
        assertEquals(24.0, product.carbsPerServing, 0.001)
        // 8 * 0.4 = 3.2g fat
        assertEquals(3.2, product.fatPerServing, 0.001)
    }

    @Test
    fun `kcal conversion converts EU energy in kJ to kcal when energyKcal absent`() {
        val product = ProductResult(
            code = "p3",
            productName = "Energy Gel",
            servingQuantity = JsonPrimitive(100.0),
            nutriments = OffNutriments(
                energy_100g = 836.8 // 836.8 kJ / 4.184 = 200.0 kcal
            )
        )

        assertEquals(200, product.kcalPerServing)
    }

    @Test
    fun `malformed JSON returns null without crashing`() {
        assertNull(OpenFoodFactsClient.parseSearchJson("{ malformed [ json }"))
        assertNull(OpenFoodFactsClient.parseBarcodeJson("invalid string"))
        assertNull(OpenFoodFactsClient.parseSearchJson(""))
    }

    @Test
    fun `portion parsing from serving_size strings extracts first number and unit`() {
        val p1 = parseServingSize("250g")
        assertNotNull(p1)
        assertEquals(250.0, p1!!.amount, 0.001)
        assertEquals("g", p1.unit)

        val p2 = parseServingSize("500 ml")
        assertNotNull(p2)
        assertEquals(500.0, p2!!.amount, 0.001)
        assertEquals("ml", p2.unit)

        val p3 = parseServingSize("2 slices (60g)")
        assertNotNull(p3)
        assertEquals(60.0, p3!!.amount, 0.001)
        assertEquals("g", p3.unit)

        // Additional edge cases
        val p4 = parseServingSize("1.5 l")
        assertNotNull(p4)
        assertEquals(1500.0, p4!!.amount, 0.001)
        assertEquals("ml", p4.unit)

        val p5 = parseServingSize("33 cl")
        assertNotNull(p5)
        assertEquals(330.0, p5!!.amount, 0.001)
        assertEquals("ml", p5.unit)

        assertNull(parseServingSize(null))
        assertNull(parseServingSize(""))
    }

    @Test
    fun `liquid inference identifies drinks and beverages as ml and solid foods as g`() {
        assertEquals("ml", inferPortionUnit("Vieve Protein Water"))
        assertEquals("ml", inferPortionUnit("Fresh Orange Juice"))
        assertEquals("ml", inferPortionUnit("Semi-Skimmed Milk"))
        assertEquals("ml", inferPortionUnit("Sparkling Energy Drink"))
        assertEquals("ml", inferPortionUnit("Iced Green Tea"))
        assertEquals("ml", inferPortionUnit("Cold Brew Coffee"))
        assertEquals("ml", inferPortionUnit("Berry Smoothie"))
        assertEquals("ml", inferPortionUnit("Protein Shake"))
        assertEquals("ml", inferPortionUnit("Cola Zero"))
        assertEquals("ml", inferPortionUnit("Craft Beer"))
        assertEquals("ml", inferPortionUnit("Red Wine"))
        assertEquals("ml", inferPortionUnit("Apple Cider"))
        assertEquals("ml", inferPortionUnit(name = "Plant Drink", category = "beverages"))
        assertEquals("ml", inferPortionUnit(name = "Unknown Liquid", category = "Fruit juices"))

        assertEquals("g", inferPortionUnit("Peanut Butter"))
        assertEquals("g", inferPortionUnit("Protein Bar"))
        assertEquals("g", inferPortionUnit("Rolled Oats"))
        assertEquals("g", inferPortionUnit("Chicken Breast"))
        assertEquals("g", inferPortionUnit(name = "Greek Yogurt", category = "dairy"))
    }

    @Test
    fun `scaling maths computes per100g times portion divided by 100 for all macros and handles servings and half preset`() {
        val base = BaseNutrimentsPer100g(
            kcal = 400.0,
            protein = 25.0,
            carbs = 40.0,
            fat = 15.0
        )

        // 1. per100g * portion / 100
        val scaled200 = scaleNutriments(base, 200.0)
        assertEquals(800.0, scaled200.kcal, 0.001)
        assertEquals(50.0, scaled200.protein, 0.001)
        assertEquals(80.0, scaled200.carbs, 0.001)
        assertEquals(30.0, scaled200.fat, 0.001)

        val scaled50 = scaleNutriments(base, 50.0)
        assertEquals(200.0, scaled50.kcal, 0.001)
        assertEquals(12.5, scaled50.protein, 0.001)
        assertEquals(20.0, scaled50.carbs, 0.001)
        assertEquals(7.5, scaled50.fat, 0.001)

        // 2. "servings" unit multiplies servingQuantity: "1.5 servings" of a 500ml bottle = 750ml
        val servingQty = 500.0
        val resolvedPortion = resolvePortionAmount(1.5, "servings", servingQty)
        assertEquals(750.0, resolvedPortion, 0.001)
        val scaledServings = scaleNutriments(base, resolvedPortion)
        assertEquals(3000.0, scaledServings.kcal, 0.001) // 400 * 7.5
        assertEquals(187.5, scaledServings.protein, 0.001)

        // 3. Half preset = 0.5x
        val halfAmount = servingQty / 2.0
        assertEquals(250.0, halfAmount, 0.001)
        val halfResolved = resolvePortionAmount(halfAmount, "ml", servingQty)
        assertEquals(250.0, halfResolved, 0.001)
        val scaledHalf = scaleNutriments(base, halfResolved)
        assertEquals(1000.0, scaledHalf.kcal, 0.001) // 400 * 2.5
    }

    @Test
    fun `LoggedMeal round-trip preserves portionGrams and portionLabel`() {
        val meal = com.scatterbrain.scatterfit.ui.LoggedMeal(
            id = "test-123",
            name = "Vieve Protein Water",
            kcal = 17.0,
            protein = 20.0,
            carbs = 0.0,
            fat = 0.0,
            mealType = "lunch",
            portionGrams = 500.0,
            portionLabel = "500 ml"
        )

        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToString(com.scatterbrain.scatterfit.ui.LoggedMeal.serializer(), meal)
        val decoded = json.decodeFromString<com.scatterbrain.scatterfit.ui.LoggedMeal>(encoded)

        assertEquals("test-123", decoded.id)
        assertEquals("Vieve Protein Water", decoded.name)
        assertEquals(17.0, decoded.kcal, 0.001)
        assertEquals(500.0, decoded.portionGrams!!, 0.001)
        assertEquals("500 ml", decoded.portionLabel)
    }

    @Test
    fun `legacy LoggedMeal without portion fields decodes with null portion fields`() {
        val legacyJson = """
            {
              "id": "legacy-1",
              "name": "Classic Oats",
              "kcal": 300.0,
              "protein": 10.0
            }
        """.trimIndent()
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<com.scatterbrain.scatterfit.ui.LoggedMeal>(legacyJson)

        assertEquals("legacy-1", decoded.id)
        assertEquals("Classic Oats", decoded.name)
        assertNull(decoded.portionGrams)
        assertNull(decoded.portionLabel)
    }

    @Test
    fun `derivePortionPresets includes 50, 100, 250, and sane household serving`() {
        val bread = ProductResult(
            code = "bread1",
            productName = "Wholemeal Bread",
            servingSize = "2 slices (60g)",
            servingQuantity = JsonPrimitive(400.0)
        )
        val presets = derivePortionPresets(bread)
        assertEquals(4, presets.size)
        assertEquals("50 g", presets[0].label)
        assertEquals(50.0, presets[0].amount, 0.001)
        assertEquals("100 g", presets[1].label)
        assertEquals(100.0, presets[1].amount, 0.001)
        assertEquals("250 g", presets[2].label)
        assertEquals(250.0, presets[2].amount, 0.001)
        assertEquals("2 slices (60g)", presets[3].label)
        assertEquals(60.0, presets[3].amount, 0.001)
    }

    @Test
    fun `generic foods library has exactly 120 items and validates spot checks and search queries`() {
        val list = OpenFoodFactsClient.loadGenericFoods()
        assertEquals("Generic foods library must contain exactly 120 items", 120, list.size)

        // All entries have brand "Generic"
        assertTrue(list.all { it.brands == "Generic" })

        // Search hits:
        val chickenHits = list.filter { it.productName?.lowercase()?.contains("chicken") == true }
        assertTrue("chicken hits >= 3 (was ${chickenHits.size})", chickenHits.size >= 3)

        val milkHits = list.filter { it.productName?.lowercase()?.contains("milk") == true }
        assertTrue("milk hits >= 5 (was ${milkHits.size})", milkHits.size >= 5)

        val riceHits = list.filter { it.productName?.lowercase()?.contains("rice") == true }
        assertTrue("rice hits >= 3 (was ${riceHits.size})", riceHits.size >= 3)

        val breadHits = list.filter { it.productName?.lowercase()?.contains("bread") == true }
        assertTrue("bread hits >= 3 (was ${breadHits.size})", breadHits.size >= 3)

        // Spot-check values:
        // 1. Chicken breast == 106 kcal / 23.1 P
        val chickenBreast = list.first { it.productName.equals("Chicken breast", ignoreCase = true) }
        assertEquals(106.0, chickenBreast.basePer100g.kcal, 0.001)
        assertEquals(23.1, chickenBreast.basePer100g.protein, 0.001)
        assertEquals("g", chickenBreast.inferredUnit)

        // 2. Olive oil == 824 kcal
        val oliveOil = list.first { it.productName.equals("Olive oil", ignoreCase = true) }
        assertEquals(824.0, oliveOil.basePer100g.kcal, 0.001)
        assertEquals("ml", oliveOil.inferredUnit)

        // 3. Semi-skimmed milk == 47 / 3.6
        val milk = list.first { it.productName.equals("Semi-skimmed milk", ignoreCase = true) }
        assertEquals(47.0, milk.basePer100g.kcal, 0.001)
        assertEquals(3.6, milk.basePer100g.protein, 0.001)
        assertEquals("ml", milk.inferredUnit)

        // 4. Whey protein powder == 380 / 80.0
        val whey = list.first { it.productName.equals("Whey protein powder", ignoreCase = true) }
        assertEquals(380.0, whey.basePer100g.kcal, 0.001)
        assertEquals(80.0, whey.basePer100g.protein, 0.001)
        assertEquals("g", whey.inferredUnit)

        // Macro scaling maths on generic food item identical to OFF path (e.g. 200g of chicken breast -> 212 kcal, 46.2 P)
        val scaledChicken = scaleNutriments(chickenBreast.basePer100g, 200.0)
        assertEquals(212.0, scaledChicken.kcal, 0.001)
        assertEquals(46.2, scaledChicken.protein, 0.001)
    }

    @Test
    fun `fuzzy search matches typos and case variants and rejects noise`() {
        val list = OpenFoodFactsClient.loadGenericFoods()

        // 1. "cicken" hits chicken breast
        val cickenHits = filterFuzzy(list, "cicken") { it.productName }
        assertTrue("cicken should hit at least one item", cickenHits.isNotEmpty())
        assertTrue("cicken should hit chicken breast", cickenHits.any { it.productName.equals("Chicken breast", ignoreCase = true) })

        // 2. "miLk" hits milks
        val milkHits = filterFuzzy(list, "miLk") { it.productName }
        assertTrue("miLk should hit multiple milks", milkHits.size >= 5)
        assertTrue("miLk should hit Semi-skimmed milk", milkHits.any { it.productName.equals("Semi-skimmed milk", ignoreCase = true) })

        // 3. "cheese toasty" hits cheese
        val cheeseHits = filterFuzzy(list, "cheese toasty") { it.productName }
        assertTrue("cheese toasty should hit cheese items", cheeseHits.isNotEmpty())
        assertTrue("cheese toasty should hit Cheddar cheese", cheeseHits.any { it.productName.equals("Cheddar cheese", ignoreCase = true) })

        // 4. Random noise returns empty list (noise stays out)
        val noiseHits = filterFuzzy(list, "xyzqwe123456789") { it.productName }
        assertTrue("Random noise should return no hits", noiseHits.isEmpty())

        // 5. Capped at top 8 results
        val broadHits = filterFuzzy(list, "e", limit = 8) { it.productName }
        assertTrue("Broad hits capped at 8", broadHits.size <= 8)
    }

    @Test
    fun `stop-word set dropped from query tokens and all tokens dropped returns zero`() {
        // Query of only stop words returns 0.0
        assertEquals(0.0, fuzzyScore("and", "Chicken and rice"), 0.001)
        assertEquals(0.0, fuzzyScore("the", "The big cheese"), 0.001)
        assertEquals(0.0, fuzzyScore("in with of a", "Bowl with a salad"), 0.001)

        // Query with stop words drops stop words and matches content tokens
        val score = fuzzyScore("chicken and rice", "Chicken Rice Bowl")
        assertTrue("Query with stop words should match content tokens", score >= 0.6)
    }

    @Test
    fun `tokenised AND matches synthetic data`() {
        val syntheticFoods = listOf(
            ProductResult(code = "1", productName = "Cheese and Pickle Sandwich"),
            ProductResult(code = "2", productName = "Ham Sandwich"),
            ProductResult(code = "3", productName = "Cheddar Cheese Block")
        )
        val hits = filterFuzzy(syntheticFoods, "cheese sandwich", limit = 5, { it.productName })
        assertTrue("Tokenised AND matches item containing both tokens", hits.any { it.code == "1" })
    }

    @Test
    fun `multi-field match across productName brands and categories`() {
        val items = listOf(
            ProductResult(code = "1", productName = "Carb Killa", brands = "Grenade", categories = "High Protein Bars"),
            ProductResult(code = "2", productName = "Oat Bar", brands = "Flapjack Co", categories = "Cereal Bars")
        )

        // Match brand
        val brandHits = filterFuzzy(items, "grenade", limit = 5, { it.productName }, { it.brands }, { it.categories })
        assertEquals(1, brandHits.size)
        assertEquals("1", brandHits.first().code)

        // Match category
        val categoryHits = filterFuzzy(items, "cereal", limit = 5, { it.productName }, { it.brands }, { it.categories })
        assertEquals(1, categoryHits.size)
        assertEquals("2", categoryHits.first().code)
    }

    @Test
    fun `product with nutrition only per-serving normalises to per-100ml and scales correctly`() {
        val product = ProductResult(
            code = "vieve1",
            productName = "Vieve Protein Water",
            servingSize = "500 ml",
            servingQuantity = JsonPrimitive(500.0),
            nutriments = OffNutriments(
                energyKcal_serving = 85.0,
                proteins_serving = 20.0
            )
        )

        // 1. Per-100ml values normalised: 85 / 5 = 17 kcal, 20 / 5 = 4g protein
        assertEquals(17.0, product.basePer100g.kcal, 0.001)
        assertEquals(4.0, product.basePer100g.protein, 0.001)
        assertEquals("ml", product.inferredUnit)

        // 2. Correct scaling at 100 ml: 17 kcal, 4.0g protein
        val scaled100 = scaleNutriments(product.basePer100g, 100.0)
        assertEquals(17.0, scaled100.kcal, 0.001)
        assertEquals(4.0, scaled100.protein, 0.001)

        // 3. Correct scaling at 250 ml: 42.5 kcal, 10.0g protein
        val scaled250 = scaleNutriments(product.basePer100g, 250.0)
        assertEquals(42.5, scaled250.kcal, 0.001)
        assertEquals(10.0, scaled250.protein, 0.001)

        // 4. Correct scaling at 500 ml: 85 kcal, 20.0g protein
        val scaled500 = scaleNutriments(product.basePer100g, 500.0)
        assertEquals(85.0, scaled500.kcal, 0.001)
        assertEquals(20.0, scaled500.protein, 0.001)
    }

    @Test
    fun `product with proper 100g fields is unaffected by serving normalization`() {
        val product = ProductResult(
            code = "bar1",
            productName = "Protein Bar",
            servingSize = "50g",
            servingQuantity = JsonPrimitive(50.0),
            nutriments = OffNutriments(
                energyKcal_100g = 400.0,
                proteins_100g = 30.0,
                carbohydrates_100g = 40.0,
                fat_100g = 10.0,
                energyKcal_serving = 200.0,
                proteins_serving = 15.0
            )
        )

        assertEquals(400.0, product.basePer100g.kcal, 0.001)
        assertEquals(30.0, product.basePer100g.protein, 0.001)
        assertEquals("g", product.inferredUnit)

        val scaled50 = scaleNutriments(product.basePer100g, 50.0)
        assertEquals(200.0, scaled50.kcal, 0.001)
        assertEquals(15.0, scaled50.protein, 0.001)
    }

    @Test
    fun `unit label infers ml for liquid products and g for solid products`() {
        val drink = ProductResult(
            code = "drink1",
            productName = "Vieve Protein Water",
            servingSize = "500 ml",
            servingQuantity = JsonPrimitive(500.0)
        )
        assertEquals("ml", drink.inferredUnit)
        val drinkServingLabel = drink.servingSize ?: "${formatAmount(drink.servingQuantityOrFallback)} ${drink.inferredUnit}"
        assertTrue(drinkServingLabel.contains("ml"))

        val solid = ProductResult(
            code = "oats1",
            productName = "Rolled Oats",
            servingSize = "40g",
            servingQuantity = JsonPrimitive(40.0)
        )
        assertEquals("g", solid.inferredUnit)
        val solidServingLabel = solid.servingSize ?: "${formatAmount(solid.servingQuantityOrFallback)} ${solid.inferredUnit}"
        assertTrue(solidServingLabel.contains("g"))
    }

    @Test
    fun `preset derived from meal logged at 250ml re-applies 250ml not 100g`() {
        val meal = com.scatterbrain.scatterfit.ui.LoggedMeal(
            name = "Vieve Protein Water",
            kcal = 42.5,
            protein = 10.0,
            portionGrams = 250.0,
            portionLabel = "250 ml",
            dayKey = "2026-10-18"
        )
        val presets = com.scatterbrain.scatterfit.ui.deriveMealPresets(listOf(meal))
        assertEquals(1, presets.size)
        val preset = presets.first()
        assertEquals(250.0, preset.portionGrams!!, 0.001)
        assertEquals("250 ml", preset.portionLabel)

        // Convert to ProductResult for ConfirmProductCard re-application
        val product = preset.toProductResult()
        assertEquals(250.0, product.servingQuantityOrFallback, 0.001)
        assertEquals("ml", product.inferredUnit)
        assertEquals("250 ml", product.servingSize)

        // Normalised per-100ml: 42.5 * (100 / 250) = 17 kcal, 10.0 * (100 / 250) = 4g P
        assertEquals(17.0, product.basePer100g.kcal, 0.001)
        assertEquals(4.0, product.basePer100g.protein, 0.001)

        // Scaled at 250ml re-applies the original 42.5 kcal and 10g protein
        val scaled = scaleNutriments(product.basePer100g, product.servingQuantityOrFallback)
        assertEquals(42.5, scaled.kcal, 0.001)
        assertEquals(10.0, scaled.protein, 0.001)
    }
}
