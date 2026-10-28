package io.github.juliegaunt.chainone.ui.counter

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.db.Counter
import io.github.juliegaunt.chainone.data.repository.RepeatProgress
import io.github.juliegaunt.chainone.data.repository.repeatProgress

/** The Counter tab: a large card per counter, Undo, adding counters, and the counting options. */
@Composable
fun CounterTab(state: CounterUiState, viewModel: CounterViewModel, modifier: Modifier = Modifier) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var settingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = viewModel::undo, enabled = state.canUndo) {
                    Icon(painterResource(R.drawable.ic_undo), contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.counter_undo))
                }
            }
        }
        itemsIndexed(state.counters, key = { _, counter -> counter.id }) { index, counter ->
            CounterCard(
                counter = counter,
                isFirst = index == 0,
                isLast = index == state.counters.lastIndex,
                onIncrement = { viewModel.increment(counter.id) },
                onDecrement = { viewModel.decrement(counter.id) },
                onSetValue = { settingId = counter.id },
                onEdit = { editingId = counter.id },
                onReset = { viewModel.reset(counter.id) },
                onMove = { by -> viewModel.move(counter.id, by) },
                onDelete = { deletingId = counter.id },
            )
        }
        item {
            OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.counter_add))
            }
        }
        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SettingSwitch(
                title = stringResource(R.string.counter_volume_keys),
                description = stringResource(R.string.counter_volume_keys_hint),
                checked = state.countWithVolumeKeys,
                onChange = viewModel::setCountWithVolumeKeys,
            )
            SettingSwitch(
                title = stringResource(R.string.counter_keep_screen_on),
                description = stringResource(R.string.counter_keep_screen_on_hint),
                checked = state.keepScreenOn,
                onChange = viewModel::setKeepScreenOn,
            )
        }
    }

    if (adding) {
        CounterDetailsDialog(
            counter = null,
            onSave = { name, length, times ->
                viewModel.add(name, length, times)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
    state.counters.firstOrNull { it.id == editingId }?.let { counter ->
        CounterDetailsDialog(
            counter = counter,
            onSave = { name, length, times ->
                viewModel.update(counter.id, name, length, times)
                editingId = null
            },
            onDismiss = { editingId = null },
        )
    }
    state.counters.firstOrNull { it.id == settingId }?.let { counter ->
        SetValueDialog(
            counter = counter,
            onSet = { total ->
                viewModel.setValue(counter.id, total)
                settingId = null
            },
            onDismiss = { settingId = null },
        )
    }
    state.counters.firstOrNull { it.id == deletingId }?.let { counter ->
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text(stringResource(R.string.counter_delete_title, counter.name)) },
            text = { Text(stringResource(R.string.counter_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(counter.id)
                    deletingId = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingId = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun CounterCard(
    counter: Counter,
    isFirst: Boolean,
    isLast: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onSetValue: () -> Unit,
    onEdit: () -> Unit,
    onReset: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val progress = counter.repeatProgress
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    counter.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                CounterMenu(counter.name, isFirst, isLast, onSetValue, onEdit, onReset, onMove, onDelete)
            }
            // Tapping the number lets you type one, e.g. to pick a project back up at row 37.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = stringResource(R.string.counter_set_number), onClick = onSetValue)
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    (progress?.inRepeat ?: counter.value).toString(),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp, fontWeight = FontWeight.Medium),
                )
                if (progress != null) {
                    Text(
                        repeatText(progress),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (progress.isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalIconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onDecrement()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(72.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_remove),
                        contentDescription = stringResource(R.string.counter_count_down, counter.name),
                        modifier = Modifier.size(32.dp),
                    )
                }
                FilledIconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onIncrement()
                    },
                    modifier = Modifier
                        .weight(2f)
                        .height(72.dp),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.counter_count_up, counter.name),
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CounterMenu(
    name: String,
    isFirst: Boolean,
    isLast: Boolean,
    onSetValue: () -> Unit,
    onEdit: () -> Unit,
    onReset: () -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.counter_options, name))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val choose = { action: () -> Unit ->
                expanded = false
                action()
            }
            MenuItem(R.string.counter_set_number) { choose(onSetValue) }
            MenuItem(R.string.counter_edit) { choose(onEdit) }
            MenuItem(R.string.counter_reset) { choose(onReset) }
            if (!isFirst) MenuItem(R.string.counter_move_up) { choose { onMove(-1) } }
            if (!isLast) MenuItem(R.string.counter_move_down) { choose { onMove(+1) } }
            MenuItem(R.string.action_delete) { choose(onDelete) }
        }
    }
}

@Composable
private fun MenuItem(@StringRes label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)
}

/**
 * The slim counter along the bottom of the Instructions and Web page tabs, for counting while
 * reading. Works before any counter exists: the first tap of + creates "Row".
 */
@Composable
fun CounterBar(
    counter: Counter?,
    defaultName: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClickLabel = stringResource(R.string.counter_open), onClick = onOpen)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Text(
                    counter?.name ?: defaultName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val progress = counter?.repeatProgress
                Text(
                    if (progress == null) {
                        (counter?.value ?: 0).toString()
                    } else {
                        stringResource(R.string.counter_bar_repeat, progress.inRepeat, progress.repeatLength, progress.repeat)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                )
            }
            FilledTonalIconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onDecrement()
                },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_remove),
                    contentDescription = stringResource(R.string.counter_count_down, counter?.name ?: defaultName),
                )
            }
            Spacer(Modifier.width(12.dp))
            FilledIconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onIncrement()
                },
                modifier = Modifier
                    .width(88.dp)
                    .height(56.dp),
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.counter_count_up, counter?.name ?: defaultName),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

@Composable
private fun repeatText(progress: RepeatProgress): String = when {
    progress.isDone -> (progress.repeatTimes ?: progress.repeat).let { pluralStringResource(R.plurals.counter_repeats_done, it, it) }
    progress.repeatTimes != null ->
        stringResource(R.string.counter_repeat_progress_times, progress.repeatLength, progress.repeat, progress.repeatTimes)
    else -> stringResource(R.string.counter_repeat_progress, progress.repeatLength, progress.repeat)
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Adds a counter or edits one: its name and, optionally, how it repeats. */
@Composable
private fun CounterDetailsDialog(counter: Counter?, onSave: (String, Int?, Int?) -> Unit, onDismiss: () -> Unit) {
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
private fun SetValueDialog(counter: Counter, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
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
