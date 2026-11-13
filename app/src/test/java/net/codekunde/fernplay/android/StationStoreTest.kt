package net.codekunde.fernplay.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import net.codekunde.fernplay.android.data.FernplayDatabase
import net.codekunde.fernplay.android.data.StationDao
import net.codekunde.fernplay.android.data.StationImport
import net.codekunde.fernplay.android.data.StationStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class StationStoreTest {

    private lateinit var db: FernplayDatabase
    private lateinit var dao: StationDao
    private lateinit var store: StationStore

    @Before
    fun open() {
        db = testDatabase()
        dao = db.stations()
        store = StationStore(dao, CoroutineScope(UnconfinedTestDispatcher()))
    }

    @After
    fun close() = db.close()

    private fun file(vararg lines: String) = lines.joinToString("\n").toByteArray()

    private suspend fun stations() = dao.stations().first()

    @Test
    fun importAddsInFileOrderWithNamesOrGuesses() = runBlocking {
        val r = store.import(
            "Two.m3u",
            file("#EXTM3U", "#EXTINF:0,Fern FM", "https://radio.example.org/fern", "http://stream.example.net/live.mp3"),
            now = 7,
        )
        assertEquals(StationImport.Added("Two.m3u", added = 2, alreadyIn = 0), r)
        val s = stations()
        assertEquals(listOf("Fern FM", "stream.example.net"), s.map { it.name })
        assertEquals(listOf(false, true), s.map { it.nameGuessed })
        assertEquals(listOf(0, 1), s.map { it.position })
        assertEquals(7L, s[0].addedAt)
    }

    @Test
    fun aFileWithOneUnnamedStationGivesItsName() = runBlocking {
        store.import("hr Fern.m3u", file("http://stream.example.net/fern.mp3"))
        assertEquals("hr Fern", stations().single().name)
    }

    @Test
    fun secondFileAppendsAndSkipsKnownAddresses() = runBlocking {
        store.import("a.m3u", file("https://radio.example.org/a", "https://radio.example.org/b"))
        val r = store.import("b.m3u8", file("HTTPS://radio.example.org/B", "https://radio.example.org/c"))
        assertEquals(StationImport.Added("b.m3u8", added = 1, alreadyIn = 1), r)
        assertEquals("Added 1 station · 1 already in Radio", r.message)
        assertEquals(listOf("/a", "/b", "/c"), stations().map { it.url.substringAfter(".org") })
        assertEquals(listOf(0, 1, 2), stations().map { it.position })
    }

    @Test
    fun theSameFileAgainAddsNothing() = runBlocking {
        store.import("a.m3u", file("https://radio.example.org/a"))
        val r = store.import("a.m3u", file("https://radio.example.org/a"))
        assertEquals("That station is already in Radio", r.message)
        assertEquals(1, stations().size)
    }

    @Test
    fun refusesOtherFilesAndSaysWhy() = runBlocking {
        assertTrue(store.import("list.pls", file("File1=https://radio.example.org/a")) is StationImport.NotAStationFile)
        assertTrue(store.import("songs.m3u", file("#EXTM3U", "Music/a.mp3")) is StationImport.NoStations)
        assertTrue(store.import("big.m3u", ByteArray(1_000_001) { 'a'.code.toByte() }) is StationImport.TooLarge)
        assertTrue(stations().isEmpty())
    }

    @Test
    fun renameKeepsTheNameAndStopsGuessing() = runBlocking {
        store.import("x.m3u", file("https://radio.example.org/a"))
        val id = stations().single().id
        assertFalse(store.rename(id, "   "))
        assertTrue(store.rename(id, "  Fern   FM "))
        val s = stations().single()
        assertEquals("Fern FM", s.name)
        assertFalse(s.nameGuessed)
    }

    @Test
    fun deleteAndUndoPutsTheStationBackInItsPlace() = runBlocking {
        store.import("a.m3u", file("https://radio.example.org/a", "https://radio.example.org/b", "https://radio.example.org/c"))
        val middle = stations()[1]
        val gone = store.delete(middle)
        assertEquals(listOf("/a", "/c"), stations().map { it.url.substringAfter(".org") })
        store.undoDelete(gone)
        val back = stations()
        assertEquals(listOf("/a", "/b", "/c"), back.map { it.url.substringAfter(".org") })
        assertNotNull(back.find { it.id == middle.id })
    }

    @Test
    fun newStationsGoAfterTheLastEvenWhenOneWasDeleted() = runBlocking {
        store.import("a.m3u", file("https://radio.example.org/a", "https://radio.example.org/b"))
        store.delete(stations()[1])
        store.import("c.m3u", file("https://radio.example.org/c"))
        assertEquals(listOf(0, 1), stations().map { it.position })
        assertEquals(listOf("/a", "/c"), stations().map { it.url.substringAfter(".org") })
    }
}
