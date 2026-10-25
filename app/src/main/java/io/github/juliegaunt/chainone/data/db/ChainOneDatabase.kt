package io.github.juliegaunt.chainone.data.db

import android.content.Context
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
    version = 1,
    exportSchema = true,
)
abstract class ChainOneDatabase : RoomDatabase() {

    abstract fun patternDao(): PatternDao

    companion object {
        const val FILE_NAME = "chain-one.db"

        fun build(context: Context): ChainOneDatabase =
            Room.databaseBuilder(context, ChainOneDatabase::class.java, FILE_NAME).build()
    }
}
