package io.github.juliegaunt.chainone.ui.counter

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Lets the screen that's showing a counter take over the phone's volume buttons.
 * [MainActivity][io.github.juliegaunt.chainone.MainActivity] passes the presses on.
 */
class VolumeKeys {
    /** Called with true for volume up and false for down; null when nothing is counting. */
    var handler: ((up: Boolean) -> Unit)? = null
}

val LocalVolumeKeys = staticCompositionLocalOf { VolumeKeys() }
