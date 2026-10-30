package io.github.juliegaunt.chainone.ui.counter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.db.Counter
import io.github.juliegaunt.chainone.data.repository.RepeatProgress
import io.github.juliegaunt.chainone.data.repository.repeatProgress

/** Adds a counter or edits one: its name and, optionally, how it repeats. */
@Composable
fun CounterDetailsDialog(counter: Counter?, onSave: (String, Int?, Int?) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(counter?.name.orEmpty()) }
    var repeats by rememberSaveable { mutableStateOf(counter?.repeatLength != null) }
    var length by rememberSaveable { mutableStateOf(counter?.repeatLength?.toString().orEmpty()) }
    var times by rememberSaveable { mutableStateOf(counter?.repeatTimes?.toString().orEmpty()) }
    val lengthValue = length.toIntOrNull()?.takeIf { it > 0 }
    val canSave = name.isNotBlank() && (!repeats || lengthValue != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (counter == null) R.string.counter_new_title else R.string.counter_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.counter_name)) },
                    placeholder = { Text(stringResource(R.string.counter_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(value = repeats, role = Role.Switch, onValueChange = { repeats = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.counter_repeats), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.counter_repeats_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = repeats, onCheckedChange = null)
                }
                if (repeats) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(length, { length = it }, stringResource(R.string.counter_repeat_length), Modifier.weight(1f))
                        NumberField(times, { times = it }, stringResource(R.string.counter_repeat_times), Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, lengthValue.takeIf { repeats }, times.toIntOrNull().takeIf { repeats }) },
                enabled = canSave,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Types in a count directly. A repeat counter asks for the repeat and the place within it. */
@Composable
fun SetValueDialog(counter: Counter, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    val progress = counter.repeatProgress
    var total by rememberSaveable { mutableStateOf(counter.value.toString()) }
    var repeat by rememberSaveable { mutableStateOf(progress?.repeat?.toString().orEmpty()) }
    var inRepeat by rememberSaveable { mutableStateOf(progress?.inRepeat?.toString().orEmpty()) }
    val result = if (progress == null) {
        total.toIntOrNull()
    } else {
        val r = repeat.toIntOrNull()
        val k = inRepeat.toIntOrNull()
        if (r != null && k != null && r >= 1 && k <= progress.repeatLength) RepeatProgress.total(k, r, progress.repeatLength) else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.counter_set_title, counter.name)) },
        text = {
            if (progress == null) {
                NumberField(total, { total = it }, stringResource(R.string.counter_value), Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        inRepeat,
                        { inRepeat = it },
                        stringResource(R.string.counter_in_repeat, progress.repeatLength),
                        Modifier.weight(1f),
                    )
                    NumberField(repeat, { repeat = it }, stringResource(R.string.counter_repeat_number), Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(onClick = { result?.let(onSet) }, enabled = result != null) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter(Char::isDigit).take(5)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
