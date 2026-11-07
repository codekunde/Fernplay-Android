package net.codekunde.fernplay.android.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.codekunde.fernplay.android.R
import net.codekunde.fernplay.android.data.PlaylistItem
import net.codekunde.fernplay.android.data.PlaylistRules
import net.codekunde.fernplay.android.library.SongList
import net.codekunde.fernplay.android.playback.PlayerConnection
import net.codekunde.fernplay.android.ui.LibraryViewModel
import net.codekunde.fernplay.android.ui.components.ArtTile
import net.codekunde.fernplay.android.ui.components.ButtonKind
import net.codekunde.fernplay.android.ui.components.EmptyState
import net.codekunde.fernplay.android.ui.components.FernButton
import net.codekunde.fernplay.android.ui.components.FernIconButton
import net.codekunde.fernplay.android.ui.components.FernMenu
import net.codekunde.fernplay.android.ui.components.FernMenuItem
import net.codekunde.fernplay.android.ui.components.FernSnackbarHost
import net.codekunde.fernplay.android.ui.theme.Fernplay

/** One playlist: Play, Shuffle, add songs; drag to reorder, remove with Undo, missing files flagged. */
@Composable
fun PlaylistScreen(
    vm: LibraryViewModel,
    player: PlayerConnection,
    playlistId: Long,
    onBack: () -> Unit,
    onAddSongs: () -> Unit,
) {
    val c = Fernplay.colors
    val store = vm.playlists
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val view by remember(playlistId) { store.view(playlistId) }.collectAsStateWithLifecycle(null)
    val now by player.now
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // The list as shown: follows the database, but holds still while a row is dragged.
    var working by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }
    val reorder = remember(listState) {
        ReorderState(listState) { from, to -> working = PlaylistRules.move(working, from, to) }
    }
    LaunchedEffect(view) { view?.let { if (reorder.draggingKey == null) working = it.items } }
    AutoScrollWhileDragging(reorder)

    val v = view
    val name = v?.name.orEmpty()
    val playable = working.mapNotNull { it.song }

    fun remove(index: Int) {
        val gone = store.remove(playlistId, working, index) ?: return
        working = working.filterIndexed { i, _ -> i != index }
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar("Removed “${gone.entry.title}”", actionLabel = "Undo", duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) store.undoRemove(playlistId, gone, index)
        }
    }

    fun move(from: Int, to: Int) {
        working = PlaylistRules.move(working, from, to)
        store.reorder(playlistId, working)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                FernIconButton(R.drawable.ic_back, "Back", onBack)
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = c.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    if (v != null) {
                        Text(
                            PlaylistRules.summary(working.size, working.sumOf { it.song?.durationMs ?: it.entry.durationMs }),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textDim,
                        )
                    }
                }
                if (v != null) {
                    Box {
                        FernIconButton(R.drawable.ic_more, "Rename or delete playlist", { menuOpen = true })
                        FernMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
                            FernMenuItem("Rename", R.drawable.ic_edit) { menuOpen = false; renaming = true }
                            FernMenuItem("Delete playlist", R.drawable.ic_delete) { menuOpen = false; deleting = true }
                        }
                    }
                }
            }
            if (v == null) return@Column // loading: a few milliseconds

            if (working.isEmpty()) {
                EmptyState(
                    icon = R.drawable.ic_fern_line,
                    title = "Nothing in here yet",
                    text = "Add songs from your library. Or long-press songs in All songs and choose Add to playlist.",
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    FernButton("Add songs", onAddSongs, icon = R.drawable.ic_add, modifier = Modifier.padding(top = 8.dp))
                }
                return@Column
            }

            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FernButton(
                    "Play",
                    { player.play(playable, 0, name) },
                    Modifier.weight(1f),
                    icon = R.drawable.ic_play,
                    enabled = playable.isNotEmpty(),
                )
                FernButton(
                    "Shuffle",
                    { player.play(playable.shuffled(), 0, name) },
                    icon = R.drawable.ic_shuffle,
                    kind = ButtonKind.Secondary,
                    enabled = playable.size > 1,
                )
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(c.surface)
                        .clickable(role = Role.Button, onClick = onAddSongs)
                        .semantics { contentDescription = "Add songs" },
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = c.text) }
            }

            val missing = working.count { it.missing }
            if (missing > 0) MissingBanner(missing)

            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(working, key = { _, it -> it.key }) { index, item ->
                    val dragging = reorder.draggingKey == item.key
                    val moves = buildList {
                        if (index > 0) add(CustomAccessibilityAction("Move up") { move(index, index - 1); true })
                        if (index < working.lastIndex) add(CustomAccessibilityAction("Move down") { move(index, index + 1); true })
                    }
                    val rowModifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) reorder.offset else 0f }
                        .semantics { customActions = moves }
                        // No placement animation for the dragged row, or it would lag behind the finger.
                        .then(if (dragging) Modifier else Modifier.animateItem())
                    if (item.missing) {
                        MissingRow(
                            item,
                            modifier = rowModifier,
                            onRescan = {
                                vm.rescan { scope.launch { snackbar.showSnackbar("Folders checked again") } }
                            },
                            onRemove = { remove(index) },
                        )
                    } else {
                        val song = item.song!!
                        val playing = now?.queueName == name && now?.mediaId == song.id.toString()
                        SongInPlaylistRow(
                            item,
                            playing = playing,
                            dragging = dragging,
                            modifier = rowModifier,
                            onPlay = { player.play(playable, playable.indexOf(song).coerceAtLeast(0), name) },
                            onRemove = { remove(index) },
                            dragHandle = Modifier.pointerInput(item.key) {
                                detectDragGestures(
                                    onDragStart = { reorder.start(item.key) },
                                    onDragEnd = { reorder.end(); store.reorder(playlistId, working) },
                                    onDragCancel = { reorder.end(); store.reorder(playlistId, working) },
                                ) { change, amount ->
                                    change.consume()
                                    reorder.drag(amount.y)
                                }
                            },
                        )
                    }
                }
            }
        }
        FernSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    if (renaming && v != null) {
        RenamePlaylistDialog(
            store,
            playlistId,
            current = v.name,
            onDismiss = { renaming = false },
            onRenamed = { newName ->
                renaming = false
                player.renameQueue(v.name, newName)
            },
        )
    }
    if (deleting && v != null) {
        DeletePlaylistDialog(
            v.name,
            songCount = v.items.size,
            onDismiss = { deleting = false },
            onDelete = {
                deleting = false
                scope.launch {
                    store.delete(playlistId)
                    onBack()
                }
            },
        )
    }
}

@Composable
private fun SongInPlaylistRow(
    item: PlaylistItem,
    playing: Boolean,
    dragging: Boolean,
    modifier: Modifier,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    dragHandle: Modifier,
) {
    val c = Fernplay.colors
    val song = item.song ?: return
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(
                if (dragging) {
                    Modifier
                        .padding(horizontal = 6.dp)
                        .shadow(10.dp, shape)
                        .clip(shape)
                        .background(c.surface)
                        .border(2.dp, c.accentLight, shape)
                } else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
                .clickable(onClickLabel = "Play", onClick = onPlay)
                .then(if (playing) Modifier.semantics { stateDescription = "Now playing" } else Modifier)
                .padding(start = if (dragging) 10.dp else 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ArtTile(song.id, icon = if (playing) R.drawable.ic_now_playing else R.drawable.ic_fern_line)
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (!playing) c.text else if (c.isDark) c.title else c.accent,
                    fontWeight = if (playing) FontWeight.Medium else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${song.artist} · ${SongList.duration(song.durationMs)}" + if (playing) " · playing" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        FernIconButton(R.drawable.ic_close, "Remove ${song.title} from playlist", onRemove, tint = c.textDim, iconSize = 20.dp)
        Box(
            dragHandle.size(48.dp).semantics { contentDescription = "Drag to reorder" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_drag), contentDescription = null, tint = if (dragging) c.accentLight else c.textDim)
        }
    }
}

/** Amber note above the list: how many songs are missing and that playback goes on. */
@Composable
private fun MissingBanner(count: Int) {
    val c = Fernplay.colors
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.amber)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(R.drawable.ic_warn), contentDescription = null, tint = c.onAmber)
        Text(
            buildString {
                append(if (count == 1) "1 song can’t be found." else "$count songs can’t be found.")
                append(" Playback skips ")
                append(if (count == 1) "it" else "them")
                append(", so the music keeps going.")
            },
            style = MaterialTheme.typography.bodyMedium,
            color = c.onAmber,
        )
    }
}

/** A song whose file is gone: what it was, where it was, and what can be done. */
@Composable
private fun MissingRow(item: PlaylistItem, modifier: Modifier, onRescan: () -> Unit, onRemove: () -> Unit) {
    val c = Fernplay.colors
    val e = item.entry
    Column(
        modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .padding(bottom = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(c.amber),
                contentAlignment = Alignment.Center,
            ) { Icon(painterResource(R.drawable.ic_warn), contentDescription = null, tint = c.onAmber) }
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.bodyLarge, color = c.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "File moved or deleted",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = c.amber,
                )
            }
        }
        Text(
            "${e.artist} · ${e.fileName} was in ${e.folder.ifEmpty { "a folder" }}, which no longer has it.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = c.textDim,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FernButton("Rescan folders", onRescan, Modifier.weight(1f), icon = R.drawable.ic_folder, kind = ButtonKind.Secondary, height = 48.dp)
            FernButton("Remove", onRemove, Modifier.weight(1f), icon = R.drawable.ic_delete, kind = ButtonKind.Outline, height = 48.dp)
        }
    }
}
