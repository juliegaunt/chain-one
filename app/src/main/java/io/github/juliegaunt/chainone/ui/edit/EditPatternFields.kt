package io.github.juliegaunt.chainone.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.category.CategoryMatcher
import io.github.juliegaunt.chainone.data.db.Category
import io.github.juliegaunt.chainone.ui.components.PatternCover
import io.github.juliegaunt.chainone.ui.components.RenameDialog

/** The cover photo box: tap it to pick (or change) the photo; the corner button removes it. */
@Composable
fun CoverPicker(model: Any?, onPick: () -> Unit, onRemove: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                onClickLabel = stringResource(if (model == null) R.string.edit_add_photo else R.string.edit_change_photo),
                onClick = onPick,
            ),
    ) {
        if (model == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val color = MaterialTheme.colorScheme.onSecondaryContainer
                Icon(Icons.Filled.Add, contentDescription = null, tint = color, modifier = Modifier.size(40.dp))
                Text(stringResource(R.string.edit_add_photo), style = MaterialTheme.typography.titleMedium, color = color)
            }
        } else {
            PatternCover(model = model, title = "", modifier = Modifier.fillMaxSize())
            FilledTonalIconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.edit_remove_photo))
            }
        }
    }
}

/**
 * The pattern's PDFs. Tapping one renames it; the cross takes it off the pattern. Nothing is
 * copied or deleted until Save.
 */
@Composable
fun PdfList(
    pdfs: List<PdfEntry>,
    onAdd: () -> Unit,
    onRename: (key: String, name: String) -> Unit,
    onRemove: (key: String) -> Unit,
) {
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    Column {
        if (pdfs.isNotEmpty()) {
            Text(stringResource(R.string.edit_pdfs), style = MaterialTheme.typography.labelLarge)
        }
        pdfs.forEach { pdf ->
            ListItem(
                headlineContent = { Text(pdf.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                leadingContent = { Icon(painterResource(R.drawable.ic_pdf), contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = { onRemove(pdf.key) }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.edit_remove_pdf, pdf.name))
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClickLabel = stringResource(R.string.action_rename)) { renaming = pdf.key },
            )
        }
        TextButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.edit_add_pdf), modifier = Modifier.padding(start = 8.dp))
        }
    }

    pdfs.firstOrNull { it.key == renaming }?.let { pdf ->
        RenameDialog(
            title = stringResource(R.string.edit_rename_pdf),
            current = pdf.name,
            onRename = { onRename(pdf.key, it) },
            onDismiss = { renaming = null },
            capitalization = KeyboardCapitalization.Sentences,
        )
    }
}

/** Tells the person what was imported and what's missing, so they know what to check. */
@Composable
fun ImportReviewCard(review: ImportReview) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.review_heading), style = MaterialTheme.typography.titleMedium)
            if (review.pdfName != null) {
                Text(stringResource(R.string.review_pdf_intro), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            Text(stringResource(R.string.review_intro, review.host), style = MaterialTheme.typography.bodyMedium)
            if (review.missingFields.isNotEmpty()) {
                val fields = review.missingFields.map { stringResource(it).lowercase() }
                    .joinToString(stringResource(R.string.review_list_separator))
                Text(stringResource(R.string.review_missing, fields), style = MaterialTheme.typography.bodyMedium)
            }
            when {
                !review.foundInstructions && review.hasPageCopy -> Text(
                    stringResource(R.string.review_no_instructions),
                    style = MaterialTheme.typography.bodyMedium,
                )
                review.hasPageCopy -> Text(stringResource(R.string.review_page_saved), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * The pattern's tags as removable chips, a box to type new ones (Enter or a comma adds one),
 * and existing tags to tap, so the same tag isn't created twice with different spellings.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagsField(
    tags: List<String>,
    input: String,
    suggestions: List<String>,
    onInputChange: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { onRemove(tag) },
                        label = { Text(tag) },
                        trailingIcon = {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.edit_remove_tag, tag),
                                modifier = Modifier.size(InputChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            label = { Text(stringResource(R.string.field_tags)) },
            placeholder = { Text(stringResource(R.string.edit_tags_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            // Handling Done keeps the keyboard open, ready for the next tag.
            keyboardActions = KeyboardActions(onDone = { onAdd(input) }),
            trailingIcon = if (input.isNotBlank()) {
                {
                    IconButton(onClick = { onAdd(input) }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.edit_add_tag))
                    }
                }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { tag ->
                    SuggestionChip(onClick = { onAdd(tag) }, label = { Text(tag) })
                }
            }
        }
    }
}

/**
 * The pattern's category: "Automatic (Hats)", worked out from the title as it's typed, or one
 * picked by hand from the menu.
 */
@Composable
fun CategoryPicker(title: String, chosenId: Long?, categories: List<Category>, onChoose: (Long?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val fromTitle = CategoryMatcher.categoryFor(title, null, categories)?.name ?: stringResource(R.string.library_other)
    val automatic = stringResource(R.string.category_automatic, fromTitle)
    val chosen = categories.firstOrNull { it.id == chosenId }
    val choose = { id: Long? ->
        expanded = false
        onChoose(id)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.field_category), style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(chosen?.name ?: automatic)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(automatic) },
                    trailingIcon = if (chosen == null) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
                    onClick = { choose(null) },
                )
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        trailingIcon = if (category.id == chosenId) ({ Icon(Icons.Filled.Check, contentDescription = null) }) else null,
                        onClick = { choose(category.id) },
                    )
                }
            }
        }
        if (chosen == null) {
            Text(
                stringResource(R.string.category_automatic_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
