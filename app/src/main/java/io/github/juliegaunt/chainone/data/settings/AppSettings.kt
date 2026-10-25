package io.github.juliegaunt.chainone.data.settings

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class LibraryLayout { GRID, LIST }

/** Small UI preferences, kept in SharedPreferences. */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _libraryLayout = MutableStateFlow(
        prefs.getString(KEY_LIBRARY_LAYOUT, null)
            ?.let { runCatching { LibraryLayout.valueOf(it) }.getOrNull() }
            ?: LibraryLayout.GRID,
    )
    val libraryLayout: StateFlow<LibraryLayout> = _libraryLayout.asStateFlow()

    fun setLibraryLayout(layout: LibraryLayout) {
        prefs.edit { putString(KEY_LIBRARY_LAYOUT, layout.name) }
        _libraryLayout.value = layout
    }

    private companion object {
        const val KEY_LIBRARY_LAYOUT = "library_layout"
    }
}
