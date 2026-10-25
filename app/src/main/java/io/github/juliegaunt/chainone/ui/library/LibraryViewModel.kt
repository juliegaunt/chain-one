package io.github.juliegaunt.chainone.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.juliegaunt.chainone.appContainer
import io.github.juliegaunt.chainone.data.db.Pattern
import io.github.juliegaunt.chainone.data.files.FileStore
import io.github.juliegaunt.chainone.data.repository.PatternRepository
import io.github.juliegaunt.chainone.data.settings.AppSettings
import io.github.juliegaunt.chainone.data.settings.LibraryLayout
import io.github.juliegaunt.chainone.ui.components.coverFile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class LibraryItem(val pattern: Pattern, val cover: File?)

data class LibraryUiState(
    val items: List<LibraryItem> = emptyList(),
    val favoritesOnly: Boolean = false,
    val layout: LibraryLayout = LibraryLayout.GRID,
    val isLoading: Boolean = true,
)

class LibraryViewModel(
    private val repository: PatternRepository,
    private val fileStore: FileStore,
    private val settings: AppSettings,
) : ViewModel() {

    private val favoritesOnly = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<LibraryUiState> = combine(
        favoritesOnly.flatMapLatest { repository.observeLibrary(it) },
        favoritesOnly,
        settings.libraryLayout,
    ) { patterns, favorites, layout ->
        LibraryUiState(
            items = patterns.map { LibraryItem(it, fileStore.coverFile(it.coverImagePath)) },
            favoritesOnly = favorites,
            layout = layout,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun toggleFavoritesOnly() {
        favoritesOnly.value = !favoritesOnly.value
    }

    fun toggleLayout() {
        settings.setLibraryLayout(
            if (settings.libraryLayout.value == LibraryLayout.GRID) LibraryLayout.LIST else LibraryLayout.GRID,
        )
    }

    fun toggleFavorite(pattern: Pattern) {
        viewModelScope.launch { repository.setFavorite(pattern.id, !pattern.isFavorite) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer
                LibraryViewModel(container.patternRepository, container.fileStore, container.settings)
            }
        }
    }
}
