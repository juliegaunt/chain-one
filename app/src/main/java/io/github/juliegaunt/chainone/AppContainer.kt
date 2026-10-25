package io.github.juliegaunt.chainone

import android.content.Context
import io.github.juliegaunt.chainone.data.db.ChainOneDatabase
import io.github.juliegaunt.chainone.data.files.FileStore
import io.github.juliegaunt.chainone.data.repository.PatternRepository
import io.github.juliegaunt.chainone.data.settings.AppSettings

/** Creates the app's long-lived objects once. This is all the dependency injection the app needs. */
class AppContainer(context: Context) {
    val database: ChainOneDatabase = ChainOneDatabase.build(context)
    val fileStore = FileStore(context.filesDir, context.contentResolver)
    val patternRepository = PatternRepository(database.patternDao(), fileStore)
    val settings = AppSettings(context)
}
