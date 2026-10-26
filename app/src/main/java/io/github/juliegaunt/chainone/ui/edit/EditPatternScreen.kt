package io.github.juliegaunt.chainone.ui.edit
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.db.PatternStatus
import io.github.juliegaunt.chainone.ui.components.PatternCover
import io.github.juliegaunt.chainone.ui.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPatternScreen(
    onBack: () -> Unit,
    onSaved: (id: Long, isNew: Boolean) -> Unit,
    viewModel: EditPatternViewModel = viewModel(factory = EditPatternViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coverFailedMessage = stringResource(R.string.edit_cover_failed)
    val filesFailedMessage = stringResource(R.string.edit_files_failed)

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::pickCover)
    }
    val launchPicker = {
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    LaunchedEffect(state.savedId) {
        state.savedId?.let { onSaved(it, state.isNew) }
    }
    LaunchedEffect(state.coverCopyFailed) {
        if (state.coverCopyFailed) snackbarHostState.showSnackbar(coverFailedMessage)
    }
    LaunchedEffect(state.importFilesFailed) {
        if (state.importFilesFailed) snackbarHostState.showSnackbar(filesFailedMessage)
    }

    val tryClose = { if (state.hasChanges) confirmDiscard = true else onBack() }
    BackHandler(onBack = tryClose)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.edit_title_new else R.string.edit_title_existing)) },
                navigationIcon = {
                    IconButton(onClick = tryClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                    }
                },
                actions = {
                    Button(
                        onClick = viewModel::save,
                        enabled = !state.isLoading && !state.isSaving,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (!state.isLoading) {
            EditForm(
                state = state,
                onChange = viewModel::updateForm,
                onTagInputChange = viewModel::updateTagInput,
                onAddTag = viewModel::addTag,
                onRemoveTag = viewModel::removeTag,
                onPickCover = launchPicker,
                onRemoveCover = viewModel::removeCover,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
            )
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = {
                Text(stringResource(if (state.review != null) R.string.edit_discard_import_title else R.string.edit_discard_title))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onBack()
                }) { Text(stringResource(R.string.action_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.action_keep_editing)) }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditForm(
    state: EditPatternUiState,
    onChange: ((PatternForm) -> PatternForm) -> Unit,
    onTagInputChange: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onPickCover: () -> Unit,
    onRemoveCover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.review?.let { ImportReviewCard(it) }

        // First, because most patterns will come from a website.
        OutlinedTextField(
            value = form.sourceUrl,
            onValueChange = { v -> onChange { it.copy(sourceUrl = v) } },
            label = { Text(stringResource(R.string.field_source_url)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        CoverPicker(model = state.coverModel, onPick = onPickCover, onRemove = onRemoveCover)

        OutlinedTextField(
            value = form.title,
            onValueChange = { v -> onChange { it.copy(title = v) } },
            label = { Text(stringResource(R.string.field_title)) },
            isError = state.showTitleError,
            supportingText = if (state.showTitleError) {
                { Text(stringResource(R.string.edit_title_required)) }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = sentenceKeyboard,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.designer,
            onValueChange = { v -> onChange { it.copy(designer = v) } },
            label = { Text(stringResource(R.string.field_designer)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.field_status), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PatternStatus.entries.forEach { status ->
                StatusChip(status, selected = form.status == status, onClick = { onChange { it.copy(status = status) } })
            }
        }

        TagsField(
            tags = form.tags,
            input = state.tagInput,
            suggestions = state.tagSuggestions,
            onInputChange = onTagInputChange,
            onAdd = onAddTag,
            onRemove = onRemoveTag,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = form.hookSize,
                onValueChange = { v -> onChange { it.copy(hookSize = v) } },
                label = { Text(stringResource(R.string.field_hook_size)) },
                placeholder = { Text(stringResource(R.string.field_hook_size_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = form.yarnWeight,
                onValueChange = { v -> onChange { it.copy(yarnWeight = v) } },
                label = { Text(stringResource(R.string.field_yarn_weight)) },
                placeholder = { Text(stringResource(R.string.field_yarn_weight_hint)) },
                singleLine = true,
                keyboardOptions = sentenceKeyboard,
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedTextField(
            value = form.materials,
            onValueChange = { v -> onChange { it.copy(materials = v) } },
            label = { Text(stringResource(R.string.field_materials)) },
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.instructions,
            onValueChange = { v -> onChange { it.copy(instructions = v) } },
            label = { Text(stringResource(R.string.field_instructions)) },
            supportingText = { Text(stringResource(R.string.edit_instructions_tip)) },
            minLines = 6,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.notes,
            onValueChange = { v -> onChange { it.copy(notes = v) } },
            label = { Text(stringResource(R.string.field_notes)) },
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val sentenceKeyboard = KeyboardOptions(
    capitalization = KeyboardCapitalization.Sentences,
    imeAction = ImeAction.Next,
)

/** The cover photo box: tap it to pick (or change) the photo; the corner button removes it. */
@Composable
private fun CoverPicker(model: Any?, onPick: () -> Unit, onRemove: () -> Unit) {
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

/** Tells the person what was imported and what's missing, so they know what to check. */
@Composable
private fun ImportReviewCard(review: ImportReview) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.review_heading), style = MaterialTheme.typography.titleMedium)
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
private fun TagsField(
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
