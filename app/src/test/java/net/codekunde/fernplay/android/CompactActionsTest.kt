package net.codekunde.fernplay.android

import androidx.media3.common.Player
import net.codekunde.fernplay.android.playback.FernplayNotificationProvider
import net.codekunde.fernplay.android.playback.PlaybackService
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CompactActionsTest {

    @Test
    fun `small notification shows repeat, play-pause, next in that order`() {
        // Media3's usual order: previous, play/pause, next, then Fernplay's own buttons.
        val buttons = listOf(
            null to Player.COMMAND_SEEK_TO_PREVIOUS,
            null to Player.COMMAND_PLAY_PAUSE,
            null to Player.COMMAND_SEEK_TO_NEXT,
            PlaybackService.ACTION_CYCLE_REPEAT to Player.COMMAND_INVALID,
        )
        assertArrayEquals(intArrayOf(3, 1, 2), FernplayNotificationProvider.compactIndices(buttons))
    }

    @Test
    fun `missing buttons are left out`() {
        val buttons = listOf(null to Player.COMMAND_PLAY_PAUSE)
        assertArrayEquals(intArrayOf(0), FernplayNotificationProvider.compactIndices(buttons))
    }
}
