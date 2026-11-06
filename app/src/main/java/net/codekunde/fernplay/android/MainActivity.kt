package net.codekunde.fernplay.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.codekunde.fernplay.android.ui.FernplayApp
import net.codekunde.fernplay.android.ui.theme.FernplayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FernplayTheme {
                FernplayApp()
            }
        }
    }
}
