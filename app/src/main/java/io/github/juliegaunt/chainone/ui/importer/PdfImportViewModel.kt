package io.github.juliegaunt.chainone.ui.importer

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.juliegaunt.chainone.appContainer
import io.github.juliegaunt.chainone.data.importer.PdfImporter
import io.github.juliegaunt.chainone.data.pdf.PdfOpenException
import io.github.juliegaunt.chainone.data.pdf.PdfProblem
import io.github.juliegaunt.chainone.ui.navigation.ARG_PDF_URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface PdfImportPhase {
    data object Loading : PdfImportPhase

    /** [problem] is null when the file couldn't be read at all (for example, it was moved). */
    data class Failed(val problem: PdfProblem?) : PdfImportPhase

    /** Copied and staged; the screen moves on to the review screen. */
    data class Ready(val importId: String) : PdfImportPhase
}

/** Copies a picked or shared PDF in, then hands over to the review screen. */
class PdfImportViewModel(
    savedStateHandle: SavedStateHandle,
    private val importer: PdfImporter,
) : ViewModel() {

    private val uri: Uri = Uri.parse(checkNotNull(savedStateHandle.get<String>(ARG_PDF_URI)))

    private val _phase = MutableStateFlow<PdfImportPhase>(PdfImportPhase.Loading)
    val phase: StateFlow<PdfImportPhase> = _phase.asStateFlow()

    init {
        viewModelScope.launch {
            _phase.value = try {
                PdfImportPhase.Ready(importer.importFile(uri).id)
            } catch (e: PdfOpenException) {
                PdfImportPhase.Failed(e.problem)
            } catch (e: IOException) {
                PdfImportPhase.Failed(null)
            } catch (e: SecurityException) {
                // The other app's permission to read the file can lapse if Chain One was restarted.
                PdfImportPhase.Failed(null)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                PdfImportViewModel(createSavedStateHandle(), appContainer.pdfImporter)
            }
        }
    }
}
