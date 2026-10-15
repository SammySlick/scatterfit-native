package com.scatterbrain.scatterfit.ui

import com.scatterbrain.scatterfit.data.Daily
import com.scatterbrain.scatterfit.data.RecordMethod
import com.scatterbrain.scatterfit.data.Zones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class FoodLoggerTest {

    private val today = "2026-10-14"
    private val defaultZone = ZoneId.of("UTC")
    private val nowMs = java.time.LocalDate.parse("2026-10-14").atTime(12, 0).atZone(defaultZone).toInstant().toEpochMilli()

    @Test
    fun `logging a meal updates the day caloriesEaten and macros`() {
        val meal = LoggedMeal(
            id = "meal-1",
            name = "Chicken wrap",
            kcal = 450.0,
            protein = 35.0,
            carbs = 40.0,
            fat = 15.0,
            timestamp = nowMs,
            dayKey = today
        )

        val record = meal.toHealthRecord()
        val records = mapOf(RecordMethod.FOOD_LOG to listOf(record))
        val daily = Daily.buildDailyDataFromMaps(
            records = records,
            zones = Zones.DEFAULT_ZONES,
            zone = defaultZone,
            nowMs = nowMs
        )

        assertEquals(450.0, daily.caloriesEaten[today] ?: 0.0, 0.001)
        assertEquals(35.0, daily.protein[today] ?: 0.0, 0.001)
        assertEquals(40.0, daily.carbs[today] ?: 0.0, 0.001)
        assertEquals(15.0, daily.fat[today] ?: 0.0, 0.001)

        val dayMeals = daily.meals[today]
        assertEquals(1, dayMeals?.size ?: 0)
        assertEquals("Chicken wrap", dayMeals?.first()?.name)
        assertEquals("app", dayMeals?.first()?.source)
    }

    @Test
    fun `deleting a meal removes its contribution`() {
        val meal1 = LoggedMeal(
            id = "meal-1",
            name = "Oatmeal",
            kcal = 300.0,
            protein = 15.0,
            timestamp = nowMs,
            dayKey = today
        )
        val meal2 = LoggedMeal(
            id = "meal-2",
            name = "Protein Shake",
            kcal = 250.0,
            protein = 30.0,
            timestamp = nowMs + 1000L,
            dayKey = today
        )

        // Both meals logged: 550 kcal, 45g protein
        val recordsBoth = mapOf(RecordMethod.FOOD_LOG to listOf(meal1.toHealthRecord(), meal2.toHealthRecord()))
        val dailyBoth = Daily.buildDailyDataFromMaps(
            records = recordsBoth,
            zones = Zones.DEFAULT_ZONES,
            zone = defaultZone,
            nowMs = nowMs + 2000L
        )
        assertEquals(550.0, dailyBoth.caloriesEaten[today] ?: 0.0, 0.001)
        assertEquals(45.0, dailyBoth.protein[today] ?: 0.0, 0.001)

        // Delete meal2 -> only meal1 remains: 300 kcal, 15g protein
        val recordsAfterDelete = mapOf(RecordMethod.FOOD_LOG to listOf(meal1.toHealthRecord()))
        val dailyAfterDelete = Daily.buildDailyDataFromMaps(
            records = recordsAfterDelete,
            zones = Zones.DEFAULT_ZONES,
            zone = defaultZone,
            nowMs = nowMs + 3000L
        )
        assertEquals(300.0, dailyAfterDelete.caloriesEaten[today] ?: 0.0, 0.001)
        assertEquals(15.0, dailyAfterDelete.protein[today] ?: 0.0, 0.001)
    }

    @Test
    fun `preset derivation duplicates dedupe, recency ordering, cap at 8`() {
        val list = mutableListOf<LoggedMeal>()

        // 3 duplicate meals at different timestamps
        list.add(LoggedMeal(name = "Oatmeal", kcal = 300.0, protein = 10.0, timestamp = 100L, dayKey = today))
        list.add(LoggedMeal(name = "Oatmeal", kcal = 300.0, protein = 10.0, timestamp = 200L, dayKey = today))
        list.add(LoggedMeal(name = "Oatmeal", kcal = 300.0, protein = 10.0, timestamp = 300L, dayKey = today))

        // 10 other distinct meals
        for (i in 1..10) {
            list.add(LoggedMeal(name = "Meal $i", kcal = 200.0 + i, protein = 10.0 + i, timestamp = (400L + i * 10), dayKey = today))
        }

        val presets = deriveMealPresets(list, limit = 8)

        // Cap at 8
        assertEquals(8, presets.size)

        // Deduplication: "Oatmeal" should only be present once with timestamp 300L
        val oatmealPresets = presets.filter { it.name == "Oatmeal" }
        assertEquals(0, oatmealPresets.size) // Oatmeal is older than the top 8 (Meal 10 to Meal 3 have timestamps 500..430, while Oatmeal is 300)

        // Test with Oatmeal as most recent:
        list.add(LoggedMeal(name = "Oatmeal", kcal = 300.0, protein = 10.0, timestamp = 2000L, dayKey = today))
        val presetsWithRecentOatmeal = deriveMealPresets(list, limit = 8)
        assertEquals(8, presetsWithRecentOatmeal.size)
        val recentOatmeal = presetsWithRecentOatmeal.filter { it.name == "Oatmeal" }
        assertEquals(1, recentOatmeal.size)
        assertEquals(2000L, recentOatmeal.first().lastUsedTimestamp)
        assertEquals("Oatmeal", presetsWithRecentOatmeal.first().name)

        // Verify strictly descending timestamp ordering
        for (i in 0 until presetsWithRecentOatmeal.size - 1) {
            assertTrue(presetsWithRecentOatmeal[i].lastUsedTimestamp >= presetsWithRecentOatmeal[i + 1].lastUsedTimestamp)
        }
    }

    @Test
    fun `preset tap pre-fills but does NOT auto-save`() {
        val vm = FoodTrackerViewModel(storage = null, initialDayKey = today)

        val preset = MealPreset(
            name = "Protein Bar",
            kcal = 220.0,
            protein = 20.0,
            carbs = 15.0,
            fat = 7.0,
            lastUsedTimestamp = 1000L
        )

        // Tap preset
        vm.applyPreset(preset)

        // Pre-fills input fields
        assertEquals("Protein Bar", vm.mealName)
        assertEquals("220", vm.kcalInput)
        assertEquals("20", vm.proteinInput)
        assertEquals("15", vm.carbsInput)
        assertEquals("7", vm.fatInput)

        // Does NOT auto-save: mealsForDay must still be empty!
        assertTrue(vm.mealsForDay.value.isEmpty())

        // Saving explicitly saves the meal
        val saved = vm.saveCurrentMeal()
        assertTrue(saved)
        assertEquals(1, vm.mealsForDay.value.size)
        assertEquals("Protein Bar", vm.mealsForDay.value.first().name)
        assertEquals(220.0, vm.mealsForDay.value.first().kcal, 0.001)
    }

    @Test
    fun `updateLocalFoodLog twice replaces previous local food without duplicating`() {
        val mealA = LoggedMeal(id = "a", name = "A", kcal = 100.0, dayKey = today).toHealthRecord()
        val mealB = LoggedMeal(id = "b", name = "B", kcal = 200.0, dayKey = today).toHealthRecord()

        com.scatterbrain.scatterfit.sync.SyncHub.updateLocalFoodLog(listOf(mealA))
        val first = com.scatterbrain.scatterfit.sync.SyncHub.records.value?.get(RecordMethod.FOOD_LOG) ?: emptyList()
        assertEquals(listOf(mealA), first)

        com.scatterbrain.scatterfit.sync.SyncHub.updateLocalFoodLog(listOf(mealB))
        val second = com.scatterbrain.scatterfit.sync.SyncHub.records.value?.get(RecordMethod.FOOD_LOG) ?: emptyList()
        assertEquals(listOf(mealB), second)
    }
    @Test
    fun `updateLocalWeights twice replaces previous local weights without duplicating`() {
        val wA = LoggedWeight(kg = 91.0, dayKey = today).toHealthRecords()
        val wB = LoggedWeight(kg = 92.0, dayKey = today).toHealthRecords()

        com.scatterbrain.scatterfit.sync.SyncHub.updateLocalWeights(wA)
        val first = com.scatterbrain.scatterfit.sync.SyncHub.records.value
            ?.get(com.scatterbrain.scatterfit.data.RecordMethod.WEIGHT) ?: emptyList()
        assertEquals(wA, first)

        com.scatterbrain.scatterfit.sync.SyncHub.updateLocalWeights(wB)
        val second = com.scatterbrain.scatterfit.sync.SyncHub.records.value
            ?.get(com.scatterbrain.scatterfit.data.RecordMethod.WEIGHT) ?: emptyList()
        assertEquals(wB, second)
    }

    @Test
    fun `weigh-in record lands in daily weight and body fat maps`() {
        val recs = LoggedWeight(kg = 91.8, bodyFatPct = 22.5, timestamp = nowMs, dayKey = today).toHealthRecords()
        val records = mapOf(
            RecordMethod.WEIGHT to recs.filter { it.data?.get("weight") != null },
            RecordMethod.BODY_FAT to recs.filter { it.data?.get("percentage") != null }
        )
        val daily = Daily.buildDailyDataFromMaps(
            records = records,
            zones = Zones.DEFAULT_ZONES,
            zone = defaultZone,
            nowMs = nowMs
        )
        assertEquals(91.8, daily.weight[today] ?: 0.0, 0.001)
        assertEquals(22.5, daily.bodyFat[today] ?: 0.0, 0.001)
    }

    @Test
    fun `logged drinks land in alcoholUnits, alcoholKcal and caloriesEaten`() {
        val drink = LoggedDrink(
            id = "d1",
            drink = DrinkReference(name = "Pint of IPA", units = 2.1, kcals = 239),
            timestamp = nowMs,
            dayKey = today
        )
        val daily = Daily.buildDailyDataFromMaps(
            records = mapOf(RecordMethod.ALCOHOL to listOf(drink.toHealthRecord())),
            zones = Zones.DEFAULT_ZONES,
            zone = defaultZone,
            nowMs = nowMs
        )
        assertEquals(2.1, daily.alcoholUnits[today] ?: 0.0, 0.001)
        assertEquals(239.0, daily.alcoholKcal[today] ?: 0.0, 0.001)
        // A pint is energy in: drink kcal joins calories eaten (MFP parity).
        assertEquals(239.0, daily.caloriesEaten[today] ?: 0.0, 0.001)
        assertTrue(daily.drinks[today] == true)
    }

    @Test
    fun `two drinks accumulate units and kcal without doubling`() {
        val a = LoggedDrink(id = "a", drink = DrinkReference(name = "Pint", units = 2.0, kcals = 200), timestamp = nowMs, dayKey = today).toHealthRecord()
        val b = LoggedDrink(id = "b", drink = DrinkReference(name = "Pint", units = 2.0, kcals = 200), timestamp = nowMs, dayKey = today).toHealthRecord()
        val daily = Daily.buildDailyDataFromMaps(
            records = mapOf(RecordMethod.ALCOHOL to listOf(a, b)),
            zones = Zones.DEFAULT_ZONES,
            zone = defaultZone,
            nowMs = nowMs
        )
        assertEquals(4.0, daily.alcoholUnits[today] ?: 0.0, 0.001)
        assertEquals(400.0, daily.caloriesEaten[today] ?: 0.0, 0.001)
    }
}
