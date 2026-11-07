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
import net.codekunde.fernplay.android.data.FernplayDatabase
import net.codekunde.fernplay.android.data.PlaylistStore
import net.codekunde.fernplay.android.library.FolderChoice
import net.codekunde.fernplay.android.library.LibraryRepository
import net.codekunde.fernplay.android.library.Rescan
import net.codekunde.fernplay.android.library.RescanProgress
import net.codekunde.fernplay.android.library.MusicFolder
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
        else s.choice.let { choice -> songs.filter { choice.includes(it.folder) } }
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
    private val _picked = MutableStateFlow<FolderChoice?>(null)
    val picked: StateFlow<FolderChoice?> = _picked

    /** Folders added with Android's folder picker that the music index has no songs for (yet). */
    private val _extraFolders = MutableStateFlow<Set<String>>(emptySet())
    val extraFolders: StateFlow<Set<String>> = _extraFolders

    fun togglePicked(path: String, current: FolderChoice) {
        _picked.value = if (current.includes(path)) current.withOff(path) else current.withOn(path)
    }

    fun addPickedFolder(path: String, current: FolderChoice) {
        if (folders.value.orEmpty().none { it.path == path }) _extraFolders.value += path
        _picked.value = current.withOn(path)
    }

    fun finishSetup(choice: FolderChoice) {
        viewModelScope.launch { store.finishSetup(choice) }
    }

    fun setTheme(theme: ThemeChoice) {
        viewModelScope.launch { store.setTheme(theme) }
    }

    /** Playlists, matched against every song on the phone so moved files are found. */
    val playlists = PlaylistStore(FernplayDatabase.get(app).playlists(), viewModelScope, allSongs)

    // Multi-select in All songs (long-press, or "Add songs" from a playlist).
    private val _selected = MutableStateFlow<Set<Long>?>(null)
    /** Ticked song ids while selecting; null when not selecting. */
    val selected: StateFlow<Set<Long>?> = _selected

    fun startSelection(first: Long? = null) {
        _selected.value = setOfNotNull(first)
    }

    /** Ticks or unticks a song. Unticking the last one ends a long-press selection, unless [keepEmpty]. */
    fun toggleSelected(id: Long, keepEmpty: Boolean) {
        val now = _selected.value ?: return
        val next = if (id in now) now - id else now + id
        _selected.value = if (next.isEmpty() && !keepEmpty) null else next
    }

    fun selectAll(ids: Collection<Long>) {
        if (_selected.value != null) _selected.value = ids.toSet()
    }

    fun endSelection() {
        _selected.value = null
    }

    private val _rescanning = MutableStateFlow<RescanProgress?>(null)
    /** Progress of a running rescan; null when none runs. */
    val rescanning: StateFlow<RescanProgress?> = _rescanning

    /** Asks Android to look at the chosen folders again; a second call while one runs is ignored. */
    fun rescan(done: () -> Unit) {
        if (_rescanning.value != null) return
        _rescanning.value = RescanProgress(0, 0)
        Rescan.folders(
            getApplication(),
            settings.value?.folders.orEmpty(),
            progress = { _rescanning.value = it },
            done = {
                _rescanning.value = null
                done()
            },
        )
    }

    // Settings: music folders. A chosen folder includes its subfolders, except those left out.

    /** Adds a folder picked in Settings; a folder already in changes nothing. Returns false then. */
    fun addFolder(path: String): Boolean {
        val now = settings.value?.choice ?: return false
        val next = now.withAdded(path) ?: return false
        viewModelScope.launch { store.setFolders(next) }
        return true
    }

    /** Stops using a chosen folder, or leaves out a subfolder a chosen parent brings in. */
    fun removeFolder(path: String) {
        val now = settings.value?.choice ?: return
        viewModelScope.launch { store.setFolders(now.withOff(path)) }
    }

    /** Takes a left-out folder back in; it stays listed, so it can be left out again. */
    fun addBack(path: String) {
        val now = settings.value?.choice ?: return
        viewModelScope.launch { store.setFolders(FolderChoice(now.chosen + path, now.leftOut - path)) }
    }

    /** Puts back the folders as they were before a change (Undo). */
    fun restoreFolders(choice: FolderChoice) {
        viewModelScope.launch { store.setFolders(choice) }
    }
}
