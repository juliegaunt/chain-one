package io.github.juliegaunt.chainone.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/** Everything in the library's database except the counters' undo history, which backups leave out. */
data class LibrarySnapshot(
    val patterns: List<Pattern>,
    val images: List<PatternImage>,
    val attachments: List<PatternAttachment>,
    val tags: List<Tag>,
    val patternTags: List<PatternTag>,
    val counters: List<Counter>,
)

/** Reads and replaces the whole library at once, for backup and restore. */
@Dao
interface BackupDao {

    @Query("SELECT * FROM patterns ORDER BY id")
    suspend fun allPatterns(): List<Pattern>

    @Query("SELECT * FROM pattern_images ORDER BY id")
    suspend fun allImages(): List<PatternImage>

    @Query("SELECT * FROM pattern_attachments ORDER BY id")
    suspend fun allAttachments(): List<PatternAttachment>

    @Query("SELECT * FROM tags ORDER BY id")
    suspend fun allTags(): List<Tag>

    @Query("SELECT * FROM pattern_tags ORDER BY patternId, tagId")
    suspend fun allPatternTags(): List<PatternTag>

    @Query("SELECT * FROM counters ORDER BY id")
    suspend fun allCounters(): List<Counter>

    @Query("SELECT COUNT(*) FROM patterns")
    suspend fun patternCount(): Int

    /** Images, PDFs, tag links, counters and undo history go too (they cascade). */
    @Query("DELETE FROM patterns")
    suspend fun deleteAllPatterns()

    @Query("DELETE FROM tags")
    suspend fun deleteAllTags()

    @Insert
    suspend fun insertPatterns(patterns: List<Pattern>)

    @Insert
    suspend fun insertImages(images: List<PatternImage>)

    @Insert
    suspend fun insertAttachments(attachments: List<PatternAttachment>)

    @Insert
    suspend fun insertTags(tags: List<Tag>)

    @Insert
    suspend fun insertPatternTags(links: List<PatternTag>)

    @Insert
    suspend fun insertCounters(counters: List<Counter>)

    /** The library as it is at one moment, even if something changes it meanwhile. */
    @Transaction
    suspend fun snapshot(): LibrarySnapshot =
        LibrarySnapshot(allPatterns(), allImages(), allAttachments(), allTags(), allPatternTags(), allCounters())

    /**
     * Swaps the whole library for [snapshot], keeping its ids so file paths still match. All or
     * nothing: if any row is rejected, the library is left as it was.
     */
    @Transaction
    suspend fun replaceAll(snapshot: LibrarySnapshot) {
        deleteAllPatterns()
        deleteAllTags()
        insertPatterns(snapshot.patterns)
        insertTags(snapshot.tags)
        insertImages(snapshot.images)
        insertAttachments(snapshot.attachments)
        insertPatternTags(snapshot.patternTags)
        insertCounters(snapshot.counters)
    }
}
