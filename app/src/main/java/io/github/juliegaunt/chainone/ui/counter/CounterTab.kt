package io.github.juliegaunt.chainone.ui.counter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.ui.components.ConfirmDialog

/** The Counter tab: a card per counter, adding counters, then Undo and the counting options. */
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
            UndoRow(enabled = state.canUndo, onUndo = viewModel::undo)
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
        ConfirmDialog(
            title = stringResource(R.string.counter_delete_title, counter.name),
            text = stringResource(R.string.counter_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete(counter.id) },
            onDismiss = { deletingId = null },
        )
    }
}

/** Undo sits with the settings rather than above the counters: − covers most slips, this the rest. */
@Composable
private fun UndoRow(enabled: Boolean, onUndo: () -> Unit) {
    SettingRow(stringResource(R.string.counter_undo_title), stringResource(R.string.counter_undo_hint)) {
        OutlinedButton(onClick = onUndo, enabled = enabled) {
            Icon(painterResource(R.drawable.ic_undo), contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.counter_undo))
        }
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(title, description, Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange)) {
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A setting's name and what it does, with its control on the right. */
@Composable
private fun SettingRow(title: String, description: String, modifier: Modifier = Modifier, control: @Composable () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        control()
    }
}
