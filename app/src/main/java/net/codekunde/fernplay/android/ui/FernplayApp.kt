package net.codekunde.fernplay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.codekunde.fernplay.android.R
import net.codekunde.fernplay.android.ui.theme.Fernplay
import net.codekunde.fernplay.android.ui.theme.LcdText

/** Screens from the design. Each one is a placeholder until its stage is built. */
object Routes {
    const val FIRST_RUN = "first-run"
    const val HOME = "home"
    const val ALL_SONGS = "songs"
    const val PLAYLIST = "playlist"
    const val NOW_PLAYING = "now-playing"
    const val SETTINGS = "settings"
}

@Composable
fun FernplayApp(nav: NavHostController = rememberNavController()) {
    val c = Fernplay.colors
    Box(Modifier.fillMaxSize().background(c.background).safeDrawingPadding()) {
        NavHost(navController = nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) { HomeShell(nav) }
            composable(Routes.FIRST_RUN) { Placeholder("Where is your music?", nav) }
            composable(Routes.ALL_SONGS) { Placeholder("All songs", nav) }
            composable(Routes.PLAYLIST) { Placeholder("Playlist", nav) }
            composable(Routes.NOW_PLAYING) { Placeholder("Now Playing", nav) }
            composable(Routes.SETTINGS) { Placeholder("Settings", nav) }
        }
    }
}

@Composable
private fun HomeShell(nav: NavHostController) {
    val c = Fernplay.colors
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )
            Text("Fernplay", style = MaterialTheme.typography.headlineSmall, color = c.title)
        }
        Column(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Screens to come", style = MaterialTheme.typography.labelSmall, color = c.textDim)
            listOf(
                "First run" to Routes.FIRST_RUN,
                "All songs" to Routes.ALL_SONGS,
                "Playlist" to Routes.PLAYLIST,
                "Now Playing" to Routes.NOW_PLAYING,
                "Settings" to Routes.SETTINGS,
            ).forEach { (label, route) ->
                OutlinedButton(onClick = { nav.navigate(route) }, modifier = Modifier.fillMaxWidth()) {
                    Text(label)
                }
            }
        }
        MiniPlayerPreview()
    }
}

/** The LCD strip the mini player will become; shows the font and colours on a phone. */
@Composable
private fun MiniPlayerPreview() {
    val c = Fernplay.colors
    Column(
        Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.lcdBackground)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text("NOTHING PLAYING", style = LcdText.mini, color = c.lcd)
        Text("0:00 / 0:00", style = LcdText.small, color = c.lcdDim)
    }
}

@Composable
private fun Placeholder(title: String, nav: NavHostController) {
    val c = Fernplay.colors
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = c.title)
        Text("Not built yet.", style = MaterialTheme.typography.bodyMedium, color = c.textDim)
        Button(onClick = { nav.popBackStack() }) { Text("Back") }
    }
}
