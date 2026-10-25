package com.scatterbrain.scatterfit.ui

import com.scatterbrain.scatterfit.data.filterFuzzy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        val clock1 = java.time.Clock.fixed(fixed1, zone)
        val todayStr = com.scatterbrain.scatterfit.core.todayKey(clock1)
        val vm = DrinkTrackerViewModel(
            initialDayKey = todayStr,
            clock = clock1
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
        val clockToday = java.time.Clock.fixed(todayInstant, zone)
        val pastDay = com.scatterbrain.scatterfit.core.todayKey(
            java.time.Clock.fixed(todayInstant.minus(java.time.Duration.ofDays(4)), zone)
        )

        val vm = DrinkTrackerViewModel(
            initialDayKey = pastDay,
            clock = clockToday
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

    @Test
    fun `round-trip - LoggedDrink with NaN cost written then read comes back repaired when library resolves`() {
        val whiteClaw = DrinkReference(
            id = "white-claw-all-flavours--330ml-can",
            name = "White Claw Hard Seltzer (All Flavours)",
            units = 1.5,
            kcals = 95,
            cost = 2.06
        )
        val storage = DrinkStorage(
            libraryLookup = { id -> if (id == whiteClaw.id) whiteClaw else null }
        )

        // Simulate corrupted entry stored with NaN cost
        val corruptDrink = whiteClaw.copy(cost = Double.NaN)
        val logged = LoggedDrink(
            id = "entry-1",
            drink = corruptDrink,
            timestamp = 1729814400000L,
            dayKey = "2026-10-25"
        )
        // saveDrink validates on write and repairs from library lookup
        storage.saveDrink(logged)

        val loaded = storage.loadLoggedDrinks("2026-10-25")
        assertEquals(1, loaded.size)
        val drink = loaded.first()
        assertEquals(1.5, drink.drink.units, 0.001)
        assertEquals(95, drink.drink.kcals)
        assertEquals(2.06, drink.drink.cost, 0.001)
        assertFalse(drink.drink.cost.isNaN())
    }

    @Test
    fun `round-trip - LoggedDrink with NaN cost written then read comes back dropped when library does not resolve`() {
        val storage = DrinkStorage(
            libraryLookup = { null }
        )

        val corruptDrink = DrinkReference(
            id = "unknown-drink-id",
            name = "Unknown Corrupted",
            units = 1.5,
            kcals = 95,
            cost = Double.NaN
        )
        val logged = LoggedDrink(
            id = "entry-2",
            drink = corruptDrink,
            timestamp = 1729814400000L,
            dayKey = "2026-10-25"
        )
        storage.saveDrink(logged)

        val loaded = storage.loadLoggedDrinks("2026-10-25")
        assertTrue("Entry with NaN cost and unresolvable library ID must be dropped", loaded.isEmpty())
    }

    @Test
    fun `bounds tests - units must be finite and between 0 and 100`() {
        assertFalse("units 0.0 is invalid", isValidDrinkUnits(0.0))
        assertFalse("negative units is invalid", isValidDrinkUnits(-1.0))
        assertFalse("units 100.0 is invalid", isValidDrinkUnits(100.0))
        assertFalse("units > 100 is invalid", isValidDrinkUnits(105.0))
        assertFalse("NaN units is invalid", isValidDrinkUnits(Double.NaN))
        assertFalse("Infinity units is invalid", isValidDrinkUnits(Double.POSITIVE_INFINITY))
        assertFalse("-Infinity units is invalid", isValidDrinkUnits(Double.NEGATIVE_INFINITY))

        assertTrue("units 0.1 is valid", isValidDrinkUnits(0.1))
        assertTrue("units 1.5 is valid", isValidDrinkUnits(1.5))
        assertTrue("units 99.9 is valid", isValidDrinkUnits(99.9))
    }

    @Test
    fun `bounds tests - kcals must be between 0 and 5000`() {
        assertFalse("negative kcals is invalid", isValidDrinkKcals(-1))
        assertFalse("kcals 5000 is invalid", isValidDrinkKcals(5000))
        assertFalse("kcals > 5000 is invalid", isValidDrinkKcals(5001))

        assertTrue("kcals 0 is valid", isValidDrinkKcals(0))
        assertTrue("kcals 95 is valid", isValidDrinkKcals(95))
        assertTrue("kcals 4999 is valid", isValidDrinkKcals(4999))
    }

    @Test
    fun `bounds tests - cost must be finite and between 0 and 1000`() {
        assertFalse("negative cost is invalid", isValidDrinkCost(-0.01))
        assertFalse("cost 1000.0 is invalid", isValidDrinkCost(1000.0))
        assertFalse("cost > 1000 is invalid", isValidDrinkCost(1050.0))
        assertFalse("NaN cost is invalid", isValidDrinkCost(Double.NaN))
        assertFalse("Infinity cost is invalid", isValidDrinkCost(Double.POSITIVE_INFINITY))
        assertFalse("-Infinity cost is invalid", isValidDrinkCost(Double.NEGATIVE_INFINITY))

        assertTrue("cost 0.0 is valid", isValidDrinkCost(0.0))
        assertTrue("cost 2.06 is valid", isValidDrinkCost(2.06))
        assertTrue("cost 999.99 is valid", isValidDrinkCost(999.99))
    }

    @Test
    fun `bounds tests - timestamp must be strictly positive`() {
        assertFalse("timestamp 0L is invalid", isValidDrinkTimestamp(0L))
        assertFalse("timestamp < 0 is invalid", isValidDrinkTimestamp(-1000L))
        assertTrue("timestamp > 0 is valid", isValidDrinkTimestamp(1L))
        assertTrue("real epoch timestamp is valid", isValidDrinkTimestamp(1729814400000L))
    }

    @Test
    fun `edit-time path test with a valid entry stays intact`() {
        val whiteClaw = DrinkReference(
            id = "white-claw-all-flavours--330ml-can",
            name = "White Claw Hard Seltzer (All Flavours)",
            units = 1.5,
            kcals = 95,
            cost = 2.06
        )
        val initialTimestamp = java.time.Instant.parse("2026-10-24T18:00:00Z").toEpochMilli()
        val vm = DrinkTrackerViewModel(
            initialDayKey = "2026-10-24",
            initialLibrary = listOf(whiteClaw)
        )

        // Log initial valid entry
        vm.logDrink(whiteClaw, timestamp = initialTimestamp)
        val initialDrink = vm.dailyState.value.loggedDrinks.first()
        assertEquals(initialTimestamp, initialDrink.timestamp)
        assertEquals(1.5, initialDrink.drink.units, 0.001)
        assertEquals(95, initialDrink.drink.kcals)
        assertEquals(2.06, initialDrink.drink.cost, 0.001)

        // Edit time to 21:30
        val newTimestamp = java.time.Instant.parse("2026-10-24T21:30:00Z").toEpochMilli()
        vm.updateLoggedDrinkTime(initialDrink, newTimestamp)

        val updatedDrink = vm.dailyState.value.loggedDrinks.first()
        assertEquals(newTimestamp, updatedDrink.timestamp)
        assertEquals(1.5, updatedDrink.drink.units, 0.001)
        assertEquals(95, updatedDrink.drink.kcals)
        assertEquals(2.06, updatedDrink.drink.cost, 0.001)
    }

    @Test
    fun `validate on write refuses invalid entries when unresolvable`() {
        val vm = DrinkTrackerViewModel(initialDayKey = "2026-10-24")

        val nanCostDrink = DrinkReference(id = "custom-1", name = "Custom", units = 1.5, kcals = 95, cost = Double.NaN)
        val zeroUnitsDrink = DrinkReference(id = "custom-2", name = "Custom", units = 0.0, kcals = 95, cost = 2.0)
        val hugeKcalDrink = DrinkReference(id = "custom-3", name = "Custom", units = 1.5, kcals = 6000, cost = 2.0)

        assertFalse("Refuses drink with NaN cost", vm.logDrink(nanCostDrink))
        assertFalse("Refuses drink with 0 units", vm.logDrink(zeroUnitsDrink))
        assertFalse("Refuses drink with 6000 kcals", vm.logDrink(hugeKcalDrink))
        assertTrue("No drinks persisted into dailyState", vm.dailyState.value.loggedDrinks.isEmpty())
    }

    @Test
    fun `defensive render check only validates when all three fields are within bounds`() {
        val valid = DrinkReference(units = 1.5, kcals = 95, cost = 2.06)
        assertTrue(isValidDrinkDetails(valid))

        assertFalse(isValidDrinkDetails(valid.copy(units = Double.NaN)))
        assertFalse(isValidDrinkDetails(valid.copy(units = 0.0)))
        assertFalse(isValidDrinkDetails(valid.copy(units = 100.0)))

        assertFalse(isValidDrinkDetails(valid.copy(kcals = -1)))
        assertFalse(isValidDrinkDetails(valid.copy(kcals = 5000)))

        assertFalse(isValidDrinkDetails(valid.copy(cost = Double.NaN)))
        assertFalse(isValidDrinkDetails(valid.copy(cost = -1.0)))
        assertFalse(isValidDrinkDetails(valid.copy(cost = 1000.0)))
    }
}
