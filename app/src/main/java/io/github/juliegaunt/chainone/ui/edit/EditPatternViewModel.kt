package io.github.juliegaunt.chainone.ui.edit

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.juliegaunt.chainone.appContainer
import io.github.juliegaunt.chainone.data.db.Pattern
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.db.PatternStatus
import io.github.juliegaunt.chainone.data.db.SourceType
import io.github.juliegaunt.chainone.data.files.FileStore
import io.github.juliegaunt.chainone.data.importer.ImportStaging
import io.github.juliegaunt.chainone.data.importer.StagedImport
import io.github.juliegaunt.chainone.data.importer.UrlTools
import io.github.juliegaunt.chainone.data.repository.PatternDraft
import io.github.juliegaunt.chainone.data.repository.PatternRepository
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
import java.io.File
import java.io.IOException

data class PatternForm(
    val title: String = "",
    val designer: String = "",
    val sourceUrl: String = "",
    val hookSize: String = "",
    val yarnWeight: String = "",
    val materials: String = "",
    val instructions: String = "",
    val notes: String = "",
    val status: PatternStatus = PatternStatus.WANT_TO_MAKE,
    val tags: List<String> = emptyList(),
) {
    fun toDraft() = PatternDraft(
        title = title,
        designer = designer,
        sourceUrl = sourceUrl,
        hookSize = hookSize,
        yarnWeight = yarnWeight,
        materials = materials,
        instructions = instructions,
        notes = notes,
        status = status,
        tags = tags,
    )

    companion object {
        fun from(staged: StagedImport) = PatternForm(
            title = staged.title,
            designer = staged.designer,
            sourceUrl = staged.sourceUrl,
            hookSize = staged.hookSize,
            yarnWeight = staged.yarnWeight,
            materials = staged.materials,
            instructions = staged.instructions,
        )

        fun from(pattern: Pattern) = PatternForm(
            title = pattern.title,
            designer = pattern.designer,
            sourceUrl = pattern.sourceUrl.orEmpty(),
            hookSize = pattern.hookSize,
            yarnWeight = pattern.yarnWeight,
            materials = pattern.materials,
            instructions = pattern.instructions,
            notes = pattern.notes,
            status = pattern.status,
        )
    }
}

/** What the review banner says about a pattern imported from a website. */
data class ImportReview(
    val host: String,
    /** String resources of the fields the page didn't provide. */
    val missingFields: List<Int>,
    val foundInstructions: Boolean,
    val hasPageCopy: Boolean,
)

data class EditPatternUiState(
    val isNew: Boolean,
    /** Set when reviewing a pattern downloaded from a website, before it's saved. */
    val review: ImportReview? = null,
    val isLoading: Boolean = !isNew,
    val form: PatternForm = PatternForm(),
    /** The form as it was when the screen opened, to detect unsaved changes. */
    val initialForm: PatternForm = PatternForm(),
    /** The cover already saved (or, when reviewing an import, the downloaded one). */
    val savedCover: File? = null,
    /** A photo picked on this screen but not saved yet. */
    val pickedCover: Uri? = null,
    val removeCover: Boolean = false,
    val showTitleError: Boolean = false,
    val isSaving: Boolean = false,
    val coverCopyFailed: Boolean = false,
    val importFilesFailed: Boolean = false,
    /** Text typed in the Tags box that hasn't been turned into a tag yet. */
    val tagInput: String = "",
    /** Every tag in the library, to suggest while typing. */
    val allTags: List<String> = emptyList(),
    /** Set when saving finished; the screen then navigates away. */
    val savedId: Long? = null,
) {
    val coverModel: Any? get() = pickedCover ?: savedCover.takeUnless { removeCover }

    val hasChanges: Boolean get() = form != initialForm || pickedCover != null || removeCover || tagInput.isNotBlank()

    /** Existing tags matching what's typed (or, before typing, any), leaving out ones already added. */
    val tagSuggestions: List<String>
        get() = allTags
            .filter { tag -> form.tags.none { it.equals(tag, ignoreCase = true) } }
            .filter { it.contains(tagInput.trim(), ignoreCase = true) }
            .take(MAX_SUGGESTIONS)

    private companion object {
        const val MAX_SUGGESTIONS = 8
    }
}

class EditPatternViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: PatternRepository,
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

    /** Null until a new pattern has been inserted, so a retry after a failed photo copy updates it. */
    private var storedId: Long? = patternId.takeUnless { isNew }

    private val _uiState = MutableStateFlow(EditPatternUiState(isNew = isNew, isLoading = !isNew || importId != null))
    val uiState: StateFlow<EditPatternUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeTags().collect { tags -> _uiState.update { it.copy(allTags = tags.map { tag -> tag.name }) } }
        }
        if (importId != null) {
            viewModelScope.launch {
                val loaded = staging.load(importId)
                staged = loaded
                val form = loaded?.let(PatternForm::from) ?: PatternForm()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        form = form,
                        // Compared with a blank form, so leaving without saving asks first.
                        initialForm = PatternForm(),
                        savedCover = loaded?.coverFile,
                        review = loaded?.takeIf { it.downloaded }?.let(::reviewOf),
                    )
                }
            }
        } else if (!isNew) {
            viewModelScope.launch {
                val pattern = repository.getPattern(patternId)
                val form = pattern?.let { PatternForm.from(it).copy(tags = repository.getTagNames(it.id)) } ?: PatternForm()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        form = form,
                        initialForm = form,
                        savedCover = fileStore.coverFile(pattern?.coverImagePath),
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
        val clean = name.trim().replace(Regex("""\s+"""), " ")
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
            val sourceType = if (staged?.downloaded == true) SourceType.URL else SourceType.MANUAL
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
                } else {
                    it.copy(isSaving = false, savedId = id)
                }
            }
        }
    }

    override fun onCleared() {
        // Saving has already moved the files out; this removes what's left, or the whole
        // download if the person discarded it. It must outlive this screen, hence appScope.
        importId?.let { id -> appScope.launch { staging.discard(id) } }
    }

    private fun reviewOf(staged: StagedImport) = ImportReview(
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
                    container.fileStore,
                    container.importStaging,
                    container.appScope,
                )
            }
        }
    }
}
