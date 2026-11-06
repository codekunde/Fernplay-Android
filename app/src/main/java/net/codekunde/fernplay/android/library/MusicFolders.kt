package net.codekunde.fernplay.android.library

/**
 * Folder names as Fernplay stores and shows them: relative to the storage root,
 * without slashes at either end ("Music", "Download/Albums").
 *
 * Android 10+ gives the folder directly (RELATIVE_PATH, "Music/"); Android 8 and 9
 * give the full file path ("/storage/emulated/0/Music/Song.mp3"). Both must end
 * up the same, so a library chosen on one Android version still matches after an
 * update.
 */
object MusicFolders {

    /** From Android 10+'s RELATIVE_PATH. */
    fun fromRelativePath(relativePath: String?): String =
        relativePath.orEmpty().trim('/')

    /**
     * From Android 8/9's full file path. [storageRoots] are the mounted volumes'
     * root paths (internal storage and any SD card); the longest match wins.
     */
    fun fromFilePath(filePath: String?, storageRoots: List<String>): String {
        val path = filePath.orEmpty()
        val folder = path.substringBeforeLast('/', missingDelimiterValue = "")
        val root = storageRoots
            .map { it.trimEnd('/') }
            .filter { it.isNotEmpty() && (folder == it || folder.startsWith("$it/")) }
            .maxByOrNull { it.length }
            ?: return folder.trim('/')
        return folder.removePrefix(root).trim('/')
    }
}
