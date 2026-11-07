package net.codekunde.fernplay.android.ui.settings

import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.codekunde.fernplay.android.R
import net.codekunde.fernplay.android.library.MusicFolders
import net.codekunde.fernplay.android.library.RescanProgress
import net.codekunde.fernplay.android.ui.LibraryViewModel
import net.codekunde.fernplay.android.ui.components.EmptyNote
import net.codekunde.fernplay.android.ui.components.FernIconButton
import net.codekunde.fernplay.android.ui.components.FernSnackbarHost
import net.codekunde.fernplay.android.ui.components.FernTopBar
import net.codekunde.fernplay.android.ui.components.SectionLabel
import net.codekunde.fernplay.android.ui.firstrun.folderLabel
import net.codekunde.fernplay.android.ui.theme.Fernplay
import net.codekunde.fernplay.android.ui.theme.ThemeChoice
import java.text.NumberFormat

/** Settings: the music folders (remove, add, rescan) and the theme. */
@Composable
fun SettingsScreen(vm: LibraryViewModel, onBack: () -> Unit) {
    val c = Fernplay.colors
    val settings by vm.settings.collectAsStateWithLifecycle()
    val allSongs by vm.allSongs.collectAsStateWithLifecycle()
    val rescanning by vm.rescanning.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val s = settings ?: return
    val folders = s.folders.sortedBy { it.lowercase() }

    fun note(text: String) = scope.launch {
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar(text)
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val path = MusicFolders.fromTreeDocumentId(DocumentsContract.getTreeDocumentId(uri))
            ?: return@rememberLauncherForActivityResult
        if (vm.addFolder(path)) {
            note("Added ${folderLabel(path)}")
        } else {
            val by = s.folders.filter { MusicFolders.includes(it, path) }.minByOrNull { it.length }
            note(if (by == null || by == path) "${folderLabel(path)} is already in your folders" else "Already included in ${folderLabel(by)}")
        }
    }

    fun remove(path: String) {
        val before = s.folders
        vm.removeFolder(path)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar("Stopped using ${folderLabel(path)}", actionLabel = "Undo", duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) vm.restoreFolders(before)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            FernTopBar(title = "Settings", onBack = onBack)
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                item(key = "folders-label") { SectionLabel("Music folders") }
                if (folders.isEmpty()) {
                    item(key = "no-folders") { EmptyNote("No folders chosen, so no songs show. Add the folder your music is in.") }
                }
                items(folders, key = { "f:$it" }) { path ->
                    val count = allSongs?.count { MusicFolders.includes(path, it.folder) }
                    // A folder inside another chosen one is shown as such; removing it alone would change nothing.
                    val parent = folders.filter { it != path && MusicFolders.includes(it, path) }.minByOrNull { it.length }
                    FolderRow(path, count, includedBy = parent, onRemove = { remove(path) })
                }
                item(key = "add") {
                    AddAndRescanRow(
                        rescanning = rescanning,
                        canRescan = folders.isNotEmpty(),
                        onAdd = { pickFolder.launch(null) },
                        onRescan = { vm.rescan { note("Folders checked") } },
                    )
                }
                item(key = "rescan-progress") { rescanning?.let { RescanBar(it) } }
                item(key = "folders-note") {
                    Text(
                        "New files in these folders appear by themselves. You never need to pick them again.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                        color = c.textDim,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                    )
                }
                item(key = "theme-label") { SectionLabel("Theme") }
                item(key = "theme") { ThemeChooser(s.theme, vm::setTheme) }
            }
        }
        FernSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun FolderRow(path: String, songCount: Int?, includedBy: String?, onRemove: () -> Unit) {
    val c = Fernplay.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(painterResource(R.drawable.ic_folder), contentDescription = null, tint = c.accentLight)
        Column(Modifier.weight(1f)) {
            Text(folderLabel(path), style = MaterialTheme.typography.bodyLarge, color = c.text)
            val count = when (songCount) {
                null -> "Counting…"
                0 -> "No songs indexed here yet"
                1 -> "1 song"
                else -> "${NumberFormat.getIntegerInstance().format(songCount)} songs"
            }
            Text(
                if (includedBy != null) "$count · in ${folderLabel(includedBy)}" else count,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textDim,
            )
        }
        if (includedBy == null) {
            FernIconButton(R.drawable.ic_close, "Stop using ${folderLabel(path)}", onRemove, tint = c.textDim, iconSize = 20.dp)
        } else {
            Box(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun AddAndRescanRow(rescanning: RescanProgress?, canRescan: Boolean, onAdd: () -> Unit, onRescan: () -> Unit) {
    val c = Fernplay.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onAdd)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = c.accentLight)
            Text("Add folder", style = MaterialTheme.typography.titleMedium, color = c.accentLight)
        }
        OutlinedButton(
            onClick = onRescan,
            enabled = canRescan && rescanning == null,
            modifier = Modifier.height(48.dp),
            border = BorderStroke(1.5.dp, c.outline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.text),
        ) { Text(if (rescanning != null) "Checking…" else "Rescan", fontWeight = FontWeight.Medium) }
    }
}

@Composable
private fun RescanBar(p: RescanProgress) {
    val c = Fernplay.colors
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Track as the design's slider track: outline at 60 %, visible on both backgrounds.
        val track = c.outline.copy(alpha = 0.6f)
        if (p.total == 0) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accent, trackColor = track)
        } else {
            LinearProgressIndicator({ p.fraction }, Modifier.fillMaxWidth(), color = c.accent, trackColor = track)
        }
        Text(
            if (p.total == 0) "Looking through your folders…" else "Checked ${p.done} of ${p.total}",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textDim,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** System / Dark / Light as one segmented control, as the design draws it. */
@Composable
private fun ThemeChooser(current: ThemeChoice, onChoose: (ThemeChoice) -> Unit) {
    val c = Fernplay.colors
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .border(1.5.dp, c.outline, shape)
            .selectableGroup(),
    ) {
        ThemeChoice.entries.forEach { choice ->
            val on = choice == current
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (on) c.accentContainer else c.background.copy(alpha = 0f))
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onChoose(choice) }),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (on) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        tint = c.onAccentContainer,
                        modifier = Modifier.padding(end = 6.dp).size(18.dp),
                    )
                }
                Text(
                    choice.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (on) c.onAccentContainer else c.text,
                )
            }
        }
    }
}
