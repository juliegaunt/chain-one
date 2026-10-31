package io.github.juliegaunt.chainone.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.settings.LibraryLayout

/**
 * The library's top bar: the logo, or the search box while searching, then search, favorites,
 * grid/list and the ⋮ menu (categories and backups).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryTopBar(
    state: LibraryUiState,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleFavorites: () -> Unit,
    onToggleLayout: () -> Unit,
    onCategories: () -> Unit,
    onBackUp: () -> Unit,
    onRestore: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            if (state.isSearching) {
                IconButton(onClick = onCloseSearch) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.library_close_search))
                }
            }
        },
        title = {
            if (state.isSearching) {
                // Opened by tapping a tag on a pattern's page, the tag is the search; no keyboard.
                SearchField(
                    state.filter.search,
                    onChange = onSearchChange,
                    focus = state.filter.tagIds.isEmpty() && state.filter.statuses.isEmpty(),
                )
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
                if (state.filter.search.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.library_clear_search))
                    }
                }
            } else {
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.library_search))
                }
            }
            IconToggleButton(
                checked = state.filter.favoritesOnly,
                onCheckedChange = { onToggleFavorites() },
                // Stays gray when on, like the other top-bar icons; the filled heart shows the state.
                colors = IconButtonDefaults.iconToggleButtonColors(checkedContentColor = LocalContentColor.current),
            ) {
                Icon(
                    if (state.filter.favoritesOnly) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(R.string.library_favorites_only),
                )
            }
            IconButton(onClick = onToggleLayout) {
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
            if (!state.isSearching) {
                LibraryMenu(onCategories = onCategories, onBackUp = onBackUp, onRestore = onRestore)
            }
        },
    )
}

/** The top bar's ⋮ menu: the categories, and backing up and restoring the whole library. */
@Composable
private fun LibraryMenu(onCategories: () -> Unit, onBackUp: () -> Unit, onRestore: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.categories_title)) },
                onClick = {
                    expanded = false
                    onCategories()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.backup_menu_back_up)) },
                onClick = {
                    expanded = false
                    onBackUp()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.backup_menu_restore)) },
                onClick = {
                    expanded = false
                    onRestore()
                },
            )
        }
    }
}

/** The search box that replaces the logo while searching. With [focus], it opens with the keyboard up. */
@Composable
private fun SearchField(text: String, onChange: (String) -> Unit, focus: Boolean) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { if (focus) focusRequester.requestFocus() }
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
