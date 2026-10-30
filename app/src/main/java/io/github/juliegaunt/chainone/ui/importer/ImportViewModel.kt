package io.github.juliegaunt.chainone.ui.importer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.juliegaunt.chainone.appContainer
import io.github.juliegaunt.chainone.data.db.Pattern
import io.github.juliegaunt.chainone.data.importer.ImportException
import io.github.juliegaunt.chainone.data.importer.ImportProblem
import io.github.juliegaunt.chainone.data.importer.PatternImporter
import io.github.juliegaunt.chainone.data.importer.UrlTools
import io.github.juliegaunt.chainone.data.repository.PatternRepository
import io.github.juliegaunt.chainone.ui.navigation.ARG_SHARED_TEXT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface ImportPhase {
    data object Idle : ImportPhase
    data object Loading : ImportPhase

    /** [problem] is null for an unexpected error, such as the phone's storage being full. */
    data class Failed(val problem: ImportProblem?) : ImportPhase
    data class Duplicate(val existing: Pattern) : ImportPhase

    /** Downloaded and staged; the screen moves on to the review screen. */
    data class Ready(val importId: String) : ImportPhase
}

data class ImportUiState(
    val url: String = "",
    val phase: ImportPhase = ImportPhase.Idle,
)

/** Downloads a pattern page from a link and stages it for the review screen. */
class ImportViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: PatternRepository,
    private val importer: PatternImporter,
) : ViewModel() {

    /** Text shared from another app with "Share → Chain One", if that's how the screen was opened. */
    private val sharedText: String? = savedStateHandle[ARG_SHARED_TEXT]

    private val _uiState = MutableStateFlow(
        ImportUiState(url = sharedText?.let { UrlTools.extractUrl(it) ?: it.trim() }.orEmpty()),
    )
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    init {
        if (sharedText != null) start()
    }

    fun updateUrl(url: String) {
        _uiState.update {
            it.copy(url = url, phase = if (it.phase is ImportPhase.Failed) ImportPhase.Idle else it.phase)
        }
    }

    /** Downloads the pattern, first checking whether it's already in the library (unless [checkDuplicate] is false). */
    fun start(checkDuplicate: Boolean = true) {
        if (_uiState.value.phase == ImportPhase.Loading) return
        val url = UrlTools.clean(_uiState.value.url)
        if (url == null) {
            _uiState.update { it.copy(phase = ImportPhase.Failed(ImportProblem.INVALID_LINK)) }
            return
        }
        _uiState.update { it.copy(url = url, phase = ImportPhase.Loading) }

        viewModelScope.launch {
            val existing = if (checkDuplicate) repository.findBySourceUrl(url) else null
            val phase = when {
                existing != null -> ImportPhase.Duplicate(existing)
                else -> try {
                    val staged = importer.import(url)
                    // A short link may turn out to lead to a pattern that's already saved.
                    val existingAfterRedirect = if (checkDuplicate && staged.sourceUrl != url) {
                        repository.findBySourceUrl(staged.sourceUrl)
                    } else {
                        null
                    }
                    if (existingAfterRedirect != null) {
                        importer.discard(staged)
                        ImportPhase.Duplicate(existingAfterRedirect)
                    } else {
                        ImportPhase.Ready(staged.id)
                    }
                } catch (e: ImportException) {
                    ImportPhase.Failed(e.problem)
                } catch (e: IOException) {
                    ImportPhase.Failed(null)
                }
            }
            _uiState.update { it.copy(phase = phase) }
        }
    }

    fun importAgain() {
        _uiState.update { it.copy(phase = ImportPhase.Idle) }
        start(checkDuplicate = false)
    }

    fun dismissDuplicate() {
        _uiState.update { it.copy(phase = ImportPhase.Idle) }
    }

    /** After a failed download: go to the review screen with just the link filled in. */
    fun saveLinkOnly() {
        val url = UrlTools.clean(_uiState.value.url) ?: return
        viewModelScope.launch {
            val phase = try {
                ImportPhase.Ready(importer.linkOnly(url).id)
            } catch (e: IOException) {
                ImportPhase.Failed(null)
            }
            _uiState.update { it.copy(phase = phase) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer
                ImportViewModel(createSavedStateHandle(), container.patternRepository, container.patternImporter)
            }
        }
    }
}
