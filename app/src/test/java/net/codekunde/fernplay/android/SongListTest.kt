package net.codekunde.fernplay.android

import net.codekunde.fernplay.android.library.Song
import net.codekunde.fernplay.android.library.SongList
import net.codekunde.fernplay.android.library.SongSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongListTest {

    private fun song(id: Long, title: String, artist: String = "Artist", album: String = "Album", added: Long = 0) =
        Song(id, "content://media/external/audio/media/$id", title, artist, album, 180_000, "Music", "$title.mp3", 1, added)

    private val songs = listOf(
        song(1, "Moss Lantern", "The Spore Prints", "Understory", added = 30),
        song(2, "bracken", "Low Canopy", "Drip", added = 10),
        song(3, "Éclair", "Canopy Club", "Green Hour", added = 20),
        song(4, "99 Ferns", "Mycelia", "Hyphae", added = 40),
        song(5, "The Night Bus", "Fern Gully Radio", "Night", added = 5),
    )

    @Test
    fun `title sort ignores case, accents and a leading The`() {
        val titles = SongList.sorted(songs, SongSort.Title).map { it.title }
        assertEquals(listOf("99 Ferns", "bracken", "Éclair", "Moss Lantern", "The Night Bus"), titles)
    }

    @Test
    fun `artist sort uses the artist without The`() {
        val artists = SongList.sorted(songs, SongSort.Artist).map { it.artist }
        assertEquals(listOf("Canopy Club", "Fern Gully Radio", "Low Canopy", "Mycelia", "The Spore Prints"), artists)
    }

    @Test
    fun `newest puts the latest added first`() {
        assertEquals(listOf(4L, 1, 3, 2, 5), SongList.sorted(songs, SongSort.Newest).map { it.id })
    }

    @Test
    fun `search needs every word, in any field, without accents`() {
        assertTrue(SongList.matches(songs[2], "eclair canopy"))
        assertTrue(SongList.matches(songs[0], "  spore  "))
        assertFalse(SongList.matches(songs[0], "spore bracken"))
        assertTrue(SongList.matches(songs[0], ""))
    }

    @Test
    fun `strip letters jump to the next letter with songs`() {
        val sorted = SongList.sorted(songs, SongSort.Title)
        val pos = SongList.letterPositions(sorted, SongSort.Title)
        assertEquals(0, pos['#']) // 99 Ferns
        assertEquals(1, pos['A']) // no A: next is B
        assertEquals(1, pos['B'])
        assertEquals(2, pos['E'])
        assertEquals(3, pos['F']) // F..M jump to Moss Lantern
        assertEquals(4, pos['N']) // Night Bus (The dropped)
        assertEquals(4, pos['Z']) // past the end: last song
    }

    @Test
    fun `no strip for newest or an empty list`() {
        assertTrue(SongList.letterPositions(songs, SongSort.Newest).isEmpty())
        assertTrue(SongList.letterPositions(emptyList(), SongSort.Title).isEmpty())
    }

    @Test
    fun `durations and totals`() {
        assertEquals("3:00", SongList.duration(180_000))
        assertEquals("1:02:03", SongList.duration(3_723_000))
        assertEquals("0:00", SongList.duration(-5))
        assertEquals("87 h", SongList.totalLength(87L * 3_600_000 + 59 * 60_000))
        assertEquals("44 min", SongList.totalLength(44L * 60_000))
    }
}
