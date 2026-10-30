package io.github.juliegaunt.chainone.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.juliegaunt.chainone.appContainer
import io.github.juliegaunt.chainone.data.db.LibraryFilter
import io.github.juliegaunt.chainone.data.db.PatternSummary
import io.github.juliegaunt.chainone.data.db.PatternStatus
import io.github.juliegaunt.chainone.data.db.TagCount
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class LibraryItem(val pattern: PatternSummary, val cover: File?)

data class LibraryUiState(
    val items: List<LibraryItem> = emptyList(),
    val filter: LibraryFilter = LibraryFilter(),
    /** True while the search box is open in the top bar. */
    val isSearching: Boolean = false,
    val tags: List<TagCount> = emptyList(),
    /** False when there are no patterns at all, as opposed to none matching the filters. */
    val hasPatterns: Boolean = false,
    val layout: LibraryLayout = LibraryLayout.GRID,
    val isLoading: Boolean = true,
)

class LibraryViewModel(
    private val repository: PatternRepository,
    private val fileStore: FileStore,
    private val settings: AppSettings,
) : ViewModel() {

    // Kept for as long as the app is open; it starts unfiltered next time.
    private val filter = MutableStateFlow(LibraryFilter())
    private val isSearching = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val patterns = filter.flatMapLatest { repository.observeLibrary(it) }

    private val controls = combine(filter, isSearching) { filter, searching -> filter to searching }

    val uiState: StateFlow<LibraryUiState> = combine(
        patterns,
        controls,
        repository.observeTags(),
        repository.observePatternCount(),
        settings.libraryLayout,
    ) { patterns, (filter, searching), tags, count, layout ->
        LibraryUiState(
            items = patterns.map { LibraryItem(it, fileStore.coverFile(it.coverImagePath)) },
            filter = filter,
            isSearching = searching,
            tags = tags,
            hasPatterns = count > 0,
            layout = layout,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    init {
        // A selected tag can disappear (renamed into another, deleted, or no longer used).
        viewModelScope.launch {
            repository.observeTags().collect { tags ->
                val existing = tags.map { it.id }.toSet()
                filter.update { it.copy(tagIds = it.tagIds intersect existing) }
            }
        }
    }

    /**
     * For a tag tapped on a pattern's page: just the patterns with that tag, nothing else filtered.
     * The filters live in the search area, so it opens to show the tag.
     */
    fun showOnlyTag(tagId: Long) {
        filter.value = LibraryFilter(tagIds = setOf(tagId))
        isSearching.value = true
    }

    fun openSearch() {
        isSearching.value = true
    }

    /**
     * Closing the search area clears what was typed and the status and tag filters with it, since
     * they're only shown there. Favorites stays: its heart is always in the top bar.
     */
    fun closeSearch() {
        isSearching.value = false
        filter.update { LibraryFilter(favoritesOnly = it.favoritesOnly) }
    }

    fun updateSearch(text: String) {
        filter.update { it.copy(search = text) }
    }

    fun toggleStatus(status: PatternStatus) {
        filter.update { it.copy(statuses = if (status in it.statuses) it.statuses - status else it.statuses + status) }
    }

    fun toggleTag(tagId: Long) {
        filter.update { it.copy(tagIds = if (tagId in it.tagIds) it.tagIds - tagId else it.tagIds + tagId) }
    }

    fun clearTags() {
        filter.update { it.copy(tagIds = emptySet()) }
    }

    fun toggleFavoritesOnly() {
        filter.update { it.copy(favoritesOnly = !it.favoritesOnly) }
    }

    fun clearFilters() {
        filter.value = LibraryFilter()
    }

    fun renameTag(tagId: Long, name: String) {
        viewModelScope.launch { repository.renameTag(tagId, name) }
    }

    fun deleteTag(tagId: Long) {
        viewModelScope.launch { repository.deleteTag(tagId) }
    }

    fun toggleLayout() {
        settings.setLibraryLayout(
            if (settings.libraryLayout.value == LibraryLayout.GRID) LibraryLayout.LIST else LibraryLayout.GRID,
        )
    }

    fun toggleFavorite(pattern: PatternSummary) {
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
