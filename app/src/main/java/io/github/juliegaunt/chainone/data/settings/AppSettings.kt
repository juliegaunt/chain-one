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

    /** The phone's volume buttons count up and down while a counter is showing. Off unless chosen. */
    private val _countWithVolumeKeys = MutableStateFlow(prefs.getBoolean(KEY_VOLUME_KEYS, false))
    val countWithVolumeKeys: StateFlow<Boolean> = _countWithVolumeKeys.asStateFlow()

    fun setCountWithVolumeKeys(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_VOLUME_KEYS, enabled) }
        _countWithVolumeKeys.value = enabled
    }

    /** The screen stays on while a counter is showing, so it doesn't lock mid-row. */
    private val _keepScreenOn = MutableStateFlow(prefs.getBoolean(KEY_KEEP_SCREEN_ON, true))
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    fun setKeepScreenOn(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_KEEP_SCREEN_ON, enabled) }
        _keepScreenOn.value = enabled
    }

    /** All the settings at once, for backups. */
    data class Snapshot(val libraryLayout: LibraryLayout, val countWithVolumeKeys: Boolean, val keepScreenOn: Boolean)

    fun snapshot() = Snapshot(libraryLayout.value, countWithVolumeKeys.value, keepScreenOn.value)

    fun restore(snapshot: Snapshot) {
        setLibraryLayout(snapshot.libraryLayout)
        setCountWithVolumeKeys(snapshot.countWithVolumeKeys)
        setKeepScreenOn(snapshot.keepScreenOn)
    }

    private companion object {
        const val KEY_LIBRARY_LAYOUT = "library_layout"
        const val KEY_VOLUME_KEYS = "count_with_volume_keys"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
    }
}
