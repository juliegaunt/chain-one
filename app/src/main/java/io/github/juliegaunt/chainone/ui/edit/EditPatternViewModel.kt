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
import io.github.juliegaunt.chainone.data.db.PatternStatus
import io.github.juliegaunt.chainone.data.files.FileStore
import io.github.juliegaunt.chainone.data.repository.PatternDraft
import io.github.juliegaunt.chainone.data.repository.PatternRepository
import io.github.juliegaunt.chainone.ui.components.coverFile
import io.github.juliegaunt.chainone.ui.navigation.ARG_PATTERN_ID
import io.github.juliegaunt.chainone.ui.navigation.NEW_PATTERN_ID
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
    )

    companion object {
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

data class EditPatternUiState(
    val isNew: Boolean,
    val isLoading: Boolean = !isNew,
    val form: PatternForm = PatternForm(),
    /** The form as it was when the screen opened, to detect unsaved changes. */
    val initialForm: PatternForm = PatternForm(),
    val savedCover: File? = null,
    /** A photo picked on this screen but not saved yet. */
    val pickedCover: Uri? = null,
    val removeCover: Boolean = false,
    val showTitleError: Boolean = false,
    val isSaving: Boolean = false,
    val coverCopyFailed: Boolean = false,
    /** Set when saving finished; the screen then navigates away. */
    val savedId: Long? = null,
) {
    val coverModel: Any? get() = pickedCover ?: savedCover.takeUnless { removeCover }

    val hasChanges: Boolean get() = form != initialForm || pickedCover != null || removeCover
}

class EditPatternViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: PatternRepository,
    private val fileStore: FileStore,
) : ViewModel() {

    private val patternId: Long = savedStateHandle[ARG_PATTERN_ID] ?: NEW_PATTERN_ID
    private val isNew = patternId == NEW_PATTERN_ID

    /** Null until a new pattern has been inserted, so a retry after a failed photo copy updates it. */
    private var storedId: Long? = patternId.takeUnless { isNew }

    private val _uiState = MutableStateFlow(EditPatternUiState(isNew = isNew))
    val uiState: StateFlow<EditPatternUiState> = _uiState.asStateFlow()

    init {
        if (!isNew) {
            viewModelScope.launch {
                val pattern = repository.getPattern(patternId)
                val form = pattern?.let(PatternForm::from) ?: PatternForm()
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

    fun updateForm(change: (PatternForm) -> PatternForm) {
        _uiState.update {
            val form = change(it.form)
            it.copy(form = form, showTitleError = it.showTitleError && form.title.isBlank())
        }
    }

    fun pickCover(uri: Uri) {
        _uiState.update { it.copy(pickedCover = uri, removeCover = false, coverCopyFailed = false) }
    }

    fun removeCover() {
        _uiState.update { it.copy(pickedCover = null, removeCover = it.savedCover != null) }
    }

    fun save() {
        val state = _uiState.value
        if (state.isSaving) return
        if (state.form.title.isBlank()) {
            _uiState.update { it.copy(showTitleError = true) }
            return
        }
        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val id = storedId
                ?.also { repository.update(it, state.form.toDraft()) }
                ?: repository.create(state.form.toDraft()).also { storedId = it }

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
                } else {
                    it.copy(isSaving = false, savedId = id)
                }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer
                EditPatternViewModel(createSavedStateHandle(), container.patternRepository, container.fileStore)
            }
        }
    }
}
