package net.codekunde.fernplay.android.library

/** One song from Android's music index. */
data class Song(
    val id: Long,
    /** content:// address of the file in the music index (text, so tests need no Android). */
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    /** Relative to the storage root, as [MusicFolders] gives it ("Music", "Download/Albums"). */
    val folder: String,
    val fileName: String,
    val sizeBytes: Long,
    /** Seconds since 1970, when Android first indexed the file. */
    val dateAdded: Long,
)

/** A folder on the phone that holds music, with how many songs. */
data class MusicFolder(val path: String, val songCount: Int)
