package com.scatterbrain.scatterfit.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scatterbrain.scatterfit.core.WeekMode
import com.scatterbrain.scatterfit.core.currentWeekKeys
import com.scatterbrain.scatterfit.core.todayKey
import com.scatterbrain.scatterfit.core.unitsScore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State representing the daily drink tracking metrics and library for an active dayKey.
 */
data class DailyState(
    val dayKey: String = todayKey(),
    val totalUnits: Double = 0.0,
    val totalAlcoholKcals: Int = 0,
    val totalSpent: Double = 0.0,
    val isDrinkDay: Boolean = false,
    val unitsScore: Double = 100.0,
    val targetUnits: Double = 14.0,
    val loggedDrinks: List<LoggedDrink> = emptyList(),
    val libraryDrinks: List<DrinkReference> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: String = "All",
    val isLoading: Boolean = false
)

class DrinkTrackerViewModel(
    private val storage: DrinkStorage? = null,
    initialDayKey: String = todayKey(),
    initialLibrary: List<DrinkReference> = emptyList()
) : ViewModel() {

    private var activeDayKey: String = initialDayKey
    private val inMemoryStorage = mutableMapOf<String, MutableList<LoggedDrink>>()

    private val _dailyState = MutableStateFlow(
        createInitialState(initialDayKey, initialLibrary)
    )
    val dailyState: StateFlow<DailyState> = _dailyState.asStateFlow()

    private fun createInitialState(dayKey: String, library: List<DrinkReference>): DailyState {
        val initialLogs = storage?.getDrinksForDay(dayKey) ?: (inMemoryStorage[dayKey] ?: emptyList())
        val units = initialLogs.sumOf { it.drink.units }
        val kcals = initialLogs.sumOf { it.drink.kcals }
        val spent = initialLogs.sumOf { it.drink.cost }
        val score = unitsScore(units, 14.0)

        return DailyState(
            dayKey = dayKey,
            totalUnits = units,
            totalAlcoholKcals = kcals,
            totalSpent = spent,
            isDrinkDay = initialLogs.isNotEmpty(),
            unitsScore = score,
            targetUnits = 14.0,
            loggedDrinks = initialLogs.sortedByDescending { it.timestamp },
            libraryDrinks = library
        )
    }

    /**
     * Changes the active dayKey to navigate between days (e.g., viewing yesterday vs today).
     */
    fun setDayKey(newDayKey: String) {
        activeDayKey = newDayKey
        val logsForDay = storage?.getDrinksForDay(newDayKey) ?: (inMemoryStorage[newDayKey] ?: emptyList())
        recalculateTotals(logsForDay, newDayKey)
    }

    /**
     * Stamps timestamp and dayKey, adds LoggedDrink, persists to storage,
     * and recalculates totals using the JSON units value.
     * When a day other than today is active, constructs the drink's timestamp
     * from the selected day's date + current wall-clock time (or last-used log time for that day).
     * Then re-keys via dayKey().
     * If target day is in the future (> today), logging is blocked and refused.
     * Returns true if logged successfully, false if refused.
     */
    fun logDrink(drink: DrinkReference, timestamp: Long? = null): Boolean {
        val today = com.scatterbrain.scatterfit.core.todayKey()
        val zone = java.time.ZoneId.systemDefault()

        val actualTimestamp = if (timestamp != null) {
            timestamp
        } else {
            val selectedDate = try {
                java.time.LocalDate.parse(activeDayKey)
            } catch (_: Exception) {
                java.time.LocalDate.now(zone)
            }

            val currentLogs = storage?.getDrinksForDay(activeDayKey)
                ?: inMemoryStorage[activeDayKey]
            val lastLogForDay = currentLogs?.maxByOrNull { it.timestamp }

            val timeToUse = if (lastLogForDay != null) {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = lastLogForDay.timestamp }
                java.time.LocalTime.of(
                    cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE),
                    cal.get(java.util.Calendar.SECOND)
                )
            } else {
                java.time.LocalTime.now(zone)
            }

            selectedDate.atTime(timeToUse).atZone(zone).toInstant().toEpochMilli()
        }

        val computedDayKey = com.scatterbrain.scatterfit.core.dayKey(actualTimestamp, zone)

        // Rule: Future days are blocked! Logging into future days is refused.
        if (computedDayKey > today) {
            return false
        }

        val loggedDrink = LoggedDrink(
            drink = drink,
            timestamp = actualTimestamp,
            dayKey = computedDayKey
        )

        val updatedLogged = if (storage != null) {
            storage.saveDrink(loggedDrink)
            storage.getDrinksForDay(activeDayKey)
        } else {
            val list = inMemoryStorage.getOrPut(computedDayKey) { mutableListOf() }
            list.add(loggedDrink)
            inMemoryStorage[activeDayKey]?.toList() ?: emptyList()
        }

        recalculateTotals(updatedLogged, activeDayKey)
        return true
    }

    /**
     * Updates a logged drink's time, recomputes its dayKey,
     * persists changes to storage, and re-sorts the strip newest-first.
     */
    fun updateLoggedDrinkTime(loggedDrink: LoggedDrink, newTimestamp: Long) {
        val newDayKey = com.scatterbrain.scatterfit.core.dayKey(newTimestamp)
        val updatedDrink = loggedDrink.copy(
            timestamp = newTimestamp,
            dayKey = newDayKey
        )

        val updatedLogged = if (storage != null) {
            storage.removeDrink(loggedDrink.dayKey, loggedDrink.id)
            storage.saveDrink(updatedDrink)
            storage.getDrinksForDay(activeDayKey)
        } else {
            inMemoryStorage[loggedDrink.dayKey]?.removeAll { it.id == loggedDrink.id }
            inMemoryStorage.getOrPut(newDayKey) { mutableListOf() }.add(updatedDrink)
            inMemoryStorage[activeDayKey] ?: emptyList()
        }

        recalculateTotals(updatedLogged, activeDayKey)
    }

    /**
     * Removes a logged drink, updates storage, and recalculates daily totals.
     */
    fun removeLoggedDrink(loggedDrink: LoggedDrink) {
        val updatedLogged = if (storage != null) {
            storage.removeDrink(loggedDrink.dayKey, loggedDrink.id)
            storage.getDrinksForDay(activeDayKey)
        } else {
            inMemoryStorage[loggedDrink.dayKey]?.removeAll { it.id == loggedDrink.id }
            inMemoryStorage[activeDayKey] ?: emptyList()
        }

        recalculateTotals(updatedLogged, activeDayKey)
    }

    /**
     * Clears all logged drinks for the active dayKey.
     */
    fun clearLogs() {
        val cleared = if (storage != null) {
            storage.clearDay(activeDayKey)
        } else {
            inMemoryStorage.remove(activeDayKey)
            emptyList()
        }
        recalculateTotals(cleared, activeDayKey)
    }

    fun onSearchQueryChanged(query: String) {
        _dailyState.update { it.copy(searchQuery = query) }
    }

    fun onCategorySelected(category: String) {
        _dailyState.update { it.copy(selectedCategory = category) }
    }

    fun setLibrary(drinks: List<DrinkReference>) {
        _dailyState.update { it.copy(libraryDrinks = drinks) }
    }

    fun loadLibraryFromAssets(context: Context, filename: String = "drinks-library.json") {
        viewModelScope.launch(Dispatchers.IO) {
            _dailyState.update { it.copy(isLoading = true) }
            val drinks = DrinkLibraryParser.loadFromAssets(context, filename)
            _dailyState.update { it.copy(libraryDrinks = drinks, isLoading = false) }
        }
    }

    fun getWeeklyUnits(weekMode: WeekMode = "monday"): Double {
        val keys = currentWeekKeys(weekMode)
        return storage?.getWeeklyUnits(keys)
            ?: keys.sumOf { k -> inMemoryStorage[k]?.sumOf { it.drink.units } ?: 0.0 }
    }

    fun getWeeklyDrinkDaysCount(weekMode: WeekMode = "monday"): Int {
        val keys = currentWeekKeys(weekMode)
        return storage?.getDrinkDaysCount(keys)
            ?: keys.count { k -> (inMemoryStorage[k]?.size ?: 0) > 0 }
    }

    fun getWeeklySpent(weekMode: WeekMode = "monday"): Double {
        val keys = currentWeekKeys(weekMode)
        return storage?.getWeeklySpent(keys)
            ?: keys.sumOf { k -> inMemoryStorage[k]?.sumOf { it.drink.cost } ?: 0.0 }
    }

    fun getWeeklyAlcoholKcal(weekMode: WeekMode = "monday"): Int {
        val keys = currentWeekKeys(weekMode)
        return storage?.getWeeklyAlcoholKcal(keys)
            ?: keys.sumOf { k -> inMemoryStorage[k]?.sumOf { it.drink.kcals } ?: 0 }
    }

    /**
     * Quick-add candidates: favourites first (fav=true, in library order),
     * then recently logged drinks (last 5 unique, most recent first),
     * deduplicated, capped at [limit] (default 6).
     */
    fun getQuickAddDrinks(limit: Int = 6): List<DrinkReference> {
        val library = _dailyState.value.libraryDrinks
        val favourites = library.filter { it.fav }

        val allLogs = storage?.loadAll()?.values?.flatten() ?: inMemoryStorage.values.flatten()
        val recents = allLogs
            .sortedByDescending { it.timestamp }
            .map { it.drink }
            .distinctBy { it.id.ifBlank { it.name + it.serving } }
            .take(5)

        return (favourites + recents)
            .distinctBy { it.id.ifBlank { it.name + it.serving } }
            .take(limit)
    }

    private fun recalculateTotals(loggedList: List<LoggedDrink>, dayKey: String) {
        // Sort newest-first by timestamp
        val sortedList = loggedList.sortedByDescending { it.timestamp }
        // UNITS PARITY FIX: Use JSON units field directly (drink.units)
        val calculatedUnits = sortedList.sumOf { it.drink.units }
        val calculatedKcals = sortedList.sumOf { it.drink.kcals }
        val calculatedSpent = sortedList.sumOf { it.drink.cost }
        val score = unitsScore(calculatedUnits, _dailyState.value.targetUnits)

        _dailyState.update { current ->
            current.copy(
                dayKey = dayKey,
                loggedDrinks = sortedList,
                totalUnits = calculatedUnits,
                totalAlcoholKcals = calculatedKcals,
                totalSpent = calculatedSpent,
                isDrinkDay = sortedList.isNotEmpty(),
                unitsScore = score
            )
        }
    }
}
