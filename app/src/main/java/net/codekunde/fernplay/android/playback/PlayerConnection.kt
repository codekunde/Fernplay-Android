package net.codekunde.fernplay.android.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.LongState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import net.codekunde.fernplay.android.library.Song

/** What the player screens show about the loaded song and queue. */
data class NowPlaying(
    val mediaId: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val index: Int,
    val count: Int,
    /** Where the queue came from: "All songs" or a playlist's name. */
    val queueName: String,
    val isPlaying: Boolean,
    val repeat: RepeatState,
)

/** One song in the play queue, as the queue view lists it. */
data class QueueItem(val mediaId: String, val title: String, val artist: String, val durationMs: Long)

/**
 * The screens' handle on the player in [PlaybackService]. Connect while the app
 * is visible ([connect] in onStart, [release] in onStop).
 */
class PlayerConnection(private val context: Context) {

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    private val _currentMediaId = mutableStateOf<String?>(null)
    /** The song now loaded in the player (its id as text), or null. */
    val currentMediaId: State<String?> = _currentMediaId

    private val _now = mutableStateOf<NowPlaying?>(null)
    /** The loaded song and queue, or null when nothing is queued. */
    val now: State<NowPlaying?> = _now

    private val _position = mutableLongStateOf(0L)
    /** Position in the loaded song, updated twice a second while playing. */
    val position: LongState = _position

    private val _queue = mutableStateOf<List<QueueItem>>(emptyList())
    /** Every song in the play queue, in play order. */
    val queue: State<List<QueueItem>> = _queue

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) refreshQueue()
            refresh()
        }
    }

    @OptIn(UnstableApi::class) // MediaMetadata.durationMs
    private fun refreshQueue() {
        val c = controller ?: return
        _queue.value = (0 until c.mediaItemCount).map { i ->
            val item = c.getMediaItemAt(i)
            val m = item.mediaMetadata
            QueueItem(item.mediaId, m.title?.toString().orEmpty(), m.artist?.toString().orEmpty(), m.durationMs ?: 0L)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            controller?.let { _position.longValue = it.currentPosition }
            main.postDelayed(this, 500)
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            if (future !== f) return@addListener // released meanwhile
            controller = c
            c.addListener(listener)
            refreshQueue()
            refresh()
            pending.forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        main.removeCallbacks(tick)
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }

    @OptIn(UnstableApi::class) // MediaMetadata.durationMs
    private fun refresh() {
        val c = controller ?: return
        val item = c.currentMediaItem
        _currentMediaId.value = item?.mediaId
        _position.longValue = c.currentPosition
        main.removeCallbacks(tick)
        if (c.isPlaying) main.postDelayed(tick, 500)
        if (item == null || c.mediaItemCount == 0) {
            _now.value = null
            return
        }
        val m = item.mediaMetadata
        val duration = c.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: m.durationMs ?: 0L
        _now.value = NowPlaying(
            mediaId = item.mediaId,
            title = m.title?.toString().orEmpty(),
            artist = m.artist?.toString().orEmpty(),
            durationMs = duration,
            index = c.currentMediaItemIndex,
            count = c.mediaItemCount,
            queueName = c.playlistMetadata.title?.toString().orEmpty(),
            isPlaying = c.isPlaying || (c.playWhenReady && c.playbackState == Player.STATE_BUFFERING),
            repeat = RepeatState.of(c.repeatMode),
        )
    }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let(action) ?: pending.add(action)
    }

    /** Plays [songs] as the queue named [queueName], starting at [index]. */
    fun play(songs: List<Song>, index: Int, queueName: String) {
        if (songs.isEmpty()) return
        val items = songs.map(MediaItems::of)
        withController { c ->
            // Shuffle is done by ordering the list, so "7 / 24" and resume follow what is heard.
            QueueEdits.replace(c, items, index, queueName)
            c.prepare()
            c.play()
        }
    }

    /** Puts [songs] right after the current song; with nothing queued they become the queue. */
    fun playNext(songs: List<Song>, queueName: String) {
        if (songs.isEmpty()) return
        withController { c ->
            if (!QueueEdits.playNext(c, songs.map(MediaItems::of))) play(songs, 0, queueName)
        }
    }

    /** Pause, or carry on with the loaded queue (also after a restart or the end of the queue). */
    fun playPause() = withController { c ->
        if (c.isPlaying || (c.playWhenReady && c.playbackState == Player.STATE_BUFFERING)) {
            c.pause()
            return@withController
        }
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
        c.play()
    }

    fun next() = withController { it.seekToNext() }

    /** Back to the start of the song, or to the previous song in its first seconds. */
    fun previous() = withController { it.seekToPrevious() }

    fun seekTo(positionMs: Long) = withController { c ->
        c.seekTo(positionMs)
        _position.longValue = positionMs
    }

    fun cycleRepeat() = withController(QueueEdits::cycleRepeat)

    /** Plays the queue's song at [index] from its start. */
    fun skipTo(index: Int) = withController { c ->
        if (index !in 0 until c.mediaItemCount) return@withController
        c.seekToDefaultPosition(index)
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    private var lastRemoved: Pair<Int, androidx.media3.common.MediaItem>? = null

    /** Takes the song at [index] out of the queue (never the one playing); [undoRemove] puts it back. */
    fun removeFromQueue(index: Int) = withController { c ->
        QueueEdits.remove(c, index)?.let { lastRemoved = it }
    }

    fun undoRemove() = withController { c ->
        val removed = lastRemoved ?: return@withController
        lastRemoved = null
        QueueEdits.undoRemove(c, removed)
    }

    /** Keeps "Playing from" right after the playing playlist is renamed. */
    fun renameQueue(from: String, to: String) = withController { c -> QueueEdits.renameQueue(c, from, to) }
}
