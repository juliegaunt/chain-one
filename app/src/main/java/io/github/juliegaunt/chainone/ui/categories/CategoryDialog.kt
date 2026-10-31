package io.github.juliegaunt.chainone.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.category.CategoryMatcher
import io.github.juliegaunt.chainone.data.db.Category
import io.github.juliegaunt.chainone.data.repository.CategoryRepository

/**
 * Adds a category, or edits [category]: its name and the extra words that also put a pattern in
 * it. As the person types, it says how many of [titles] the category would catch. A blank name, or
 * one another category already has, can't be saved.
 */
@Composable
fun CategoryDialog(
    category: Category?,
    otherCategories: List<Category>,
    titles: List<String>,
    onSave: (name: String, extraWords: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(category?.name.orEmpty()) }
    var extraWords by rememberSaveable { mutableStateOf(category?.extraWords.orEmpty()) }
    val clean = CategoryRepository.cleanName(name)
    val taken = otherCategories.any { it.name.equals(clean, ignoreCase = true) }
    val draft = Category(name = clean, extraWords = extraWords, position = 0)
    val matches = remember(clean, extraWords, titles) { titles.count { CategoryMatcher.matches(it, draft) } }
    val canSave = clean.isNotEmpty() && !taken
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (category == null) R.string.categories_add else R.string.categories_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.categories_name)) },
                    placeholder = { Text(stringResource(R.string.categories_name_hint)) },
                    isError = taken,
                    supportingText = if (taken) {
                        { Text(stringResource(R.string.categories_name_taken)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                OutlinedTextField(
                    value = extraWords,
                    onValueChange = { extraWords = it },
                    label = { Text(stringResource(R.string.categories_also_matches)) },
                    placeholder = { Text(stringResource(R.string.categories_also_matches_hint)) },
                    supportingText = { Text(stringResource(R.string.categories_also_matches_help)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (clean.isNotEmpty()) {
                    Text(
                        pluralStringResource(R.plurals.categories_matches, matches, matches),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(clean, extraWords)
                    onDismiss()
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
