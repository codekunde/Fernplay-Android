package net.codekunde.fernplay.android

import net.codekunde.fernplay.android.playback.ResumeCodec
import net.codekunde.fernplay.android.playback.ResumeItem
import net.codekunde.fernplay.android.playback.ResumePosition
import net.codekunde.fernplay.android.playback.ResumeQueue
import net.codekunde.fernplay.android.playback.ResumeState
import net.codekunde.fernplay.android.playback.ResumeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ResumeTest {

    private fun item(id: String, title: String = "Song $id") =
        ResumeItem(id, "content://media/external/audio/media/$id", title, "The Spore Prints", "Mycelium", 180_000)

    @Test
    fun queueSurvivesTabsNewlinesBackslashesAndAccents() {
        val q = ResumeQueue(
            "Gym\tPush\n2",
            listOf(item("1", "Moss\\Lantern"), item("2", "Café\r\nNoir"), item("3", "")),
        )
        assertEquals(q, ResumeCodec.decodeQueue(ResumeCodec.encodeQueue(q)))
    }

    @Test
    fun positionRoundTrips() {
        val p = ResumePosition("42", 6, 72_000, 2)
        assertEquals(p, ResumeCodec.decodePosition(ResumeCodec.encodePosition(p)))
    }

    @Test
    fun damagedFilesReadAsNothing() {
        assertNull(ResumeCodec.decodeQueue(""))
        assertNull(ResumeCodec.decodeQueue("something else\nname\n"))
        assertNull(ResumeCodec.decodeQueue("fernplay-queue 1\nname\n1\turi\tonly three\n"))
        assertNull(ResumeCodec.decodeQueue("fernplay-queue 1\nname\n1\tu\tt\ta\tb\tnot-a-number\n"))
        assertNull(ResumeCodec.decodePosition("fernplay-position 1\n42\nseven\n0\n0\n"))
    }

    @Test
    fun startIndexFollowsTheSongWhenTheQueueMoved() {
        val q = ResumeQueue("All songs", listOf(item("1"), item("2"), item("3")))
        assertEquals(1, ResumeState(q, ResumePosition("2", 1, 5_000, 0)).startIndex)
        // Saved index points at another song: find the saved song instead.
        val moved = ResumeState(q, ResumePosition("3", 0, 5_000, 0))
        assertEquals(2, moved.startIndex)
        assertEquals(5_000, moved.startPositionMs)
        // Song gone: keep the place in the list, start the song from the top.
        val gone = ResumeState(q, ResumePosition("9", 7, 5_000, 0))
        assertEquals(2, gone.startIndex)
        assertEquals(0, gone.startPositionMs)
    }

    @Test
    fun storeWritesAndReadsBack() {
        val dir = Files.createTempDirectory("fernplay-resume").toFile()
        try {
            val store = ResumeStore(dir)
            assertNull(store.read())
            val q = ResumeQueue("All songs", listOf(item("1"), item("2")))
            store.writeQueue(q)
            // Queue without a position file: start at the first song.
            assertEquals(ResumeState(q, ResumePosition("1", 0, 0, 0)), store.read())
            store.writePosition(ResumePosition("2", 1, 61_000, 1))
            store.writePosition(ResumePosition("2", 1, 62_000, 1)) // replaces the file
            val read = store.read()!!
            assertEquals(1, read.startIndex)
            assertEquals(62_000, read.startPositionMs)
            assertEquals(1, read.position.repeatMode)
            assertEquals(listOf("resume-position.txt", "resume-queue.txt"), dir.list()!!.sorted())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun emptyOrDamagedQueueFileMeansNothingToResume() {
        val dir = Files.createTempDirectory("fernplay-resume").toFile()
        try {
            val store = ResumeStore(dir)
            store.writeQueue(ResumeQueue("Empty", emptyList()))
            assertNull(store.read())
            File(dir, "resume-queue.txt").writeText("half a fi")
            assertNull(store.read())
        } finally {
            dir.deleteRecursively()
        }
    }
}
