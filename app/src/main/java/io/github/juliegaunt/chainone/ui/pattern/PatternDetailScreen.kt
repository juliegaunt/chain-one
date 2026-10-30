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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.db.Counter
import io.github.juliegaunt.chainone.data.db.Pattern
import io.github.juliegaunt.chainone.data.db.PatternAttachment
import io.github.juliegaunt.chainone.data.db.PatternStatus
import io.github.juliegaunt.chainone.data.db.RowMarker
import io.github.juliegaunt.chainone.data.db.rowMarker
import io.github.juliegaunt.chainone.data.importer.UrlTools
import io.github.juliegaunt.chainone.data.db.Tag
import io.github.juliegaunt.chainone.ui.components.PatternCover
import io.github.juliegaunt.chainone.ui.components.colors
import io.github.juliegaunt.chainone.ui.components.labelRes
import io.github.juliegaunt.chainone.ui.counter.CounterBar
import io.github.juliegaunt.chainone.ui.counter.CounterTab
import io.github.juliegaunt.chainone.ui.counter.CounterUiState
import io.github.juliegaunt.chainone.ui.counter.CounterViewModel
import io.github.juliegaunt.chainone.ui.counter.LocalVolumeKeys
import java.io.File

private const val MAX_FIXED_TABS = 4

private enum class DetailTab(val labelRes: Int) {
    OVERVIEW(R.string.tab_overview),
    INSTRUCTIONS(R.string.tab_instructions),
    COUNTER(R.string.tab_counter),
    WEB_PAGE(R.string.tab_web_page),
    PDF(R.string.tab_pdf),
}

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewTab(
    pattern: Pattern,
    cover: File?,
    tags: List<Tag>,
    onStatusChange: (PatternStatus) -> Unit,
    onShowTag: (Long) -> Unit,
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

            StatusPicker(pattern.status, onStatusChange)

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

/**
 * The instructions, with the row the main counter is on highlighted. Tapping a row moves the
 * counter there and makes it follow that row's section (piece or size) from then on.
 */
@Composable
private fun InstructionsTab(
    pattern: Pattern,
    counter: Counter?,
    onRowTap: (InstructionRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(pattern.instructions) { InstructionRows.of(pattern.instructions) }
    val section = (counter?.rowSection ?: 0).coerceIn(0, (rows.sections.size - 1).coerceAtLeast(0))
    val highlighted = counter?.let { rows.rowFor(section, it.value) }
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (pattern.instructions.isBlank()) {
            Text(stringResource(R.string.instructions_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            if (!rows.isEmpty) {
                Text(
                    if (rows.sections.size > 1) {
                        val title = rows.sections[section].title ?: stringResource(R.string.instructions_section_number, section + 1)
                        stringResource(R.string.instructions_following, title)
                    } else {
                        stringResource(R.string.instructions_rows_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            InstructionsText(pattern.instructions, rows = rows, highlighted = highlighted, onRowTap = onRowTap)
        }
    }
}

/** The pattern's PDF; with several, a menu above it switches between them. */
@Composable
private fun PdfTab(
    pdfs: List<PatternAttachment>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    fileOf: (PatternAttachment) -> File,
    onPageChange: (pdfId: Long, page: Int) -> Unit,
    onMarkerChange: (pdfId: Long, RowMarker?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pdf = pdfs.firstOrNull { it.id == selectedId } ?: pdfs.first()
    Column(modifier.fillMaxWidth()) {
        if (pdfs.size > 1) {
            var expanded by remember { mutableStateOf(false) }
            Box(Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = { expanded = true }) {
                    Text(pdf.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = stringResource(R.string.pdf_choose))
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    pdfs.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.displayName) },
                            trailingIcon = if (option.id == pdf.id) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else {
                                null
                            },
                            onClick = {
                                expanded = false
                                onSelect(option.id)
                            },
                        )
                    }
                }
            }
        }
        // Keyed so switching PDFs starts the viewer afresh on that PDF's own page and marker.
        key(pdf.id) {
            PdfViewer(
                file = fileOf(pdf),
                initialPage = pdf.lastPage,
                initialMarker = pdf.rowMarker,
                onPageChange = { onPageChange(pdf.id, it) },
                onMarkerChange = { onMarkerChange(pdf.id, it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
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

/**
 * While a counter is showing: keeps the screen on and lets the volume buttons count, if those
 * settings are on. Both stop when the counter goes away.
 */
@Composable
private fun CountingEffects(active: Boolean, state: CounterUiState, onVolumeUp: () -> Unit, onVolumeDown: () -> Unit) {
    val view = LocalView.current
    val keepOn = active && state.keepScreenOn
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    val volumeKeys = LocalVolumeKeys.current
    val haptics = LocalHapticFeedback.current
    val countWithKeys = active && state.countWithVolumeKeys
    val up by rememberUpdatedState(onVolumeUp)
    val down by rememberUpdatedState(onVolumeDown)
    DisposableEffect(countWithKeys) {
        val handler: (Boolean) -> Unit = { isUp ->
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            if (isUp) up() else down()
        }
        if (countWithKeys) volumeKeys.handler = handler
        onDispose { if (volumeKeys.handler === handler) volumeKeys.handler = null }
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
