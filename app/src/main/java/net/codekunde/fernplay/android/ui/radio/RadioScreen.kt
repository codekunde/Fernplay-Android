package net.codekunde.fernplay.android.ui.radio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.codekunde.fernplay.android.R
import net.codekunde.fernplay.android.data.StationEntity
import net.codekunde.fernplay.android.data.StationFiles
import net.codekunde.fernplay.android.data.StationStore
import net.codekunde.fernplay.android.ui.LibraryViewModel
import net.codekunde.fernplay.android.ui.components.ArtTile
import net.codekunde.fernplay.android.ui.components.EmptyState
import net.codekunde.fernplay.android.ui.components.FernButton
import net.codekunde.fernplay.android.ui.components.FernIconButton
import net.codekunde.fernplay.android.ui.components.FernMenu
import net.codekunde.fernplay.android.ui.components.FernMenuItem
import net.codekunde.fernplay.android.ui.components.FernSnackbarHost
import net.codekunde.fernplay.android.ui.components.FernTopBar
import net.codekunde.fernplay.android.ui.theme.Fernplay
import java.net.URI

/** Radio: the stations added from .m3u / .m3u8 files; add more, rename, delete with Undo. */
@Composable
fun RadioScreen(vm: LibraryViewModel, onBack: () -> Unit) {
    val store = vm.stations
    val stations by store.stations.collectAsStateWithLifecycle()
    val imported by vm.stationImport.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var renaming by remember { mutableStateOf<StationEntity?>(null) }
    // Every file type: providers label .m3u files in different ways; a wrong file gets a clear message.
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importStations(uri, fromOtherApp = false)
    }
    val addFromFile = { pick.launch(arrayOf("*/*")) }

    LaunchedEffect(imported) {
        val r = imported ?: return@LaunchedEffect
        // Clearing the result restarts this effect, so the message runs in the screen's scope.
        vm.stationImportShown()
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(r.message, duration = SnackbarDuration.Long)
        }
    }

    fun delete(s: StationEntity) {
        val gone = store.delete(s)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar("Deleted “${gone.name}”", actionLabel = "Undo", duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) store.undoDelete(gone)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            FernTopBar(title = "Radio", onBack = onBack) {
                FernIconButton(R.drawable.ic_add, "Add stations from a playlist file", addFromFile)
            }
            val list = stations ?: return@Column // a few milliseconds
            if (list.isEmpty()) {
                EmptyState(
                    icon = R.drawable.ic_radio,
                    title = "No stations yet",
                    text = "Add stations from an .m3u or .m3u8 playlist file, the kind radio websites offer for download. " +
                        "You can also open such a file with Fernplay from your file manager.",
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    FernButton("Add from playlist file", addFromFile, icon = R.drawable.ic_add, modifier = Modifier.padding(top = 8.dp))
                }
                return@Column
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(list, key = { it.id }) { s ->
                    StationRow(s, onRename = { renaming = s }, onDelete = { delete(s) })
                }
                item(key = "add") { AddFromFileRow(addFromFile) }
            }
        }
        FernSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }

    renaming?.let { s ->
        RenameStationDialog(store, s, onDismiss = { renaming = null }, onRenamed = { renaming = null })
    }
}

@Composable
private fun StationRow(s: StationEntity, onRename: () -> Unit, onDelete: () -> Unit) {
    val c = Fernplay.colors
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ArtTile(s.id, icon = R.drawable.ic_radio)
        Column(Modifier.weight(1f)) {
            Text(s.name, style = MaterialTheme.typography.bodyLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                address(s.url),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            FernIconButton(R.drawable.ic_more, "More for ${s.name}", { menuOpen = true }, tint = c.textDim)
            FernMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
                FernMenuItem("Rename", R.drawable.ic_edit) { menuOpen = false; onRename() }
                FernMenuItem("Delete", R.drawable.ic_delete) { menuOpen = false; onDelete() }
            }
        }
    }
}

/** The server's name, enough to tell two streams apart: "stream.example.org". */
private fun address(url: String): String = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.") ?: url

@Composable
private fun AddFromFileRow(onClick: () -> Unit) {
    val c = Fernplay.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = c.accentLight)
        Text("Add from playlist file", style = MaterialTheme.typography.titleMedium, color = c.accentLight)
    }
}

/** Asks for a new name; the old one is filled in and selected. */
@Composable
private fun RenameStationDialog(store: StationStore, s: StationEntity, onDismiss: () -> Unit, onRenamed: () -> Unit) {
    val scope = rememberCoroutineScope()
    var field by remember { mutableStateOf(TextFieldValue(s.name, TextRange(0, s.name.length))) }
    var problem by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    fun submit() {
        if (field.text.trim() == s.name) return onDismiss()
        problem = store.nameProblem(field.text)
        if (problem == null) scope.launch { if (store.rename(s.id, field.text)) onRenamed() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename station") },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it.copy(text = it.text.take(StationFiles.MAX_NAME + 1)); problem = null },
                label = { Text("Name") },
                singleLine = true,
                isError = problem != null,
                supportingText = problem?.let { p -> { Text(p) } },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = ::submit, enabled = field.text.isNotBlank()) { Text("Rename") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}
