package io.github.juliegaunt.chainone.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** A pattern's PDFs (the `pattern_attachments` table), oldest first. */
@Dao
interface PdfDao {

    @Query("SELECT * FROM pattern_attachments WHERE patternId = :patternId ORDER BY addedAt")
    fun observeAll(patternId: Long): Flow<List<PatternAttachment>>

    @Query("SELECT * FROM pattern_attachments WHERE patternId = :patternId ORDER BY addedAt")
    suspend fun getAll(patternId: Long): List<PatternAttachment>

    @Query("SELECT * FROM pattern_attachments WHERE id = :id")
    suspend fun get(id: Long): PatternAttachment?

    @Insert
    suspend fun insert(pdf: PatternAttachment): Long

    @Query("UPDATE pattern_attachments SET displayName = :name WHERE id = :id")
    suspend fun setName(id: Long, name: String)

    @Query("UPDATE pattern_attachments SET lastPage = :page WHERE id = :id")
    suspend fun setLastPage(id: Long, page: Int)

    @Query("UPDATE pattern_attachments SET markerPage = :page, markerTop = :top, markerHeight = :height WHERE id = :id")
    suspend fun setMarker(id: Long, page: Int?, top: Float?, height: Float?)

    @Query("DELETE FROM pattern_attachments WHERE id = :id")
    suspend fun delete(id: Long)
}
