package io.github.juliegaunt.chainone.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.juliegaunt.chainone.data.db.ChainOneDatabase
import io.github.juliegaunt.chainone.data.db.LibraryFilter
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

/** Search, status, tag and favorite filters on the library, alone and combined. */
@RunWith(RobolectricTestRunner::class)
class LibraryFilterTest {

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

    private suspend fun add(draft: PatternDraft, favorite: Boolean = false): Long =
        repository.create(draft).also { if (favorite) repository.setFavorite(it, true) }

    private suspend fun titles(filter: LibraryFilter) = repository.observeLibrary(filter).first().map { it.title }.toSet()

    private suspend fun tagId(name: String) = repository.observeTags().first().single { it.name == name }.id

    @Test
    fun searchLooksInEveryTextFieldAndTags() = runTest {
        add(PatternDraft(title = "Granny Square Tote"))
        add(PatternDraft(title = "Beanie", designer = "Jo Granny"))
        add(PatternDraft(title = "Cowl", instructions = "Rnd 1: granny cluster"))
        add(PatternDraft(title = "Mitts", notes = "Gift for Gran"))
        add(PatternDraft(title = "Blanket", tags = listOf("grandma")))
        add(PatternDraft(title = "Scarf", hookSize = "5 mm", yarnWeight = "Worsted", materials = "Cotton"))

        // Part of a word matches, and capital letters don't matter.
        assertEquals(setOf("Granny Square Tote", "Beanie", "Cowl", "Mitts", "Blanket"), titles(LibraryFilter(search = "GRAN")))
        assertEquals(setOf("Scarf"), titles(LibraryFilter(search = "worsted")))
        assertEquals(setOf("Scarf"), titles(LibraryFilter(search = "5 mm")))
    }

    @Test
    fun everySearchWordMustMatchSomewhere() = runTest {
        add(PatternDraft(title = "Granny Square Bag"))
        add(PatternDraft(title = "Market Bag"))
        add(PatternDraft(title = "Granny Blanket", tags = listOf("bag-free")))

        assertEquals(setOf("Granny Square Bag", "Granny Blanket"), titles(LibraryFilter(search = "  granny   bag ")))
        assertEquals(setOf("Market Bag"), titles(LibraryFilter(search = "bag market")))
    }

    @Test
    fun searchTreatsPercentAndUnderscoreAsPlainCharacters() = runTest {
        add(PatternDraft(title = "100% cotton dishcloth"))
        add(PatternDraft(title = "1000 yard blanket"))
        add(PatternDraft(title = "sc_2tog practice"))
        add(PatternDraft(title = "sc2tog swatch"))

        assertEquals(setOf("100% cotton dishcloth"), titles(LibraryFilter(search = "100%")))
        assertEquals(setOf("sc_2tog practice"), titles(LibraryFilter(search = "sc_2")))
    }

    @Test
    fun statusFilterShowsAnyOfTheChosenStatuses() = runTest {
        add(PatternDraft(title = "Hat", status = PatternStatus.WANT_TO_MAKE))
        add(PatternDraft(title = "Scarf", status = PatternStatus.IN_PROGRESS))
        add(PatternDraft(title = "Mitts", status = PatternStatus.FINISHED))

        assertEquals(setOf("Scarf"), titles(LibraryFilter(statuses = setOf(PatternStatus.IN_PROGRESS))))
        assertEquals(
            setOf("Hat", "Scarf"),
            titles(LibraryFilter(statuses = setOf(PatternStatus.WANT_TO_MAKE, PatternStatus.IN_PROGRESS))),
        )
    }

    @Test
    fun tagFilterNeedsEveryChosenTag() = runTest {
        add(PatternDraft(title = "Baby blanket", tags = listOf("baby", "gift")))
        add(PatternDraft(title = "Baby hat", tags = listOf("baby")))
        add(PatternDraft(title = "Teacher mug cozy", tags = listOf("gift")))

        val baby = tagId("baby")
        val gift = tagId("gift")
        assertEquals(setOf("Baby blanket", "Baby hat"), titles(LibraryFilter(tagIds = setOf(baby))))
        assertEquals(setOf("Baby blanket"), titles(LibraryFilter(tagIds = setOf(baby, gift))))
    }

    @Test
    fun filtersCombine() = runTest {
        add(PatternDraft(title = "Granny cardigan", status = PatternStatus.IN_PROGRESS, tags = listOf("gift")), favorite = true)
        add(PatternDraft(title = "Granny vest", status = PatternStatus.IN_PROGRESS, tags = listOf("gift")))
        add(PatternDraft(title = "Granny bag", status = PatternStatus.FINISHED, tags = listOf("gift")), favorite = true)
        add(PatternDraft(title = "Raglan", status = PatternStatus.IN_PROGRESS, tags = listOf("gift")), favorite = true)

        val filter = LibraryFilter(
            search = "granny",
            statuses = setOf(PatternStatus.IN_PROGRESS),
            tagIds = setOf(tagId("gift")),
            favoritesOnly = true,
        )
        assertEquals(setOf("Granny cardigan"), titles(filter))
    }

    @Test
    fun filteredLibraryKeepsMostRecentlyUpdatedFirst() = runTest {
        add(PatternDraft(title = "First granny"))
        add(PatternDraft(title = "Second granny"))

        assertEquals(
            listOf("Second granny", "First granny"),
            repository.observeLibrary(LibraryFilter(search = "granny")).first().map { it.title },
        )
    }
}
