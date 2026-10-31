package io.github.juliegaunt.chainone.data.category

import io.github.juliegaunt.chainone.data.db.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class CategorySuggestionsTest {

    private val titles = listOf(
        "Easy Crochet Hat Pattern",
        "Ribbed Winter Hats",
        "Baby Hat in 3 Sizes",
        "Market Bag",
        "Granny Square Bag",
        "Amigurumi Fox",
        "Fingerless Gloves Crochet Pattern – The Bauble Fingerless Gloves",
    )

    @Test
    fun suggestsItemWordsSharedByTwoOrMoreTitlesMostCommonFirst() {
        val suggestions = CategorySuggestions.from(titles, emptyList())

        assertEquals(listOf(CategorySuggestion("Hats", 3), CategorySuggestion("Bags", 2)), suggestions)
    }

    @Test
    fun skipsKindsACategoryAlreadyCovers() {
        val totes = Category(id = 1, name = "Totes", position = 0)

        assertEquals(listOf("Hats"), CategorySuggestions.from(titles, listOf(totes)).map { it.name })
    }

    @Test
    fun differentWordsForTheSameItemMakeOneSuggestion() {
        val hats = listOf("Slouchy Beanie", "Ribbed Beanie", "Cozy Cap", "Newsboy Cap")

        assertEquals(listOf(CategorySuggestion("Hats", 4)), CategorySuggestions.from(hats, emptyList()))
    }

    @Test
    fun eachTitleCountsTowardItsMainItemOnly() {
        val titles = listOf("Daisy Cardigan Bonus Beanie", "Ava Cardigan", "Cozy Cap")

        assertEquals(listOf(CategorySuggestion("Sweaters & Tops", 2)), CategorySuggestions.from(titles, emptyList()))
    }

    @Test
    fun wordsOutsideTheBuiltInKindsAreStillSuggested() {
        val titles = listOf("Christmas Star Garland", "Christmas Tree Skirt", "Owl Coaster")

        // Kinds of item come first; plain shared words after them.
        assertEquals(listOf(CategorySuggestion("Home", 2), CategorySuggestion("Christmas", 2)), CategorySuggestions.from(titles, emptyList()))
    }

    @Test
    fun aWordRepeatedInOneTitleCountsOnce() {
        // "Gloves" appears twice in one title, which isn't two patterns.
        assertEquals(emptyList<CategorySuggestion>(), CategorySuggestions.from(titles.takeLast(1), emptyList()))
    }
}
