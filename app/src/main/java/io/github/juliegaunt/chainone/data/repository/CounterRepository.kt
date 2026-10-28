package io.github.juliegaunt.chainone.data.repository

import io.github.juliegaunt.chainone.data.db.Counter
import io.github.juliegaunt.chainone.data.db.CounterDao
import io.github.juliegaunt.chainone.data.db.PatternDao
import io.github.juliegaunt.chainone.data.db.PatternStatus
import kotlinx.coroutines.flow.Flow

/**
 * Where a repeat counter's total stands: [inRepeat] of [repeatLength] within repeat number
 * [repeat] (of [repeatTimes], if the pattern says how many). A total of 0 is "0, repeat 1".
 */
data class RepeatProgress(
    val inRepeat: Int,
    val repeatLength: Int,
    val repeat: Int,
    val repeatTimes: Int?,
) {
    /** True once the last row of the last repeat has been counted. */
    val isDone: Boolean get() = repeatTimes != null && (repeat > repeatTimes || (repeat == repeatTimes && inRepeat == repeatLength))

    companion object {
        fun of(value: Int, repeatLength: Int, repeatTimes: Int?): RepeatProgress =
            if (value <= 0) {
                RepeatProgress(0, repeatLength, 1, repeatTimes)
            } else {
                RepeatProgress((value - 1) % repeatLength + 1, repeatLength, (value - 1) / repeatLength + 1, repeatTimes)
            }

        /** The total for "row [inRepeat] of repeat [repeat]". */
        fun total(inRepeat: Int, repeat: Int, repeatLength: Int): Int =
            if (inRepeat <= 0 && repeat <= 1) 0 else (repeat.coerceAtLeast(1) - 1) * repeatLength + inRepeat.coerceIn(1, repeatLength)
    }
}

/** Null for a plain counter. */
val Counter.repeatProgress: RepeatProgress?
    get() = repeatLength?.takeIf { it > 0 }?.let { RepeatProgress.of(value, it, repeatTimes) }

/** Counters for each pattern. Every change saves straight away and can be undone, even after a restart. */
class CounterRepository(
    private val dao: CounterDao,
    private val patternDao: PatternDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun observeCounters(patternId: Long): Flow<List<Counter>> = dao.observeCounters(patternId)

    fun observeCanUndo(patternId: Long): Flow<Boolean> = dao.observeCanUndo(patternId)

    suspend fun add(patternId: Long, name: String, repeatLength: Int? = null, repeatTimes: Int? = null): Long =
        dao.insert(
            Counter(
                patternId = patternId,
                name = cleanName(name),
                position = dao.nextPosition(patternId),
                updatedAt = clock(),
                repeatLength = repeatLength?.takeIf { it > 0 },
                repeatTimes = repeatTimes?.takeIf { it > 0 && repeatLength != null },
            ),
        )

    /** The pattern's first counter, creating a "Row" counter if it has none. */
    suspend fun mainCounter(patternId: Long, defaultName: String): Counter =
        dao.getCounters(patternId).firstOrNull() ?: dao.getCounter(add(patternId, defaultName))!!

    suspend fun increment(counterId: Long) = changeBy(counterId, +1)

    suspend fun decrement(counterId: Long) = changeBy(counterId, -1)

    /** Sets the total directly, e.g. when picking a project back up at row 37. */
    suspend fun setValue(counterId: Long, value: Int) {
        val counter = dao.getCounter(counterId) ?: return
        if (dao.change(counterId, value, clock(), KEEP_HISTORY) != null && value > counter.value) markStarted(counter.patternId)
    }

    /**
     * For a row tapped in the instructions: the main counter follows that row's [section] from
     * now on and jumps to [value] (undoable like any other change).
     */
    suspend fun jumpToRow(patternId: Long, defaultName: String, value: Int, section: Int) {
        val counter = mainCounter(patternId, defaultName)
        dao.setRowSection(counter.id, section)
        setValue(counter.id, value)
    }

    suspend fun reset(counterId: Long) {
        dao.change(counterId, 0, clock(), KEEP_HISTORY)
    }

    suspend fun undo(patternId: Long): Boolean = dao.undo(patternId, clock())

    suspend fun updateDetails(counterId: Long, name: String, repeatLength: Int?, repeatTimes: Int?) {
        val length = repeatLength?.takeIf { it > 0 }
        dao.updateDetails(counterId, cleanName(name), length, repeatTimes?.takeIf { it > 0 && length != null }, clock())
    }

    suspend fun delete(counterId: Long) = dao.delete(counterId)

    /** Moves a counter one place up (-1) or down (+1) among its pattern's counters. */
    suspend fun move(counterId: Long, by: Int) {
        val counter = dao.getCounter(counterId) ?: return
        val ids = dao.getCounters(counter.patternId).map { it.id }.toMutableList()
        val from = ids.indexOf(counterId)
        val to = (from + by).coerceIn(0, ids.lastIndex)
        if (from == to) return
        ids.add(to, ids.removeAt(from))
        dao.reorder(ids)
    }

    private suspend fun changeBy(counterId: Long, delta: Int) {
        val counter = dao.getCounter(counterId) ?: return
        val changed = dao.change(counterId, counter.value + delta, clock(), KEEP_HISTORY) != null
        if (changed && delta > 0) markStarted(counter.patternId)
    }

    /** Counting on a "Want to make" pattern means it's been started. */
    private suspend fun markStarted(patternId: Long) {
        val pattern = patternDao.getPattern(patternId) ?: return
        if (pattern.status == PatternStatus.WANT_TO_MAKE) patternDao.setStatus(patternId, PatternStatus.IN_PROGRESS, clock())
    }

    private fun cleanName(name: String) = name.trim().replace(Regex("""\s+"""), " ").take(MAX_NAME_LENGTH).ifEmpty { DEFAULT_NAME }

    private companion object {
        /** Undo steps kept per pattern; older ones are dropped. */
        const val KEEP_HISTORY = 200
        const val MAX_NAME_LENGTH = 30
        const val DEFAULT_NAME = "Counter"
    }
}
