package io.github.juliegaunt.chainone.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.db.PatternSummary
import io.github.juliegaunt.chainone.ui.components.PatternCover
import io.github.juliegaunt.chainone.ui.components.StatusBadge

// Leaves room under the last row so the floating "Add" button doesn't cover it.
private val ListBottomPadding = 96.dp

/**
 * Patterns as cards with their cover photos, two or more to a row, under a foldable header for
 * each section when [showHeaders] (that is, once there are categories).
 */
@Composable
fun PatternGrid(
    sections: List<LibrarySection>,
    showHeaders: Boolean,
    onToggleSection: (LibrarySection) -> Unit,
    onOpen: (Long) -> Unit,
    onToggleFavorite: (PatternSummary) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        state = state,
        modifier = modifier,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = ListBottomPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        for (section in sections) {
            if (showHeaders) {
                item(key = headerKey(section), span = { GridItemSpan(maxLineSpan) }, contentType = HEADER) {
                    SectionHeader(section, onToggleSection)
                }
            }
            if (!section.isFolded) {
                items(section.items, key = { it.pattern.id }) { item -> PatternCard(item, onOpen, onToggleFavorite) }
            }
        }
    }
}

/** Patterns as a compact list with small covers, with the same foldable section headers as [PatternGrid]. */
@Composable
fun PatternList(
    sections: List<LibrarySection>,
    showHeaders: Boolean,
    onToggleSection: (LibrarySection) -> Unit,
    onOpen: (Long) -> Unit,
    onToggleFavorite: (PatternSummary) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
) {
    LazyColumn(modifier = modifier, state = state, contentPadding = PaddingValues(bottom = ListBottomPadding)) {
        for (section in sections) {
            if (showHeaders) {
                item(key = headerKey(section), contentType = HEADER) {
                    SectionHeader(section, onToggleSection, Modifier.padding(horizontal = 12.dp))
                }
            }
            if (!section.isFolded) {
                items(section.items, key = { it.pattern.id }) { item -> PatternRow(item, onOpen, onToggleFavorite) }
            }
        }
    }
}

/** Where [sectionKey]'s header is in the grid or list, to scroll to it; null if it isn't shown. */
fun headerIndex(sections: List<LibrarySection>, sectionKey: String): Int? {
    var index = 0
    for (section in sections) {
        if (section.key == sectionKey) return index
        index += 1 + if (section.isFolded) 0 else section.items.size
    }
    return null
}

/** A section's name and pattern count; tapping it folds or unfolds the section. */
@Composable
private fun SectionHeader(section: LibrarySection, onToggle: (LibrarySection) -> Unit, modifier: Modifier = Modifier) {
    val name = section.category?.name ?: stringResource(R.string.library_other)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = stringResource(if (section.isFolded) R.string.library_expand else R.string.library_collapse)) {
                onToggle(section)
            }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            " · ${section.items.size}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            if (section.isFolded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One pattern in the grid: its cover, title, designer and status. */
@Composable
private fun PatternCard(item: LibraryItem, onOpen: (Long) -> Unit, onToggleFavorite: (PatternSummary) -> Unit) {
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

/** One pattern in the list: a small cover, title, designer and status. */
@Composable
private fun PatternRow(item: LibraryItem, onOpen: (Long) -> Unit, onToggleFavorite: (PatternSummary) -> Unit) {
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

private fun headerKey(section: LibrarySection) = "section-${section.key}"

private const val HEADER = "header"

@Composable
private fun FavoriteButton(pattern: PatternSummary, onToggle: (PatternSummary) -> Unit, modifier: Modifier = Modifier) {
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
