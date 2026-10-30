package io.github.juliegaunt.chainone.data.importer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/** A downloaded (or picked) pattern waiting on the review screen. Nothing is in the library until it's saved. */
data class StagedImport(
    /** Name of its folder under the staging root. */
    val id: String,
    val sourceUrl: String,
    val title: String = "",
    val designer: String = "",
    val hookSize: String = "",
    val yarnWeight: String = "",
    val materials: String = "",
    val instructions: String = "",
    val foundInstructions: Boolean = false,
    /** False when the page couldn't be downloaded and only the link is being saved. */
    val downloaded: Boolean = true,
    val coverFile: File? = null,
    /** Folder holding the saved copy of the page (`index.html` plus its photos). */
    val snapshotDir: File? = null,
    /** Set when the import is a PDF (picked, shared, or downloaded from a link to one). */
    val pdfFile: File? = null,
    /** The PDF's name as shown on the pattern page, e.g. "Granny Square". */
    val pdfName: String = "",
)

/**
 * Keeps imports under review in the app's cache folder (`cache/imports/<id>/`), so they survive
 * the app being closed mid-review but never clutter the library. Saving moves the files into
 * the pattern's own folder; discarding deletes them.
 */
class ImportStaging(
    private val root: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun newFolder(): File = withContext(Dispatchers.IO) {
        File(root, UUID.randomUUID().toString()).apply {
            if (!mkdirs()) throw IOException("Could not create $this")
        }
    }

    suspend fun save(staged: StagedImport) = withContext(Dispatchers.IO) {
        val json = JSONObject()
            .put("sourceUrl", staged.sourceUrl)
            .put("title", staged.title)
            .put("designer", staged.designer)
            .put("hookSize", staged.hookSize)
            .put("yarnWeight", staged.yarnWeight)
            .put("materials", staged.materials)
            .put("instructions", staged.instructions)
            .put("foundInstructions", staged.foundInstructions)
            .put("downloaded", staged.downloaded)
            .put("cover", staged.coverFile?.name ?: JSONObject.NULL)
            .put("snapshot", staged.snapshotDir?.name ?: JSONObject.NULL)
            .put("pdf", staged.pdfFile?.name ?: JSONObject.NULL)
            .put("pdfName", staged.pdfName)
        File(folder(staged.id), MANIFEST).writeText(json.toString())
    }

    suspend fun load(id: String): StagedImport? = withContext(Dispatchers.IO) {
        val folder = folder(id)
        val manifest = File(folder, MANIFEST)
        if (!manifest.isFile) return@withContext null
        try {
            val json = JSONObject(manifest.readText())
            StagedImport(
                id = id,
                sourceUrl = json.getString("sourceUrl"),
                title = json.optString("title"),
                designer = json.optString("designer"),
                hookSize = json.optString("hookSize"),
                yarnWeight = json.optString("yarnWeight"),
                materials = json.optString("materials"),
                instructions = json.optString("instructions"),
                foundInstructions = json.optBoolean("foundInstructions"),
                downloaded = json.optBoolean("downloaded", true),
                coverFile = json.optFileIn(folder, "cover"),
                snapshotDir = json.optFileIn(folder, "snapshot"),
                pdfFile = json.optFileIn(folder, "pdf"),
                pdfName = json.optString("pdfName"),
            )
        } catch (e: JSONException) {
            null
        }
    }

    suspend fun discard(id: String) = withContext(Dispatchers.IO) {
        folder(id).deleteRecursively()
    }

    /** Deletes reviews that were abandoned (for example when the phone restarted mid-review). */
    suspend fun discardStale(maxAgeMillis: Long = STALE_AFTER_MILLIS) = withContext(Dispatchers.IO) {
        val cutoff = clock() - maxAgeMillis
        root.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
    }

    /** Ids are folder names we created; anything else (like "../x") is rejected. */
    private fun folder(id: String): File {
        require(id.isNotEmpty() && id.none { it == '/' || it == '\\' } && id != "." && id != "..") { "Bad import id" }
        return File(root, id)
    }

    private fun JSONObject.optFileIn(folder: File, key: String): File? =
        if (isNull(key)) null else File(folder, getString(key)).takeIf { it.exists() }

    private companion object {
        const val MANIFEST = "import.json"
        const val STALE_AFTER_MILLIS = 24 * 60 * 60 * 1000L
    }
}
