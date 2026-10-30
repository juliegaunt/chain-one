package io.github.juliegaunt.chainone.ui.importer

import io.github.juliegaunt.chainone.ui.components.ProgressRow
import io.github.juliegaunt.chainone.ui.components.ErrorCard
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.pdf.PdfProblem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfImportScreen(
    onBack: () -> Unit,
    onImported: (importId: String) -> Unit,
    viewModel: PdfImportViewModel = viewModel(factory = PdfImportViewModel.Factory),
) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()

    LaunchedEffect(phase) {
        (phase as? PdfImportPhase.Ready)?.let { onImported(it.importId) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pdf_import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            when (val current = phase) {
                PdfImportPhase.Loading, is PdfImportPhase.Ready -> ProgressRow(stringResource(R.string.pdf_import_loading))
                is PdfImportPhase.Failed -> ErrorCard(stringResource(current.problem.messageRes))
            }
        }
    }
}

val PdfProblem?.messageRes: Int
    get() = when (this) {
        PdfProblem.PROTECTED -> R.string.pdf_error_protected
        PdfProblem.UNREADABLE -> R.string.pdf_error_unreadable
        PdfProblem.TOO_LARGE -> R.string.pdf_error_too_large
        null -> R.string.pdf_error_copy
    }
