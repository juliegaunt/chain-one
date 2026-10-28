package io.github.juliegaunt.chainone.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import io.github.juliegaunt.chainone.data.db.ChainOneDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Builds a database exactly as version 1 of the app left it (from the committed schema
 * file), then opens it with the current app, which must upgrade it without losing anything.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    private fun createVersion1Database() {
        val schema = JSONObject(File("schemas/${ChainOneDatabase::class.qualifiedName}/1.json").readText())
            .getJSONObject("database")
        context.deleteDatabase(name)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                        val indices = entity.optJSONArray("indices") ?: continue
                        for (j in 0 until indices.length()) {
                            db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                        }
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                }

                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase.apply {
            execSQL("INSERT INTO patterns (id, title, designer, sourceType, hookSize, yarnWeight, materials, instructions, notes, status, isFavorite, createdAt, updatedAt) VALUES (1, 'Blanket', '', 'MANUAL', '', '', '', '', '', 'IN_PROGRESS', 1, 1, 1)")
            execSQL("INSERT INTO counters (id, patternId, name, value, position, updatedAt) VALUES (1, 1, 'Row', 12, 0, 1)")
            execSQL("INSERT INTO counter_history (counterId, previousValue, createdAt) VALUES (1, 11, 1)")
            close()
        }
    }

    @Test
    fun version1LibraryOpensInTheCurrentVersionWithNothingLost() = runTest {
        createVersion1Database()

        val db = Room.databaseBuilder(context, ChainOneDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            val pattern = db.patternDao().getPattern(1)!!
            assertEquals("Blanket", pattern.title)
            assertEquals(true, pattern.isFavorite)

            val counter = db.counterDao().observeCounters(1).first().single()
            assertEquals("Row", counter.name)
            assertEquals(12, counter.value)
            assertNull(counter.repeatLength)
            assertNull(counter.repeatTimes)
            assertNull(counter.rowSection)
            assertEquals(11, db.counterDao().latestHistory(1)!!.previousValue)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
