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
}
