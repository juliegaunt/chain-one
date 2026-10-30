package io.github.juliegaunt.chainone.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.db.LibraryFilter
import io.github.juliegaunt.chainone.data.db.PatternStatus
import io.github.juliegaunt.chainone.data.db.TagCount
import io.github.juliegaunt.chainone.ui.components.StatusChip

/** Status and tag filters under the search box; it scrolls sideways on narrow screens. */
@Composable
fun FilterRow(
    filter: LibraryFilter,
    tags: List<TagCount>,
    onToggleStatus: (PatternStatus) -> Unit,
    onOpenTags: () -> Unit,
    onClear: () -> Unit,
) {
    // Picking a filter means the person is done typing; the keyboard would hide the results.
    val focusManager = LocalFocusManager.current
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PatternStatus.entries.forEach { status ->
            StatusChip(status, selected = status in filter.statuses, onClick = {
                focusManager.clearFocus()
                onToggleStatus(status)
            })
        }
        val selectedTags = tags.filter { it.id in filter.tagIds }
        FilterChip(
            selected = selectedTags.isNotEmpty(),
            onClick = {
                focusManager.clearFocus()
                onOpenTags()
            },
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
fun TagFilterSheet(
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
