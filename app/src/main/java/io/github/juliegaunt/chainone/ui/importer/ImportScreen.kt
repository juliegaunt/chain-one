package io.github.juliegaunt.chainone.ui.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.importer.ImportProblem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    onImported: (importId: String) -> Unit,
    onOpenPattern: (Long) -> Unit,
    viewModel: ImportViewModel = viewModel(factory = ImportViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val phase = state.phase
    val isLoading = phase == ImportPhase.Loading

    LaunchedEffect(phase) {
        if (phase is ImportPhase.Ready) onImported(phase.importId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.url,
                onValueChange = viewModel::updateUrl,
                label = { Text(stringResource(R.string.import_url_label)) },
                placeholder = { Text(stringResource(R.string.import_url_placeholder)) },
                singleLine = true,
                enabled = !isLoading,
                trailingIcon = { PasteButton(enabled = !isLoading, onPaste = viewModel::updateUrl) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { viewModel.start() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.start() },
                enabled = state.url.isNotBlank() && !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.import_button))
            }

            when (phase) {
                ImportPhase.Loading -> Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Text(stringResource(R.string.import_loading), style = MaterialTheme.typography.bodyLarge)
                }
                is ImportPhase.Failed -> ImportError(
                    problem = phase.problem,
                    onRetry = { viewModel.start() },
                    onSaveLinkOnly = viewModel::saveLinkOnly,
                )
                else -> Text(
                    stringResource(R.string.import_tip),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (phase is ImportPhase.Duplicate) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicate,
            title = { Text(stringResource(R.string.import_duplicate_title)) },
            text = { Text(stringResource(R.string.import_duplicate_message, phase.existing.title)) },
            confirmButton = {
                TextButton(onClick = { onOpenPattern(phase.existing.id) }) {
                    Text(stringResource(R.string.import_open_existing))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::importAgain) { Text(stringResource(R.string.import_again)) }
            },
        )
    }
}

@Composable
private fun PasteButton(enabled: Boolean, onPaste: (String) -> Unit) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    TextButton(
        enabled = enabled,
        onClick = {
            scope.launch {
                val clip = clipboard.getClipEntry()?.clipData ?: return@launch
                if (clip.itemCount > 0) {
                    clip.getItemAt(0).coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(onPaste)
                }
            }
        },
    ) {
        Text(stringResource(R.string.import_paste))
    }
}

@Composable
private fun ImportError(problem: ImportProblem?, onRetry: () -> Unit, onSaveLinkOnly: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(problem.messageRes), style = MaterialTheme.typography.bodyLarge)
            // A bad link can only be fixed by editing it, so the buttons would just repeat the error.
            if (problem != ImportProblem.INVALID_LINK) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.import_try_again)) }
                    TextButton(onClick = onSaveLinkOnly) { Text(stringResource(R.string.import_save_link_only)) }
                }
            }
        }
    }
}

private val ImportProblem?.messageRes: Int
    get() = when (this) {
        ImportProblem.INVALID_LINK -> R.string.import_error_invalid_link
        ImportProblem.NO_CONNECTION -> R.string.import_error_no_connection
        ImportProblem.TIMEOUT -> R.string.import_error_timeout
        ImportProblem.BLOCKED -> R.string.import_error_blocked
        ImportProblem.NOT_FOUND -> R.string.import_error_not_found
        ImportProblem.SERVER_ERROR -> R.string.import_error_server
        ImportProblem.IS_PDF -> R.string.import_error_pdf
        ImportProblem.PDF_PROTECTED -> R.string.pdf_error_protected
        ImportProblem.PDF_UNREADABLE -> R.string.pdf_error_unreadable
        ImportProblem.NOT_A_WEB_PAGE -> R.string.import_error_not_web_page
        null -> R.string.import_error_unknown
    }
