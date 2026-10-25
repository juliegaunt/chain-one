package io.github.juliegaunt.chainone.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PatternDao {

    @Query(
        """
        SELECT * FROM patterns
        WHERE (:favoritesOnly = 0 OR isFavorite = 1)
        ORDER BY updatedAt DESC, id DESC
        """,
    )
    fun observeLibrary(favoritesOnly: Boolean): Flow<List<Pattern>>

    @Query("SELECT * FROM patterns WHERE id = :id")
    fun observePattern(id: Long): Flow<Pattern?>

    @Query("SELECT * FROM patterns WHERE id = :id")
    suspend fun getPattern(id: Long): Pattern?

    @Insert
    suspend fun insert(pattern: Pattern): Long

    @Update
    suspend fun update(pattern: Pattern)

    @Query("UPDATE patterns SET isFavorite = :favorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, updatedAt: Long)

    @Query("UPDATE patterns SET coverImagePath = :path, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setCoverImagePath(id: Long, path: String?, updatedAt: Long)

    @Query("UPDATE patterns SET snapshotPath = :path, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setSnapshotPath(id: Long, path: String?, updatedAt: Long)

    /** The first pattern saved from any of [urls] (spellings of the same link). */
    @Query("SELECT * FROM patterns WHERE sourceUrl IN (:urls) ORDER BY createdAt LIMIT 1")
    suspend fun findBySourceUrl(urls: List<String>): Pattern?

    @Query("UPDATE patterns SET lastOpenedAt = :openedAt WHERE id = :id")
    suspend fun setLastOpened(id: Long, openedAt: Long)

    @Query("DELETE FROM patterns WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM pattern_images WHERE patternId = :patternId ORDER BY position")
    fun observeImages(patternId: Long): Flow<List<PatternImage>>

    @Insert
    suspend fun insertImage(image: PatternImage): Long

    @Query("SELECT * FROM pattern_attachments WHERE patternId = :patternId ORDER BY addedAt")
    fun observeAttachments(patternId: Long): Flow<List<PatternAttachment>>

    @Insert
    suspend fun insertAttachment(attachment: PatternAttachment): Long
}
