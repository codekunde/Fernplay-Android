package net.codekunde.fernplay.android.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** Reads songs from Android's music index. */
object MediaStoreSource {

    val collection: Uri
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

    /** Root paths of the mounted volumes (internal storage, SD card), for Android 8 and 9. */
    private fun storageRoots(context: Context): List<String> =
        context.getExternalFilesDirs(null)
            .filterNotNull()
            .map { it.absolutePath.substringBefore("/Android/") }

    /**
     * All music Android has finished indexing. Files still being written or scanned
     * (pending) are left out; they appear on the next change notice.
     */
    fun songs(context: Context): List<Song> {
        val q = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        @Suppress("DEPRECATION")
        val pathColumn = if (q) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            pathColumn,
        )
        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            if (q) append(" AND ${MediaStore.Audio.Media.IS_PENDING} = 0")
        }
        val roots = if (q) emptyList() else storageRoots(context)
        val base = collection
        val result = ArrayList<Song>()
        context.contentResolver.query(base, projection, selection, null, null)?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val name = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val size = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val added = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val path = c.getColumnIndexOrThrow(pathColumn)
            while (c.moveToNext()) {
                val songId = c.getLong(id)
                val fileName = c.getString(name).orEmpty()
                result += Song(
                    id = songId,
                    uri = ContentUris.withAppendedId(base, songId).toString(),
                    title = c.getString(title)?.takeIf { it.isNotBlank() } ?: fileName.substringBeforeLast('.'),
                    artist = c.getString(artist)?.takeUnless { it.isBlank() || it == UNKNOWN } ?: "Unknown artist",
                    album = c.getString(album)?.takeUnless { it.isBlank() || it == UNKNOWN } ?: "Unknown album",
                    durationMs = c.getLong(duration),
                    folder = if (q) MusicFolders.fromRelativePath(c.getString(path))
                    else MusicFolders.fromFilePath(c.getString(path), roots),
                    fileName = fileName,
                    sizeBytes = c.getLong(size),
                    dateAdded = c.getLong(added),
                )
            }
        }
        return result
    }

    private const val UNKNOWN = "<unknown>"
}
