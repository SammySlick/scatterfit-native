package com.scatterbrain.scatterfit.data

import com.scatterbrain.scatterfit.core.lastNDayKeys
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.roundToInt

/**
 * DEMO MONTH — Kotlin port of web `src/lib/demo.ts`. A deterministic, messy
 * 30-day month for a 35y male, 178 cm, ~80 kg aiming for 79 kg: dry week,
 * binge weekend, 5 bad days, a scale spike, sleep deficits after drinking,
 * missed weigh-ins. Same seed, same calendar, same numbers as web.
 * Never persisted or synced.
 */
const val DEMO_DAYS = 30
private const val SEED = 0x5ca77e5

private val WEAR = "com.google.android.apps.fitness"
private val HEVY = "com.hevy"
private val SCALE = SOURCE_HUME
private val FOOD = "com.myfitnesspal.android"

/** The five library drinks the demo pours (mirrors drinks-library.json). */
private val DEMO_DRINKS = mapOf(
    "stella-artois--pint" to DrinkInfo("stella-artois--pint", "Stella Artois", "Pint", 2.6, 227, 4.5),
    "-camden-hells--440ml-can" to DrinkInfo("-camden-hells--440ml-can", " Camden Hells", "440ml Can", 2.02, 176, 2.05),
    "peroni-nastro-azzurro--pint" to DrinkInfo("peroni-nastro-azzurro--pint", "Peroni Nastro Azzurro", "Pint", 2.9, 252, 4.9),
    "heineken--440ml-can" to DrinkInfo("heineken--440ml-can", "Heineken", "440ml Can", 1.89, 165, 1.78),
)

data class DrinkInfo(val id: String, val name: String, val serving: String, val units: Double, val kcal: Int, val price: Double)

data class DemoDrink(val day: String, val id: String, val name: String, val serving: String, val units: Double, val kcal: Int, val price: Double, val time: String)

data class DemoMonth(
    val records: Map<String, List<LocalRecord>>,
    val drinks: List<DemoDrink>,
    val keys: List<String>,
)

/** mulberry32 — bit-for-bit the web generator's PRNG. */
private class Rng(seed: Int) {
    var a = seed
    fun next(): Double {
        a += 0x6d2b79f5.toInt()
        var t = a
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + ((t xor (t ushr 7)) * (t or 61)))
        return (t xor (t ushr 14)).toLong().let { (it and 0xFFFF_FFFFL) / 4294967296.0 }
    }
}

private class DemoState(val zone: ZoneId, val r: Rng, val nowMs: Long) {
    fun dateOf(key: String): LocalDateTime {
        val p = key.split("-").map { it.toInt() }
        return LocalDate.of(p[0], p[1], p[2]).atTime(12, 0).atZone(zone).toLocalDateTime()
    }

    fun at(key: String, hour: Int, minute: Int = 0): Instant {
        val d = dateOf(key).toLocalDate().atStartOfDay(zone)
        return d.plusSeconds((hour * 60 + minute) * 60L).toInstant()
    }

    fun iso(t: Instant): String = t.toString()
    fun past(t: Instant): Boolean = t.toEpochMilli() <= nowMs
    fun between(lo: Double, hi: Double): Double = lo + r.next() * (hi - lo)
    fun dow(key: String): Int = dateOf(key).dayOfWeek.value % 7 // JS getDay(): Sun=0
    fun sign(x: Double): Int = if (x > 0) 1 else if (x < 0) -1 else 0
}

fun makeDemoRecords(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): DemoMonth {
    val keys = lastNDayKeys(DEMO_DAYS, endKey = com.scatterbrain.scatterfit.core.dayKey(now.toEpochMilli(), zone), zone = zone)
    val last = keys.size - 1
    val r = Rng(SEED)
    val nowMs = now.toEpochMilli()
    val s = DemoState(zone, r, nowMs)
    val dow = keys.map { s.dow(it) }

    val rec = mutableMapOf<String, MutableList<LocalRecord>>(
        RecordMethod.STEPS.name to mutableListOf(), RecordMethod.DISTANCE.name to mutableListOf(),
        RecordMethod.NUTRITION.name to mutableListOf(), RecordMethod.TOTAL_CALORIES_BURNED.name to mutableListOf(),
        RecordMethod.WEIGHT.name to mutableListOf(), RecordMethod.RESTING_HEART_RATE.name to mutableListOf(),
        RecordMethod.SLEEP_SESSION.name to mutableListOf(), RecordMethod.EXERCISE_SESSION.name to mutableListOf(),
        RecordMethod.BODY_FAT.name to mutableListOf(), RecordMethod.HEART_RATE.name to mutableListOf(),
    )

    // ---- calendar of events (index-based, deterministic) ----
    val fridays = keys.indices.filter { dow[it] == 5 }
    val bingeFri = fridays.firstOrNull { it >= 7 && it + 1 < last } ?: 8
    val bingeSat = bingeFri + 1
    val badStart = bingeSat + 1
    val badDays = setOf(0, 1, 2, 3, 4).map { badStart + it }.toSet()
    val spikeDay = minOf(last, bingeSat + 7)
    val midweek = (keys.indices).reversed().firstOrNull { dow[it] == 3 && it < last && it > badStart + 4 } ?: last - 3
    val sunday15k = keys.indices.firstOrNull { dow[it] == 0 && it !in badDays } ?: -1
    val lowStepDays = setOf(badStart + 2, keys.indices.firstOrNull { it > 2 && dow[it] == 2 } ?: -1)
    val missingSleep = minOf(last - 2, spikeDay + 2)

    val drinksByDay = mutableMapOf<Int, MutableList<DemoDrink>>()
    fun pour(i: Int, id: String, hour: Int, minute: Int) {
        val d = DEMO_DRINKS[id]!!
        drinksByDay.getOrPut(i) { mutableListOf() }
            .add(DemoDrink(keys[i], d.id, d.name, d.serving, d.units, d.kcal, d.price, s.iso(s.at(keys[i], hour, minute))))
    }
    pour(bingeFri, "stella-artois--pint", 19, 30)
    pour(bingeFri, "stella-artois--pint", 20, 45)
    pour(bingeSat, "-camden-hells--440ml-can", 18, 15)
    pour(bingeSat, "peroni-nastro-azzurro--pint", 21, 0)
    pour(midweek, "heineken--440ml-can", 19, 40)

    // ---- nutrition plan: 1-2 unlogged days a week, 1-2 high days a week ----
    val noFood = mutableSetOf(bingeSat)
    val high = mutableSetOf(bingeFri)
    var w = 0
    while (w < DEMO_DAYS) {
        val span = (w until minOf(w + 7, DEMO_DAYS)).filter { it < last }
        val pick = { avoid: Set<Int> -> span.filter { it !in avoid && it !in noFood && it !in high } }
        val nf = pick(emptySet())
        if (nf.isNotEmpty() && span.none { it in noFood }) noFood.add(nf[floor(r.next() * nf.size).toInt()])
        span.filter { dow[it] == 5 && it !in noFood }.forEach { high.add(it) }
        if (r.next() < 0.6) { val h = pick(emptySet()); if (h.isNotEmpty()) high.add(h[floor(r.next() * h.size).toInt()]) }
        w += 7
    }
    badDays.forEach { i -> if (r.next() < 0.35 && i !in noFood) high.add(i) }

    // ---- exercise plan: 2-3 lifts + 1-2 walks a week, none during the bad stretch ----
    val lifts = mutableSetOf<Int>()
    val walks = mutableSetOf<Int>()
    keys.indices.forEach { i ->
        if (i in badDays) return@forEach
        if (dow[i] in setOf(1, 3, 5) && r.next() < 0.8) lifts.add(i)
        else if ((dow[i] == 0 || dow[i] == 6) && r.next() < 0.55) walks.add(i)
        else if (dow[i] == 2 && r.next() < 0.3) walks.add(i)
    }
    if (sunday15k >= 0) walks.add(sunday15k)

    val shortNightAfter = high.map { it + 1 }.filter { r.next() < 0.5 || it == bingeFri + 1 }.toSet()

    var prevWeight = 0.0
    keys.forEachIndexed { i, key ->
        val isToday = i == last
        val weekend = dow[i] == 0 || dow[i] == 6

        // ---------- sleep (night ending this morning) ----------
        var sleptMin: Double? = null // web keeps the float; truncation flips poorSleep boundaries
        if (i != missingSleep) {
            val short = i in shortNightAfter
            val bedMin = ((if (short) s.between(60.0, 90.0) else s.between(-60.0, 75.0)) + 24 * 60)
            var durMin = if (short) s.between(300.0, 330.0) else s.between(400.0, 490.0)
            val wakeMin = bedMin + durMin
            val minWake = 24 * 60 + 6.5 * 60
            val maxWake = (24 * 60 + 9 * 60).toDouble()
            val wake = minOf(maxWake, maxOf(if (short) 0.0 else minWake, wakeMin))
            durMin = wake - bedMin
            val startD = s.at(key, 0, 0).plusMillis(((bedMin - 24 * 60) * 60_000).toLong())
            val endD = startD.plusMillis((durMin * 60_000).toLong())
            if (s.past(endD)) {
                val stages = mutableListOf<Triple<String, Instant, Instant>>()
                var t = startD
                fun push(stage: String, min: Double) {
                    if (min < 1) return
                    // web advances in MILLISECONDS (t + min*60_000); second-level
                    // truncation desynced every stage boundary
                    val ms = (min * 60_000).toLong()
                    stages.add(Triple(stage, t, t.plusMillis(ms)))
                    t = t.plusMillis(ms)
                }
                val deepTot = durMin * s.between(0.15, 0.2)
                val remTot = durMin * s.between(0.2, 0.25)
                val awakeTot = durMin * s.between(0.03, 0.06)
                val lightTot = durMin - deepTot - remTot - awakeTot
                val dw = listOf(0.36, 0.3, 0.2, 0.1, 0.04)
                val rw = listOf(0.1, 0.16, 0.2, 0.24, 0.3)
                push("LIGHT", 8 + r.next() * 6)
                for (c in 0 until 5) {
                    push("LIGHT", lightTot * 0.1)
                    push("DEEP", deepTot * dw[c])
                    push("LIGHT", lightTot * 0.08)
                    push("REM", remTot * rw[c])
                    if (c < 4) push("AWAKE", awakeTot * (if (c == 2) 0.35 else 0.15))
                }
                val remaining = (endD.toEpochMilli() - t.toEpochMilli()) / 60_000.0
                push("LIGHT", maxOf(1.0, remaining - awakeTot * 0.2))
                push("AWAKE", maxOf(1.0, (endD.toEpochMilli() - t.toEpochMilli()) / 60_000.0))
                rec[RecordMethod.SLEEP_SESSION.name]!!.add(
                    LocalRecord(s.iso(startD), s.iso(t), WEAR, mapOf(
                        "title" to "Sleep",
                        "stages" to stages.map { (stage, st, en) -> mapOf("startTime" to s.iso(st), "endTime" to s.iso(en), "stage" to stage) },
                    ))
                )
                sleptMin = durMin // keep the float — web never rounds it
            }
        }

        // ---------- alcohol the night before / RHR ----------
        val drankLastNight = drinksByDay[i - 1]?.isNotEmpty() == true
        val poorSleep = sleptMin == null || sleptMin < 360
        val rhr = (s.between(52.0, 58.0) + (if (drankLastNight) s.between(3.0, 5.0) else 0.0) + (if (poorSleep && !drankLastNight) s.between(2.0, 4.0) else 0.0)).roundToInt()
        val rhrAt = s.at(key, 7, 5)
        if (s.past(rhrAt)) rec[RecordMethod.RESTING_HEART_RATE.name]!!.add(LocalRecord(s.iso(rhrAt), s.iso(rhrAt), WEAR, mapOf("beatsPerMinute" to rhr)))

        // ---------- weight + body fat ----------
        val trend = 80.4 - (0.8 * i) / (DEMO_DAYS - 1)
        val spike = when (i) {
            spikeDay -> 1.2; spikeDay + 1 -> 0.75; spikeDay + 2 -> 0.4; spikeDay + 3 -> 0.15; else -> 0.0
        }
        val afterHigh = if (i - 1 in high) 0.35 else 0.0
        var wv = trend + spike + afterHigh + s.between(-0.6, 0.6)
        if (prevWeight != 0.0 && abs(wv - prevWeight) > 1.3 && spike == 0.0) wv = prevWeight + s.sign(wv - prevWeight) * 0.8
        prevWeight = wv
        val wAt = s.at(key, 7, 10 + floor(r.next() * 25).toInt())
        if (s.past(wAt) && !(i == missingSleep && r.next() < 0.5)) {
            rec[RecordMethod.WEIGHT.name]!!.add(LocalRecord(s.iso(wAt), s.iso(wAt), SCALE, mapOf("weight" to mapOf("inKilograms" to (wv * 100).roundToInt() / 100.0))))
            val bf = 20.4 + (wv - 80) * 0.45 - i * 0.012 + s.between(-0.45, 0.45)
            rec[RecordMethod.BODY_FAT.name]!!.add(LocalRecord(s.iso(wAt), s.iso(wAt), SCALE, mapOf("percentage" to (bf * 10).roundToInt() / 10.0)))
        }

        // ---------- steps ----------
        var steps = if (weekend) s.between(2000.0, 6000.0) else s.between(4000.0, 11000.0)
        if (i == sunday15k) steps = s.between(15500.0, 17500.0)
        if (i in lowStepDays) steps = s.between(1100.0, 1900.0)
        if (i in walks && i != sunday15k) steps += 3500
        val chunks = listOf(Triple(7, 12, 0.35), Triple(12, 17, 0.4), Triple(17, 22, 0.25))
        for ((a, b, frac) in chunks) {
            val start = s.at(key, a)
            val end = s.at(key, b)
            if (!s.past(start)) continue
            val portion = if (s.past(end)) 1.0 else (nowMs - start.toEpochMilli()) / (end.toEpochMilli() - start.toEpochMilli()).toDouble()
            val count = (steps * frac * portion).roundToInt()
            val endT = if (s.past(end)) end else now
            rec[RecordMethod.STEPS.name]!!.add(LocalRecord(s.iso(start), s.iso(endT), WEAR, mapOf("count" to count)))
            rec[RecordMethod.DISTANCE.name]!!.add(LocalRecord(s.iso(start), s.iso(endT), WEAR, mapOf("distance" to mapOf("inMeters" to (count * 0.76).roundToInt()))))
            rec[RecordMethod.TOTAL_CALORIES_BURNED.name]!!.add(LocalRecord(s.iso(start), s.iso(endT), WEAR, mapOf("energy" to mapOf("inKilocalories" to ((count * 0.04 + 40) * portion).roundToInt()))))
        }

        // ---------- exercise ----------
        fun hr(startD: Instant, mins: Int, lo: Int, hi: Int) {
            val samples = mutableListOf<Map<String, Any?>>()
            for (m in 0 until mins) {
                val t = startD.plusSeconds(m * 60L)
                if (!s.past(t)) break
                val wave = (sin(m / 3.0) + 1) / 2
                samples.add(mapOf("time" to s.iso(t), "beatsPerMinute" to (lo + (hi - lo) * wave + s.between(-4.0, 4.0)).roundToInt()))
            }
            if (samples.isNotEmpty()) rec[RecordMethod.HEART_RATE.name]!!.add(
                LocalRecord(samples.first()["time"] as String, samples.last()["time"] as String, WEAR, mapOf("samples" to samples))
            )
        }
        if (i in lifts) {
            val start = s.at(key, if (weekend) 10 else 18, 15 + floor(r.next() * 30).toInt())
            val mins = s.between(48.0, 72.0).roundToInt()
            val end = start.plusSeconds(mins * 60L)
            if (s.past(end)) {
                val title = listOf("Push day", "Pull day", "Legs", "Upper body")[floor(r.next() * 4).toInt()]
                rec[RecordMethod.EXERCISE_SESSION.name]!!.add(LocalRecord(s.iso(start), s.iso(end), HEVY, mapOf("title" to title, "exerciseType" to 70)))
                rec[RecordMethod.TOTAL_CALORIES_BURNED.name]!!.add(LocalRecord(s.iso(start), s.iso(end), HEVY, mapOf("energy" to mapOf("inKilocalories" to (mins * 6.5).roundToInt()))))
                hr(start, mins, 118, 162)
            }
        }
        if (i in walks) {
            val start = s.at(key, if (i == sunday15k) 10 else 13)
            val mins = if (i == sunday15k) 150 else s.between(35.0, 55.0).roundToInt()
            val end = start.plusSeconds(mins * 60L)
            if (s.past(end)) {
                rec[RecordMethod.EXERCISE_SESSION.name]!!.add(LocalRecord(s.iso(start), s.iso(end), WEAR, mapOf("title" to "Walk", "exerciseType" to 79)))
                hr(start, mins, 126, 140)
            }
        }
        if (isToday) {
            val samples = mutableListOf<Map<String, Any?>>()
            var m = 7 * 60
            while (m < 23 * 60) {
                val t = s.at(key, 0, m)
                if (!s.past(t)) break
                samples.add(mapOf("time" to s.iso(t), "beatsPerMinute" to s.between(64.0, 82.0).roundToInt()))
                m += 20
            }
            if (samples.isNotEmpty()) rec[RecordMethod.HEART_RATE.name]!!.add(
                LocalRecord(samples.first()["time"] as String, samples.last()["time"] as String, WEAR, mapOf("samples" to samples))
            )
        }

        // ---------- nutrition ----------
        if (i !in noFood) {
            val isHigh = i in high
            val kcal = if (isHigh) s.between(2700.0, 3600.0) else s.between(1900.0, 2400.0)
            val pRoll = r.next()
            val protein = if (pRoll < 0.2) s.between(90.0, 120.0) else if (pRoll > 0.85) s.between(170.0, 195.0) else s.between(130.0, 170.0)
            val fat = kcal * (if (isHigh) 0.36 else 0.3) / 9
            val carbs = maxOf(80.0, (kcal - protein * 4 - fat * 9) / 4)
            val meals: List<List<Any>> = if (isHigh) {
                listOf(
                    listOf("Bacon roll & coffee", "breakfast", 8, 40, 0.15),
                    listOf("Meal deal", "lunch", 12, 50, 0.22),
                    listOf("Crisps", "snack", 16, 20, 0.07),
                    listOf(if (dow[i] == 5) "Burger, fries & sides (restaurant)" else "Takeaway curry & naan", "dinner", 20, 15, 0.56),
                )
            } else {
                listOf(
                    listOf("Overnight oats & berries", "breakfast", 7, 45, 0.23),
                    listOf("Chicken & rice bowl", "lunch", 12, 35, 0.32),
                    listOf("Greek yoghurt & almonds", "snack", 15, 50, 0.1),
                    listOf("Salmon, potatoes & greens", "dinner", 19, 10, 0.35),
                )
            }
            val skipSnack = r.next() < 0.3
            for (m in meals) {
                val name = m[0] as String
                val mealType = m[1] as String
                val h = m[2] as Int
                val mi = m[3] as Int
                val frac = m[4] as Double
                if (mealType == "snack" && skipSnack) continue
                val start = s.at(key, h as Int, (mi as Int) + floor(r.next() * 20).toInt())
                if (!s.past(start)) continue
                val end = start.plusSeconds(15 * 60)
                rec[RecordMethod.NUTRITION.name]!!.add(
                    LocalRecord(s.iso(start), s.iso(end), FOOD, mapOf(
                        "name" to name, "mealType" to mealType,
                        "energy" to mapOf("inKilocalories" to (kcal * (frac as Double)).roundToInt()),
                        "protein" to mapOf("inGrams" to (protein * frac).roundToInt()),
                        "carbohydrates" to mapOf("inGrams" to (carbs * frac).roundToInt()),
                        "fat" to mapOf("inGrams" to (fat * frac).roundToInt()),
                    ))
                )
            }
        }
    }

    val drinks = drinksByDay.values.flatten().filter { Instant.parse(it.time).toEpochMilli() <= nowMs }
    return DemoMonth(rec, drinks, keys)
}

/** getDemoData: records + drinks, alcohol summed into per-day kcal/units/day maps. */
fun getDemoAlcohol(month: DemoMonth): Map<String, Triple<Double, Double, Boolean>> {
    val out = mutableMapOf<String, Triple<Double, Double, Boolean>>()
    for (d in month.drinks) {
        val cur = out[d.day] ?: Triple(0.0, 0.0, false)
        out[d.day] = Triple(
            ((cur.first + d.kcal) * 100).roundToInt() / 100.0,
            ((cur.second + d.units) * 100).roundToInt() / 100.0,
            true,
        )
    }
    return out // day -> (kcal, units, drank)
}
