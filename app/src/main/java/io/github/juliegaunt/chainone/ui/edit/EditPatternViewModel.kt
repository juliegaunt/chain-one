package io.github.juliegaunt.chainone.ui.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.appContainer
import io.github.juliegaunt.chainone.data.db.SourceType
import io.github.juliegaunt.chainone.data.files.FileStore
import io.github.juliegaunt.chainone.data.importer.ImportStaging
import io.github.juliegaunt.chainone.data.importer.PdfNames
import io.github.juliegaunt.chainone.data.importer.StagedImport
import io.github.juliegaunt.chainone.data.importer.UrlTools
import io.github.juliegaunt.chainone.data.repository.CategoryRepository
import io.github.juliegaunt.chainone.data.repository.PatternRepository
import io.github.juliegaunt.chainone.data.repository.PdfRepository
import io.github.juliegaunt.chainone.ui.components.coverFile
import io.github.juliegaunt.chainone.ui.navigation.ARG_IMPORT_ID
import io.github.juliegaunt.chainone.ui.navigation.ARG_PATTERN_ID
import io.github.juliegaunt.chainone.ui.navigation.NEW_PATTERN_ID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

private val SPACES = Regex("""\s+""")

/** The edit screen's form. Nothing is written until [save], which stores the pattern, its tags, cover and PDFs together. */
class EditPatternViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: PatternRepository,
    private val pdfRepository: PdfRepository,
    categoryRepository: CategoryRepository,
    private val fileStore: FileStore,
    private val staging: ImportStaging,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val patternId: Long = savedStateHandle[ARG_PATTERN_ID] ?: NEW_PATTERN_ID

    /** Set when reviewing a pattern imported from a website; the downloaded files wait in [staging]. */
    private val importId: String? = savedStateHandle[ARG_IMPORT_ID]
    private val isNew = patternId == NEW_PATTERN_ID
    private var staged: StagedImport? = null
    private var importAttached = false
    private var nextPdfKey = 0

    /** Null until a new pattern has been inserted, so a retry after a failed photo copy updates it. */
    private var storedId: Long? = patternId.takeUnless { isNew }

    private val _uiState = MutableStateFlow(EditPatternUiState(isNew = isNew, isLoading = !isNew || importId != null))
    val uiState: StateFlow<EditPatternUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeTags().collect { tags -> _uiState.update { it.copy(allTags = tags.map { tag -> tag.name }) } }
        }
        viewModelScope.launch {
            categoryRepository.observeCategories().collect { categories ->
                // A hand-picked category that's been deleted falls back to the title.
                _uiState.update { state ->
                    val chosen = state.form.categoryId?.takeIf { id -> categories.any { it.id == id } }
                    state.copy(categories = categories, form = state.form.copy(categoryId = chosen))
                }
            }
        }
        if (importId != null) {
            viewModelScope.launch {
                val loaded = staging.load(importId)
                staged = loaded
                val form = loaded?.let(PatternForm::from) ?: PatternForm()
                val pdf = loaded?.pdfFile?.let { PdfEntry(key = "staged", name = loaded.pdfName, stagedFile = it) }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        form = form,
                        // Compared with a blank form, so leaving without saving asks first.
                        initialForm = PatternForm(),
                        savedCover = loaded?.coverFile,
                        pdfs = listOfNotNull(pdf),
                        review = loaded?.takeIf { it.downloaded }?.let(::reviewOf),
                    )
                }
            }
        } else if (!isNew) {
            viewModelScope.launch {
                val pattern = repository.getPattern(patternId)
                val form = pattern?.let { PatternForm.from(it).copy(tags = repository.getTagNames(it.id)) } ?: PatternForm()
                val pdfs = pdfRepository.getPdfs(patternId).map { PdfEntry(key = "saved-${it.id}", name = it.displayName, savedId = it.id) }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        form = form,
                        initialForm = form,
                        savedCover = fileStore.coverFile(pattern?.coverImagePath),
                        pdfs = pdfs,
                        initialPdfs = pdfs,
                    )
                }
            }
        }
    }

    /** Typing a comma finishes a tag, like pressing Enter. */
    fun updateTagInput(text: String) {
        if (',' in text) {
            text.split(',').dropLast(1).forEach(::addTag)
            _uiState.update { it.copy(tagInput = text.substringAfterLast(',')) }
        } else {
            _uiState.update { it.copy(tagInput = text) }
        }
    }

    /** Adds a tag, using an existing tag's spelling if one matches regardless of case. */
    fun addTag(name: String) {
        val clean = name.trim().replace(SPACES, " ")
        _uiState.update { state ->
            if (clean.isEmpty()) return@update state.copy(tagInput = "")
            val spelling = state.allTags.firstOrNull { it.equals(clean, ignoreCase = true) } ?: clean
            val tags = if (state.form.tags.any { it.equals(spelling, ignoreCase = true) }) state.form.tags else state.form.tags + spelling
            state.copy(form = state.form.copy(tags = tags), tagInput = "")
        }
    }

    fun removeTag(name: String) {
        _uiState.update { it.copy(form = it.form.copy(tags = it.form.tags - name)) }
    }

    fun updateForm(change: (PatternForm) -> PatternForm) {
        _uiState.update {
            val form = change(it.form)
            it.copy(form = form, showTitleError = it.showTitleError && form.title.isBlank())
        }
    }

    fun pickCover(uri: Uri) {
        _uiState.update {
            it.copy(pickedCover = uri, removeCover = false, coverCopyFailed = false, importFilesFailed = false)
        }
    }

    fun removeCover() {
        _uiState.update { it.copy(pickedCover = null, removeCover = it.savedCover != null) }
    }

    /** Adds a PDF picked on this screen; it's copied in when the pattern is saved. */
    fun addPdf(uri: Uri) {
        viewModelScope.launch {
            val name = PdfNames.displayName(fileStore.displayName(uri) ?: PdfNames.DEFAULT_FILE_NAME)
            _uiState.update {
                it.copy(pdfs = it.pdfs + PdfEntry(key = "picked-${nextPdfKey++}", name = name, pickedUri = uri), pdfCopyFailed = false)
            }
        }
    }

    fun renamePdf(key: String, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        _uiState.update { state -> state.copy(pdfs = state.pdfs.map { if (it.key == key) it.copy(name = clean) else it }) }
    }

    fun removePdf(key: String) {
        _uiState.update { state -> state.copy(pdfs = state.pdfs.filterNot { it.key == key }) }
    }

    fun save() {
        // A tag still in the box when Save is pressed counts too.
        if (_uiState.value.tagInput.isNotBlank()) addTag(_uiState.value.tagInput)
        val state = _uiState.value
        if (state.isSaving) return
        if (state.form.title.isBlank()) {
            _uiState.update { it.copy(showTitleError = true) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val staged = this@EditPatternViewModel.staged
            val sourceType = when {
                staged?.pdfFile != null -> SourceType.PDF
                staged?.downloaded == true -> SourceType.URL
                else -> SourceType.MANUAL
            }
            val id = storedId
                ?.also { repository.update(it, state.form.toDraft()) }
                ?: repository.create(state.form.toDraft(), sourceType).also { storedId = it }

            var filesFailed = false
            if (staged != null && !importAttached) {
                try {
                    repository.attachImport(id, staged, keepCover = state.pickedCover == null && !state.removeCover)
                    importAttached = true
                } catch (e: IOException) {
                    filesFailed = true
                }
            }

            val pdfsFailed = !savePdfs(id)

            var coverFailed = false
            try {
                when {
                    state.pickedCover != null -> repository.setCover(id, state.pickedCover)
                    state.removeCover -> repository.removeCover(id)
                }
            } catch (e: IOException) {
                coverFailed = true
            } catch (e: SecurityException) {
                // The photo picker's permission can lapse if the app was restarted in between.
                coverFailed = true
            }

            _uiState.update {
                if (coverFailed) {
                    // The text is saved; stay on screen so the person can pick the photo again.
                    it.copy(isSaving = false, coverCopyFailed = true, pickedCover = null, initialForm = it.form)
                } else if (filesFailed) {
                    // The text is saved; Save again retries moving the files.
                    it.copy(isSaving = false, importFilesFailed = true, initialForm = it.form)
                } else if (pdfsFailed) {
                    // The PDFs that couldn't be copied stay in the list; Save again retries them.
                    it.copy(isSaving = false, pdfCopyFailed = true, initialForm = it.form)
                } else {
                    it.copy(isSaving = false, savedId = id)
                }
            }
        }
    }

    /**
     * Applies the PDF list: removes, renames and copies in. Each PDF that's done is marked as
     * saved, so a retry after a failure doesn't add it twice. Returns false if any failed.
     */
    private suspend fun savePdfs(id: Long): Boolean {
        val state = _uiState.value
        val keptIds = state.pdfs.mapNotNull { it.savedId }.toSet()
        state.initialPdfs.mapNotNull { it.savedId }.filter { it !in keptIds }.forEach { pdfRepository.removePdf(it) }
        val initialNames = state.initialPdfs.associate { it.key to it.name }

        var allSaved = true
        val saved = state.pdfs.map { entry ->
            try {
                when {
                    entry.savedId != null -> {
                        if (initialNames[entry.key] != entry.name) pdfRepository.renamePdf(entry.savedId, entry.name)
                        entry
                    }
                    entry.stagedFile != null -> entry.copy(savedId = pdfRepository.adoptPdf(id, entry.stagedFile, entry.name), stagedFile = null)
                    entry.pickedUri != null -> entry.copy(savedId = pdfRepository.addPdf(id, entry.pickedUri, entry.name), pickedUri = null)
                    else -> entry
                }
            } catch (e: IOException) {
                allSaved = false
                entry
            } catch (e: SecurityException) {
                // The file picker's permission can lapse if the app was restarted in between.
                allSaved = false
                entry
            }
        }
        _uiState.update { it.copy(pdfs = saved, initialPdfs = saved.filter { entry -> entry.savedId != null }) }
        return allSaved
    }

    override fun onCleared() {
        // Saving has already moved the files out; this removes what's left, or the whole
        // download if the person discarded it. It must outlive this screen, hence appScope.
        importId?.let { id -> appScope.launch { staging.discard(id) } }
    }

    private fun reviewOf(staged: StagedImport) = if (staged.pdfFile != null) {
        ImportReview(
            host = staged.sourceUrl.takeIf { it.isNotBlank() }?.let(UrlTools::host).orEmpty(),
            missingFields = emptyList(),
            foundInstructions = false,
            hasPageCopy = false,
            pdfName = staged.pdfName,
        )
    } else {
        webReviewOf(staged)
    }

    private fun webReviewOf(staged: StagedImport) = ImportReview(
        host = UrlTools.host(staged.sourceUrl),
        missingFields = listOfNotNull(
            R.string.field_designer.takeIf { staged.designer.isBlank() },
            R.string.field_hook_size.takeIf { staged.hookSize.isBlank() },
            R.string.field_yarn_weight.takeIf { staged.yarnWeight.isBlank() },
            R.string.field_materials.takeIf { staged.materials.isBlank() },
        ),
        foundInstructions = staged.foundInstructions,
        hasPageCopy = staged.snapshotDir != null,
    )

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer
                EditPatternViewModel(
                    createSavedStateHandle(),
                    container.patternRepository,
                    container.pdfRepository,
                    container.categoryRepository,
                    container.fileStore,
                    container.importStaging,
                    container.appScope,
                )
            }
        }
    }
}
