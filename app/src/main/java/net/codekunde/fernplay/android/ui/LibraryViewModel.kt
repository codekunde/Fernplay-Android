package net.codekunde.fernplay.android.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.codekunde.fernplay.android.library.LibraryRepository
import net.codekunde.fernplay.android.library.MusicFolder
import net.codekunde.fernplay.android.library.MusicFolders
import net.codekunde.fernplay.android.library.Song
import net.codekunde.fernplay.android.library.SongList
import net.codekunde.fernplay.android.library.SongSort
import net.codekunde.fernplay.android.settings.Settings
import net.codekunde.fernplay.android.settings.SettingsStore
import net.codekunde.fernplay.android.ui.theme.ThemeChoice

/** What the All songs list shows: songs in order and where each strip letter starts. */
data class SongListView(val songs: List<Song>, val letters: Map<Char, Int>)

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = LibraryRepository(app)
    private val store = SettingsStore(app)

    val musicPermission: String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private val _hasPermission = MutableStateFlow(checkPermission())
    val hasPermission: StateFlow<Boolean> = _hasPermission

    /** Call when the app comes back to the front: access may have changed in Android's settings. */
    fun refreshPermission() {
        _hasPermission.value = checkPermission()
    }

    private fun checkPermission(): Boolean =
        ContextCompat.checkSelfPermission(getApplication(), musicPermission) == PackageManager.PERMISSION_GRANTED

    /** Null until the stored settings are read. */
    val settings: StateFlow<Settings?> =
        store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Every song on the phone; null while the first read is running. */
    val allSongs: StateFlow<List<Song>?> = hasPermission
        .flatMapLatest { granted -> if (granted) repository.allSongs() else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val folders: StateFlow<List<MusicFolder>?> = allSongs
        .map { songs -> songs?.let { LibraryRepository.folders(it) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Songs in the chosen folders: Fernplay's library. */
    val librarySongs: StateFlow<List<Song>?> = combine(allSongs, settings) { songs, s ->
        if (songs == null || s == null) null
        else songs.filter { MusicFolders.includedByAny(s.folders, it.folder) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val query = MutableStateFlow("")
    val sort = MutableStateFlow(SongSort.Title)

    val songList: StateFlow<SongListView?> = combine(librarySongs, query, sort) { songs, q, s ->
        songs?.let {
            val shown = SongList.sorted(it.filter { song -> SongList.matches(song, q) }, s)
            SongListView(shown, if (q.isBlank()) SongList.letterPositions(shown, s) else emptyMap())
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // First run: which folders are ticked. Null until the user changes something:
    // then every folder Android found is ticked.
    private val _picked = MutableStateFlow<Set<String>?>(null)
    val picked: StateFlow<Set<String>?> = _picked

    /** Folders added with Android's folder picker that the music index has no songs for (yet). */
    private val _extraFolders = MutableStateFlow<Set<String>>(emptySet())
    val extraFolders: StateFlow<Set<String>> = _extraFolders

    fun togglePicked(path: String, current: Set<String>) {
        _picked.value = if (path in current) current - path else current + path
    }

    fun addPickedFolder(path: String, current: Set<String>) {
        if (folders.value.orEmpty().none { it.path == path }) _extraFolders.value += path
        _picked.value = current + path
    }

    fun finishSetup(folders: Set<String>) {
        viewModelScope.launch { store.finishSetup(folders) }
    }

    fun setTheme(theme: ThemeChoice) {
        viewModelScope.launch { store.setTheme(theme) }
    }
}
