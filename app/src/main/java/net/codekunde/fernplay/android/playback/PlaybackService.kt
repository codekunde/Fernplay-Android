package net.codekunde.fernplay.android.playback

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import net.codekunde.fernplay.android.eq.EqRenderersFactory
import net.codekunde.fernplay.android.eq.EqualizerState

/**
 * Plays music in the background and owns the media session: notification, lock
 * screen, headset, Bluetooth and car buttons all come through it.
 *
 * It also keeps the resume state: the queue, the song, the position and the
 * repeat mode are saved while playing and loaded again when the service starts,
 * so the app (and a headset's play button) carries on where it stopped.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var store: ResumeStore
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var savedQueueIds: List<String> = emptyList()
    private var savedQueueName: String? = null
    private var errorsInRow = 0

    private val saveTick = object : Runnable {
        override fun run() {
            savePosition()
            main.postDelayed(this, SAVE_EVERY_MS)
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(FernplayNotificationProvider(this))
        store = ResumeStore(filesDir)

        val eq = EqualizerState.get(this)
        val engine = ExoPlayer.Builder(this, EqRenderersFactory(this) { eq.settings.value })
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        val player = FernplayPlayer(engine)
        restore(player)
        SleepTimer.shared.attach(engine)

        val s = MediaSession.Builder(this, player)
            .setCallback(Callback())
            .build()
        player.addListener(object : Player.Listener {
            override fun onRepeatModeChanged(repeatMode: Int) {
                s.setCustomLayout(layoutFor(RepeatState.of(repeatMode)))
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_PLAYLIST_METADATA_CHANGED)) saveQueue()
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED,
                        Player.EVENT_POSITION_DISCONTINUITY,
                    )
                ) savePosition()
                if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                    main.removeCallbacks(saveTick)
                    if (player.isPlaying) {
                        errorsInRow = 0
                        main.postDelayed(saveTick, SAVE_EVERY_MS)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) = skipBrokenSong(player)
        })
        s.setCustomLayout(layoutFor(RepeatState.of(player.repeatMode)))
        session = s
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        main.removeCallbacks(saveTick)
        session?.run {
            savePosition()
            (player as? FernplayPlayer)?.engine?.let(SleepTimer.shared::detach)
            player.release()
            release()
        }
        session = null
        io.shutdown() // queued writes still finish
        super.onDestroy()
    }

    /** Loads the saved queue without starting it: Play (in the app, on a headset) carries on. */
    private fun restore(player: Player) {
        val state = runCatching { store.read() }.getOrNull() ?: return
        QueueEdits.restore(player, state)
        savedQueueIds = state.queue.items.map { it.id }
        savedQueueName = state.queue.name
    }

    private fun saveQueue() {
        val p = session?.player ?: return
        val items = (0 until p.mediaItemCount).map { p.getMediaItemAt(it) }
        val ids = items.map { it.mediaId }
        val name = p.playlistMetadata.title?.toString().orEmpty()
        if (ids.isEmpty() || (ids == savedQueueIds && name == savedQueueName)) return
        savedQueueIds = ids
        savedQueueName = name
        val queue = ResumeQueue(name, items.map(MediaItems::toResume))
        io.execute { runCatching { store.writeQueue(queue) } }
    }

    private fun savePosition() {
        val p = session?.player ?: return
        val item = p.currentMediaItem ?: return
        val position = ResumePosition(item.mediaId, p.currentMediaItemIndex, p.currentPosition, p.repeatMode)
        io.execute { runCatching { store.writePosition(position) } }
    }

    /**
     * A song that cannot be played (moved, deleted, damaged) is skipped. Stops
     * after one round through the queue, so a queue of only broken files ends.
     */
    private fun skipBrokenSong(player: Player) {
        errorsInRow++
        if (QueueEdits.skipAfterError(errorsInRow, player)) {
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        }
    }

    private inner class Callback : MediaSession.Callback {
        @OptIn(UnstableApi::class) // DEFAULT_SESSION_COMMANDS, AcceptedResultBuilder
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(CYCLE_REPEAT)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .setCustomLayout(layoutFor(RepeatState.of(session.player.repeatMode)))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_CYCLE_REPEAT) {
                val p = session.player
                p.repeatMode = RepeatState.of(p.repeatMode).next.mode
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        /** Play from a headset, Bluetooth device or Android's media card when nothing is queued. */
        @OptIn(UnstableApi::class)
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val state = runCatching { store.read() }.getOrNull()
                ?: return Futures.immediateFailedFuture(UnsupportedOperationException("Nothing to resume"))
            mediaSession.player.playlistMetadata = MediaItems.queueName(state.queue.name)
            mediaSession.player.repeatMode = state.position.repeatMode
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    state.queue.items.map(MediaItems::of),
                    state.startIndex,
                    state.startPositionMs,
                )
            )
        }
    }

    companion object {
        const val ACTION_CYCLE_REPEAT = "net.codekunde.fernplay.android.CYCLE_REPEAT"
        private val CYCLE_REPEAT = SessionCommand(ACTION_CYCLE_REPEAT, Bundle.EMPTY)
        private const val SAVE_EVERY_MS = 10_000L

        private fun layoutFor(state: RepeatState): ImmutableList<CommandButton> =
            ImmutableList.of(
                CommandButton.Builder()
                    .setDisplayName("Repeat ${state.label.lowercase()}")
                    .setIconResId(state.icon)
                    .setSessionCommand(CYCLE_REPEAT)
                    .build()
            )
    }
}
