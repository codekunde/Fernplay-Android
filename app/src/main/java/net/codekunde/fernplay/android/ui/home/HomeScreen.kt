package net.codekunde.fernplay.android.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.codekunde.fernplay.android.R
import net.codekunde.fernplay.android.library.SongList
import net.codekunde.fernplay.android.playback.PlayerConnection
import net.codekunde.fernplay.android.ui.LibraryViewModel
import net.codekunde.fernplay.android.ui.components.EmptyNote
import net.codekunde.fernplay.android.ui.components.EmptyState
import net.codekunde.fernplay.android.ui.components.FernIconButton
import net.codekunde.fernplay.android.ui.components.FernTopBar
import net.codekunde.fernplay.android.ui.components.NavRow
import net.codekunde.fernplay.android.ui.components.SectionLabel
import net.codekunde.fernplay.android.ui.player.ContinueCard
import net.codekunde.fernplay.android.ui.remote.PcTile
import net.codekunde.fernplay.android.ui.playlists.NewPlaylistDialog
import net.codekunde.fernplay.android.ui.playlists.NewPlaylistRow
import net.codekunde.fernplay.android.ui.playlists.PlaylistRow
import net.codekunde.fernplay.android.ui.theme.Fernplay
import java.text.NumberFormat

@Composable
fun HomeScreen(
    vm: LibraryViewModel,
    player: PlayerConnection,
    onAllSongs: () -> Unit,
    onPlaylist: (Long) -> Unit,
    onSettings: () -> Unit,
    onChooseFolders: () -> Unit,
    onRemote: () -> Unit,
) {
    val c = Fernplay.colors
    val settings by vm.settings.collectAsStateWithLifecycle()
    val songs by vm.librarySongs.collectAsStateWithLifecycle()
    val granted by vm.hasPermission.collectAsStateWithLifecycle()
    val playlists by vm.playlists.summaries.collectAsStateWithLifecycle()
    val now by player.now
    val scope = rememberCoroutineScope()
    var creating by rememberSaveable { mutableStateOf(false) }
    var refused by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val askAccess = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        vm.refreshPermission()
        refused = !ok
    }

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
                    // Access is asked for here, so the folders already chosen stay as they are.
                    onClick = { if (granted) onChooseFolders() else askAccess.launch(vm.musicPermission) },
                    modifier = Modifier.padding(top = 8.dp).height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_folder), contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(if (granted) "Choose folders" else "Allow access")
                }
                if (!granted && refused) {
                    // After two refusals Android stops asking; access can still be given in its settings.
                    OutlinedButton(
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            )
                        },
                        modifier = Modifier.height(48.dp),
                    ) { Text("Open Fernplay in Android settings") }
                }
            }
            else -> LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                item(key = "continue") { ContinueCard(player) }
                item(key = "all") {
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
                }
                item(key = "remote") {
                    NavRow(
                        title = "Fernplay on your PC",
                        subtitle = "Control the PC's player from here",
                        onClick = onRemote,
                        leading = { PcTile() },
                    )
                }
                item(key = "label") { SectionLabel("Playlists") }
                val list = playlists
                if (list != null && list.isEmpty()) {
                    item(key = "none") { EmptyNote("No playlists yet.") }
                }
                items(list.orEmpty(), key = { "p${it.id}" }) { p ->
                    PlaylistRow(
                        p,
                        // Names are unique, so the queue's name tells which playlist is playing.
                        playing = now?.queueName == p.name,
                        onOpen = { onPlaylist(p.id) },
                        onPlay = {
                            scope.launch {
                                val view = vm.playlists.view(p.id).filterNotNull().first()
                                player.play(view.songs, 0, view.name)
                            }
                        },
                    )
                }
                item(key = "new") { NewPlaylistRow { creating = true } }
            }
        }
    }
    if (creating) {
        NewPlaylistDialog(
            vm.playlists,
            onDismiss = { creating = false },
            onCreated = { id -> creating = false; onPlaylist(id) },
        )
    }
}

private fun count(n: Int): String = if (n == 1) "1 song" else "${NumberFormat.getIntegerInstance().format(n)} songs"
