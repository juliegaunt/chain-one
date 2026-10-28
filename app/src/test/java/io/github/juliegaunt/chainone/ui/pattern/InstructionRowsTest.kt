package io.github.juliegaunt.chainone.ui.pattern

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sample instructions are written for these tests, laid out like the patterns that inspired them. */
class InstructionRowsTest {

    /** Numbered steps, starting over for each piece. */
    private val mitts = """
        ## Instructions
        Make 2.
        ## Cuff
        1. Ch 8. Hdc in 2nd ch from hook and across. Turn. – 7 st
        2. Ch 1. Hdc in back loop of each st. Turn. – 7 st
        Repeat row 2 until you have 18 (20, 22) rows.
        Gauge check: the cuff should be 7 in long.
        ## Hand
        1. Ch 1. Work 30 sc along the cuff edge. Ss to 1st sc. – 30 st
        2. Ch 3. Dc in each st around. Ss to top of ch 3.
        3. Ch 1. Hdc in each st around.
        4. Repeat round 2.
        Weave in your ends.
    """.trimIndent()

    /** "R" rows with ranges, repeated for each size. */
    private val beanie = """
        Child (6-10 years)
        Finished size: 19 in around, 8 in tall, for a 21 in head.
        R1: Magic ring, ch 3, 12 dc, sl st to join. [12]
        R2: Ch 3, 2 dc in each st around. [24]
        R3-R14: Sk the first dc, sc and dc in the next sc around. [24]
        R15: Sc in each st around. [24]
        Adult (S)
        R1: Magic ring, ch 3, 12 dc, sl st to join. [12]
        R2: Ch 3, 2 dc in each st around. [24]
        R3-R16: Sk the first dc, sc and dc in the next sc around. [24]
    """.trimIndent()

    /** "Rnd" rows without a colon, wrapping across lines, then other text. */
    private val square = """
        Rnd 1 Ch 3 (counts as dc), 2 dc in ring, *ch 2, 3 dc in ring, repeat from * 3 times, ch 2; join with
        sl st in top of beg ch-3. (12 dc) Tie off.
        Rnd 2 Join next color in any ch-2 sp, ch 3, (2 dc, ch 2, 3 dc) in same sp, ch 1,
        repeat around; join. Tie off.
        Assembly
        Whip stitch the squares together as pictured, matching the corners carefully, then
        make a chain about 61
        ch sts long for each handle and attach it to the top of the next square.
    """.trimIndent()

    private fun lineOf(text: String, start: String) = text.lines().indexOfFirst { it.startsWith(start) }

    @Test
    fun recognisesTheCommonWaysOfWritingRows() {
        listOf(
            "Row 1: sc across" to (1 to 1),
            "ROW 12. Ch 1, turn" to (12 to 12),
            "Rnd 3 Join next color" to (3 to 3),
            "Round 4 - sc around" to (4 to 4),
            "Rnds 5-8: Repeat rnd 4." to (5 to 8),
            "Rows 9 – 12: sc across" to (9 to 12),
            "Rows 2 to 4: hdc across" to (2 to 4),
            "R7-R19: Sk the first dc" to (7 to 19),
            "R20: sc around" to (20 to 20),
            "Row 5 (RS): dc across" to (5 to 5),
            "- Row 6: sl st across" to (6 to 6),
            "1. Ch 6. Hdc in 2nd ch" to (1 to 1),
            "14) Fasten off" to (14 to 14),
        ).forEach { (line, expected) -> assertEquals(line, expected, InstructionRows.rowRange(line)) }
    }

    @Test
    fun ignoresLinesThatOnlyStartWithANumberOrWord() {
        listOf(
            "17 (19, 21) rows",
            "61 ch sts long for each handle",
            "3.5 mm hook",
            "2 skeins of worsted yarn",
            "R2 wool from the stash",
            "Rows are worked flat.",
            "Square #1: Make 4 Rnd 1 B, Rnd 2 D",
        ).forEach { assertNull(it, InstructionRows.rowRange(it)) }
    }

    @Test
    fun numberingStartingOverMakesNewSectionsNamedByTheirHeading() {
        val rows = InstructionRows.of(mitts)

        assertEquals(listOf("Cuff", "Hand"), rows.sections.map { it.title })
        assertEquals(listOf(1, 2), rows.rows.filter { it.section == 0 }.map { it.from })
        assertEquals(listOf(1, 2, 3, 4), rows.rows.filter { it.section == 1 }.map { it.from })
    }

    @Test
    fun countsFindTheirRowInTheChosenSection() {
        val rows = InstructionRows.of(mitts)

        assertNull("nothing counted yet", rows.rowFor(0, 0))
        assertEquals(lineOf(mitts, "1. Ch 8"), rows.rowFor(0, 1)!!.firstLine)
        assertEquals(lineOf(mitts, "3. Ch 1"), rows.rowFor(1, 3)!!.firstLine)
        // Rows 3–18 of the cuff aren't written out: stay on row 2 and its "repeat" note.
        val cuffRow2 = rows.rowFor(0, 9)!!
        assertEquals(lineOf(mitts, "2. Ch 1"), cuffRow2.firstLine)
        assertEquals(lineOf(mitts, "Repeat row 2"), cuffRow2.lastLine)
    }

    @Test
    fun aNewSentenceAfterTheRowIsNotPartOfIt() {
        val rows = InstructionRows.of(mitts)

        // "Repeat row 2 …" belongs to row 2; "Gauge check: …" starts something new.
        assertEquals(lineOf(mitts, "Repeat row 2"), rows.rowFor(0, 2)!!.lastLine)
        assertNull(rows.rowAtLine(lineOf(mitts, "Gauge check")))
    }

    @Test
    fun rangesCoverEveryCountInThem() {
        val rows = InstructionRows.of(beanie)

        assertEquals(listOf("Child (6-10 years)", "Adult (S)"), rows.sections.map { it.title })
        val range = lineOf(beanie, "R3-R14")
        listOf(3, 9, 14).forEach { assertEquals(range, rows.rowFor(0, it)!!.firstLine) }
        assertEquals(lineOf(beanie, "R15"), rows.rowFor(0, 15)!!.firstLine)
        // The second size has its own, longer range.
        assertEquals(beanie.lines().indexOfLast { it.startsWith("R3-R16") }, rows.rowFor(1, 15)!!.firstLine)
    }

    @Test
    fun wrappedRowsIncludeTheirFollowingLinesButStopAtTheNextPart() {
        val rows = InstructionRows.of(square)

        val rnd1 = rows.rowFor(0, 1)!!
        assertEquals(0..1, rnd1.lines)
        val rnd2 = rows.rowFor(0, 2)!!
        assertEquals(2..3, rnd2.lines)
        // The "Assembly" label ends round 2, and "61 ch sts" isn't mistaken for row 61.
        assertEquals(2, rows.rows.size)
        assertEquals(rnd2, rows.rowFor(0, 61))
    }

    @Test
    fun aRowCanBeFoundFromAnyOfItsLines() {
        val rows = InstructionRows.of(square)

        assertEquals(1, rows.rowAtLine(1)!!.from)
        assertEquals(2, rows.rowAtLine(3)!!.from)
        assertNull(rows.rowAtLine(5))
    }

    @Test
    fun instructionsWithoutRowsHaveNone() {
        val rows = InstructionRows.of("Work 2–3 rounds of single crochet around the bag.\nWeave in ends.")

        assertTrue(rows.isEmpty)
        assertNull(rows.rowFor(0, 3))
    }
}
