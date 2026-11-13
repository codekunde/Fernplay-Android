package net.codekunde.fernplay.android.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PlaylistEntity::class, PlaylistEntryEntity::class, StationEntity::class],
    version = 2,
    exportSchema = true,
    // 2: radio stations (a new table; playlists untouched).
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class FernplayDatabase : RoomDatabase() {
    abstract fun playlists(): PlaylistDao
    abstract fun stations(): StationDao

    companion object {
        @Volatile private var instance: FernplayDatabase? = null

        fun get(context: Context): FernplayDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FernplayDatabase::class.java,
                    "fernplay.db",
                ).build().also { instance = it }
            }
    }
}
