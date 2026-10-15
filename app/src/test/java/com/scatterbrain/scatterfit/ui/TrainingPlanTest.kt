package com.scatterbrain.scatterfit.ui

import org.junit.Assert.assertFalse
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
}
