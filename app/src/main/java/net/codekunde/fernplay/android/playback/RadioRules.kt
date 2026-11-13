package net.codekunde.fernplay.android.playback

import androidx.media3.common.PlaybackException

/**
 * The radio rules the service and the screens share. No Android types beyond
 * error codes, so they are unit tested.
 */
object RadioRules {

    private const val PREFIX = "station:"

    /** A station's id in the player queue: "station:12". Songs use their MediaStore number. */
    fun mediaId(stationId: Long): String = "$PREFIX$stationId"

    fun isStation(mediaId: String?): Boolean = mediaId?.startsWith(PREFIX) == true

    /** The station's row id, or null when [mediaId] is a song. */
    fun stationId(mediaId: String?): Long? =
        if (isStation(mediaId)) mediaId!!.removePrefix(PREFIX).toLongOrNull() else null

    /**
     * Errors worth connecting again for: the network dropped or timed out. A
     * wrong address, a refused http station, a file that is not audio: no.
     */
    fun retries(errorCode: Int): Boolean = errorCode in RETRY_CODES

    private val RETRY_CODES = setOf(
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
        PlaybackException.ERROR_CODE_TIMEOUT,
    )
}

/** What the screens, notification and car show while a station plays. */
data class LiveTitle(val title: String, val artist: String)

object LiveTitles {

    private const val MAX = 200

    /**
     * Splits a station's "Artist - Title" into song and artist. No title yet:
     * the station's name. A programme item that starts with the station's own
     * name ("Radio Nord 98,1 - Wir sind die Stadt") or has no " - " ("*** News")
     * stays whole, with the station as its second line.
     */
    fun of(streamTitle: String?, station: String, icyName: String? = null): LiveTitle {
        val raw = clean(streamTitle) ?: return LiveTitle(station, "")
        val cut = raw.indexOf(" - ")
        if (cut <= 0) return LiveTitle(raw, station)
        val left = raw.substring(0, cut).trim()
        val right = raw.substring(cut + 3).trim()
        if (left.isEmpty() || right.isEmpty()) return LiveTitle(raw, station)
        if (sameName(left, station) || (icyName != null && sameName(left, icyName))) return LiveTitle(raw, station)
        return LiveTitle(right, left)
    }

    /** Control characters out, spaces collapsed, at most 200 characters; empty or "-" is no title. */
    fun clean(text: String?): String? {
        if (text == null) return null
        // Tabs and line breaks become spaces first; other control characters go.
        val t = text.replace(Regex("\\s+"), " ").filter { !it.isISOControl() }.trim().take(MAX)
        return t.takeIf { it.isNotEmpty() && it != "-" }
    }

    /** "rn1" and "rn1 Stadt-Land", "Radio Nord 98,1" and "Radio Nord 98.1": one is the start of the other, letters and digits only. */
    internal fun sameName(a: String, b: String): Boolean {
        val x = key(a)
        val y = key(b)
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        val (short, long) = if (x.length <= y.length) x to y else y to x
        return short.length >= 3 && long.startsWith(short)
    }

    private fun key(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
}

/**
 * Reconnects after the station's stream broke: at most [max] in [windowMs]
 * (the desktop's rule, 3 a minute), each after a longer wait (1, 2, 4 s), and
 * at most [max] in a row without a sound in between. Media3 itself retries for
 * about 30 s before it reports an error, so without the second limit a dead
 * station would be tried for ever.
 */
class ReconnectBudget(private val max: Int = 3, private val windowMs: Long = 60_000) {
    private val times = ArrayDeque<Long>()
    private var sincePlayed = 0

    /** True and counted when another attempt is allowed at [now]. */
    fun take(now: Long): Boolean {
        while (times.isNotEmpty() && now - times.first() >= windowMs) times.removeFirst()
        if (times.size >= max || sincePlayed >= max) return false
        times.addLast(now)
        sincePlayed++
        return true
    }

    /** The wait before the attempt just taken. */
    fun delayMs(): Long = 1000L shl (times.size - 1).coerceIn(0, 4)

    /** The station plays again: the attempts in a row start over (the minute's count stays). */
    fun played() {
        sincePlayed = 0
    }

    fun reset() {
        times.clear()
        sincePlayed = 0
    }
}

/** The phone's default network, as far as a station handover cares. */
enum class NetworkKind { Wifi, Cellular, Other, None }

object Handover {
    /**
     * True when a station playing over mobile data should reconnect: the default
     * network just became (validated) Wi-Fi. Wi-Fi to mobile data needs nothing:
     * the broken stream reconnects on its own.
     */
    fun shouldReconnect(before: NetworkKind, now: NetworkKind): Boolean =
        before == NetworkKind.Cellular && now == NetworkKind.Wifi
}
