package io.github.juliegaunt.chainone.data.category

import io.github.juliegaunt.chainone.data.db.Category

/** A category Chain One could add, and how many titles in "Other" it would take. */
data class CategorySuggestion(val name: String, val patternCount: Int)

/**
 * Finds what several uncategorized titles have in common: a kind of item from [ItemFamilies]
 * ("Hats" for "Winter Hat", "Slouchy Beanie" and "Cozy Cap"), or else a word they share.
 */
object CategorySuggestions {

    /**
     * Up to [limit] suggestions that would each take at least two of [titles] (the patterns in
     * "Other"), most patterns first. Kinds of item and words that [categories] already cover are
     * left out, and so are words that say nothing about the item ("crochet", "pattern", "easy").
     */
    fun from(titles: List<String>, categories: List<Category>, limit: Int = 8): List<CategorySuggestion> {
        val coveredFamilies = categories.flatMap { ItemFamilies.familiesOf(CategoryMatcher.terms(it)) }.toSet()
        val covered = categories.flatMap { CategoryMatcher.terms(it) + CategoryMatcher.familyTerms(it) }
            .filter { it.size == 1 }.map { it.single() }.toSet()
        val familyWords = ItemFamilies.all.flatMap { it.terms }.filter { it.size == 1 }.map { it.single() }.toSet()
        val familyCounts = mutableMapOf<ItemFamilies.Family, Int>()
        val wordCounts = mutableMapOf<String, Int>()
        val spellings = mutableMapOf<String, MutableMap<String, Int>>()
        for (title in titles) {
            // The kind of item the title is mainly about ("Cardigan Bonus Beanie" is a sweater).
            mainFamily(title)?.takeIf { it !in coveredFamilies }?.let { familyCounts.merge(it, 1, Int::plus) }
            val seen = mutableSetOf<String>()
            for (word in CategoryMatcher.splitJoinedWords(title).split(NOT_A_WORD)) {
                // Skips short words, numbers and runs of one letter ("AAA" used to sort titles).
                if (word.length < MIN_LENGTH || word.any { it.isDigit() } || word.lowercase().toSet().size == 1) continue
                val base = CategoryMatcher.baseForm(word.lowercase())
                if (base in IGNORED || base in covered || base in familyWords || !seen.add(base)) continue
                wordCounts.merge(base, 1, Int::plus)
                spellings.getOrPut(base) { mutableMapOf() }.merge(word.lowercase().replaceFirstChar { it.titlecase() }, 1, Int::plus)
            }
        }
        val families = familyCounts.map { (family, count) -> CategorySuggestion(family.name, count) }
            .sortedWith(compareByDescending<CategorySuggestion> { it.patternCount }.thenBy { it.name })
        val words = wordCounts.map { (base, count) -> CategorySuggestion(spellings.getValue(base).maxBy { it.value }.key, count) }
            .sortedWith(compareByDescending<CategorySuggestion> { it.patternCount }.thenBy { it.name })
        // Kinds of item first: they make the best categories. Then shared words, most common first.
        return (families + words).filter { it.patternCount >= MIN_PATTERNS }.take(limit)
    }

    /** The family of a title's main item: the last family word in the first part that has one. */
    private fun mainFamily(title: String): ItemFamilies.Family? {
        for (part in CategoryMatcher.parts(title)) {
            val words = CategoryMatcher.wordsOf(part)
            val last = CategoryMatcher.lastWordIndex(words)
            val found = words.indices.reversed().firstNotNullOfOrNull { end ->
                ItemFamilies.all.firstOrNull { family ->
                    family.terms.any { term ->
                        CategoryMatcher.runEndsAt(words, term, end) && (end == last || term !in family.lastOnlyTerms)
                    }
                }
            }
            if (found != null) return found
        }
        return null
    }

    private const val MIN_LENGTH = 3
    private const val MIN_PATTERNS = 2
    private val NOT_A_WORD = Regex("""[^\p{L}\p{N}]+""")

    /** Words in pattern titles that don't name the item being made. */
    private val IGNORED = listOf(
        "the", "and", "with", "for", "from", "your", "how", "make", "made", "diy", "tutorial", "video",
        "crochet", "crocheted", "pattern", "free", "easy", "simple", "beginner", "beginners", "quick", "cute",
        "cozy", "cosy", "chunky", "mini", "little", "big", "small", "new", "best", "perfect", "ultimate",
        "modern", "vintage", "classic", "inspired", "style", "version", "size", "design", "project",
        "stitch", "yarn", "hook", "knit", "one", "two", "three",
    ).map(CategoryMatcher::baseForm).toSet()
}
