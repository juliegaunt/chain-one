package io.github.juliegaunt.chainone.ui.pattern

import kotlinx.coroutines.flow.update
import java.io.IOException
import io.github.juliegaunt.chainone.data.importer.SavedPageEditor
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.juliegaunt.chainone.appContainer
import io.github.juliegaunt.chainone.data.category.CategoryMatcher
import io.github.juliegaunt.chainone.data.db.Category
import io.github.juliegaunt.chainone.data.db.Pattern
import io.github.juliegaunt.chainone.data.db.PatternAttachment
import io.github.juliegaunt.chainone.data.db.PatternStatus
import io.github.juliegaunt.chainone.data.db.RowMarker
import io.github.juliegaunt.chainone.data.db.Tag
import io.github.juliegaunt.chainone.data.files.FileStore
import io.github.juliegaunt.chainone.data.repository.CategoryRepository
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
    /** Goes up whenever the saved page's file changes, so the Web page tab reloads it. */
    val savedPageVersion: Int = 0,
    /** Parts of the saved page that are hidden (promotions, or pictures the person hid). */
    val hiddenOnPage: Int = 0,
    val pdfs: List<PatternAttachment> = emptyList(),
    /** The library section the pattern is in; null is "Other". */
    val category: Category? = null,
    /** False until the first category is made; the pattern page then doesn't mention categories. */
    val hasCategories: Boolean = false,
    val isLoading: Boolean = true,
    /** Set once the pattern has been deleted, so the screen can close itself. */
    val isDeleted: Boolean = false,
)

/** One pattern with its tags and PDFs, for the pattern page. */
class PatternDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: PatternRepository,
    private val pdfRepository: PdfRepository,
    categoryRepository: CategoryRepository,
    private val fileStore: FileStore,
) : ViewModel() {

    private val patternId: Long = checkNotNull(savedStateHandle[ARG_PATTERN_ID])

    /** What this screen changes itself: whether the pattern was deleted, and its saved page's edits. */
    private data class Local(val isDeleted: Boolean = false, val pageVersion: Int = 0, val hiddenOnPage: Int = 0)

    private val local = MutableStateFlow(Local())

    val uiState: StateFlow<PatternDetailUiState> = combine(
        repository.observePattern(patternId),
        repository.observeTagsFor(patternId),
        pdfRepository.observePdfs(patternId),
        categoryRepository.observeCategories(),
        local,
    ) { pattern, tags, pdfs, categories, local ->
        PatternDetailUiState(
            pattern = pattern,
            cover = fileStore.coverFile(pattern?.coverImagePath),
            tags = tags,
            savedPage = pattern?.snapshotPath?.let(fileStore::resolve),
            savedPageVersion = local.pageVersion,
            hiddenOnPage = local.hiddenOnPage,
            pdfs = pdfs,
            category = pattern?.let { CategoryMatcher.categoryFor(it.title, it.categoryId, categories) },
            hasCategories = categories.isNotEmpty(),
            isLoading = false,
            isDeleted = local.isDeleted,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PatternDetailUiState())

    init {
        viewModelScope.launch { repository.markOpened(patternId) }
        // Pages saved before promotions were hidden on import get tidied the first time they're opened.
        editSavedPage { SavedPageEditor.tidyOnce(it) }
    }

    /** Hides a picture on the saved page, by its file name (such as "image-3.jpg"). */
    fun hidePicture(fileName: String) = editSavedPage { SavedPageEditor.hidePicture(it, fileName) }

    fun showHiddenOnPage() = editSavedPage { SavedPageEditor.showAll(it) }

    private fun editSavedPage(change: suspend (File) -> Boolean) {
        viewModelScope.launch {
            val page = repository.getPattern(patternId)?.snapshotPath?.let(fileStore::resolve) ?: return@launch
            val changed = try {
                change(page)
            } catch (e: IOException) {
                false
            }
            val hidden = SavedPageEditor.hiddenCount(page)
            local.update { it.copy(pageVersion = it.pageVersion + if (changed) 1 else 0, hiddenOnPage = hidden) }
        }
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
            local.update { it.copy(isDeleted = true) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer
                PatternDetailViewModel(createSavedStateHandle(), container.patternRepository, container.pdfRepository, container.categoryRepository, container.fileStore)
            }
        }
    }
}
