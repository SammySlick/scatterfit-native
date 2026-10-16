package com.scatterbrain.scatterfit.ui

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineStorageTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `round-trip encode and decode preserves Routine structure with repsMin and repsMax`() {
        val exercise1 = RoutineExercise(
            exerciseId = "barbell-bench-press",
            exerciseName = "Barbell Bench Press",
            tempo = "2-0-2-1",
            isTimed = false,
            sets = listOf(
                PlannedSet(type = "warmup", weightKg = 40.0, repsMin = 10, repsMax = 12),
                PlannedSet(type = "normal", weightKg = 80.0, repsMin = 8, repsMax = null),
                PlannedSet(type = "failure", weightKg = 85.0, repsMin = 6, repsMax = 6)
            )
        )
        val routine = Routine(
            id = "routine-1",
            name = "Push Day",
            exercises = listOf(exercise1),
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val encoded = json.encodeToString(listOf(routine))
        val decoded = json.decodeFromString<List<Routine>>(encoded)

        assertEquals(1, decoded.size)
        val loaded = decoded.first()
        assertEquals("routine-1", loaded.id)
        assertEquals("Push Day", loaded.name)
        assertEquals(1, loaded.exercises.size)
        val ex = loaded.exercises.first()
        assertEquals("2-0-2-1", ex.tempo)
        assertFalse(ex.isTimed)
        assertEquals(3, ex.sets.size)
        assertEquals("warmup", ex.sets[0].type)
        assertEquals(40.0, ex.sets[0].weightKg!!, 0.001)
        assertEquals(10, ex.sets[0].repsMin)
        assertEquals(12, ex.sets[0].repsMax)
        assertEquals(8, ex.sets[1].repsMin)
        assertNull(ex.sets[1].repsMax)
    }

    @Test
    fun `custom exercise survives round-trip`() {
        val customEx = ExerciseRef(
            id = "custom-123",
            name = "Landmine Press",
            muscle = "other",
            isCustom = true
        )

        val encoded = json.encodeToString(customEx)
        val decoded = json.decodeFromString<ExerciseRef>(encoded)

        assertEquals("custom-123", decoded.id)
        assertEquals("Landmine Press", decoded.name)
        assertEquals("other", decoded.muscle)
        assertTrue(decoded.isCustom)
    }

    @Test
    fun `upsert replaces routine with same id or appends new`() {
        val r1 = Routine(id = "r1", name = "Legs A")
        val r2 = Routine(id = "r2", name = "Legs B")

        val list = mutableListOf(r1)

        // Upsert r2 -> appends
        val i2 = list.indexOfFirst { it.id == r2.id }
        if (i2 >= 0) list[i2] = r2 else list.add(r2)
        assertEquals(2, list.size)

        // Upsert r1 modified -> replaces
        val r1Updated = r1.copy(name = "Legs Heavy")
        val i1 = list.indexOfFirst { it.id == r1Updated.id }
        if (i1 >= 0) list[i1] = r1Updated else list.add(r1Updated)
        assertEquals(2, list.size)
        assertEquals("Legs Heavy", list.find { it.id == "r1" }?.name)
    }

    @Test
    fun `delete removes routine by id`() {
        val r1 = Routine(id = "r1", name = "Upper")
        val r2 = Routine(id = "r2", name = "Lower")
        val list = mutableListOf(r1, r2)

        list.removeAll { it.id == "r1" }
        assertEquals(1, list.size)
        assertEquals("r2", list.first().id)
        assertNull(list.find { it.id == "r1" })
    }

    @Test
    fun `migration test - decode old JSON shape with reps maps to repsMin`() {
        val oldJson = """
            [
              {
                "id": "old-routine-1",
                "name": "Pull Day",
                "exercises": [
                  {
                    "exerciseId": "lat-pulldown",
                    "exerciseName": "Lat Pulldown",
                    "sets": [
                      {
                        "type": "normal",
                        "weightKg": 60.0,
                        "reps": 10
                      }
                    ]
                  }
                ],
                "createdAt": 1000,
                "updatedAt": 2000
              }
            ]
        """.trimIndent()

        val decoded = decodeRoutinesJson(oldJson)
        assertEquals(1, decoded.size)
        val r = decoded.first()
        val ex = r.exercises.first()
        assertEquals(0, ex.restSeconds)
        assertNull(ex.note)
        assertNull(ex.customName)
        assertNull(ex.tempo)
        assertFalse(ex.isTimed)
        assertEquals("sec", ex.timeUnit)
        assertEquals("Lat Pulldown", ex.displayName)

        val set = ex.sets.first()
        assertEquals(10, set.repsMin)
        assertNull(set.repsMax)
        assertNull(set.timeSecondsMin)
        assertEquals("sec", set.timeUnit)
        assertNull(set.note)
        assertNull(set.difficulty)
    }

    @Test
    fun `repsLabel formats min only, min-max range, and empty`() {
        assertEquals("8", repsLabel(PlannedSet(repsMin = 8)))
        assertEquals("8-12", repsLabel(PlannedSet(repsMin = 8, repsMax = 12)))
        assertEquals("12", repsLabel(PlannedSet(repsMax = 12)))
        assertEquals("", repsLabel(PlannedSet()))
    }

    @Test
    fun `timeLabel formats seconds in clock format`() {
        assertEquals("00:30", timeLabel(PlannedSet(timeSecondsMin = 30)))
        assertEquals("00:45", timeLabel(PlannedSet(timeSecondsMin = 45)))
        assertEquals("", timeLabel(PlannedSet()))
    }

    @Test
    fun `formatClockTime and displayTimeLabel format clock style MM SS and H MM SS`() {
        assertEquals("00:30", formatClockTime(30))
        assertEquals("00:59", formatClockTime(59))
        assertEquals("01:30", formatClockTime(90))
        assertEquals("09:59", formatClockTime(599))
        assertEquals("1:00:00", formatClockTime(3600))
        assertEquals("1:30:00", formatClockTime(5400))
        assertEquals("2:00:00", formatClockTime(7200))

        assertEquals("00:30", displayTimeLabel(30))
        assertEquals("01:30", displayTimeLabel(90))
        assertEquals("09:59", displayTimeLabel(599))
        assertEquals("1:00:00", displayTimeLabel(3600))
        assertEquals("1:30:00", displayTimeLabel(5400))
        assertEquals("2:00:00", displayTimeLabel(7200))
        assertEquals("", displayTimeLabel(null))
    }

    @Test
    fun `timeLabelScaled formats sec min hour correctly with rounding`() {
        // sec unit
        assertEquals("30 secs", timeLabelScaled(PlannedSet(timeSecondsMin = 30), "sec"))

        // min unit
        assertEquals("90 mins", timeLabelScaled(PlannedSet(timeSecondsMin = 5400), "min"))
        assertEquals("1.5 mins", timeLabelScaled(PlannedSet(timeSecondsMin = 90), "min"))

        // hour unit
        assertEquals("1.5 hours", timeLabelScaled(PlannedSet(timeSecondsMin = 5400), "hour"))
        assertEquals("0.1 hours", timeLabelScaled(PlannedSet(timeSecondsMin = 30), "hour"))

        // empty
        assertEquals("", timeLabelScaled(PlannedSet(), "hour"))
    }

    @Test
    fun `tempo formatting converts parts with blanks to 0 or null if all blank`() {
        assertEquals("2-0-2-1", formatTempo("2", "0", "2", "1"))
        assertEquals("3-0-0-0", formatTempo("3", "", "", ""))
        assertEquals("0-1-0-2", formatTempo("", "1", "", "2"))
        assertNull(formatTempo("", "", "", ""))
        assertNull(formatTempo("   ", "", "", ""))

        val parsed = parseTempo("2-0-2-1")
        assertEquals(listOf("2", "0", "2", "1"), parsed)
        assertEquals(listOf("", "", "", ""), parseTempo(null))
    }

    @Test
    fun `timed exercise round-trip serialises timeSeconds and collapses legacy max`() {
        val timedEx = RoutineExercise(
            exerciseId = "plank",
            exerciseName = "Plank",
            isTimed = true,
            tempo = null,
            sets = listOf(
                PlannedSet(type = "normal", timeSecondsMin = 45),
                PlannedSet(type = "normal", timeSecondsMin = 60)
            )
        )
        val routine = Routine(name = "Core", exercises = listOf(timedEx))

        val encoded = json.encodeToString(listOf(routine))
        val decoded = decodeRoutinesJson(encoded)

        assertEquals(1, decoded.size)
        val ex = decoded.first().exercises.first()
        assertTrue(ex.isTimed)
        assertEquals(45, ex.sets[0].timeSecondsMin)
        assertEquals(60, ex.sets[1].timeSecondsMin)
    }

    @Test
    fun `legacy time ranges decode collapsed to single value`() {
        val legacyJson = """
        [
          {
            "id": "r1",
            "name": "Upper A",
            "exercises": [
              {
                "exerciseId": "dead-hangs",
                "exerciseName": "Dead Hangs",
                "isTimed": true,
                "sets": [
                  {"type": "normal", "timeSecondsMin": 30, "timeSecondsMax": 50},
                  {"type": "normal", "timeSecondsMin": 90, "timeSecondsMax": null}
                ]
              }
            ]
          }
        ]
        """.trimIndent()

        val decoded = decodeRoutinesJson(legacyJson)
        val sets = decoded.first().exercises.first().sets
        assertEquals(30, sets[0].timeSecondsMin)
        assertNull(sets[0].timeSecondsMax)
        assertEquals(90, sets[1].timeSecondsMin)
    }

    @Test
    fun `displayTimeEntry formats sub-minute hour as 0 point 008 and scales correctly`() {
        assertEquals("0.008", displayTimeEntry(30, "hour"))
        assertEquals("30", displayTimeEntry(30, "sec"))
        assertEquals("0.5", displayTimeEntry(30, "min"))
        assertEquals("1.5", displayTimeEntry(90, "min"))
        assertEquals("1.5", displayTimeEntry(5400, "hour"))
        assertEquals("2", displayTimeEntry(7200, "hour"))
        assertEquals("", displayTimeEntry(null, "hour"))
    }

    @Test
    fun `round-trip store and display matches exact values`() {
        // sec round-trip for 1..7200
        val testSecs = listOf(1, 15, 30, 45, 60, 90, 120, 300, 1800, 3600, 5400, 7200)
        for (sec in testSecs) {
            val displayed = timeEntryText(sec, "sec")
            val restored = timeEntryToSeconds(displayed, "sec")
            assertEquals(sec, restored)
        }

        // min round-trip for representative values
        val testMins = listOf(30, 60, 90, 120, 180, 600, 3600, 5400)
        for (sec in testMins) {
            val displayed = timeEntryText(sec, "min")
            val restored = timeEntryToSeconds(displayed, "min")
            assertEquals(sec, restored)
        }

        // hour round-trip for hour values
        val testHours = listOf(1800, 3600, 5400, 7200)
        for (sec in testHours) {
            val displayed = timeEntryText(sec, "hour")
            val restored = timeEntryToSeconds(displayed, "hour")
            assertEquals(sec, restored)
        }
    }

    @Test
    fun `re-scale on unit change converts 30 secs to 0 point 5 min`() {
        val rawSec = 30
        assertEquals("30", timeEntryText(rawSec, "sec"))
        assertEquals("0.5", timeEntryText(rawSec, "min"))
        assertEquals("0.008", timeEntryText(rawSec, "hour"))
    }

    @Test
    fun `blank and invalid inputs to timeEntryToSeconds return null`() {
        assertNull(timeEntryToSeconds("", "sec"))
        assertNull(timeEntryToSeconds("   ", "min"))
        assertNull(timeEntryToSeconds("abc", "hour"))
        assertNull(timeEntryToSeconds("-5", "sec"))
    }

    @Test
    fun `customName displays instead of library name when set, falls back when blank`() {
        val exWithCustom = RoutineExercise(
            exerciseId = "lat-pulldown",
            exerciseName = "Lat Pulldown",
            customName = "Cable D-Handle Pulldown"
        )
        assertEquals("Cable D-Handle Pulldown", exWithCustom.displayName)

        val exBlankCustom = RoutineExercise(
            exerciseId = "lat-pulldown",
            exerciseName = "Lat Pulldown",
            customName = "   "
        )
        assertEquals("Lat Pulldown", exBlankCustom.displayName)

        val exNullCustom = RoutineExercise(
            exerciseId = "lat-pulldown",
            exerciseName = "Lat Pulldown",
            customName = null
        )
        assertEquals("Lat Pulldown", exNullCustom.displayName)
    }

    @Test
    fun `summary counts sets by muscle correctly`() {
        val exercises = listOf(
            RoutineExercise(
                exerciseId = "barbell-bench-press",
                exerciseName = "Barbell Bench Press",
                sets = listOf(PlannedSet(), PlannedSet(), PlannedSet())
            ),
            RoutineExercise(
                exerciseId = "incline-dumbbell-bench-press",
                exerciseName = "Incline DB Bench Press",
                sets = listOf(PlannedSet(), PlannedSet())
            ),
            RoutineExercise(
                exerciseId = "skull-crusher",
                exerciseName = "Skull Crusher",
                sets = listOf(PlannedSet(), PlannedSet(), PlannedSet())
            )
        )

        val lookup: (String) -> String = { id ->
            when (id) {
                "barbell-bench-press", "incline-dumbbell-bench-press" -> "chest"
                "skull-crusher" -> "triceps"
                else -> "other"
            }
        }

        val muscleSets = calculateSetsByMuscle(exercises, lookup)
        assertEquals(5, muscleSets["chest"])
        assertEquals(3, muscleSets["triceps"])
    }

    @Test
    fun `calculateRoutineEstimateMinutes rounds to nearest 5 minutes`() {
        // Exercise 1: 3 sets, rest = 60s -> 3 * 40 + 2 * 60 = 240s (4 min)
        // Exercise 2: 3 sets, rest = 60s -> 3 * 40 + 2 * 60 = 240s (4 min)
        // Total = 480s = 8 min -> rounds to 10 min
        val exercises = listOf(
            RoutineExercise(
                exerciseId = "ex1",
                exerciseName = "Ex 1",
                restSeconds = 60,
                sets = listOf(PlannedSet(), PlannedSet(), PlannedSet())
            ),
            RoutineExercise(
                exerciseId = "ex2",
                exerciseName = "Ex 2",
                restSeconds = 60,
                sets = listOf(PlannedSet(), PlannedSet(), PlannedSet())
            )
        )
        val est = calculateRoutineEstimateMinutes(exercises)
        assertEquals(10, est)
    }

    @Test
    fun `formatExerciseSetsSummary collapses identical sets and expands differing sets`() {
        // Identical rep-based sets collapse to compact form with kg suffix
        val exIdentical = RoutineExercise(
            exerciseId = "bench",
            exerciseName = "Bench Press",
            sets = listOf(
                PlannedSet(weightKg = 60.0, repsMin = 8, repsMax = 12),
                PlannedSet(weightKg = 60.0, repsMin = 8, repsMax = 12),
                PlannedSet(weightKg = 60.0, repsMin = 8, repsMax = 12),
                PlannedSet(weightKg = 60.0, repsMin = 8, repsMax = 12)
            )
        )
        val identicalSummary = formatExerciseSetsSummary(exIdentical)
        assertEquals(1, identicalSummary.size)
        assertEquals("4 × 8-12 @ 60 kg", identicalSummary.first())

        // Identical timed sets collapse to auto-scaled clock format
        val exTimedIdentical = RoutineExercise(
            exerciseId = "plank",
            exerciseName = "Plank",
            isTimed = true,
            sets = listOf(
                PlannedSet(timeSecondsMin = 30),
                PlannedSet(timeSecondsMin = 30),
                PlannedSet(timeSecondsMin = 30)
            )
        )
        val timedSummary = formatExerciseSetsSummary(exTimedIdentical)
        assertEquals(1, timedSummary.size)
        assertEquals("3 × 00:30", timedSummary.first())

        // Differing sets expand to per-set lines with kg suffix
        val exDiffering = RoutineExercise(
            exerciseId = "squat",
            exerciseName = "Squat",
            sets = listOf(
                PlannedSet(type = "warmup", weightKg = 40.0, repsMin = 12),
                PlannedSet(type = "normal", weightKg = 60.0, repsMin = 8, repsMax = 12, note = "slow")
            )
        )
        val differingSummary = formatExerciseSetsSummary(exDiffering)
        assertEquals(2, differingSummary.size)
        assertEquals("Set 1 (warmup): 40 kg × 12", differingSummary[0])
        assertEquals("Set 2: 60 kg × 8-12 · slow", differingSummary[1])

        // Empty sets render 'Set N: —' instead of collapsing to count
        val exEmpty = RoutineExercise(
            exerciseId = "curl",
            exerciseName = "Bicep Curl",
            sets = listOf(
                PlannedSet(),
                PlannedSet()
            )
        )
        val emptySummary = formatExerciseSetsSummary(exEmpty)
        assertEquals(2, emptySummary.size)
        assertEquals("Set 1: —", emptySummary[0])
        assertEquals("Set 2: —", emptySummary[1])

        // Pluralization: single set without weight or target
        val exSingle = RoutineExercise(
            exerciseId = "shrug",
            exerciseName = "Shrug",
            sets = listOf(PlannedSet(type = "normal", weightKg = 50.0))
        )
        val singleSummary = formatExerciseSetsSummary(exSingle)
        assertEquals("1 × 50 kg", singleSummary.first())
    }
}
