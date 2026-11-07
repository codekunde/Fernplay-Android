package net.codekunde.fernplay.android.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.codekunde.fernplay.android.library.Song

/** A playlist as the detail screen shows it. */
data class PlaylistView(val id: Long, val name: String, val items: List<PlaylistItem>) {
    val songs: List<Song> get() = items.mapNotNull { it.song }
    val missingCount: Int get() = items.count { it.missing }
    val totalMs: Long get() = items.sumOf { it.song?.durationMs ?: it.entry.durationMs }
}

/**
 * Playlists for the screens: lists, one playlist matched to the songs on the
 * phone, and the edits. Writes run in [scope] (the view model's).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistStore(
    private val dao: PlaylistDao,
    private val scope: CoroutineScope,
    /** Every song on the phone (null while reading), to find moved files. */
    private val allSongs: StateFlow<List<Song>?>,
) {
    val summaries: StateFlow<List<PlaylistSummary>?> =
        dao.summaries().stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** For the add sheet: which songs each playlist already holds. */
    val idsByPlaylist: StateFlow<Map<Long, Set<Long>>> = dao.allEntries()
        .map { all -> all.groupBy({ it.playlistId }, { it.mediaId }).mapValues { it.value.toSet() } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** One playlist, null while loading or after it is gone. */
    fun view(id: Long): Flow<PlaylistView?> =
        combine(dao.playlist(id), dao.entries(id), allSongs) { p, entries, songs ->
            if (p == null || songs == null) return@combine null
            // Without any songs (no music access yet) nothing can be matched: show entries as they were saved.
            val items = if (songs.isEmpty()) {
                PlaylistRules.resolve(entries, emptyList()).map { it.copy(song = null) }
            } else PlaylistRules.resolve(entries, songs)
            repairMoved(id, items)
            PlaylistView(p.id, p.name, items)
        }

    /** Saves the new id and folder of songs found by name, so the next lookup is direct. */
    private fun repairMoved(playlistId: Long, items: List<PlaylistItem>) {
        val moved = items.filter { it.song != null && it.song.id != it.entry.mediaId }
        if (moved.isEmpty()) return
        scope.launch {
            moved.forEach { dao.repoint(playlistId, it.entry.position, it.song!!.id, it.song.folder) }
        }
    }

    suspend fun nameProblem(name: String): String? = PlaylistRules.nameProblem(name, dao.names())

    /** Creates a playlist; returns its id, or null when [nameProblem] objects to the name. */
    suspend fun create(name: String, now: Long = System.currentTimeMillis()): Long? {
        if (nameProblem(name) != null) return null
        return dao.insertPlaylist(PlaylistEntity(name = name.trim(), createdAt = now))
    }

    /** Why playlist [id] cannot be renamed to [name], or null when it can (its own name, recased, is fine). */
    suspend fun renameProblem(id: Long, name: String): String? = PlaylistRules.nameProblem(name, dao.namesExcept(id))

    /** Renames; returns false when [renameProblem] objects. */
    suspend fun rename(id: Long, name: String): Boolean {
        if (renameProblem(id, name) != null) return false
        dao.rename(id, name.trim())
        return true
    }

    /** Deletes the playlist and its entries. The songs themselves stay on the phone. */
    suspend fun delete(id: Long) = dao.delete(id)

    /** Adds songs at the end, skipping ones already in. Returns (added, skipped). */
    suspend fun add(playlistId: Long, songs: List<Song>): Pair<Int, Int> {
        val (add, skip) = PlaylistRules.withoutDuplicates(dao.entriesNow(playlistId), songs)
        if (add.isNotEmpty()) dao.append(playlistId, add.map(PlaylistRules::entryOf))
        return add.size to skip.size
    }

    fun reorder(playlistId: Long, items: List<PlaylistItem>) {
        val entries = items.map { it.entry }
        scope.launch { dao.replaceEntries(playlistId, entries) }
    }

    /** Removes the entry at [index] of [items]; returns what [undoRemove] needs. */
    fun remove(playlistId: Long, items: List<PlaylistItem>, index: Int): PlaylistItem? {
        val gone = items.getOrNull(index) ?: return null
        val rest = items.filterIndexed { i, _ -> i != index }.map { it.entry }
        scope.launch { dao.replaceEntries(playlistId, rest) }
        return gone
    }

    fun undoRemove(playlistId: Long, item: PlaylistItem, index: Int) {
        scope.launch {
            val now = dao.entriesNow(playlistId).toMutableList()
            now.add(index.coerceIn(0, now.size), item.entry)
            dao.replaceEntries(playlistId, now)
        }
    }
}
