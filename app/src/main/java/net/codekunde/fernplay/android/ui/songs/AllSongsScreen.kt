package net.codekunde.fernplay.android.ui.songs

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import net.codekunde.fernplay.android.R
import net.codekunde.fernplay.android.data.PlaylistRules
import net.codekunde.fernplay.android.library.Song
import net.codekunde.fernplay.android.library.SongList
import net.codekunde.fernplay.android.ui.components.ButtonKind
import net.codekunde.fernplay.android.ui.components.FernButton
import net.codekunde.fernplay.android.ui.components.FernSnackbarHost
import net.codekunde.fernplay.android.ui.components.FernMenuItem
import net.codekunde.fernplay.android.ui.playlists.AddToPlaylistSheet
import net.codekunde.fernplay.android.library.SongSort
import net.codekunde.fernplay.android.playback.PlayerConnection
import net.codekunde.fernplay.android.ui.LibraryViewModel
import net.codekunde.fernplay.android.ui.components.EmptyState
import net.codekunde.fernplay.android.ui.components.FernIconButton
import net.codekunde.fernplay.android.ui.components.FernTopBar
import net.codekunde.fernplay.android.ui.components.SongRow
import net.codekunde.fernplay.android.ui.theme.Fernplay
import java.text.NumberFormat

/**
 * Every song in the library. Long-press starts selecting (Add to playlist,
 * Queue). With [addTo] set (a playlist's "Add songs"), it opens in selection
 * mode and adds the ticked songs to that playlist.
 */
@Composable
fun AllSongsScreen(vm: LibraryViewModel, player: PlayerConnection, onBack: () -> Unit, addTo: Long? = null) {
    val c = Fernplay.colors
    val view by vm.songList.collectAsStateWithLifecycle()
    val total by vm.librarySongs.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val playingId by player.currentMediaId
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var sheetFor by remember { mutableStateOf<List<Song>?>(null) }
    val target by remember(addTo) { addTo?.let { vm.playlists.view(it) } ?: flowOf(null) }.collectAsStateWithLifecycle(null)

    // "Add songs" from a playlist starts with ticks; leaving the screen always ends selecting.
    DisposableEffect(addTo) {
        if (addTo != null) vm.startSelection()
        onDispose { vm.endSelection() }
    }
    BackHandler(enabled = selected != null && addTo == null) { vm.endSelection() }

    fun selectedSongs(): List<Song> {
        val ids = selected.orEmpty()
        return SongList.sorted(total.orEmpty().filter { it.id in ids }, sort)
    }
    fun note(text: String) = scope.launch {
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar(text)
    }
    // A new sort or search starts at the top, not at whichever song was on top before.
    // Coming back to this screen keeps the place.
    var shownFor by rememberSaveable { mutableStateOf("$sort|$query") }
    LaunchedEffect(sort, query) {
        val now = "$sort|$query"
        if (now != shownFor) {
            shownFor = now
            listState.scrollToItem(0)
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        val sel = selected
        if (sel == null) {
            FernTopBar(title = "All songs", onBack = onBack) {
                total?.let {
                    Text(
                        NumberFormat.getIntegerInstance().format(it.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textDim,
                        modifier = Modifier.padding(end = 12.dp).semantics { contentDescription = "${it.size} songs" },
                    )
                }
            }
        } else {
            SelectionBar(
                count = sel.size,
                title = target?.let { "Add to ${it.name}" },
                onCancel = { if (addTo != null) onBack() else vm.endSelection() },
                onSelectAll = { vm.selectAll(view?.songs.orEmpty().map { it.id }) },
            )
        }
        SearchField(query, onChange = { vm.query.value = it })
        SortChips(sort, onSort = { vm.sort.value = it })

        val v = view
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                v == null -> Unit // first read: takes a few milliseconds
                total.isNullOrEmpty() -> EmptyState(
                    icon = R.drawable.ic_music,
                    title = "No songs yet",
                    text = "Songs copied into your music folders show up here by themselves.",
                    modifier = Modifier.fillMaxSize(),
                )
                v.songs.isEmpty() -> EmptyState(
                    icon = R.drawable.ic_search,
                    title = "Nothing matches",
                    text = "No song, artist or album contains “${query.trim()}”.",
                    modifier = Modifier.fillMaxSize(),
                ) {
                    OutlinedButton(onClick = { vm.query.value = "" }) { Text("Clear search") }
                }
                else -> SongListWithStrip(
                    listState, v.songs, v.letters, sort, playingId,
                    selected = sel,
                    onPlay = { index -> player.play(v.songs, index, queueName = "All songs") },
                    onToggle = { id -> vm.toggleSelected(id, keepEmpty = addTo != null) },
                    onLongPress = { id -> if (sel == null) vm.startSelection(id) else vm.toggleSelected(id, keepEmpty = addTo != null) },
                    onPlayNext = { song ->
                        player.playNext(listOf(song), "All songs")
                        note("“${song.title}” plays next")
                    },
                    onAddToPlaylist = { song -> sheetFor = listOf(song) },
                )
            }
        }

        if (sel != null) {
            SelectionActions {
                val n = sel.size
                if (addTo != null) {
                    FernButton(
                        if (n == 0) "Tick songs to add" else "Add ${PlaylistRules.songCount(n)}",
                        {
                            scope.launch {
                                vm.playlists.add(addTo, selectedSongs())
                                onBack()
                            }
                        },
                        Modifier.weight(1f),
                        icon = R.drawable.ic_playlist_add,
                        enabled = n > 0,
                    )
                } else {
                    FernButton("Add to playlist", { sheetFor = selectedSongs() }, Modifier.weight(1f), icon = R.drawable.ic_playlist_add, enabled = n > 0)
                    FernButton(
                        "Queue",
                        {
                            val songs = selectedSongs()
                            player.playNext(songs, "All songs")
                            vm.endSelection()
                            note(if (songs.size == 1) "Plays next" else "${songs.size} songs play next")
                        },
                        icon = R.drawable.ic_queue,
                        kind = ButtonKind.Secondary,
                        enabled = n > 0,
                    )
                }
            }
        }
    }
    FernSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (selected != null) 88.dp else 0.dp))
    }

    sheetFor?.let { songs ->
        AddToPlaylistSheet(
            vm.playlists,
            songs,
            onDismiss = { sheetFor = null },
            onDone = { message ->
                sheetFor = null
                vm.endSelection()
                note(message)
            },
        )
    }
}

/** Replaces the top bar while selecting: Cancel, how many, Select all. */
@Composable
private fun SelectionBar(count: Int, title: String?, onCancel: () -> Unit, onSelectAll: () -> Unit) {
    val c = Fernplay.colors
    Row(
        Modifier.fillMaxWidth().height(64.dp).background(c.accentContainer).padding(start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FernIconButton(R.drawable.ic_close, "Cancel selection", onCancel, tint = c.onAccentContainer)
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = c.onAccentContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "$count selected",
                style = MaterialTheme.typography.headlineSmall.let { if (title != null) it.copy(fontSize = 18.sp, lineHeight = 22.sp) else it },
                color = c.onAccentContainer,
                modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
            )
        }
        FernIconButton(R.drawable.ic_check, "Select all", onSelectAll, tint = c.onAccentContainer)
    }
}

/** Bottom bar while selecting. */
@Composable
private fun SelectionActions(content: @Composable RowScope.() -> Unit) {
    val c = Fernplay.colors
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(c.surface)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    val c = Fernplay.colors
    val focus = LocalFocusManager.current
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(c.surface)
            .padding(start = 16.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = c.textDim)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Search songs, artists, albums", style = MaterialTheme.typography.bodyLarge, color = c.textDim)
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search songs, artists, albums" },
            )
        }
        if (query.isNotEmpty()) FernIconButton(R.drawable.ic_close, "Clear search", { onChange("") }, tint = c.textDim)
    }
}

@Composable
private fun SortChips(sort: SongSort, onSort: (SongSort) -> Unit) {
    val c = Fernplay.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SongSort.entries.forEach { option ->
            val selected = option == sort
            FilterChip(
                selected = selected,
                onClick = { onSort(option) },
                label = { Text(option.label, fontWeight = FontWeight.Medium) },
                leadingIcon = if (selected) {
                    { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(40.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = c.background,
                    labelColor = c.text,
                    selectedContainerColor = c.accentContainer,
                    selectedLabelColor = c.onAccentContainer,
                    selectedLeadingIconColor = c.onAccentContainer,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = c.outline,
                    selectedBorderColor = c.accentContainer,
                ),
            )
        }
    }
}

@Composable
private fun SongListWithStrip(
    listState: LazyListState,
    songs: List<net.codekunde.fernplay.android.library.Song>,
    letters: Map<Char, Int>,
    sort: SongSort,
    playingId: String?,
    selected: Set<Long>?,
    onPlay: (Int) -> Unit,
    onToggle: (Long) -> Unit,
    onLongPress: (Long) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val showStrip = letters.isNotEmpty() && songs.size > 1
    val activeLetter by remember(songs, sort) {
        derivedStateOf {
            songs.getOrNull(listState.firstVisibleItemIndex)?.let { SongList.letterOf(SongList.keyOf(it, sort)) }
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                if (selected != null) {
                    SelectableSongRow(
                        song,
                        checked = song.id in selected,
                        onToggle = { onToggle(song.id) },
                        endPadding = if (showStrip) 34.dp else 16.dp,
                    )
                } else {
                    SongRow(
                        song = song,
                        playing = song.id.toString() == playingId,
                        onClick = { onPlay(index) },
                        // With the strip, ⋮ stays clear of its 48 dp touch zone.
                        endPadding = if (showStrip) 60.dp else 16.dp,
                        onLongClick = { onLongPress(song.id) },
                        menu = { close ->
                            FernMenuItem("Play next", R.drawable.ic_queue) { close(); onPlayNext(song) }
                            FernMenuItem("Add to playlist", R.drawable.ic_playlist_add) { close(); onAddToPlaylist(song) }
                            FernMenuItem("Select", R.drawable.ic_check) { close(); onLongPress(song.id) }
                        },
                    )
                }
            }
        }
        if (showStrip) {
            AzStrip(
                active = activeLetter,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp),
                onLetter = { letter -> letters[letter]?.let { scope.launch { listState.scrollToItem(it) } } },
            )
        }
    }
}

/** A song while selecting: a round tick in place of the art; the whole row toggles. */
@Composable
private fun SelectableSongRow(song: Song, checked: Boolean, onToggle: () -> Unit, endPadding: androidx.compose.ui.unit.Dp) {
    val c = Fernplay.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .background(if (checked) c.selected else c.background.copy(alpha = 0f))
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(start = 16.dp, end = endPadding, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (checked) c.accent else c.background.copy(alpha = 0f))
                    .border(2.dp, if (checked) c.accent else c.textDim, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = c.onAccent, modifier = Modifier.size(18.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${song.artist} · ${SongList.duration(song.durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The A–Z strip: 26 dp of letters inside a 48 dp touch zone. Tap or drag to
 * jump. Hidden from screen readers, which scroll the list directly.
 */
@Composable
private fun AzStrip(active: Char?, modifier: Modifier, onLetter: (Char) -> Unit) {
    val c = Fernplay.colors
    var height by remember { mutableIntStateOf(1) }
    val letters = SongList.STRIP
    fun letterAt(y: Float): Char = letters[((y / height) * letters.size).toInt().coerceIn(0, letters.lastIndex)]
    Box(
        modifier
            .width(48.dp)
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .clearAndSetSemantics { }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    onLetter(letterAt(down.position.y))
                    drag(down.id) { change ->
                        onLetter(letterAt(change.position.y))
                        change.consume()
                    }
                }
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            Modifier.width(26.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { letter ->
                val on = letter == active
                Box(
                    Modifier.size(width = 24.dp, height = 16.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (on) c.accent else c.background.copy(alpha = 0f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        letter.toString(),
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (on) c.onAccent else c.textDim,
                    )
                }
            }
        }
    }
}
