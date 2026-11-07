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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import net.codekunde.fernplay.android.playback.PlayerConnection
import net.codekunde.fernplay.android.ui.components.FernTopBar
import net.codekunde.fernplay.android.ui.firstrun.FirstRunScreen
import net.codekunde.fernplay.android.ui.home.HomeScreen
import net.codekunde.fernplay.android.ui.player.GymScreen
import net.codekunde.fernplay.android.ui.player.MiniPlayer
import net.codekunde.fernplay.android.ui.player.NowPlayingScreen
import net.codekunde.fernplay.android.ui.songs.AllSongsScreen
import net.codekunde.fernplay.android.ui.theme.Fernplay

object Routes {
    const val FIRST_RUN = "first-run"
    const val HOME = "home"
    const val ALL_SONGS = "songs"
    const val SETTINGS = "settings"
    const val NOW_PLAYING = "now-playing"
    const val GYM = "gym"
}

@Composable
fun FernplayApp(vm: LibraryViewModel, player: PlayerConnection, nav: NavHostController = rememberNavController()) {
    val c = Fernplay.colors
    val settings by vm.settings.collectAsStateWithLifecycle()
    val entry by nav.currentBackStackEntryAsState()
    val now by player.now
    val openPlayer = { nav.navigate(Routes.NOW_PLAYING) { launchSingleTop = true } }
    Column(Modifier.fillMaxSize().background(c.background).safeDrawingPadding()) {
        val s = settings ?: return@Column // settings are read in a few milliseconds
        // Decided once: first launch starts with the folder question, later launches at Home.
        val start = remember { if (s.setupDone) Routes.HOME else Routes.FIRST_RUN }
        NavHost(navController = nav, startDestination = start, modifier = Modifier.weight(1f)) {
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
                    player,
                    onAllSongs = { nav.navigate(Routes.ALL_SONGS) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                    onChooseFolders = { nav.navigate(Routes.FIRST_RUN) },
                )
            }
            composable(Routes.ALL_SONGS) { AllSongsScreen(vm, player, onBack = { nav.popBackStack() }) }
            composable(Routes.SETTINGS) { NotBuiltYet("Settings", onBack = { nav.popBackStack() }) }
            composable(Routes.NOW_PLAYING) {
                NowPlayingScreen(
                    player,
                    onClose = { nav.popBackStack() },
                    onGym = { nav.navigate(Routes.GYM) { launchSingleTop = true } },
                )
            }
            composable(Routes.GYM) { GymScreen(player, onClose = { nav.popBackStack() }) }
        }
        if (now != null && entry?.destination?.route in MINI_PLAYER_ROUTES) MiniPlayer(player, onOpen = openPlayer)
    }
}

/** Screens that show the mini player when something is queued. */
private val MINI_PLAYER_ROUTES = setOf(Routes.HOME, Routes.ALL_SONGS)

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
