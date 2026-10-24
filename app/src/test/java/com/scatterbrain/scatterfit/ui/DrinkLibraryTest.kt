package com.scatterbrain.scatterfit.ui

import com.scatterbrain.scatterfit.data.filterFuzzy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DrinkLibraryTest {

    @Test
    fun `drinks-library json parses all drinks and contains White Claw and BuzzBallz`() {
        val file = File("src/main/assets/drinks-library.json")
        val content = file.readText()
        val drinks = DrinkLibraryParser.parseJson(content)

        // Ensure parser parses the full collection
        val whiteClaw = drinks.find { it.name == "White Claw Hard Seltzer (All Flavours)" }
        assertNotNull("White Claw should exist in library", whiteClaw)
        assertEquals(330, whiteClaw!!.volumeMl)
        assertEquals(4.5, whiteClaw.abv, 0.001)
        assertEquals(95, whiteClaw.kcals)
        assertEquals(2.06, whiteClaw.cost, 0.001)
        assertEquals(1.5, whiteClaw.units, 0.001)
        assertTrue(whiteClaw.aliases.contains("seltzer"))

        val buzzBallzColada = drinks.find { it.name == "BuzzBallz (Fruit Ritas / Colada / Espresso)" }
        assertNotNull(buzzBallzColada)
        assertEquals(200, buzzBallzColada!!.volumeMl)
        assertEquals(13.5, buzzBallzColada.abv, 0.001)
        assertEquals(265, buzzBallzColada.kcals)
        assertEquals(4.0, buzzBallzColada.cost, 0.001)
        assertEquals(2.7, buzzBallzColada.units, 0.001)

        val buzzBallzMartini = drinks.find { it.name == "BuzzBallz (Pornstar Martini)" }
        assertNotNull(buzzBallzMartini)
        assertEquals(200, buzzBallzMartini!!.volumeMl)
        assertEquals(13.5, buzzBallzMartini.abv, 0.001)
        assertEquals(288, buzzBallzMartini.kcals)
        assertEquals(4.0, buzzBallzMartini.cost, 0.001)
        assertEquals(2.7, buzzBallzMartini.units, 0.001)

        val buzzBallzMango = drinks.find { it.name == "BuzzBallz (Chili Mango)" }
        assertNotNull(buzzBallzMango)
        assertEquals(200, buzzBallzMango!!.volumeMl)
        assertEquals(15.0, buzzBallzMango.abv, 0.001)
        assertEquals(252, buzzBallzMango.kcals)
        assertEquals(4.0, buzzBallzMango.cost, 0.001)
        assertEquals(3.0, buzzBallzMango.units, 0.001)
    }

    @Test
    fun `drink search matches seltzer via alias and sletzer via fuzzy`() {
        val file = File("src/main/assets/drinks-library.json")
        val content = file.readText()
        val drinks = DrinkLibraryParser.parseJson(content)

        // "seltzer" via alias
        val seltzerHits = filterFuzzy(
            drinks, "seltzer", limit = 10,
            { it.name }, { it.brand }, { it.category }, { it.aliases.joinToString(" ") }
        )
        assertTrue("seltzer should hit White Claw via alias", seltzerHits.any { it.name.startsWith("White Claw") })

        // "sletzer" via fuzzy
        val sletzerHits = filterFuzzy(
            drinks, "sletzer", limit = 10,
            { it.name }, { it.brand }, { it.category }, { it.aliases.joinToString(" ") }
        )
        assertTrue("sletzer should hit White Claw via fuzzy match", sletzerHits.any { it.name.startsWith("White Claw") })
    }

    @Test
    fun `logging two drinks today produces distinct increasing timestamps`() {
        val zone = java.time.ZoneId.of("UTC")
        val fixed1 = java.time.Instant.parse("2026-10-24T18:45:00Z")
        val fixed2 = java.time.Instant.parse("2026-10-24T20:45:00Z")

        val todayStr = "2026-10-24"
        val vm = DrinkTrackerViewModel(
            initialDayKey = todayStr,
            clock = java.time.Clock.fixed(fixed1, zone)
        )
        val drink = DrinkReference(id = "brewdog", name = "Brewdog IPA", units = 2.0, kcals = 180)

        // Log 1st drink at 18:45
        vm.logDrink(drink)
        assertEquals(1, vm.dailyState.value.loggedDrinks.size)
        val firstTimestamp = vm.dailyState.value.loggedDrinks.first().timestamp
        assertEquals(fixed1.toEpochMilli(), firstTimestamp)

        // Advance clock by 2h and log 2nd drink
        vm.clock = java.time.Clock.fixed(fixed2, zone)
        vm.logDrink(drink)
        assertEquals(2, vm.dailyState.value.loggedDrinks.size)

        val logs = vm.dailyState.value.loggedDrinks.sortedBy { it.timestamp }
        assertTrue("Second drink timestamp must be after first drink", logs[1].timestamp > logs[0].timestamp)
        assertEquals(fixed1.toEpochMilli(), logs[0].timestamp)
        assertEquals(fixed2.toEpochMilli(), logs[1].timestamp)
    }

    @Test
    fun `retro-logging two drinks to a past day shares the last logged time`() {
        val zone = java.time.ZoneId.of("UTC")
        val todayInstant = java.time.Instant.parse("2026-10-24T12:00:00Z")
        val pastDay = "2026-10-20"

        val vm = DrinkTrackerViewModel(
            initialDayKey = pastDay,
            clock = java.time.Clock.fixed(todayInstant, zone)
        )
        val drink = DrinkReference(id = "brewdog", name = "Brewdog IPA", units = 2.0, kcals = 180)

        // Log 1st drink into past day with explicit timestamp at 19:30
        val explicitPastTime = java.time.Instant.parse("2026-10-20T19:30:00Z").toEpochMilli()
        vm.logDrink(drink, timestamp = explicitPastTime)

        // Log 2nd drink into past day without explicit timestamp
        vm.logDrink(drink)

        val logs = vm.dailyState.value.loggedDrinks
        assertEquals(2, logs.size)
        // Both share the 19:30 time of the day
        assertEquals(explicitPastTime, logs[0].timestamp)
        assertEquals(explicitPastTime, logs[1].timestamp)
    }

    @Test
    fun `count in dailyState matches exact count of logged drinks list`() {
        val vm = DrinkTrackerViewModel(initialDayKey = "2026-10-24")
        val drink1 = DrinkReference(id = "d1", name = "Beer 1", units = 2.0, kcals = 150)
        val drink2 = DrinkReference(id = "d2", name = "Beer 2", units = 2.0, kcals = 150)
        val drink3 = DrinkReference(id = "d3", name = "Beer 3", units = 2.0, kcals = 150)

        vm.logDrink(drink1)
        vm.logDrink(drink2)
        vm.logDrink(drink3)

        val state = vm.dailyState.value
        assertEquals(3, state.loggedDrinks.size)
    }
}
