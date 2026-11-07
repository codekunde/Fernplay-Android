package net.codekunde.fernplay.android.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.ceil

/** What the sleep timer is doing. */
sealed interface SleepState {
    data object Off : SleepState

    /** Stops when the clock ([SystemClock.elapsedRealtime]) reaches [endAt]. */
    data class At(val endAt: Long) : SleepState

    /** Pauses when the playing song ends. */
    data object EndOfSong : SleepState
}

/** The sleep timer's choices, fade curve and text. No Android parts, so the JVM tests cover them. */
object SleepRules {
    val MINUTES = listOf(15, 30, 45, 60)
    const val EXTEND_MINUTES = 15
    const val FADE_MS = 30_000L
    const val MINUTE_MS = 60_000L

    /**
     * Player volume with [remainingMs] to go: full until the last 30 s, then
     * down to silence. Squared, so the drop sounds even instead of falling off
     * at the very end.
     */
    fun volumeAt(remainingMs: Long): Float {
        if (remainingMs >= FADE_MS) return 1f
        val f = remainingMs.coerceAtLeast(0) / FADE_MS.toFloat()
        return f * f
    }

    /** Fifteen more minutes: from the current end, or from now when none is set. */
    fun extend(state: SleepState, now: Long): SleepState = when (state) {
        is SleepState.At -> SleepState.At(maxOf(state.endAt, now) + EXTEND_MINUTES * MINUTE_MS)
        else -> SleepState.At(now + EXTEND_MINUTES * MINUTE_MS)
    }

    /** Time left as the LCD shows it: "23:10", "1:00:00"; seconds round up so it never shows 0:00 early. */
    fun clock(remainingMs: Long): String {
        val total = ceil(remainingMs.coerceAtLeast(0) / 1000.0).toLong()
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** What TalkBack and the sheet say: "23 minutes left", "1 minute left", "under a minute left". */
    fun spoken(remainingMs: Long): String {
        val min = ceil(remainingMs.coerceAtLeast(0) / MINUTE_MS.toDouble()).toLong()
        return when {
            remainingMs < MINUTE_MS -> "under a minute left"
            min == 1L -> "1 minute left"
            else -> "$min minutes left"
        }
    }

    fun choiceLabel(minutes: Int): String = if (minutes == 60) "1 hour" else "$minutes minutes"
}

/**
 * The sleep timer of the app process. The screens set it; [PlaybackService]
 * attaches its player, so the timer runs with the app closed. A timed stop
 * fades the volume over the last 30 s, pauses and puts the volume back; End of
 * song uses ExoPlayer's pause at the end of the item.
 */
class SleepTimer(
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) {
    private val _state = MutableStateFlow<SleepState>(SleepState.Off)
    val state: StateFlow<SleepState> = _state

    private val _remaining = MutableStateFlow<Long?>(null)
    /** Milliseconds left for a timed stop, updated every second (more often while fading); null otherwise. */
    val remaining: StateFlow<Long?> = _remaining

    private var player: ExoPlayer? = null
    private var faded = false

    private val endListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                _state.value == SleepState.EndOfSong
            ) set(SleepState.Off)
        }
    }

    private val tick = object : Runnable {
        override fun run() = step()
    }

    fun attach(p: ExoPlayer) {
        player?.removeListener(endListener)
        player = p
        p.addListener(endListener)
        apply()
    }

    fun detach(p: ExoPlayer) {
        if (player !== p) return
        p.removeListener(endListener)
        restoreVolume()
        p.pauseAtEndOfMediaItems = false
        player = null
    }

    fun start(minutes: Int) = set(SleepState.At(clock() + minutes * SleepRules.MINUTE_MS))
    fun endOfSong() = set(SleepState.EndOfSong)
    fun extend() = set(SleepRules.extend(_state.value, clock()))
    fun cancel() = set(SleepState.Off)

    private fun set(s: SleepState) {
        _state.value = s
        apply()
    }

    private fun apply() {
        handler.removeCallbacks(tick)
        val s = _state.value
        player?.pauseAtEndOfMediaItems = s == SleepState.EndOfSong
        if (s is SleepState.At) step() else {
            _remaining.value = null
            restoreVolume()
        }
    }

    private fun step() {
        val s = _state.value as? SleepState.At ?: return
        val left = s.endAt - clock()
        if (left <= 0) {
            player?.pause()
            _state.value = SleepState.Off
            _remaining.value = null
            restoreVolume()
            return
        }
        _remaining.value = left
        val v = SleepRules.volumeAt(left)
        if (v < 1f) {
            player?.volume = v
            faded = true
        } else restoreVolume()
        handler.postDelayed(tick, if (left <= SleepRules.FADE_MS + 1000) 250L else (left % 1000).let { if (it == 0L) 1000L else it })
    }

    private fun restoreVolume() {
        if (faded) {
            player?.volume = 1f
            faded = false
        }
    }

    companion object {
        /** The one the app uses; created on the main thread by the first caller. */
        val shared: SleepTimer by lazy { SleepTimer() }
    }
}
