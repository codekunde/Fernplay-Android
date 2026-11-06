package net.codekunde.fernplay.android.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.codekunde.fernplay.android.ui.theme.ThemeChoice

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    /** False until the first-run screen is finished once. */
    val setupDone: Boolean,
    /** Music folders, relative to storage ("Music", "Download/Albums"); subfolders included. */
    val folders: Set<String>,
    val theme: ThemeChoice,
)

class SettingsStore(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            setupDone = p[SETUP_DONE] ?: false,
            folders = p[FOLDERS] ?: emptySet(),
            theme = p[THEME]?.let { name -> ThemeChoice.entries.firstOrNull { it.name == name } } ?: ThemeChoice.System,
        )
    }

    suspend fun finishSetup(folders: Set<String>) {
        context.dataStore.edit {
            it[FOLDERS] = folders
            it[SETUP_DONE] = true
        }
    }

    suspend fun setFolders(folders: Set<String>) {
        context.dataStore.edit { it[FOLDERS] = folders }
    }

    suspend fun setTheme(theme: ThemeChoice) {
        context.dataStore.edit { it[THEME] = theme.name }
    }

    private companion object {
        val SETUP_DONE = booleanPreferencesKey("setup_done")
        val FOLDERS = stringSetPreferencesKey("folders")
        val THEME = stringPreferencesKey("theme")
    }
}
