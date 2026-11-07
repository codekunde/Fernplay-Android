package net.codekunde.fernplay.android.ui.playlists

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.codekunde.fernplay.android.R
import net.codekunde.fernplay.android.data.PlaylistRules
import net.codekunde.fernplay.android.data.PlaylistStore
import net.codekunde.fernplay.android.library.Song
import net.codekunde.fernplay.android.ui.components.ArtTile
import net.codekunde.fernplay.android.ui.components.FernButton
import net.codekunde.fernplay.android.ui.theme.Fernplay

/**
 * "Add N songs to…": type a name for a new playlist (one tap), or pick one.
 * [onDone] gets the note to show (what was added, what was already in).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(store: PlaylistStore, songs: List<Song>, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val c = Fernplay.colors
    val playlists by store.summaries.collectAsStateWithLifecycle()
    val inside by store.idsByPlaylist.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }

    fun addTo(id: Long, playlistName: String) {
        scope.launch {
            val (added, skipped) = store.add(id, songs)
            sheet.hide()
            onDone(PlaylistRules.addedMessage(added, skipped, playlistName))
        }
    }

    fun createAndAdd() {
        scope.launch {
            problem = store.nameProblem(name)
            if (problem != null) return@launch
            val id = store.create(name) ?: return@launch
            addTo(id, name.trim())
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            Text(
                "Add ${PlaylistRules.songCount(songs.size)} to…",
                fontSize = 20.sp,
                lineHeight = 26.sp,
                color = c.title,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 10.dp).semantics { heading() },
            )

            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .border(2.dp, if (problem != null) c.amber else c.accentLight, RoundedCornerShape(16.dp))
                    .padding(start = 16.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = c.accentLight)
                Box(Modifier.weight(1f)) {
                    if (name.isEmpty()) Text("New playlist name", style = MaterialTheme.typography.bodyLarge, color = c.textDim)
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it.take(PlaylistRules.MAX_NAME + 1); problem = null },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text),
                        cursorBrush = SolidColor(c.accentLight),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) createAndAdd() }),
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "New playlist name" },
                    )
                }
                FernButton("Create & add", ::createAndAdd, height = 44.dp, enabled = name.isNotBlank())
            }
            Text(
                problem ?: "New playlist: type a name, one tap. No folders, no files.",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = if (problem != null) c.amber else c.textDim,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 4.dp),
            )

            val list = playlists.orEmpty()
            if (list.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp), color = c.outline.copy(alpha = 0.5f))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(list, key = { it.id }) { p ->
                        val ids = inside[p.id].orEmpty()
                        val already = songs.filter { it.id in ids }.map { it.title }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .clickable(onClickLabel = "Add to ${p.name}") { addTo(p.id, p.name) }
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            ArtTile(p.id, size = 40.dp, icon = R.drawable.ic_queue)
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    PlaylistRules.sheetLine(p.songCount, already),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = c.textDim,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(painterResource(R.drawable.ic_chevron), contentDescription = null, tint = c.textDim)
                        }
                    }
                }
            }
        }
    }
}
