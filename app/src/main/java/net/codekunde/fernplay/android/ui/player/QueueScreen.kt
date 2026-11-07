package net.codekunde.fernplay.android.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.codekunde.fernplay.android.R
import net.codekunde.fernplay.android.library.SongList
import net.codekunde.fernplay.android.playback.NowPlayingText
import net.codekunde.fernplay.android.playback.PlayerConnection
import net.codekunde.fernplay.android.playback.QueueItem
import net.codekunde.fernplay.android.ui.components.ArtTile
import net.codekunde.fernplay.android.ui.components.EmptyState
import net.codekunde.fernplay.android.ui.components.FernIconButton
import net.codekunde.fernplay.android.ui.components.FernSnackbarHost
import net.codekunde.fernplay.android.ui.components.FernTopBar
import net.codekunde.fernplay.android.ui.theme.Fernplay

/**
 * The play queue: tap a song to play it. With [editable] (from Now Playing) a
 * song can be taken out, with Undo; from the gym view the list is read-only so
 * a stray tap mid-set cannot change it.
 */
@Composable
fun QueueScreen(player: PlayerConnection, editable: Boolean, onBack: () -> Unit) {
    val c = Fernplay.colors
    val now by player.now
    val queue by player.queue
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val n = now

    // Open at the playing song, with the one before it still in view.
    LaunchedEffect(Unit) { n?.let { listState.scrollToItem((it.index - 1).coerceAtLeast(0)) } }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            FernTopBar(title = "Queue", onBack = onBack) {
                if (n != null) {
                    Text(
                        NowPlayingText.trackOf(n.index, n.count),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textDim,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
            }
            if (n == null || queue.isEmpty()) {
                EmptyState(
                    icon = R.drawable.ic_queue,
                    title = "Queue is empty",
                    text = "Play a playlist or a song and the songs to come show here.",
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                return@Column
            }
            Text(
                "Playing from ${n.queueName.ifEmpty { "Fernplay" }}",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            )
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
                // Keys by position: the same song may be queued twice.
                itemsIndexed(queue, key = { i, item -> "$i:${item.mediaId}" }) { index, item ->
                    QueueRow(
                        item,
                        playing = index == n.index,
                        onPlay = { player.skipTo(index) },
                        onRemove = if (editable && index != n.index) {
                            {
                                player.removeFromQueue(index)
                                scope.launch {
                                    snackbar.currentSnackbarData?.dismiss()
                                    val r = snackbar.showSnackbar(
                                        "Took “${item.title}” out of the queue",
                                        actionLabel = "Undo",
                                        duration = SnackbarDuration.Long,
                                    )
                                    if (r == SnackbarResult.ActionPerformed) player.undoRemove()
                                }
                            }
                        } else null,
                    )
                }
            }
        }
        FernSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun QueueRow(item: QueueItem, playing: Boolean, onPlay: () -> Unit, onRemove: (() -> Unit)?) {
    val c = Fernplay.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
                .clickable(onClickLabel = "Play", onClick = onPlay)
                .then(if (playing) Modifier.semantics { stateDescription = "Now playing" } else Modifier)
                .padding(start = 16.dp, end = if (onRemove == null) 16.dp else 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ArtTile(item.mediaId.toLongOrNull() ?: 0L, icon = if (playing) R.drawable.ic_now_playing else R.drawable.ic_fern_line)
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (!playing) c.text else if (c.isDark) c.title else c.accent,
                    fontWeight = if (playing) FontWeight.Medium else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${item.artist} · ${SongList.duration(item.durationMs)}" + if (playing) " · playing" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onRemove != null) {
            FernIconButton(R.drawable.ic_close, "Take ${item.title} out of the queue", onRemove, tint = c.textDim, iconSize = 20.dp)
            Box(Modifier.padding(end = 4.dp))
        }
    }
}
