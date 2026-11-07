package net.codekunde.fernplay.android

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.codekunde.fernplay.android.playback.MediaItems
import net.codekunde.fernplay.android.playback.QueueEdits
import net.codekunde.fernplay.android.playback.ResumeItem
import net.codekunde.fernplay.android.playback.ResumePosition
import net.codekunde.fernplay.android.playback.ResumeQueue
import net.codekunde.fernplay.android.playback.ResumeState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The queue rules on a real ExoPlayer (nothing is prepared or played). */
@RunWith(AndroidJUnit4::class)
class QueueEditsTest {

    private lateinit var p: ExoPlayer

    @Before
    fun open() {
        p = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
    }

    @After
    fun close() = p.release()

    private fun items(vararg ids: Long) = ids.map { MediaItems.of(testSong(it)) }

    private fun queue(): List<String> = (0 until p.mediaItemCount).map { p.getMediaItemAt(it).mediaId }

    private fun name() = p.playlistMetadata.title?.toString()

    @Test
    fun replaceSetsTheQueueNameAndStartTurnsShuffleOff() {
        p.shuffleModeEnabled = true
        QueueEdits.replace(p, items(1, 2, 3), 9, "Gym Push")
        assertEquals(listOf("1", "2", "3"), queue())
        assertEquals(2, p.currentMediaItemIndex) // clamped to the last song
        assertEquals("Gym Push", name())
        assertFalse(p.shuffleModeEnabled)

        QueueEdits.replace(p, emptyList(), 0, "Nothing")
        assertEquals("Gym Push", name()) // an empty list changes nothing
    }

    @Test
    fun playNextGoesRightAfterThePlayingSongInOrder() {
        assertFalse(QueueEdits.playNext(p, items(9))) // nothing queued: the caller plays them instead
        assertEquals(0, p.mediaItemCount)

        QueueEdits.replace(p, items(1, 2, 3), 1, "All songs")
        assertTrue(QueueEdits.playNext(p, items(8, 9)))
        assertEquals(listOf("1", "2", "8", "9", "3"), queue())
        assertEquals(1, p.currentMediaItemIndex)
        assertEquals("All songs", name()) // the queue keeps its name
    }

    @Test
    fun thePlayingSongCannotBeRemoved() {
        QueueEdits.replace(p, items(1, 2, 3), 1, "A")
        assertNull(QueueEdits.remove(p, 1))
        assertNull(QueueEdits.remove(p, -1))
        assertNull(QueueEdits.remove(p, 3))
        assertEquals(listOf("1", "2", "3"), queue())
    }

    @Test
    fun removeAndUndo() {
        QueueEdits.replace(p, items(1, 2, 3, 4), 1, "A")
        val removed = QueueEdits.remove(p, 0)!!
        assertEquals(listOf("2", "3", "4"), queue())
        assertEquals("2", p.currentMediaItem?.mediaId) // still the same song playing
        QueueEdits.undoRemove(p, removed)
        assertEquals(listOf("1", "2", "3", "4"), queue())
        assertEquals("2", p.currentMediaItem?.mediaId)
    }

    @Test
    fun undoAfterTheQueueShrankPutsTheSongAtTheEnd() {
        QueueEdits.replace(p, items(1, 2, 3, 4), 0, "A")
        val removed = QueueEdits.remove(p, 3)!!
        QueueEdits.remove(p, 2)
        QueueEdits.undoRemove(p, removed)
        assertEquals(listOf("1", "2", "4"), queue())
    }

    @Test
    fun repeatCyclesOffListTrackOnThePlayer() {
        assertEquals(Player.REPEAT_MODE_OFF, p.repeatMode)
        QueueEdits.cycleRepeat(p)
        assertEquals(Player.REPEAT_MODE_ALL, p.repeatMode)
        QueueEdits.cycleRepeat(p)
        assertEquals(Player.REPEAT_MODE_ONE, p.repeatMode)
        QueueEdits.cycleRepeat(p)
        assertEquals(Player.REPEAT_MODE_OFF, p.repeatMode)
    }

    @Test
    fun renamingAPlaylistRenamesOnlyItsOwnQueue() {
        QueueEdits.replace(p, items(1), 0, "Gym")
        QueueEdits.renameQueue(p, "Drive", "Night Drive")
        assertEquals("Gym", name())
        QueueEdits.renameQueue(p, "Gym", "Gym Push")
        assertEquals("Gym Push", name())
    }

    @Test
    fun restoreFindsTheSongWhereverItMovedAndKeepsRepeat() {
        fun item(id: Long) = ResumeItem("$id", "content://media/external/audio/media/$id", "Song $id", "Artist", "Album", 180_000)
        val state = ResumeState(
            ResumeQueue("Late Drive", listOf(item(5), item(6), item(7))),
            ResumePosition(mediaId = "7", index = 0, positionMs = 42_000, repeatMode = Player.REPEAT_MODE_ONE),
        )
        QueueEdits.restore(p, state)
        assertEquals(listOf("5", "6", "7"), queue())
        assertEquals(2, p.currentMediaItemIndex)
        assertEquals(42_000L, p.currentPosition)
        assertEquals(Player.REPEAT_MODE_ONE, p.repeatMode)
        assertEquals("Late Drive", name())
        assertFalse(p.playWhenReady) // loaded, not started
    }

    @Test
    fun brokenSongsAreSkippedAtMostOnceRoundTheQueue() {
        QueueEdits.replace(p, items(1, 2, 3), 0, "A")
        assertTrue(QueueEdits.skipAfterError(1, p))
        p.seekToDefaultPosition(2)
        assertFalse(QueueEdits.skipAfterError(1, p)) // last song, repeat off: stop

        p.repeatMode = Player.REPEAT_MODE_ALL // the queue wraps round
        assertTrue(QueueEdits.skipAfterError(2, p))
        assertFalse(QueueEdits.skipAfterError(3, p)) // every song failed once: stop
    }
}
