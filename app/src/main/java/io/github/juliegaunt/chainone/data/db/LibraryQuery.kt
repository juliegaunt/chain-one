package io.github.juliegaunt.chainone.data.db

import androidx.sqlite.db.SimpleSQLiteQuery

/** What the library shows. Every filter that's set must match. */
data class LibraryFilter(
    /** Each word must appear somewhere in the pattern (title, designer, tags, notes, …). */
    val search: String = "",
    /** Patterns with any of these statuses; empty means all. */
    val statuses: Set<PatternStatus> = emptySet(),
    /** Patterns that have every one of these tags. */
    val tagIds: Set<Long> = emptySet(),
    val favoritesOnly: Boolean = false,
) {
    val isActive: Boolean
        get() = search.isNotBlank() || statuses.isNotEmpty() || tagIds.isNotEmpty() || favoritesOnly
}

/**
 * The parts of a pattern a library card shows. The library loads just these, not every pattern's
 * full instructions, materials and notes.
 */
data class PatternSummary(
    val id: Long,
    val title: String,
    val designer: String,
    val status: PatternStatus,
    val isFavorite: Boolean,
    val coverImagePath: String?,
)

/** A tag and how many patterns carry it. */
data class TagCount(val id: Long, val name: String, val patternCount: Int)

/**
 * Builds the library's SQL from a [LibraryFilter]. Search uses `LIKE '%word%'`, so parts of
 * words match ("gran" finds "granny"); SQLite's LIKE ignores case for English letters.
 */
internal object LibraryQuery {

    private const val SUMMARY_COLUMNS = "p.id, p.title, p.designer, p.status, p.isFavorite, p.coverImagePath"
    private val SPACES = Regex("""\s+""")

    private val SEARCHED_COLUMNS = listOf("title", "designer", "hookSize", "yarnWeight", "materials", "instructions", "notes")

    fun build(filter: LibraryFilter): SimpleSQLiteQuery {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<Any>()

        if (filter.favoritesOnly) conditions += "p.isFavorite = 1"
        if (filter.statuses.isNotEmpty()) {
            conditions += "p.status IN (${placeholders(filter.statuses.size)})"
            args.addAll(filter.statuses.map { it.name })
        }
        if (filter.tagIds.isNotEmpty()) {
            // Counting the matching tags demands all of them, not just any.
            conditions += "(SELECT COUNT(*) FROM pattern_tags pt WHERE pt.patternId = p.id " +
                "AND pt.tagId IN (${placeholders(filter.tagIds.size)})) = ${filter.tagIds.size}"
            args.addAll(filter.tagIds)
        }
        for (word in words(filter.search)) {
            val pattern = "%${escapeLike(word)}%"
            val matches = SEARCHED_COLUMNS.map { "p.$it LIKE ? ESCAPE '\\'" } + (
                "EXISTS (SELECT 1 FROM pattern_tags pt JOIN tags t ON t.id = pt.tagId " +
                    "WHERE pt.patternId = p.id AND t.name LIKE ? ESCAPE '\\')"
                )
            conditions += matches.joinToString(" OR ", prefix = "(", postfix = ")")
            repeat(matches.size) { args += pattern }
        }

        val where = if (conditions.isEmpty()) "" else " WHERE " + conditions.joinToString(" AND ")
        return SimpleSQLiteQuery("SELECT $SUMMARY_COLUMNS FROM patterns p$where ORDER BY p.updatedAt DESC, p.id DESC", args.toTypedArray())
    }

    fun words(search: String): List<String> = search.trim().split(SPACES).filter { it.isNotEmpty() }

    /** So a search for "100%" or "sc_2" looks for those characters, not LIKE's wildcards. */
    fun escapeLike(text: String): String = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun placeholders(count: Int) = List(count) { "?" }.joinToString(", ")
}
