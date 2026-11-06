package net.codekunde.fernplay.android

import androidx.media3.common.Player
import net.codekunde.fernplay.android.playback.RepeatState
import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatStateTest {

    @Test
    fun `one tap cycles off, list, track, off`() {
        assertEquals(RepeatState.List, RepeatState.Off.next)
        assertEquals(RepeatState.Track, RepeatState.List.next)
        assertEquals(RepeatState.Off, RepeatState.Track.next)
    }

    @Test
    fun `states map to the player's repeat modes`() {
        assertEquals(RepeatState.Off, RepeatState.of(Player.REPEAT_MODE_OFF))
        assertEquals(RepeatState.List, RepeatState.of(Player.REPEAT_MODE_ALL))
        assertEquals(RepeatState.Track, RepeatState.of(Player.REPEAT_MODE_ONE))
        assertEquals(RepeatState.Off, RepeatState.of(42))
    }
}
