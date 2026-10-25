package io.github.juliegaunt.chainone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.juliegaunt.chainone.ui.navigation.ChainOneNavHost
import io.github.juliegaunt.chainone.ui.theme.ChainOneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChainOneTheme {
                ChainOneNavHost()
            }
        }
    }
}
