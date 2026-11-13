package net.codekunde.fernplay.android.playback

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyInfo
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
import net.codekunde.fernplay.android.data.FernplayDatabase
import net.codekunde.fernplay.android.data.StationFiles
import net.codekunde.fernplay.android.eq.EqRenderersFactory
import net.codekunde.fernplay.android.eq.EqualizerState

/**
 * Plays music and radio in the background and owns the media session:
 * notification, lock screen, headset, Bluetooth and car buttons all come through it.
 *
 * It also keeps the resume state: the queue, the song, the position and the
 * repeat mode are saved while playing and loaded again when the service starts,
 * so the app (and a headset's play button) carries on where it stopped.
 *
 * Stations: the live song goes into the item's title and artist, a broken stream
 * is connected again (at most 3 times a minute) instead of skipped, the stream
 * moves to Wi-Fi when Wi-Fi comes back, and Wi-Fi stays awake with the screen off.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var engine: ExoPlayer
    private lateinit var store: ResumeStore
    private val io: ExecutorService = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var savedQueueIds: List<String> = emptyList()
    private var savedQueueName: String? = null
    private var errorsInRow = 0

    // Radio
    private val reconnects = ReconnectBudget()
    private var streamTitle: String? = null
    private var currentId: String? = null
    private var currentIndex = C.INDEX_UNSET
    private val namesChecked = HashSet<Long>()
    private var network = NetworkKind.None
    private var connectivity: ConnectivityManager? = null

    private val saveTick = object : Runnable {
        override fun run() {
            savePosition()
            main.postDelayed(this, SAVE_EVERY_MS)
        }
    }

    /** Connects the playing station again after a break (see [stationBroke]). */
    private val reconnect = Runnable {
        val p = session?.player ?: return@Runnable
        if (!p.playWhenReady || !RadioRules.isStation(p.currentMediaItem?.mediaId)) return@Runnable
        // From the start of the stream: after an error (idle) or a stream that ended.
        p.seekToDefaultPosition(p.currentMediaItemIndex)
        if (p.playbackState == Player.STATE_IDLE) p.prepare()
    }

    /** Runs on the main thread (registered with its handler): the player may only be touched there. */
    private val networkWatch = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(n: Network, caps: NetworkCapabilities) {
            val now = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> NetworkKind.Wifi
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.Cellular
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> return // not validated yet: wait
                else -> NetworkKind.Other
            }
            val before = network
            network = now
            if (Handover.shouldReconnect(before, now)) moveStationToWifi()
        }

        override fun onLost(n: Network) {
            network = NetworkKind.None
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(FernplayNotificationProvider(this))
        store = ResumeStore(filesDir)

        val eq = EqualizerState.get(this)
        // Some stations send http to https redirects; Media3 refuses those unless allowed.
        val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        engine = ExoPlayer.Builder(this, EqRenderersFactory(this) { eq.settings.value })
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
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
                        reconnects.played()
                        main.postDelayed(saveTick, SAVE_EVERY_MS)
                    }
                }
                if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) && !player.playWhenReady) {
                    main.removeCallbacks(reconnect) // Pause stops a pending reconnect
                }
                if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED) &&
                    player.playbackState == Player.STATE_ENDED &&
                    RadioRules.isStation(player.currentMediaItem?.mediaId)
                ) stationBroke(player)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val p = session?.player ?: player
                val wasStation = RadioRules.isStation(currentId)
                // A station's stream that ends moves Media3 on to the next item: go back and reconnect.
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && wasStation &&
                    currentIndex in 0 until p.mediaItemCount && p.getMediaItemAt(currentIndex).mediaId == currentId
                ) {
                    val again = p.playWhenReady && reconnects.take(SystemClock.elapsedRealtime())
                    p.seekToDefaultPosition(currentIndex) // connects again at once
                    if (!again) p.pause()
                    return
                }
                if (mediaItem?.mediaId != currentId) {
                    currentId = mediaItem?.mediaId
                    streamTitle = null
                    reconnects.reset()
                    main.removeCallbacks(reconnect)
                }
                currentIndex = p.currentMediaItemIndex
                applyWakeMode()
            }

            override fun onPlayerError(error: PlaybackException) {
                if (RadioRules.isStation(player.currentMediaItem?.mediaId)) stationFailed(player, error)
                else skipBrokenSong(player)
            }

            override fun onMetadata(metadata: Metadata) {
                if (!RadioRules.isStation(player.currentMediaItem?.mediaId)) return
                for (i in 0 until metadata.length()) {
                    val icy = metadata.get(i) as? IcyInfo ?: continue
                    // Stations repeat the same title in every metadata block (about once a second).
                    if (icy.title == streamTitle) continue
                    streamTitle = icy.title
                    showLiveTitle(player)
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                adoptStationName(player, mediaMetadata.station?.toString())
            }
        })
        s.setCustomLayout(layoutFor(RepeatState.of(player.repeatMode)))
        session = s
        currentId = player.currentMediaItem?.mediaId
        currentIndex = player.currentMediaItemIndex
        applyWakeMode()

        connectivity = getSystemService(ConnectivityManager::class.java)?.also {
            runCatching { it.registerDefaultNetworkCallback(networkWatch, main) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        main.removeCallbacks(saveTick)
        main.removeCallbacks(reconnect)
        connectivity?.let { runCatching { it.unregisterNetworkCallback(networkWatch) } }
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

    /**
     * A station is never skipped (the desktop's rule): a network error is tried
     * again within the budget, anything else (wrong address, not audio) stops at
     * once. Stopping = paused with the error kept, which the app shows as
     * "Station unreachable"; Play tries again.
     */
    private fun stationFailed(player: Player, error: PlaybackException) {
        if (!RadioRules.retries(error.errorCode)) {
            giveUp(player)
            return
        }
        stationBroke(player)
    }

    private fun stationBroke(player: Player) {
        main.removeCallbacks(reconnect)
        if (!player.playWhenReady) return
        if (reconnects.take(SystemClock.elapsedRealtime())) {
            main.postDelayed(reconnect, reconnects.delayMs())
        } else {
            giveUp(player)
        }
    }

    /** Stops trying: paused, and the last song's title gives way to the station's name. */
    private fun giveUp(player: Player) {
        player.pause()
        if (player.playbackState == Player.STATE_ENDED) player.stop()
        streamTitle = null
        showLiveTitle(player)
    }

    /** A station on mobile data moves to Wi-Fi: the open stream would stay on mobile data until it broke. */
    private fun moveStationToWifi() {
        val p = session?.player ?: return
        if (!RadioRules.isStation(p.currentMediaItem?.mediaId) || !p.playWhenReady) return
        if (p.playbackState == Player.STATE_IDLE) return // a reconnect is already pending or it stopped
        main.removeCallbacks(reconnect)
        p.stop()
        p.prepare()
    }

    /** The live song into the item, so the notification, lock screen, car and app all show it. */
    private fun showLiveTitle(player: Player) {
        val index = player.currentMediaItemIndex
        val item = player.currentMediaItem ?: return
        val live = LiveTitles.of(streamTitle, MediaItems.stationName(item), player.mediaMetadata.station?.toString())
        val m = item.mediaMetadata
        if (m.title?.toString() == live.title && m.artist?.toString() == live.artist) return
        // Same address, so Media3 updates the item in place without reconnecting.
        player.replaceMediaItem(index, MediaItems.withLiveTitle(item, live))
    }

    /** A station added without a name in its file takes the name it sends (icy-name), once. */
    private fun adoptStationName(player: Player, icyName: String?) {
        val item = player.currentMediaItem ?: return
        val id = RadioRules.stationId(item.mediaId) ?: return
        val name = icyName?.let(StationFiles::clean) ?: return
        if (!namesChecked.add(id)) return
        val dao = FernplayDatabase.get(this).stations()
        io.execute {
            val changed = runCatching { dao.replaceGuessedName(id, name) }.getOrDefault(0) > 0
            if (changed) main.post { renameInQueue(item.mediaId, name) }
        }
    }

    private fun renameInQueue(mediaId: String, name: String) {
        val p = session?.player ?: return
        savedQueueIds = emptyList() // same ids, new name: the saved queue must be written again
        for (i in 0 until p.mediaItemCount) {
            val it = p.getMediaItemAt(i)
            if (it.mediaId == mediaId) p.replaceMediaItem(i, MediaItems.renamed(it, name))
        }
    }

    /** Wi-Fi and the CPU stay awake for a station with the screen off; songs are as before. */
    @OptIn(UnstableApi::class)
    private fun applyWakeMode() {
        engine.setWakeMode(if (RadioRules.isStation(currentId)) C.WAKE_MODE_NETWORK else C.WAKE_MODE_NONE)
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