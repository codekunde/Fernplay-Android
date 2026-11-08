package net.codekunde.fernplay.android.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.SocketTimeoutException

/** A PC that answered on the Wi-Fi. */
data class FoundPc(val name: String, val host: String, val port: Int, val fingerprint: String)

/**
 * Finds Fernplay PCs on the Wi-Fi: broadcasts "FERNPLAY?" on UDP 47801 and collects the answers. Some routers
 * block broadcasts between devices; the typed address is the fallback. Blocking: use it from Dispatchers.IO.
 */
object PcFinder {

    fun search(context: Context, durationMs: Long = 2_500): List<FoundPc> {
        val app = context.applicationContext
        // Some phones drop broadcast replies while the screen's Wi-Fi power saving is on, unless this lock is held.
        val lock = (app.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createMulticastLock("fernplay-remote")?.apply { setReferenceCounted(false); acquire() }
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 250
                val targets = (broadcastAddresses(app) + InetAddress.getByName("255.255.255.255")).distinct()
                val question = RemoteProtocol.QUESTION.toByteArray()
                val found = LinkedHashMap<String, FoundPc>()
                val start = System.currentTimeMillis()
                var asked = 0
                val buffer = ByteArray(2048)
                while (System.currentTimeMillis() - start < durationMs) {
                    // Ask twice (a dropped packet is common on Wi-Fi): at the start and after a second.
                    if (asked < 2 && System.currentTimeMillis() - start >= asked * 1_000L) {
                        for (t in targets) {
                            try { socket.send(DatagramPacket(question, question.size, t, RemoteProtocol.DISCOVERY_PORT)) } catch (_: IOException) { }
                        }
                        asked++
                    }
                    val packet = DatagramPacket(buffer, buffer.size)
                    try { socket.receive(packet) } catch (_: SocketTimeoutException) { continue }
                    val host = packet.address?.hostAddress ?: continue
                    parseAnswer(String(packet.data, 0, packet.length, Charsets.UTF_8), host)?.let { found[it.fingerprint] = it }
                }
                return found.values.toList()
            }
        } catch (_: IOException) {
            return emptyList()
        } finally {
            lock?.release()
        }
    }

    /** {"fernplay":1,"name":…,"port":…,"fp":…} from [host], or null for anything else. */
    internal fun parseAnswer(text: String, host: String): FoundPc? = try {
        val o = JSONObject(text)
        val port = o.optInt("port", 0)
        val fp = o.optString("fp")
        if (o.optInt("fernplay") != 1 || port !in 1..65535 || !fp.matches(Regex("[0-9a-f]{64}"))) null
        else FoundPc(o.optString("name").ifBlank { host }.take(60), host, port, fp)
    } catch (_: org.json.JSONException) {
        null
    }

    /** The subnet broadcast address of each IPv4 address on the current network (192.168.1.255 for a /24). */
    private fun broadcastAddresses(context: Context): List<InetAddress> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return emptyList()
        val props = cm.getLinkProperties(cm.activeNetwork) ?: return emptyList()
        return props.linkAddresses.mapNotNull { la ->
            val a = la.address as? Inet4Address ?: return@mapNotNull null
            broadcastOf(a.address, la.prefixLength)?.let { InetAddress.getByAddress(it) }
        }
    }

    internal fun broadcastOf(address: ByteArray, prefix: Int): ByteArray? {
        if (address.size != 4 || prefix !in 1..30) return null
        val ip = address.fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
        val bcast = ip or ((1 shl (32 - prefix)) - 1)
        return byteArrayOf((bcast ushr 24).toByte(), (bcast ushr 16).toByte(), (bcast ushr 8).toByte(), bcast.toByte())
    }
}
