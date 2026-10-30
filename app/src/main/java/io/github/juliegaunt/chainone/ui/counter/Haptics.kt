package io.github.juliegaunt.chainone.ui.counter

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** [action] with the short tick of a mechanical counter, so a count can be felt as well as seen. */
@Composable
fun withTick(action: () -> Unit): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return {
        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        action()
    }
}
