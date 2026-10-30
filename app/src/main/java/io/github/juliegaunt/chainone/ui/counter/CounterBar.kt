package io.github.juliegaunt.chainone.ui.counter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.db.Counter
import io.github.juliegaunt.chainone.data.repository.repeatProgress

/**
 * The slim counter along the bottom of the Instructions, Web page and PDF tabs, for counting while
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
                onClick = withTick(onDecrement),
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_remove),
                    contentDescription = stringResource(R.string.counter_count_down, counter?.name ?: defaultName),
                )
            }
            Spacer(Modifier.width(12.dp))
            FilledIconButton(
                onClick = withTick(onIncrement),
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
