package io.github.juliegaunt.chainone.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.db.Pattern
import io.github.juliegaunt.chainone.data.settings.LibraryLayout
import io.github.juliegaunt.chainone.ui.components.PatternCover

// Leaves room under the last row so the floating "Add" button doesn't cover it.
private val ListBottomPadding = 96.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenPattern: (Long) -> Unit,
    onAddPattern: () -> Unit,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconToggleButton(
                        checked = state.favoritesOnly,
                        onCheckedChange = { viewModel.toggleFavoritesOnly() },
                        // Stays gray when on, like the other top-bar icons; the filled heart shows the state.
                        colors = IconButtonDefaults.iconToggleButtonColors(checkedContentColor = LocalContentColor.current),
                    ) {
                        Icon(
                            if (state.favoritesOnly) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
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
            ExtendedFloatingActionButton(
                onClick = onAddPattern,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.library_add_pattern)) },
            )
        },
    ) { padding ->
        val modifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when {
            state.isLoading -> Unit
            state.items.isEmpty() -> EmptyLibrary(state.favoritesOnly, modifier)
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

@Composable
private fun EmptyLibrary(favoritesOnly: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(
                if (favoritesOnly) R.string.library_empty_favorites else R.string.library_empty,
            ),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
                supportingContent = item.pattern.designer.takeIf { it.isNotBlank() }?.let { designer ->
                    { Text(designer, maxLines = 1, overflow = TextOverflow.Ellipsis) }
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
