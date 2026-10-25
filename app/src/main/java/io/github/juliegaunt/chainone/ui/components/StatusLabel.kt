package io.github.juliegaunt.chainone.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
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
