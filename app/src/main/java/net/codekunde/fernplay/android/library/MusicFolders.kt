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

    /**
     * A folder picked with Android's folder picker, from its tree document id
     * ("primary:Music/Live" -> "Music/Live"). Null when the id has no volume part.
     */
    fun fromTreeDocumentId(documentId: String?): String? {
        val id = documentId ?: return null
        if (':' !in id) return null
        return id.substringAfter(':').trim('/')
    }

    /** A chosen folder includes its subfolders. An empty choice ("") is the whole storage. */
    fun includes(chosen: String, folder: String): Boolean =
        chosen.isEmpty() || folder == chosen || folder.startsWith("$chosen/")

    fun includedByAny(chosen: Collection<String>, folder: String): Boolean =
        chosen.any { includes(it, folder) }
}
