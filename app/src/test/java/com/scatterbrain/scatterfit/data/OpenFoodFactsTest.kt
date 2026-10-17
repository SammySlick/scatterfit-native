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
}
