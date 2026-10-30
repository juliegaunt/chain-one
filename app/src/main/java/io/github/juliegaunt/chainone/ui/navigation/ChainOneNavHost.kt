package io.github.juliegaunt.chainone.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.juliegaunt.chainone.ui.edit.EditPatternScreen
import io.github.juliegaunt.chainone.ui.importer.ImportScreen
import io.github.juliegaunt.chainone.ui.importer.PdfImportScreen
import io.github.juliegaunt.chainone.ui.library.LibraryScreen
import io.github.juliegaunt.chainone.ui.pattern.PatternDetailScreen

object Routes {
    const val LIBRARY = "library"
    const val PATTERN = "pattern/{$ARG_PATTERN_ID}"
    const val EDIT = "edit?$ARG_PATTERN_ID={$ARG_PATTERN_ID}&$ARG_IMPORT_ID={$ARG_IMPORT_ID}"
    const val IMPORT = "import?$ARG_SHARED_TEXT={$ARG_SHARED_TEXT}"
    const val IMPORT_PDF = "import-pdf?$ARG_PDF_URI={$ARG_PDF_URI}"

    fun pattern(id: Long) = "pattern/$id"
    fun edit(id: Long? = null) = if (id == null) "edit" else "edit?$ARG_PATTERN_ID=$id"

    /** The edit screen, pre-filled with an import for the person to check before saving. */
    fun review(importId: String) = "edit?$ARG_IMPORT_ID=${Uri.encode(importId)}"
    fun import(sharedText: String? = null) =
        if (sharedText == null) "import" else "import?$ARG_SHARED_TEXT=${Uri.encode(sharedText)}"

    /** Copies in a PDF (picked in the file picker or shared from another app) for review. */
    fun importPdf(uri: Uri) = "import-pdf?$ARG_PDF_URI=${Uri.encode(uri.toString())}"
}

const val ARG_PATTERN_ID = "patternId"
const val ARG_IMPORT_ID = "importId"
const val ARG_SHARED_TEXT = "sharedText"
const val ARG_PDF_URI = "pdfUri"

/** Something another app sent to Chain One with Share or "Open with". */
sealed interface SharedItem {
    /** Usually a link to a pattern page. */
    data class Text(val text: String) : SharedItem
    data class Pdf(val uri: Uri) : SharedItem
}

/** Set on the library's saved state to show only the patterns with this tag id. */
const val KEY_SHOW_TAG = "showTag"

/** Used in the edit route when creating a new pattern. */
const val NEW_PATTERN_ID = -1L

/**
 * @param shared what another app shared with "Share → Chain One" (a link or a PDF), which
 *   opens the matching import screen; [onShareHandled] is called once it has
 */
@Composable
fun ChainOneNavHost(shared: SharedItem? = null, onShareHandled: () -> Unit = {}) {
    val navController = rememberNavController()

    LaunchedEffect(shared) {
        when (shared) {
            is SharedItem.Text -> navController.navigate(Routes.import(shared.text))
            is SharedItem.Pdf -> navController.navigate(Routes.importPdf(shared.uri))
            null -> return@LaunchedEffect
        }
        onShareHandled()
    }

    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) { entry ->
            // Set by a pattern's page when one of its tags is tapped.
            val showTag by entry.savedStateHandle.getStateFlow<Long?>(KEY_SHOW_TAG, null).collectAsState()
            LibraryScreen(
                onOpenPattern = { navController.navigate(Routes.pattern(it)) },
                onAddManually = { navController.navigate(Routes.edit()) },
                onImportFromWeb = { navController.navigate(Routes.import()) },
                onImportPdf = { navController.navigate(Routes.importPdf(it)) },
                showTagRequest = showTag,
                onShowTagHandled = { entry.savedStateHandle[KEY_SHOW_TAG] = null },
            )
        }

        composable(
            Routes.PATTERN,
            arguments = listOf(navArgument(ARG_PATTERN_ID) { type = NavType.LongType }),
        ) {
            PatternDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.edit(it)) },
                onShowTag = { tagId ->
                    // Back to the library, filtered to that tag.
                    navController.getBackStackEntry(Routes.LIBRARY).savedStateHandle[KEY_SHOW_TAG] = tagId
                    navController.popBackStack(Routes.LIBRARY, inclusive = false)
                },
            )
        }

        composable(
            Routes.IMPORT,
            arguments = listOf(
                navArgument(ARG_SHARED_TEXT) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            ImportScreen(
                onBack = { navController.popBackStack() },
                // The import screen is replaced, so Back from the review returns to the library.
                onImported = { importId ->
                    navController.navigate(Routes.review(importId)) {
                        popUpTo(Routes.IMPORT) { inclusive = true }
                    }
                },
                onOpenPattern = { id ->
                    navController.navigate(Routes.pattern(id)) {
                        popUpTo(Routes.IMPORT) { inclusive = true }
                    }
                },
            )
        }

        composable(
            Routes.IMPORT_PDF,
            arguments = listOf(navArgument(ARG_PDF_URI) { type = NavType.StringType }),
        ) {
            PdfImportScreen(
                onBack = { navController.popBackStack() },
                onImported = { importId ->
                    navController.navigate(Routes.review(importId)) {
                        popUpTo(Routes.IMPORT_PDF) { inclusive = true }
                    }
                },
            )
        }

        composable(
            Routes.EDIT,
            arguments = listOf(
                navArgument(ARG_PATTERN_ID) {
                    type = NavType.LongType
                    defaultValue = NEW_PATTERN_ID
                },
                navArgument(ARG_IMPORT_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            EditPatternScreen(
                onBack = { navController.popBackStack() },
                onSaved = { id, isNew ->
                    if (isNew) {
                        // Replace the edit screen with the new pattern's page.
                        navController.navigate(Routes.pattern(id)) {
                            popUpTo(Routes.LIBRARY)
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }
    }
}
