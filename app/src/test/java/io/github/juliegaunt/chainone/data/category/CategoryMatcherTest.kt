package io.github.juliegaunt.chainone.data.category

import io.github.juliegaunt.chainone.data.db.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryMatcherTest {

    private val hats = Category(id = 1, name = "Hats", extraWords = "beanie, beret", position = 0)
    private val gloves = Category(id = 2, name = "Gloves", extraWords = "mittens", position = 1)
    private val blankets = Category(id = 3, name = "Blanket", extraWords = "granny square, afghan", position = 2)
    private val categories = listOf(hats, gloves, blankets)

    @Test
    fun singularAndPluralShareABaseForm() {
        listOf("hat" to "hats", "bunny" to "bunnies", "pouch" to "pouches", "dress" to "dresses", "box" to "boxes",
            "glove" to "gloves", "scarf" to "scarves", "knife" to "knives", "shoe" to "shoes", "toy" to "toys",
            "beanie" to "beanies", "bootie" to "booties", "tie" to "ties").forEach { (one, many) ->
            assertEquals("$one / $many", CategoryMatcher.baseForm(one), CategoryMatcher.baseForm(many))
        }
        assertEquals("dress", CategoryMatcher.baseForm("dress"))
        assertEquals("bag", CategoryMatcher.baseForm("bag"))
    }

    @Test
    fun matchesWholeWordsInAnyFormAndCase() {
        assertTrue(CategoryMatcher.matches("Ribbed Winter Hat", hats))
        assertTrue(CategoryMatcher.matches("BABY HATS in 3 sizes", hats))
        assertTrue(CategoryMatcher.matches("Top-down hat!", hats))
        assertFalse(CategoryMatcher.matches("Chat Bag", hats))
        assertFalse(CategoryMatcher.matches("That Cardigan", hats))
        assertFalse(CategoryMatcher.matches("Hatchback Cozy", hats))
    }

    @Test
    fun extraWordsAndPhrasesMatchToo() {
        assertTrue(CategoryMatcher.matches("Slouchy Beanie", hats))
        assertTrue(CategoryMatcher.matches("Pom-pom Berets", hats))
        assertTrue(CategoryMatcher.matches("Sunburst Granny Squares Throw", blankets))
        assertFalse(CategoryMatcher.matches("Granny Bag", blankets))
        assertFalse(CategoryMatcher.matches("Square Coaster", blankets))
    }

    @Test
    fun theFirstMatchingCategoryWinsUnlessOneWasPickedByHand() {
        assertEquals(hats, CategoryMatcher.categoryFor("Hat and Mittens Set", null, categories))
        assertEquals(gloves, CategoryMatcher.categoryFor("Hat and Mittens Set", gloves.id, categories))
        assertEquals(gloves, CategoryMatcher.categoryFor("Fingerless Gloves Crochet Pattern", null, categories))
        assertNull(CategoryMatcher.categoryFor("Amigurumi Fox", null, categories))
        // A hand-picked category that no longer exists falls back to the title.
        assertEquals(hats, CategoryMatcher.categoryFor("Winter Hat", 99, categories))
    }

    @Test
    fun groupsFollowTheCategoryOrderWithOtherLastAndNoEmptyGroups() {
        val titles = listOf("Fox", "Mittens", "Beanie", "Hat", "Owl")
        val groups = CategoryMatcher.group(titles, categories, { it }, { null })

        assertEquals(listOf(hats, gloves, null), groups.map { it.first })
        assertEquals(listOf(listOf("Beanie", "Hat"), listOf("Mittens"), listOf("Fox", "Owl")), groups.map { it.second })
    }

    @Test
    fun commonWordsForAnItemMatchWithoutBeingTyped() {
        val plainHats = Category(id = 10, name = "Hats", position = 0)

        listOf("Cozy Cap", "Slouchy Beanies", "Pom-pom Berets", "Baby Bonnet").forEach {
            assertTrue(it, CategoryMatcher.matches(it, plainHats))
        }
        assertFalse(CategoryMatcher.matches("Market Bag", plainHats))
        // "top" is left out on purpose: a top-down hat isn't a sweater.
        val sweaters = Category(id = 11, name = "Sweaters", position = 0)
        assertFalse(CategoryMatcher.matches("Top-down Ribbed Hat", sweaters))
        assertTrue(CategoryMatcher.matches("Easy Crop Top", sweaters))
    }

    @Test
    fun yourOwnWordsComeBeforeBuiltInOnes() {
        val plainHats = Category(id = 10, name = "Hats", position = 0)
        val beanies = Category(id = 11, name = "Beanies", position = 1)
        val amigurumi = Category(id = 12, name = "Amigurumi", position = 0)

        assertEquals(beanies, CategoryMatcher.categoryFor("Slouchy Beanie", null, listOf(plainHats, beanies)))
        assertEquals(plainHats, CategoryMatcher.categoryFor("Cozy Cap", null, listOf(plainHats, beanies)))
        assertEquals(plainHats, CategoryMatcher.categoryFor("Bunny Ears Hat", null, listOf(amigurumi, plainHats)))
        assertEquals(amigurumi, CategoryMatcher.categoryFor("Little Bunny", null, listOf(amigurumi, plainHats)))
    }

    @Test
    fun aNameCanListSeveralItems() {
        val warm = Category(id = 10, name = "Hats & Scarves", position = 0)
        val hands = Category(id = 11, name = "Gloves and Mittens", position = 0)

        assertTrue(CategoryMatcher.matches("Winter Scarf", warm))
        assertTrue(CategoryMatcher.matches("Cozy Hat", warm))
        assertTrue(CategoryMatcher.matches("Chunky Cowl", warm))
        assertTrue(CategoryMatcher.matches("Baby Mittens", hands))
    }

    @Test
    fun broadKindsAreOnlyJoinedThroughTheirGeneralWords() {
        // A "Bears" category takes bears, not every amigurumi animal; "Amigurumi" takes them all.
        val bears = Category(id = 10, name = "Bears", position = 0)
        assertTrue(CategoryMatcher.matches("Teddy Bear", bears))
        assertFalse(CategoryMatcher.matches("Little Bunny", bears))
        assertTrue(CategoryMatcher.matches("Little Bunny", Category(id = 11, name = "Amigurumi", position = 0)))

        val baskets = Category(id = 12, name = "Baskets", position = 0)
        assertFalse(CategoryMatcher.matches("Daisy Coaster", baskets))
        assertTrue(CategoryMatcher.matches("Daisy Coaster", Category(id = 13, name = "Home", position = 0)))
    }

    @Test
    fun theMainItemDecidesNotABonusOne() {
        val plainHats = Category(id = 10, name = "Hats", position = 0)
        val sweaters = Category(id = 11, name = "Sweaters & Tops", position = 1)
        val title = "EvelynandPeter Daisy Daydream Cardigan Bonus Beanie B72635"

        assertEquals(sweaters, CategoryMatcher.categoryFor(title, null, listOf(plainHats, sweaters)))
        // Without a category for cardigans it's "Other", not Hats for the bonus beanie.
        assertNull(CategoryMatcher.categoryFor(title, null, listOf(plainHats)))
        assertNull(CategoryMatcher.categoryFor("AVA-CARDIGAN", null, listOf(plainHats)))
        // Named first wins, wherever the categories are in the list.
        assertEquals(hats, CategoryMatcher.categoryFor("Hat and Mittens Set", null, listOf(gloves, hats)))
        assertEquals(sweaters, CategoryMatcher.categoryFor("Cardigan with Matching Hat", null, listOf(plainHats, sweaters)))
        // The cardigan is the item; "cap" here only describes its sleeves.
        assertNull(CategoryMatcher.categoryFor("Cap Sleeve Cardigan", null, listOf(plainHats)))
        assertEquals(sweaters, CategoryMatcher.categoryFor("Cap Sleeve Cardigan", null, listOf(plainHats, sweaters)))
    }

    @Test
    fun theItemComesAfterTheWordsDescribingIt() {
        val amigurumi = Category(id = 10, name = "Amigurumi", position = 0)
        val home = Category(id = 11, name = "Home", position = 1)
        val baby = Category(id = 12, name = "Baby", position = 2)
        val blankets = Category(id = 13, name = "Blankets", position = 3)

        assertEquals(home, CategoryMatcher.categoryFor("Owl Pillow", null, listOf(amigurumi, home)))
        assertEquals(blankets, CategoryMatcher.categoryFor("Baby Blanket", null, listOf(baby, blankets)))
        // With no category for the item itself, a describing word a category names still counts,
        // but another item doesn't: an owl pillow is a pillow, not amigurumi.
        assertEquals(baby, CategoryMatcher.categoryFor("Baby Blanket", null, listOf(baby)))
        assertNull(CategoryMatcher.categoryFor("Owl Pillow", null, listOf(amigurumi)))
    }

    @Test
    fun aLeadingPartThatNamesNoItemIsSkipped() {
        val plainHats = Category(id = 10, name = "Hats", position = 0)

        assertEquals(plainHats, CategoryMatcher.categoryFor("Free Pattern: Cozy Cap", null, listOf(plainHats)))
        assertEquals(listOf("Daisy Cardigan", "Beanie"), CategoryMatcher.parts("Daisy Cardigan Bonus Beanie"))
        assertEquals(listOf("Top-down Hat"), CategoryMatcher.parts("Top-down Hat"))
    }

    @Test
    fun wordsRunTogetherAreSplit() {
        val plainHats = Category(id = 10, name = "Hats", position = 0)
        val bags = Category(id = 11, name = "Bags", position = 1)

        assertEquals(plainHats, CategoryMatcher.categoryFor("Pattern OutoftheSnowBeanie Crochet", null, listOf(plainHats)))
        assertEquals(plainHats, CategoryMatcher.categoryFor("Pattern BowEarWarmer", null, listOf(plainHats)))
        assertEquals(bags, CategoryMatcher.categoryFor("MollsMadeIt FoldableTurtleBagPattern Rev03", null, listOf(plainHats, bags)))
    }

    @Test
    fun animalsCountOnlyWhenTheyAreTheItem() {
        val amigurumi = Category(id = 10, name = "Amigurumi", position = 0)

        assertEquals(amigurumi, CategoryMatcher.categoryFor("Little Bunny", null, listOf(amigurumi)))
        assertEquals(amigurumi, CategoryMatcher.categoryFor("Teddy Bear Crochet Pattern", null, listOf(amigurumi)))
        assertEquals(amigurumi, CategoryMatcher.categoryFor("Elephants in Love pattern", null, listOf(amigurumi)))
        assertNull(CategoryMatcher.categoryFor("18 Crochet Cat House & Nest Bed Patterns", null, listOf(amigurumi)))
        assertNull(CategoryMatcher.categoryFor("Foldable Turtle Bag", null, listOf(amigurumi)))
    }

    @Test
    fun aPonchoFitsSweatersOrShawls() {
        val sweaters = Category(id = 10, name = "Sweaters & Tops", position = 0)
        val shawls = Category(id = 11, name = "Shawls & Wraps", position = 1)
        val title = "Crochet Turtleneck Poncho – Free Harvest Pattern"

        assertEquals(sweaters, CategoryMatcher.categoryFor(title, null, listOf(sweaters)))
        assertEquals(shawls, CategoryMatcher.categoryFor(title, null, listOf(shawls)))
        assertEquals(sweaters, CategoryMatcher.categoryFor(title, null, listOf(sweaters, shawls)))
    }

    @Test
    fun extraWordsAreTidied() {
        assertEquals(listOf("beanie", "beret", "cloche"), CategoryMatcher.splitExtraWords(" Beanie,beret ,, cloche, beanie "))
    }
}
