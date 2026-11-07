package net.codekunde.fernplay.android

import android.os.Looper
import android.os.SystemClock
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Duration
import net.codekunde.fernplay.android.playback.SleepRules
import net.codekunde.fernplay.android.playback.SleepState
import net.codekunde.fernplay.android.playback.SleepTimer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** The sleep timer's rules, and the timer on a real ExoPlayer with Robolectric's clock. */
@RunWith(AndroidJUnit4::class)
class SleepTimerTest {

    private lateinit var p: ExoPlayer
    private lateinit var timer: SleepTimer

    @Before
    fun open() {
        p = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        timer = SleepTimer()
        timer.attach(p)
        p.play()
    }

    @After
    fun close() {
        timer.detach(p)
        p.release()
    }

    private fun pass(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test
    fun fadeCurve() {
        assertEquals(1f, SleepRules.volumeAt(60_000), 0f)
        assertEquals(1f, SleepRules.volumeAt(30_000), 0f)
        assertEquals(0.25f, SleepRules.volumeAt(15_000), 1e-6f)
        assertEquals(0f, SleepRules.volumeAt(0), 0f)
        assertEquals(0f, SleepRules.volumeAt(-5), 0f)
    }

    @Test
    fun clockAndWords() {
        assertEquals("15:00", SleepRules.clock(15 * 60_000L))
        assertEquals("0:01", SleepRules.clock(1)) // rounds up, never 0:00 early
        assertEquals("1:00:00", SleepRules.clock(60 * 60_000L))
        assertEquals("23 minutes left", SleepRules.spoken(22 * 60_000L + 1))
        assertEquals("1 minute left", SleepRules.spoken(60_000))
        assertEquals("under a minute left", SleepRules.spoken(59_000))
        assertEquals("1 hour", SleepRules.choiceLabel(60))
    }

    @Test
    fun extendAddsFifteenMinutes() {
        assertEquals(SleepState.At(1_000 + 15 * 60_000L), SleepRules.extend(SleepState.Off, 1_000))
        assertEquals(SleepState.At(1_000 + 15 * 60_000L), SleepRules.extend(SleepState.EndOfSong, 1_000))
        assertEquals(SleepState.At(500_000 + 15 * 60_000L), SleepRules.extend(SleepState.At(500_000), 1_000))
        // An end already passed counts from now.
        assertEquals(SleepState.At(9_000 + 15 * 60_000L), SleepRules.extend(SleepState.At(5_000), 9_000))
    }

    @Test
    fun timedStopFadesPausesAndRestoresVolume() {
        timer.start(15)
        assertEquals(15 * 60_000L, timer.remaining.value)
        pass(14 * 60_000L + 30_000) // 30 s left: still full
        assertEquals(1f, p.volume, 0f)
        pass(15_000) // 15 s left: a quarter
        assertEquals(0.25f, p.volume, 0.02f)
        assertTrue(p.playWhenReady)
        pass(16_000)
        assertFalse(p.playWhenReady)
        assertEquals(1f, p.volume, 0f)
        assertEquals(SleepState.Off, timer.state.value)
        assertNull(timer.remaining.value)
    }

    @Test
    fun cancelDuringFadeRestoresVolume() {
        timer.start(15)
        pass(15 * 60_000L - 10_000)
        assertTrue(p.volume < 0.2f)
        timer.cancel()
        assertEquals(1f, p.volume, 0f)
        pass(60_000)
        assertTrue(p.playWhenReady)
    }

    @Test
    fun extendDuringFadeRestoresVolume() {
        timer.start(15)
        pass(15 * 60_000L - 10_000)
        timer.extend()
        assertEquals(1f, p.volume, 0f)
        val left = timer.remaining.value!!
        assertEquals((15 * 60_000L + 10_000).toDouble(), left.toDouble(), 300.0)
    }

    @Test
    fun endOfSongUsesPauseAtEndAndTurnsOffWithTheTimer() {
        timer.endOfSong()
        assertTrue(p.pauseAtEndOfMediaItems)
        assertNull(timer.remaining.value)
        timer.cancel()
        assertFalse(p.pauseAtEndOfMediaItems)
        timer.start(30)
        assertFalse(p.pauseAtEndOfMediaItems)
    }

    @Test
    fun detachLeavesThePlayerAsFound() {
        timer.start(1)
        pass(50_000) // 10 s left: faded
        assertTrue(p.volume < 1f)
        timer.detach(p)
        assertEquals(1f, p.volume, 0f)
        assertFalse(p.pauseAtEndOfMediaItems)
        // The clock is SystemClock's; Robolectric advanced it with the looper.
        assertTrue(SystemClock.elapsedRealtime() > 0)
    }
}
