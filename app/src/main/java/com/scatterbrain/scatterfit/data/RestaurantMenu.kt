package com.scatterbrain.scatterfit.data

import android.content.Context
import com.scatterbrain.scatterfit.R
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One item off a chain's published menu, per PORTION (not per 100g).
 *
 * Chain menus are the opposite problem to Open Food Facts: OFF is a barcode
 * database, so it holds packaged groceries and nothing from a counter. A Costa
 * tuna melt has no barcode to scan, so it can never appear in OFF no matter how
 * good the ranking gets. These items ship in the APK instead, which also means
 * they work with no signal in a basement café.
 *
 * Macros are nullable because not every chain publishes all four. McDonald's UK
 * gives calories and protein but no fat or carbs, and a made-up 0 would quietly
 * corrupt the day's totals - so we store what's published and show what's there.
 */
@Serializable
data class RestaurantMenuItem(
    val id: String,
    val brand: String,
    val name: String,
    val serving: String = "1 portion",
    val kcal: Double,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val source: String? = null,
) {
    /** What the logger shows as the meal name: "Costa — Tuna Melt Panini". */
    val displayName: String get() = "$brand — $name"

    /** Everything a search query could plausibly contain, as one string. */
    val searchText: String get() = "$brand $name $serving"
}

@Serializable
data class RestaurantMenuFile(
    val version: Int = 1,
    val note: String? = null,
    val items: List<RestaurantMenuItem> = emptyList(),
)

object RestaurantMenu {

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): List<RestaurantMenuItem> = try {
        json.decodeFromString<RestaurantMenuFile>(raw).items
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * Read the bundled menus, or empty if they're missing - a broken library must
     * never take the food screen down with it.
     *
     * Same three-way resolution as the generic-foods library: the APK resource when
     * there's a Context, then the file on disk, then the JVM classpath. That last
     * path is what lets unit tests read the REAL shipped file instead of a copy in
     * test/resources that quietly drifts out of date.
     */
    fun load(context: Context? = null, filename: String = "restaurant_foods.json"): List<RestaurantMenuItem> {
        val fromApk: String? = if (context != null) {
            try {
                context.resources.openRawResource(R.raw.restaurant_foods)
                    .bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                null
            }
        } else null

        val raw = fromApk ?: run {
            val candidates = listOf(
                java.io.File("src/main/res/raw/$filename"),
                java.io.File("app/src/main/res/raw/$filename"),
            )
            candidates.firstOrNull { it.exists() }?.let { f ->
                try { f.readText() } catch (_: Exception) { null }
            } ?: try {
                RestaurantMenu::class.java.classLoader
                    ?.getResourceAsStream("raw/$filename")
                    ?.bufferedReader()?.use { it.readText() }
            } catch (_: Exception) {
                null
            }
        }

        return if (raw != null) parse(raw) else emptyList()
    }

    /**
     * Brand words that mean "only this chain". A query naming a chain must never
     * return another chain's food: without this, "m&s prawn mayo" returned a Costa
     * Prawn Mayo Sandwich, because "prawn mayo" is two thirds of the words and the
     * scorer has no idea who made it. Logging the wrong brand's lunch silently is
     * worse than returning nothing.
     */
    private val BRAND_ALIASES: Map<String, String> = mapOf(
        "costa" to "Costa",
        "greggs" to "Greggs",
        "kfc" to "KFC",
        "mcdonalds" to "McDonald's",
        "mcdonald" to "McDonald's",
        "maccies" to "McDonald's",
        "pret" to "Pret",
        "nandos" to "Nando's",
        "nando" to "Nando's",
        "m&s" to "M&S Food",
        "marks" to "M&S Food",
        "spoons" to "Wetherspoon",
    )

    /**
     * Every chain this build can name. The JSON is checked against this in tests, so
     * a brand spelled "Costa Coffee" in the data fails the build instead of quietly
     * never matching the "costa" alias.
     */
    val supportedBrands: Set<String> = BRAND_ALIASES.values.toSet()

    private fun words(text: String): List<String> =
        normaliseQuery(text).lowercase()
            .split(Regex("[^a-z0-9&']+"))
            .filter { it.length >= 2 }

    /**
     * Does a query word refer to this word? Exact, substring, or a transposition.
     *
     * The transposition case is what makes "staek" find "steak": Levenshtein calls
     * that distance 2 on a five-letter word, which is below the 0.65 similarity bar,
     * so typos of exactly the kind thumbs make would score zero. Same letters, same
     * length, different order is a typo, not a different food.
     */
    private fun sameWord(queryWord: String, targetWord: String): Boolean {
        if (queryWord == targetWord) return true
        // Only words long enough for containment to mean something. Without the
        // length floor, "egg" matches inside "greggs" and a bare "greggs" search
        // ranks the Egg Mayonnaise Sandwich top, which is nonsense.
        if (queryWord.length >= 4 && targetWord.contains(queryWord)) return true
        if (queryWord.length == targetWord.length && queryWord.length >= 4) {
            return queryWord.toCharArray().sorted() == targetWord.toCharArray().sorted()
        }
        return false
    }

    private fun coverage(needles: List<String>, hay: List<String>): Double {
        if (needles.isEmpty()) return 0.0
        return needles.count { n -> hay.any { sameWord(n, it) } }.toDouble() / needles.size
    }

    /** Brands the query names, as canonical names. Empty when it names none. */
    fun brandsInQuery(query: String): Set<String> =
        words(query).mapNotNull { BRAND_ALIASES[it] }.toSet()

    /**
     * Score one item against a query, best-first by this number.
     *
     * Four parts, because each fixes a ranking bug the others can't:
     *
     *  - QUERY COVERAGE: how much of what was typed the item accounts for.
     *  - NAME COVERAGE: how much of the ITEM'S OWN NAME appears in the query. This
     *    is what stops "Costa Chai Latte" beating "Costa Latte" - the short exact
     *    name is fully described, the longer one is half described.
     *  - BRAND: a query that names a chain should favour that chain's own food.
     *  - FUZZY: the shared typo-tolerant score, as a tiebreaker only.
     */
    fun score(query: String, item: RestaurantMenuItem): Double {
        val queryWords = words(query)
        if (queryWords.isEmpty()) return 0.0
        val nameWords = words(item.name)
        val itemWords = words("${item.brand} ${item.name} ${item.serving}")
        val queryCoverage = coverage(queryWords.filter { it !in BRAND_ALIASES }, itemWords)
        val nameCoverage = coverage(nameWords, queryWords)
        val brandBonus = if (item.brand in brandsInQuery(query)) 1.5 else 0.0
        return queryCoverage * 2.0 + nameCoverage * 2.0 + brandBonus + fuzzyScore(query, item.searchText)
    }

    /**
     * Local menu hits for [query], best first.
     *
     * A result needs half the query's meaning or a real typo score. When the query
     * names a brand, only that brand is eligible - see BRAND_ALIASES.
     */
    fun search(
        items: List<RestaurantMenuItem>,
        query: String,
        limit: Int = 6,
    ): List<RestaurantMenuItem> {
        val queryWords = words(query)
        if (queryWords.isEmpty()) return emptyList()
        val namedBrands = brandsInQuery(query)
        val pool = if (namedBrands.isEmpty()) items else items.filter { it.brand in namedBrands }
        if (pool.isEmpty()) return emptyList()

        // "greggs" on its own is a complete question - it means "show me Greggs" -
        // so with no content words left, brand membership alone qualifies.
        val contentWords = queryWords.filter { it !in BRAND_ALIASES }

        val scored = pool
            .map { it to score(query, it) }
            .filter { (item, s) ->
                if (contentWords.isEmpty()) return@filter true
                val itemWords = words("${item.brand} ${item.name} ${item.serving}")
                coverage(contentWords, itemWords) >= 0.5 || s >= 3.0
            }
            .sortedWith(compareByDescending<Pair<RestaurantMenuItem, Double>> { it.second }.thenBy { it.first.name.length })

        // A multi-word query that someone's menu answers exactly belongs to that
        // chain: "big mac" should not offer Greggs' Mac & Cheese alongside it. One
        // word ("latte") stays open on purpose - every chain sells one, and the
        // user's location is the answer, not the scorer's opinion.
        val exact = scored.firstOrNull()?.first?.takeIf { top ->
            contentWords.size >= 2 &&
                coverage(words(top.name), contentWords) >= 1.0 &&
                coverage(contentWords, words("${top.brand} ${top.name} ${top.serving}")) >= 1.0
        }
        val narrowed = if (exact != null) scored.filter { it.first.brand == exact.brand } else scored

        return narrowed.take(limit).map { it.first }
    }
}
