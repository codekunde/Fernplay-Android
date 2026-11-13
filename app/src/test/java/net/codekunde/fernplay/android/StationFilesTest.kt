package net.codekunde.fernplay.android

import net.codekunde.fernplay.android.data.ParsedStation
import net.codekunde.fernplay.android.data.StationFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The .m3u / .m3u8 reader. Made-up stations only: the repo is public. */
class StationFilesTest {

    @Test
    fun readsNamedAndUnnamedStreamsInOrder() {
        val text = """
            #EXTM3U
            #EXTINF:0,Fern FM
            https://radio.example.org/fern

            http://stream.example.net:8000/live.mp3
            #EXTINF:-1,Second Name
            HTTPS://Radio.Example.org/second
        """.trimIndent()
        assertEquals(
            listOf(
                ParsedStation("Fern FM", "https://radio.example.org/fern"),
                ParsedStation(null, "http://stream.example.net:8000/live.mp3"),
                ParsedStation("Second Name", "HTTPS://Radio.Example.org/second"),
            ),
            StationFiles.parse(text),
        )
    }

    @Test
    fun ignoresLocalPathsOtherSchemesAndComments() {
        val text = """
            #EXTM3U
            #EXTINF:200,Artist - A song on the phone
            Music/Album/01 Song.mp3
            C:\Music\song.flac
            file:///sdcard/Music/song.mp3
            rtsp://radio.example.org/live
            #EXTVLCOPT:network-caching=1000
            https://radio.example.org/ok
        """.trimIndent()
        assertEquals(listOf(ParsedStation(null, "https://radio.example.org/ok")), StationFiles.parse(text))
    }

    @Test
    fun aNameOnlyBelongsToTheEntryRightAfterIt() {
        val text = "#EXTINF:0,Song name\nMusic/song.mp3\nhttps://radio.example.org/a"
        assertEquals(listOf(ParsedStation(null, "https://radio.example.org/a")), StationFiles.parse(text))
    }

    @Test
    fun theSameAddressTwiceCountsOnce() {
        val text = "https://radio.example.org/a\n#EXTINF:0,Again\nHTTPS://RADIO.EXAMPLE.ORG/A\nhttps://radio.example.org/b"
        assertEquals(listOf("https://radio.example.org/a", "https://radio.example.org/b"), StationFiles.parse(text).map { it.url })
    }

    @Test
    fun windowsLineEndingsAndSpacesAround() {
        val text = "#EXTM3U\r\n#EXTINF:0,  Fern   FM  \r\n  https://radio.example.org/fern  \r\n"
        assertEquals(listOf(ParsedStation("Fern FM", "https://radio.example.org/fern")), StationFiles.parse(text))
    }

    @Test
    fun stopsAtTheLimit() {
        val text = (1..StationFiles.MAX_STATIONS + 20).joinToString("\n") { "https://radio.example.org/$it" }
        assertEquals(StationFiles.MAX_STATIONS, StationFiles.parse(text).size)
    }

    @Test
    fun extinfNameIsTheTextAfterTheFirstCommaOutsideQuotes() {
        assertEquals("Fern FM", StationFiles.extinfName("#EXTINF:-1 tvg-logo=\"https://x.example/a,b.png\",Fern FM"))
        assertEquals("Rock, Pop", StationFiles.extinfName("#EXTINF:0,Rock, Pop"))
        assertNull(StationFiles.extinfName("#EXTINF:0,"))
        assertNull(StationFiles.extinfName("#EXTINF:0"))
    }

    @Test
    fun streamUrlsNeedHttpOrHttpsAndAHost() {
        assertTrue(StationFiles.isStreamUrl("http://radio.example.org:8000/live"))
        assertTrue(StationFiles.isStreamUrl("https://radio.example.org/live?type=.mp3&x=1"))
        assertFalse(StationFiles.isStreamUrl("https:///nohost"))
        assertFalse(StationFiles.isStreamUrl("https://radio.example.org/with space"))
        assertFalse(StationFiles.isStreamUrl("ftp://radio.example.org/a"))
        assertFalse(StationFiles.isStreamUrl("radio.example.org/a"))
    }

    @Test
    fun m3u8IsUtf8AndTheBomIsDropped() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "#EXTINF:0,Grüße FM\n".toByteArray(Charsets.UTF_8)
        assertEquals("#EXTINF:0,Grüße FM\n", StationFiles.decode(bytes, "list.m3u8"))
    }

    @Test
    fun m3uIsUtf8WhenItDecodesElseWindows1252() {
        assertEquals("Grüße", StationFiles.decode("Grüße".toByteArray(Charsets.UTF_8), "a.m3u"))
        assertEquals("Grüße", StationFiles.decode("Grüße".toByteArray(charset("windows-1252")), "a.M3U"))
    }

    @Test
    fun onlyM3uAndM3u8FilesAreStationFiles() {
        assertTrue(StationFiles.isStationFile("Fern FM.m3u"))
        assertTrue(StationFiles.isStationFile("LIST.M3U8"))
        assertFalse(StationFiles.isStationFile("list.pls"))
        assertFalse(StationFiles.isStationFile("list.m3u.txt"))
    }

    @Test
    fun guessedNameIsTheFileForOneStationElseTheServer() {
        assertEquals("fern fm_live", StationFiles.guessName("http://radio.example.org/a", "fern fm_live.m3u", 1))
        assertEquals("radio.example.org", StationFiles.guessName("http://www.radio.example.org/a", "many.m3u", 3))
        assertEquals("radio.example.org", StationFiles.guessName("http://radio.example.org/a", ".m3u", 1))
    }

    @Test
    fun namesAreTrimmedAndCut() {
        assertEquals("A B", StationFiles.clean("  A \t B "))
        assertEquals(StationFiles.MAX_NAME, StationFiles.clean("x".repeat(200))!!.length)
        assertNull(StationFiles.clean("   "))
    }
}
