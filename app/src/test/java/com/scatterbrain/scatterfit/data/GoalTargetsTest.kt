package com.scatterbrain.scatterfit.data

import com.scatterbrain.scatterfit.core.GoalId
import com.scatterbrain.scatterfit.core.GoalPreset
import com.scatterbrain.scatterfit.core.UnitSystem
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalTargetsTest {

    @After
    fun tearDown() {
        GoalTargetsStore.reset()
    }

    @Test
    fun `preset mapping maps to correct primary metric`() {
        assertEquals(GoalId.BODY_FAT, primaryMetricFor(GoalPreset.FAT_LOSS))
        assertEquals(GoalId.WEIGHT, primaryMetricFor(GoalPreset.MAINTENANCE))
        assertEquals(GoalId.WEIGHT, primaryMetricFor(GoalPreset.BUILD_MUSCLE))
        assertEquals(GoalId.RHR_TREND, primaryMetricFor(GoalPreset.FITNESS))
        assertEquals(GoalId.WEIGHT, primaryMetricFor(GoalPreset.CUSTOM))
    }

    @Test
    fun `store demo defaults match expected values`() {
        val targets = GoalTargetsStore.load()
        assertEquals(17.0, targets.targets[GoalId.BODY_FAT]!!, 1e-6)
        assertEquals(84.0, targets.targets[GoalId.WEIGHT]!!, 1e-6)
        assertEquals(52.0, targets.targets[GoalId.RHR_TREND]!!, 1e-6)

        assertEquals(17.0, GoalTargetsStore.targetFor(GoalPreset.FAT_LOSS)!!, 1e-6)
        assertEquals(84.0, GoalTargetsStore.targetFor(GoalPreset.MAINTENANCE)!!, 1e-6)
        assertEquals(52.0, GoalTargetsStore.targetFor(GoalPreset.FITNESS)!!, 1e-6)
    }

    @Test
    fun `store save seam updates targets and reset restores defaults`() {
        GoalTargetsStore.save(GoalTargets(mapOf(GoalId.BODY_FAT to 15.5)))
        assertEquals(15.5, GoalTargetsStore.targetFor(GoalId.BODY_FAT)!!, 1e-6)
        assertNull(GoalTargetsStore.targetFor(GoalId.WEIGHT))

        GoalTargetsStore.reset()
        assertEquals(84.0, GoalTargetsStore.targetFor(GoalId.WEIGHT)!!, 1e-6)
    }

    @Test
    fun `formatting formats units correctly and handles null`() {
        assertEquals("26.3%", formatGoalValue(26.3, GoalId.BODY_FAT))
        assertEquals("17.0%", formatGoalValue(17.0, GoalId.BODY_FAT))

        assertEquals("84.0 kg", formatGoalValue(84.0, GoalId.WEIGHT, UnitSystem.METRIC))
        assertEquals("185.2 lb", formatGoalValue(84.0, GoalId.WEIGHT, UnitSystem.IMPERIAL))

        assertEquals("52 bpm", formatGoalValue(52.0, GoalId.RHR_TREND))
        assertEquals("52 bpm", formatGoalValue(52.4, GoalId.RHR_TREND))

        assertEquals("—", formatGoalValue(null, GoalId.BODY_FAT))
        assertEquals("—", formatGoalValue(null, GoalId.WEIGHT))
        assertEquals("—", formatGoalValue(null, GoalId.RHR_TREND))
    }

    @Test
    fun `currentPrimaryMetricValue returns null on empty records or no match`() {
        assertNull(currentPrimaryMetricValue(emptyList(), GoalId.BODY_FAT))
        assertNull(currentPrimaryMetricValue(emptyList(), GoalPreset.FAT_LOSS))

        val unrelated = listOf(
            HealthRecord(
                app = "com.fit",
                start = "2026-10-09T08:00:00Z",
                data = buildJsonObject { put("count", JsonPrimitive(5000)) }
            )
        )
        assertNull(currentPrimaryMetricValue(unrelated, GoalId.BODY_FAT))
        assertNull(currentPrimaryMetricValue(unrelated, GoalId.WEIGHT))
    }

    @Test
    fun `currentPrimaryMetricValue extracts latest body fat ignoring invalid and blocked`() {
        val records = listOf(
            HealthRecord(
                app = "com.elink.fittrackhealth.pro",
                start = "2026-10-09T07:00:00Z",
                data = buildJsonObject { put("percentage", JsonPrimitive(26.5)) }
            ),
            HealthRecord(
                app = "com.elink.fittrackhealth.pro",
                start = "2026-10-09T09:00:00Z",
                data = buildJsonObject { put("percentage", JsonPrimitive(26.3)) }
            ),
            // Ignored app
            HealthRecord(
                app = "com.myzone.myzoneble",
                start = "2026-10-09T10:00:00Z",
                data = buildJsonObject { put("percentage", JsonPrimitive(12.0)) }
            ),
            // Out of physiological range (> 60)
            HealthRecord(
                app = "com.elink.fittrackhealth.pro",
                start = "2026-10-09T11:00:00Z",
                data = buildJsonObject { put("percentage", JsonPrimitive(75.0)) }
            )
        )
        val latestBf = currentPrimaryMetricValue(records, GoalPreset.FAT_LOSS)
        assertEquals(26.3, latestBf!!, 1e-6)
    }

    @Test
    fun `currentPrimaryMetricValue extracts latest weight`() {
        val records = listOf(
            HealthRecord(
                app = "com.elink.fittrackhealth.pro",
                start = "2026-10-09T07:00:00Z",
                data = buildJsonObject {
                    put("weight", buildJsonObject { put("inKilograms", JsonPrimitive(85.2)) })
                }
            ),
            HealthRecord(
                app = "com.elink.fittrackhealth.pro",
                start = "2026-10-09T08:30:00Z",
                data = buildJsonObject {
                    put("weight", buildJsonObject { put("inKilograms", JsonPrimitive(84.9)) })
                }
            )
        )
        val latestW = currentPrimaryMetricValue(records, GoalPreset.MAINTENANCE)
        assertEquals(84.9, latestW!!, 1e-6)
    }

    @Test
    fun `currentPrimaryMetricValue extracts resting heart rate`() {
        val records = listOf(
            HealthRecord(
                app = "com.google.android.apps.fitness",
                start = "2026-10-09T06:00:00Z",
                data = buildJsonObject { put("beatsPerMinute", JsonPrimitive(54)) }
            ),
            HealthRecord(
                app = "com.google.android.apps.fitness",
                start = "2026-10-09T07:00:00Z",
                data = buildJsonObject { put("beatsPerMinute", JsonPrimitive(51)) }
            )
        )
        val latestRhr = currentPrimaryMetricValue(records, GoalPreset.FITNESS)
        assertEquals(51.0, latestRhr!!, 1e-6)
    }
}
