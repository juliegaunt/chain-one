package io.github.juliegaunt.chainone.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.juliegaunt.chainone.ui.edit.EditPatternScreen
import io.github.juliegaunt.chainone.ui.library.LibraryScreen
import io.github.juliegaunt.chainone.ui.pattern.PatternDetailScreen

object Routes {
    const val LIBRARY = "library"
    const val PATTERN = "pattern/{$ARG_PATTERN_ID}"
    const val EDIT = "edit?$ARG_PATTERN_ID={$ARG_PATTERN_ID}"

    fun pattern(id: Long) = "pattern/$id"
    fun edit(id: Long? = null) = if (id == null) "edit" else "edit?$ARG_PATTERN_ID=$id"
}

const val ARG_PATTERN_ID = "patternId"

/** Used in the edit route when creating a new pattern. */
const val NEW_PATTERN_ID = -1L

@Composable
fun ChainOneNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onOpenPattern = { navController.navigate(Routes.pattern(it)) },
                onAddPattern = { navController.navigate(Routes.edit()) },
            )
        }

        composable(
            Routes.PATTERN,
            arguments = listOf(navArgument(ARG_PATTERN_ID) { type = NavType.LongType }),
        ) {
            PatternDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.edit(it)) },
            )
        }

        composable(
            Routes.EDIT,
            arguments = listOf(
                navArgument(ARG_PATTERN_ID) {
                    type = NavType.LongType
                    defaultValue = NEW_PATTERN_ID
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
