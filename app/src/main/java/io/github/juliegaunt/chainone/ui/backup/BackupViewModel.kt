package io.github.juliegaunt.chainone.ui.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.juliegaunt.chainone.appContainer
import io.github.juliegaunt.chainone.data.backup.BackupException
import io.github.juliegaunt.chainone.data.backup.BackupProblem
import io.github.juliegaunt.chainone.data.backup.BackupSummary
import io.github.juliegaunt.chainone.data.backup.LibraryBackup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.io.IOException

sealed interface BackupPhase {
    data object Idle : BackupPhase

    /** Something is running; the screen shows a progress message and waits. */
    data class Working(val task: BackupTask) : BackupPhase

    /** A backup has been read and is waiting for the person to confirm replacing the library. */
    data class ConfirmRestore(val uri: Uri, val summary: BackupSummary, val currentPatterns: Int) : BackupPhase
}

enum class BackupTask { SAVING, READING, RESTORING }

/** A one-off message for the snackbar once a task has finished. */
enum class BackupMessage { SAVED, RESTORED, SAVE_FAILED, READ_FAILED, NOT_A_BACKUP, NEWER_VERSION, DAMAGED, NO_SPACE }

data class BackupUiState(
    val phase: BackupPhase = BackupPhase.Idle,
    val message: BackupMessage? = null,
)

/** Backing up to, and restoring from, a zip file the person picks. */
class BackupViewModel(
    private val backup: LibraryBackup,
    private val contentResolver: ContentResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    /** Writes a backup to [uri], a new file from the system's save screen. */
    fun backUp(uri: Uri) = run(BackupTask.SAVING) {
        val message = try {
            val output = contentResolver.openOutputStream(uri, "wt") ?: throw FileNotFoundException(uri.toString())
            backup.export(output)
            BackupMessage.SAVED
        } catch (e: IOException) {
            deleteQuietly(uri)
            BackupMessage.SAVE_FAILED
        } catch (e: SecurityException) {
            deleteQuietly(uri)
            BackupMessage.SAVE_FAILED
        }
        finish(message)
    }

    /** Reads the backup at [uri] and asks the person to confirm before anything changes. */
    fun inspect(uri: Uri) = run(BackupTask.READING) {
        try {
            val summary = open(uri).let { backup.inspect(it) }
            _uiState.update { it.copy(phase = BackupPhase.ConfirmRestore(uri, summary, backup.patternCount())) }
        } catch (e: BackupException) {
            finish(e.problem.message)
        } catch (e: IOException) {
            finish(BackupMessage.READ_FAILED)
        } catch (e: SecurityException) {
            finish(BackupMessage.READ_FAILED)
        }
    }

    fun confirmRestore() {
        val confirm = _uiState.value.phase as? BackupPhase.ConfirmRestore ?: return
        run(BackupTask.RESTORING) {
            val message = try {
                backup.restore(open(confirm.uri))
                BackupMessage.RESTORED
            } catch (e: BackupException) {
                e.problem.message
            } catch (e: IOException) {
                BackupMessage.READ_FAILED
            } catch (e: SecurityException) {
                BackupMessage.READ_FAILED
            }
            finish(message)
        }
    }

    fun cancelRestore() {
        _uiState.update { it.copy(phase = BackupPhase.Idle) }
    }

    fun messageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private fun run(task: BackupTask, block: suspend () -> Unit) {
        if (_uiState.value.phase is BackupPhase.Working) return
        _uiState.update { it.copy(phase = BackupPhase.Working(task), message = null) }
        viewModelScope.launch { block() }
    }

    private fun finish(message: BackupMessage) {
        _uiState.update { it.copy(phase = BackupPhase.Idle, message = message) }
    }

    private fun open(uri: Uri) = contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())

    /** A half-written backup would only cause confusion later. */
    private fun deleteQuietly(uri: Uri) {
        try {
            DocumentsContract.deleteDocument(contentResolver, uri)
        } catch (e: Exception) {
            // Some places (or older phones) don't allow it; the failure message still shows.
        }
    }

    private val BackupProblem.message: BackupMessage
        get() = when (this) {
            BackupProblem.NOT_A_BACKUP -> BackupMessage.NOT_A_BACKUP
            BackupProblem.NEWER_VERSION -> BackupMessage.NEWER_VERSION
            BackupProblem.DAMAGED -> BackupMessage.DAMAGED
            BackupProblem.NO_SPACE -> BackupMessage.NO_SPACE
        }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                BackupViewModel(appContainer.libraryBackup, checkNotNull(this[APPLICATION_KEY]).contentResolver)
            }
        }
    }
}
