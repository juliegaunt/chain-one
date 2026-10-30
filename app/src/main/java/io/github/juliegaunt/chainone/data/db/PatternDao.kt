package io.github.juliegaunt.chainone.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface PatternDao {

    /** The library with filters applied; build [query] with [LibraryQuery]. */
    @RawQuery(observedEntities = [Pattern::class, PatternTag::class, Tag::class])
    fun observeLibrary(query: SupportSQLiteQuery): Flow<List<Pattern>>

    @Query("SELECT COUNT(*) FROM patterns")
    fun observePatternCount(): Flow<Int>

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

    @Query("UPDATE patterns SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setStatus(id: Long, status: PatternStatus, updatedAt: Long)

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

    @Query("SELECT * FROM pattern_attachments WHERE patternId = :patternId ORDER BY addedAt")
    suspend fun getAttachments(patternId: Long): List<PatternAttachment>

    @Query("SELECT * FROM pattern_attachments WHERE id = :id")
    suspend fun getAttachment(id: Long): PatternAttachment?

    @Insert
    suspend fun insertAttachment(attachment: PatternAttachment): Long

    @Query("UPDATE pattern_attachments SET displayName = :name WHERE id = :id")
    suspend fun setAttachmentName(id: Long, name: String)

    @Query("UPDATE pattern_attachments SET lastPage = :page WHERE id = :id")
    suspend fun setAttachmentLastPage(id: Long, page: Int)

    @Query("UPDATE pattern_attachments SET markerPage = :page, markerTop = :top, markerHeight = :height WHERE id = :id")
    suspend fun setAttachmentMarker(id: Long, page: Int?, top: Float?, height: Float?)

    @Query("DELETE FROM pattern_attachments WHERE id = :id")
    suspend fun deleteAttachment(id: Long)

    // Tags. Names compare without regard to case (the column is NOCASE), so "Baby" finds "baby".

    @Query(
        """
        SELECT tags.id, tags.name, COUNT(pattern_tags.patternId) AS patternCount
        FROM tags LEFT JOIN pattern_tags ON pattern_tags.tagId = tags.id
        GROUP BY tags.id
        ORDER BY tags.name COLLATE NOCASE
        """,
    )
    fun observeTagCounts(): Flow<List<TagCount>>

    @Query(
        """
        SELECT tags.* FROM tags JOIN pattern_tags ON pattern_tags.tagId = tags.id
        WHERE pattern_tags.patternId = :patternId
        ORDER BY tags.name COLLATE NOCASE
        """,
    )
    fun observeTagsFor(patternId: Long): Flow<List<Tag>>

    @Query(
        """
        SELECT tags.* FROM tags JOIN pattern_tags ON pattern_tags.tagId = tags.id
        WHERE pattern_tags.patternId = :patternId
        ORDER BY tags.name COLLATE NOCASE
        """,
    )
    suspend fun getTagsFor(patternId: Long): List<Tag>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findTag(name: String): Tag?

    @Insert
    suspend fun insertTag(tag: Tag): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPatternTags(links: List<PatternTag>)

    @Query("DELETE FROM pattern_tags WHERE patternId = :patternId")
    suspend fun clearTags(patternId: Long)

    /** Tags no pattern uses any more disappear, so the tag list stays tidy. */
    @Query("DELETE FROM tags WHERE id NOT IN (SELECT tagId FROM pattern_tags)")
    suspend fun deleteUnusedTags()

    @Query("UPDATE tags SET name = :name WHERE id = :id")
    suspend fun setTagName(id: Long, name: String)

    @Query("INSERT OR IGNORE INTO pattern_tags (patternId, tagId) SELECT patternId, :intoId FROM pattern_tags WHERE tagId = :fromId")
    suspend fun copyTagLinks(fromId: Long, intoId: Long)

    /** Removes the tag from every pattern too (the links cascade). */
    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun deleteTag(id: Long)

    /** Gives a pattern exactly [names] as its tags, reusing existing tags whatever their case. */
    @Transaction
    suspend fun replaceTags(patternId: Long, names: List<String>) {
        clearTags(patternId)
        val ids = names.map { name -> findTag(name)?.id ?: insertTag(Tag(name = name)) }
        insertPatternTags(ids.distinct().map { PatternTag(patternId = patternId, tagId = it) })
        deleteUnusedTags()
    }

    /** Renames a tag; if another tag already has that name, the two are merged into it. */
    @Transaction
    suspend fun renameTag(id: Long, name: String) {
        val existing = findTag(name)
        if (existing != null && existing.id != id) {
            copyTagLinks(fromId = id, intoId = existing.id)
            deleteTag(id)
        } else {
            setTagName(id, name)
        }
    }
}
