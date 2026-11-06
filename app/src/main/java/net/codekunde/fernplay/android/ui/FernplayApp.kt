package net.codekunde.fernplay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.codekunde.fernplay.android.playback.PlayerConnection
import net.codekunde.fernplay.android.ui.components.FernTopBar
import net.codekunde.fernplay.android.ui.firstrun.FirstRunScreen
import net.codekunde.fernplay.android.ui.home.HomeScreen
import net.codekunde.fernplay.android.ui.songs.AllSongsScreen
import net.codekunde.fernplay.android.ui.theme.Fernplay

object Routes {
    const val FIRST_RUN = "first-run"
    const val HOME = "home"
    const val ALL_SONGS = "songs"
    const val SETTINGS = "settings"
}

@Composable
fun FernplayApp(vm: LibraryViewModel, player: PlayerConnection, nav: NavHostController = rememberNavController()) {
    val c = Fernplay.colors
    val settings by vm.settings.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize().background(c.background).safeDrawingPadding()) {
        val s = settings ?: return@Box // settings are read in a few milliseconds
        // Decided once: first launch starts with the folder question, later launches at Home.
        val start = remember { if (s.setupDone) Routes.HOME else Routes.FIRST_RUN }
        NavHost(navController = nav, startDestination = start) {
            composable(Routes.FIRST_RUN) {
                FirstRunScreen(vm, onDone = {
                    if (!nav.popBackStack(Routes.HOME, inclusive = false)) {
                        nav.navigate(Routes.HOME) { popUpTo(Routes.FIRST_RUN) { inclusive = true } }
                    }
                })
            }
            composable(Routes.HOME) {
                HomeScreen(
                    vm,
                    onAllSongs = { nav.navigate(Routes.ALL_SONGS) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onChooseFolders = { nav.navigate(Routes.FIRST_RUN) },
                )
            }
            composable(Routes.ALL_SONGS) { AllSongsScreen(vm, player, onBack = { nav.popBackStack() }) }
            composable(Routes.SETTINGS) { NotBuiltYet("Settings", onBack = { nav.popBackStack() }) }
        }
    }
}

@Composable
private fun NotBuiltYet(title: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FernTopBar(title = title, onBack = onBack)
        Text(
            "Not built yet.",
            style = MaterialTheme.typography.bodyLarge,
            color = Fernplay.colors.textDim,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
