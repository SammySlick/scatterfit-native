package com.scatterbrain.scatterfit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodRankingTest {

    private fun off(name: String, brand: String = "", kcal100: Double = 250.0) = ProductResult(
        code = name.hashCode().toString(),
        productName = name,
        brands = brand,
        nutriments = OffNutriments(
            energyKcal_100g = kcal100,
            proteins_100g = 5.0,
            carbohydrates_100g = 30.0,
            fat_100g = 10.0,
        ),
    )

    private val library: List<RestaurantFoodItem> by lazy { RestaurantFoods.load(null) }

    // --- brand detection -------------------------------------------------

    @Test
    fun `detects brand aliases`() {
        assertEquals("McDonald's", FoodRanking.detectBrand("mcdonalds fries").first)
        assertEquals("McDonald's", FoodRanking.detectBrand("mcd fries").first)
        assertEquals("KFC", FoodRanking.detectBrand("kfc zinger burger").first)
        assertEquals("Nando's", FoodRanking.detectBrand("nandos half chicken").first)
        assertEquals("sausage roll", FoodRanking.detectBrand("sausage roll").second)
    }

    @Test
    fun `brand remainder strips the brand token`() {
        assertEquals("zinger burger", FoodRanking.detectBrand("kfc zinger burger").second)
    }

    // --- the bundled library ---------------------------------------------

    @Test
    fun `library loads and covers the main chains`() {
        assertTrue("library should load", library.size > 500)
        val brands = library.map { it.brand }.toSet()
        listOf("McDonald's", "KFC", "Greggs", "Nando's", "Subway", "Costa").forEach {
            assertTrue("missing brand $it", brands.contains(it))
        }
    }

    @Test
    fun `every item converts to a product with its published kcal per serving`() {
        val bad = library.map { it.toProductResult() to it }
            .filter { (product, item) -> Math.abs(product.kcalPerServing - item.kcal) > 1.0 }
        assertEquals("convertible items off by more than 1 kcal: ${bad.take(3).map { it.second.name }}", 0, bad.size)
    }

    // --- restaurant ranking ----------------------------------------------

    @Test
    fun `zinger burger returns the KFC zinger first`() {
        val first = FoodRanking.rankRestaurants("kfc zinger burger", library).firstOrNull()
        assertEquals("Zinger Burger", first?.name)
        assertEquals("KFC", first?.brand)
        assertEquals(468.0, first?.kcal ?: 0.0, 0.5)
    }

    @Test
    fun `big mac returns the McDonald's Big Mac`() {
        val first = FoodRanking.rankRestaurants("big mac", library).firstOrNull()
        assertEquals("Big Mac", first?.name)
        assertEquals("McDonald's", first?.brand)
    }

    @Test
    fun `greggs sausage roll returns Greggs, not another chain`() {
        val results = FoodRanking.rankRestaurants("greggs sausage roll", library)
        assertEquals("Greggs", results.firstOrNull()?.brand)
        assertTrue(results.all { it.brand == "Greggs" })
    }

    @Test
    fun `steak bake returns only Greggs items`() {
        val results = FoodRanking.rankRestaurants("steak bake", library)
        assertEquals("Steak Bake", results.firstOrNull()?.name)
        assertTrue("leaked other brands: ${results.map { it.brand }}", results.all { it.brand == "Greggs" })
    }

    @Test
    fun `porridge oats returns nothing rather than a mismatched chain item`() {
        // Regression: an earlier coverage rule accepted any name token that was a substring
        // of a query token, so "a" in "Choc-A-Lot" matched; "porridge oats" returned a
        // McDonald's Filet-O-Fish.
        val results = FoodRanking.rankRestaurants("porridge oats", library)
        assertTrue("unexpected: ${results.map { it.brand + " " + it.name }}", results.isEmpty())
    }

    @Test
    fun `nandos half chicken returns the half chicken`() {
        val first = FoodRanking.rankRestaurants("nandos half chicken", library).firstOrNull()
        assertEquals("1/2 Chicken", first?.name)
    }

    @Test
    fun `cross chain search does not return unrelated chains`() {
        val results = FoodRanking.rankRestaurants("chicken breast", library)
        assertTrue("Burger King leaked into chicken breast", results.none { it.brand == "Burger King" })
    }

    // --- OFF re-ranking ---------------------------------------------------

    @Test
    fun `exact product name outranks a longer two token match`() {
        // Regression: two matched tokens scored 1.5 and an exact substring only 1.2, so
        // "big daddy mac mix" was promoted above a product actually called "Big Mac".
        val products = listOf(off("big daddy mac mix"), off("Big Mac"), off("Big Mac Sauce"))
        val ranked = FoodRanking.rankOff("big mac", products)
        assertEquals("Big Mac", ranked.first().productName)
    }

    @Test
    fun `brand query drops products that only mention the brand`() {
        val products = listOf(
            off("KFC Zinger crisps", "Lays"),
            off("Zinger Burger", "KFC"),
            off("Ginger shot"),
        )
        val ranked = FoodRanking.rankOff("kfc zinger burger", products)
        assertEquals("Zinger Burger", ranked.first().productName)
    }

    @Test
    fun `rankOff never returns an empty list when given products`() {
        val products = listOf(off("unrelated thing"), off("another thing"))
        assertEquals(2, FoodRanking.rankOff("zzingerr", products).size)
    }

    @Test
    fun `rankOff keeps Open Food Facts order for a single result`() {
        val only = listOf(off("Greek Yogurt"))
        assertEquals("Greek Yogurt", FoodRanking.rankOff("greek yogurt", only).first().productName)
    }
}
