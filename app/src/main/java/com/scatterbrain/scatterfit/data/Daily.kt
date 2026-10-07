package com.scatterbrain.scatterfit.data

import com.scatterbrain.scatterfit.core.dayKeyFromIso
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.ZoneId

/** Full assembled DailyData (web types.ts DailyData). The scoring engine's
 *  core DailyData is the trimmed view of this. */
data class AssembledDaily(
    val steps: Map<String, Double> = emptyMap(),
    val caloriesEaten: Map<String, Double> = emptyMap(),
    val protein: Map<String, Double> = emptyMap(),
    val carbs: Map<String, Double> = emptyMap(),
    val fat: Map<String, Double> = emptyMap(),
    val caloriesBurned: Map<String, Double> = emptyMap(),
    val weight: Map<String, Double> = emptyMap(),
    val restingHr: Map<String, Double> = emptyMap(),
    val latestHeartRate: Pair<Double, String>? = null,
    val trainingMinutes: Map<String, Double> = emptyMap(),
    val zoneBounds: ZoneBounds,
    val maxHr: Double? = null,
    val hrZones: Map<String, ZoneDay> = emptyMap(),
    val sleep: Map<String, SleepNight> = emptyMap(),
    val sleepNights: List<MergedNight> = emptyList(),
    val sessions: List<ExerciseSession> = emptyList(),
    val bodyFat: Map<String, Double> = emptyMap(),
    val stepsSource: Map<String, String> = emptyMap(),
    val distance: Map<String, Double> = emptyMap(),
    val distanceSource: Map<String, String> = emptyMap(),
    val burnedSource: Map<String, String> = emptyMap(),
    val drinks: Map<String, Boolean> = emptyMap(),
    val foodKcal: Map<String, Double> = emptyMap(),
    val alcoholKcal: Map<String, Double> = emptyMap(),
    val alcoholUnits: Map<String, Double> = emptyMap(),
    val meals: Map<String, List<MealEntry>> = emptyMap(),
    /** Local zone used for all day keys (device TZ at the call site). */
    val zone: ZoneId,
)

data class SleepNight(
    val day: String,
    val totalMin: Double,
    val deepMin: Double,
    val remMin: Double,
    val lightMin: Double,
    val awakeMin: Double,
    val end: String,
)

data class ExerciseSession(val day: String, val minutes: Double, val title: String, val start: String)

data class MealEntry(
    val name: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val meal: String,
    val time: String?,
    val source: String,
)

/** Derived per-day HR stats cache shape (web types.ts HrDayStats). */
data class HrDayStats(
    val exerciseMinutes: Double,
    val z2: Double,
    val z3: Double,
    val z4: Double,
    val z5: Double,
    val z3Run20: Boolean = false,
    val sampleCoverage: Double,
    val rhr: Double? = null,
)

/** DailyData assembly from record lists. Port of web src/lib/daily.ts. */
object Daily {

    /** Full assembly (one local zone threaded through — the web reads the
     *  runtime TZ; Foundations pins native day keys to the device zone). */
    fun buildDailyData(
        steps: List<HealthRecord>,
        nutrition: List<HealthRecord>,
        burned: List<HealthRecord>,
        weight: List<HealthRecord>,
        restingHr: List<HealthRecord>,
        sleep: List<HealthRecord>,
        exercise: List<HealthRecord>,
        bodyFat: List<HealthRecord>,
        distance: List<HealthRecord> = emptyList(),
        foodLog: List<HealthRecord> = emptyList(),
        heartRate: List<HealthRecord> = emptyList(),
        zones: ZoneBounds,
        zone: ZoneId,
    ): AssembledDaily {
        // Steps, distance and burned kcal: several devices record the same
        // activity. Sum within each data origin per day (deduping identical
        // windows), then take the MAX across origins — never the sum.
        val (stepMap, stepSrc) = Normalise.maxAcrossOrigins(steps, zone) { r -> Normalise.num(r.data?.get("count")) }
        val (distMap, distSrc) = Normalise.maxAcrossOrigins(distance, zone) { r -> Normalise.normaliseDistanceM(r.data ?: JsonObject(emptyMap())) }
        val (burnMap, burnSrc) = Normalise.maxAcrossOrigins(burned, zone) { r -> Normalise.normaliseEnergyKcal(r.data ?: JsonObject(emptyMap())) }

        // ---- Nutrition: parse flat + double-nested shapes, dedupe across sources.
        data class Food(
            val day: String, val name: String, val meal: String,
            val kcal: Double?, val p: Double?, val c: Double?, val f: Double?,
            val app: String, val startMs: Long, val realTime: Boolean,
            val start: String, val precision: Int,
        )

        val foods = mutableListOf<Food>()
        for (r in nutrition + foodLog) {
            val outer = r.data ?: JsonObject(emptyMap())
            val inner = (outer["data"] as? JsonObject) ?: JsonObject(emptyMap())
            val data = JsonObject(outer + inner)
            val kcal = Normalise.normaliseEnergyKcal(data)
                ?: Normalise.num(data["kcal"]) ?: Normalise.num(data["calories"])
            val p = Normalise.grams(data["protein"])
            val c = Normalise.grams(data["carbohydrates"] ?: data["carbs"])
            val f = Normalise.grams(data["fat"] ?: data["totalFat"])
            val startMs = parseMillis(r.start) ?: -1L
            val endMs = parseMillis(r.end) ?: -1L
            val dt = if (startMs >= 0) Instant.ofEpochMilli(startMs).atZone(zone) else null
            val midnightStamp = dt != null && dt.hour == 0 && dt.minute == 0 && dt.second == 0
            val wideWindow = endMs >= 0 && endMs - startMs > 2 * 3_600_000L
            val macros = listOf(p, c, f).filterNotNull()
            val precision = macros.size * 10 + macros.count { it != it.toInt().toDouble() }
            val name = ((data["name"] ?: data["foodName"] ?: data["title"]) as? JsonPrimitive)
                ?.takeIf { it.isString }?.content?.trim().orEmpty().ifEmpty { "Food entry" }
            val meal = ((data["meal"] ?: data["mealType"] ?: data["mealName"]) as? JsonPrimitive)
                ?.takeIf { it.isString }?.content?.trim().orEmpty()
            foods.add(
                Food(
                    dayKeyFromIso(r.start, zone), name, meal,
                    kcal, p, c, f, r.app ?: "", startMs,
                    startMs >= 0 && !midnightStamp && !wideWindow,
                    r.start, precision,
                ),
            )
        }
        fun isDup(a: Food, b: Food): Boolean {
            if (a.day != b.day) return false
            if (a.meal.isEmpty() || !a.meal.equals(b.meal, ignoreCase = true)) return false
            if (a.kcal == null || b.kcal == null) return false
            val hi = maxOf(a.kcal, b.kcal)
            if (hi > 0 && Math.abs(a.kcal - b.kcal) > hi * 0.2) return false
            if (a.realTime && b.realTime) return Math.abs(a.startMs - b.startMs) <= 15 * 60_000.0
            return true
        }
        val kept = mutableListOf<Food>()
        for (food in foods) {
            val i = kept.indexOfFirst { isDup(it, food) }
            if (i < 0) { kept.add(food); continue }
            val prev = kept[i]
            val pair = if (food.precision != prev.precision)
                if (food.precision > prev.precision) food to prev else prev to food
            else
                if (food.realTime && !prev.realTime) food to prev else prev to food
            // Winner keeps its macros; borrow the loser's real time if it has none.
            kept[i] = if (!pair.first.realTime && pair.second.realTime)
                pair.first.copy(realTime = true, start = pair.second.start, startMs = pair.second.startMs)
            else pair.first
        }
        val caloriesEaten = LinkedHashMap<String, Double>()
        val protein = LinkedHashMap<String, Double>()
        val carbs = LinkedHashMap<String, Double>()
        val fat = LinkedHashMap<String, Double>()
        val meals = LinkedHashMap<String, MutableList<MealEntry>>()
        fun add(map: MutableMap<String, Double>, key: String, value: Double) {
            if (value.isNaN()) return
            map[key] = (map[key] ?: 0.0) + value
        }
        for (food in kept) {
            food.kcal?.let { add(caloriesEaten, food.day, it) }
            food.p?.let { add(protein, food.day, it) }
            food.c?.let { add(carbs, food.day, it) }
            food.f?.let { add(fat, food.day, it) }
            meals.getOrPut(food.day) { mutableListOf() }.add(
                MealEntry(food.name, food.kcal ?: 0.0, food.p ?: 0.0, food.c ?: 0.0, food.f ?: 0.0, food.meal, if (food.realTime) food.start else null, Normalise.sourceName(food.app)),
            )
        }
        for (list in meals.values) {
            list.sortWith(compareBy({ it.time == null }, { it.time ?: "" }))
        }

        // ---- Weight: per app per day keep the LAST reading, then prefer the
        // trusted Hume scale, else the latest remaining app. Myzone ignored.
        val perAppDay = LinkedHashMap<String, Quad<String, String, Long, Double>>()
        for (r in weight) {
            val app = r.app ?: ""
            if (app in Normalise.IGNORED_WEIGHT_APPS) continue
            val v = Normalise.normaliseWeightKg(r.data ?: JsonObject(emptyMap())) ?: continue
            val k = dayKeyFromIso(r.start, zone)
            val t = parseMillis(r.start) ?: continue
            val id = "$app|$k"
            val prev = perAppDay[id]
            if (prev == null || t >= prev.third) perAppDay[id] = Quad(app, k, t, v)
        }
        val weightPick = LinkedHashMap<String, Triple<Boolean, Long, Double>>()
        for (e in perAppDay.values) {
            val hume = e.first == Normalise.TRUSTED_WEIGHT_APP
            val cur = weightPick[e.second]
            if (cur == null || (hume && !cur.first) || (hume == cur.first && e.third > cur.second)) {
                weightPick[e.second] = Triple(hume, e.third, e.fourth)
            }
        }
        val weightOut = weightPick.mapValues { it.value.third }

        // ---- Body fat: Myzone writes fake 0%; drop anything physiologically invalid.
        val bfLatest = LinkedHashMap<String, Pair<Long, Double>>()
        for (r in bodyFat) {
            if ((r.app ?: "") in Normalise.IGNORED_WEIGHT_APPS) continue
            val v = Normalise.num(r.data?.get("percentage")) ?: continue
            if (v <= 0 || v > 60) continue
            val k = dayKeyFromIso(r.start, zone)
            val t = parseMillis(r.start) ?: continue
            val prev = bfLatest[k]
            if (prev == null || t > prev.first) bfLatest[k] = t to v
        }
        val bodyFatOut = bfLatest.mapValues { it.value.second }

        val restingHrOut = LinkedHashMap<String, Double>()
        for (r in restingHr) {
            val v = Normalise.num(r.data?.get("beatsPerMinute") ?: r.data?.get("bpm"))
            if (v != null) restingHrOut[dayKeyFromIso(r.start, zone)] = v
        }

        val latest = Zones.hrSamples(heartRate).lastOrNull()
        val latestHeartRate = latest?.let { it.second to iso(it.first) }

        // ---- Sleep: keyed by wake day; a split night contributes only its
        // longest single session, never a sum.
        val sleepNights = Sleep.buildSleepNights(sleep, zone)
        val sleepOut = LinkedHashMap<String, SleepNight>()
        for (n in sleepNights) {
            val validSessions = n.sessions.filter { it.totalMin <= Sleep.MAX_NIGHT_MIN }
            val src = if (n.split) validSessions.maxByOrNull { it.totalMin } else
                SleepSlice(n.start, n.end, n.totalMin, n.deepMin, n.remMin, n.lightMin, n.awakeMin, n.hasStages, n.stages)
            src ?: continue
            val k = dayKeyFromIso(src.end, zone)
            val night = SleepNight(k, src.totalMin, src.deepMin, src.remMin, src.lightMin, src.awakeMin, src.end)
            val prev = sleepOut[k]
            if (prev == null || night.totalMin > prev.totalMin) sleepOut[k] = night
        }

        // ---- Sessions
        val sessions = exercise.mapNotNull { r ->
            val mins = Sleep.minutesBetween(r.start, r.end)
            if (mins <= 0) return@mapNotNull null
            val d = r.data ?: JsonObject(emptyMap())
            val t = ((d["title"] ?: d["exerciseName"]) as? JsonPrimitive)?.takeIf { it.isString }?.content
            val title = if (t != null && t.isNotEmpty()) t
            else if (r.app != null) "Workout (${r.app})" else "Workout"
            ExerciseSession(dayKeyFromIso(r.start, zone), mins, title, r.start)
        }.sortedBy { parseMillis(it.start) ?: 0L }

        // ---- HR zones + training minutes + max HR
        val hrZones = Zones.zoneBreakdownByDay(heartRate, zones, zone)
        val trainingMinutes = Zones.trainingMinutesFromZones(hrZones, exercise, zone)
        val mh = Zones.maxHrFromRecords(heartRate, zone)

        return AssembledDaily(
            steps = stepMap, caloriesEaten = caloriesEaten, protein = protein, carbs = carbs, fat = fat,
            caloriesBurned = burnMap, weight = weightOut, restingHr = restingHrOut,
            latestHeartRate = latestHeartRate, trainingMinutes = trainingMinutes, zoneBounds = zones,
            maxHr = if (mh.second) mh.first else null, hrZones = hrZones,
            sleep = sleepOut, sleepNights = sleepNights, sessions = sessions,
            bodyFat = bodyFatOut, stepsSource = stepSrc, distance = distMap,
            distanceSource = distSrc, burnedSource = burnSrc,
            drinks = emptyMap(), foodKcal = emptyMap(), alcoholKcal = emptyMap(), alcoholUnits = emptyMap(),
            meals = meals, zone = zone,
        )
    }

    /** 120-day window (web HISTORY_DAYS). */
    const val HISTORY_DAYS = 120

    fun buildDailyDataFromMaps(
        records: Map<RecordMethod, List<HealthRecord>>,
        zones: ZoneBounds,
        zone: ZoneId,
        hrDerived: Map<String, HrDayStats?>? = null,
        burn: BurnConfig? = null,
        nowMs: Long,
    ): AssembledDaily {
        val since = parseMillis(daysAgoKey(HISTORY_DAYS, zone, nowMs)) ?: 0L
        fun inWindow(m: RecordMethod): List<HealthRecord> {
            val list = Records.clean(m, records[m])
            return list.filter { r ->
                (parseMillis(r.start) ?: -1L) >= since || (parseMillis(r.end) ?: -1L) >= since
            }
        }
        val exerciseRecords = inWindow(RecordMethod.EXERCISE_SESSION)
        val out = buildDailyData(
            steps = inWindow(RecordMethod.STEPS),
            nutrition = inWindow(RecordMethod.NUTRITION),
            burned = inWindow(RecordMethod.TOTAL_CALORIES_BURNED),
            weight = inWindow(RecordMethod.WEIGHT),
            restingHr = inWindow(RecordMethod.RESTING_HEART_RATE),
            sleep = inWindow(RecordMethod.SLEEP_SESSION),
            exercise = exerciseRecords,
            bodyFat = inWindow(RecordMethod.BODY_FAT),
            distance = inWindow(RecordMethod.DISTANCE),
            foodLog = inWindow(RecordMethod.FOOD_LOG),
            heartRate = inWindow(RecordMethod.HEART_RATE),
            zones = zones, zone = zone,
        )
        // Days whose raw HR is absent but whose derived stats are cached still
        // get their zone minutes, training minutes and RHR — charts render
        // instantly from the derived cache and upgrade when raw HR lands.
        if (hrDerived != null) {
            val mutableZones = out.hrZones.toMutableMap()
            val restingHr = out.restingHr.toMutableMap()
            for ((d, s) in hrDerived) {
                if (s == null) continue
                val z = mutableZones.getOrPut(d) {
                    ZoneDay(0.0, 0.0, 0.0, 0.0, 0.0, s.z3Run20, s.sampleCoverage, null)
                }
                if (s.z3Run20) mutableZones[d] = z.copy(z3Run20 = true)
                val cur = mutableZones[d]!!
                if (cur.z2 == 0.0 && cur.z3 == 0.0 && cur.z4 == 0.0 && cur.z5 == 0.0 && cur.exerciseMinutes == 0.0) {
                    val merged = cur.copy(
                        z2 = s.z2, z3 = s.z3, z4 = s.z4, z5 = s.z5,
                        exerciseMinutes = s.exerciseMinutes,
                        sampleCoverage = maxOf(cur.sampleCoverage, s.sampleCoverage),
                    )
                    mutableZones[d] = merged
                }
                // RHR comes from the SOURCE (restingHeartRate records) or the
                // internal maths (5th percentile of raw HR samples, ≥500-sample
                // gate). A day with neither is NO DATA (a gap).
                if (restingHr[d] == null && s.rhr != null) restingHr[d] = s.rhr
            }
            val trainingMinutes = Zones.trainingMinutesFromZones(mutableZones, exerciseRecords, zone)
            return out.copy(hrZones = mutableZones, restingHr = restingHr, trainingMinutes = trainingMinutes)
        }
        if (burn != null) {
            // Derived burn covers EVERY day in the window: zone days use BMR +
            // zone activity, no-HR days use the activity-setting fallback.
            var t = since
            val end = parseMillis(todayKeyFor(zone, nowMs))!! + 86_400_000L
            val burned = out.caloriesBurned.toMutableMap()
            val burnedSource = out.burnedSource.toMutableMap()
            while (t < end) {
                val k = dayKeyFromIso(iso(t), zone)
                val (kcal, source) = Burn.derivedBurnKcal(burn, out.hrZones[k])
                burned[k] = kcal
                burnedSource[k] = source
                t += 86_400_000L
            }
            return out.copy(caloriesBurned = burned, burnedSource = burnedSource)
        }
        return out
    }

    /** Local date N days before the day of nowMs, as a bare date key. */
    fun daysAgoKey(days: Int, zone: ZoneId, nowMs: Long): String {
        val local = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().minusDays(days.toLong())
        return local.toString()
    }

    fun todayKeyFor(zone: ZoneId, nowMs: Long): String =
        Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toString()
}

/** Tiny tuple helpers (kotlin stdlib caps at 5 with Pair/Triple). */
data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
