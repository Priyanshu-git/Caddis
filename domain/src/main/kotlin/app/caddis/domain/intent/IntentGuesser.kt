package app.caddis.domain.intent

import app.caddis.domain.model.ContentKind
import app.caddis.domain.model.Intents
import app.caddis.domain.model.Platform

/** What the guesser knows about a save. Details may still be loading, so text can be empty. */
data class GuessInput(
    val platform: Platform,
    val kind: ContentKind,
    val url: String = "",
    val title: String = "",
    val description: String = "",
)

/**
 * How often the person corrected a guess for a platform + kind, e.g. Instagram Reel → Cook: 3. Ordinary sites share one bucket.
 * Kept on the phone; never sent anywhere.
 */
data class CorrectionStats(val counts: Map<String, Map<String, Int>> = emptyMap()) {
    fun forKey(key: String): Map<String, Int> = counts[key].orEmpty()

    companion object {
        fun key(platform: Platform, kind: ContentKind) = "${platform.learningKey}:${kind.name}"
    }
}

/**
 * INT-02: guess the intent with plain rules (tech arch decision 5 — no language model).
 *
 * Order: keyword rules over URL + title + description, then the person's own corrections for this
 * platform and kind (once they have corrected it at least [MIN_CORRECTIONS] times), then the
 * platform/kind default.
 */
class IntentGuesser(private val validIntentIds: Set<String> = Intents.defaults.map { it.id }.toSet()) {

    private val keywordRules: List<Pair<String, Regex>> = listOf(
        Intents.COOK to words(
            "recipe", "recipes", "cook", "cooking", "bake", "baking", "baked", "dinner", "lunch", "breakfast",
            "brunch", "dessert", "cookies?", "cake", "bread", "sourdough", "pasta", "orzo", "curry", "dal", "soup",
            "salad", "noodles?", "chili", "meal prep", "one-pan", "one pot", "air fryer", "potato(es)?", "sauce",
        ),
        Intents.BUY to words(
            "buy", "shop", "shopping", "deal", "deals", "sale", "price", "under \\d+k?", "best .* to buy",
            "review", "unboxing", "discount", "gift", "gifts", "amazon", "flipkart", "etsy", "product",
            "headphones", "notebook", "cart",
        ),
        Intents.GO to words(
            "travel", "trip", "trips", "itinerary", "day trips?", "restaurant", "restaurants", "cafe", "cafes",
            "hotel", "hotels", "visit", "places to", "things to do", "walking tours?", "beach", "hike", "hiking",
            "maps", "guide to .* city",
        ),
        Intents.LEARN to words(
            "tutorial", "learn", "learning", "course", "lesson", "lecture", "how to", "explained", "intro to",
            "introduction to", "beginners?", "crash course", "masterclass", "talk", "fundamentals", "ownership",
            "sql", "python", "kotlin", "rust", "javascript",
        ),
        Intents.TRY to words(
            "diy", "hack", "hacks", "routine", "workout", "exercise", "stretch", "setup", "ideas", "tips",
            "try this", "challenge", "habit", "habits", "shelves", "project",
        ),
    )

    fun guess(input: GuessInput, corrections: CorrectionStats = CorrectionStats()): String {
        val text = listOf(input.url, input.title, input.description).joinToString(" ").lowercase()
        keywordRules.firstOrNull { (_, rx) -> rx.containsMatchIn(text) }?.let { (intent, _) ->
            if (intent in validIntentIds) return intent
        }
        val learned = corrections.forKey(CorrectionStats.key(input.platform, input.kind))
            .filterKeys { it in validIntentIds }
            .maxByOrNull { it.value }
        if (learned != null && learned.value >= MIN_CORRECTIONS) return learned.key
        return defaultFor(input.platform, input.kind)
    }

    private fun defaultFor(platform: Platform, kind: ContentKind): String = when {
        platform.isNote -> Intents.KEEP
        kind.isVideo -> Intents.WATCH
        kind == ContentKind.CHANNEL || kind == ContentKind.PROFILE || kind == ContentKind.SUBREDDIT ||
            kind == ContentKind.FILE || kind == ContentKind.FOLDER -> Intents.KEEP
        kind == ContentKind.PDF -> Intents.READ
        else -> Intents.READ
    }

    private fun words(vararg w: String) = Regex("\\b(" + w.joinToString("|") + ")\\b")

    companion object {
        const val MIN_CORRECTIONS = 2
    }
}
