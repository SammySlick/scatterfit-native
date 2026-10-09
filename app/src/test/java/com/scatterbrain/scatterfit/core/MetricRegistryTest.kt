package com.scatterbrain.scatterfit.core

import com.scatterbrain.scatterfit.data.AssembledDaily
import com.scatterbrain.scatterfit.data.SleepNight
import com.scatterbrain.scatterfit.data.ZoneBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

/** Acceptance: MetricRegistry mirrors web METRICS[] — web vitest metric-set
 *  cases are the acceptance tests (Native Foundations port rule). */
class MetricRegistryTest {

    private val zone = ZoneId.of("UTC")
    private val dayKey = "2026-10-10"

    private fun daily(
        steps: Double? = null, weight: Double? = null, sleep: Double? = null,
        kcal: Double? = null, protein: Double? = null, units: Double? = null,
        rhr: Double? = null, minutes: Double? = null, burned: Double? = null,
    ): AssembledDaily = AssembledDaily(
        steps = steps?.let { mapOf(dayKey to it) } ?: emptyMap(),
        caloriesEaten = kcal?.let { mapOf(dayKey to it) } ?: emptyMap(),
        protein = protein?.let { mapOf(dayKey to it) } ?: emptyMap(),
        weight = weight?.let { mapOf(dayKey to it) } ?: emptyMap(),
        restingHr = rhr?.let { mapOf(dayKey to it) } ?: emptyMap(),
        trainingMinutes = minutes?.let { mapOf(dayKey to it) } ?: emptyMap(),
        caloriesBurned = burned?.let { mapOf(dayKey to it) } ?: emptyMap(),
        sleep = sleep?.let {
            mapOf(dayKey to SleepNight(dayKey, it * 60.0, 0.0, 0.0, 0.0, 0.0, "08:00"))
        } ?: emptyMap(),
        alcoholUnits = units?.let { mapOf(dayKey to it) } ?: emptyMap(),
        zoneBounds = ZoneBounds(0, 0, 0, 0),
        zone = zone,
    )

    private fun value(id: MetricId, d: AssembledDaily): Double? = MetricRegistry.byId(id).valueFrom(d, dayKey)

    // --- Web parity: the tab set and its order ---
    @Test
    fun `tabs match the web metric set in web order`() {
        val labels = MetricRegistry.TABS.map { it.label }
        assertEquals(
            listOf("Steps", "Body Comp", "Calories", "Macros", "Alcohol", "Sleep", "HR Zones", "Resting HR"),
            labels,
        )
    }

    @Test
    fun `calories burned has no tab of its own`() {
        val burned = MetricRegistry.byId(MetricId.CALORIES_BURNED)
        assertFalse(burned.hasTab)
        assertFalse(MetricRegistry.TABS.any { it.id == MetricId.CALORIES_BURNED })
    }

    // --- valueFrom: registry and assembler are the single pipe ---
    @Test
    fun `steps value comes from the assembled day map`() {
        assertEquals(8420.0, value(MetricId.STEPS, daily(steps = 8420.0))!!, 1e-9)
    }

    @Test
    fun `sleep is exposed in hours, not minutes`() {
        assertEquals(6.0, value(MetricId.SLEEP, daily(sleep = 6.0))!!, 1e-9)
    }

    @Test
    fun `burned routes through the merged burned entry`() {
        assertEquals(2340.0, value(MetricId.CALORIES_BURNED, daily(burned = 2340.0))!!, 1e-9)
    }

    @Test
    fun `missing day is a gap not a zero`() {
        val d = daily(steps = 8420.0)
        assertNull(MetricRegistry.byId(MetricId.STEPS).valueFrom(d, "2026-10-01"))
    }

    @Test
    fun `every def survives an empty day without throwing`() {
        val empty = daily()
        for (def in MetricRegistry.ALL) assertNull(def.valueFrom(empty, dayKey))
    }

    // --- format: honest em-dash, registry-driven decimals ---
    @Test
    fun `format renders dashes for null values`() {
        assertEquals("—", MetricRegistry.format(MetricRegistry.byId(MetricId.STEPS), null))
    }

    @Test
    fun `format renders units per the registry entry`() {
        assertEquals("84.2 kg", MetricRegistry.format(MetricRegistry.byId(MetricId.WEIGHT), 84.2))
        assertEquals("8420", MetricRegistry.format(MetricRegistry.byId(MetricId.STEPS), 8420.0))
        assertEquals("2.0 units", MetricRegistry.format(MetricRegistry.byId(MetricId.ALCOHOL_UNITS), 2.0))
    }
}
