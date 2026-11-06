package net.codekunde.fernplay.android.ui.firstrun

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.codekunde.fernplay.android.R
import net.codekunde.fernplay.android.library.MusicFolder
import net.codekunde.fernplay.android.library.MusicFolders
import net.codekunde.fernplay.android.ui.LibraryViewModel
import net.codekunde.fernplay.android.ui.components.SectionLabel
import net.codekunde.fernplay.android.ui.theme.Fernplay
import java.text.NumberFormat

/** "Download/Albums" -> "Download / Albums", as the design writes folder names. */
fun folderLabel(path: String): String = if (path.isEmpty()) "Phone storage" else path.replace("/", " / ")

private fun songs(n: Int): String = if (n == 1) "1 song" else "${NumberFormat.getIntegerInstance().format(n)} songs"

@Composable
fun FirstRunScreen(vm: LibraryViewModel, onDone: () -> Unit) {
    val granted by vm.hasPermission.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Header()
        if (granted) FolderChooser(vm, onDone) else AskPermission(vm)
    }
}

@Composable
private fun Header() {
    val c = Fernplay.colors
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 8.dp)) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(c.lcdBackground)
                .border(2.dp, c.surface, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_fern_line), contentDescription = null, tint = c.lcd, modifier = Modifier.size(32.dp))
        }
        Text(
            "Where is your music?",
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = MaterialTheme.typography.headlineSmall.fontSize * 1.36f),
            color = c.title,
            modifier = Modifier.padding(top = 20.dp).semantics { heading() },
        )
        Text(
            "Pick your folders once. Fernplay remembers them and finds new songs by itself. You will not be asked again.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.textDim,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun AskPermission(vm: LibraryViewModel) {
    val c = Fernplay.colors
    val context = LocalContext.current
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        vm.refreshPermission()
        denied = !ok
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (denied) "Fernplay needs access to your music to list your songs. Nothing leaves your phone."
            else "First, let Fernplay see the music on this phone. Nothing leaves your phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.text,
        )
        Button(
            onClick = { launcher.launch(vm.musicPermission) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
        ) { Text("Allow access to music") }
        if (denied) {
            // After two refusals Android stops asking; access can still be given in its settings.
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Open Fernplay in Android settings") }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.FolderChooser(vm: LibraryViewModel, onDone: () -> Unit) {
    val c = Fernplay.colors
    val folders by vm.folders.collectAsStateWithLifecycle()
    val pickedState by vm.picked.collectAsStateWithLifecycle()
    val extra by vm.extraFolders.collectAsStateWithLifecycle()
    val found = folders
    val picked = pickedState ?: found?.map { it.path }?.toSet() ?: emptySet()

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val path = MusicFolders.fromTreeDocumentId(DocumentsContract.getTreeDocumentId(uri))
            if (path != null) vm.addPickedFolder(path, picked)
        }
    }

    val rows: List<MusicFolder> = (found.orEmpty() + extra.filter { e -> found.orEmpty().none { it.path == e } }.map { MusicFolder(it, 0) })
    val songCount = remember(rows, picked) {
        found.orEmpty().filter { MusicFolders.includedByAny(picked, it.path) }.sumOf { it.songCount }
    }

    LazyColumn(Modifier.weight(1f)) {
        item { SectionLabel("Found on this phone", Modifier.padding(top = 6.dp)) }
        when {
            found == null -> item {
                Text("Looking for music…", color = c.textDim, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            }
            rows.isEmpty() -> item {
                Text(
                    "No music found on this phone yet. Copy songs into the Music folder, or choose a folder below.",
                    color = c.textDim,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(rows, key = { it.path }) { folder ->
            // A ticked parent already includes this folder: show it ticked and locked.
            val parent = picked.filter { it != folder.path && MusicFolders.includes(it, folder.path) }.minByOrNull { it.length }
            FolderRow(
                folder,
                checked = parent != null || folder.path in picked,
                includedBy = parent,
                onToggle = { vm.togglePicked(folder.path, picked) },
            )
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .heightIn(min = 56.dp)
                    .clickable(role = Role.Button, onClick = { pickFolder.launch(null) })
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null, tint = c.accentLight)
                Text("Choose another folder", style = MaterialTheme.typography.titleMedium, color = c.accentLight)
            }
        }
    }
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
        Button(
            onClick = { onDone(); vm.finishSetup(picked) },
            enabled = picked.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = c.onAccent),
        ) {
            Text(if (picked.isEmpty()) "Choose at least one folder" else "Scan ${songs(songCount)}")
        }
        Text(
            "Folders can be changed later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
    }
}

@Composable
private fun FolderRow(folder: MusicFolder, checked: Boolean, includedBy: String?, onToggle: () -> Unit) {
    val c = Fernplay.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) c.selected else c.background)
            .heightIn(min = 64.dp)
            .toggleable(value = checked, enabled = includedBy == null, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) c.accent else c.background)
                .border(2.dp, if (checked) c.accent else c.textDim, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = c.onAccent, modifier = Modifier.size(18.dp))
        }
        Icon(painterResource(R.drawable.ic_folder), contentDescription = null, tint = if (checked) c.accentLight else c.textDim)
        Column(Modifier.weight(1f)) {
            Text(folderLabel(folder.path), style = MaterialTheme.typography.bodyLarge, color = c.text)
            val count = if (folder.songCount == 0) "No songs indexed here yet" else songs(folder.songCount)
            Text(
                if (includedBy != null) "$count · in ${folderLabel(includedBy)}" else count,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textDim,
            )
        }
        Spacer(Modifier.size(0.dp))
    }
}
