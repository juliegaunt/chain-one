package io.github.juliegaunt.chainone.ui.components

import androidx.annotation.StringRes
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.data.db.PatternStatus

@get:StringRes
val PatternStatus.labelRes: Int
    get() = when (this) {
        PatternStatus.WANT_TO_MAKE -> R.string.status_want_to_make
        PatternStatus.IN_PROGRESS -> R.string.status_in_progress
        PatternStatus.FINISHED -> R.string.status_finished
    }
