package io.github.juliegaunt.chainone.data.category

import io.github.juliegaunt.chainone.data.db.Category

/**
 * Sorts patterns into categories by what their titles are mainly about. A category called "Hats"
 * takes "Ribbed Winter Hat" and "Baby Hats", and, through the built-in [ItemFamilies], "Cozy Cap"
 * and "Slouchy Beanie" too. Words are compared whole and in their base form, so "Chat Bag" isn't a
 * hat and "Scarves" is a scarf.
 *
 * Titles name their main item first and describe it before naming it: "Daisy Cardigan Bonus
 * Beanie" is a cardigan, "Bunny Ears Hat" a hat. So only the title's first part counts (up to
 * "with", "and", "bonus", "for", a dash, a colon and the like), and in it the last word a
 * category recognises wins. If that part names an item no category takes, the pattern goes to
 * "Other" rather than wherever a bonus item would.
 */
object CategoryMatcher {

    /**
     * The category a pattern belongs in: the one picked by hand ([chosenId]) if there is one, else
     * the one for the main item of [title] (see the class notes), else null, the library's "Other".
     * When two categories recognise the same word, one that names it itself beats one that only
     * knows it as a built-in word (so "Beanies" gets beanies even below "Hats"), and then the
     * higher category wins.
     */
    fun categoryFor(title: String, chosenId: Long?, categories: List<Category>): Category? {
        categories.firstOrNull { it.id == chosenId }?.let { return it }
        for (part in parts(title)) {
            val words = wordsOf(part)
            val last = lastWordIndex(words)
            // Set once the main item turns out to have no category: after that, only a describing
            // word a category names itself counts ("Baby Blanket" → Baby), not another item
            // ("Cap Sleeve Cardigan" isn't a hat).
            var mainItemUntaken = false
            // Right to left: the item comes after the words describing it.
            for (end in words.indices.reversed()) {
                categories.firstOrNull { category ->
                    terms(category).any { runEndsAt(words, it, end) && !(mainItemUntaken && isItem(it)) }
                }?.let { return it }
                if (!mainItemUntaken) {
                    categories.firstOrNull { category ->
                        familyTerms(category).any { runEndsAt(words, it, end) && (end == last || !isLastOnly(it)) }
                    }?.let { return it }
                    mainItemUntaken = ItemFamilies.all.any { family ->
                        family.terms.any { runEndsAt(words, it, end) && (end == last || it !in family.lastOnlyTerms) }
                    }
                }
            }
            // This part names an item, but no category takes it: the pattern is about that item.
            if (mainItemUntaken) return null
        }
        return null
    }

    /**
     * The title split where it moves on to something else: "Daisy Cardigan Bonus Beanie" →
     * [Daisy Cardigan, Beanie]. A part that names no item at all, such as "Free Pattern:", is
     * skipped over by [categoryFor].
     */
    fun parts(title: String): List<String> = title.split(TITLE_JOINS).filter { it.isNotBlank() }

    /**
     * [items] sorted into [categories] (in their order), then the ones in none under null, which
     * the library calls "Other". Empty groups are left out; items keep their order within a group.
     */
    fun <T> group(items: List<T>, categories: List<Category>, title: (T) -> String, chosenId: (T) -> Long?): List<Pair<Category?, List<T>>> {
        val byCategory = items.groupBy { categoryFor(title(it), chosenId(it), categories)?.id }
        return (categories.map { it to byCategory[it.id] } + (null to byCategory[null]))
            .mapNotNull { (category, members) -> members?.let { category to it } }
    }

    /** True if [term] names a kind of item (a word in [ItemFamilies]) rather than describing one. */
    private fun isItem(term: List<String>): Boolean = ItemFamilies.all.any { term in it.terms }

    /** True for a word such as "bunny" that only names the item as a title's last word. */
    private fun isLastOnly(term: List<String>): Boolean = ItemFamilies.all.any { term in it.lastOnlyTerms }

    /** The title part's last real word, past trailing "pattern", "crochet", "free" and numbers. */
    fun lastWordIndex(words: List<String>): Int =
        words.indexOfLast { word -> word !in FILLER && word.any { it.isLetter() } }

    /** True if [category] would take a pattern called [title], were it the only category. */
    fun matches(title: String, category: Category): Boolean = categoryFor(title, null, listOf(category)) != null

    /**
     * The category's own words as base forms: each item its name lists ("Hats & Scarves" →
     * [hat], [scarve]), and each extra word or phrase ("granny squares" → [granny, square]).
     */
    fun terms(category: Category): List<List<String>> =
        (nameItems(category.name) + splitExtraWords(category.extraWords)).map(::wordsOf).filter { it.isNotEmpty() }

    /** The items a name lists: "Hats & Scarves" → [Hats, Scarves]; "Granny Squares" → [Granny Squares]. */
    fun nameItems(name: String): List<String> = name.split(NAME_SEPARATOR).filter { it.isNotBlank() }

    /** The words the category takes from the built-in families its own words belong to. */
    fun familyTerms(category: Category): List<List<String>> = ItemFamilies.familiesOf(terms(category)).flatMap { it.terms }

    /** "beanie, Beret,  cloche" → [beanie, beret, cloche]: trimmed, lowercase, no blanks or repeats. */
    fun splitExtraWords(text: String): List<String> =
        text.split(',').map { it.trim().replace(SPACES, " ").lowercase() }.filter { it.isNotEmpty() }.distinct()

    /** The lowercase base forms of [text]'s words: "Top-down Baby Hats!" → [top, down, baby, hat]. */
    fun wordsOf(text: String): List<String> =
        splitJoinedWords(text).lowercase().split(NOT_A_WORD).filter { it.isNotEmpty() }.map(::baseForm)

    /** Words run together in file-like titles, apart: "OutoftheSnowBeanie" → "Outofthe Snow Beanie", "Edition12" → "Edition 12". */
    fun splitJoinedWords(text: String): String = text.replace(JOINED_WORDS, " ")

    /**
     * A word's base form, the same for its singular and plural: hats and hat → hat, bunnies →
     * bunny, pouches → pouch, gloves → glove, beanie and beanies → beany, scarf and scarves →
     * scarve. It isn't always a real word; it only has to match, and both sides go through it.
     */
    fun baseForm(word: String): String {
        if (word.length <= 3) return word
        val single = when {
            word.endsWith("ies") && word.length > 4 -> word.dropLast(3) + "y"
            SIBILANT_ES.any { word.endsWith(it) } -> word.dropLast(2)
            word.endsWith("ss") || word.endsWith("us") || word.endsWith("is") -> word
            word.endsWith("s") -> word.dropLast(1)
            else -> word
        }
        return when {
            // scarf / scarves, knife / knives, leaf / leaves.
            single.endsWith("fe") -> single.dropLast(2) + "ve"
            single.endsWith("f") -> single.dropLast(1) + "ve"
            // beanie / beanies, bootie / booties: "-ies" can't tell these from bunny / bunnies.
            single.endsWith("ie") && single.length > 3 -> single.dropLast(2) + "y"
            else -> single
        }
    }

    /** True if [run] appears in [words], in order and next to each other. */
    fun containsRun(words: List<String>, run: List<String>): Boolean =
        run.isNotEmpty() && words.windowed(run.size).any { it == run }

    /** True if [run] appears in [words] ending at word [end]. */
    fun runEndsAt(words: List<String>, run: List<String>, end: Int): Boolean {
        val start = end - run.size + 1
        return run.isNotEmpty() && start >= 0 && words.subList(start, end + 1) == run
    }

    private val NOT_A_WORD = Regex("""[^\p{L}\p{N}]+""")

    /** Where a title moves on from its main item: "with", "and", "bonus", "for", "in", "&", a comma, a spaced dash, a colon, brackets. */
    private val TITLE_JOINS =
        Regex("""\s+(?:and|with|plus|bonus|for|or|in)\s+|\s*[&+,;:|()\[\]]\s*|\s+[-–—]+\s+""", RegexOption.IGNORE_CASE)

    /** A lowercase letter before a capital, or a letter next to a digit: where joined words meet. */
    private val JOINED_WORDS = Regex("""(?<=\p{Ll})(?=\p{Lu})|(?<=\p{L})(?=\p{N})|(?<=\p{N})(?=\p{L})""")

    /** Words that trail a title without naming anything: "Teddy Bear Crochet Pattern" is about the bear. */
    private val FILLER = setOf("pattern", "crochet", "crocheted", "free", "tutorial", "pdf", "diy", "set", "kit", "version", "edition", "rev")

    /** "Hats & Scarves", "Gloves and Mittens", "Socks, Slippers": names that list several items. */
    private val NAME_SEPARATOR = Regex("""\s*(?:&|,|/|\+|\band\b)\s*""", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("""\s+""")
    private val SIBILANT_ES = listOf("sses", "xes", "zes", "ches", "shes")
}
