package io.github.juliegaunt.chainone.data.category

/**
 * Common crochet items and the words titles use for them, so a "Hats" category also takes
 * "Cozy Cap" and "Slouchy Beanie" without being told. A category joins a family when one of its
 * own words is in it. Ambiguous words are left out on purpose: "top" (as in "top-down hat") and
 * "granny square" (bags and blankets both use them).
 */
object ItemFamilies {

    /**
     * A kind of item: its usual category [name] and the [words] that mean it. A category joins the
     * family through its name's words or [joinedBy] (all of [words] unless the family is broad,
     * so a "Bears" category doesn't take every amigurumi bunny).
     */
    data class Family(
        val name: String,
        val words: List<String>,
        val joinedBy: List<String> = words,
        /** Words that only name this item as the title's last word: "Little Bunny", not "Bunny Ears Hat". */
        val onlyAsLast: List<String> = emptyList(),
    ) {
        /** [words] as base forms ("Tank top" → [tank, top]), ready for matching. */
        val terms: List<List<String>> = words.map(CategoryMatcher::wordsOf)

        internal val lastOnlyTerms: List<List<String>> = onlyAsLast.map(CategoryMatcher::wordsOf)

        internal val joinTerms: List<List<String>> =
            (CategoryMatcher.nameItems(name) + joinedBy).map(CategoryMatcher::wordsOf).filter { it.isNotEmpty() }
    }

    /** Animals and creatures: amigurumi when they're the item, but often just describing one ("Cat House", "Owl Pillow"). */
    private val CREATURES = listOf(
        "bunny", "rabbit", "bear", "teddy", "octopus", "owl", "fox", "cat", "kitten", "dog", "puppy", "unicorn",
        "dinosaur", "dino", "elephant", "whale", "turtle", "frog", "penguin", "monkey", "lamb", "sheep", "lion",
        "giraffe", "mouse", "pig", "duck", "chick", "bee", "bird", "snail", "dragon", "fairy", "troll", "gnome",
        "mermaid", "monster",
    )

    val all: List<Family> = listOf(
        Family(
            "Hats",
            listOf(
                "hat", "beanie", "cap", "beret", "cloche", "bonnet", "toque", "tuque", "balaclava", "earflap", "ear flap",
                "headband", "ear warmer", "earwarmer", "head wrap", "headwrap", "turban",
            ),
        ),
        Family("Scarves & Cowls", listOf("scarf", "cowl", "snood", "neckwarmer", "neck warmer")),
        Family(
            "Gloves & Mittens",
            listOf("glove", "mitten", "mitt", "wrister", "wristwarmer", "wrist warmer", "handwarmer", "hand warmer", "gauntlet"),
        ),
        Family("Socks", listOf("sock", "legwarmer", "leg warmer")),
        Family("Slippers", listOf("slipper", "bootie", "moccasin", "house shoe")),
        Family("Shawls & Wraps", listOf("shawl", "shawlette", "wrap", "shrug", "poncho", "stole", "capelet")),
        Family(
            "Sweaters & Tops",
            listOf(
                "sweater", "cardigan", "cardi", "pullover", "jumper", "tee", "t-shirt", "tank top", "crop top",
                "vest", "tunic", "blouse", "jacket", "coat", "waistcoat", "hoodie", "kimono",
                // Also a wrap: whichever of the two categories exists (or is higher) takes it.
                "poncho",
            ),
            joinedBy = listOf("sweater", "pullover", "jumper", "top", "garment"),
        ),
        Family("Bags", listOf("bag", "tote", "purse", "pouch", "clutch", "backpack", "crossbody", "wristlet")),
        Family("Blankets", listOf("blanket", "afghan", "throw", "quilt", "lovey")),
        Family(
            "Amigurumi",
            listOf("amigurumi", "ami", "plushie", "stuffie", "softie", "stuffed animal") + CREATURES,
            joinedBy = listOf("amigurumi", "ami", "plushie", "stuffie", "softie", "stuffed animal"),
            onlyAsLast = CREATURES,
        ),
        Family(
            "Toys",
            listOf("toy", "doll", "rattle", "teether", "ball", "puppet", "playmat", "play mat"),
            joinedBy = listOf("toy"),
        ),
        Family(
            "Home",
            listOf(
                "coaster", "pillow", "cushion", "rug", "potholder", "pot holder", "placemat", "garland", "bunting",
                "basket", "doily", "table runner", "wall hanging", "plant hanger", "ornament", "valance", "curtain",
            ),
            joinedBy = listOf("home", "decor", "home decor", "homeware"),
        ),
        Family(
            "Dishcloths",
            listOf("dishcloth", "dish cloth", "washcloth", "wash cloth", "scrubbie", "scrubber", "face scrubbie"),
        ),
    )

    /**
     * The families a category belongs to: those it joins with a word or phrase in one of its terms
     * (see [CategoryMatcher.terms]), so "Hats", "Baby Hats" and "Beanies" all join Hats.
     */
    fun familiesOf(categoryTerms: List<List<String>>): List<Family> =
        all.filter { family -> family.joinTerms.any { term -> categoryTerms.any { CategoryMatcher.containsRun(it, term) } } }

    /** The families [titleWords] (base forms) mention, each once. */
    fun familiesIn(titleWords: List<String>): List<Family> =
        all.filter { family -> family.terms.any { term -> CategoryMatcher.containsRun(titleWords, term) } }
}
