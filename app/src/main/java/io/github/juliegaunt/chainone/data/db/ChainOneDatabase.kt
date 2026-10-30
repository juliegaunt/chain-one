package io.github.juliegaunt.chainone.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Pattern::class,
        PatternImage::class,
        PatternAttachment::class,
        Tag::class,
        PatternTag::class,
        Counter::class,
        CounterHistory::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        // v2: repeat counters (two new, empty columns on `counters`).
        AutoMigration(from = 1, to = 2),
        // v3: which section of the instructions a counter follows (one new, empty column).
        AutoMigration(from = 2, to = 3),
        // v4: the row marker on PDFs (three new, empty columns on `pattern_attachments`).
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class ChainOneDatabase : RoomDatabase() {

    abstract fun patternDao(): PatternDao

    abstract fun counterDao(): CounterDao

    abstract fun pdfDao(): PdfDao

    abstract fun backupDao(): BackupDao

    companion object {
        const val FILE_NAME = "chain-one.db"

        fun build(context: Context): ChainOneDatabase =
            Room.databaseBuilder(context, ChainOneDatabase::class.java, FILE_NAME).build()
    }
}
