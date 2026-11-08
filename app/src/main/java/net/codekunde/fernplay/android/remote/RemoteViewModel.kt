package net.codekunde.fernplay.android.remote

import android.app.Application
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** The connection to the chosen PC, as the Remote screen shows it. */
sealed interface Link {
    data object Idle : Link
    data object Connecting : Link
    data object Connected : Link
    /** Not connected; retries by itself unless [reason] needs a new pairing. */
    data class Lost(val reason: RemoteException.Reason) : Link
}

/** The pairing dialog's progress. */
sealed interface Pairing {
    data object Idle : Pairing
    data object Working : Pairing
    data class Failed(val reason: RemoteException.Reason) : Pairing
}

/** A PC to pair with: found on the Wi-Fi (with its fingerprint) or typed in. */
data class PairTarget(val name: String, val host: String, val port: Int, val fingerprint: String?)

class RemoteViewModel(app: Application) : AndroidViewModel(app) {

    private val store = PairedPcs(app, AndroidKeyVault())

    /** Paired PCs, last used first; null until read. */
    val pcs: StateFlow<List<PairedPc>?> = store.all.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _link = MutableStateFlow<Link>(Link.Idle)
    val link: StateFlow<Link> = _link.asStateFlow()

    private val _state = MutableStateFlow<PcState?>(null)
    val state: StateFlow<PcState?> = _state.asStateFlow()

    private val _playlist = MutableStateFlow<List<PcTrack>>(emptyList())
    val playlist: StateFlow<List<PcTrack>> = _playlist.asStateFlow()

    private val _found = MutableStateFlow<List<FoundPc>?>(null)
    /** PCs that answered the last search; null before the first one ends. */
    val found: StateFlow<List<FoundPc>?> = _found.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private val _pairing = MutableStateFlow<Pairing>(Pairing.Idle)
    val pairing: StateFlow<Pairing> = _pairing.asStateFlow()

    /** The volume being dragged, sent as it moves (latest value wins). */
    private val volumeOut = MutableStateFlow<Float?>(null)

    @Volatile private var socket: LineSocket? = null
    private var linkJob: Job? = null
    private var searchJob: Job? = null

    /** The Remote screen is on screen: connect to the last used PC and keep the connection. */
    fun start() {
        if (linkJob?.isActive == true) return
        linkJob = viewModelScope.launch { runLink() }
    }

    /** The screen left or the app went to the background (v1: no control from the background). */
    fun stop() {
        linkJob?.cancel()
        linkJob = null
        socket?.close()
        socket = null
        _link.value = Link.Idle
    }

    /** Connect again now (Retry), or to another PC after [use]. */
    fun reconnect() {
        stop()
        start()
    }

    private suspend fun runLink() {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            val pc = pcs.filterNotNull().first().firstOrNull() ?: run { _link.value = Link.Idle; return }
            val key = store.key(pc) ?: run { _link.value = Link.Lost(RemoteException.Reason.Forgotten); return }
            _link.value = Link.Connecting
            try {
                val s = try {
                    withContext(Dispatchers.IO) { RemoteClient.login(pc.host, pc.port, pc.fingerprint, pc.phoneId, key) }
                } catch (e: RemoteException) {
                    // The PC may have a new address: look for its certificate on the Wi-Fi once.
                    if (e.reason != RemoteException.Reason.Unreachable) throw e
                    val moved = withContext(Dispatchers.IO) { PcFinder.search(getApplication(), 1_500) }
                        .firstOrNull { it.fingerprint == pc.fingerprint && (it.host != pc.host || it.port != pc.port) } ?: throw e
                    store.moved(pc.fingerprint, moved.host, moved.port)
                    withContext(Dispatchers.IO) { RemoteClient.login(moved.host, moved.port, pc.fingerprint, pc.phoneId, key) }
                }
                socket = s
                attempt = 0
                _link.value = Link.Connected
                store.touch(pc.fingerprint)
                session(s)
                throw RemoteException(RemoteException.Reason.Unreachable, "closed")
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                socket?.close()
                socket = null
                currentCoroutineContext().ensureActive() // stopped: the closed socket is not a lost connection
                val reason = (e as? RemoteException)?.reason ?: RemoteException.Reason.Unreachable
                _link.value = Link.Lost(reason)
                if (reason.pairAgain) return
                // 2, 4, 8, then every 15 s while the screen is open.
                delay(minOf(15_000L, 2_000L shl attempt.coerceAtMost(3)))
                attempt++
            }
        }
    }

    /** Reads the PC's pushes, pings every 30 s, sends the dragged volume; returns when the connection ends. */
    private suspend fun session(s: LineSocket) = coroutineScope {
        s.setReadTimeout(75_000) // two missed pongs: the Wi-Fi is gone
        val pinger = launch(Dispatchers.IO) {
            while (isActive) {
                delay(30_000)
                s.send(RemoteProtocol.ping())
            }
        }
        val volume = launch(Dispatchers.IO) {
            volumeOut.filterNotNull().collect { v -> s.send(RemoteProtocol.command("volume", v.toDouble())) }
        }
        try {
            withContext(Dispatchers.IO) {
                while (isActive) {
                    val m = RemoteProtocol.parse(s.read() ?: break) ?: continue
                    when (RemoteProtocol.type(m)) {
                        "state" -> _state.value = RemoteProtocol.state(m)
                        "playlist" -> _playlist.value = RemoteProtocol.playlist(m)
                        "error" -> if (m.optString("error") in setOf("unknown-phone", "bad-proof")) {
                            throw RemoteException(RemoteException.Reason.Forgotten)
                        }
                    }
                }
            }
        } finally {
            pinger.cancel()
            volume.cancel()
            s.close()
        }
    }

    private fun send(action: String, value: Any? = null) {
        val s = socket ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try { s.send(RemoteProtocol.command(action, value)) } catch (_: IOException) { s.close() } // the read loop reconnects
        }
    }

    fun playPause() = send("toggle")
    fun stopPlayback() = send("stop")
    fun next() = send("next")
    fun previous() = send("previous")
    fun seekTo(ms: Long) = send("seek", ms / 1000.0)
    fun playIndex(i: Int) = send("playIndex", i)
    fun setShuffle(on: Boolean) = send("shuffle", on)
    fun setCrossfade(on: Boolean) = send("xfade", on)
    fun setRepeat(r: PcRepeat) = send("repeat", r.wire)

    /** While dragging: the PC follows (latest value wins). */
    fun setVolume(v: Float) {
        volumeOut.value = v
    }

    /** The drag ended: once the PC has had the last value, its own volume shows again. */
    fun finishVolume() {
        val last = volumeOut.value ?: return
        viewModelScope.launch { delay(600); if (volumeOut.value == last) volumeOut.value = null }
    }

    /** The volume to show: the dragged one until the PC has caught up. */
    val draggedVolume: StateFlow<Float?> = volumeOut.asStateFlow()

    /** Looks for PCs on the Wi-Fi. */
    fun search() {
        if (searchJob?.isActive == true) return
        searchJob = viewModelScope.launch {
            _searching.value = true
            try {
                _found.value = withContext(Dispatchers.IO) { PcFinder.search(getApplication()) }
            } finally {
                _searching.value = false
            }
        }
    }

    /** Pairs with [target] using the typed [code]; on success that PC becomes the current one. */
    fun pair(target: PairTarget, code: String, onPaired: () -> Unit) {
        if (_pairing.value == Pairing.Working) return
        val clean = RemoteProtocol.cleanCode(code) ?: run { _pairing.value = Pairing.Failed(RemoteException.Reason.WrongCode); return }
        _pairing.value = Pairing.Working
        viewModelScope.launch {
            try {
                val reply = withContext(Dispatchers.IO) { RemoteClient.pair(target.host, target.port, clean, phoneName(), target.fingerprint) }
                store.add(reply, target.host, target.port)
                pcs.first { it?.firstOrNull()?.fingerprint == reply.fingerprint }
                _pairing.value = Pairing.Idle
                onPaired()
                reconnect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _pairing.value = Pairing.Failed((e as? RemoteException)?.reason ?: RemoteException.Reason.Unreachable)
            }
        }
    }

    fun clearPairing() {
        if (_pairing.value != Pairing.Working) _pairing.value = Pairing.Idle
    }

    /** Makes [pc] the current PC and connects to it. */
    fun use(pc: PairedPc) {
        viewModelScope.launch {
            store.touch(pc.fingerprint)
            pcs.first { it?.firstOrNull()?.fingerprint == pc.fingerprint }
            _state.value = null
            _playlist.value = emptyList()
            reconnect()
        }
    }

    /** Forgets [pc] on this phone (the PC lists the phone until it is removed there too). */
    fun forget(pc: PairedPc) {
        viewModelScope.launch {
            stop()
            store.remove(pc.fingerprint)
            pcs.first { list -> list?.none { it.fingerprint == pc.fingerprint } == true }
            _state.value = null
            _playlist.value = emptyList()
            start()
        }
    }

    /** The name the PC lists in its Settings: the phone's own name, else its model. */
    private fun phoneName(): String {
        val app = getApplication<Application>()
        val named = Settings.Global.getString(app.contentResolver, Settings.Global.DEVICE_NAME)
        return (named?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: "Android phone").take(40)
    }

    override fun onCleared() {
        socket?.close()
    }
}
