package com.scatterbrain.scatterfit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestaurantMenuTest {

    private fun item(
        id: String,
        brand: String,
        name: String,
        kcal: Double = 400.0,
        protein: Double? = 20.0,
    ) = RestaurantMenuItem(id = id, brand = brand, name = name, kcal = kcal, protein = protein)

    private val costaPanini = item("costa-tuna-melt-panini", "Costa", "Tuna Melt Panini", 462.0, 26.0)
    private val costaLatte = item("costa-latte", "Costa", "Latte", 204.0, 10.0)
    private val costaChai = item("costa-chai-latte", "Costa", "Chai Latte", 231.0, 8.0)
    private val greggsSteak = item("greggs-steak-bake", "Greggs", "Steak Bake", 428.0, 15.0)
    private val greggsSausage = item("greggs-sausage-roll", "Greggs", "Sausage Roll", 329.0, 9.0)
    private val greggsBreakfast = item("greggs-sausage-breakfast-roll", "Greggs", "Sausage Breakfast Roll", 400.0, 14.0)
    private val kfcZinger = item("kfc-zinger-burger", "KFC", "Zinger Burger", 468.0, 23.0)
    private val kfcStacker = item("kfc-zinger-stacker", "KFC", "Zinger Stacker", 610.0, 30.0)
    private val mcdBigMac = item("mcd-big-mac", "McDonald's", "Big Mac", 509.0, 27.0)
    private val pretLatte = item("pret-latte", "Pret", "Latte", 190.0, 10.0)

    private val all = listOf(
        costaPanini, costaLatte, costaChai, greggsSteak, greggsSausage,
        greggsBreakfast, kfcZinger, kfcStacker, mcdBigMac, pretLatte
    )

    @Test
    fun `brand plus dish finds the dish - the case that made Open Food Facts useless`() {
        val hits = RestaurantMenu.search(all, "costa tuna melt")
        assertEquals("Tuna Melt Panini", hits.first().name)
        assertEquals("Costa", hits.first().brand)
    }

    @Test
    fun `a noisy query still lands because every menu word is on the device`() {
        // "coffee" is nowhere in the panini's text, so this is 3 of 4 words - enough.
        val hits = RestaurantMenu.search(all, "costa coffee tuna melt")
        assertEquals("costa-tuna-melt-panini", hits.first().id)
    }

    @Test
    fun `a brand-only query shows that brand and nothing else`() {
        val hits = RestaurantMenu.search(all, "greggs")
        assertEquals(3, hits.size)
        assertTrue(hits.all { it.brand == "Greggs" })
    }

    @Test
    fun `a single shared word does not drag the whole menu in`() {
        val hits = RestaurantMenu.search(all, "burger")
        assertEquals(listOf("kfc-zinger-burger"), hits.map { it.id })
    }

    @Test
    fun `typos still match - including the transposed kind thumbs make`() {
        assertEquals("greggs-steak-bake", RestaurantMenu.search(all, "greggs staek bake").firstOrNull()?.id)
    }

    @Test
    fun `the short exact name beats a longer one containing it`() {
        // "Costa Chai Latte" matches every word of "costa latte" too, so without the
        // name-coverage term it could outrank the plain Latte that was actually meant.
        val hits = RestaurantMenu.search(all, "costa latte")
        assertEquals("Latte", hits.first().name)
    }

    @Test
    fun `a chain query never returns another chain's food`() {
        // Found live: "m&s prawn mayo" surfaced a Costa Prawn Mayo Sandwich, because
        // "prawn mayo" is two thirds of the words and the scorer didn't check who
        // made it. No M&S items are bundled yet, so the honest answer is nothing.
        assertTrue(RestaurantMenu.search(all, "m&s prawn mayo").isEmpty())
        assertTrue(RestaurantMenu.search(all, "nandos chicken").isEmpty())
        assertEquals(
            setOf("Costa"),
            RestaurantMenu.search(all, "costa latte").map { it.brand }.toSet()
        )
    }

    @Test
    fun `brand aliases resolve to the canonical brand`() {
        assertEquals(setOf("McDonald's"), RestaurantMenu.brandsInQuery("maccies big mac"))
        assertEquals(setOf("McDonald's"), RestaurantMenu.brandsInQuery("mcdonalds fries"))
        assertEquals(setOf("M&S Food"), RestaurantMenu.brandsInQuery("m&s prawn mayo"))
        assertTrue(RestaurantMenu.brandsInQuery("chicken burger").isEmpty())
    }

    @Test
    fun `an exactly answered multi-word query narrows to that chain`() {
        // Without this, "big mac" also offered Greggs' Mac & Cheese.
        val hits = RestaurantMenu.search(all, "big mac")
        assertEquals("Big Mac", hits.first().name)
        assertTrue(hits.all { it.brand == "McDonald's" })
    }

    @Test
    fun `one-word queries stay open across brands`() {
        // Every chain sells a latte; which one depends on where Sam is standing.
        val hits = RestaurantMenu.search(all, "latte")
        assertTrue(hits.map { it.brand }.toSet().size > 1)
    }

    @Test
    fun `empty and whitespace queries return nothing rather than everything`() {
        assertTrue(RestaurantMenu.search(all, "").isEmpty())
        assertTrue(RestaurantMenu.search(all, "   ").isEmpty())
        assertTrue(RestaurantMenu.search(all, "a").isEmpty())
    }

    @Test
    fun `results are capped`() {
        val wide = (1..30).map { item("costa-$it", "Costa", "Latte $it") }
        assertTrue(RestaurantMenu.search(wide, "costa latte", limit = 5).size <= 5)
    }

    @Test
    fun `display name carries the brand so a logged meal is identifiable later`() {
        assertEquals("Costa — Tuna Melt Panini", costaPanini.displayName)
    }

    @Test
    fun `missing macros parse as null instead of zero`() {
        val json = """
        {"version":1,"items":[
          {"id":"mcd-big-mac","brand":"McDonald's","name":"Big Mac","serving":"1 burger","kcal":509,"protein":27}
        ]}
        """.trimIndent()
        val parsed = RestaurantMenu.parse(json)
        assertEquals(1, parsed.size)
        assertEquals(509.0, parsed[0].kcal, 0.001)
        assertEquals(27.0, parsed[0].protein!!, 0.001)
        assertEquals("fat must stay null, not become 0", null, parsed[0].fat)
        assertEquals("carbs must stay null, not become 0", null, parsed[0].carbs)
    }

    @Test
    fun `a corrupt menu file degrades to empty rather than crashing the food screen`() {
        assertTrue(RestaurantMenu.parse("{not json at all").isEmpty())
        assertTrue(RestaurantMenu.parse("").isEmpty())
    }

    /**
     * The real shipped library, read straight from src/main/res/raw. No copy in
     * test/resources on purpose - a copy silently goes stale the moment someone
     * edits the menu, and then the tests are checking a file that no longer ships.
     */
    private val bundled: List<RestaurantMenuItem> by lazy { RestaurantMenu.load() }

    @Test
    fun `the bundled menu file is readable from the module root`() {
        assertTrue(
            "restaurant_foods.json not found; looked from ${java.io.File(".").absolutePath}",
            bundled.isNotEmpty()
        )
    }

    @Test
    fun `every bundled item is sane`() {
        org.junit.Assume.assumeTrue(bundled.isNotEmpty())
        assertTrue("bundled menu should not be empty", bundled.size > 100)

        bundled.forEach { i ->
            assertTrue("${i.id} needs a brand", i.brand.isNotBlank())
            assertTrue("${i.id} needs a name", i.name.isNotBlank())
            assertTrue("${i.id} kcal out of range: ${i.kcal}", i.kcal in 1.0..3000.0)
            i.protein?.let { assertTrue("${i.id} protein absurd: $it", it in 0.0..200.0) }
            i.carbs?.let { assertTrue("${i.id} carbs absurd: $it", it in 0.0..400.0) }
            i.fat?.let { assertTrue("${i.id} fat absurd: $it", it in 0.0..200.0) }
            // A row with all three macros must agree with Atwater, or the columns were
            // transposed upstream. The tolerance is 8 kcal or 20%, whichever is bigger:
            // every real item in the bundled menu sits within 8% (worst: Greggs savoury
            // mince pie, 860 kcal, 7.9%), and the absolute floor covers small items where
            // fibre calories and rounding dominate - the 16 kcal side salad lands 4.8 kcal
            // out, which is 30% of nothing. Injected transposition and typo faults are
            // caught 75% of the time, which is what makes this worth keeping: a silent
            // column swap on a 400 kcal lunch is the bug this is here for.
            val p = i.protein
            val c = i.carbs
            val f = i.fat
            if (p != null && c != null && f != null) {
                val calc = p * 4 + c * 4 + f * 9
                assertTrue(
                    "${i.id}: macros say ${calc.toInt()} kcal but the item says ${i.kcal.toInt()}",
                    kotlin.math.abs(calc - i.kcal) <= maxOf(8.0, i.kcal * 0.2)
                )
            }
        }

        assertEquals("ids must be unique", bundled.size, bundled.map { it.id }.toSet().size)
        assertTrue(
            "every bundled brand must be spellable by a brand alias, or its food can never be found",
            bundled.map { it.brand }.toSet().all { it in RestaurantMenu.supportedBrands }
        )
    }

    @Test
    fun `the Costa tuna melt Sam could not find is actually in the bundled menu`() {
        org.junit.Assume.assumeTrue(bundled.isNotEmpty())
        val hits = RestaurantMenu.search(bundled, "costa coffee tuna melt")
        assertFalse("bundled menu must answer this query", hits.isEmpty())
        assertEquals("Tuna Melt Panini", hits.first().name)
        assertEquals("Costa", hits.first().brand)
    }

    @Test
    fun `the bundled menu answers the everyday queries without cross-brand noise`() {
        org.junit.Assume.assumeTrue(bundled.isNotEmpty())
        listOf(
            "costa tuna melt" to "Costa",
            "greggs sausage roll" to "Greggs",
            "kfc zinger burger" to "KFC",
            "mcdonalds big mac" to "McDonald's",
            "pret chicken caesar" to "Pret",
            "nandos chicken burger" to "Nando's",
        ).forEach { (query, brand) ->
            val hits = RestaurantMenu.search(bundled, query)
            assertFalse("no hits for '$query'", hits.isEmpty())
            assertEquals("'$query' should answer with $brand", brand, hits.first().brand)
        }
    }

    @Test
    fun `no query ever answers with a chain it did not name`() {
        org.junit.Assume.assumeTrue(bundled.isNotEmpty())
        listOf(
            "costa tuna melt" to "Costa",
            "greggs sausage roll" to "Greggs",
            "kfc zinger burger" to "KFC",
            "mcdonalds big mac" to "McDonald's",
            "pret chicken caesar" to "Pret",
            "nandos chips" to "Nando's",
        ).forEach { (query, brand) ->
            val brands = RestaurantMenu.search(bundled, query).map { it.brand }.toSet()
            assertTrue("'$query' leaked ${brands - brand}", brands.all { it == brand })
        }
    }
}
