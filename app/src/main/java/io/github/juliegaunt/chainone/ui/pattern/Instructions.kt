package io.github.juliegaunt.chainone.ui.pattern

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

sealed interface InstructionBlock {
    data class Heading(val text: String) : InstructionBlock
    data class Bullet(val text: String) : InstructionBlock
    data class Paragraph(val text: String) : InstructionBlock
    data object Gap : InstructionBlock
}

/**
 * Splits instruction text into blocks: `## ` starts a heading, `- ` or `* ` a bullet,
 * and blank lines become gaps (several in a row count as one).
 */
fun parseInstructionBlocks(text: String): List<InstructionBlock> {
    val blocks = mutableListOf<InstructionBlock>()
    for (raw in text.lines()) {
        val line = raw.trim()
        val block = when {
            line.isEmpty() -> InstructionBlock.Gap
            line.startsWith("## ") -> InstructionBlock.Heading(line.removePrefix("## ").trim())
            line.startsWith("- ") || line.startsWith("* ") -> InstructionBlock.Bullet(line.drop(2).trim())
            else -> InstructionBlock.Paragraph(line)
        }
        if (block == InstructionBlock.Gap && (blocks.isEmpty() || blocks.last() == InstructionBlock.Gap)) continue
        blocks += block
    }
    if (blocks.lastOrNull() == InstructionBlock.Gap) blocks.removeAt(blocks.lastIndex)
    return blocks
}

@Composable
fun InstructionsText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseInstructionBlocks(text) }
    // Selectable so a row can be copied into a note or message.
    SelectionContainer(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (block in blocks) {
                when (block) {
                    is InstructionBlock.Heading -> Text(
                        block.text,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    is InstructionBlock.Bullet -> Row {
                        Text("•", modifier = Modifier.padding(end = 8.dp), style = MaterialTheme.typography.bodyLarge)
                        Text(block.text, style = MaterialTheme.typography.bodyLarge)
                    }
                    is InstructionBlock.Paragraph -> Text(block.text, style = MaterialTheme.typography.bodyLarge)
                    InstructionBlock.Gap -> Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}
