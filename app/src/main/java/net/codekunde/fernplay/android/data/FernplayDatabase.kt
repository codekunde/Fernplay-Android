package net.codekunde.fernplay.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PlaylistEntity::class, PlaylistEntryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class FernplayDatabase : RoomDatabase() {
    abstract fun playlists(): PlaylistDao

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
