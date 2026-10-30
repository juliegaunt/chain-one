package io.github.juliegaunt.chainone.ui.library

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.db.LibraryFilter
import io.github.juliegaunt.chainone.data.db.Pattern
import io.github.juliegaunt.chainone.data.db.PatternStatus
import io.github.juliegaunt.chainone.data.db.TagCount
import io.github.juliegaunt.chainone.data.settings.LibraryLayout
import io.github.juliegaunt.chainone.ui.components.PatternCover
import io.github.juliegaunt.chainone.ui.components.StatusBadge
import io.github.juliegaunt.chainone.ui.components.StatusChip

// Leaves room under the last row so the floating "Add" button doesn't cover it.
private val ListBottomPadding = 96.dp

private const val PDF_TYPE = "application/pdf"

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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddChoices by rememberSaveable { mutableStateOf(false) }
    var showTagSheet by rememberSaveable { mutableStateOf(false) }
    var renamingTagId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingTagId by rememberSaveable { mutableStateOf<Long?>(null) }
    val filter = state.filter
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(onImportPdf)
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
            TopAppBar(
                navigationIcon = {
                    if (state.isSearching) {
                        IconButton(onClick = viewModel::closeSearch) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.library_close_search))
                        }
                    }
                },
                title = {
                    if (state.isSearching) {
                        SearchField(filter.search, onChange = viewModel::updateSearch)
                    } else {
                        // The "ch1" logo in place of the app name; screen readers still say "Chain One".
                        Icon(
                            painterResource(R.drawable.ic_logo),
                            contentDescription = stringResource(R.string.app_name),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp),
                        )
                    }
                },
                actions = {
                    if (state.isSearching) {
                        if (filter.search.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearch("") }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.library_clear_search))
                            }
                        }
                    } else {
                        IconButton(onClick = viewModel::openSearch) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.library_search))
                        }
                    }
                    IconToggleButton(
                        checked = filter.favoritesOnly,
                        onCheckedChange = { viewModel.toggleFavoritesOnly() },
                        // Stays gray when on, like the other top-bar icons; the filled heart shows the state.
                        colors = IconButtonDefaults.iconToggleButtonColors(checkedContentColor = LocalContentColor.current),
                    ) {
                        Icon(
                            if (filter.favoritesOnly) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = stringResource(R.string.library_favorites_only),
                        )
                    }
                    IconButton(onClick = viewModel::toggleLayout) {
                        if (state.layout == LibraryLayout.GRID) {
                            Icon(
                                Icons.AutoMirrored.Filled.List,
                                contentDescription = stringResource(R.string.library_show_list),
                            )
                        } else {
                            Icon(
                                painterResource(R.drawable.ic_grid_view),
                                contentDescription = stringResource(R.string.library_show_grid),
                            )
                        }
                    }
                },
            )
        },
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
            if (state.hasPatterns) {
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
        RenameTagDialog(
            tag = tag,
            onRename = { name ->
                viewModel.renameTag(tag.id, name)
                renamingTagId = null
            },
            onDismiss = { renamingTagId = null },
        )
    }
    state.tags.firstOrNull { it.id == deletingTagId }?.let { tag ->
        AlertDialog(
            onDismissRequest = { deletingTagId = null },
            title = { Text(stringResource(R.string.tag_delete_title, tag.name)) },
            text = { Text(pluralStringResource(R.plurals.tag_delete_message, tag.patternCount, tag.patternCount)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTag(tag.id)
                    deletingTagId = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingTagId = null }) { Text(stringResource(R.string.action_cancel)) }
            },
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

/** The search box that replaces the logo while searching. It opens with the keyboard up. */
@Composable
private fun SearchField(text: String, onChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TextField(
        value = text,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.library_search_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
    )
}

/** Status and tag filters under the top bar; it scrolls sideways on narrow screens. */
@Composable
private fun FilterRow(
    filter: LibraryFilter,
    tags: List<TagCount>,
    onToggleStatus: (PatternStatus) -> Unit,
    onOpenTags: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PatternStatus.entries.forEach { status ->
            StatusChip(status, selected = status in filter.statuses, onClick = { onToggleStatus(status) })
        }
        val selectedTags = tags.filter { it.id in filter.tagIds }
        FilterChip(
            selected = selectedTags.isNotEmpty(),
            onClick = onOpenTags,
            label = {
                Text(
                    when (selectedTags.size) {
                        0 -> stringResource(R.string.library_filter_tags)
                        1 -> selectedTags.single().name
                        else -> pluralStringResource(R.plurals.library_tags_selected, selectedTags.size, selectedTags.size)
                    },
                )
            },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        if (filter.isActive) {
            TextButton(onClick = onClear) { Text(stringResource(R.string.library_clear_filters)) }
        }
    }
}

/** Lets the person pick tags to filter by, and rename or delete them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagFilterSheet(
    tags: List<TagCount>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    onClear: () -> Unit,
    onRename: (TagCount) -> Unit,
    onDelete: (TagCount) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Text(
                stringResource(R.string.tags_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (tags.isEmpty()) {
                Text(
                    stringResource(R.string.tags_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            } else {
                Text(
                    stringResource(R.string.tags_sheet_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(tags, key = { it.id }) { tag ->
                        TagRow(
                            tag = tag,
                            checked = tag.id in selected,
                            onToggle = { onToggle(tag.id) },
                            onRename = { onRename(tag) },
                            onDelete = { onDelete(tag) },
                        )
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                if (selected.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) }
                }
                Button(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
            }
        }
    }
}

@Composable
private fun TagRow(tag: TagCount, checked: Boolean, onToggle: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    ListItem(
        leadingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        headlineContent = { Text(tag.name) },
        supportingContent = { Text(pluralStringResource(R.plurals.tag_pattern_count, tag.patternCount, tag.patternCount)) },
        trailingContent = {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.tag_options, tag.name))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        onClick = {
                            showMenu = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() }),
    )
}

/** Opens with the old name selected, so typing replaces it. */
@Composable
private fun RenameTagDialog(tag: TagCount, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var field by rememberSaveable(tag.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(tag.name, selection = TextRange(0, tag.name.length)))
    }
    val name = field.text
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tag_rename_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onRename(name) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                Text(stringResource(R.string.tag_rename_hint), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onRename(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_rename)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun PatternGrid(
    items: List<LibraryItem>,
    onOpen: (Long) -> Unit,
    onToggleFavorite: (Pattern) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = modifier,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = ListBottomPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.pattern.id }) { item ->
            Card(onClick = { onOpen(item.pattern.id) }) {
                Box {
                    PatternCover(
                        model = item.cover,
                        title = item.pattern.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 5f),
                    )
                    FavoriteButton(
                        pattern = item.pattern,
                        onToggle = onToggleFavorite,
                        // A soft backdrop keeps the heart visible on top of any photo.
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), CircleShape),
                    )
                }
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        item.pattern.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.pattern.designer.isNotBlank()) {
                        Text(
                            item.pattern.designer,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    StatusBadge(item.pattern.status, Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun PatternList(
    items: List<LibraryItem>,
    onOpen: (Long) -> Unit,
    onToggleFavorite: (Pattern) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = ListBottomPadding)) {
        items(items, key = { it.pattern.id }) { item ->
            ListItem(
                modifier = Modifier.clickable { onOpen(item.pattern.id) },
                leadingContent = {
                    PatternCover(
                        model = item.cover,
                        title = item.pattern.title,
                        letterStyle = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                },
                headlineContent = { Text(item.pattern.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Column {
                        if (item.pattern.designer.isNotBlank()) {
                            Text(item.pattern.designer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        StatusBadge(item.pattern.status, Modifier.padding(top = 4.dp))
                    }
                },
                trailingContent = { FavoriteButton(item.pattern, onToggleFavorite) },
            )
        }
    }
}

@Composable
private fun FavoriteButton(pattern: Pattern, onToggle: (Pattern) -> Unit, modifier: Modifier = Modifier) {
    IconToggleButton(
        checked = pattern.isFavorite,
        onCheckedChange = { onToggle(pattern) },
        modifier = modifier,
    ) {
        Icon(
            if (pattern.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = stringResource(
                if (pattern.isFavorite) R.string.action_unfavorite else R.string.action_favorite,
            ),
            tint = if (pattern.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
