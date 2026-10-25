package io.github.juliegaunt.chainone.data.importer

import org.junit.Assert.assertEquals
import org.junit.Test

class CraftDetailsTest {

    @Test
    fun hookSizeInMillimetresAndUsLetters() {
        assertEquals("5.0 mm (H-8)", CraftDetails.findHookSize("You will need an H/8 (5.0mm) crochet hook."))
        assertEquals("4 mm", CraftDetails.findHookSize("Yarn: DK cotton\nHook: 4 mm"))
        assertEquals("6,5".replace(',', '.') + " mm", CraftDetails.findHookSize("6,5 mm hook"))
        assertEquals("K-10.5", CraftDetails.findHookSize("Use a K-10.5 hook"))
        assertEquals("M/N-13", CraftDetails.findHookSize("Hook M/N-13"))
    }

    @Test
    fun hookSizeOnlyFromLinesAboutHooks() {
        assertEquals("", CraftDetails.findHookSize("The finished bag is 300 mm wide."))
        assertEquals(
            "4 mm",
            CraftDetails.findHookSize("The finished bag is 300 mm wide. Use a 4 mm hook for the handles."),
        )
    }

    @Test
    fun yarnWeightByNameAndNumber() {
        assertEquals("Worsted (#4)", CraftDetails.findYarnWeight("Yarn: worsted weight (#4) acrylic"))
        assertEquals("DK", CraftDetails.findYarnWeight("DK weight cotton, about 200 yards"))
        assertEquals("Super bulky", CraftDetails.findYarnWeight("Super bulky yarn"))
        assertEquals("#5 Bulky", CraftDetails.findYarnWeight("Any yarn in weight category 5"))
        assertEquals("Medium", CraftDetails.findYarnWeight("A medium weight yarn"))
        assertEquals("DK", CraftDetails.findYarnWeight("Double knitting yarn"))
    }

    @Test
    fun yarnWeightIgnoresUnrelatedLines() {
        assertEquals("", CraftDetails.findYarnWeight("Rnd 4: dk in sport"))
        assertEquals("", CraftDetails.findYarnWeight("Yarn: 100 g of cotton"))
    }
}
