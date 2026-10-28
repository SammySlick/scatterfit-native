package com.scatterbrain.scatterfit.data

import com.scatterbrain.scatterfit.core.WorkoutKind
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutProposalsTest {

    @Test
    fun `proposedType mapping for the three kinds`() {
        assertEquals("Run", mapProposedType(WorkoutKind.RUN))
        assertEquals("Walk", mapProposedType(WorkoutKind.WALK))
        assertEquals("Workout", mapProposedType(WorkoutKind.OTHER))

        assertEquals("Run", mapProposedType("RUN"))
        assertEquals("Walk", mapProposedType("WALK"))
        assertEquals("Workout", mapProposedType("OTHER"))
        assertEquals("Workout", mapProposedType("UNKNOWN"))
    }

    @Test
    fun `hasSignificantOverlap calculates 50 percent and 30 percent overlap correctly`() {
        val baseMs = 1700000000000L
        val startA = baseMs
        val endA = baseMs + 30 * 60000L // 30 min duration

        // Overlap of 15 min (50% of 30 min) -> true
        val start50 = baseMs + 15 * 60000L
        val end50 = baseMs + 45 * 60000L
        assertTrue("50% overlap must trigger significant overlap", hasSignificantOverlap(startA, endA, start50, end50, 0.5))

        // Overlap of 9 min (30% of 30 min) -> false
        val start30 = baseMs + 21 * 60000L
        val end30 = baseMs + 51 * 60000L
        assertFalse("30% overlap must not trigger significant overlap", hasSignificantOverlap(startA, endA, start30, end30, 0.5))
    }

    @Test
    fun `overlap suppression skips 50 percent overlap and keeps 30 percent overlap`() = kotlinx.coroutines.runBlocking {
        val storage = WorkoutProposalsStorage()
        val nowMs = 1700000000000L
        val baseMs = nowMs - 2 * 3600 * 1000L // 2h ago

        val existingProposal = WorkoutProposal(
            id = "existing-1",
            dayKey = "2023-11-14",
            startMs = baseMs,
            endMs = baseMs + 30 * 60000L,
            durationMin = 30,
            kind = "RUN",
            proposedType = "Run",
            meanHr = 150.0,
            peakHr = 165.0,
            pctTimeInZ3Plus = 0.8,
            cadenceSpm = 145.0,
            createdAtMs = nowMs
        )
        storage.save(listOf(existingProposal))

        // 1. Candidate with 50% overlap (15 min overlap with existing 30 min proposal)
        val hrRecords50 = (0 until 30).map { min ->
            val t = baseMs + (15 + min) * 60000L
            val iso = java.time.Instant.ofEpochMilli(t).toString()
            HealthRecord(start = iso, end = iso, data = buildJsonObject { put("bpm", 155.0) })
        }
        val created50 = WorkoutProposalDetector.processSyncProposals(
            hrRecords = hrRecords50,
            stepRecords = emptyList(),
            exerciseRecords = emptyList(),
            storage = storage,
            nowMs = nowMs
        )
        assertTrue("50% overlap should produce no new proposals", created50.isEmpty())
        assertEquals("Storage should still have only 1 proposal", 1, storage.load().size)

        // 2. Candidate with 30% overlap (9 min overlap with existing 30 min proposal)
        val hrRecords30 = (0 until 30).map { min ->
            val t = baseMs + (21 + min) * 60000L
            val iso = java.time.Instant.ofEpochMilli(t).toString()
            HealthRecord(start = iso, end = iso, data = buildJsonObject { put("bpm", 155.0) })
        }
        val created30 = WorkoutProposalDetector.processSyncProposals(
            hrRecords = hrRecords30,
            stepRecords = emptyList(),
            exerciseRecords = emptyList(),
            storage = storage,
            nowMs = nowMs
        )
        assertEquals("30% overlap should produce a new proposal", 1, created30.size)
        assertEquals("Storage should now have 2 proposals", 2, storage.load().size)
    }

    @Test
    fun `processSyncProposals with rawStepReader reclassifies candidate to RUN`() = kotlinx.coroutines.runBlocking {
        val storage = WorkoutProposalsStorage()
        val nowMs = 1700000000000L
        val baseMs = nowMs - 2 * 3600 * 1000L // 2h ago

        val hrRecords = (0 until 30).map { min ->
            val t = baseMs + min * 60000L
            val iso = java.time.Instant.ofEpochMilli(t).toString()
            HealthRecord(start = iso, end = iso, data = buildJsonObject { put("bpm", 155.0) })
        }

        val proposals = WorkoutProposalDetector.processSyncProposals(
            hrRecords = hrRecords,
            stepRecords = emptyList(), // No daily step buckets -> initially OTHER
            exerciseRecords = emptyList(),
            storage = storage,
            nowMs = nowMs,
            rawStepReader = { fromMs, toMs ->
                // Return 4400 steps over the span -> 146 spm
                listOf(
                    HealthRecord(
                        start = java.time.Instant.ofEpochMilli(baseMs).toString(),
                        data = buildJsonObject { put("count", 4400) }
                    )
                )
            }
        )

        assertEquals(1, proposals.size)
        val p = proposals.first()
        assertEquals("RUN", p.kind)
        assertEquals("Run", p.proposedType)
        assertEquals(146.66, p.cadenceSpm!!, 0.1)
    }

    @Test
    fun `proposal 8 days old is removed by pruneExpired while 6 day old one is kept`() {
        val storage = WorkoutProposalsStorage()
        val nowMs = 1700000000000L
        val dayMs = 24L * 3600L * 1000L

        val eightDaysOld = WorkoutProposal(
            id = "old-8d",
            dayKey = "2023-11-06",
            startMs = nowMs - 8 * dayMs,
            endMs = nowMs - 8 * dayMs + 30 * 60000L,
            durationMin = 30,
            kind = "RUN",
            proposedType = "Run",
            meanHr = 150.0,
            peakHr = 160.0,
            pctTimeInZ3Plus = 0.5,
            cadenceSpm = 140.0,
            createdAtMs = nowMs - 8 * dayMs
        )

        val sixDaysOld = WorkoutProposal(
            id = "recent-6d",
            dayKey = "2023-11-08",
            startMs = nowMs - 6 * dayMs,
            endMs = nowMs - 6 * dayMs + 30 * 60000L,
            durationMin = 30,
            kind = "WALK",
            proposedType = "Walk",
            meanHr = 135.0,
            peakHr = 140.0,
            pctTimeInZ3Plus = 0.0,
            cadenceSpm = 95.0,
            createdAtMs = nowMs - 6 * dayMs
        )

        storage.save(listOf(eightDaysOld, sixDaysOld))
        assertEquals(2, storage.load().size)

        val kept = storage.pruneExpired(nowMs)
        assertEquals(1, kept.size)
        assertEquals("recent-6d", kept.first().id)
        assertEquals(1, storage.load().size)
        assertEquals("recent-6d", storage.load().first().id)
    }

    @Test
    fun `stepBucketsFromRecords parses both flat and nested samples into minute buckets`() {
        val baseMs = 1700000000000L
        val min0 = (baseMs / 60000L) * 60000L
        val min1 = min0 + 60000L

        // Flat record
        val r1 = HealthRecord(
            start = java.time.Instant.ofEpochMilli(min0 + 10000L).toString(),
            data = buildJsonObject { put("count", 50) }
        )

        // Second flat record in same minute
        val r2 = HealthRecord(
            start = java.time.Instant.ofEpochMilli(min0 + 20000L).toString(),
            data = buildJsonObject { put("count", 30) }
        )

        // Record in next minute
        val r3 = HealthRecord(
            start = java.time.Instant.ofEpochMilli(min1 + 5000L).toString(),
            data = buildJsonObject { put("count", 75) }
        )

        val buckets = Zones.stepBucketsFromRecords(listOf(r1, r2, r3))
        assertEquals(2, buckets.size)
        assertEquals(min0 to 80, buckets[0])
        assertEquals(min1 to 75, buckets[1])
    }
}
