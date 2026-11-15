package com.scatterbrain.scatterfit.data

/**
 * Relevance ranking for food search.
 *
 * Two problems it exists to solve:
 *
 * 1. Our own re-ranking used to score two matched tokens (1.5) above an exact substring
 *    (1.2), so a product literally named "Big Mac" was pushed below "big daddy mac mix".
 *    Open Food Facts had it right; we un-ranked it.
 * 2. Restaurant items are largely absent from Open Food Facts (no barcode), so chain
 *    queries returned supermarket crisps that merely mention the chain. The bundled
 *    restaurant library is searched first.
 *
 * The scoring below was validated against 15 real queries before being ported from the
 * prototype, so the constants are not arbitrary. Pure functions: unit-testable without
 * Android.
 */
object FoodRanking {

    private val STOP = setOf("and", "the", "a", "of", "with", "in")

    /**
     * Brand aliases whose colloquial name differs from the catalog name.
     * Deliberately conservative: single generic words ("burger", "chicken") are absent,
     * because they pulled unrelated chains into ordinary food searches.
     */
    private val BRAND_ALIASES: Map<String, String> = linkedMapOf(
        "mcdonalds" to "McDonald's",
        "maccies" to "McDonald's",
        "mcd" to "McDonald's",
        "kfc" to "KFC",
        "greggs" to "Greggs",
        "nandos" to "Nando's",
        "wagamama" to "Wagamama",
        "costa" to "Costa",
        "starbucks" to "Starbucks",
        "subway" to "Subway",
        "dominos" to "Domino's",
        "pizzahut" to "Pizza Hut",
        "papajohns" to "Papa John's",
        "burgerking" to "Burger King",
        "fiveguys" to "Five Guys",
        "timhortons" to "Tim Hortons",
        "caffenero" to "Caffe Nero",
        "nero" to "Caffe Nero",
        "pret" to "Pret",
        "itsu" to "Itsu",
        "wasabi" to "Wasabi",
        "leon" to "Leon",
        "toby" to "Toby Carvery",
        "dishoom" to "Dishoom",
        "pizzaexpress" to "PizzaExpress",
    )

    fun normalise(s: String): String = s.trim().lowercase().replace(Regex("\\s+"), " ")

    fun tokens(s: String): List<String> =
        normalise(s).split(Regex("[^a-z0-9']+")).filter { it.isNotEmpty() }

    private fun contentTokens(s: String): List<String> = tokens(s).filter { it !in STOP }

    /** Splits a query into (brand, remainder). Brand may appear anywhere in the query. */
    fun detectBrand(query: String): Pair<String?, String> {
        val q = normalise(query)
        for ((alias, canonical) in BRAND_ALIASES) {
            if (q.contains(alias)) {
                val rest = q.replace(alias, " ").replace(Regex("\\s+"), " ").trim()
                return canonical to rest
            }
        }
        return null to q
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        val m = a.length
        val n = b.length
        if (m == 0 || n == 0) return maxOf(m, n)
        var prev = IntArray(n + 1) { it }
        var cur = IntArray(n + 1)
        for (i in 1..m) {
            cur[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) prev[j - 1] else minOf(prev[j - 1], prev[j], cur[j - 1]) + 1
                cur[j] = cost
            }
            val swap = prev; prev = cur; cur = swap
        }
        return prev[n]
    }

    /**
     * Similarity of a query to a candidate name, 0..1.2.
     * An exact or prefix phrase scores above 1.0 so it can beat any partial match.
     */
    fun fuzzy(query: String, candidate: String?): Double {
        val qc = normalise(query)
        val tc = (candidate ?: "").trim().lowercase()
        if (qc.isEmpty() || tc.isEmpty()) return 0.0
        val fq = qc.split(" ").filter { it.isNotEmpty() && it !in STOP }
        if (fq.isEmpty()) return 0.0
        if (tc.contains(qc)) return if (tc.startsWith(qc)) 1.2 else 1.0
        val tt = tokens(tc)
        if (tt.isEmpty()) return 0.0

        var matched = 0
        var total = 0.0
        for (qt in fq) {
            var best = 0.0
            for (tk in tt) {
                if (tk == qt) { best = maxOf(best, 1.0); continue }
                if (qt.length >= 2 && tk.contains(qt)) { best = maxOf(best, 0.85); continue }
                val maxDist = if (qt.length <= 3) 1 else if (qt.length <= 6) 2 else 3
                if (Math.abs(qt.length - tk.length) <= maxDist) {
                    val d = levenshtein(qt, tk)
                    if (d <= maxDist) {
                        val sim = 1.0 - d.toDouble() / maxOf(qt.length, tk.length)
                        if (sim >= 0.65) best = maxOf(best, sim)
                    }
                }
            }
            if (best >= 0.65) { matched++; total += best }
        }
        if (matched == 0) return 0.0
        return (total / fq.size) * (matched.toDouble() / fq.size) + 0.5 * total / matched
    }

    /** 1.0 when the whole query appears verbatim in the name. */
    fun phraseHit(query: String, candidate: String?): Double {
        val q = normalise(query)
        val n = (candidate ?: "").lowercase()
        return if (q.isNotEmpty() && n.contains(q)) 1.0 else 0.0
    }

    /**
     * True when every query token appears in the candidate's tokens. Tokens shorter than
     * three characters are ignored on both sides: without that guard, "steak bake" matched
     * a Nando's "Choc-A-Lot Cake" because "a" is a substring of "steak".
     */
    private fun covers(query: String, candidateName: String): Boolean {
        val qt = contentTokens(query)
        if (qt.isEmpty()) return false
        val nt = tokens(candidateName)
        return qt.all { t ->
            nt.any { n -> t == n || (t.length >= 3 && n.length >= 3 && (t.contains(n) || n.contains(t))) }
        }
    }

    /**
     * Rank Open Food Facts results. Keeps OFF's own order as a 15% prior, so a query OFF
     * ranked well is not wrecked by our heuristics.
     */
    fun rankOff(query: String, products: List<ProductResult>, limit: Int = 8): List<ProductResult> {
        if (products.size <= 1) return products
        val (brand, rest) = detectBrand(query)
        val n = products.size.toDouble()
        val scored = mutableListOf<Pair<ProductResult, Double>>()
        products.forEachIndexed { i, p ->
            val nm = p.productName ?: return@forEachIndexed
            val rel = minOf(fuzzy(query, nm), 1.0)
            val ph = phraseHit(query, nm)
            var s = 0.45 * rel + 0.40 * ph + 0.15 * (1.0 - i / n)
            if (brand != null) {
                // A brand query whose remaining terms do not match is noise, not a result.
                if (rest.isNotBlank() && fuzzy(rest, nm) < 0.6 && phraseHit(rest, nm) == 0.0) {
                    return@forEachIndexed
                }
                val br = p.brands.orEmpty()
                if (normalise(br).contains(normalise(brand))) s += 0.6
                else if (normalise(nm).contains(normalise(brand))) s -= 0.25
            }
            if (s >= 0.5) scored.add(p to s)
        }
        scored.sortByDescending { it.second }
        val out = scored.map { it.first }
        // If our scoring rejected everything, fall back to OFF's order rather than
        // showing the user an empty screen.
        return (if (out.isEmpty()) products else out).take(limit)
    }

    /**
     * Rank the bundled restaurant library. A brand query searches that brand's menu;
     * otherwise a cross-chain search requires the name to cover every query token, so
     * "porridge oats" cannot return a Filet-O-Fish.
     */
    fun rankRestaurants(
        query: String,
        library: List<RestaurantFoodItem>,
        limit: Int = 5,
    ): List<RestaurantFoodItem> {
        if (query.isBlank()) return emptyList()
        val (brand, rest) = detectBrand(query)
        if (brand != null) {
            val candidates = library.filter { it.brand == brand }
            if (candidates.isEmpty()) return emptyList()
            if (rest.isBlank()) return candidates.sortedBy { it.name }.take(limit)
            val scored = mutableListOf<Pair<RestaurantFoodItem, Double>>()
            for (item in candidates) {
                val rel = fuzzy(rest, item.name)
                val ph = phraseHit(rest, item.name)
                if (rel < 0.6 && ph == 0.0) continue
                var s = maxOf(rel, ph * 1.6)
                if (normalise(rest) == normalise(item.name)) s += 0.8
                s -= minOf(item.name.length, 60) * 0.004
                scored.add(item to s)
            }
            scored.sortByDescending { it.second }
            val out = scored.map { it.first }
            return (if (out.isEmpty()) candidates.sortedBy { it.name } else out).take(limit)
        }
        val scored = mutableListOf<Pair<RestaurantFoodItem, Double>>()
        for (item in library) {
            val ph = phraseHit(query, item.name)
            if (ph == 0.0 && !covers(query, item.name)) continue
            var s = maxOf(fuzzy(query, item.name), ph * 1.6)
            s -= minOf(item.name.length, 60) * 0.004
            scored.add(item to s)
        }
        scored.sortByDescending { it.second }
        return scored.map { it.first }.take(limit)
    }
}
