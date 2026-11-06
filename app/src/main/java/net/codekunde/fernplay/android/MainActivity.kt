package net.codekunde.fernplay.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.codekunde.fernplay.android.playback.PlayerConnection
import net.codekunde.fernplay.android.ui.FernplayApp
import net.codekunde.fernplay.android.ui.LibraryViewModel
import net.codekunde.fernplay.android.ui.theme.FernplayTheme
import net.codekunde.fernplay.android.ui.theme.ThemeChoice

class MainActivity : ComponentActivity() {

    private val vm: LibraryViewModel by viewModels()
    private lateinit var player: PlayerConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        player = PlayerConnection(this)
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            FernplayTheme(settings?.theme ?: ThemeChoice.System) {
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
}
