package net.codekunde.fernplay.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.codekunde.fernplay.android.R
import net.codekunde.fernplay.android.library.SongList
import net.codekunde.fernplay.android.playback.PlayerConnection
import net.codekunde.fernplay.android.ui.player.ContinueCard
import net.codekunde.fernplay.android.ui.LibraryViewModel
import net.codekunde.fernplay.android.ui.components.EmptyState
import net.codekunde.fernplay.android.ui.components.FernIconButton
import net.codekunde.fernplay.android.ui.components.FernTopBar
import net.codekunde.fernplay.android.ui.components.NavRow
import net.codekunde.fernplay.android.ui.components.SectionLabel
import net.codekunde.fernplay.android.ui.theme.Fernplay
import java.text.NumberFormat

@Composable
fun HomeScreen(
    vm: LibraryViewModel,
    player: PlayerConnection,
    onAllSongs: () -> Unit,
    onSettings: () -> Unit,
    onChooseFolders: () -> Unit,
) {
    val c = Fernplay.colors
    val settings by vm.settings.collectAsStateWithLifecycle()
    val songs by vm.librarySongs.collectAsStateWithLifecycle()
    val granted by vm.hasPermission.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        FernTopBar(title = "Fernplay", showFern = true) {
            FernIconButton(R.drawable.ic_tune, "Settings", onSettings)
        }
        val noFolders = settings?.folders?.isEmpty() == true
        when {
            noFolders || !granted -> EmptyState(
                icon = R.drawable.ic_folder,
                title = if (granted) "No music folders yet" else "Fernplay can't see your music",
                text = if (granted) {
                    "Choose where your songs live and they will show up here, with your playlists. This is a one-time step."
                } else {
                    "Music access was turned off in Android's settings. Allow it again to see your songs."
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                Button(
                    onClick = onChooseFolders,
                    modifier = Modifier.padding(top = 8.dp).height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_folder), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(if (granted) "Choose folders" else "Allow access")
                }
            }
            else -> Column(Modifier.weight(1f)) {
                ContinueCard(player)
                val list = songs
                NavRow(
                    title = "All songs",
                    subtitle = when {
                        list == null -> "Reading your music…"
                        list.isEmpty() -> "No songs in your folders yet"
                        else -> "${count(list.size)} · ${SongList.totalLength(list.sumOf { it.durationMs })}"
                    },
                    onClick = onAllSongs,
                    leading = {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(c.accentContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.ic_music), contentDescription = null, tint = c.onAccentContainer)
                        }
                    },
                )
                SectionLabel("Playlists")
                Text(
                    "No playlists yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textDim,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private fun count(n: Int): String = if (n == 1) "1 song" else "${NumberFormat.getIntegerInstance().format(n)} songs"
