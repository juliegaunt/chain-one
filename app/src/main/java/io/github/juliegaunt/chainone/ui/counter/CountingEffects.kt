package io.github.juliegaunt.chainone.ui.counter

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import io.github.juliegaunt.chainone.ui.counter.CounterUiState
import io.github.juliegaunt.chainone.ui.counter.LocalVolumeKeys
import io.github.juliegaunt.chainone.ui.counter.withTick

/**
 * While a counter is showing: keeps the screen on and lets the volume buttons count, if those
 * settings are on. Both stop when the counter goes away.
 */
@Composable
fun CountingEffects(active: Boolean, state: CounterUiState, onVolumeUp: () -> Unit, onVolumeDown: () -> Unit) {
    val view = LocalView.current
    val keepOn = active && state.keepScreenOn
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }

    val volumeKeys = LocalVolumeKeys.current
    val countWithKeys = active && state.countWithVolumeKeys
    val up by rememberUpdatedState(withTick(onVolumeUp))
    val down by rememberUpdatedState(withTick(onVolumeDown))
    DisposableEffect(countWithKeys) {
        val handler: (Boolean) -> Unit = { isUp -> if (isUp) up() else down() }
        if (countWithKeys) volumeKeys.handler = handler
        onDispose { if (volumeKeys.handler === handler) volumeKeys.handler = null }
    }
}
