package io.github.juliegaunt.chainone.ui.pattern

import io.github.juliegaunt.chainone.ui.pattern.InstructionBlock.Bullet
import io.github.juliegaunt.chainone.ui.pattern.InstructionBlock.Gap
import io.github.juliegaunt.chainone.ui.pattern.InstructionBlock.Heading
import io.github.juliegaunt.chainone.ui.pattern.InstructionBlock.Paragraph
import org.junit.Assert.assertEquals
import org.junit.Test

class InstructionBlocksTest {

    @Test
    fun recognisesHeadingsBulletsAndParagraphs() {
        val text = """
            ## Round 1
            Ch 4, sl st to join.
            - 12 dc in ring
            * sl st to first dc
        """.trimIndent()

        assertEquals(
            listOf(
                Heading("Round 1"),
                Paragraph("Ch 4, sl st to join."),
                Bullet("12 dc in ring"),
                Bullet("sl st to first dc"),
            ),
            parseInstructionBlocks(text),
        )
    }

    @Test
    fun collapsesRepeatedBlankLinesAndTrimsEnds() {
        val text = "\n\nRow 1: sc across\n\n\n\nRow 2: ch 1, turn\n\n"

        assertEquals(
            listOf(Paragraph("Row 1: sc across"), Gap, Paragraph("Row 2: ch 1, turn")),
            parseInstructionBlocks(text),
        )
    }

    @Test
    fun hashWithoutSpaceIsNotAHeading() {
        // Stitch counts like "#3" shouldn't turn into headings.
        assertEquals(listOf(Paragraph("##3 rows remaining")), parseInstructionBlocks("##3 rows remaining"))
    }
}
