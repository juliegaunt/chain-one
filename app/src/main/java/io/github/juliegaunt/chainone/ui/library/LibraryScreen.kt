package io.github.juliegaunt.chainone.ui.library

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.db.LibraryFilter
import io.github.juliegaunt.chainone.data.settings.LibraryLayout
import io.github.juliegaunt.chainone.ui.backup.BackupDialogs
import io.github.juliegaunt.chainone.ui.backup.BackupViewModel
import io.github.juliegaunt.chainone.ui.backup.textRes
import io.github.juliegaunt.chainone.ui.components.ConfirmDialog
import io.github.juliegaunt.chainone.ui.components.RenameDialog
import java.time.LocalDate

private const val PDF_TYPE = "application/pdf"
private const val ZIP_TYPE = "application/zip"

/** Some apps and cloud drives label zip files differently, so a backup is found whichever they use. */
private val BACKUP_TYPES = arrayOf(ZIP_TYPE, "application/x-zip-compressed", "application/octet-stream")

/** The main screen: every pattern as a grid or list, with search, filters, favorites and backups. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenPattern: (Long) -> Unit,
    onAddManually: () -> Unit,
    onImportFromWeb: () -> Unit,
    onImportPdf: (Uri) -> Unit,
    showTagRequest: Long? = null,
    onShowTagHandled: () -> Unit = {},
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
    backupViewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val backup by backupViewModel.uiState.collectAsStateWithLifecycle()
    var showAddChoices by rememberSaveable { mutableStateOf(false) }
    var showTagSheet by rememberSaveable { mutableStateOf(false) }
    var renamingTagId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingTagId by rememberSaveable { mutableStateOf<Long?>(null) }
    val filter = state.filter
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImportPdf)
    }
    // The system's own save and open screens: the person picks where the backup goes and comes from.
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ZIP_TYPE)) { uri ->
        uri?.let(backupViewModel::backUp)
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(backupViewModel::inspect)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val backupMessage = backup.message?.let { stringResource(it.textRes) }
    LaunchedEffect(backupMessage) {
        if (backupMessage != null) {
            // Cleared once it's gone, not before: clearing it would cancel this effect, and the snackbar with it.
            snackbarHostState.showSnackbar(backupMessage, withDismissAction = true)
            backupViewModel.messageShown()
        }
    }

    BackHandler(enabled = state.isSearching, onBack = viewModel::closeSearch)
    LaunchedEffect(showTagRequest) {
        showTagRequest?.let {
            viewModel.showOnlyTag(it)
            onShowTagHandled()
        }
    }

    Scaffold(
        topBar = {
            LibraryTopBar(
                state = state,
                onOpenSearch = viewModel::openSearch,
                onCloseSearch = viewModel::closeSearch,
                onSearchChange = viewModel::updateSearch,
                onToggleFavorites = viewModel::toggleFavoritesOnly,
                onToggleLayout = viewModel::toggleLayout,
                onBackUp = { saveBackup.launch("chain-one-backup-${LocalDate.now()}.zip") },
                onRestore = { openBackup.launch(BACKUP_TYPES) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // A compact version of Material's labelled button: 48dp tall (still easy to tap), with
            // tighter spacing than ExtendedFloatingActionButton allows, so the text fills it.
            FloatingActionButton(
                onClick = { showAddChoices = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.height(48.dp),
            ) {
                Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.library_add_pattern),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Status and tag filters are part of searching, so they only show while it's open.
            if (state.hasPatterns && state.isSearching) {
                FilterRow(
                    filter = filter,
                    tags = state.tags,
                    onToggleStatus = viewModel::toggleStatus,
                    onOpenTags = { showTagSheet = true },
                    onClear = viewModel::clearFilters,
                )
            }
            val modifier = Modifier.fillMaxSize()
            when {
                state.isLoading -> Unit
                !state.hasPatterns -> EmptyMessage(stringResource(R.string.library_empty), modifier)
                state.items.isEmpty() && filter == LibraryFilter(favoritesOnly = true) ->
                    EmptyMessage(stringResource(R.string.library_empty_favorites), modifier)
                state.items.isEmpty() -> EmptyMessage(stringResource(R.string.library_no_matches), modifier) {
                    TextButton(onClick = viewModel::clearFilters) { Text(stringResource(R.string.library_clear_filters)) }
                }
                state.layout == LibraryLayout.GRID -> PatternGrid(
                    items = state.items,
                    onOpen = onOpenPattern,
                    onToggleFavorite = viewModel::toggleFavorite,
                    modifier = modifier,
                )
                else -> PatternList(
                    items = state.items,
                    onOpen = onOpenPattern,
                    onToggleFavorite = viewModel::toggleFavorite,
                    modifier = modifier,
                )
            }
        }
    }

    BackupDialogs(
        phase = backup.phase,
        onConfirmRestore = backupViewModel::confirmRestore,
        onCancelRestore = backupViewModel::cancelRestore,
    )

    if (showTagSheet) {
        TagFilterSheet(
            tags = state.tags,
            selected = filter.tagIds,
            onToggle = viewModel::toggleTag,
            onClear = viewModel::clearTags,
            onRename = { renamingTagId = it.id },
            onDelete = { deletingTagId = it.id },
            onDismiss = { showTagSheet = false },
        )
    }
    state.tags.firstOrNull { it.id == renamingTagId }?.let { tag ->
        RenameDialog(
            title = stringResource(R.string.tag_rename_title),
            current = tag.name,
            hint = stringResource(R.string.tag_rename_hint),
            onRename = { viewModel.renameTag(tag.id, it) },
            onDismiss = { renamingTagId = null },
        )
    }
    state.tags.firstOrNull { it.id == deletingTagId }?.let { tag ->
        ConfirmDialog(
            title = stringResource(R.string.tag_delete_title, tag.name),
            text = pluralStringResource(R.plurals.tag_delete_message, tag.patternCount, tag.patternCount),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.deleteTag(tag.id) },
            onDismiss = { deletingTagId = null },
        )
    }

    if (showAddChoices) {
        AddPatternSheet(
            onDismiss = { showAddChoices = false },
            onImportFromWeb = {
                showAddChoices = false
                onImportFromWeb()
            },
            onImportPdf = {
                showAddChoices = false
                pickPdf.launch(arrayOf(PDF_TYPE))
            },
            onAddManually = {
                showAddChoices = false
                onAddManually()
            },
        )
    }
}

/** Asks how to add a pattern: from a website (most patterns), a PDF, or by typing it in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPatternSheet(
    onDismiss: () -> Unit,
    onImportFromWeb: () -> Unit,
    onImportPdf: () -> Unit,
    onAddManually: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.library_add_from_web)) },
                supportingContent = { Text(stringResource(R.string.library_add_from_web_hint)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_link), contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onImportFromWeb),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.library_add_pdf)) },
                supportingContent = { Text(stringResource(R.string.library_add_pdf_hint)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_pdf), contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onImportPdf),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.library_add_manually)) },
                supportingContent = { Text(stringResource(R.string.library_add_manually_hint)) },
                leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onAddManually),
            )
        }
    }
}

@Composable
private fun EmptyMessage(text: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action()
    }
}
