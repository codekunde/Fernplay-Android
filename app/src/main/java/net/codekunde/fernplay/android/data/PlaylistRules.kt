package net.codekunde.fernplay.android.data

import net.codekunde.fernplay.android.library.Song
import net.codekunde.fernplay.android.library.SongList

/**
 * A playlist entry matched to a song on the phone. [song] is null when the file
 * was moved out of reach or deleted. [key] stays the same when the list is
 * reordered, so the list can animate and drag by it.
 */
data class PlaylistItem(val entry: PlaylistEntryEntity, val song: Song?, val key: String) {
    val missing: Boolean get() = song == null
}

/** Playlist logic with no Android types, so it is unit tested. */
object PlaylistRules {

    /** The library's own queue name; no playlist may take it. */
    const val ALL_SONGS = "All songs"
    const val MAX_NAME = 60

    fun entryOf(song: Song): PlaylistEntryEntity = PlaylistEntryEntity(
        playlistId = 0,
        position = 0,
        mediaId = song.id,
        folder = song.folder,
        fileName = song.fileName,
        sizeBytes = song.sizeBytes,
        title = song.title,
        artist = song.artist,
        durationMs = song.durationMs,
    )

    /**
     * Finds each entry's song: by its id in the music index (if the file name
     * still matches), else by folder and file name (re-indexed in place), else
     * by file name and size anywhere (moved to another folder, only if exactly
     * one file fits). Otherwise the entry is missing.
     */
    fun resolve(entries: List<PlaylistEntryEntity>, songs: List<Song>): List<PlaylistItem> {
        val byId = songs.associateBy { it.id }
        val byPath = songs.associateBy { pathKey(it.folder, it.fileName) }
        val byNameSize = songs.groupBy { it.fileName.lowercase() to it.sizeBytes }
        val seen = HashMap<Long, Int>()
        return entries.map { e ->
            val song = byId[e.mediaId]?.takeIf { it.fileName.equals(e.fileName, ignoreCase = true) }
                ?: byPath[pathKey(e.folder, e.fileName)]
                ?: byNameSize[e.fileName.lowercase() to e.sizeBytes]?.singleOrNull()
            val n = seen.merge(e.mediaId, 1, Int::plus)
            PlaylistItem(e, song, "${e.mediaId}#$n")
        }
    }

    private fun pathKey(folder: String, fileName: String) = "$folder/$fileName".lowercase()

    /** Entries numbered 0..n-1 for [playlistId], in list order. */
    fun renumber(entries: List<PlaylistEntryEntity>, playlistId: Long): List<PlaylistEntryEntity> =
        entries.mapIndexed { i, e -> e.copy(playlistId = playlistId, position = i) }

    fun <T> move(list: List<T>, from: Int, to: Int): List<T> {
        if (from == to || from !in list.indices || to !in list.indices) return list
        val m = list.toMutableList()
        m.add(to, m.removeAt(from))
        return m
    }

    /** Splits [songs] into those to add and those [existing] already holds; repeats within [songs] count once. */
    fun withoutDuplicates(existing: List<PlaylistEntryEntity>, songs: List<Song>): Pair<List<Song>, List<Song>> {
        val have = existing.mapTo(HashSet()) { it.mediaId }
        val add = ArrayList<Song>()
        val skip = ArrayList<Song>()
        for (s in songs) if (have.add(s.id)) add += s else skip += s
        return add to skip
    }

    /** Why [name] cannot be used for a new playlist, or null when it can. */
    fun nameProblem(name: String, existing: List<String>): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> "Type a name"
            n.length > MAX_NAME -> "Keep it under $MAX_NAME characters"
            n.equals(ALL_SONGS, ignoreCase = true) || existing.any { it.trim().equals(n, ignoreCase = true) } ->
                "There is already a playlist called “$n”"
            else -> null
        }
    }

    fun songCount(n: Int): String = if (n == 1) "1 song" else "$n songs"

    /** "1 h 38 min", "44 min"; under a minute as "0:45". */
    fun length(ms: Long): String {
        val minutes = ms / 60_000
        return when {
            minutes >= 60 -> if (minutes % 60 == 0L) "${minutes / 60} h" else "${minutes / 60} h ${minutes % 60} min"
            minutes >= 1 -> "$minutes min"
            else -> SongList.duration(ms)
        }
    }

    /** "24 songs · 1 h 38 min", or "0 songs". */
    fun summary(count: Int, totalMs: Long): String =
        if (count == 0) songCount(0) else "${songCount(count)} · ${length(totalMs)}"

    /** The note after adding: what went in and what was already there. */
    fun addedMessage(added: Int, skipped: Int, playlist: String): String = when {
        added == 0 -> if (skipped == 1) "That song is already in $playlist" else "Those songs are already in $playlist"
        skipped == 0 -> "Added ${songCount(added)} to $playlist"
        else -> "Added ${songCount(added)} to $playlist · $skipped already in"
    }

    /** Second line in the add sheet: "12 songs · Frond already in", "41 songs · 2 already in". */
    fun sheetLine(count: Int, alreadyIn: List<String>): String = when (alreadyIn.size) {
        0 -> songCount(count)
        1 -> "${songCount(count)} · ${alreadyIn[0]} already in"
        else -> "${songCount(count)} · ${alreadyIn.size} already in"
    }
}
