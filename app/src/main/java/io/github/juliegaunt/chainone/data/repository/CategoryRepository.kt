package io.github.juliegaunt.chainone.data.repository

import io.github.juliegaunt.chainone.data.category.CategoryMatcher
import io.github.juliegaunt.chainone.data.db.Category
import io.github.juliegaunt.chainone.data.db.CategoryDao
import kotlinx.coroutines.flow.Flow

/** The library's categories: their names, extra words and order. */
class CategoryRepository(private val dao: CategoryDao) {

    fun observeCategories(): Flow<List<Category>> = dao.observeAll()

    /** Adds a category at the end. Returns false (and changes nothing) if the name is blank or taken. */
    suspend fun add(name: String, extraWords: String): Boolean {
        val clean = cleanName(name)
        if (clean.isEmpty() || dao.findByName(clean) != null) return false
        dao.insert(Category(name = clean, extraWords = cleanExtraWords(extraWords), position = dao.nextPosition()))
        return true
    }

    /** Renames a category or changes its extra words. Returns false if the name is blank or another category has it. */
    suspend fun update(id: Long, name: String, extraWords: String): Boolean {
        val clean = cleanName(name)
        val existing = dao.findByName(clean)
        if (clean.isEmpty() || (existing != null && existing.id != id)) return false
        dao.update(id, clean, cleanExtraWords(extraWords))
        return true
    }

    /** Patterns are never deleted with it: they go wherever else their titles fit, or to Other. */
    suspend fun delete(id: Long) = dao.delete(id)

    /** Moves a category one place up (-1) or down (+1), which moves its library section too. */
    suspend fun move(id: Long, by: Int) {
        val ids = dao.getAll().map { it.id }.toMutableList()
        val from = ids.indexOf(id)
        if (from < 0) return
        val to = (from + by).coerceIn(0, ids.lastIndex)
        if (from == to) return
        ids.add(to, ids.removeAt(from))
        dao.reorder(ids)
    }

    companion object {
        private const val MAX_NAME_LENGTH = 40
        private val SPACES = Regex("""\s+""")

        fun cleanName(name: String): String = name.trim().replace(SPACES, " ").take(MAX_NAME_LENGTH).trim()

        /** "Beanie,beret ,, cloche" → "beanie, beret, cloche". */
        fun cleanExtraWords(text: String): String = CategoryMatcher.splitExtraWords(text).joinToString(", ")
    }
}
