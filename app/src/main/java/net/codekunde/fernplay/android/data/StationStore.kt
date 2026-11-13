package net.codekunde.fernplay.android.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What adding stations from a file did, for the message under the list. */
sealed interface StationImport {
    val fileName: String

    data class Added(override val fileName: String, val added: Int, val alreadyIn: Int) : StationImport
    /** The file had no http or https stream lines (for example a playlist of songs on the phone). */
    data class NoStations(override val fileName: String) : StationImport
    data class NotAStationFile(override val fileName: String) : StationImport
    data class TooLarge(override val fileName: String) : StationImport
    data class Unreadable(override val fileName: String) : StationImport

    val message: String
        get() = when (this) {
            is Added -> when {
                added == 0 -> if (alreadyIn == 1) "That station is already in Radio" else "Those stations are already in Radio"
                alreadyIn == 0 -> "Added ${stations(added)}"
                else -> "Added ${stations(added)} · $alreadyIn already in Radio"
            }
            is NoStations -> "No stations in “$fileName”. Fernplay adds the http and https streams it lists."
            is NotAStationFile -> "“$fileName” is not an .m3u or .m3u8 file"
            is TooLarge -> "“$fileName” is too large for a station list"
            is Unreadable -> "Couldn’t read “$fileName”"
        }

    companion object {
        fun stations(n: Int) = if (n == 1) "1 station" else "$n stations"
    }
}

/** The Radio list for the screens: read, add from a file, rename, delete with Undo. */
class StationStore(private val dao: StationDao, private val scope: CoroutineScope) {

    val stations: StateFlow<List<StationEntity>?> =
        dao.stations().stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** Adds the stations in a playlist file's [bytes] at the end of the list, skipping addresses already in. */
    suspend fun import(fileName: String, bytes: ByteArray, now: Long = System.currentTimeMillis()): StationImport {
        if (!StationFiles.isStationFile(fileName)) return StationImport.NotAStationFile(fileName)
        if (bytes.size > StationFiles.MAX_BYTES) return StationImport.TooLarge(fileName)
        val parsed = StationFiles.parse(StationFiles.decode(bytes, fileName))
        if (parsed.isEmpty()) return StationImport.NoStations(fileName)
        val known = dao.urls().map { it.lowercase() }.toSet()
        val fresh = parsed.filter { it.url.lowercase() !in known }
        if (fresh.isNotEmpty()) {
            val start = dao.nextPosition()
            dao.insert(
                fresh.mapIndexed { i, p ->
                    StationEntity(
                        name = p.name ?: StationFiles.guessName(p.url, fileName, parsed.size),
                        url = p.url,
                        position = start + i,
                        nameGuessed = p.name == null,
                        addedAt = now,
                    )
                }
            )
        }
        return StationImport.Added(fileName, fresh.size, parsed.size - fresh.size)
    }

    /** Why [name] cannot be a station's name, or null when it can. Names need not be unique. */
    fun nameProblem(name: String): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> "Type a name"
            n.length > StationFiles.MAX_NAME -> "Keep it under ${StationFiles.MAX_NAME} characters"
            else -> null
        }
    }

    suspend fun rename(id: Long, name: String): Boolean {
        if (nameProblem(name) != null) return false
        dao.rename(id, StationFiles.clean(name)!!)
        return true
    }

    /** Deletes the station; [undoDelete] with the returned row puts it back in its place. */
    fun delete(station: StationEntity): StationEntity {
        scope.launch { dao.delete(station.id) }
        return station
    }

    fun undoDelete(station: StationEntity) {
        scope.launch { dao.insert(listOf(station)) }
    }
}
