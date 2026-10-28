package com.scatterbrain.scatterfit.core

import com.scatterbrain.scatterfit.data.Zones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutDetectionTest {

    private fun generateMinuteSamples(
        startMin: Int,
        endMin: Int,
        bpm: Double,
        baseMs: Long = 1700000000000L
    ): List<Pair<Long, Double>> {
        return (startMin until endMin).map { min ->
            (baseMs + min * 60000L) to bpm
        }
    }

    @Test
    fun `ninety minute match with halftime merges into one session`() {
        val baseMs = 1700000000000L
        val bounds = Zones.DEFAULT_ZONES
        val samples = generateMinuteSamples(0, 45, 155.0, baseMs) +
                generateMinuteSamples(45, 57, 95.0, baseMs) +
                generateMinuteSamples(57, 90, 158.0, baseMs)

        val candidates = detectWorkoutCandidates(samples, bounds)
        assertEquals(1, candidates.size)
        val c = candidates.first()
        assertTrue("Duration should be ~78-90 min", c.features.durationMin in 78..90)
        assertEquals(WorkoutKind.OTHER, c.kind)
        assertNull(c.features.cadenceSpm)
    }

    @Test
    fun `walk with a twenty minute stop splits into two`() {
        val baseMs = 1700000000000L
        val bounds = Zones.DEFAULT_ZONES
        val samples = generateMinuteSamples(0, 20, 135.0, baseMs) +
                generateMinuteSamples(20, 40, 90.0, baseMs) +
                generateMinuteSamples(40, 60, 136.0, baseMs)

        // Splits into two because each 20-minute segment is below the 25-minute floor (MERGE_MIN_SEGMENT_MIN),
        // not because of the gap length.
        val candidates = detectWorkoutCandidates(samples, bounds)
        assertEquals(2, candidates.size)
        assertEquals(20, candidates[0].features.durationMin)
        assertEquals(20, candidates[1].features.durationMin)
    }

    @Test
    fun `five minute pause does not split`() {
        val baseMs = 1700000000000L
        val bounds = Zones.DEFAULT_ZONES
        val samples = generateMinuteSamples(0, 20, 150.0, baseMs) +
                generateMinuteSamples(20, 25, 100.0, baseMs) +
                generateMinuteSamples(25, 45, 150.0, baseMs)

        val candidates = detectWorkoutCandidates(samples, bounds)
        assertEquals(1, candidates.size)
        assertEquals(45, candidates.first().features.durationMin)
    }

    @Test
    fun `run versus walk by cadence`() {
        val baseMs = 1700000000000L
        val bounds = Zones.DEFAULT_ZONES
        val durationMin = 20
        val samples = generateMinuteSamples(0, durationMin, 150.0, baseMs)

        // 145 spm -> RUN
        val runBuckets = (0 until durationMin).map { min ->
            (baseMs + min * 60000L) to 145
        }
        val runCandidates = detectWorkoutCandidates(samples, bounds, runBuckets)
        assertEquals(1, runCandidates.size)
        assertEquals(WorkoutKind.RUN, runCandidates.first().kind)
        assertEquals(145.0, runCandidates.first().features.cadenceSpm!!, 0.001)

        // 95 spm -> WALK
        val walkBuckets = (0 until durationMin).map { min ->
            (baseMs + min * 60000L) to 95
        }
        val walkCandidates = detectWorkoutCandidates(samples, bounds, walkBuckets)
        assertEquals(1, walkCandidates.size)
        assertEquals(WorkoutKind.WALK, walkCandidates.first().kind)
        assertEquals(95.0, walkCandidates.first().features.cadenceSpm!!, 0.001)
    }

    @Test
    fun `short burst under ten minutes discarded`() {
        val baseMs = 1700000000000L
        val bounds = Zones.DEFAULT_ZONES
        val samples = generateMinuteSamples(0, 8, 160.0, baseMs)

        val candidates = detectWorkoutCandidates(samples, bounds)
        assertTrue("Short burst under 10 min must be discarded", candidates.isEmpty())
    }

    @Test
    fun `long run with sparse samples does not invent coverage`() {
        val baseMs = 1700000000000L
        val bounds = Zones.DEFAULT_ZONES
        // Samples every 10 minutes: 0, 10, 20, 30, 40, 50, 60
        val samples = (0..60 step 10).map { min ->
            (baseMs + min * 60000L) to 160.0
        }

        val candidates = detectWorkoutCandidates(samples, bounds)
        assertTrue("Sparse samples every 10 min must not produce candidate", candidates.isEmpty())
    }

    @Test
    fun `two long sessions whose union exceeds the ceiling stay separate`() {
        val baseMs = 1700000000000L
        val bounds = Zones.DEFAULT_ZONES
        // Two 80-minute sessions at 150bpm separated by a 20-minute gap -> union 180 min > 150 min
        val samples = generateMinuteSamples(0, 80, 150.0, baseMs) +
                generateMinuteSamples(80, 100, 90.0, baseMs) +
                generateMinuteSamples(100, 180, 150.0, baseMs)

        val candidates = detectWorkoutCandidates(samples, bounds)
        assertEquals("Two long sessions exceeding ceiling must stay separate", 2, candidates.size)
        assertEquals(80, candidates[0].features.durationMin)
        assertEquals(80, candidates[1].features.durationMin)
    }

    @Test
    fun `sixteen minute halftime still merges into one session`() {
        val baseMs = 1700000000000L
        val bounds = Zones.DEFAULT_ZONES
        // 0-45min at 155bpm, 45-61min at 95bpm (16 min — past REST_END_MIN so the half genuinely ends),
        // 61-106min at 158bpm -> exactly ONE candidate, duration ~90-106 minutes.
        val samples = generateMinuteSamples(0, 45, 155.0, baseMs) +
                generateMinuteSamples(45, 61, 95.0, baseMs) +
                generateMinuteSamples(61, 106, 158.0, baseMs)

        val candidates = detectWorkoutCandidates(samples, bounds)
        assertEquals(1, candidates.size)
        val c = candidates.first()
        assertTrue("Duration should be ~90-106 min", c.features.durationMin in 90..106)
        assertEquals(WorkoutKind.OTHER, c.kind)
    }

    @Test
    fun `union of exactly 150 minutes still merges`() {
        val baseMs = 1700000000000L
        val bounds = Zones.DEFAULT_ZONES
        // Two 65-minute sessions at 150bpm separated by a 20-minute gap -> union 150 min
        val samples = generateMinuteSamples(0, 65, 150.0, baseMs) +
                generateMinuteSamples(65, 85, 90.0, baseMs) +
                generateMinuteSamples(85, 150, 150.0, baseMs)

        val candidates = detectWorkoutCandidates(samples, bounds)
        assertEquals(1, candidates.size)
        assertEquals(150, candidates.first().features.durationMin)
    }

    @Test
    fun `classifyByCadence classifies 30-min candidate based on step counts`() {
        val baseMs = 1700000000000L
        val candidate = WorkoutCandidate(
            features = WorkoutFeatures(
                startMs = baseMs,
                endMs = baseMs + 30 * 60000L,
                durationMin = 30,
                meanHr = 150.0,
                peakHr = 165.0,
                pctTimeInZ3Plus = 0.7,
                cadenceSpm = null,
                hrSpikiness = 4.0
            ),
            kind = WorkoutKind.OTHER
        )

        // 4400 steps in span -> RUN (146.6 spm >= 130)
        val runBuckets = listOf(baseMs to 4400)
        val runCandidate = classifyByCadence(candidate, runBuckets)
        assertEquals(WorkoutKind.RUN, runCandidate.kind)
        assertEquals(146.66, runCandidate.features.cadenceSpm!!, 0.1)

        // 2700 steps in span -> WALK (90 spm)
        val walkBuckets = listOf(baseMs to 2700)
        val walkCandidate = classifyByCadence(candidate, walkBuckets)
        assertEquals(WorkoutKind.WALK, walkCandidate.kind)
        assertEquals(90.0, walkCandidate.features.cadenceSpm!!, 0.001)

        // 900 steps in span -> OTHER (30 spm)
        val slowBuckets = listOf(baseMs to 900)
        val slowCandidate = classifyByCadence(candidate, slowBuckets)
        assertEquals(WorkoutKind.OTHER, slowCandidate.kind)
        assertEquals(30.0, slowCandidate.features.cadenceSpm!!, 0.001)

        // Empty bucket list -> OTHER (kind unchanged, cadence null)
        val emptyCandidate = classifyByCadence(candidate, emptyList())
        assertEquals(WorkoutKind.OTHER, emptyCandidate.kind)
        assertNull(emptyCandidate.features.cadenceSpm)
    }

    @Test
    fun `classifyByCadence ignores buckets outside candidate span`() {
        val baseMs = 1700000000000L
        val candidate = WorkoutCandidate(
            features = WorkoutFeatures(
                startMs = baseMs,
                endMs = baseMs + 30 * 60000L,
                durationMin = 30,
                meanHr = 150.0,
                peakHr = 165.0,
                pctTimeInZ3Plus = 0.7,
                cadenceSpm = null,
                hrSpikiness = 4.0
            ),
            kind = WorkoutKind.OTHER
        )

        val bucketsWithOutsideNoise = listOf(
            (baseMs - 10 * 60000L) to 3000,               // 10 min before start: ignored
            baseMs to 2700,                               // inside span: 2700 steps
            (baseMs + 30 * 60000L + 10 * 60000L) to 5000 // 10 min after end: ignored
        )

        val result = classifyByCadence(candidate, bucketsWithOutsideNoise)
        assertEquals(WorkoutKind.WALK, result.kind)
        assertEquals(90.0, result.features.cadenceSpm!!, 0.001)
    }
}
