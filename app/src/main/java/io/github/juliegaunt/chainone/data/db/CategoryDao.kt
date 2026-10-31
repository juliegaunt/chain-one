package io.github.juliegaunt.chainone.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** The library's categories, in the order the person arranged them. */
@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY position, id")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY position, id")
    suspend fun getAll(): List<Category>

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Category?

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM categories")
    suspend fun nextPosition(): Int

    @Insert
    suspend fun insert(category: Category): Long

    @Query("UPDATE categories SET name = :name, extraWords = :extraWords WHERE id = :id")
    suspend fun update(id: Long, name: String, extraWords: String)

    @Query("UPDATE categories SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    /** Patterns picked into it by hand go back to sorting by title (the foreign key sets them to null). */
    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Transaction
    suspend fun reorder(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { index, id -> setPosition(id, index) }
    }
}
