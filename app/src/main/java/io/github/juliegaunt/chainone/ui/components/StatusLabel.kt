package io.github.juliegaunt.chainone.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.db.PatternStatus
import io.github.juliegaunt.chainone.ui.theme.LocalStatusColors
import io.github.juliegaunt.chainone.ui.theme.StatusColor

@get:StringRes
val PatternStatus.labelRes: Int
    get() = when (this) {
        PatternStatus.WANT_TO_MAKE -> R.string.status_want_to_make
        PatternStatus.IN_PROGRESS -> R.string.status_in_progress
        PatternStatus.FINISHED -> R.string.status_finished
    }

val PatternStatus.colors: StatusColor
    @Composable @ReadOnlyComposable
    get() = LocalStatusColors.current.let {
        when (this) {
            PatternStatus.WANT_TO_MAKE -> it.wantToMake
            PatternStatus.IN_PROGRESS -> it.inProgress
            PatternStatus.FINISHED -> it.finished
        }
    }

/** A chip for choosing a status, in that status's color: filled with a ✓ when selected, outlined otherwise. */
@Composable
fun StatusChip(status: PatternStatus, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = status.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(stringResource(status.labelRes)) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(FilterChipDefaults.IconSize)) }
        } else {
            null
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.container,
            selectedLabelColor = colors.content,
            selectedLeadingIconColor = colors.content,
        ),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = colors.accent),
        modifier = modifier,
    )
}

/** A small label in the status's color, for library cards. */
@Composable
fun StatusBadge(status: PatternStatus, modifier: Modifier = Modifier) {
    val colors = status.colors
    Text(
        stringResource(status.labelRes),
        style = MaterialTheme.typography.labelSmall,
        color = colors.content,
        maxLines = 1,
        modifier = modifier
            .background(colors.container, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
