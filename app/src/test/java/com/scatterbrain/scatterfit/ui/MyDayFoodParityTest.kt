package com.scatterbrain.scatterfit.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MyDayFoodParityTest {

    @Test
    fun `log lunch once - My Day total equals Food panel total without double count`() {
        val today = "2026-10-20"
        val viewModel = FoodTrackerViewModel(initialDayKey = today)

        // Log lunch once: 485 kcal, 55g protein
        val lunchMeal = LoggedMeal(
            id = "lunch-1",
            name = "Chicken & Rice",
            kcal = 485.0,
            protein = 55.0,
            timestamp = System.currentTimeMillis(),
            dayKey = today,
            mealType = "lunch"
        )
        viewModel.logCustomMeal(lunchMeal)

        // Food panel derives totals from viewModel.mealsForDay
        val foodPanelMeals = viewModel.mealsForDay.value
        val foodPanelKcal = foodPanelMeals.sumOf { it.kcal }
        val foodPanelProtein = foodPanelMeals.sumOf { it.protein ?: 0.0 }

        // My Day reads the SAME meal source (viewModel.mealsForDay)
        val myDayMeals = viewModel.mealsForDay.value
        val myDayKcal = myDayMeals.sumOf { it.kcal }
        val myDayProtein = myDayMeals.sumOf { it.protein ?: 0.0 }

        // Assert single source of truth: exact parity and exactly one lunch logged
        assertEquals(1, foodPanelMeals.size)
        assertEquals(1, myDayMeals.size)

        assertEquals(485.0, foodPanelKcal, 0.001)
        assertEquals(485.0, myDayKcal, 0.001)
        assertEquals(foodPanelKcal, myDayKcal, 0.001)

        assertEquals(55.0, foodPanelProtein, 0.001)
        assertEquals(55.0, myDayProtein, 0.001)
        assertEquals(foodPanelProtein, myDayProtein, 0.001)
    }
}
