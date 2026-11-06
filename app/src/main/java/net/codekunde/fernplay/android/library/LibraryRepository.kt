package net.codekunde.fernplay.android.library

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * All songs on the phone, updated whenever Android's music index changes
 * (a download finishes, a file is copied over USB, a file is deleted).
 */
class LibraryRepository(private val context: Context) {

    fun allSongs(): Flow<List<Song>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(MediaStoreSource.collection, true, observer)
        trySend(Unit)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }
        .conflate()
        .map {
            try {
                MediaStoreSource.songs(context)
            } catch (e: SecurityException) {
                emptyList() // music access was taken away in Android's settings
            }
        }
        .flowOn(Dispatchers.IO)

    companion object {
        fun folders(songs: List<Song>): List<MusicFolder> =
            songs.groupingBy { it.folder }.eachCount()
                .map { (path, count) -> MusicFolder(path, count) }
                .sortedWith(compareByDescending<MusicFolder> { it.songCount }.thenBy { it.path.lowercase() })
    }
}
