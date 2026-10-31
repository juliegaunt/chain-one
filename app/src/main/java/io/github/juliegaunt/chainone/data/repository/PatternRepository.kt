package io.github.juliegaunt.chainone.data.repository

import android.net.Uri
import io.github.juliegaunt.chainone.data.db.LibraryFilter
import io.github.juliegaunt.chainone.data.db.LibraryQuery
import io.github.juliegaunt.chainone.data.db.Pattern
import io.github.juliegaunt.chainone.data.db.PatternDao
import io.github.juliegaunt.chainone.data.db.PatternStatus
import io.github.juliegaunt.chainone.data.db.PatternSummary
import io.github.juliegaunt.chainone.data.db.SourceType
import io.github.juliegaunt.chainone.data.db.Tag
import io.github.juliegaunt.chainone.data.db.TagCount
import io.github.juliegaunt.chainone.data.files.FileStore
import io.github.juliegaunt.chainone.data.importer.PatternImporter
import io.github.juliegaunt.chainone.data.importer.StagedImport
import io.github.juliegaunt.chainone.data.importer.UrlTools
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/** The fields a person can type in on the edit screen. */
data class PatternDraft(
    val title: String,
    val designer: String = "",
    val sourceUrl: String? = null,
    val hookSize: String = "",
    val yarnWeight: String = "",
    val materials: String = "",
    val instructions: String = "",
    val notes: String = "",
    val status: PatternStatus = PatternStatus.WANT_TO_MAKE,
    val tags: List<String> = emptyList(),
    /** A category picked by hand, or null to sort by title. */
    val categoryId: Long? = null,
)

/** Keeps the database and the files on disk in step for everything pattern-related. */
class PatternRepository(
    private val dao: PatternDao,
    private val files: FileStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun observeLibrary(filter: LibraryFilter): Flow<List<PatternSummary>> = dao.observeLibrary(LibraryQuery.build(filter))

    fun observePatternCount(): Flow<Int> = dao.observePatternCount()

    fun observeTags(): Flow<List<TagCount>> = dao.observeTagCounts()

    fun observeTagsFor(patternId: Long): Flow<List<Tag>> = dao.observeTagsFor(patternId)

    suspend fun getTagNames(patternId: Long): List<String> = dao.getTagsFor(patternId).map { it.name }

    fun observePattern(id: Long): Flow<Pattern?> = dao.observePattern(id)

    suspend fun getPattern(id: Long): Pattern? = dao.getPattern(id)

    suspend fun create(draft: PatternDraft, sourceType: SourceType = SourceType.MANUAL): Long {
        val now = clock()
        val id = dao.insert(
            Pattern(
                title = draft.title.trim(),
                designer = draft.designer.trim(),
                sourceUrl = draft.sourceUrl?.trim()?.ifEmpty { null },
                sourceType = sourceType,
                hookSize = draft.hookSize.trim(),
                yarnWeight = draft.yarnWeight.trim(),
                materials = draft.materials.trim(),
                instructions = draft.instructions.trim(),
                notes = draft.notes.trim(),
                status = draft.status,
                categoryId = draft.categoryId,
                createdAt = now,
                updatedAt = now,
            ),
        )
        dao.replaceTags(id, cleanTags(draft.tags))
        return id
    }

    suspend fun update(id: Long, draft: PatternDraft) {
        val existing = dao.getPattern(id) ?: return
        dao.update(
            existing.copy(
                title = draft.title.trim(),
                designer = draft.designer.trim(),
                sourceUrl = draft.sourceUrl?.trim()?.ifEmpty { null },
                hookSize = draft.hookSize.trim(),
                yarnWeight = draft.yarnWeight.trim(),
                materials = draft.materials.trim(),
                instructions = draft.instructions.trim(),
                notes = draft.notes.trim(),
                status = draft.status,
                categoryId = draft.categoryId,
                updatedAt = clock(),
            ),
        )
        dao.replaceTags(id, cleanTags(draft.tags))
    }

    suspend fun setStatus(id: Long, status: PatternStatus) = dao.setStatus(id, status, clock())

    /** Renames a tag on every pattern; if the new name is already a tag, the two become one. */
    suspend fun renameTag(id: Long, name: String) {
        val clean = cleanTag(name)
        if (clean.isNotEmpty()) dao.renameTag(id, clean)
    }

    /** Removes a tag from every pattern. */
    suspend fun deleteTag(id: Long) = dao.deleteTag(id)

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    suspend fun markOpened(id: Long) = dao.setLastOpened(id, clock())

    /** Copies a picked photo into app storage and makes it the cover, replacing any old one. */
    suspend fun setCover(id: Long, uri: Uri) = replaceCover(id, files.importToPattern(id, uri, COVER_NAME))

    suspend fun setCover(id: Long, input: InputStream, extension: String) =
        replaceCover(id, files.saveToPattern(id, input, COVER_NAME, extension))

    suspend fun removeCover(id: Long) = replaceCover(id, null)

    /** A pattern already saved from this link (or another spelling of it), if any. */
    suspend fun findBySourceUrl(cleanUrl: String): Pattern? = dao.findBySourceUrl(UrlTools.sameUrlVariants(cleanUrl))

    /**
     * Moves an import's files into the saved pattern [id]: the saved copy of the page and,
     * if [keepCover], the downloaded cover photo.
     */
    suspend fun attachImport(id: Long, staged: StagedImport, keepCover: Boolean) {
        if (keepCover) {
            staged.coverFile?.let { replaceCover(id, files.adoptIntoPattern(id, it, COVER_NAME)) }
        }
        staged.snapshotDir?.let { folder ->
            dao.setSnapshotPath(id, files.adoptSnapshot(id, folder, PatternImporter.SNAPSHOT_PAGE), clock())
        }
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
        dao.deleteUnusedTags()
        files.deletePatternFiles(id)
    }

    private suspend fun replaceCover(id: Long, newPath: String?) {
        val oldPath = dao.getPattern(id)?.coverImagePath
        dao.setCoverImagePath(id, newPath, clock())
        if (oldPath != null && oldPath != newPath) files.delete(oldPath)
    }

    private companion object {
        const val COVER_NAME = "cover"
        const val MAX_TAG_LENGTH = 40
        val SPACES = Regex("""\s+""")

        fun cleanTag(name: String): String = name.trim().replace(SPACES, " ").take(MAX_TAG_LENGTH).trim()

        /** Trimmed, without blanks, and without the same tag twice in different case. */
        fun cleanTags(names: List<String>): List<String> =
            names.map(::cleanTag).filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
    }
}
