package com.scatterbrain.scatterfit.ui

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingPlanTest {

    @Test
    fun `isTomorrowCardVisible returns true for 19 to 23 and 0 to 7, false for 8 to 18`() {
        // 19:00 - 23:59 -> true
        for (h in 19..23) {
            assertTrue("Hour $h should be visible", isTomorrowCardVisible(h))
        }

        // 00:00 - 07:59 -> true
        for (h in 0..7) {
            assertTrue("Hour $h should be visible", isTomorrowCardVisible(h))
        }

        // 08:00 - 18:59 -> false
        for (h in 8..18) {
            assertFalse("Hour $h should NOT be visible", isTomorrowCardVisible(h))
        }
    }

    @Test
    fun `plan storage round-trip encodes and decodes weekly plan`() {
        val json = Json { ignoreUnknownKeys = true }
        val plan = mapOf(
            "MONDAY" to "Upper A",
            "TUESDAY" to "Lower A",
            "WEDNESDAY" to null,
            "THURSDAY" to "Upper B",
            "FRIDAY" to "Lower B",
            "SATURDAY" to "HIIT",
            "SUNDAY" to ""
        )
        val clean = plan.filterValues { !it.isNullOrBlank() }
        val encoded = json.encodeToString(clean)
        val decoded = json.decodeFromString<Map<String, String?>>(encoded)

        assertEquals("Upper A", decoded["MONDAY"])
        assertEquals("Lower A", decoded["TUESDAY"])
        assertNull(decoded["WEDNESDAY"])
        assertEquals("Upper B", decoded["THURSDAY"])
        assertEquals("Lower B", decoded["FRIDAY"])
        assertEquals("HIIT", decoded["SATURDAY"])
        assertNull(decoded["SUNDAY"])
    }
}
