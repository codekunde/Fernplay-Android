package net.codekunde.fernplay.android.ui.remote

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.codekunde.fernplay.android.R
import net.codekunde.fernplay.android.library.SongList
import net.codekunde.fernplay.android.playback.NowPlayingText
import net.codekunde.fernplay.android.playback.RepeatState
import net.codekunde.fernplay.android.remote.Link
import net.codekunde.fernplay.android.remote.PairTarget
import net.codekunde.fernplay.android.remote.PairedPc
import net.codekunde.fernplay.android.remote.Pairing
import net.codekunde.fernplay.android.remote.PcRepeat
import net.codekunde.fernplay.android.remote.PcState
import net.codekunde.fernplay.android.remote.PcTrack
import net.codekunde.fernplay.android.remote.PlayState
import net.codekunde.fernplay.android.remote.RemoteException.Reason
import net.codekunde.fernplay.android.remote.RemoteProtocol
import net.codekunde.fernplay.android.remote.RemoteViewModel
import net.codekunde.fernplay.android.ui.components.ButtonKind
import net.codekunde.fernplay.android.ui.components.EmptyNote
import net.codekunde.fernplay.android.ui.components.FernButton
import net.codekunde.fernplay.android.ui.components.FernIconButton
import net.codekunde.fernplay.android.ui.components.FernMenu
import net.codekunde.fernplay.android.ui.components.FernMenuItem
import net.codekunde.fernplay.android.ui.components.FernTopBar
import net.codekunde.fernplay.android.ui.components.NavRow
import net.codekunde.fernplay.android.ui.components.SectionLabel
import net.codekunde.fernplay.android.ui.player.LcdGlow
import net.codekunde.fernplay.android.ui.player.LcdPanel
import net.codekunde.fernplay.android.ui.player.PadButton
import net.codekunde.fernplay.android.ui.player.RepeatToggle
import net.codekunde.fernplay.android.ui.player.SeekBar
import net.codekunde.fernplay.android.ui.theme.Fernplay
import net.codekunde.fernplay.android.ui.theme.LcdText

/**
 * "Fernplay on your PC": pairs with desktop Fernplay on the same Wi-Fi and controls it. The phone's own
 * playback is untouched. Connected only while this screen is in front (v1).
 */
@Composable
fun RemoteScreen(onBack: () -> Unit, vm: RemoteViewModel = viewModel()) {
    val pcs by vm.pcs.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    // "Pair again" for a PC the phone knows: straight to its code, no search or typing.
    var again by remember { mutableStateOf<PairTarget?>(null) }
    val list = pcs ?: return
    if (list.isEmpty() || adding) {
        PairPane(
            vm,
            onBack = if (adding && list.isNotEmpty()) ({ adding = false; again = null }) else onBack,
            onPaired = { adding = false; again = null },
            first = again,
        )
    } else {
        ControlPane(
            vm,
            list,
            onBack,
            onAddPc = { adding = true },
            onPairAgain = { pc -> again = PairTarget(pc.name, pc.host, pc.port, null); adding = true },
        )
    }
}

// ================= Pairing =================

@Composable
private fun PairPane(vm: RemoteViewModel, onBack: () -> Unit, onPaired: () -> Unit, first: PairTarget? = null) {
    val c = Fernplay.colors
    val found by vm.found.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf(first) }
    var address by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.search() }

    Column(Modifier.fillMaxSize()) {
        FernTopBar(title = "Fernplay on your PC", onBack = onBack)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item(key = "intro") {
                Text(
                    "Control Fernplay on your PC from this phone. On the PC, open Settings, switch PHONE REMOTE on, " +
                        "choose PAIR A PHONE… and type its code here. Both need to be on the same Wi-Fi.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.textDim,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                )
            }
            item(key = "found-label") { SectionLabel("PCs on this Wi-Fi") }
            if (searching) {
                item(key = "searching") {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), color = c.accent, strokeWidth = 3.dp)
                        Text("Looking for PCs…", style = MaterialTheme.typography.bodyLarge, color = c.textDim)
                    }
                }
            }
            val pcs = found.orEmpty()
            items(pcs, key = { "f${it.fingerprint}" }) { pc ->
                NavRow(
                    title = pc.name,
                    subtitle = pc.host,
                    onClick = { target = PairTarget(pc.name, pc.host, pc.port, pc.fingerprint) },
                    leading = { PcTile() },
                )
            }
            if (!searching && found != null && pcs.isEmpty()) {
                item(key = "none") {
                    EmptyNote("No PC answered. Check that PHONE REMOTE is on in Fernplay's Settings, or type the PC's address below.")
                }
            }
            item(key = "again") {
                FernButton(
                    "Search again",
                    onClick = vm::search,
                    enabled = !searching,
                    kind = ButtonKind.Outline,
                    height = 48.dp,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
            }
            item(key = "typed-label") { SectionLabel("Or type the PC's address", Modifier.padding(top = 12.dp)) }
            item(key = "typed") {
                val parsed = RemoteProtocol.parseAddress(address)
                fun next() {
                    parsed?.let { (host, port) -> target = PairTarget(host, host, port, null) }
                }
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it.take(80) },
                        label = { Text("PC address") },
                        singleLine = true,
                        supportingText = { Text("Shown under PHONE REMOTE in the PC's Settings, like 192.168.1.20") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { next() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FernButton("Next", onClick = ::next, enabled = parsed != null, height = 48.dp)
                }
            }
            item(key = "end") { Box(Modifier.height(24.dp)) }
        }
    }

    target?.let { t ->
        CodeDialog(
            vm,
            t,
            onDismiss = { vm.clearPairing(); target = null },
            onPaired = { target = null; onPaired() },
        )
    }
}

@Composable
private fun CodeDialog(vm: RemoteViewModel, target: PairTarget, onDismiss: () -> Unit, onPaired: () -> Unit) {
    val pairing by vm.pairing.collectAsStateWithLifecycle()
    var code by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val working = pairing == Pairing.Working
    val problem = (pairing as? Pairing.Failed)?.reason?.let(::pairMessage)
    fun submit() {
        if (RemoteProtocol.cleanCode(code) != null) vm.pair(target, code, onPaired)
    }
    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Pair with ${target.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Type the 6-digit code that PAIR A PHONE… shows on the PC.")
                OutlinedTextField(
                    value = code,
                    onValueChange = { typed -> code = typed.filter(Char::isDigit).take(RemoteProtocol.CODE_LENGTH); vm.clearPairing() },
                    label = { Text("Code") },
                    singleLine = true,
                    enabled = !working,
                    isError = problem != null,
                    supportingText = problem?.let { p -> { Text(p, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 28.sp, letterSpacing = 6.sp, textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = ::submit, enabled = !working && RemoteProtocol.cleanCode(code) != null) {
                Text(if (working) "Pairing…" else "Pair")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text("Cancel") } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

private fun pairMessage(reason: Reason): String = when (reason) {
    Reason.WrongCode -> "That code is not right. Check the 6 digits on the PC."
    Reason.TooManyTries -> "Too many wrong codes. On the PC, choose PAIR A PHONE… again for a new code."
    Reason.Expired -> "The code ran out. On the PC, choose PAIR A PHONE… again."
    Reason.NotPairing -> "The PC is not pairing now. On the PC, choose PAIR A PHONE… first."
    Reason.Unreachable -> "Can't reach the PC. Check that PHONE REMOTE is on and both are on the same Wi-Fi."
    Reason.CertificateChanged -> "A different PC answered than the one found. Search again."
    Reason.Forgotten, Reason.Protocol -> "Fernplay on the PC did not answer as expected. Update Fernplay on the PC."
}

// ================= Remote control =================

@Composable
private fun ControlPane(
    vm: RemoteViewModel,
    pcs: List<PairedPc>,
    onBack: () -> Unit,
    onAddPc: () -> Unit,
    onPairAgain: (PairedPc) -> Unit,
) {
    val c = Fernplay.colors
    val pc = pcs.first()
    val link by vm.link.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val playlist by vm.playlist.collectAsStateWithLifecycle()
    val dragged by vm.draggedVolume.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var forgetting by remember { mutableStateOf(false) }
    var scrub by remember { mutableStateOf<Long?>(null) }

    // Connected only while this screen is in front; the app in the background lets go of the PC.
    LifecycleStartEffect(vm) {
        vm.start()
        onStopOrDispose { vm.stop() }
    }

    Column(Modifier.fillMaxSize()) {
        FernTopBar(title = pc.name, onBack = onBack) {
            Box {
                FernIconButton(R.drawable.ic_more, "More", { menuOpen = true })
                FernMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
                    pcs.drop(1).forEach { other ->
                        FernMenuItem("Switch to ${other.name}", R.drawable.ic_computer) { menuOpen = false; vm.use(other) }
                    }
                    FernMenuItem("Pair another PC", R.drawable.ic_add) { menuOpen = false; onAddPc() }
                    FernMenuItem("Forget this PC", R.drawable.ic_delete) { menuOpen = false; forgetting = true }
                }
            }
        }
        val s = state
        val connected = link == Link.Connected && s != null
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item(key = "lcd") {
                PcLcd(s, playlist.size, scrub, connected, Modifier.padding(horizontal = 16.dp).fillMaxWidth())
            }
            if (!connected) {
                item(key = "status") { LinkStatus(link, pc.name, onRetry = vm::reconnect, onPairAgain = { onPairAgain(pc) }) }
            } else {
                item(key = "controls") {
                    Controls(
                        s!!,
                        volume = dragged ?: s.volume,
                        onScrub = { scrub = it },
                        vm = vm,
                    )
                }
                item(key = "list-label") {
                    SectionLabel(if (playlist.size == 1) "Playlist · 1 track" else "Playlist · ${playlist.size} tracks", Modifier.padding(top = 8.dp))
                }
                if (playlist.isEmpty()) item(key = "list-empty") { EmptyNote("The PC's playlist is empty.") }
                items(playlist.size, key = { "t$it" }) { i ->
                    TrackRow(i, playlist[i], current = i == s.index, onPlay = { vm.playIndex(i) })
                }
            }
            item(key = "end") { Box(Modifier.height(16.dp)) }
        }
    }

    if (forgetting) {
        AlertDialog(
            onDismissRequest = { forgetting = false },
            title = { Text("Forget ${pc.name}?") },
            text = { Text("This phone stops controlling it. To remove the phone on the PC too, use REMOVE under PHONE REMOTE in its Settings.") },
            confirmButton = { TextButton(onClick = { forgetting = false; vm.forget(pc) }) { Text("Forget", color = c.text) } },
            dismissButton = { TextButton(onClick = { forgetting = false }) { Text("Cancel") } },
        )
    }
}

/** Title, artist, track number and the clock, in the app's LCD. */
@Composable
private fun PcLcd(s: PcState?, count: Int, scrub: Long?, connected: Boolean, modifier: Modifier) {
    val c = Fernplay.colors
    LcdPanel(modifier) {
        val title = s?.title ?: if (s != null) "Nothing loaded" else "Fernplay on your PC"
        Text(title, style = LcdText.title.copy(shadow = LcdGlow), color = if (connected) c.lcd else c.lcdSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val second = listOfNotNull(
            s?.artist?.uppercase(),
            s?.takeIf { it.index >= 0 && count > 0 }?.let { NowPlayingText.trackOf(it.index, count) },
            if (s?.play == PlayState.Paused) "PAUSED" else if (s?.play == PlayState.Stopped && s.title != null) "STOPPED" else null,
        ).joinToString(" · ")
        Text(
            second.ifEmpty { " " },
            style = LcdText.small.copy(fontSize = 15.sp, lineHeight = 20.sp),
            color = c.lcdSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val pos = scrub ?: s?.positionMs ?: 0L
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                if (s == null) "-:--" else NowPlayingText.elapsed(pos),
                style = LcdText.time.copy(shadow = LcdGlow),
                color = c.lcd,
                modifier = Modifier.weight(1f).alignByBaseline(),
            )
            Text(
                when {
                    s == null -> ""
                    s.station -> "RADIO"
                    else -> NowPlayingText.remaining(pos, s.lengthMs)
                },
                style = LcdText.small.copy(fontSize = 18.sp, lineHeight = 22.sp),
                color = c.lcdSoft,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

@Composable
private fun LinkStatus(link: Link, pcName: String, onRetry: () -> Unit, onPairAgain: () -> Unit) {
    val c = Fernplay.colors
    val (text, action) = when (link) {
        Link.Idle, Link.Connecting, Link.Connected -> "Connecting to $pcName…" to null
        is Link.Lost -> when (link.reason) {
            Reason.Forgotten -> "$pcName no longer knows this phone (it was removed in the PC's Settings). Pair again to control it." to "Pair again"
            Reason.CertificateChanged ->
                "$pcName answered with a different security certificate, so this phone did not connect. If Fernplay on the PC was reinstalled or reset, pair again." to "Pair again"
            Reason.Protocol -> "Fernplay on $pcName answered in a way this app does not understand. Update Fernplay on the PC or this phone." to "Retry"
            else -> "Can't reach $pcName. Check that the PC is on, PHONE REMOTE is on in Fernplay's Settings, and both are on the same Wi-Fi. Trying again…" to "Retry"
        }
    }
    Column(
        Modifier.padding(16.dp).fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (action == null) {
                CircularProgressIndicator(Modifier.size(24.dp), color = c.accent, strokeWidth = 3.dp)
            } else {
                Icon(painterResource(R.drawable.ic_warn), contentDescription = null, tint = c.amber)
            }
            Text(text, style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
        }
        if (action != null) {
            FernButton(action, onClick = if (action == "Pair again") onPairAgain else onRetry, height = 48.dp, kind = ButtonKind.Secondary)
        }
    }
}

@Composable
private fun Controls(s: PcState, volume: Float, onScrub: (Long?) -> Unit, vm: RemoteViewModel) {
    val c = Fernplay.colors
    Column(Modifier.fillMaxWidth()) {
        SeekBar(
            positionMs = s.positionMs,
            durationMs = if (s.canSeek) s.lengthMs else 0L,
            onSeek = vm::seekTo,
            onScrub = onScrub,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
        )
        val playing = s.play == PlayState.Playing
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PadButton(R.drawable.ic_prev, "Previous", vm::previous, Modifier.size(64.dp), 22.dp, iconSize = 36.dp)
            PadButton(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                if (playing) "Pause" else "Play",
                vm::playPause,
                Modifier.size(80.dp),
                28.dp,
                filled = true,
            )
            PadButton(R.drawable.ic_stop, "Stop", vm::stopPlayback, Modifier.size(64.dp), 22.dp, iconSize = 32.dp)
            PadButton(R.drawable.ic_next, "Next", vm::next, Modifier.size(64.dp), 22.dp, iconSize = 36.dp)
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(painterResource(R.drawable.ic_volume), contentDescription = null, tint = c.textDim)
            Slider(
                value = volume,
                onValueChange = { vm.setVolume(it) },
                onValueChangeFinished = vm::finishVolume,
                colors = SliderDefaults.colors(
                    thumbColor = c.title,
                    activeTrackColor = c.accent,
                    inactiveTrackColor = c.surface,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f).semantics { contentDescription = "PC volume" },
            )
            Text(
                "${(volume * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textDim,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 44.dp),
            )
        }
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeToggle(R.drawable.ic_shuffle, "Shuffle", s.shuffle, vm::setShuffle, Modifier.weight(1f))
            RepeatToggle(
                s.repeat.asRepeatState(),
                { vm.setRepeat(s.repeat.next()) },
                Modifier.weight(1.25f),
                fontSize = 11.sp,
            )
            ModeToggle(R.drawable.ic_crossfade, "Xfade", s.crossfade, vm::setCrossfade, Modifier.weight(1f), spoken = "Crossfade")
        }
    }
}

/** The PC's repeat in the app's own Repeat button: all = List, one = Track. */
private fun PcRepeat.asRepeatState(): RepeatState = when (this) {
    PcRepeat.Off -> RepeatState.Off
    PcRepeat.All -> RepeatState.List
    PcRepeat.One -> RepeatState.Track
}

/** Same order as the phone's own Repeat: Off -> List -> Track -> Off. */
private fun PcRepeat.next(): PcRepeat = when (this) {
    PcRepeat.Off -> PcRepeat.All
    PcRepeat.All -> PcRepeat.One
    PcRepeat.One -> PcRepeat.Off
}

/** An on/off mode beside Repeat: the word ON/OFF, the fill and the border all change; TalkBack hears a switch. */
@Composable
private fun ModeToggle(
    @DrawableRes icon: Int,
    label: String,
    on: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    spoken: String = label,
    height: Dp = 64.dp,
) {
    val c = Fernplay.colors
    val shape = RoundedCornerShape(20.dp)
    val (bg, border, fg) = if (on) Triple(c.accentContainer, c.accentLight, c.onAccentContainer) else Triple(Color.Transparent, c.outline, c.textDim)
    Column(
        modifier
            .height(height)
            .clip(shape)
            .background(bg)
            .border(2.dp, border, shape)
            .toggleable(value = on, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = spoken }
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
        Text(
            "${label.uppercase()} ${if (on) "ON" else "OFF"}",
            style = TextStyle(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = fg,
            maxLines = 1,
        )
    }
}

@Composable
private fun TrackRow(index: Int, track: PcTrack, current: Boolean, onPlay: () -> Unit) {
    val c = Fernplay.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClickLabel = "Play on the PC", onClick = onPlay)
            .then(if (current) Modifier.semantics { stateDescription = "Current track on the PC" } else Modifier)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.CenterEnd) {
            if (current) {
                Icon(painterResource(R.drawable.ic_now_playing), contentDescription = null, tint = if (c.isDark) c.title else c.accent, modifier = Modifier.size(20.dp))
            } else {
                Text("${index + 1}", style = MaterialTheme.typography.bodyMedium, color = c.textDim, maxLines = 1)
            }
        }
        Text(
            track.title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (!current) c.text else if (c.isDark) c.title else c.accent,
            fontWeight = if (current) FontWeight.Medium else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            when {
                track.station -> "Radio"
                track.lengthMs != null -> SongList.duration(track.lengthMs)
                else -> ""
            },
            style = MaterialTheme.typography.bodyMedium,
            color = c.textDim,
            maxLines = 1,
        )
    }
}

/** The PC icon on a pale green tile, as "All songs" has its note. */
@Composable
fun PcTile() {
    val c = Fernplay.colors
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(c.accentContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_computer), contentDescription = null, tint = c.onAccentContainer)
    }
}
