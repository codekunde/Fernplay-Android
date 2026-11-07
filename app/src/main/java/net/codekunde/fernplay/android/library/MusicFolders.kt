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

/**
 * The library's folders: each chosen folder with its subfolders, minus the folders
 * left out ("Music" chosen, "Music/Telegram" left out). The deepest matching entry
 * wins, so a folder chosen inside a left-out one is back in.
 */
data class FolderChoice(val chosen: Set<String>, val leftOut: Set<String> = emptySet()) {

    fun includes(folder: String): Boolean {
        val on = chosen.filter { MusicFolders.includes(it, folder) }.maxOfOrNull { it.length } ?: return false
        val off = leftOut.filter { MusicFolders.includes(it, folder) }.maxOfOrNull { it.length } ?: return true
        return on > off
    }

    /** The chosen folder that brings [folder] in from above, if any. */
    fun parentOf(folder: String): String? =
        chosen.filter { it != folder && MusicFolders.includes(it, folder) }.minByOrNull { it.length }

    /** Ticks [path]: no longer left out, and chosen if nothing else brings it in. */
    fun withOn(path: String): FolderChoice {
        val next = copy(leftOut = leftOut - path)
        return (if (next.includes(path)) next else next.copy(chosen = chosen + path)).tidy()
    }

    /** Unticks [path]: no longer chosen, and left out if a chosen parent still brings it in. */
    fun withOff(path: String): FolderChoice {
        val next = copy(chosen = chosen - path, leftOut = leftOut - path)
        return (if (next.includes(path)) next.copy(leftOut = next.leftOut + path) else next).tidy()
    }

    /**
     * After adding [path] with the folder picker in Settings, or null when it is
     * already in. The whole folder comes in: chosen and left-out folders inside it go.
     */
    fun withAdded(path: String): FolderChoice? {
        if (includes(path)) return null
        val cleared = FolderChoice(
            chosen.filterNot { MusicFolders.includes(path, it) }.toSet(),
            leftOut.filterNot { MusicFolders.includes(path, it) }.toSet(),
        )
        // A left-out folder only needs taking back in; its parent brings it in again.
        return (if (cleared.includes(path)) cleared else cleared.copy(chosen = cleared.chosen + path)).tidy()
    }

    /** Drops left-out folders that would be out anyway. */
    private fun tidy(): FolderChoice =
        copy(leftOut = leftOut.filter { copy(leftOut = leftOut - it).includes(it) }.toSet())
}
