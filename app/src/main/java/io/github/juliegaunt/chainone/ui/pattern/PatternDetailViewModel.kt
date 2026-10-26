package io.github.juliegaunt.chainone.ui.pattern

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.juliegaunt.chainone.appContainer
import io.github.juliegaunt.chainone.data.db.Pattern
import io.github.juliegaunt.chainone.data.db.PatternStatus
import io.github.juliegaunt.chainone.data.db.Tag
import io.github.juliegaunt.chainone.data.files.FileStore
import io.github.juliegaunt.chainone.data.repository.PatternRepository
import io.github.juliegaunt.chainone.ui.components.coverFile
import io.github.juliegaunt.chainone.ui.navigation.ARG_PATTERN_ID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class PatternDetailUiState(
    val pattern: Pattern? = null,
    val cover: File? = null,
    val tags: List<Tag> = emptyList(),
    /** The saved copy of the pattern's web page, if it was imported from one. */
    val savedPage: File? = null,
    val isLoading: Boolean = true,
    /** Set once the pattern has been deleted, so the screen can close itself. */
    val isDeleted: Boolean = false,
)

class PatternDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: PatternRepository,
    private val fileStore: FileStore,
) : ViewModel() {

    private val patternId: Long = checkNotNull(savedStateHandle[ARG_PATTERN_ID])
    private val isDeleted = MutableStateFlow(false)

    val uiState: StateFlow<PatternDetailUiState> = combine(
        repository.observePattern(patternId),
        repository.observeTagsFor(patternId),
        isDeleted,
    ) { pattern, tags, deleted ->
        PatternDetailUiState(
            pattern = pattern,
            cover = fileStore.coverFile(pattern?.coverImagePath),
            tags = tags,
            savedPage = pattern?.snapshotPath?.let(fileStore::resolve),
            isLoading = false,
            isDeleted = deleted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatternDetailUiState())

    init {
        viewModelScope.launch { repository.markOpened(patternId) }
    }

    fun toggleFavorite() {
        val pattern = uiState.value.pattern ?: return
        viewModelScope.launch { repository.setFavorite(pattern.id, !pattern.isFavorite) }
    }

    fun setStatus(status: PatternStatus) {
        viewModelScope.launch { repository.setStatus(patternId, status) }
    }

    fun delete() {
        viewModelScope.launch {
            repository.delete(patternId)
            isDeleted.value = true
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer
                PatternDetailViewModel(createSavedStateHandle(), container.patternRepository, container.fileStore)
            }
        }
    }
}
