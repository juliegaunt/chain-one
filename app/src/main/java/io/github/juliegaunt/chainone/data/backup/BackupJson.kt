package io.github.juliegaunt.chainone.data.backup

import io.github.juliegaunt.chainone.data.db.Category
import io.github.juliegaunt.chainone.data.db.Counter
import io.github.juliegaunt.chainone.data.db.LibrarySnapshot
import io.github.juliegaunt.chainone.data.db.Pattern
import io.github.juliegaunt.chainone.data.db.PatternAttachment
import io.github.juliegaunt.chainone.data.db.PatternImage
import io.github.juliegaunt.chainone.data.db.PatternStatus
import io.github.juliegaunt.chainone.data.db.PatternTag
import io.github.juliegaunt.chainone.data.db.SourceType
import io.github.juliegaunt.chainone.data.db.Tag
import io.github.juliegaunt.chainone.data.settings.AppSettings
import io.github.juliegaunt.chainone.data.settings.LibraryLayout
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** What a backup's `backup.json` holds. */
data class BackupContents(
    val createdAt: Long,
    val library: LibrarySnapshot,
    val settings: AppSettings.Snapshot,
    /**
     * Every file under `files/` and its size. A restore unpacks only these, no bigger, and checks
     * they all arrived: that tells a complete backup from one cut short or tampered with.
     */
    val files: Map<String, Long>,
)

/**
 * Writes and reads `backup.json`. Every field is spelled out (rather than copied from the database)
 * so a backup stays readable after the database changes: a later version only has to read the
 * fields of older backups.
 */
internal object BackupJson {

    const val FORMAT = "chain-one-backup"

    /**
     * Raise when older versions of the app couldn't restore a backup faithfully.
     * 2: categories (version 1 backups have none, and still restore).
     */
    const val VERSION = 2

    fun write(contents: BackupContents, appVersion: String): String {
        val library = contents.library
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("appVersion", appVersion)
            .put("createdAt", contents.createdAt)
            .put("settings", settingsJson(contents.settings))
            .put("patterns", JSONArray(library.patterns.map(::patternJson)))
            .put("images", JSONArray(library.images.map(::imageJson)))
            .put("attachments", JSONArray(library.attachments.map(::attachmentJson)))
            .put("tags", JSONArray(library.tags.map { JSONObject().put("id", it.id).put("name", it.name) }))
            .put("patternTags", JSONArray(library.patternTags.map { JSONObject().put("patternId", it.patternId).put("tagId", it.tagId) }))
            .put("counters", JSONArray(library.counters.map(::counterJson)))
            .put("categories", JSONArray(library.categories.map(::categoryJson)))
            .put("files", JSONArray(contents.files.map { (path, size) -> JSONObject().put("path", path).put("size", size) }))
            .toString()
    }

    /**
     * Reads a backup. Throws [BackupException] if it isn't one, is from a newer version of the app,
     * or is missing something it needs.
     */
    fun read(text: String): BackupContents {
        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw BackupException(BackupProblem.NOT_A_BACKUP, e)
        }
        if (json.optString("format") != FORMAT) throw BackupException(BackupProblem.NOT_A_BACKUP)
        if (json.optInt("version", Int.MAX_VALUE) > VERSION) throw BackupException(BackupProblem.NEWER_VERSION)
        return try {
            BackupContents(
                createdAt = json.getLong("createdAt"),
                library = LibrarySnapshot(
                    patterns = json.objects("patterns").map(::readPattern),
                    images = json.objects("images").map(::readImage),
                    attachments = json.objects("attachments").map(::readAttachment),
                    tags = json.objects("tags").map { Tag(id = it.getLong("id"), name = it.getString("name")) },
                    patternTags = json.objects("patternTags").map { PatternTag(it.getLong("patternId"), it.getLong("tagId")) },
                    counters = json.objects("counters").map(::readCounter),
                    categories = json.objects("categories").map(::readCategory),
                ),
                settings = readSettings(json.optJSONObject("settings") ?: JSONObject()),
                files = json.getJSONArray("files").let { files ->
                    List(files.length()) { files.getJSONObject(it) }.associate { it.getString("path") to it.getLong("size") }
                },
            )
        } catch (e: JSONException) {
            throw BackupException(BackupProblem.DAMAGED, e)
        } catch (e: IllegalArgumentException) {
            // An unknown status or source type.
            throw BackupException(BackupProblem.DAMAGED, e)
        }
    }

    private fun patternJson(p: Pattern) = JSONObject()
        .put("id", p.id)
        .put("title", p.title)
        .put("designer", p.designer)
        .put("sourceUrl", p.sourceUrl ?: JSONObject.NULL)
        .put("sourceType", p.sourceType.name)
        .put("hookSize", p.hookSize)
        .put("yarnWeight", p.yarnWeight)
        .put("materials", p.materials)
        .put("instructions", p.instructions)
        .put("notes", p.notes)
        .put("status", p.status.name)
        .put("isFavorite", p.isFavorite)
        .put("coverImagePath", p.coverImagePath ?: JSONObject.NULL)
        .put("snapshotPath", p.snapshotPath ?: JSONObject.NULL)
        .put("createdAt", p.createdAt)
        .put("updatedAt", p.updatedAt)
        .put("lastOpenedAt", p.lastOpenedAt ?: JSONObject.NULL)
        .put("categoryId", p.categoryId ?: JSONObject.NULL)

    private fun readPattern(o: JSONObject) = Pattern(
        id = o.getLong("id"),
        title = o.getString("title"),
        designer = o.optString("designer"),
        sourceUrl = o.stringOrNull("sourceUrl"),
        sourceType = SourceType.valueOf(o.optString("sourceType", SourceType.MANUAL.name)),
        hookSize = o.optString("hookSize"),
        yarnWeight = o.optString("yarnWeight"),
        materials = o.optString("materials"),
        instructions = o.optString("instructions"),
        notes = o.optString("notes"),
        status = PatternStatus.valueOf(o.optString("status", PatternStatus.WANT_TO_MAKE.name)),
        isFavorite = o.optBoolean("isFavorite"),
        coverImagePath = o.stringOrNull("coverImagePath"),
        snapshotPath = o.stringOrNull("snapshotPath"),
        createdAt = o.getLong("createdAt"),
        updatedAt = o.getLong("updatedAt"),
        lastOpenedAt = o.longOrNull("lastOpenedAt"),
        categoryId = o.longOrNull("categoryId"),
    )

    private fun imageJson(i: PatternImage) = JSONObject()
        .put("id", i.id)
        .put("patternId", i.patternId)
        .put("filePath", i.filePath)
        .put("position", i.position)

    private fun readImage(o: JSONObject) = PatternImage(
        id = o.getLong("id"),
        patternId = o.getLong("patternId"),
        filePath = o.getString("filePath"),
        position = o.optInt("position"),
    )

    private fun attachmentJson(a: PatternAttachment) = JSONObject()
        .put("id", a.id)
        .put("patternId", a.patternId)
        .put("filePath", a.filePath)
        .put("displayName", a.displayName)
        .put("lastPage", a.lastPage)
        .put("addedAt", a.addedAt)
        .put("markerPage", a.markerPage ?: JSONObject.NULL)
        .put("markerTop", a.markerTop?.toDouble() ?: JSONObject.NULL)
        .put("markerHeight", a.markerHeight?.toDouble() ?: JSONObject.NULL)

    private fun readAttachment(o: JSONObject) = PatternAttachment(
        id = o.getLong("id"),
        patternId = o.getLong("patternId"),
        filePath = o.getString("filePath"),
        displayName = o.getString("displayName"),
        lastPage = o.optInt("lastPage"),
        addedAt = o.getLong("addedAt"),
        markerPage = o.intOrNull("markerPage"),
        markerTop = o.doubleOrNull("markerTop")?.toFloat(),
        markerHeight = o.doubleOrNull("markerHeight")?.toFloat(),
    )

    private fun counterJson(c: Counter) = JSONObject()
        .put("id", c.id)
        .put("patternId", c.patternId)
        .put("name", c.name)
        .put("value", c.value)
        .put("position", c.position)
        .put("updatedAt", c.updatedAt)
        .put("repeatLength", c.repeatLength ?: JSONObject.NULL)
        .put("repeatTimes", c.repeatTimes ?: JSONObject.NULL)
        .put("rowSection", c.rowSection ?: JSONObject.NULL)

    private fun readCounter(o: JSONObject) = Counter(
        id = o.getLong("id"),
        patternId = o.getLong("patternId"),
        name = o.getString("name"),
        value = o.optInt("value"),
        position = o.optInt("position"),
        updatedAt = o.getLong("updatedAt"),
        repeatLength = o.intOrNull("repeatLength"),
        repeatTimes = o.intOrNull("repeatTimes"),
        rowSection = o.intOrNull("rowSection"),
    )

    private fun categoryJson(c: Category) = JSONObject()
        .put("id", c.id)
        .put("name", c.name)
        .put("extraWords", c.extraWords)
        .put("position", c.position)

    private fun readCategory(o: JSONObject) = Category(
        id = o.getLong("id"),
        name = o.getString("name"),
        extraWords = o.optString("extraWords"),
        position = o.optInt("position"),
    )

    private fun settingsJson(s: AppSettings.Snapshot) = JSONObject()
        .put("libraryLayout", s.libraryLayout.name)
        .put("countWithVolumeKeys", s.countWithVolumeKeys)
        .put("keepScreenOn", s.keepScreenOn)

    /** Settings a backup doesn't have (or an older layout name) fall back to the defaults. */
    private fun readSettings(o: JSONObject) = AppSettings.Snapshot(
        libraryLayout = LibraryLayout.entries.firstOrNull { it.name == o.optString("libraryLayout") } ?: LibraryLayout.GRID,
        countWithVolumeKeys = o.optBoolean("countWithVolumeKeys", false),
        keepScreenOn = o.optBoolean("keepScreenOn", true),
    )

    private fun JSONObject.objects(key: String): List<JSONObject> {
        val array = optJSONArray(key) ?: return emptyList()
        return List(array.length()) { array.getJSONObject(it) }
    }

    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)

    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)

    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)

    private fun JSONObject.doubleOrNull(key: String): Double? = if (isNull(key)) null else getDouble(key)
}
