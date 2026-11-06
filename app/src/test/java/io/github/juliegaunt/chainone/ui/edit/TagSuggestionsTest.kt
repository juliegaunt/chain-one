package io.github.juliegaunt.chainone.ui.edit

import org.junit.Assert.assertEquals
import org.junit.Test

class TagSuggestionsTest {

    private val allTags = listOf("baby", "gifts", "handbag", "summer", "winter")

    private fun suggestions(typed: String, added: List<String> = emptyList()) =
        EditPatternUiState(isNew = true, allTags = allTags, tagInput = typed, form = PatternForm(tags = added)).tagSuggestions

    @Test
    fun nothingIsSuggestedUntilSomethingIsTyped() {
        assertEquals(emptyList<String>(), suggestions(""))
        assertEquals(emptyList<String>(), suggestions("   "))
    }

    @Test
    fun tagsStartingWithTheTextComeFirst() {
        assertEquals(listOf("baby", "handbag"), suggestions("ba"))
        assertEquals(listOf("summer", "winter"), suggestions("ER").sorted())
    }

    @Test
    fun tagsAlreadyAddedAreLeftOut() {
        assertEquals(listOf("handbag"), suggestions("ba", added = listOf("Baby")))
    }
}
