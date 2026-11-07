package net.codekunde.fernplay.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.codekunde.fernplay.android.data.FernplayDatabase
import net.codekunde.fernplay.android.data.PlaylistStore
import net.codekunde.fernplay.android.library.Song
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistStoreTest {

    private lateinit var db: FernplayDatabase
    private lateinit var job: Job
    private lateinit var songs: MutableStateFlow<List<Song>?>
    private lateinit var store: PlaylistStore

    @Before
    fun open() {
        db = testDatabase()
        job = SupervisorJob()
        songs = MutableStateFlow(null)
        store = PlaylistStore(db.playlists(), CoroutineScope(job + Dispatchers.Unconfined), songs)
        // The store's shared flows run in its scope for as long as it lives: never wait for those.
        sharing = job.children.toSet()
    }

    private lateinit var sharing: Set<Job>

    @After
    fun close() {
        CoroutineScope(job).cancel()
        db.close()
    }

    /** Waits for the writes the store launched in its scope. */
    private suspend fun settle() = withTimeout(5_000) { (job.children - sharing).forEach { it.join() } }

    private suspend fun ids(playlistId: Long) = db.playlists().entriesNow(playlistId).map { it.mediaId }

    @Test
    fun addSkipsSongsAlreadyIn() = runBlocking {
        val p = store.create("Gym")!!
        assertEquals(2 to 0, store.add(p, listOf(testSong(1), testSong(2))))
        assertEquals(1 to 2, store.add(p, listOf(testSong(2), testSong(3), testSong(3))))
        assertEquals(listOf(1L, 2L, 3L), ids(p))
    }

    @Test
    fun namesAreUniqueIgnoringCaseAndNeverAllSongs() = runBlocking {
        val a = store.create("  Gym ")!!
        assertEquals("Gym", db.playlists().playlist(a).first()?.name)
        assertNull(store.create("gym"))
        assertNull(store.create("all songs"))
        val b = store.create("Drive")!!
        assertFalse(store.rename(b, "GYM"))
        assertTrue(store.rename(a, "GYM")) // its own name, recased
        assertEquals(setOf("GYM", "Drive"), db.playlists().names().toSet())
    }

    @Test
    fun removeAndUndoPutTheSongBackInPlace() = runBlocking {
        val p = store.create("A")!!
        store.add(p, listOf(1L, 2L, 3L).map { testSong(it) })
        songs.value = listOf(1L, 2L, 3L).map { testSong(it) }
        val items = withTimeout(5_000) { store.view(p).filterNotNull().first() }.items

        val gone = store.remove(p, items, 1)
        settle()
        assertEquals(listOf(1L, 3L), ids(p))
        store.undoRemove(p, gone!!, 1)
        settle()
        assertEquals(listOf(1L, 2L, 3L), ids(p))
        assertEquals(listOf(0, 1, 2), db.playlists().entriesNow(p).map { it.position })
    }

    @Test
    fun reorderIsSaved() = runBlocking {
        val p = store.create("A")!!
        store.add(p, listOf(1L, 2L, 3L).map { testSong(it) })
        songs.value = listOf(1L, 2L, 3L).map { testSong(it) }
        val items = withTimeout(5_000) { store.view(p).filterNotNull().first() }.items
        store.reorder(p, listOf(items[2], items[0], items[1]))
        settle()
        assertEquals(listOf(3L, 1L, 2L), ids(p))
    }

    @Test
    fun aMovedSongIsFoundAndRepointedAfterARescan() = runBlocking {
        val p = store.create("A")!!
        store.add(p, listOf(testSong(1), testSong(2)))
        // After the rescan song 2 has a new id in another folder; song 1 is gone.
        songs.value = listOf(testSong(2).copy(id = 42, folder = "Download/Albums"), testSong(7))

        val view = withTimeout(5_000) { store.view(p).filterNotNull().first() }
        settle()
        assertEquals(listOf(null, 42L), view.items.map { it.song?.id })
        assertEquals(1, view.missingCount)
        assertEquals(listOf(1L, 42L), ids(p))
        assertEquals("Download/Albums", db.playlists().entriesNow(p)[1].folder)
        // The missing song still counts towards the length, from what was saved.
        assertEquals(360_000L, view.totalMs)
    }

    @Test
    fun withoutMusicAccessEveryEntryShowsAsSaved() = runBlocking {
        val p = store.create("A")!!
        store.add(p, listOf(testSong(1)))
        songs.value = emptyList()
        val view = withTimeout(5_000) { store.view(p).filterNotNull().first() }
        assertTrue(view.items.single().missing)
        assertEquals("Song 1", view.items.single().entry.title)
    }

    @Test
    fun deleteKeepsOtherPlaylists() = runBlocking {
        val a = store.create("A")!!
        val b = store.create("B")!!
        store.add(a, listOf(testSong(1)))
        store.add(b, listOf(testSong(1)))
        store.delete(a)
        assertEquals(listOf(1L), ids(b))
        assertNotNull(db.playlists().playlist(b).first())
        assertNull(db.playlists().playlist(a).first())
    }
}
