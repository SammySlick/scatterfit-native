package com.scatterbrain.scatterfit.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId

class DailySnapshotTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `snapshot round-trip saves and loads AssembledDaily`() {
        val file = File(tempFolder.root, "daily-snapshot.json")
        val storage = DailySnapshotStorage(file)

        val original = AssembledDaily(
            steps = mapOf("2026-10-14" to 8420.0),
            caloriesEaten = mapOf("2026-10-14" to 1950.0),
            protein = mapOf("2026-10-14" to 140.0),
            carbs = mapOf("2026-10-14" to 210.0),
            fat = mapOf("2026-10-14" to 65.0),
            caloriesBurned = mapOf("2026-10-14" to 2300.0),
            weight = mapOf("2026-10-14" to 76.4),
            restingHr = mapOf("2026-10-14" to 54.0),
            latestHeartRate = Pair(56.0, "2026-10-14T12:00:00Z"),
            trainingMinutes = mapOf("2026-10-14" to 45.0),
            zoneBounds = ZoneBounds(130, 145, 160, 175),
            maxHr = 182.0,
            hrZones = mapOf("2026-10-14" to ZoneDay(45.0, 10.0, 20.0, 15.0, 0.0, true, 45.0, 1.0)),
            sleep = mapOf("2026-10-14" to SleepNight("2026-10-14", 460.0, 70.0, 90.0, 240.0, 60.0, "2026-10-14T07:00:00Z")),
            sleepNights = listOf(MergedNight("2026-10-13T23:00:00Z", "2026-10-14T07:00:00Z", 460.0, 70.0, 90.0, 240.0, 60.0, true, emptyList(), "2026-10-13", emptyList(), false)),
            sessions = listOf(ExerciseSession("2026-10-14", 45.0, "Workout", "2026-10-14T08:00:00Z")),
            bodyFat = mapOf("2026-10-14" to 17.5),
            stepsSource = mapOf("2026-10-14" to "Pixel Watch"),
            distance = mapOf("2026-10-14" to 6200.0),
            distanceSource = mapOf("2026-10-14" to "Pixel Watch"),
            burnedSource = mapOf("2026-10-14" to "Fitbit"),
            drinks = mapOf("2026-10-14" to true),
            foodKcal = mapOf("2026-10-14" to 1950.0),
            alcoholKcal = mapOf("2026-10-14" to 140.0),
            alcoholUnits = mapOf("2026-10-14" to 2.0),
            meals = mapOf("2026-10-14" to listOf(MealEntry("Lunch", 650.0, 45.0, 60.0, 20.0, "Lunch", "12:30", "app"))),
            zone = ZoneId.of("UTC")
        )

        storage.save(original)
        val loaded = storage.load()

        assertNotNull(loaded)
        assertEquals(original, loaded)
    }
}
