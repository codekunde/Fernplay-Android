package net.codekunde.fernplay.android

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import net.codekunde.fernplay.android.data.FernplayDatabase
import net.codekunde.fernplay.android.library.Song
import java.util.concurrent.Executor

/** An in-memory database whose queries run on the calling thread, so tests read results at once. */
fun testDatabase(): FernplayDatabase {
    val direct = Executor { it.run() }
    return Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FernplayDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(direct)
        .setTransactionExecutor(direct)
        .build()
}

fun testSong(
    id: Long,
    file: String = "song$id.mp3",
    folder: String = "Music",
    size: Long = 1000 + id,
    title: String = "Song $id",
    durationMs: Long = 180_000,
) = Song(id, "content://media/external/audio/media/$id", title, "Artist", "Album", durationMs, folder, file, size, 0)
