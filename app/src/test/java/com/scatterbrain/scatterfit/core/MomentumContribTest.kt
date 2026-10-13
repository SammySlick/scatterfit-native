package com.scatterbrain.scatterfit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Acceptance pins for weekContributions (port of the web's weekContributions,
 * L audit 2026-10-06: only series with data enter the denominator; parts sum
 * exactly to the renormalised week score).
 *
 * Real-calendar discipline: windows are taken from the LAST N days of each
 * series, so wall-clock can never drift into the assertions.
 */
class MomentumContribTest {

    private fun sd(score: Double?) = GoalDay("2026-10-06", score)

    private fun series(id: GoalId, name: String, vararg scores: Double?) =
        GoalSeries(id, name, scores.map { sd(it) }, scores.lastOrNull() ?: 0.0, "why")

    // FAT_LOSS table: BODY_FAT 10, CALORIES 8, UNITS 7, PROTEIN 6, EXERCISE 5, WEIGHT 0
    private val weights = GOAL_WEIGHTS[GoalPreset.FAT_LOSS]!!

    @Test fun `untracked series never enter the denominator`() {
        val s = listOf(
            series(GoalId.BODY_FAT, "Body fat", 80.0, 80.0),
            series(GoalId.PROTEIN, "Protein", 50.0, null),
        )
        val c = weekContributions(s, weights, 2)
        // Protein has data? No - one null score but one 50 -> it participates.
        // Use a fully-null series instead:
        val none = listOf(
            series(GoalId.BODY_FAT, "Body fat", 80.0, 80.0),
            series(GoalId.PROTEIN, "Protein", null, null),
        )
        val c2 = weekContributions(none, weights, 2)
        assertEquals(listOf(GoalId.BODY_FAT), c2.map { it.id })
        assertEquals(100.0, c2[0].share, 0.0001)
    }

    @Test fun `parts sum exactly to the renormalised week score`() {
        val s = listOf(
            series(GoalId.BODY_FAT, "Body fat", 90.0, 90.0),
            series(GoalId.CALORIES, "Calories", 60.0, 60.0),
            series(GoalId.EXERCISE, "Exercise", null, 40.0),
        )
        val c = weekContributions(s, weights, 2)
        val week = weekScoreFromContributions(c)!!
        // manual renormalised week score over participating series
        val expected = (10.0 * 90.0 + 8.0 * 60.0 + 5.0 * 40.0) / (10.0 + 8.0 + 5.0)
        assertEquals(expected, week, 0.0001)
        assertEquals(expected, c.sumOf { it.part }, 0.0001)
    }

    @Test fun `zero-weight series never lift the score`() {
        val with = listOf(series(GoalId.BODY_FAT, "Body fat", 80.0, 80.0))
        val c = weekContributions(with, weights, 2)
        assertEquals(80.0, weekScoreFromContributions(c)!!, 0.0001)
        assertTrue(c.none { it.id == GoalId.WEIGHT })
    }

    @Test fun `shares sum to 100`() {
        val s = listOf(
            series(GoalId.BODY_FAT, "Body fat", 70.0, 90.0),
            series(GoalId.CALORIES, "Calories", 55.0, 65.0),
            series(GoalId.PROTEIN, "Protein", 80.0, null, 75.0),
        )
        val c = weekContributions(s, weights, 3)
        assertEquals(100.0, c.sumOf { it.share }, 0.0001)
    }

    @Test fun `loggedDays counts only scored days in the window`() {
        val s = listOf(
            series(GoalId.BODY_FAT, "Body fat", 70.0, null, 90.0),
            series(GoalId.EXERCISE, "Exercise", null, null),
        )
        val c = weekContributions(s, weights, 3)
        assertEquals(2, c.first { it.id == GoalId.BODY_FAT }.loggedDays)
        assertTrue(c.none { it.id == GoalId.EXERCISE })
    }

    @Test fun `empty input is empty`() {
        assertTrue(weekContributions(emptyList(), weights, 7).isEmpty())
    }
}
