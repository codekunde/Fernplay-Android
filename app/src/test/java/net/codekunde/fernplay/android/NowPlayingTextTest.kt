package net.codekunde.fernplay.android

import net.codekunde.fernplay.android.playback.NowPlayingText
import net.codekunde.fernplay.android.playback.RepeatState
import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingTextTest {

    @Test
    fun elapsedAndRemainingAddUpToTheLength() {
        assertEquals("1:12", NowPlayingText.elapsed(72_400))
        assertEquals("-1:48", NowPlayingText.remaining(72_400, 180_000))
        assertEquals("-3:00", NowPlayingText.remaining(0, 180_000))
        assertEquals("-0:00", NowPlayingText.remaining(181_000, 180_000))
    }

    @Test
    fun linesAsInTheDesign() {
        assertEquals("7 / 24", NowPlayingText.trackOf(6, 24))
        assertEquals("Track 7 of 24 · 1:12 in", NowPlayingText.continueLine(6, 24, 72_000))
        assertEquals("THE SPORE PRINTS 1:12/3:00", NowPlayingText.miniLine("The Spore Prints", 72_000, 180_000))
    }

    @Test
    fun repeatButtonSaysStateAndNextStep() {
        assertEquals("REPEAT OFF", NowPlayingText.repeatLabel(RepeatState.Off))
        assertEquals("REPEAT TRACK", NowPlayingText.repeatLabel(RepeatState.Track))
        assertEquals("Repeat: list. Tap for track.", NowPlayingText.repeatDescription(RepeatState.List))
        assertEquals("Repeat: track. Tap for off.", NowPlayingText.repeatDescription(RepeatState.Track))
    }
}
