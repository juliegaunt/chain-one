package io.github.juliegaunt.chainone.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.db.Category
import io.github.juliegaunt.chainone.ui.components.ConfirmDialog

/** Adding, editing, ordering and deleting the categories that divide up the library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBack: () -> Unit,
    viewModel: CategoriesViewModel = viewModel(factory = CategoriesViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.categories_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) return@Scaffold
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.categories_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (state.suggestions.isNotEmpty()) {
                item { Suggestions(state, onAdd = { viewModel.add(it) }) }
            }
            items(state.rows, key = { it.category.id }) { row ->
                CategoryItem(
                    row = row,
                    isFirst = row == state.rows.first(),
                    isLast = row == state.rows.last(),
                    onEdit = { editingId = row.category.id },
                    onMove = { by -> viewModel.move(row.category.id, by) },
                    onDelete = { deletingId = row.category.id },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.library_other)) },
                    supportingContent = {
                        Text(pluralStringResource(R.plurals.categories_other_count, state.otherCount, state.otherCount))
                    },
                )
                OutlinedButton(
                    onClick = { adding = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.categories_add))
                }
            }
        }
    }

    val others = { except: Long? -> state.rows.map { it.category }.filter { it.id != except } }
    if (adding) {
        CategoryDialog(
            category = null,
            otherCategories = others(null),
            titles = state.titles,
            onSave = { name, extraWords -> viewModel.add(name, extraWords) },
            onDismiss = { adding = false },
        )
    }
    state.rows.firstOrNull { it.category.id == editingId }?.let { row ->
        CategoryDialog(
            category = row.category,
            otherCategories = others(row.category.id),
            titles = state.titles,
            onSave = { name, extraWords -> viewModel.update(row.category.id, name, extraWords) },
            onDismiss = { editingId = null },
        )
    }
    state.rows.firstOrNull { it.category.id == deletingId }?.let { row ->
        ConfirmDialog(
            title = stringResource(R.string.categories_delete_title, row.category.name),
            text = stringResource(R.string.categories_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete(row.category.id) },
            onDismiss = { deletingId = null },
        )
    }
}

/** Words several titles in "Other" share, each one tap from becoming a category. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(state: CategoriesUiState, onAdd: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.categories_suggested),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.suggestions.forEach { suggestion ->
                SuggestionChip(
                    onClick = { onAdd(suggestion.name) },
                    label = { Text(stringResource(R.string.categories_suggestion, suggestion.name, suggestion.patternCount)) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp)) },
                )
            }
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun CategoryItem(
    row: CategoryRow,
    isFirst: Boolean,
    isLast: Boolean,
    onEdit: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val category = row.category
    ListItem(
        headlineContent = { Text(category.name) },
        supportingContent = {
            Column {
                if (category.extraWords.isNotBlank()) Text(stringResource(R.string.categories_also_matches_list, category.extraWords))
                Text(pluralStringResource(R.plurals.categories_pattern_count, row.patternCount, row.patternCount))
            }
        },
        trailingContent = { CategoryMenu(category, isFirst, isLast, onEdit, onMove, onDelete) },
    )
}

@Composable
private fun CategoryMenu(category: Category, isFirst: Boolean, isLast: Boolean, onEdit: () -> Unit, onMove: (Int) -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val choose = { action: () -> Unit ->
        expanded = false
        action()
    }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.categories_options, category.name))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = { choose(onEdit) })
            if (!isFirst) DropdownMenuItem(text = { Text(stringResource(R.string.action_move_up)) }, onClick = { choose { onMove(-1) } })
            if (!isLast) DropdownMenuItem(text = { Text(stringResource(R.string.action_move_down)) }, onClick = { choose { onMove(+1) } })
            DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { choose(onDelete) })
        }
    }
}
