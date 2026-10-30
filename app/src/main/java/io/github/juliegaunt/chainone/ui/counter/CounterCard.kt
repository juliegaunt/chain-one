package io.github.juliegaunt.chainone.ui.counter

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.db.Counter
import io.github.juliegaunt.chainone.data.repository.RepeatProgress
import io.github.juliegaunt.chainone.data.repository.repeatProgress

/** One counter on the Counter tab: its name and menu, the big number between − and +. */
@Composable
fun CounterCard(
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
    val progress = counter.repeatProgress
    Card(Modifier.fillMaxWidth()) {
        // The menu button brings its own touch padding, so the card's edge is tight around it.
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
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
            // The number sits between the buttons, so each counter takes about half the height.
            Row(
                Modifier.padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalIconButton(
                    onClick = withTick(onDecrement),
                    modifier = Modifier.size(width = 72.dp, height = 72.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_remove),
                        contentDescription = stringResource(R.string.counter_count_down, counter.name),
                        modifier = Modifier.size(32.dp),
                    )
                }
                // Tapping the number lets you type one, e.g. to pick a project back up at row 37.
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(onClickLabel = stringResource(R.string.counter_set_number), onClick = onSetValue),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CounterNumber(progress?.inRepeat ?: counter.value)
                    if (progress != null) {
                        Text(
                            repeatText(progress),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (progress.isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                FilledIconButton(
                    onClick = withTick(onIncrement),
                    modifier = Modifier.size(width = 104.dp, height = 72.dp),
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

/** The big count; a long one (rare, like 10000) shrinks to fit between the buttons rather than wrapping. */
@Composable
private fun CounterNumber(value: Int) {
    val style = MaterialTheme.typography.displayLarge.copy(fontSize = 72.sp, lineHeight = 72.sp, fontWeight = FontWeight.Medium)
    val color = LocalContentColor.current
    BasicText(
        value.toString(),
        style = style,
        color = { color },
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 32.sp, maxFontSize = 72.sp),
    )
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

@Composable
private fun repeatText(progress: RepeatProgress): String = when {
    progress.isDone -> (progress.repeatTimes ?: progress.repeat).let { pluralStringResource(R.plurals.counter_repeats_done, it, it) }
    progress.repeatTimes != null ->
        stringResource(R.string.counter_repeat_progress_times, progress.repeatLength, progress.repeat, progress.repeatTimes)
    else -> stringResource(R.string.counter_repeat_progress, progress.repeatLength, progress.repeat)
}
