package io.github.juliegaunt.chainone.ui.pattern

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.db.Pattern
import io.github.juliegaunt.chainone.ui.components.PatternCover
import io.github.juliegaunt.chainone.ui.components.labelRes
import java.io.File

private enum class DetailTab(val labelRes: Int) {
    OVERVIEW(R.string.tab_overview),
    INSTRUCTIONS(R.string.tab_instructions),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: PatternDetailViewModel = viewModel(factory = PatternDetailViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onBack()
    }

    val pattern = state.pattern

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(pattern?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (pattern != null) {
                        IconToggleButton(checked = pattern.isFavorite, onCheckedChange = { viewModel.toggleFavorite() }) {
                            Icon(
                                if (pattern.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = stringResource(
                                    if (pattern.isFavorite) R.string.action_unfavorite else R.string.action_favorite,
                                ),
                            )
                        }
                        IconButton(onClick = { onEdit(pattern.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_delete)) },
                                    onClick = {
                                        showMenu = false
                                        confirmDelete = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.isLoading -> Unit
                pattern == null -> if (!state.isDeleted) {
                    Text(
                        stringResource(R.string.pattern_not_found),
                        modifier = Modifier.padding(24.dp),
                    )
                }
                else -> {
                    PrimaryTabRow(selectedTabIndex = selectedTab) {
                        DetailTab.entries.forEachIndexed { index, tab ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(stringResource(tab.labelRes)) },
                            )
                        }
                    }
                    when (DetailTab.entries[selectedTab]) {
                        DetailTab.OVERVIEW -> OverviewTab(pattern, state.cover)
                        DetailTab.INSTRUCTIONS -> InstructionsTab(pattern)
                    }
                }
            }
        }
    }

    if (confirmDelete && pattern != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_pattern_title)) },
            text = { Text(stringResource(R.string.delete_pattern_message, pattern.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun OverviewTab(pattern: Pattern, cover: File?) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (cover != null) {
            PatternCover(
                model = cover,
                title = pattern.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
            )
        }
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(pattern.title, style = MaterialTheme.typography.headlineSmall)
                if (pattern.designer.isNotBlank()) {
                    Text(
                        stringResource(R.string.pattern_by_designer, pattern.designer),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AssistChip(onClick = {}, label = { Text(stringResource(pattern.status.labelRes)) })

            if (pattern.hookSize.isNotBlank() || pattern.yarnWeight.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    if (pattern.hookSize.isNotBlank()) Fact(stringResource(R.string.field_hook_size), pattern.hookSize)
                    if (pattern.yarnWeight.isNotBlank()) Fact(stringResource(R.string.field_yarn_weight), pattern.yarnWeight)
                }
            }

            if (pattern.materials.isNotBlank()) {
                Section(stringResource(R.string.field_materials)) { InstructionsText(pattern.materials) }
            }
            if (pattern.notes.isNotBlank()) {
                Section(stringResource(R.string.field_notes)) {
                    SelectionContainer { Text(pattern.notes, style = MaterialTheme.typography.bodyLarge) }
                }
            }
            pattern.sourceUrl?.let { url ->
                val uriHandler = LocalUriHandler.current
                Section(stringResource(R.string.field_source_url)) {
                    Text(
                        url,
                        style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { runCatching { uriHandler.openUri(url) } }
                            .padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InstructionsTab(pattern: Pattern) {
    Box(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        if (pattern.instructions.isBlank()) {
            Text(
                stringResource(R.string.instructions_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopStart),
            )
        } else {
            InstructionsText(pattern.instructions)
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        content()
    }
}
