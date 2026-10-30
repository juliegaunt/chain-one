package io.github.juliegaunt.chainone.data.importer

/** Picks hook sizes and yarn weights out of free text like "5 mm (H-8) crochet hook". */
object CraftDetails {

    private val MILLIMETRES = Regex("""(\d{1,2}(?:[.,]\d{1,2})?)\s?mm\b""", RegexOption.IGNORE_CASE)

    // US hook letters, e.g. "H-8", "H/8", "K-10.5", "M/N-13". Letters alone ("size H") are too ambiguous.
    private val US_HOOK = Regex("""\b([B-Q](?:/[B-Q])?)\s?[-/]\s?(\d{1,2}(?:\.\d)?)\b""")

    // A lone letter is only trusted next to a millimetre size: "5.5 mm (I)", "US size G, 4.25 mm".
    private val US_LETTER_ONLY = Regex("""\((?:US\s*)?([B-Q])\)|\b(?:US\s+)?size\s+([B-Q])\b""")

    private val HOOK_WORD = Regex("""\bhooks?\b""", RegexOption.IGNORE_CASE)

    /** Craft Yarn Council weight categories, 0–7. */
    private val CATEGORY_NAMES = listOf("Lace", "Super fine", "Fine", "Light", "Medium", "Bulky", "Super bulky", "Jumbo")

    // Longest names first so "super bulky" wins over "bulky".
    private val WEIGHT_NAMES = listOf(
        "super bulky" to "Super bulky",
        "super chunky" to "Super chunky",
        "super fine" to "Super fine",
        "light worsted" to "Light worsted",
        "double knitting" to "DK",
        "double knit" to "DK",
        "fingering" to "Fingering",
        "worsted" to "Worsted",
        "chunky" to "Chunky",
        "bulky" to "Bulky",
        "jumbo" to "Jumbo",
        "sport" to "Sport",
        "aran" to "Aran",
        "lace" to "Lace",
        "sock" to "Sock",
        "dk" to "DK",
    )
    private val WEIGHT_NAME = Regex(
        """\b(${WEIGHT_NAMES.joinToString("|") { it.first.replace(" ", "\\s+") }})\b(?:\s*(?:weight|yarn))?""",
        RegexOption.IGNORE_CASE,
    )

    // "#4", "(4)", "[4]", "weight 4", "category 4", "medium weight 4".
    private val WEIGHT_NUMBER = Regex(
        """(?:#\s?([0-7])\b|[(\[]([0-7])[)\]]|\b(?:weight|category|cyc)\s*:?\s*#?([0-7])\b)""",
        RegexOption.IGNORE_CASE,
    )
    private val CATEGORY_WORD = Regex("""\b(super\s+fine|fine|light|medium)\s+weight\b""", RegexOption.IGNORE_CASE)
    private val YARN_WORD = Regex("""\b(yarn|weight)\b""", RegexOption.IGNORE_CASE)
    private val SPACES = Regex("""\s+""")

    /** Where one sentence ends and the next begins, for text that runs several onto one line. */
    private val SENTENCE_END = Regex("""(?<=[.!?])\s+(?=[A-Z])""")

    /**
     * The hook size from the first line of [text] that mentions a hook, such as "5 mm (H-8)".
     * Returns "" if none is found.
     */
    fun findHookSize(text: String): String {
        for (line in linesOf(text)) {
            if (!HOOK_WORD.containsMatchIn(line)) continue
            val size = hookSizeIn(line)
            if (size.isNotEmpty()) return size
        }
        return ""
    }

    /** Reads a hook size from a value that is already known to be one, e.g. a "Hook size" field. */
    fun hookSizeIn(value: String): String {
        val mm = MILLIMETRES.findAll(value).map { it.groupValues[1].replace(',', '.') + " mm" }.distinct().toList()
        val us = US_HOOK.findAll(value).map { "${it.groupValues[1]}-${it.groupValues[2]}" }.distinct().toList()
            .ifEmpty {
                if (mm.isEmpty()) emptyList()
                else US_LETTER_ONLY.findAll(value).map { m -> m.groupValues.drop(1).first { it.isNotEmpty() } }.distinct().toList()
            }
        return when {
            mm.isNotEmpty() && us.isNotEmpty() -> mm.joinToString(", ") + " (" + us.joinToString(", ") + ")"
            mm.isNotEmpty() -> mm.joinToString(", ")
            else -> us.joinToString(", ")
        }
    }

    /**
     * The yarn weight from the first line of [text] that talks about yarn or weight, such as
     * "Worsted (#4)". Returns "" if none is found.
     */
    fun findYarnWeight(text: String): String {
        for (line in linesOf(text)) {
            if (!YARN_WORD.containsMatchIn(line)) continue
            val weight = yarnWeightIn(line)
            if (weight.isNotEmpty()) return weight
        }
        return ""
    }

    /** Reads a yarn weight from a value that is already known to be one, e.g. a "Yarn weight" field. */
    fun yarnWeightIn(value: String): String {
        val name = WEIGHT_NAME.find(value)?.groupValues?.get(1)?.let { found ->
            val key = found.lowercase().replace(SPACES, " ")
            WEIGHT_NAMES.first { it.first == key }.second
        }
        val number = WEIGHT_NUMBER.find(value)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }?.toInt()
        val category = number?.let { CATEGORY_NAMES[it] }
            ?: CATEGORY_WORD.find(value)?.groupValues?.get(1)?.let { word ->
                CATEGORY_NAMES.first { it.equals(word.replace(SPACES, " "), ignoreCase = true) }
            }
        return when {
            name != null && number != null -> "$name (#$number)"
            name != null -> name
            number != null -> "#$number $category"
            category != null -> category
            else -> ""
        }
    }

    private fun linesOf(text: String): Sequence<String> =
        text.lineSequence().flatMap { it.split(SENTENCE_END) }.filter { it.isNotBlank() }
}
