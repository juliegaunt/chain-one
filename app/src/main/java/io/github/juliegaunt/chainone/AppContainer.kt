package io.github.juliegaunt.chainone

import android.content.Context
import io.github.juliegaunt.chainone.data.backup.LibraryBackup
import io.github.juliegaunt.chainone.data.db.ChainOneDatabase
import io.github.juliegaunt.chainone.data.files.FileStore
import io.github.juliegaunt.chainone.data.importer.ImportStaging
import io.github.juliegaunt.chainone.data.importer.JsoupWebClient
import io.github.juliegaunt.chainone.data.importer.PatternImporter
import io.github.juliegaunt.chainone.data.importer.PdfImporter
import io.github.juliegaunt.chainone.data.pdf.RendererPdfInspector
import io.github.juliegaunt.chainone.data.repository.CounterRepository
import io.github.juliegaunt.chainone.data.repository.PatternRepository
import io.github.juliegaunt.chainone.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Creates the app's long-lived objects once. This is all the dependency injection the app needs. */
class AppContainer(context: Context) {
    val database: ChainOneDatabase = ChainOneDatabase.build(context)
    val fileStore = FileStore(context.filesDir, context.contentResolver)
    val patternRepository = PatternRepository(database.patternDao(), fileStore)
    val counterRepository = CounterRepository(database.counterDao(), database.patternDao())
    val settings = AppSettings(context)
    val libraryBackup = LibraryBackup(
        database.backupDao(),
        context.filesDir,
        settings,
        appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
    )
    val importStaging = ImportStaging(File(context.cacheDir, "imports"))
    val pdfImporter = PdfImporter(importStaging, context.contentResolver, RendererPdfInspector())
    val patternImporter = PatternImporter(JsoupWebClient(), importStaging, pdfImporter) { host ->
        context.getString(R.string.snapshot_caption, host, DateFormat.getDateInstance(DateFormat.LONG).format(Date()))
    }

    /** For clean-up work that must finish even after the screen that started it has closed. */
    val appScope = CoroutineScope(SupervisorJob())
}
