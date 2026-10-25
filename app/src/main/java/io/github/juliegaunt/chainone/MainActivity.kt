package io.github.juliegaunt.chainone

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.juliegaunt.chainone.ui.navigation.ChainOneNavHost
import io.github.juliegaunt.chainone.ui.theme.ChainOneTheme

class MainActivity : ComponentActivity() {

    /** A link shared with "Share → Chain One" that hasn't been opened on the import screen yet. */
    private var pendingShare by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on first launch: after a rotation the import screen is already showing.
        if (savedInstanceState == null) pendingShare = sharedText(intent)
        setContent {
            ChainOneTheme {
                ChainOneNavHost(sharedText = pendingShare, onShareHandled = { pendingShare = null })
            }
        }
    }

    /** Sharing while Chain One is already open can arrive here instead of starting a new screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedText(intent)?.let { pendingShare = it }
    }

    private fun sharedText(intent: Intent?): String? =
        if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT) else null
}
