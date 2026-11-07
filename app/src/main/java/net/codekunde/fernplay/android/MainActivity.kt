package net.codekunde.fernplay.android

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.codekunde.fernplay.android.playback.PlayerConnection
import net.codekunde.fernplay.android.ui.FernplayApp
import net.codekunde.fernplay.android.ui.LibraryViewModel
import net.codekunde.fernplay.android.ui.theme.FernplayTheme
import net.codekunde.fernplay.android.ui.theme.ThemeChoice
import net.codekunde.fernplay.android.ui.theme.isDark

class MainActivity : ComponentActivity() {

    private val vm: LibraryViewModel by viewModels()
    private lateinit var player: PlayerConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        player = PlayerConnection(this)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val choice = settings?.theme ?: ThemeChoice.System
            val dark = isDark(choice)
            // Status and navigation bar icons follow the app's theme, not the phone's:
            // with the phone dark and Fernplay set to Light they must turn dark.
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
            }
            FernplayTheme(choice) {
                FernplayApp(vm, player)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        player.connect()
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermission() // music access may have changed in Android's settings
    }

    override fun onStop() {
        player.release()
        super.onStop()
    }

    private companion object {
        // The scrims enableEdgeToEdge() uses by default (three-button navigation only).
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
