package net.codekunde.fernplay.android

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.codekunde.fernplay.android.data.FernplayDatabase
import net.codekunde.fernplay.android.data.StationStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Executor

/**
 * An installed 0.3.0 has a version 1 database with playlists in it. Version 2 adds
 * the station table; the playlists must come through untouched.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {

    @Test
    fun version1PlaylistsSurviveAndStationsWork() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-test.db"
        val path = context.getDatabasePath(name).also { it.parentFile?.mkdirs(); it.delete() }

        // Build the database exactly as version 1's exported schema describes it.
        val schema = JSONObject(File("schemas/net.codekunde.fernplay.android.data.FernplayDatabase/1.json").readText())
            .getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                old.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
            old.execSQL("INSERT INTO playlist (id, name, createdAt) VALUES (1, 'Gym', 0)")
            old.execSQL(
                "INSERT INTO playlist_entry VALUES (1, 0, 42, 'Music', 'a.mp3', 100, 'A song', 'An artist', 180000)"
            )
            old.version = 1
        }

        val direct = Executor { it.run() }
        val db = Room.databaseBuilder(context, FernplayDatabase::class.java, name)
            .allowMainThreadQueries()
            .setQueryExecutor(direct)
            .setTransactionExecutor(direct)
            .build()
        try {
            val playlists = db.playlists().summaries().first()
            assertEquals(listOf("Gym"), playlists.map { it.name })
            assertEquals(1, playlists.single().songCount)
            assertEquals("A song", db.playlists().entriesNow(1).single().title)

            StationStore(db.stations(), CoroutineScope(UnconfinedTestDispatcher()))
                .import("a.m3u", "https://radio.example.org/a".toByteArray())
            assertEquals(1, db.stations().stations().first().size)
            assertEquals(2, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
            path.delete()
        }
    }
}
