package com.scatterbrain.scatterfit.ui

import com.scatterbrain.scatterfit.core.GOAL_WEIGHTS
import com.scatterbrain.scatterfit.core.GoalPreset
import com.scatterbrain.scatterfit.core.ScoringSettings
import com.scatterbrain.scatterfit.data.AssembledDaily
import com.scatterbrain.scatterfit.data.ZoneBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * The spider graph moved from the retired Matrix page into the Weekly Check-in.
 * These pin the rule the move could have quietly broken:
 *
 *   "metrics without data get a deliberate ghost/hollow axis state (never zero —
 *    untracked must not look like failing)" — Momentum Rework Spec v1, 2026-10-25.
 *
 * The canvas picks dashed-vs-solid from `loggedDays > 0 && mean != null`, so that
 * pair IS the rule. If the builder ever fills in 0.0 for an untracked metric the
 * graph starts drawing a collapsed spoke in a failing colour and nothing notices.
 *
 * NOTE: the axes come from GOAL_WEIGHTS (the raw preset table), not
 * effectiveWeights. Steps and Sleep carry no entry in FAT_LOSS, so they get no
 * axis under that goal at all — that is existing behaviour, preserved by the move.
 */
class MomentumSpiderTest {

    private val anchor = "2026-10-25"
    private val zone = ZoneId.of("UTC")
    private val fixedClock = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), zone)
    private val bounds = ZoneBounds(130, 145, 160, 175)

    private fun source(daily: AssembledDaily, preset: GoalPreset = GoalPreset.FAT_LOSS) =
        TodayFacadeMomentumSource(
            daily = daily,
            settings = ScoringSettings(activeGoal = preset),
            zone = zone,
            clock = fixedClock,
        )

    private fun emptyDaily() = AssembledDaily(zoneBounds = bounds, zone = zone)

    @Test
    fun `an empty app produces a spoke per weighted goal, none of them with data`() {
        val spokes = buildSpokeItems(source(emptyDaily()), windowDays = 7, anchorDay = anchor)

        val weightedGoals = GOAL_WEIGHTS[GoalPreset.FAT_LOSS]!!.filter { it.value > 0.0 }
        assertEquals(
            "One spoke per weighted goal — the axis count comes from here",
            weightedGoals.size,
            spokes.size,
        )

        // The ghost-axis precondition. A zero here would draw every spoke
        // collapsed to the centre in a failing colour on a new install.
        spokes.forEach { spoke ->
            assertNull("${spoke.id} has no data, so mean must be null not 0.0", spoke.mean)
            assertEquals("${spoke.id} has no data, so loggedDays must be 0", 0, spoke.loggedDays)
        }
    }

    @Test
    fun `a metric with logged calories reports data, so its axis is solid`() {
        val daily = AssembledDaily(
            caloriesEaten = mapOf(anchor to 2_000.0),
            zoneBounds = bounds,
            zone = zone,
        )

        val calories = buildSpokeItems(source(daily), windowDays = 7, anchorDay = anchor)
            .first { it.name == "Calories" }

        assertTrue("Calories were logged, so the axis must be solid", calories.loggedDays > 0)
        assertNotNull("Calories were logged, so the spoke must have a mean", calories.mean)
    }

    @Test
    fun `one logged metric leaves the other axes as ghosts`() {
        val daily = AssembledDaily(
            caloriesEaten = mapOf(anchor to 2_000.0),
            zoneBounds = bounds,
            zone = zone,
        )

        val spokes = buildSpokeItems(source(daily), windowDays = 7, anchorDay = anchor)
        val logged = spokes.filter { it.loggedDays > 0 && it.mean != null }
        val ghosts = spokes.filter { it.loggedDays == 0 }

        assertEquals("Only calories had data", 1, logged.size)
        assertEquals("Calories", logged.first().name)
        assertEquals(
            "Every other spoke stays a ghost — logging one metric must not make the rest look measured",
            spokes.size - 1,
            ghosts.size,
        )
        ghosts.forEach { assertNull("${it.id} is untracked, mean must stay null", it.mean) }
    }

    @Test
    fun `spokes follow the active goal preset, not a fixed list`() {
        val fatLoss = buildSpokeItems(source(emptyDaily(), GoalPreset.FAT_LOSS), 7, anchor)
        val fitter = buildSpokeItems(source(emptyDaily(), GoalPreset.FITNESS), 7, anchor)

        val fitterGoals = GOAL_WEIGHTS[GoalPreset.FITNESS]!!.filter { it.value > 0.0 }
        assertEquals("Axes must come from the fitness preset", fitterGoals.size, fitter.size)

        assertTrue(
            "Changing goal must redraw the graph — FAT_LOSS and FITNESS are different shapes",
            fatLoss.map { it.id } != fitter.map { it.id },
        )
    }

    @Test
    fun `a metric weighted zero is not an axis`() {
        // Weight carries 0.0 on FAT_LOSS (a soft target), so it must not appear
        // as a spoke. A soft-zero rendered as an axis would read as a failing metric.
        val spokes = buildSpokeItems(source(emptyDaily(), GoalPreset.FAT_LOSS), 7, anchor)
        assertTrue(
            "Weight is 0-weighted on fat loss and must not get an axis",
            spokes.none { it.weight == 0.0 },
        )
    }
}
