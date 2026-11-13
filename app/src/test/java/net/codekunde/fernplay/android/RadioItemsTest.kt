package net.codekunde.fernplay.android

import androidx.media3.common.MediaMetadata
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.codekunde.fernplay.android.data.StationEntity
import net.codekunde.fernplay.android.playback.LiveTitle
import net.codekunde.fernplay.android.playback.MediaItems
import net.codekunde.fernplay.android.playback.ResumePosition
import net.codekunde.fernplay.android.playback.ResumeQueue
import net.codekunde.fernplay.android.playback.ResumeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Stations as player items: the name stays put while the live song changes, and resume starts the station afresh. */
@RunWith(AndroidJUnit4::class)
class RadioItemsTest {

    private val station = StationEntity(id = 7, name = "Test FM", url = "http://radio.test/live", position = 0, nameGuessed = false, addedAt = 0)

    @Test
    fun aStationItemCarriesItsNameAndAddress() {
        val item = MediaItems.of(station)
        assertEquals("station:7", item.mediaId)
        assertEquals("http://radio.test/live", item.localConfiguration!!.uri.toString())
        assertEquals("Test FM", item.mediaMetadata.title)
        assertEquals("Test FM", MediaItems.stationName(item))
        assertEquals(MediaMetadata.MEDIA_TYPE_RADIO_STATION, item.mediaMetadata.mediaType)
        assertNull(item.mediaMetadata.durationMs)
    }

    @Test
    fun theLiveSongIsNotSavedForResume() {
        val playing = MediaItems.withLiveTitle(MediaItems.of(station), LiveTitle("Night Glass", "Lumen"))
        assertEquals("Night Glass", playing.mediaMetadata.title)
        assertEquals("Lumen", playing.mediaMetadata.artist)
        assertEquals("Test FM", MediaItems.stationName(playing))

        val saved = MediaItems.toResume(playing)
        assertEquals("Test FM", saved.title)
        assertEquals("", saved.artist)
        assertEquals(0L, saved.durationMs)

        val back = MediaItems.of(saved)
        assertEquals("station:7", back.mediaId)
        assertEquals("Test FM", back.mediaMetadata.title)
        assertEquals(MediaMetadata.MEDIA_TYPE_RADIO_STATION, back.mediaMetadata.mediaType)
    }

    @Test
    fun aRenameFollowsIntoTheTitleOnlyWhenTheTitleWasTheName() {
        val idle = MediaItems.renamed(MediaItems.of(station), "Real Name")
        assertEquals("Real Name", idle.mediaMetadata.title)
        assertEquals("Real Name", MediaItems.stationName(idle))
        val live = MediaItems.renamed(MediaItems.withLiveTitle(MediaItems.of(station), LiveTitle("Song", "Artist")), "Real Name")
        assertEquals("Song", live.mediaMetadata.title)
        assertEquals("Real Name", MediaItems.stationName(live))
    }

    @Test
    fun aResumedStationStartsLive() {
        val saved = MediaItems.toResume(MediaItems.of(station))
        val state = ResumeState(ResumeQueue("Radio", listOf(saved)), ResumePosition("station:7", 0, 3_600_000, 0))
        assertEquals(0, state.startIndex)
        assertEquals(0L, state.startPositionMs)
    }
}
