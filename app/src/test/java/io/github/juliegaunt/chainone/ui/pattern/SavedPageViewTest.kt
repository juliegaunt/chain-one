package io.github.juliegaunt.chainone.ui.pattern

import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SavedPageViewTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun theSavedPageCanLoadOnlyItsOwnFiles() {
        val snapshot = tempFolder.newFolder("patterns", "3", "snapshot")
        val photo = Uri.fromFile(File(snapshot, "image-1.jpg"))
        val database = Uri.fromFile(File(tempFolder.root, "databases/chain-one.db"))
        val sneaky = Uri.parse("file://${snapshot.path}/../../../databases/chain-one.db")

        assertTrue(isInsideFolder(snapshot, photo))
        assertFalse(isInsideFolder(snapshot, database))
        assertFalse(isInsideFolder(snapshot, sneaky))
        assertFalse(isInsideFolder(snapshot, Uri.parse("content://com.example/x")))
        assertFalse(isInsideFolder(snapshot, Uri.parse("https://example.com/tracker.gif")))
    }
}
