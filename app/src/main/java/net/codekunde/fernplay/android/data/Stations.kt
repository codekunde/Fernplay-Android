package net.codekunde.fernplay.android.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * An internet radio station, in its own list (never inside a playlist). One row
 * per stream address; the list keeps the order the stations were added in.
 */
@Entity(tableName = "station", indices = [Index(value = ["url"], unique = true)])
data class StationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val position: Int,
    /**
     * True when the file gave no name and Fernplay made one up (the file's name or
     * the server's address); the station's own name may replace it when it plays.
     * A rename by the user sets it false.
     */
    val nameGuessed: Boolean,
    val addedAt: Long,
)

@Dao
interface StationDao {
    @Query("SELECT * FROM station ORDER BY position")
    fun stations(): Flow<List<StationEntity>>

    @Query("SELECT url FROM station")
    suspend fun urls(): List<String>

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM station")
    suspend fun nextPosition(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(stations: List<StationEntity>): List<Long>

    @Query("UPDATE station SET name = :name, nameGuessed = 0 WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM station WHERE id = :id")
    suspend fun delete(id: Long)
}
