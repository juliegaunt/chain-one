package io.github.juliegaunt.chainone.ui.pattern

/**
 * A row (or range of rows) written out in the instructions, such as "Row 3:", "Rnd 3",
 * "R7-R19:" or "3.". It covers lines [firstLine]..[lastLine] of the text: rows often wrap.
 */
data class InstructionRow(
    /** Which section it belongs to; numbering starting over at row 1 begins a new one. */
    val section: Int,
    val from: Int,
    val to: Int,
    val firstLine: Int,
    val lastLine: Int,
) {
    val lines: IntRange get() = firstLine..lastLine
}

/** A run of rows numbered from the start, such as one piece ("Band") or one size ("Child"). */
data class InstructionSection(val index: Int, val title: String?)

/**
 * Finds the rows in a pattern's instructions, so the Instructions tab can highlight the one
 * the counter is on. Patterns usually start over at row 1 for each piece or size; those
 * become separate sections, and the person picks one by tapping a row in it.
 */
class InstructionRows private constructor(
    val rows: List<InstructionRow>,
    val sections: List<InstructionSection>,
) {

    val isEmpty: Boolean get() = rows.isEmpty()

    /**
     * The row for [count] in [section]: the one whose number (or range) covers it, else the last
     * row written out before it ("Repeat row 2 until you have 17 rows" stays on that line).
     * Null before the section's first row, e.g. at a count of 0.
     */
    fun rowFor(section: Int, count: Int): InstructionRow? {
        val started = rows.filter { it.section == section && it.from <= count }
        return started.firstOrNull { count <= it.to } ?: started.lastOrNull()
    }

    /** The row that line [line] is part of, if any. */
    fun rowAtLine(line: Int): InstructionRow? = rows.firstOrNull { line in it.lines }

    companion object {
        private const val WORD = """(?:rows?|rnds?|rounds?|r)"""

        // "Row 3", "Rnds 3-6", "R7-R19", "Rows 3 to 6", "Row 5 (RS)", followed by ":", ".", ")", a dash or text.
        private val WORD_ROW = Regex(
            """^(?:[-*•]\s+)?(?<word>$WORD)\s*\.?\s*(?<a>\d{1,3})""" +
                """(?:\s*(?:-|–|—|to|through|thru|&|and)\s*(?:$WORD\s*\.?\s*)?(?<b>\d{1,3}))?""" +
                """(?:\s*\([^)]{0,30}\))?(?<sep>\s*[:.)\-–—]|\s+(?=\S))""",
            RegexOption.IGNORE_CASE,
        )

        // "3." or "3)" then text: a numbered step. A bare "17 (19, 21) rows" or "3.5 mm" isn't one.
        private val NUMBERED_ROW = Regex("""^(?<a>\d{1,3})(?:\s*[-–]\s*(?<b>\d{1,3}))?[.)]\s+(?=\p{L})""")

        /** Rows rarely run longer than this many extra lines; past it, it's probably other text. */
        private const val MAX_CONTINUATION_LINES = 4
        private const val MAX_RANGE = 300
        private const val MAX_LABEL_LENGTH = 40

        fun of(text: String): InstructionRows {
            val lines = text.lines()
            val rows = mutableListOf<InstructionRow>()
            val sections = mutableListOf<InstructionSection>()
            var lastLabel: String? = null
            var continuing = false

            for ((index, raw) in lines.withIndex()) {
                val line = raw.trim()
                val range = rowRange(line)
                when {
                    range != null -> {
                        val (from, to) = range
                        // Numbering starting over (or not going forward) means a new piece or size.
                        val restarted = rows.isNotEmpty() && from <= rows.last().to
                        if (sections.isEmpty() || restarted) sections += InstructionSection(sections.size, lastLabel)
                        rows += InstructionRow(sections.lastIndex, from, to, index, index)
                        continuing = true
                    }
                    line.isEmpty() -> continuing = false
                    line.startsWith("## ") -> {
                        lastLabel = line.removePrefix("## ").trim().trimEnd(':')
                        continuing = false
                    }
                    isLabel(line) -> {
                        lastLabel = line.trimEnd(':')
                        continuing = false
                    }
                    continuing -> {
                        val row = rows.last()
                        if (index - row.firstLine <= MAX_CONTINUATION_LINES && continuesRow(lines[index - 1].trim(), line)) {
                            rows[rows.lastIndex] = row.copy(lastLine = index)
                        } else {
                            continuing = false
                        }
                    }
                }
            }
            return InstructionRows(rows, sections)
        }

        /** The row number (or range) a line starts with, or null if it doesn't start a row. */
        internal fun rowRange(line: String): Pair<Int, Int>? {
            val match = WORD_ROW.find(line)?.takeIf { m ->
                // A lone "R" is only trusted when followed by ":" etc., not "R2 wool…".
                !m.groups["word"]!!.value.equals("r", ignoreCase = true) || m.groups["sep"]!!.value.isNotBlank()
            } ?: NUMBERED_ROW.find(line) ?: return null
            val from = match.groups["a"]!!.value.toInt()
            val to = match.groups["b"]?.value?.toInt()?.takeIf { it >= from && it - from <= MAX_RANGE } ?: from
            return from to to
        }

        /**
         * Whether [line] is still part of the row above: the text wrapped mid-sentence, or it
         * carries on in lowercase, or it says how to repeat the row. A fresh sentence such as
         * "Gauge check: …" is the pattern moving on.
         */
        private fun continuesRow(previous: String, line: String): Boolean =
            !previous.endsWithSentence() || line.first().isLowerCase() || line.startsWith("Rep", ignoreCase = true)

        private fun String.endsWithSentence() = isNotEmpty() && last() in ".!?)]"

        /** A short title line such as "Glove Body", "Child (6-10 years)" or "Assembly:". */
        private fun isLabel(line: String): Boolean =
            line.length <= MAX_LABEL_LENGTH && line.first().isUpperCase() &&
                !line.endsWith('.') && !line.endsWith(',') && !line.startsWith("- ")
    }
}
