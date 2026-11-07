package net.codekunde.fernplay.android.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.codekunde.fernplay.android.R
import net.codekunde.fernplay.android.data.PlaylistRules
import net.codekunde.fernplay.android.data.PlaylistStore
import net.codekunde.fernplay.android.data.PlaylistSummary
import net.codekunde.fernplay.android.ui.components.ArtTile
import net.codekunde.fernplay.android.ui.components.FernIconButton
import net.codekunde.fernplay.android.ui.theme.Fernplay

/** A playlist on Home: tap the row to open it, ▶ to play it at once. */
@Composable
fun PlaylistRow(p: PlaylistSummary, playing: Boolean, onOpen: () -> Unit, onPlay: () -> Unit) {
    val c = Fernplay.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 64.dp)
                .clickable(onClickLabel = "Open", onClick = onOpen)
                .then(if (playing) Modifier.semantics { stateDescription = "Playing" } else Modifier)
                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ArtTile(p.id, icon = if (playing) R.drawable.ic_now_playing else R.drawable.ic_queue)
            Column(Modifier.weight(1f)) {
                Text(
                    p.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (!playing) c.text else if (c.isDark) c.title else c.accent,
                    fontWeight = if (playing) FontWeight.Medium else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    PlaylistRules.summary(p.songCount, p.totalMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textDim,
                    maxLines = 1,
                )
            }
        }
        if (p.songCount > 0) FernIconButton(R.drawable.ic_play, "Play ${p.name}", onPlay)
    }
}

/** Asks for a name and creates the playlist; [onCreated] gets its id. */
@Composable
fun NewPlaylistDialog(store: PlaylistStore, onDismiss: () -> Unit, onCreated: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    fun submit() {
        scope.launch {
            problem = store.nameProblem(name)
            if (problem == null) store.create(name)?.let(onCreated)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(PlaylistRules.MAX_NAME + 1); problem = null },
                label = { Text("Name") },
                singleLine = true,
                isError = problem != null,
                supportingText = { Text(problem ?: "Playlists live in Fernplay: no folders, no files.") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = ::submit, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Asks for a new name; the old one is filled in and selected. [onRenamed] gets the new name. */
@Composable
fun RenamePlaylistDialog(store: PlaylistStore, id: Long, current: String, onDismiss: () -> Unit, onRenamed: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var field by remember {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(current, androidx.compose.ui.text.TextRange(0, current.length)))
    }
    var problem by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    fun submit() {
        val name = field.text
        if (name.trim() == current) return onDismiss()
        scope.launch {
            problem = store.renameProblem(id, name)
            if (problem == null && store.rename(id, name)) onRenamed(name.trim())
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename playlist") },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it.copy(text = it.text.take(PlaylistRules.MAX_NAME + 1)); problem = null },
                label = { Text("Name") },
                singleLine = true,
                isError = problem != null,
                supportingText = problem?.let { p -> { Text(p) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = ::submit, enabled = field.text.isNotBlank()) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Delete asks first (design rule: nothing destructive on one tap). Songs stay on the phone. */
@Composable
fun DeletePlaylistDialog(name: String, songCount: Int, onDismiss: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete “$name”?") },
        text = {
            Text(
                when (songCount) {
                    0 -> "The playlist goes. Nothing else changes."
                    1 -> "The playlist goes. Its song stays on your phone and in All songs."
                    else -> "The playlist and its order go. The $songCount songs stay on your phone and in All songs."
                }
            )
        },
        confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = Fernplay.colors.amber) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The "+ New playlist" row under the playlists. */
@Composable
fun NewPlaylistRow(onClick: () -> Unit) {
    val c = Fernplay.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        androidx.compose.material3.Icon(
            androidx.compose.ui.res.painterResource(R.drawable.ic_add),
            contentDescription = null,
            tint = c.accentLight,
        )
        Text("New playlist", style = MaterialTheme.typography.titleMedium, color = c.accentLight)
    }
}
