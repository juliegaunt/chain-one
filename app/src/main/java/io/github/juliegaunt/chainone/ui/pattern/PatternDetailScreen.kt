package io.github.juliegaunt.chainone.ui.pattern

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.ui.components.ConfirmDialog
import io.github.juliegaunt.chainone.ui.components.colors
import io.github.juliegaunt.chainone.ui.components.labelRes
import io.github.juliegaunt.chainone.ui.counter.CounterBar
import io.github.juliegaunt.chainone.ui.counter.CounterTab
import io.github.juliegaunt.chainone.ui.counter.CounterViewModel
import io.github.juliegaunt.chainone.ui.counter.CountingEffects
import io.github.juliegaunt.chainone.ui.pdf.PdfTab

private const val MAX_FIXED_TABS = 4

private enum class DetailTab(val labelRes: Int) {
    OVERVIEW(R.string.tab_overview),
    INSTRUCTIONS(R.string.tab_instructions),
    COUNTER(R.string.tab_counter),
    WEB_PAGE(R.string.tab_web_page),
    PDF(R.string.tab_pdf),
}

/** One pattern's page: Overview, Instructions and Counter tabs, plus Web page and PDF when it has them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatternDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onShowTag: (Long) -> Unit,
    viewModel: PatternDetailViewModel = viewModel(factory = PatternDetailViewModel.Factory),
    counterViewModel: CounterViewModel = viewModel(factory = CounterViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val counters by counterViewModel.uiState.collectAsStateWithLifecycle()
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf<DetailTab?>(null) }
    var selectedPdfId by rememberSaveable { mutableStateOf<Long?>(null) }

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
                        IconToggleButton(
                            checked = pattern.isFavorite,
                            onCheckedChange = { viewModel.toggleFavorite() },
                            // Stays gray when on, like the other top-bar icons; the filled heart shows the state.
                            colors = IconButtonDefaults.iconToggleButtonColors(checkedContentColor = LocalContentColor.current),
                        ) {
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
                    val tabs = DetailTab.entries.filter {
                        when (it) {
                            DetailTab.WEB_PAGE -> state.savedPage != null
                            DetailTab.PDF -> state.pdfs.isNotEmpty()
                            else -> true
                        }
                    }
                    // Without typed-in instructions, the PDF or the saved page is where they are.
                    val defaultTab = when {
                        pattern.instructions.isNotBlank() -> DetailTab.OVERVIEW
                        state.pdfs.isNotEmpty() -> DetailTab.PDF
                        state.savedPage != null -> DetailTab.WEB_PAGE
                        else -> DetailTab.OVERVIEW
                    }
                    val currentTab = selectedTab?.takeIf { it in tabs } ?: defaultTab
                    val tabContent: @Composable () -> Unit = {
                        tabs.forEach { tab ->
                            Tab(
                                selected = currentTab == tab,
                                onClick = { selectedTab = tab },
                            ) { TabLabel(stringResource(tab.labelRes)) }
                        }
                    }
                    // Four tabs fit across a phone; with five, the row scrolls.
                    if (tabs.size <= MAX_FIXED_TABS) {
                        PrimaryTabRow(selectedTabIndex = tabs.indexOf(currentTab), tabs = tabContent)
                    } else {
                        PrimaryScrollableTabRow(selectedTabIndex = tabs.indexOf(currentTab), edgePadding = 0.dp, tabs = tabContent)
                    }
                    // A counter is on screen on these tabs: the Counter tab itself, or the bar under the others.
                    CountingEffects(
                        active = currentTab != DetailTab.OVERVIEW,
                        state = counters,
                        onVolumeUp = counterViewModel::incrementMain,
                        onVolumeDown = counterViewModel::decrementMain,
                    )
                    when (currentTab) {
                        DetailTab.OVERVIEW -> OverviewTab(
                            pattern = pattern,
                            cover = state.cover,
                            tags = state.tags,
                            onStatusChange = viewModel::setStatus,
                            onShowTag = onShowTag,
                        )
                        DetailTab.INSTRUCTIONS, DetailTab.WEB_PAGE, DetailTab.PDF -> Column(Modifier.fillMaxSize()) {
                            when (currentTab) {
                                DetailTab.INSTRUCTIONS -> InstructionsTab(
                                    pattern = pattern,
                                    counter = counters.main,
                                    onRowTap = { row -> counterViewModel.jumpToRow(row.from, row.section) },
                                    modifier = Modifier.weight(1f),
                                )
                                DetailTab.WEB_PAGE -> state.savedPage?.let { SavedPageView(it, Modifier.weight(1f)) }
                                else -> PdfTab(
                                    pdfs = state.pdfs,
                                    selectedId = selectedPdfId,
                                    onSelect = { selectedPdfId = it },
                                    fileOf = viewModel::pdfFile,
                                    onPageChange = viewModel::setPdfPage,
                                    onMarkerChange = viewModel::setRowMarker,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            CounterBar(
                                counter = counters.main,
                                defaultName = stringResource(R.string.counter_default_name),
                                onIncrement = counterViewModel::incrementMain,
                                onDecrement = counterViewModel::decrementMain,
                                onOpen = { selectedTab = DetailTab.COUNTER },
                            )
                        }
                        DetailTab.COUNTER -> {
                            LaunchedEffect(Unit) { counterViewModel.ensureCounter() }
                            CounterTab(counters, counterViewModel)
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete && pattern != null) {
        ConfirmDialog(
            title = stringResource(R.string.delete_pattern_title),
            text = stringResource(R.string.delete_pattern_message, pattern.title),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = viewModel::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}

/**
 * A tab label with less side padding than Material's default, so four tabs fit on a phone, and
 * kept to one line: with large system text it shrinks a little instead of wrapping.
 */
@Composable
private fun TabLabel(text: String) {
    val style = MaterialTheme.typography.titleSmall
    val color = LocalContentColor.current
    BasicText(
        text,
        style = style,
        color = { color },
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = style.fontSize),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 14.dp),
    )
}
