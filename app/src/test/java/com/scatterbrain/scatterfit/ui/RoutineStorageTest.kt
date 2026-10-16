package com.scatterbrain.scatterfit.ui

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineStorageTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `round-trip encode and decode preserves Routine structure`() {
        val exercise1 = RoutineExercise(
            exerciseId = "barbell-bench-press",
            exerciseName = "Barbell Bench Press",
            sets = listOf(
                PlannedSet(type = "warmup", weightKg = 40.0, reps = 10),
                PlannedSet(type = "normal", weightKg = 80.0, reps = 8),
                PlannedSet(type = "failure", weightKg = 85.0, reps = 6)
            )
        )
        val routine = Routine(
            id = "routine-1",
            name = "Push Day",
            exercises = listOf(exercise1),
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val encoded = json.encodeToString(listOf(routine))
        val decoded = json.decodeFromString<List<Routine>>(encoded)

        assertEquals(1, decoded.size)
        val loaded = decoded.first()
        assertEquals("routine-1", loaded.id)
        assertEquals("Push Day", loaded.name)
        assertEquals(1, loaded.exercises.size)
        assertEquals(3, loaded.exercises.first().sets.size)
        assertEquals("warmup", loaded.exercises.first().sets[0].type)
        assertEquals(80.0, loaded.exercises.first().sets[1].weightKg!!, 0.001)
        assertEquals(6, loaded.exercises.first().sets[2].reps)
    }

    @Test
    fun `custom exercise survives round-trip`() {
        val customEx = ExerciseRef(
            id = "custom-123",
            name = "Landmine Press",
            muscle = "other",
            isCustom = true
        )

        val encoded = json.encodeToString(customEx)
        val decoded = json.decodeFromString<ExerciseRef>(encoded)

        assertEquals("custom-123", decoded.id)
        assertEquals("Landmine Press", decoded.name)
        assertEquals("other", decoded.muscle)
        assertTrue(decoded.isCustom)
    }

    @Test
    fun `upsert replaces routine with same id or appends new`() {
        val r1 = Routine(id = "r1", name = "Legs A")
        val r2 = Routine(id = "r2", name = "Legs B")

        val list = mutableListOf(r1)

        // Upsert r2 -> appends
        val i2 = list.indexOfFirst { it.id == r2.id }
        if (i2 >= 0) list[i2] = r2 else list.add(r2)
        assertEquals(2, list.size)

        // Upsert r1 modified -> replaces
        val r1Updated = r1.copy(name = "Legs Heavy")
        val i1 = list.indexOfFirst { it.id == r1Updated.id }
        if (i1 >= 0) list[i1] = r1Updated else list.add(r1Updated)
        assertEquals(2, list.size)
        assertEquals("Legs Heavy", list.find { it.id == "r1" }?.name)
    }

    @Test
    fun `delete removes routine by id`() {
        val r1 = Routine(id = "r1", name = "Upper")
        val r2 = Routine(id = "r2", name = "Lower")
        val list = mutableListOf(r1, r2)

        list.removeAll { it.id == "r1" }
        assertEquals(1, list.size)
        assertEquals("r2", list.first().id)
        assertNull(list.find { it.id == "r1" })
    }

    @Test
    fun `migration test - decode old JSON shape applies new defaults`() {
        val oldJson = """
            [
              {
                "id": "old-routine-1",
                "name": "Pull Day",
                "exercises": [
                  {
                    "exerciseId": "lat-pulldown",
                    "exerciseName": "Lat Pulldown",
                    "sets": [
                      {
                        "type": "normal",
                        "weightKg": 60.0,
                        "reps": 10
                      }
                    ]
                  }
                ],
                "createdAt": 1000,
                "updatedAt": 2000
              }
            ]
        """.trimIndent()

        val decoded = json.decodeFromString<List<Routine>>(oldJson)
        assertEquals(1, decoded.size)
        val r = decoded.first()
        val ex = r.exercises.first()
        assertEquals(0, ex.restSeconds)
        assertNull(ex.note)
        assertNull(ex.customName)
        assertEquals("Lat Pulldown", ex.displayName)

        val set = ex.sets.first()
        assertNull(set.note)
        assertNull(set.difficulty)
    }

    @Test
    fun `customName displays instead of library name when set, falls back when blank`() {
        val exWithCustom = RoutineExercise(
            exerciseId = "lat-pulldown",
            exerciseName = "Lat Pulldown",
            customName = "Cable D-Handle Pulldown"
        )
        assertEquals("Cable D-Handle Pulldown", exWithCustom.displayName)

        val exBlankCustom = RoutineExercise(
            exerciseId = "lat-pulldown",
            exerciseName = "Lat Pulldown",
            customName = "   "
        )
        assertEquals("Lat Pulldown", exBlankCustom.displayName)

        val exNullCustom = RoutineExercise(
            exerciseId = "lat-pulldown",
            exerciseName = "Lat Pulldown",
            customName = null
        )
        assertEquals("Lat Pulldown", exNullCustom.displayName)
    }

    @Test
    fun `cycleDifficulty transitions null to easy to medium to hard to null`() {
        assertEquals("easy", cycleDifficulty(null))
        assertEquals("medium", cycleDifficulty("easy"))
        assertEquals("hard", cycleDifficulty("medium"))
        assertNull(cycleDifficulty("hard"))
    }

    @Test
    fun `summary counts sets by muscle correctly`() {
        val exercises = listOf(
            RoutineExercise(
                exerciseId = "barbell-bench-press",
                exerciseName = "Barbell Bench Press",
                sets = listOf(PlannedSet(), PlannedSet(), PlannedSet())
            ),
            RoutineExercise(
                exerciseId = "incline-dumbbell-bench-press",
                exerciseName = "Incline DB Bench Press",
                sets = listOf(PlannedSet(), PlannedSet())
            ),
            RoutineExercise(
                exerciseId = "skull-crusher",
                exerciseName = "Skull Crusher",
                sets = listOf(PlannedSet(), PlannedSet(), PlannedSet())
            )
        )

        val lookup: (String) -> String = { id ->
            when (id) {
                "barbell-bench-press", "incline-dumbbell-bench-press" -> "chest"
                "skull-crusher" -> "triceps"
                else -> "other"
            }
        }

        val muscleSets = calculateSetsByMuscle(exercises, lookup)
        assertEquals(5, muscleSets["chest"])
        assertEquals(3, muscleSets["triceps"])
    }

    @Test
    fun `calculateRoutineEstimateMinutes rounds to nearest 5 minutes`() {
        // Exercise 1: 3 sets, rest = 60s -> 3 * 40 + 2 * 60 = 240s (4 min)
        // Exercise 2: 3 sets, rest = 60s -> 3 * 40 + 2 * 60 = 240s (4 min)
        // Total = 480s = 8 min -> rounds to 10 min
        val exercises = listOf(
            RoutineExercise(
                exerciseId = "ex1",
                exerciseName = "Ex 1",
                restSeconds = 60,
                sets = listOf(PlannedSet(), PlannedSet(), PlannedSet())
            ),
            RoutineExercise(
                exerciseId = "ex2",
                exerciseName = "Ex 2",
                restSeconds = 60,
                sets = listOf(PlannedSet(), PlannedSet(), PlannedSet())
            )
        )
        val est = calculateRoutineEstimateMinutes(exercises)
        assertEquals(10, est)
    }
}
