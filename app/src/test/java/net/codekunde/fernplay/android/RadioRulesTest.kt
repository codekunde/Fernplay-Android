package net.codekunde.fernplay.android

import androidx.media3.common.PlaybackException
import net.codekunde.fernplay.android.playback.Handover
import net.codekunde.fernplay.android.playback.LiveStatus
import net.codekunde.fernplay.android.playback.LiveTitle
import net.codekunde.fernplay.android.playback.LiveTitles
import net.codekunde.fernplay.android.playback.NetworkKind
import net.codekunde.fernplay.android.playback.NowPlayingText
import net.codekunde.fernplay.android.playback.RadioRules
import net.codekunde.fernplay.android.playback.ReconnectBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioRulesTest {

    @Test
    fun stationIdsAreTellableFromSongIds() {
        assertEquals("station:12", RadioRules.mediaId(12))
        assertTrue(RadioRules.isStation("station:12"))
        assertFalse(RadioRules.isStation("12"))
        assertFalse(RadioRules.isStation(null))
        assertEquals(12L, RadioRules.stationId("station:12"))
        assertNull(RadioRules.stationId("12"))
        assertNull(RadioRules.stationId("station:x"))
    }

    @Test
    fun artistAndTitleAreSplit() {
        assertEquals(LiveTitle("Night Glass (Original Mix)", "Lumen"), LiveTitles.of("Lumen - Night Glass (Original Mix)", "Lounge"))
        // Only the first " - " splits: the rest belongs to the title.
        assertEquals(LiveTitle("Song - Radio Edit", "Artist"), LiveTitles.of("Artist - Song - Radio Edit", "Station"))
        // A hyphen without spaces is part of a name.
        assertEquals(LiveTitle("Jay-Z", "Station"), LiveTitles.of("Jay-Z", "Station"))
    }

    @Test
    fun noTitleShowsTheStation() {
        assertEquals(LiveTitle("rn1", ""), LiveTitles.of(null, "rn1"))
        assertEquals(LiveTitle("rn1", ""), LiveTitles.of("   ", "rn1"))
        assertEquals(LiveTitle("rn1", ""), LiveTitles.of(" - ", "rn1"))
    }

    @Test
    fun programmeItemsStayWholeUnderTheStation() {
        assertEquals(LiveTitle("*** News", "Radio Nord 98,1"), LiveTitles.of("*** News", "Radio Nord 98,1"))
        assertEquals(
            LiveTitle("Radio Nord 98,1 - Wir sind die Stadt", "Radio Nord 98.1"),
            LiveTitles.of("Radio Nord 98,1 - Wir sind die Stadt", "Radio Nord 98.1"),
        )
        // The station's own icy-name counts too, and a short form of it.
        assertEquals(
            LiveTitle("rn1 - Der Morgen", "My rn1"),
            LiveTitles.of("rn1 - Der Morgen", "My rn1", icyName = "rn1 Stadt-Land"),
        )
        assertEquals(LiveTitle(" - Song".trim(), "Station"), LiveTitles.of(" - Song", "Station"))
    }

    @Test
    fun titlesAreCleaned() {
        assertEquals("Grüße aus Köln", LiveTitles.clean("  Grüße\u0000 aus\t\tKöln "))
        assertEquals(200, LiveTitles.clean("x".repeat(500))!!.length)
        assertNull(LiveTitles.clean("-"))
    }

    @Test
    fun threeReconnectsAMinuteWithLongerWaits() {
        val b = ReconnectBudget()
        assertTrue(b.take(0)); assertEquals(1000, b.delayMs())
        assertTrue(b.take(5_000)); assertEquals(2000, b.delayMs())
        assertTrue(b.take(10_000)); assertEquals(4000, b.delayMs())
        assertFalse(b.take(20_000))
        assertFalse(b.take(59_999))
        // It played meanwhile and the first attempt left the minute: one more is allowed.
        b.played()
        assertTrue(b.take(60_000))
        assertFalse(b.take(60_001))
        b.reset()
        assertTrue(b.take(60_002))
    }

    @Test
    fun aDeadStationStopsAfterThreeAttemptsInARow() {
        // Media3 spends about 30 s on each attempt, so the minute never fills up.
        val b = ReconnectBudget()
        assertTrue(b.take(0))
        assertTrue(b.take(35_000))
        assertTrue(b.take(70_000))
        assertFalse(b.take(105_000))
        // A sound in between starts the count over.
        val c = ReconnectBudget()
        assertTrue(c.take(0)); c.played()
        assertTrue(c.take(35_000)); c.played()
        assertTrue(c.take(70_000)); c.played()
        assertTrue(c.take(105_000))
    }

    @Test
    fun onlyNetworkErrorsAreRetried() {
        assertTrue(RadioRules.retries(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED))
        assertTrue(RadioRules.retries(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
        assertFalse(RadioRules.retries(PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED))
        assertFalse(RadioRules.retries(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertFalse(RadioRules.retries(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED))
    }

    @Test
    fun onlyMobileDataToWifiMovesTheStream() {
        assertTrue(Handover.shouldReconnect(NetworkKind.Cellular, NetworkKind.Wifi))
        assertFalse(Handover.shouldReconnect(NetworkKind.Wifi, NetworkKind.Cellular))
        assertFalse(Handover.shouldReconnect(NetworkKind.Wifi, NetworkKind.Wifi))
        assertFalse(Handover.shouldReconnect(NetworkKind.None, NetworkKind.Wifi)) // first report at start
        assertFalse(Handover.shouldReconnect(NetworkKind.Other, NetworkKind.Wifi))
    }

    @Test
    fun stationLines() {
        assertEquals("RN1", NowPlayingText.stationLine("", "rn1"))
        assertEquals("RN1", NowPlayingText.stationLine("rn1", "rn1"))
        assertEquals("LUMEN · LOUNGE", NowPlayingText.stationLine("Lumen", "Lounge"))
        assertEquals("RN1 RECONNECTING…", NowPlayingText.miniStationLine("", "rn1", LiveStatus.Reconnecting))
        assertEquals("LIVE", NowPlayingText.liveLabel(LiveStatus.Live))
        assertEquals("UNREACHABLE", NowPlayingText.liveLabel(LiveStatus.Unreachable))
        assertEquals("Station 2 of 4", NowPlayingText.continueStationLine(1, 4))
    }
}
