package io.github.juliegaunt.chainone.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.juliegaunt.chainone.R

/**
 * A question with a yes and a no, such as "Delete this pattern?". [onConfirm] runs for yes;
 * [onDismiss] closes the dialog either way.
 */
@Composable
fun ConfirmDialog(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    text: String? = null,
    dismissLabel: String = stringResource(R.string.action_cancel),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}

/**
 * Asks for a new name. It opens with [current] selected and the keyboard up, so typing replaces
 * it; a blank name can't be saved. [onRename] gets the new name, then [onDismiss] closes it.
 */
@Composable
fun RenameDialog(
    title: String,
    current: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
    hint: String? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
) {
    var field by rememberSaveable(current, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(current, selection = TextRange(0, current.length)))
    }
    val name = field.text
    val save = {
        if (name.isNotBlank()) {
            onRename(name)
            onDismiss()
        }
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = capitalization, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = save, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_rename)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
