package io.github.juliegaunt.chainone.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Room stores enums by constant name, so renaming a constant needs a database migration.

enum class SourceType { MANUAL, URL, PDF }

enum class PatternStatus { WANT_TO_MAKE, IN_PROGRESS, FINISHED }

/**
 * A crochet pattern. All file paths are relative to the app's private files directory
 * (see [io.github.juliegaunt.chainone.data.files.FileStore]) so backups stay portable.
 *
 * [instructions] is plain text with light markup: lines starting with `## ` are headings
 * and lines starting with `- ` are bullets.
 */
@Entity(
    tableName = "patterns",
    indices = [Index("status"), Index("isFavorite"), Index("updatedAt")],
)
data class Pattern(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val designer: String = "",
    val sourceUrl: String? = null,
    val sourceType: SourceType = SourceType.MANUAL,
    val hookSize: String = "",
    val yarnWeight: String = "",
    val materials: String = "",
    val instructions: String = "",
    val notes: String = "",
    val status: PatternStatus = PatternStatus.WANT_TO_MAKE,
    val isFavorite: Boolean = false,
    val coverImagePath: String? = null,
    /** Cleaned copy of the original web page, shown when URL extraction is poor. */
    val snapshotPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long? = null,
)

@Entity(
    tableName = "pattern_images",
    foreignKeys = [
        ForeignKey(
            entity = Pattern::class,
            parentColumns = ["id"],
            childColumns = ["patternId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("patternId")],
)
data class PatternImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patternId: Long,
    val filePath: String,
    val position: Int,
)

/** A PDF belonging to a pattern. A purchased pattern can come with several. */
@Entity(
    tableName = "pattern_attachments",
    foreignKeys = [
        ForeignKey(
            entity = Pattern::class,
            parentColumns = ["id"],
            childColumns = ["patternId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("patternId")],
)
data class PatternAttachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patternId: Long,
    val filePath: String,
    val displayName: String,
    /** Zero-based page the viewer was last showing. */
    val lastPage: Int = 0,
    val addedAt: Long,
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class Tag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
)

@Entity(
    tableName = "pattern_tags",
    primaryKeys = ["patternId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = Pattern::class,
            parentColumns = ["id"],
            childColumns = ["patternId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class PatternTag(
    val patternId: Long,
    val tagId: Long,
)

@Entity(
    tableName = "counters",
    foreignKeys = [
        ForeignKey(
            entity = Pattern::class,
            parentColumns = ["id"],
            childColumns = ["patternId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("patternId")],
)
/**
 * A row/stitch counter. [value] is always the total count. A repeat counter (with
 * [repeatLength]) shows it as "3 of 4, repeat 2 of 5" instead; see `RepeatProgress`.
 */
data class Counter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val patternId: Long,
    val name: String,
    val value: Int = 0,
    val position: Int,
    val updatedAt: Long,
    /** Counts this many, then starts over at 1 (null for a plain counter). Added in schema v2. */
    val repeatLength: Int? = null,
    /** How many repeats the pattern asks for, if it says (null if not). Added in schema v2. */
    val repeatTimes: Int? = null,
    /**
     * Which run of rows in the instructions this counter follows (the piece or size the
     * person tapped a row in); null until they do. Added in schema v3.
     */
    val rowSection: Int? = null,
)

/** One entry per counter change, so undo works even after the app has been closed. */
@Entity(
    tableName = "counter_history",
    foreignKeys = [
        ForeignKey(
            entity = Counter::class,
            parentColumns = ["id"],
            childColumns = ["counterId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("counterId")],
)
data class CounterHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val counterId: Long,
    val previousValue: Int,
    val createdAt: Long,
)
