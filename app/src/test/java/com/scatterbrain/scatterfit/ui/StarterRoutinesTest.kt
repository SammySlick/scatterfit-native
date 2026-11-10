package com.scatterbrain.scatterfit.ui

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterRoutinesTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val upperA = StarterRoutines.upperA

    @Test
    fun `Upper A keeps the Hevy structure of 11 exercises and 26 sets`() {
        assertEquals("Upper A", upperA.name)
        assertEquals(StarterRoutines.UPPER_A_ID, upperA.id)
        assertEquals(11, upperA.exercises.size)
        assertEquals(26, upperA.exercises.sumOf { it.sets.size })
    }

    @Test
    fun `exercise order matches the Hevy routine`() {
        assertEquals(
            listOf(
                "Cable D Handle External Rotation",
                "Dead Hang",
                "Hip Adduction (Machine)",
                "Butterfly (Pec Deck)",
                "Lateral Raise (Machine)",
                "Incline Bench Press (Dumbbell)",
                "Bench Press (Dumbbell)",
                "Single Arm Triceps Pushdown (Cable)",
                "Seated Incline Curl (Dumbbell)",
                "Skullcrusher (Dumbbell)",
                "Preacher Curl (Machine)",
            ),
            upperA.exercises.map { it.displayName }
        )
    }

    @Test
    fun `dead hang is timed and every other exercise is rep based`() {
        val deadHang = upperA.exercises.first { it.displayName == "Dead Hang" }
        assertTrue(deadHang.isTimed)
        assertEquals(listOf(20, 20), deadHang.sets.map { it.timeSeconds })

        assertTrue(
            "Only the dead hang is timed",
            upperA.exercises.filterNot { it.displayName == "Dead Hang" }.none { it.isTimed }
        )
    }

    @Test
    fun `rest pause blocks run heavy to light at a 20 second rest`() {
        val restPause = listOf("Bench Press (Dumbbell)", "Preacher Curl (Machine)").map { name ->
            upperA.exercises.first { it.displayName == name }
        }

        for (exercise in restPause) {
            assertEquals(20, exercise.restSeconds)
            assertEquals(3, exercise.sets.size)
            assertEquals(listOf(8 to 12, 4 to 8, 2 to 4), exercise.sets.map { it.repsMin to it.repsMax })
            assertTrue(exercise.sets.all { it.weightKg == null })
        }
    }

    @Test
    fun `tempo strings survive as four part notation`() {
        val expected = mapOf(
            "Cable D Handle External Rotation" to "2-0-2-1",
            "Hip Adduction (Machine)" to "3-1-2-1",
            "Butterfly (Pec Deck)" to "3-0-2-1",
            "Lateral Raise (Machine)" to "4-1-4-1",
            "Incline Bench Press (Dumbbell)" to "2-0-2-1",
            "Bench Press (Dumbbell)" to "3-0-2-1",
            "Single Arm Triceps Pushdown (Cable)" to "3-0-3-1",
            "Seated Incline Curl (Dumbbell)" to "3-0-2-1",
            "Skullcrusher (Dumbbell)" to "4-0-1-0",
            "Preacher Curl (Machine)" to "3-0-2-1",
        )
        for ((name, tempo) in expected) {
            val exercise = upperA.exercises.first { it.displayName == name }
            assertEquals("tempo for $name", tempo, exercise.tempo)
            assertEquals("tempo parts for $name", 4, parseTempo(exercise.tempo).size)
        }
        assertNull(upperA.exercises.first { it.displayName == "Dead Hang" }.tempo)
    }

    @Test
    fun `library exercise ids resolve so the muscle summary is not all unknown`() {
        val libraryIds = setOf(
            "machine-hip-adduction",
            "machine-chest-fly",
            "machine-lateral-raise",
            "incline-dumbbell-bench-press",
            "dumbbell-bench-press",
            "tricep-rope-pushdown",
            "seated-incline-dumbbell-curl",
            "skull-crusher",
            "machine-bicep-curl",
        )
        val used = upperA.exercises.map { it.exerciseId }.toSet()
        assertTrue("Library ids used by Upper A must all be from the library list", used.containsAll(libraryIds))
        assertEquals(2, used.size - libraryIds.size) // the two custom movements
    }

    @Test
    fun `Upper A round-trips through storage encoding unchanged`() {
        val encoded = json.encodeToString(listOf(upperA))
        val decoded = decodeRoutinesJson(encoded)

        assertEquals(1, decoded.size)
        assertEquals(11, decoded.first().exercises.size)
        assertEquals(26, decoded.first().exercises.sumOf { it.sets.size })
        assertEquals("2-0-2-1", decoded.first().exercises.first().tempo)
    }
}
