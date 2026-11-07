package net.codekunde.fernplay.android

import net.codekunde.fernplay.android.data.PlaylistRules
import net.codekunde.fernplay.android.library.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistRulesTest {

    private fun song(id: Long, file: String = "song$id.mp3", folder: String = "Music", size: Long = 1000 + id, title: String = "Song $id") =
        Song(id, "content://media/external/audio/media/$id", title, "Artist", "Album", 180_000, folder, file, size, 0)

    @Test
    fun songsAreFoundByIdThenPathThenNameAndSize() {
        val a = song(1)
        val b = song(2)
        val c = song(3)
        val entries = listOf(a, b, c).map(PlaylistRules::entryOf)
        // a: same id. b: re-indexed with a new id, same place. c: moved to another folder with a new id.
        val now = listOf(a, b.copy(id = 22), c.copy(id = 33, folder = "Download/Albums"))
        val items = PlaylistRules.resolve(entries, now)
        assertEquals(listOf(1L, 22L, 33L), items.map { it.song?.id })
        assertTrue(items.none { it.missing })
    }

    @Test
    fun anIdTakenByAnotherFileDoesNotCount() {
        val entries = listOf(PlaylistRules.entryOf(song(1, file = "old.mp3")))
        val items = PlaylistRules.resolve(entries, listOf(song(1, file = "new.mp3")))
        assertTrue(items.single().missing)
    }

    @Test
    fun twoFilesWithTheSameNameAndSizeAreNotGuessed() {
        val entries = listOf(PlaylistRules.entryOf(song(1, file = "x.mp3", size = 5)))
        val now = listOf(song(7, file = "x.mp3", size = 5, folder = "A"), song(8, file = "x.mp3", size = 5, folder = "B"))
        assertTrue(PlaylistRules.resolve(entries, now).single().missing)
    }

    @Test
    fun keysStayWhenTheListIsReordered() {
        val entries = listOf(song(1), song(2), song(3)).map(PlaylistRules::entryOf)
        val keys = PlaylistRules.resolve(entries, emptyList()).map { it.key }
        val moved = PlaylistRules.resolve(PlaylistRules.move(entries, 0, 2), emptyList()).map { it.key }
        assertEquals(listOf(keys[1], keys[2], keys[0]), moved)
        assertEquals(3, keys.toSet().size)
    }

    @Test
    fun moveAndRenumber() {
        assertEquals(listOf("b", "c", "a"), PlaylistRules.move(listOf("a", "b", "c"), 0, 2))
        assertEquals(listOf("c", "a", "b"), PlaylistRules.move(listOf("a", "b", "c"), 2, 0))
        assertEquals(listOf("a", "b"), PlaylistRules.move(listOf("a", "b"), 0, 5))
        val renumbered = PlaylistRules.renumber(listOf(song(5), song(6)).map(PlaylistRules::entryOf), 9)
        assertEquals(listOf(9L to 0, 9L to 1), renumbered.map { it.playlistId to it.position })
    }

    @Test
    fun duplicatesAreSkipped() {
        val existing = listOf(PlaylistRules.entryOf(song(1)))
        val (add, skip) = PlaylistRules.withoutDuplicates(existing, listOf(song(1), song(2), song(2), song(3)))
        assertEquals(listOf(2L, 3L), add.map { it.id })
        assertEquals(listOf(1L, 2L), skip.map { it.id })
    }

    @Test
    fun names() {
        assertNull(PlaylistRules.nameProblem("Gym Push", listOf("Late Drive")))
        assertEquals("Type a name", PlaylistRules.nameProblem("   ", emptyList()))
        assertEquals("There is already a playlist called “gym push”", PlaylistRules.nameProblem(" gym push ", listOf("Gym Push")))
        assertEquals("There is already a playlist called “all songs”", PlaylistRules.nameProblem("all songs", emptyList()))
        assertEquals("Keep it under 60 characters", PlaylistRules.nameProblem("x".repeat(61), emptyList()))
    }

    @Test
    fun textsAsInTheDesign() {
        assertEquals("24 songs · 1 h 38 min", PlaylistRules.summary(24, (98 * 60_000).toLong()))
        assertEquals("12 songs · 44 min", PlaylistRules.summary(12, 44 * 60_000L))
        assertEquals("1 song · 0:45", PlaylistRules.summary(1, 45_000))
        assertEquals("2 songs · 2 h", PlaylistRules.summary(2, 120 * 60_000L))
        assertEquals("0 songs", PlaylistRules.summary(0, 0))
        assertEquals("12 songs · Frond already in", PlaylistRules.sheetLine(12, listOf("Frond")))
        assertEquals("41 songs · 2 already in", PlaylistRules.sheetLine(41, listOf("a", "b")))
        assertEquals("Added 3 songs to Gym Push", PlaylistRules.addedMessage(3, 0, "Gym Push"))
        assertEquals("Added 2 songs to Gym Push · 1 already in", PlaylistRules.addedMessage(2, 1, "Gym Push"))
        assertEquals("That song is already in Gym Push", PlaylistRules.addedMessage(0, 1, "Gym Push"))
    }
}
