package com.scatterbrain.scatterfit.data

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    fun `derivePortionPresets includes Whole, Half, 100g, and sane household serving`() {
        val bread = ProductResult(
            code = "bread1",
            productName = "Wholemeal Bread",
            servingSize = "2 slices (60g)",
            servingQuantity = JsonPrimitive(400.0)
        )
        val presets = derivePortionPresets(bread)
        assertEquals(4, presets.size)
        assertEquals("Whole (400 g)", presets[0].label)
        assertEquals(400.0, presets[0].amount, 0.001)
        assertEquals("Half (200 g)", presets[1].label)
        assertEquals(200.0, presets[1].amount, 0.001)
        assertEquals("100 g", presets[2].label)
        assertEquals(100.0, presets[2].amount, 0.001)
        assertEquals("2 slices (60g)", presets[3].label)
        assertEquals(60.0, presets[3].amount, 0.001)
    }
}
