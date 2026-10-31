package io.github.juliegaunt.chainone.ui.pattern

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.db.Category
import io.github.juliegaunt.chainone.data.db.Pattern
import io.github.juliegaunt.chainone.data.db.PatternStatus
import io.github.juliegaunt.chainone.data.db.Tag
import io.github.juliegaunt.chainone.data.importer.UrlTools
import io.github.juliegaunt.chainone.ui.components.PatternCover
import io.github.juliegaunt.chainone.ui.components.colors
import io.github.juliegaunt.chainone.ui.components.labelRes
import io.github.juliegaunt.chainone.ui.library.LibraryViewModel
import java.io.File

/** The pattern at a glance: photo, title, status, tags, hook and yarn, materials, notes and source link. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OverviewTab(
    pattern: Pattern,
    cover: File?,
    tags: List<Tag>,
    category: Category?,
    hasCategories: Boolean,
    onStatusChange: (PatternStatus) -> Unit,
    onShowTag: (Long) -> Unit,
    onShowSection: (String) -> Unit,
) {
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

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPicker(pattern.status, onStatusChange)
                if (hasCategories) {
                    // Tapping the category opens the library at that section.
                    AssistChip(
                        onClick = { onShowSection(LibraryViewModel.sectionKey(category)) },
                        label = { Text(category?.name ?: stringResource(R.string.library_other)) },
                    )
                }
            }

            if (tags.isNotEmpty()) {
                // Tapping a tag shows the library's other patterns with it.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { tag ->
                        AssistChip(onClick = { onShowTag(tag.id) }, label = { Text(tag.name) })
                    }
                }
            }

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
                Section(stringResource(R.string.field_source_url)) { SourceLink(url) }
            }
        }
    }
}

/** A web address opens in the browser; anything else (typed by hand, say) is shown as plain text. */
@Composable
private fun SourceLink(url: String) {
    if (!UrlTools.isWebLink(url)) {
        SelectionContainer { Text(url, style = MaterialTheme.typography.bodyMedium) }
        return
    }
    val uriHandler = LocalUriHandler.current
    Text(
        url,
        style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clickable { runCatching { uriHandler.openUri(url) } }
            .padding(vertical = 4.dp),
    )
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

/** The status badge; tapping it offers the other statuses, so changing it needs no trip to the edit screen. */
@Composable
private fun StatusPicker(status: PatternStatus, onChange: (PatternStatus) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val statusColors = status.colors
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(stringResource(status.labelRes)) },
            trailingIcon = {
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = stringResource(R.string.pattern_change_status),
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = statusColors.container,
                labelColor = statusColors.content,
                trailingIconContentColor = statusColors.content,
            ),
            border = null,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PatternStatus.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(12.dp)
                                .background(option.colors.accent, CircleShape),
                        )
                    },
                    trailingIcon = if (option == status) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        if (option != status) onChange(option)
                    },
                )
            }
        }
    }
}
