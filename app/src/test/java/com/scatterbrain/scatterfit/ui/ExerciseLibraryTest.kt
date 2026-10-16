package com.scatterbrain.scatterfit.ui

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExerciseLibraryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `exercise library JSON has exactly 150 exercises with valid fields`() {
        val file = File("src/main/res/raw/exercise_library.json")
        val altFile = File("app/src/main/res/raw/exercise_library.json")
        val target = if (file.exists()) file else altFile
        assertTrue("exercise_library.json must exist", target.exists())

        val text = target.readText()
        val items = json.decodeFromString<List<ExerciseLibraryItem>>(text)

        assertEquals("Library must contain exactly 150 exercises", 150, items.size)

        val allowedMuscles = setOf("chest", "back", "shoulders", "biceps", "triceps", "legs", "glutes", "core", "fullbody", "other")
        val allowedEquipment = setOf("barbell", "dumbbell", "machine", "cable", "bodyweight", "other")

        for (item in items) {
            assertTrue("id must not be blank: ${item.id}", item.id.isNotBlank())
            assertTrue("name must not be blank: ${item.name}", item.name.isNotBlank())
            assertTrue("muscle must be allowed: ${item.muscle}", allowedMuscles.contains(item.muscle))
            assertTrue("equipment must be allowed: ${item.equipment}", allowedEquipment.contains(item.equipment))
        }

        assertEquals("All IDs must be unique", 150, items.map { it.id }.distinct().size)
    }

    @Test
    fun `resolveOrCreate with matching name returns library exercise case-insensitively`() {
        val library = listOf(
            ExerciseRef(id = "barbell-bench-press", name = "Barbell Bench Press", muscle = "chest", isCustom = false),
            ExerciseRef(id = "pull-up", name = "Pull Up", muscle = "back", isCustom = false)
        )

        val exactMatch = ExerciseLibrary.resolveOrCreate("Barbell Bench Press", library)
        assertEquals("barbell-bench-press", exactMatch.id)
        assertFalse(exactMatch.isCustom)

        val lowerCaseMatch = ExerciseLibrary.resolveOrCreate("barbell bench press", library)
        assertEquals("barbell-bench-press", lowerCaseMatch.id)
        assertFalse(lowerCaseMatch.isCustom)

        val upperCaseMatch = ExerciseLibrary.resolveOrCreate("PULL UP", library)
        assertEquals("pull-up", upperCaseMatch.id)
        assertFalse(upperCaseMatch.isCustom)
    }

    @Test
    fun `resolveOrCreate with new name creates custom exercise with muscle other`() {
        val library = listOf(
            ExerciseRef(id = "barbell-bench-press", name = "Barbell Bench Press", muscle = "chest", isCustom = false)
        )

        val custom = ExerciseLibrary.resolveOrCreate("Bicep Drag Curl", library)
        assertTrue(custom.id.startsWith("custom-"))
        assertEquals("Bicep Drag Curl", custom.name)
        assertEquals("other", custom.muscle)
        assertTrue(custom.isCustom)
    }
}
