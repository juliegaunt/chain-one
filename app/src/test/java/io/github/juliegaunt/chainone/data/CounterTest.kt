package io.github.juliegaunt.chainone.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.juliegaunt.chainone.data.db.ChainOneDatabase
import io.github.juliegaunt.chainone.data.db.PatternStatus
import io.github.juliegaunt.chainone.data.files.FileStore
import io.github.juliegaunt.chainone.data.repository.CounterRepository
import io.github.juliegaunt.chainone.data.repository.PatternDraft
import io.github.juliegaunt.chainone.data.repository.PatternRepository
import io.github.juliegaunt.chainone.data.repository.RepeatProgress
import io.github.juliegaunt.chainone.data.repository.repeatProgress
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
class CounterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var db: ChainOneDatabase
    private lateinit var patterns: PatternRepository
    private lateinit var counters: CounterRepository
    private var now = 1L
    private var patternId = 0L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ChainOneDatabase::class.java).allowMainThreadQueries().build()
        patterns = PatternRepository(db.patternDao(), FileStore(tempFolder.newFolder(), context.contentResolver)) { now++ }
        counters = CounterRepository(db.counterDao(), db.patternDao()) { now++ }
        patternId = patterns.create(PatternDraft(title = "Blanket"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun list() = counters.observeCounters(patternId).first()
    private suspend fun value(id: Long) = list().single { it.id == id }.value

    @Test
    fun countsUpAndDownButNeverBelowZero() = runTest {
        val row = counters.add(patternId, "Row")

        repeat(3) { counters.increment(row) }
        counters.decrement(row)
        assertEquals(2, value(row))

        repeat(5) { counters.decrement(row) }
        assertEquals(0, value(row))
    }

    @Test
    fun undoTakesBackTheLatestChangeOnWhicheverCounter() = runTest {
        val row = counters.add(patternId, "Row")
        val sleeve = counters.add(patternId, "Sleeve")
        counters.increment(row)
        counters.increment(row)
        counters.increment(sleeve)
        counters.setValue(row, 37)

        assertTrue(counters.undo(patternId))
        assertEquals(2, value(row))
        assertTrue(counters.undo(patternId))
        assertEquals(0, value(sleeve))
        assertTrue(counters.undo(patternId))
        assertTrue(counters.undo(patternId))
        assertEquals(0, value(row))
        assertFalse(counters.undo(patternId))
        assertFalse(counters.observeCanUndo(patternId).first())
    }

    @Test
    fun undoStillWorksAfterTheAppRestarts() = runTest {
        val row = counters.add(patternId, "Row")
        counters.increment(row)
        counters.increment(row)

        // A fresh repository on the same database, as after reopening the app.
        val reopened = CounterRepository(db.counterDao(), db.patternDao()) { now++ }
        reopened.undo(patternId)

        assertEquals(1, value(row))
    }

    @Test
    fun changesThatChangeNothingAreNotUndoSteps() = runTest {
        val row = counters.add(patternId, "Row")
        counters.decrement(row)
        counters.reset(row)

        assertFalse(counters.observeCanUndo(patternId).first())
    }

    @Test
    fun undoHistoryIsCapped() = runTest {
        val row = counters.add(patternId, "Row")
        repeat(205) { counters.increment(row) }

        var undone = 0
        while (counters.undo(patternId)) undone++

        assertEquals(200, undone)
        assertEquals(5, value(row))
    }

    @Test
    fun countingUpStartsAWantToMakePatternButNeverChangesOtherStatuses() = runTest {
        val row = counters.add(patternId, "Row")
        counters.decrement(row)
        assertEquals(PatternStatus.WANT_TO_MAKE, patterns.getPattern(patternId)!!.status)

        counters.increment(row)
        assertEquals(PatternStatus.IN_PROGRESS, patterns.getPattern(patternId)!!.status)

        patterns.setStatus(patternId, PatternStatus.FINISHED)
        counters.increment(row)
        assertEquals(PatternStatus.FINISHED, patterns.getPattern(patternId)!!.status)
    }

    @Test
    fun mainCounterIsCreatedOnceAsRow() = runTest {
        val first = counters.mainCounter(patternId, "Row")
        val again = counters.mainCounter(patternId, "Row")

        assertEquals("Row", first.name)
        assertEquals(first.id, again.id)
        assertEquals(1, list().size)
    }

    @Test
    fun countersCanBeReorderedRenamedAndDeleted() = runTest {
        val a = counters.add(patternId, "A")
        val b = counters.add(patternId, "B")
        val c = counters.add(patternId, "C")

        counters.move(c, -1)
        assertEquals(listOf("A", "C", "B"), list().map { it.name })
        counters.move(a, -1) // already first: nothing happens
        assertEquals(listOf("A", "C", "B"), list().map { it.name })

        counters.updateDetails(b, "  Sleeve  ", repeatLength = 4, repeatTimes = 5)
        val sleeve = list().single { it.id == b }
        assertEquals("Sleeve", sleeve.name)
        assertEquals(4, sleeve.repeatLength)
        assertEquals(5, sleeve.repeatTimes)

        counters.updateDetails(b, "Sleeve", repeatLength = null, repeatTimes = 5)
        assertNull(list().single { it.id == b }.repeatTimes)

        counters.increment(a)
        counters.delete(a)
        assertEquals(listOf("C", "Sleeve"), list().map { it.name })
        assertFalse("its undo steps go with it", counters.observeCanUndo(patternId).first())
    }

    @Test
    fun deletingThePatternDeletesItsCounters() = runTest {
        counters.add(patternId, "Row")

        patterns.delete(patternId)

        assertEquals(emptyList<Any>(), list())
    }

    @Test
    fun repeatProgressCountsWithinEachRepeat() {
        // Repeat rows 1–4 five times.
        fun at(value: Int) = RepeatProgress.of(value, repeatLength = 4, repeatTimes = 5).let { Triple(it.inRepeat, it.repeat, it.isDone) }

        assertEquals(Triple(0, 1, false), at(0))
        assertEquals(Triple(1, 1, false), at(1))
        assertEquals(Triple(4, 1, false), at(4))
        assertEquals(Triple(1, 2, false), at(5))
        assertEquals(Triple(4, 5, true), at(20))
        assertEquals(Triple(1, 6, true), at(21))
        assertEquals(0, RepeatProgress.total(inRepeat = 0, repeat = 1, repeatLength = 4))
        assertEquals(10, RepeatProgress.total(inRepeat = 2, repeat = 3, repeatLength = 4))
    }

    @Test
    fun plainCountersHaveNoRepeatProgress() = runTest {
        val row = counters.add(patternId, "Row")
        assertNull(list().single { it.id == row }.repeatProgress)
    }
}
