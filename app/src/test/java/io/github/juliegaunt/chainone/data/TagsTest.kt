package io.github.juliegaunt.chainone.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.juliegaunt.chainone.data.db.ChainOneDatabase
import io.github.juliegaunt.chainone.data.db.PatternStatus
import io.github.juliegaunt.chainone.data.files.FileStore
import io.github.juliegaunt.chainone.data.repository.PatternDraft
import io.github.juliegaunt.chainone.data.repository.PatternRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TagsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: ChainOneDatabase
    private lateinit var repository: PatternRepository
    private var now = 1L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ChainOneDatabase::class.java).allowMainThreadQueries().build()
        repository = PatternRepository(db.patternDao(), FileStore(tempFolder.newFolder(), context.contentResolver)) { now++ }
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Tag names with how many patterns use each, alphabetically. */
    private suspend fun tagList() = repository.observeTags().first().map { "${it.name}=${it.patternCount}" }

    @Test
    fun tagsAreTrimmedDeduplicatedAndSharedWhateverTheirCase() = runTest {
        val hat = repository.create(PatternDraft(title = "Hat", tags = listOf(" Baby ", "baby", "", "winter  wear")))
        repository.create(PatternDraft(title = "Booties", tags = listOf("BABY")))

        // "BABY" reuses the existing "Baby" tag rather than creating a second one.
        assertEquals(listOf("Baby=2", "winter wear=1"), tagList())
        assertEquals(listOf("Baby", "winter wear"), repository.getTagNames(hat))
    }

    @Test
    fun editingTagsReplacesThemAndDropsTagsNoLongerUsed() = runTest {
        val id = repository.create(PatternDraft(title = "Hat", tags = listOf("baby", "gift")))

        repository.update(id, PatternDraft(title = "Hat", tags = listOf("gift", "winter")))

        assertEquals(listOf("gift", "winter"), repository.getTagNames(id))
        assertEquals(listOf("gift=1", "winter=1"), tagList())
    }

    @Test
    fun renamingChangesTheTagOnEveryPattern() = runTest {
        val a = repository.create(PatternDraft(title = "A", tags = listOf("presents")))
        val b = repository.create(PatternDraft(title = "B", tags = listOf("presents")))
        val id = repository.observeTags().first().single().id

        repository.renameTag(id, "  Gifts ")

        assertEquals(listOf("Gifts"), repository.getTagNames(a))
        assertEquals(listOf("Gifts"), repository.getTagNames(b))
    }

    @Test
    fun renamingOnlyTheCaseKeepsTheSameTag() = runTest {
        repository.create(PatternDraft(title = "A", tags = listOf("baby")))
        val id = repository.observeTags().first().single().id

        repository.renameTag(id, "Baby")

        assertEquals(listOf("Baby=1"), tagList())
    }

    @Test
    fun renamingToAnExistingNameMergesTheTwo() = runTest {
        val a = repository.create(PatternDraft(title = "A", tags = listOf("hat")))
        val b = repository.create(PatternDraft(title = "B", tags = listOf("hats")))
        val both = repository.create(PatternDraft(title = "C", tags = listOf("hat", "hats")))
        val hat = repository.observeTags().first().single { it.name == "hat" }.id

        repository.renameTag(hat, "Hats")

        assertEquals(listOf("hats=3"), tagList())
        listOf(a, b, both).forEach { assertEquals(listOf("hats"), repository.getTagNames(it)) }
    }

    @Test
    fun blankRenameIsIgnored() = runTest {
        repository.create(PatternDraft(title = "A", tags = listOf("baby")))
        val id = repository.observeTags().first().single().id

        repository.renameTag(id, "   ")

        assertEquals(listOf("baby=1"), tagList())
    }

    @Test
    fun deletingATagRemovesItFromPatternsButKeepsThePatterns() = runTest {
        val id = repository.create(PatternDraft(title = "Hat", tags = listOf("baby", "gift")))
        val baby = repository.observeTags().first().single { it.name == "baby" }.id

        repository.deleteTag(baby)

        assertEquals(listOf("gift"), repository.getTagNames(id))
        assertEquals("Hat", repository.getPattern(id)!!.title)
    }

    @Test
    fun deletingAPatternDropsTagsOnlyItUsed() = runTest {
        val hat = repository.create(PatternDraft(title = "Hat", tags = listOf("baby", "gift")))
        repository.create(PatternDraft(title = "Mug cozy", tags = listOf("gift")))

        repository.delete(hat)

        assertEquals(listOf("gift=1"), tagList())
    }

    @Test
    fun setStatusChangesItAndCountsAsAnUpdate() = runTest {
        val id = repository.create(PatternDraft(title = "Hat"))
        val before = repository.getPattern(id)!!.updatedAt

        repository.setStatus(id, PatternStatus.FINISHED)

        val pattern = repository.getPattern(id)!!
        assertEquals(PatternStatus.FINISHED, pattern.status)
        assertEquals(true, pattern.updatedAt > before)
    }
}
