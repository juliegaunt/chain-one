package io.github.juliegaunt.chainone

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.juliegaunt.chainone.ui.counter.LocalVolumeKeys
import io.github.juliegaunt.chainone.ui.counter.VolumeKeys
import io.github.juliegaunt.chainone.ui.navigation.ChainOneNavHost
import io.github.juliegaunt.chainone.ui.theme.ChainOneTheme

class MainActivity : ComponentActivity() {

    /** A link shared with "Share → Chain One" that hasn't been opened on the import screen yet. */
    private var pendingShare by mutableStateOf<String?>(null)

    private val volumeKeys = VolumeKeys()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on first launch: after a rotation the import screen is already showing.
        if (savedInstanceState == null) pendingShare = sharedText(intent)
        setContent {
            CompositionLocalProvider(LocalVolumeKeys provides volumeKeys) {
                ChainOneTheme {
                    ChainOneNavHost(sharedText = pendingShare, onShareHandled = { pendingShare = null })
                }
            }
        }
    }

    /** Sharing while Chain One is already open can arrive here instead of starting a new screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedText(intent)?.let { pendingShare = it }
    }

    /**
     * While a counter is showing (and the setting is on), the volume buttons count instead of
     * changing the volume. Holding a button down counts once, not repeatedly.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val handler = volumeKeys.handler
        if (handler == null || !isVolumeKey(keyCode)) return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0) handler(keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        return true
    }

    /** Swallows the release too, so the system volume panel doesn't appear. */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        if (volumeKeys.handler != null && isVolumeKey(keyCode)) true else super.onKeyUp(keyCode, event)

    private fun isVolumeKey(keyCode: Int) = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

    private fun sharedText(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
}
