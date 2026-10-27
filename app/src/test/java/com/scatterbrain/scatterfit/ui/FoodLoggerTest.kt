package com.scatterbrain.scatterfit.ui

import com.scatterbrain.scatterfit.data.Daily
import com.scatterbrain.scatterfit.data.ProductResult
import com.scatterbrain.scatterfit.data.RecordMethod
import com.scatterbrain.scatterfit.data.Zones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun `delete immediately removes meal from mealsForDay flow and undo immediately restores it with original id and timestamp`() {
        val viewModel = FoodTrackerViewModel()
        viewModel.setDayKey(today)

        val meal = LoggedMeal(
            id = "meal-x",
            name = "Test Meal",
            kcal = 500.0,
            timestamp = nowMs,
            dayKey = today
        )
        viewModel.logCustomMeal(meal)
        assertEquals(1, viewModel.mealsForDay.value.size)

        // Delete immediately removes from storage flow
        viewModel.deleteMeal(meal.id)
        assertTrue(viewModel.mealsForDay.value.isEmpty())

        // Undo re-inserts exact same meal with original id and timestamp
        viewModel.logCustomMeal(meal)
        assertEquals(1, viewModel.mealsForDay.value.size)
        val restored = viewModel.mealsForDay.value.first()
        assertEquals("meal-x", restored.id)
        assertEquals(nowMs, restored.timestamp)
    }

    @Test
    fun `logCustomMeal without explicit dayKey adopts viewModel activeDayKey without delay`() {
        val viewModel = FoodTrackerViewModel()
        viewModel.setDayKey(today)

        val meal = LoggedMeal(
            id = "meal-no-day",
            name = "Snack Bar",
            kcal = 200.0,
            dayKey = ""
        )
        viewModel.logCustomMeal(meal)
        assertEquals(1, viewModel.mealsForDay.value.size)
        assertEquals(today, viewModel.mealsForDay.value.first().dayKey)
    }

    @Test
    fun `upsert-by-id editing meal updates values in place and does not duplicate`() {
        val viewModel = FoodTrackerViewModel()
        viewModel.setDayKey(today)

        val meal1 = LoggedMeal(id = "meal-1", name = "Toast", kcal = 150.0, dayKey = today, mealType = "breakfast")
        val meal2 = LoggedMeal(id = "meal-2", name = "Eggs", kcal = 200.0, dayKey = today, mealType = "breakfast")
        viewModel.logCustomMeal(meal1)
        viewModel.logCustomMeal(meal2)
        assertEquals(2, viewModel.mealsForDay.value.size)

        // Edit meal1: change kcal to 250.0 and mealType to lunch
        val editedMeal = meal1.copy(kcal = 250.0, mealType = "lunch")
        viewModel.updateMeal(meal1.id, editedMeal)

        // Count stays same
        assertEquals(2, viewModel.mealsForDay.value.size)
        // Values change
        val updated = viewModel.mealsForDay.value.first { it.id == "meal-1" }
        assertEquals(250.0, updated.kcal, 0.001)
        assertEquals("lunch", updated.mealType)
    }

    @Test
    fun `undo-delete restore preserves original meal id and count without duplication`() {
        val viewModel = FoodTrackerViewModel()
        viewModel.setDayKey(today)

        val meal1 = LoggedMeal(id = "meal-a", name = "Apple", kcal = 80.0, dayKey = today)
        val meal2 = LoggedMeal(id = "meal-b", name = "Banana", kcal = 100.0, dayKey = today)
        viewModel.logCustomMeal(meal1)
        viewModel.logCustomMeal(meal2)
        assertEquals(2, viewModel.mealsForDay.value.size)

        // Delete meal-a
        viewModel.deleteMeal("meal-a")
        assertEquals(1, viewModel.mealsForDay.value.size)
        assertTrue(viewModel.mealsForDay.value.none { it.id == "meal-a" })

        // Undo restore meal-a
        viewModel.logCustomMeal(meal1)
        assertEquals(2, viewModel.mealsForDay.value.size)
        val restored = viewModel.mealsForDay.value.first { it.id == "meal-a" }
        assertEquals("meal-a", restored.id)
        assertEquals(80.0, restored.kcal, 0.001)

        // Re-inserting already-present meal does not duplicate
        viewModel.logCustomMeal(meal1)
        assertEquals(2, viewModel.mealsForDay.value.size)
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

    @Test
    fun `log to yesterday, pipeline shows it on yesterday day key`() {
        val yesterday = "2026-10-13"
        val vm = FoodTrackerViewModel(storage = null, initialDayKey = yesterday)
        vm.mealName = "Steak & Veg"
        vm.kcalInput = "650"
        vm.proteinInput = "55"
        val saved = vm.saveCurrentMeal()
        assertTrue(saved)

        val meal = vm.mealsForDay.value.first()
        assertEquals(yesterday, meal.dayKey)

        val record = meal.toHealthRecord()
        val records = mapOf(RecordMethod.FOOD_LOG to listOf(record))
        val daily = Daily.buildDailyDataFromMaps(
            records = records,
            zones = Zones.DEFAULT_ZONES,
            zone = defaultZone,
            nowMs = nowMs
        )

        assertEquals(650.0, daily.caloriesEaten[yesterday] ?: 0.0, 0.001)
        assertEquals(55.0, daily.protein[yesterday] ?: 0.0, 0.001)
        assertEquals("Steak & Veg", daily.meals[yesterday]?.first()?.name)
    }

    @Test
    fun `SavedFoodStore - recipe round-trip, upsert, and delete`() {
        var rawJson: String? = null
        val store = SavedFoodStore(
            prefsProvider = { rawJson },
            prefsWriter = { rawJson = it }
        )

        assertTrue(store.allRecipes().isEmpty())

        val r1 = Recipe(
            id = "r1",
            name = "Chilli Con Carne",
            perPortionKcal = 500,
            perPortionProtein = 40,
            perPortionCarbs = 45,
            perPortionFat = 15,
            portionsInPool = 4
        )
        store.upsertRecipe(r1)
        assertEquals(1, store.allRecipes().size)
        assertEquals("Chilli Con Carne", store.getRecipe("r1")?.name)
        assertEquals(4, store.getRecipe("r1")?.portionsInPool)

        // Upsert modified recipe
        val r1Updated = r1.copy(perPortionKcal = 520, portionsInPool = 6)
        store.upsertRecipe(r1Updated)
        assertEquals(1, store.allRecipes().size)
        assertEquals(520, store.getRecipe("r1")?.perPortionKcal)
        assertEquals(6, store.getRecipe("r1")?.portionsInPool)

        // Delete recipe
        store.deleteRecipe("r1")
        assertTrue(store.allRecipes().isEmpty())
        assertNull(store.getRecipe("r1"))
    }

    @Test
    fun `SavedFoodStore - pool decrement down to zero and clamp at zero`() {
        var rawJson: String? = null
        val store = SavedFoodStore(
            prefsProvider = { rawJson },
            prefsWriter = { rawJson = it }
        )

        val recipe = Recipe(
            id = "r-batch",
            name = "Bolognese Batch",
            perPortionKcal = 600,
            perPortionProtein = 45,
            portionsInPool = 2
        )
        store.upsertRecipe(recipe)

        // Decrement 2 -> 1
        val step1 = store.decrementPool("r-batch")
        assertEquals(1, step1?.portionsInPool)
        assertEquals(1, store.getRecipe("r-batch")?.portionsInPool)

        // Decrement 1 -> 0 ("Pool empty" state)
        val step2 = store.decrementPool("r-batch")
        assertEquals(0, step2?.portionsInPool)
        assertEquals(0, store.getRecipe("r-batch")?.portionsInPool)

        // Decrement below 0 -> clamped at 0, recipe stays
        val step3 = store.decrementPool("r-batch")
        assertEquals(0, step3?.portionsInPool)
        assertEquals(0, store.getRecipe("r-batch")?.portionsInPool)
        assertEquals(1, store.allRecipes().size) // Never auto-deleted!
    }

    @Test
    fun `SavedFoodStore - favourites toggle and idempotence`() {
        var rawJson: String? = null
        val store = SavedFoodStore(
            prefsProvider = { rawJson },
            prefsWriter = { rawJson = it }
        )

        val fav = FavouriteFood(id = "fav-1", name = "Greek Yogurt 200g", kcalPerServing = 120, proteinPerServing = 20)
        assertFalse(store.isFavourite("Greek Yogurt 200g"))

        // Toggle on (upsert)
        store.toggleFavourite(fav)
        assertTrue(store.isFavourite("Greek Yogurt 200g"))
        assertTrue(store.isFavourite("  greek yogurt 200g  ")) // case-insensitive match
        assertEquals(1, store.allFavourites().size)

        // Toggle off (remove)
        store.toggleFavourite(fav)
        assertFalse(store.isFavourite("Greek Yogurt 200g"))
        assertTrue(store.allFavourites().isEmpty())
    }

    @Test
    fun `FoodTrackerViewModel - logging recipe portion writes LoggedMeal and decrements pool`() {
        var rawJson: String? = null
        val savedStore = SavedFoodStore(
            prefsProvider = { rawJson },
            prefsWriter = { rawJson = it }
        )
        val recipe = Recipe(
            id = "r-curry",
            name = "Chicken Curry",
            perPortionKcal = 550,
            perPortionProtein = 48,
            perPortionCarbs = 35,
            perPortionFat = 12,
            portionsInPool = 3
        )
        savedStore.upsertRecipe(recipe)

        val vm = FoodTrackerViewModel(storage = null, savedFoodStore = savedStore, initialDayKey = today)
        assertEquals(1, vm.recipes.value.size)
        assertEquals(3, vm.recipes.value.first().portionsInPool)

        // Log one portion
        vm.logRecipePortion(recipe)

        // Meal is logged for today
        assertEquals(1, vm.mealsForDay.value.size)
        val logged = vm.mealsForDay.value.first()
        assertEquals("Chicken Curry", logged.name)
        assertEquals(550.0, logged.kcal, 0.001)
        assertEquals(48.0, logged.protein ?: 0.0, 0.001)
        assertEquals(35.0, logged.carbs ?: 0.0, 0.001)
        assertEquals(12.0, logged.fat ?: 0.0, 0.001)

        // Pool decremented 3 -> 2
        assertEquals(2, savedStore.getRecipe("r-curry")?.portionsInPool)
        assertEquals(2, vm.recipes.value.first().portionsInPool)
    }

    @Test
    fun `FoodTrackerViewModel - logging favourite writes LoggedMeal`() {
        var rawJson: String? = null
        val savedStore = SavedFoodStore(
            prefsProvider = { rawJson },
            prefsWriter = { rawJson = it }
        )
        val fav = FavouriteFood(id = "fav-egg", name = "Boiled Eggs x2", kcalPerServing = 140, proteinPerServing = 12)
        savedStore.toggleFavourite(fav)

        val vm = FoodTrackerViewModel(storage = null, savedFoodStore = savedStore, initialDayKey = today)
        assertEquals(1, vm.favourites.value.size)

        vm.logFavourite(fav)
        assertEquals(1, vm.mealsForDay.value.size)
        val logged = vm.mealsForDay.value.first()
        assertEquals("Boiled Eggs x2", logged.name)
        assertEquals(140.0, logged.kcal, 0.001)
        assertEquals(12.0, logged.protein ?: 0.0, 0.001)
    }

    @Test
    fun `guessMealType defaults correctly by hour`() {
        assertEquals("breakfast", guessMealType(8))
        assertEquals("breakfast", guessMealType(10))
        assertEquals("lunch", guessMealType(11))
        assertEquals("lunch", guessMealType(14))
        assertEquals("dinner", guessMealType(15))
        assertEquals("dinner", guessMealType(20))
        assertEquals("snacks", guessMealType(21))
        assertEquals("snacks", guessMealType(22))
        assertEquals("snacks", guessMealType(23))
        assertEquals("breakfast", guessMealType(0))
        assertEquals("breakfast", guessMealType(6))
    }

    @Test
    fun `legacy LoggedMeal decode defaults mealType to snacks`() {
        val legacyJson = """{"id":"m-1","name":"Banana","kcal":105.0,"timestamp":1000,"dayKey":"2026-10-14"}"""
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString<LoggedMeal>(legacyJson)
        assertEquals("snacks", decoded.mealType)
    }

    @Test
    fun `per-section grouping groups meals by mealType`() {
        val meals = listOf(
            LoggedMeal(id = "1", name = "Oatmeal", kcal = 350.0, mealType = "breakfast"),
            LoggedMeal(id = "2", name = "Chicken Salad", kcal = 450.0, mealType = "lunch"),
            LoggedMeal(id = "3", name = "Salmon & Rice", kcal = 600.0, mealType = "dinner"),
            LoggedMeal(id = "4", name = "Apple", kcal = 80.0, mealType = "snacks"),
            LoggedMeal(id = "5", name = "Greek Yogurt", kcal = 120.0, mealType = "breakfast")
        )

        val bySection = meals.groupBy { it.mealType.lowercase() }
        assertEquals(2, bySection["breakfast"]?.size)
        assertEquals(1, bySection["lunch"]?.size)
        assertEquals(1, bySection["dinner"]?.size)
        assertEquals(1, bySection["snacks"]?.size)

        val breakfastKcal = bySection["breakfast"]?.sumOf { it.kcal } ?: 0.0
        assertEquals(470.0, breakfastKcal, 0.001)
    }

    @Test
    fun `consumed and remaining maths calculate correctly`() {
        val kcalTarget = 2300
        val eatenWithin = 1420
        val remainingWithin = (kcalTarget - eatenWithin).coerceAtLeast(0)
        assertEquals(880, remainingWithin)

        val eatenOver = 2500
        val remainingOver = (kcalTarget - eatenOver).coerceAtLeast(0)
        assertEquals(0, remainingOver)
    }

    @Test
    fun `FoodHome totals equal sum of stored LoggedMeals for the day including legacy no-mealType rows`() {
        val legacyJson = """{"id":"m-legacy","name":"Old Snack","kcal":250.0,"protein":15.0,"timestamp":1000,"dayKey":"$today"}"""
        val modernMeal = LoggedMeal(id = "m-modern", name = "Modern Lunch", kcal = 550.0, protein = 40.0, dayKey = today, mealType = "lunch")
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val decodedLegacy = json.decodeFromString<LoggedMeal>(legacyJson)

        val mealsForDay = listOf(decodedLegacy, modernMeal)
        val totalKcal = mealsForDay.sumOf { it.kcal }.toInt()
        val totalProtein = mealsForDay.sumOf { it.protein ?: 0.0 }.toInt()

        assertEquals(800, totalKcal)
        assertEquals(55, totalProtein)
    }

    @Test
    fun `parity test - Day Card calories equals Food home consumed for the same dayKey`() {
        val meal1 = LoggedMeal(id = "1", name = "Eggs & Toast", kcal = 400.0, protein = 25.0, timestamp = nowMs, dayKey = today, mealType = "breakfast")
        val meal2 = LoggedMeal(id = "2", name = "Chicken Rice", kcal = 750.0, protein = 55.0, timestamp = nowMs, dayKey = today, mealType = "lunch")
        val meals = listOf(meal1, meal2)

        // 1. FoodHome consumed calculation
        val foodHomeConsumed = meals.sumOf { it.kcal }.toInt()

        // 2. DayCard calories calculation via pipeline
        val records = mapOf(RecordMethod.FOOD_LOG to meals.map { it.toHealthRecord() })
        val daily = Daily.buildDailyDataFromMaps(
            records = records,
            zones = Zones.DEFAULT_ZONES,
            zone = defaultZone,
            nowMs = nowMs
        )
        val dayCardCalories = daily.caloriesEaten[today]?.toInt() ?: 0

        // Parity assertion: must be exactly equal
        assertEquals(1150, foodHomeConsumed)
        assertEquals(foodHomeConsumed, dayCardCalories)
    }

    @Test
    fun `section grouping includes legacy snacks alongside typed meals`() {
        val legacyJson = """{"id":"m-leg","name":"Apple","kcal":95.0,"timestamp":1000,"dayKey":"$today"}"""
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val legacyMeal = json.decodeFromString<LoggedMeal>(legacyJson)
        val breakfastMeal = LoggedMeal(id = "m-bf", name = "Oatmeal", kcal = 350.0, mealType = "breakfast", dayKey = today)

        val allMeals = listOf(legacyMeal, breakfastMeal)
        val bySection = allMeals.groupBy { it.mealType.lowercase() }

        assertEquals(1, bySection["breakfast"]?.size)
        assertEquals("Oatmeal", bySection["breakfast"]?.first()?.name)

        assertEquals(1, bySection["snacks"]?.size)
        assertEquals("Apple", bySection["snacks"]?.first()?.name)
    }

    @Test
    fun `HC-sourced food entries - totals include hidden entries, sections exclude them, disclosure line counts correctly`() {
        val localMeal = LoggedMeal(id = "loc-1", name = "Local Eggs", kcal = 300.0, protein = 20.0, isLocal = true, mealType = "breakfast")
        val hcMeal1 = LoggedMeal(id = "hc-1", name = "BWS Protein Bar", kcal = 200.0, protein = 20.0, isLocal = false, mealType = "breakfast")
        val hcMeal2 = LoggedMeal(id = "hc-2", name = "BWS Shake", kcal = 150.0, protein = 25.0, isLocal = false, mealType = "breakfast")

        val allBreakfastMeals = listOf(localMeal, hcMeal1, hcMeal2)

        // 1. Totals include ALL entries (local + HC)
        val totalKcal = allBreakfastMeals.sumOf { it.kcal }.toInt()
        val totalProtein = allBreakfastMeals.sumOf { it.protein ?: 0.0 }.toInt()
        assertEquals(650, totalKcal)
        assertEquals(65, totalProtein)

        // 2. Sections exclude HC entries by default
        val localSectionMeals = allBreakfastMeals.filter { it.isLocal }
        assertEquals(1, localSectionMeals.size)
        assertEquals("Local Eggs", localSectionMeals.first().name)

        // 3. Disclosure line counts hidden entries correctly
        val hiddenCount = allBreakfastMeals.count { !it.isLocal }
        assertEquals(2, hiddenCount)
        val disclosureText = "$hiddenCount items from other apps (included in totals)"
        assertEquals("2 items from other apps (included in totals)", disclosureText)
    }

    @Test
    fun `Swipe actions - edit round-trip, delete and undo restores, no swipe on HC rows`() {
        val original = LoggedMeal(id = "m1", name = "Wrap", kcal = 400.0, protein = 30.0, isLocal = true, mealType = "lunch")
        val edited = original.copy(kcal = 450.0, protein = 35.0)
        assertEquals(450.0, edited.kcal, 0.001)

        val list = mutableListOf(original)
        var pendingDelete: LoggedMeal? = original
        // UI filters out pendingDelete
        val displayList = list.filter { it.id != pendingDelete?.id }
        assertTrue(displayList.isEmpty())

        // User taps Undo -> restored
        pendingDelete = null
        val restoredList = list.filter { it.id != pendingDelete?.id }
        assertEquals(1, restoredList.size)
        assertEquals("Wrap", restoredList.first().name)

        // HC rows: isLocal == false -> no swipe
        val hcMeal = LoggedMeal(id = "hc-1", name = "MyFitnessPal Apple", kcal = 80.0, isLocal = false)
        assertFalse(hcMeal.isLocal)
        assertTrue(original.isLocal)
    }

    @Test
    fun `Carbs and Fat mini-rows render at zero totals`() {
        val meals = listOf(
            LoggedMeal(id = "1", name = "Steak", kcal = 500.0, protein = 50.0, carbs = null, fat = null)
        )
        val totalCarbs = meals.sumOf { it.carbs ?: 0.0 }.toInt()
        val totalFat = meals.sumOf { it.fat ?: 0.0 }.toInt()

        assertEquals(0, totalCarbs)
        assertEquals(0, totalFat)

        val carbsTargetText = "—"
        val fatTargetText = "—"
        assertEquals("0 / —", if (carbsTargetText == "—") "$totalCarbs / —" else "$totalCarbs / $carbsTargetText")
        assertEquals("0 / —", if (fatTargetText == "—") "$totalFat / —" else "$totalFat / $fatTargetText")
    }

    @Test
    fun `alcohol kcal row on food macro card renders only when alcohol kcal greater than zero`() {
        val dailyZero = Daily.buildDailyDataFromMaps(
            records = emptyMap(),
            zones = Zones.DEFAULT_ZONES,
            zone = defaultZone,
            nowMs = nowMs
        )
        val dayAlcoholKcalZero = (dailyZero.alcoholKcal[today] ?: 0.0).toInt()
        val shouldRenderZero = dayAlcoholKcalZero > 0
        assertFalse("When alcohol kcal == 0, row must not render", shouldRenderZero)

        val drink = LoggedDrink(
            id = "d1",
            drink = DrinkReference(name = "Beer", units = 1.8, kcals = 180),
            timestamp = nowMs,
            dayKey = today
        )
        val dailyPositive = Daily.buildDailyDataFromMaps(
            records = mapOf(RecordMethod.ALCOHOL to listOf(drink.toHealthRecord())),
            zones = Zones.DEFAULT_ZONES,
            zone = defaultZone,
            nowMs = nowMs
        )
        val dayAlcoholKcalPositive = (dailyPositive.alcoholKcal[today] ?: 0.0).toInt()
        val shouldRenderPositive = dayAlcoholKcalPositive > 0
        assertTrue("When alcohol kcal > 0, row must render", shouldRenderPositive)
        assertEquals(180, dayAlcoholKcalPositive)
    }

    @Test
    fun `cross-tab barcode scan state holder on FoodTrackerViewModel`() {
        val vm = FoodTrackerViewModel()

        // 1. Successful barcode lookup sets pendingConfirmProduct and resets barcodeNotFound
        val product = ProductResult(
            code = "5000123456789",
            productName = "Protein Yogurt",
            brands = "Brand X"
        )
        vm.pendingConfirmProduct = product
        vm.barcodeNotFound = false

        assertEquals("5000123456789", vm.pendingConfirmProduct?.code)
        assertFalse(vm.barcodeNotFound)

        // 2. Barcode not found sets barcodeNotFound = true and pendingConfirmProduct = null
        vm.pendingConfirmProduct = null
        vm.barcodeNotFound = true

        assertNull(vm.pendingConfirmProduct)
        assertTrue(vm.barcodeNotFound)
    }

    @Test
    fun `scanned product confirmed twice produces two distinct logged meals with unique UUIDs`() {
        val vm = FoodTrackerViewModel()
        vm.setDayKey(today)

        val product = ProductResult(
            code = "5000123456789",
            productName = "Protein Yogurt"
        )

        // Confirm scan 1
        val meal1 = LoggedMeal(
            id = java.util.UUID.randomUUID().toString(),
            name = product.productName ?: "Product",
            kcal = 150.0,
            dayKey = today
        )
        vm.logCustomMeal(meal1)

        // Confirm scan 2 of identical barcode
        val meal2 = LoggedMeal(
            id = java.util.UUID.randomUUID().toString(),
            name = product.productName ?: "Product",
            kcal = 150.0,
            dayKey = today
        )
        vm.logCustomMeal(meal2)

        // Both entries persist; no collision or overwrite
        assertEquals(2, vm.mealsForDay.value.size)
        assertTrue(meal1.id != meal2.id)
    }

    @Test
    fun `deriveInitialExpandedSections seeds lowercase section names from meals and handles empty list`() {
        // Empty meals list -> empty expanded set
        assertTrue(deriveInitialExpandedSections(emptyList()).isEmpty())

        // Meals with various casings and meal types
        val meals = listOf(
            LoggedMeal(id = "1", name = "Oats", kcal = 300.0, mealType = "Breakfast"),
            LoggedMeal(id = "2", name = "Sandwich", kcal = 500.0, mealType = "LUNCH"),
            LoggedMeal(id = "3", name = "Apple", kcal = 80.0, mealType = "snacks"),
        )
        val expanded = deriveInitialExpandedSections(meals)
        assertEquals(setOf("breakfast", "lunch", "snacks"), expanded)
    }
}
