package net.codekunde.fernplay.android.playback

import net.codekunde.fernplay.android.library.SongList

/** The words and times the player screens show. No Android types, so it is unit tested. */
object NowPlayingText {

    fun elapsed(positionMs: Long): String = SongList.duration(positionMs)

    /** "-1:48". Rounded up, so elapsed plus remaining adds up to the length. */
    fun remaining(positionMs: Long, durationMs: Long): String {
        val left = (durationMs - positionMs).coerceAtLeast(0)
        return "-" + SongList.duration((left + 999) / 1000 * 1000)
    }

    /** "7 / 24" on the display. */
    fun trackOf(index: Int, count: Int): String = "${index + 1} / $count"

    /** "Track 7 of 24 · 1:12 in" on Home's Continue card. */
    fun continueLine(index: Int, count: Int, positionMs: Long): String =
        "Track ${index + 1} of $count · ${SongList.duration(positionMs)} in"

    /** "THE SPORE PRINTS 1:12/3:00" under the title in the mini player. */
    fun miniLine(artist: String, positionMs: Long, durationMs: Long): String =
        "${artist.uppercase()} ${SongList.duration(positionMs)}/${SongList.duration(durationMs)}"

    /** The LCD's right corner while a station plays: "LIVE", "RECONNECTING…". */
    fun liveLabel(status: LiveStatus): String = when (status) {
        LiveStatus.None, LiveStatus.Live -> "LIVE"
        LiveStatus.Connecting -> "CONNECTING…"
        LiveStatus.Reconnecting -> "RECONNECTING…"
        LiveStatus.Unreachable -> "UNREACHABLE"
    }

    /** What TalkBack reads for that corner. */
    fun liveDescription(status: LiveStatus): String = when (status) {
        LiveStatus.None, LiveStatus.Live -> "Live"
        LiveStatus.Connecting -> "Connecting to the station"
        LiveStatus.Reconnecting -> "Connection lost, reconnecting"
        LiveStatus.Unreachable -> "Station unreachable. Press Play to try again."
    }

    /**
     * The line under a station's title: "THE SPORE PRINTS · RN1" while a song with
     * an artist plays, else just the station.
     */
    fun stationLine(artist: String, station: String): String =
        if (artist.isBlank() || artist == station) station.uppercase() else "${artist.uppercase()} · ${station.uppercase()}"

    /** "RN1 LIVE" in the mini player. */
    fun miniStationLine(artist: String, station: String, status: LiveStatus): String =
        "${stationLine(artist, station)} ${liveLabel(status)}"

    /** "Station 2 of 4" on Home's Continue card. */
    fun continueStationLine(index: Int, count: Int): String = "Station ${index + 1} of $count"

    fun repeatLabel(state: RepeatState): String = "REPEAT ${state.label.uppercase()}"

    /** What TalkBack reads for the Repeat button: the state and what a tap does. */
    fun repeatDescription(state: RepeatState): String =
        "Repeat: ${state.label.lowercase()}. Tap for ${state.next.label.lowercase()}."
}
