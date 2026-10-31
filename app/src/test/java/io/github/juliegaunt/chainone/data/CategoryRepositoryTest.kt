package io.github.juliegaunt.chainone.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.juliegaunt.chainone.data.db.ChainOneDatabase
import io.github.juliegaunt.chainone.data.files.FileStore
import io.github.juliegaunt.chainone.data.repository.CategoryRepository
import io.github.juliegaunt.chainone.data.repository.PatternDraft
import io.github.juliegaunt.chainone.data.repository.PatternRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoryRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: ChainOneDatabase
    private lateinit var categories: CategoryRepository
    private lateinit var patterns: PatternRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ChainOneDatabase::class.java).allowMainThreadQueries().build()
        categories = CategoryRepository(db.categoryDao())
        patterns = PatternRepository(db.patternDao(), FileStore(tempFolder.newFolder(), context.contentResolver)) { 1L }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun all() = categories.observeCategories().first()

    @Test
    fun addsTidiedCategoriesInOrderAndRefusesDuplicateNames() = runTest {
        assertTrue(categories.add("  Hats  ", " Beanie,beret ,, "))
        assertTrue(categories.add("Bags", ""))
        assertFalse(categories.add("hats", "cloche"))
        assertFalse(categories.add("   ", ""))

        assertEquals(listOf("Hats", "Bags"), all().map { it.name })
        assertEquals("beanie, beret", all().first().extraWords)
    }

    @Test
    fun renamingCannotTakeAnotherCategorysName() = runTest {
        categories.add("Hats", "")
        categories.add("Bags", "")
        val hats = all().first()

        assertFalse(categories.update(hats.id, "BAGS", ""))
        assertTrue(categories.update(hats.id, "Hats & Caps", "beanie"))
        assertEquals("Hats & Caps", all().first().name)
    }

    @Test
    fun movingChangesTheOrder() = runTest {
        listOf("Hats", "Bags", "Toys").forEach { categories.add(it, "") }

        categories.move(all().last().id, -1)

        assertEquals(listOf("Hats", "Toys", "Bags"), all().map { it.name })
    }

    @Test
    fun deletingACategoryPutsItsHandPickedPatternsBackOnAutomatic() = runTest {
        categories.add("Hats", "")
        val hats = all().single()
        val id = patterns.create(PatternDraft(title = "Owl", categoryId = hats.id))
        assertEquals(hats.id, patterns.getPattern(id)!!.categoryId)

        categories.delete(hats.id)

        assertNull(patterns.getPattern(id)!!.categoryId)
        assertEquals("Owl", patterns.getPattern(id)!!.title)
    }
}
