package net.codekunde.fernplay.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.codekunde.fernplay.android.data.FernplayDatabase
import net.codekunde.fernplay.android.data.PlaylistDao
import net.codekunde.fernplay.android.data.PlaylistEntity
import net.codekunde.fernplay.android.data.PlaylistRules
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistDaoTest {

    private lateinit var db: FernplayDatabase
    private lateinit var dao: PlaylistDao

    @Before
    fun open() {
        db = testDatabase()
        dao = db.playlists()
    }

    @After
    fun close() = db.close()

    private suspend fun playlist(name: String) = dao.insertPlaylist(PlaylistEntity(name = name, createdAt = 0))

    private fun entries(vararg ids: Long, durationMs: Long = 60_000) =
        ids.map { PlaylistRules.entryOf(testSong(it, durationMs = durationMs)) }

    @Test
    fun summariesCountSongsAndLengthSortedByNameIgnoringCase() = runBlocking {
        val gym = playlist("gym Push")
        playlist("Empty")
        val drive = playlist("Late Drive")
        dao.append(gym, entries(1, 2, 3))
        dao.append(drive, entries(4, durationMs = 200_000))

        val s = dao.summaries().first()
        assertEquals(listOf("Empty", "gym Push", "Late Drive"), s.map { it.name })
        assertEquals(listOf(0, 3, 1), s.map { it.songCount })
        assertEquals(listOf(0L, 180_000L, 200_000L), s.map { it.totalMs })
    }

    @Test
    fun appendCarriesOnAfterTheLastPosition() = runBlocking {
        val p = playlist("A")
        dao.append(p, entries(1, 2))
        dao.append(p, entries(3))
        val now = dao.entriesNow(p)
        assertEquals(listOf(1L, 2L, 3L), now.map { it.mediaId })
        assertEquals(listOf(0, 1, 2), now.map { it.position })
        assertTrue(now.all { it.playlistId == p })
    }

    @Test
    fun replaceEntriesKeepsPositionsWithoutGaps() = runBlocking {
        val p = playlist("A")
        dao.append(p, entries(1, 2, 3, 4))
        val now = dao.entriesNow(p)
        // Remove the second, move the last to the front.
        dao.replaceEntries(p, listOf(now[3], now[0], now[2]))
        val after = dao.entriesNow(p)
        assertEquals(listOf(4L, 1L, 3L), after.map { it.mediaId })
        assertEquals(listOf(0, 1, 2), after.map { it.position })
        assertEquals(3, dao.nextPosition(p))
    }

    @Test
    fun theSameSongCanBeInAPlaylistTwice() = runBlocking {
        val p = playlist("A")
        dao.append(p, entries(1, 1))
        assertEquals(listOf(1L, 1L), dao.entriesNow(p).map { it.mediaId })
    }

    @Test
    fun deletingAPlaylistDeletesItsEntriesOnly() = runBlocking {
        val a = playlist("A")
        val b = playlist("B")
        dao.append(a, entries(1, 2))
        dao.append(b, entries(1))
        dao.delete(a)
        assertEquals(emptyList<Any>(), dao.entriesNow(a))
        assertEquals(listOf(1L), dao.entriesNow(b).map { it.mediaId })
        assertEquals(listOf("B"), dao.names())
        assertEquals(listOf(b), dao.allEntries().first().map { it.playlistId })
    }

    @Test
    fun repointChangesOneEntry() = runBlocking {
        val p = playlist("A")
        dao.append(p, entries(1, 2))
        dao.repoint(p, 1, 22, "Download/Albums")
        val now = dao.entriesNow(p)
        assertEquals(listOf(1L to "Music", 22L to "Download/Albums"), now.map { it.mediaId to it.folder })
        assertEquals("song2.mp3", now[1].fileName)
    }

    @Test
    fun renameAndNames() = runBlocking {
        val a = playlist("A")
        playlist("B")
        dao.rename(a, "Alpha")
        assertEquals(setOf("Alpha", "B"), dao.names().toSet())
        assertEquals(listOf("B"), dao.namesExcept(a))
        assertEquals("Alpha", dao.playlist(a).first()?.name)
    }
}
