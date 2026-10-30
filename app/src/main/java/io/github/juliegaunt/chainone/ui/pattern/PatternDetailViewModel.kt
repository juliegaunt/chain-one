package io.github.juliegaunt.chainone.ui.pattern

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.juliegaunt.chainone.appContainer
import io.github.juliegaunt.chainone.data.db.Pattern
import io.github.juliegaunt.chainone.data.db.PatternAttachment
import io.github.juliegaunt.chainone.data.db.PatternStatus
import io.github.juliegaunt.chainone.data.db.RowMarker
import io.github.juliegaunt.chainone.data.db.Tag
import io.github.juliegaunt.chainone.data.files.FileStore
import io.github.juliegaunt.chainone.data.repository.PatternRepository
import io.github.juliegaunt.chainone.data.repository.PdfRepository
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
    val pdfs: List<PatternAttachment> = emptyList(),
    val isLoading: Boolean = true,
    /** Set once the pattern has been deleted, so the screen can close itself. */
    val isDeleted: Boolean = false,
)

/** One pattern with its tags and PDFs, for the pattern page. */
class PatternDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: PatternRepository,
    private val pdfRepository: PdfRepository,
    private val fileStore: FileStore,
) : ViewModel() {

    private val patternId: Long = checkNotNull(savedStateHandle[ARG_PATTERN_ID])
    private val isDeleted = MutableStateFlow(false)

    val uiState: StateFlow<PatternDetailUiState> = combine(
        repository.observePattern(patternId),
        repository.observeTagsFor(patternId),
        pdfRepository.observePdfs(patternId),
        isDeleted,
    ) { pattern, tags, pdfs, deleted ->
        PatternDetailUiState(
            pattern = pattern,
            cover = fileStore.coverFile(pattern?.coverImagePath),
            tags = tags,
            savedPage = pattern?.snapshotPath?.let(fileStore::resolve),
            pdfs = pdfs,
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

    fun pdfFile(pdf: PatternAttachment): File = fileStore.resolve(pdf.filePath)

    fun setPdfPage(pdfId: Long, page: Int) {
        viewModelScope.launch { pdfRepository.setPage(pdfId, page) }
    }

    fun setRowMarker(pdfId: Long, marker: RowMarker?) {
        viewModelScope.launch { pdfRepository.setRowMarker(pdfId, marker) }
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
                PatternDetailViewModel(createSavedStateHandle(), container.patternRepository, container.pdfRepository, container.fileStore)
            }
        }
    }
}
