package io.github.juliegaunt.chainone.ui.pattern

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.db.Counter
import io.github.juliegaunt.chainone.data.db.Pattern

sealed interface InstructionBlock {
    data class Heading(val text: String) : InstructionBlock
    data class Bullet(val text: String) : InstructionBlock
    data class Paragraph(val text: String) : InstructionBlock
    data object Gap : InstructionBlock
}

/** A block together with the line of the text it came from, so rows can be matched to it. */
data class LineBlock(val line: Int, val block: InstructionBlock)

/**
 * Splits instruction text into blocks: `## ` starts a heading, `- ` or `* ` a bullet,
 * and blank lines become gaps (several in a row count as one).
 */
fun parseInstructionBlocks(text: String): List<InstructionBlock> = parseInstructionLines(text).map { it.block }

/** [parseInstructionBlocks], keeping each block's line number. */
fun parseInstructionLines(text: String): List<LineBlock> {
    val blocks = mutableListOf<LineBlock>()
    for ((index, raw) in text.lines().withIndex()) {
        val line = raw.trim()
        val block = when {
            line.isEmpty() -> InstructionBlock.Gap
            line.startsWith("## ") -> InstructionBlock.Heading(line.removePrefix("## ").trim())
            line.startsWith("- ") || line.startsWith("* ") -> InstructionBlock.Bullet(line.drop(2).trim())
            else -> InstructionBlock.Paragraph(line)
        }
        if (block == InstructionBlock.Gap && (blocks.isEmpty() || blocks.last().block == InstructionBlock.Gap)) continue
        blocks += LineBlock(index, block)
    }
    if (blocks.lastOrNull()?.block == InstructionBlock.Gap) blocks.removeAt(blocks.lastIndex)
    return blocks
}

/**
 * Shows instruction text. With [rows], the [highlighted] row is marked and kept in view as the
 * counter moves, and tapping any row calls [onRowTap] (to move the counter there).
 */
@Composable
fun InstructionsText(
    text: String,
    modifier: Modifier = Modifier,
    rows: InstructionRows? = null,
    highlighted: InstructionRow? = null,
    onRowTap: ((InstructionRow) -> Unit)? = null,
) {
    val blocks = remember(text) { parseInstructionLines(text) }
    // Split into runs: each row's lines stay together, so a row is highlighted and tapped as one.
    val runs = remember(blocks, rows) { blocks.groupConsecutive { rows?.rowAtLine(it.line) } }
    // Selectable so a row can be copied into a note or message.
    SelectionContainer(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((row, run) in runs) {
                if (row == null) {
                    // The same side padding as rows, so everything lines up.
                    Column(Modifier.padding(horizontal = RowPadding), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        run.forEach { InstructionBlockText(it.block) }
                    }
                } else {
                    RowRun(row, run, isHighlighted = row == highlighted, onTap = onRowTap?.let { { it(row) } })
                }
            }
        }
    }
}

/** One row's lines; the highlighted row gets a soft background and scrolls into view. */
@Composable
private fun RowRun(row: InstructionRow, run: List<LineBlock>, isHighlighted: Boolean, onTap: (() -> Unit)?) {
    val requester = remember { BringIntoViewRequester() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    LaunchedEffect(isHighlighted, size) {
        if (isHighlighted && size != IntSize.Zero) {
            // Some room above for context and more below, so the next rows are in view too.
            val above = with(density) { 96.dp.toPx() }
            val below = with(density) { 200.dp.toPx() }
            requester.bringIntoView(Rect(0f, -above, size.width.toFloat(), size.height + below))
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(requester)
            .onSizeChanged { size = it }
            .then(
                if (isHighlighted) {
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                },
            )
            .then(if (onTap != null) Modifier.clickable(onClickLabel = stringResource(R.string.instructions_move_counter), onClick = onTap) else Modifier)
            .padding(horizontal = RowPadding, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        run.forEach { InstructionBlockText(it.block, emphasise = isHighlighted) }
    }
}

@Composable
private fun InstructionBlockText(block: InstructionBlock, emphasise: Boolean = false) {
    val body = MaterialTheme.typography.bodyLarge.let { if (emphasise) it.copy(fontWeight = FontWeight.Medium) else it }
    when (block) {
        is InstructionBlock.Heading -> Text(
            block.text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
        is InstructionBlock.Bullet -> Row {
            Text("•", modifier = Modifier.padding(end = 8.dp), style = body)
            Text(block.text, style = body)
        }
        is InstructionBlock.Paragraph -> Text(block.text, style = body)
        InstructionBlock.Gap -> Spacer(Modifier.height(4.dp))
    }
}

/** Room around a row for its highlight; other text is padded the same so it lines up. */
private val RowPadding = 8.dp

/** Groups neighbouring items that share the same [key] (null keys group too). */
private fun <T, K> List<T>.groupConsecutive(key: (T) -> K): List<Pair<K, List<T>>> {
    val groups = mutableListOf<Pair<K, MutableList<T>>>()
    for (item in this) {
        val k = key(item)
        if (groups.isNotEmpty() && groups.last().first == k) groups.last().second += item else groups += k to mutableListOf(item)
    }
    return groups
}

/**
 * The instructions, with the row the main counter is on highlighted. Tapping a row moves the
 * counter there and makes it follow that row's section (piece or size) from then on.
 */
@Composable
fun InstructionsTab(
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
