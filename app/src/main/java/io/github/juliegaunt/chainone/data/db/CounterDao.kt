package io.github.juliegaunt.chainone.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CounterDao {

    @Query("SELECT * FROM counters WHERE patternId = :patternId ORDER BY position, id")
    fun observeCounters(patternId: Long): Flow<List<Counter>>

    @Query("SELECT * FROM counters WHERE patternId = :patternId ORDER BY position, id")
    suspend fun getCounters(patternId: Long): List<Counter>

    @Query("SELECT * FROM counters WHERE id = :id")
    suspend fun getCounter(id: Long): Counter?

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM counters WHERE patternId = :patternId")
    suspend fun nextPosition(patternId: Long): Int

    @Insert
    suspend fun insert(counter: Counter): Long

    @Query("UPDATE counters SET name = :name, repeatLength = :repeatLength, repeatTimes = :repeatTimes, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateDetails(id: Long, name: String, repeatLength: Int?, repeatTimes: Int?, updatedAt: Long)

    @Query("UPDATE counters SET value = :value, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setValue(id: Long, value: Int, updatedAt: Long)

    @Query("UPDATE counters SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Int)

    /** Its undo history goes with it (the history rows cascade). */
    @Query("DELETE FROM counters WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertHistory(entry: CounterHistory): Long

    @Query(
        """
        SELECT counter_history.* FROM counter_history
        JOIN counters ON counters.id = counter_history.counterId
        WHERE counters.patternId = :patternId
        ORDER BY counter_history.id DESC LIMIT 1
        """,
    )
    suspend fun latestHistory(patternId: Long): CounterHistory?

    @Query(
        """
        SELECT EXISTS (SELECT 1 FROM counter_history JOIN counters ON counters.id = counter_history.counterId
        WHERE counters.patternId = :patternId)
        """,
    )
    fun observeCanUndo(patternId: Long): Flow<Boolean>

    @Query("DELETE FROM counter_history WHERE id = :id")
    suspend fun deleteHistory(id: Long)

    /** Keeps only the newest [keep] undo steps for a pattern. */
    @Query(
        """
        DELETE FROM counter_history WHERE counterId IN (SELECT id FROM counters WHERE patternId = :patternId)
        AND id NOT IN (
            SELECT counter_history.id FROM counter_history
            JOIN counters ON counters.id = counter_history.counterId
            WHERE counters.patternId = :patternId
            ORDER BY counter_history.id DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimHistory(patternId: Long, keep: Int)

    /**
     * Sets a counter to [value] (never below 0), remembering the old value for undo.
     * Returns the old value, or null if nothing changed.
     */
    @Transaction
    suspend fun change(counterId: Long, value: Int, now: Long, keepHistory: Int): Int? {
        val counter = getCounter(counterId) ?: return null
        val newValue = value.coerceAtLeast(0)
        if (newValue == counter.value) return null
        insertHistory(CounterHistory(counterId = counterId, previousValue = counter.value, createdAt = now))
        setValue(counterId, newValue, now)
        trimHistory(counter.patternId, keepHistory)
        return counter.value
    }

    /** Takes back the pattern's most recent counter change, whichever counter it was on. */
    @Transaction
    suspend fun undo(patternId: Long, now: Long): Boolean {
        val entry = latestHistory(patternId) ?: return false
        setValue(entry.counterId, entry.previousValue, now)
        deleteHistory(entry.id)
        return true
    }

    @Transaction
    suspend fun reorder(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { index, id -> setPosition(id, index) }
    }
}
