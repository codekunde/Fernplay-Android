package net.codekunde.fernplay.android.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import net.codekunde.fernplay.android.library.Song

/**
 * The screens' handle on the player in [PlaybackService]. Connect while the app
 * is visible ([connect] in onStart, [release] in onStop).
 */
class PlayerConnection(private val context: Context) {

    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()

    private val _currentMediaId = mutableStateOf<String?>(null)
    /** The song now loaded in the player (its id as text), or null. */
    val currentMediaId: State<String?> = _currentMediaId

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentMediaId.value = mediaItem?.mediaId
        }
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            _currentMediaId.value = c.currentMediaItem?.mediaId
            pending.forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(context))
    }

    fun release() {
        controller?.removeListener(listener)
        future?.let { MediaController.releaseFuture(it) }
        future = null
        controller = null
    }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let(action) ?: pending.add(action)
    }

    /** Plays [songs] as the queue, starting at [index]. */
    fun play(songs: List<Song>, index: Int) {
        if (songs.isEmpty()) return
        val items = songs.map { it.toMediaItem() }
        withController { c ->
            c.setMediaItems(items, index.coerceIn(0, items.lastIndex), 0L)
            c.prepare()
            c.play()
        }
    }

    companion object {
        fun Song.toMediaItem(): MediaItem =
            MediaItem.Builder()
                .setMediaId(id.toString())
                .setUri(Uri.parse(uri))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(artist)
                        .setAlbumTitle(album)
                        .build()
                )
                .build()
    }
}
