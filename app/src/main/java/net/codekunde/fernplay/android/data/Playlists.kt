package net.codekunde.fernplay.android.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** A playlist lives in the app, never as a file in a folder. */
@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/**
 * One song in a playlist, in order. The song is found by its id in Android's
 * music index; folder, file name and size are kept so a moved file can be found
 * again after a rescan, and a missing one can be shown as such.
 */
@Entity(
    tableName = "playlist_entry",
    primaryKeys = ["playlistId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("mediaId")],
)
data class PlaylistEntryEntity(
    val playlistId: Long,
    val position: Int,
    val mediaId: Long,
    val folder: String,
    val fileName: String,
    val sizeBytes: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
)

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val songCount: Int,
    val totalMs: Long,
)

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.id, p.name, COUNT(e.position) AS songCount, COALESCE(SUM(e.durationMs), 0) AS totalMs
        FROM playlist p LEFT JOIN playlist_entry e ON e.playlistId = p.id
        GROUP BY p.id ORDER BY p.name COLLATE NOCASE
        """
    )
    fun summaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlist_entry WHERE playlistId = :playlistId ORDER BY position")
    fun entries(playlistId: Long): Flow<List<PlaylistEntryEntity>>

    @Query("SELECT * FROM playlist_entry WHERE playlistId = :playlistId ORDER BY position")
    suspend fun entriesNow(playlistId: Long): List<PlaylistEntryEntity>

    /** Every entry, for the add sheet's "already in" line. */
    @Query("SELECT * FROM playlist_entry")
    fun allEntries(): Flow<List<PlaylistEntryEntity>>

    @Query("SELECT * FROM playlist WHERE id = :id")
    fun playlist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT name FROM playlist")
    suspend fun names(): List<String>

    @Query("DELETE FROM playlist_entry WHERE playlistId = :playlistId")
    suspend fun clearEntries(playlistId: Long)

    /** Points an entry at the song's new id after the music index re-numbered or moved it. */
    @Query(
        """
        UPDATE playlist_entry SET mediaId = :mediaId, folder = :folder
        WHERE playlistId = :playlistId AND position = :position
        """
    )
    suspend fun repoint(playlistId: Long, position: Int, mediaId: Long, folder: String)

    /** Replaces the whole list: used for reorder, remove and undo, so positions stay 0..n-1. */
    @Transaction
    suspend fun replaceEntries(playlistId: Long, entries: List<PlaylistEntryEntity>) {
        clearEntries(playlistId)
        insertEntries(PlaylistRules.renumber(entries, playlistId))
    }

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert
    suspend fun insertEntries(entries: List<PlaylistEntryEntity>)

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM playlist_entry WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Query("UPDATE playlist SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlist WHERE id = :id")
    suspend fun delete(id: Long)

    /** Appends songs at the end, in the order given. */
    @Transaction
    suspend fun append(playlistId: Long, songs: List<PlaylistEntryEntity>) {
        val start = nextPosition(playlistId)
        insertEntries(songs.mapIndexed { i, s -> s.copy(playlistId = playlistId, position = start + i) })
    }
}
