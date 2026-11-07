package net.codekunde.fernplay.android.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.LongState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
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

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
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
            c.playlistMetadata = MediaItems.queueName(queueName)
            c.setMediaItems(items, index.coerceIn(0, items.lastIndex), 0L)
            c.prepare()
            c.play()
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

    fun cycleRepeat() = withController { c -> c.repeatMode = RepeatState.of(c.repeatMode).next.mode }
}
