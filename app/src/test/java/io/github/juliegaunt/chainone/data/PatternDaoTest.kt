package io.github.juliegaunt.chainone.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.juliegaunt.chainone.data.db.ChainOneDatabase
import io.github.juliegaunt.chainone.data.db.LibraryFilter
import io.github.juliegaunt.chainone.data.db.LibraryQuery
import io.github.juliegaunt.chainone.data.db.Pattern
import io.github.juliegaunt.chainone.data.db.PatternAttachment
import io.github.juliegaunt.chainone.data.db.PatternDao
import io.github.juliegaunt.chainone.data.db.PatternImage
import io.github.juliegaunt.chainone.data.db.PatternStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PatternDaoTest {

    private lateinit var db: ChainOneDatabase
    private lateinit var dao: PatternDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ChainOneDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.patternDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun pattern(title: String, updatedAt: Long = 1, favorite: Boolean = false) =
        Pattern(title = title, isFavorite = favorite, createdAt = 1, updatedAt = updatedAt)

    @Test
    fun insertedPatternCanBeReadBack() = runTest {
        val id = dao.insert(
            pattern("Granny Square Blanket").copy(
                designer = "Ana",
                hookSize = "5 mm",
                status = PatternStatus.IN_PROGRESS,
            ),
        )

        val loaded = dao.getPattern(id)!!
        assertEquals("Granny Square Blanket", loaded.title)
        assertEquals("Ana", loaded.designer)
        assertEquals("5 mm", loaded.hookSize)
        assertEquals(PatternStatus.IN_PROGRESS, loaded.status)
    }

    @Test
    fun libraryListsMostRecentlyUpdatedFirst() = runTest {
        dao.insert(pattern("Old", updatedAt = 100))
        dao.insert(pattern("Newest", updatedAt = 300))
        dao.insert(pattern("Middle", updatedAt = 200))

        val titles = dao.observeLibrary(LibraryQuery.build(LibraryFilter())).first().map { it.title }
        assertEquals(listOf("Newest", "Middle", "Old"), titles)
    }

    @Test
    fun favoritesOnlyHidesOtherPatterns() = runTest {
        dao.insert(pattern("Hat", favorite = true))
        dao.insert(pattern("Scarf"))

        val titles = dao.observeLibrary(LibraryQuery.build(LibraryFilter(favoritesOnly = true))).first().map { it.title }
        assertEquals(listOf("Hat"), titles)
    }

    @Test
    fun favoritingKeepsThePatternsPlaceInTheLibrary() = runTest {
        val fox = dao.insert(pattern("Amigurumi Fox", updatedAt = 1))
        dao.insert(pattern("Hat", updatedAt = 2))

        dao.setFavorite(fox, favorite = true)

        val loaded = dao.getPattern(fox)!!
        assertTrue(loaded.isFavorite)
        assertEquals(1, loaded.updatedAt)
        assertEquals(listOf("Hat", "Amigurumi Fox"), dao.observeLibrary(LibraryQuery.build(LibraryFilter())).first().map { it.title })
    }

    @Test
    fun libraryFlowEmitsAfterChanges() = runTest {
        val id = dao.insert(pattern("Before"))
        dao.update(dao.getPattern(id)!!.copy(title = "After"))

        assertEquals("After", dao.observeLibrary(LibraryQuery.build(LibraryFilter())).first().single().title)
    }

    @Test
    fun deletingPatternRemovesAllItsChildRows() = runTest {
        val id = dao.insert(pattern("Cardigan"))
        val keepId = dao.insert(pattern("Keep me"))
        dao.insertImage(PatternImage(patternId = id, filePath = "patterns/$id/a.jpg", position = 0))
        db.pdfDao().insert(PatternAttachment(patternId = id, filePath = "patterns/$id/p.pdf", displayName = "p.pdf", addedAt = 1))
        val sql = db.openHelper.writableDatabase
        sql.execSQL("INSERT INTO counters (id, patternId, name, value, position, updatedAt) VALUES (1, $id, 'Row', 3, 0, 1)")
        sql.execSQL("INSERT INTO counter_history (counterId, previousValue, createdAt) VALUES (1, 2, 1)")
        sql.execSQL("INSERT INTO tags (id, name) VALUES (1, 'sweater')")
        sql.execSQL("INSERT INTO pattern_tags (patternId, tagId) VALUES ($id, 1)")

        dao.delete(id)

        assertNull(dao.getPattern(id))
        assertFalse(dao.getPattern(keepId) == null)
        for (table in listOf("pattern_images", "pattern_attachments", "counters", "counter_history", "pattern_tags")) {
            assertEquals("rows left in $table", 0, count(table))
        }
        // Tags are shared between patterns, so the tag itself stays.
        assertEquals(1, count("tags"))
    }

    private fun count(table: String): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            it.moveToFirst()
            it.getInt(0)
        }
}
