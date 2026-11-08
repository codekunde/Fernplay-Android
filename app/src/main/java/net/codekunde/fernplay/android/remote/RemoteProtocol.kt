package net.codekunde.fernplay.android.remote

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * The desktop remote's wire format (Fernplay `Fernplay.Core/Remote`): one JSON object per line over TLS.
 * The formulas match `RemoteSecurity` on the PC; change both sides together.
 */
object RemoteProtocol {
    const val TCP_PORT = 47800
    const val DISCOVERY_PORT = 47801
    const val QUESTION = "FERNPLAY?"
    const val CODE_LENGTH = 6

    /** SHA-256 of the certificate's DER bytes, lower-case hex: what the phone pins. */
    fun fingerprint(der: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(der))

    /** Pairing proof: HMAC-SHA256 keyed with the 6-digit code over the certificate fingerprint (hex). */
    fun pairProof(code: String, fingerprint: String): String = hmac(code.toByteArray(), fingerprint.toByteArray())

    /** Login proof: HMAC-SHA256 keyed with the phone's key over nonce + fingerprint (hex). */
    fun authProof(key: ByteArray, nonce: ByteArray, fingerprint: String): String =
        hmac(key, nonce + fingerprint.toByteArray())

    /** Six digits, nothing else (spaces typed between groups are dropped first). */
    fun cleanCode(typed: String): String? = typed.filterNot { it.isWhitespace() }.takeIf { it.length == CODE_LENGTH && it.all(Char::isDigit) }

    fun pair(proof: String, name: String): String = JSONObject().put("t", "pair").put("proof", proof).put("name", name).toString()
    fun hello(id: String): String = JSONObject().put("t", "hello").put("id", id).toString()
    fun auth(proof: String): String = JSONObject().put("t", "auth").put("proof", proof).toString()
    fun ping(): String = JSONObject().put("t", "ping").toString()

    /** A command; [value] is a number, a boolean, a string or nothing, as the PC expects for [action]. */
    fun command(action: String, value: Any? = null): String =
        JSONObject().put("t", "cmd").put("do", action).apply { if (value != null) put("value", value) }.toString()

    fun parse(line: String): JSONObject? = try { JSONObject(line) } catch (_: org.json.JSONException) { null }

    fun type(m: JSONObject?): String? = m?.optString("t")?.takeIf { it.isNotEmpty() }

    fun state(m: JSONObject): PcState = PcState(
        play = PlayState.of(m.optString("play")),
        title = m.optNullableString("title"),
        artist = m.optNullableString("artist"),
        station = m.optBoolean("station"),
        positionMs = (m.optDouble("pos", 0.0).finiteOr0() * 1000).toLong().coerceAtLeast(0),
        lengthMs = (m.optDouble("len", 0.0).finiteOr0() * 1000).toLong().coerceAtLeast(0),
        canSeek = m.optBoolean("canSeek"),
        volume = m.optDouble("vol", 0.0).finiteOr0().coerceIn(0.0, 1.0).toFloat(),
        shuffle = m.optBoolean("shuffle"),
        repeat = PcRepeat.of(m.optString("repeat")),
        crossfade = m.optBoolean("xfade"),
        index = m.optInt("index", -1),
        listVersion = m.optInt("list", -1),
    )

    fun playlist(m: JSONObject): List<PcTrack> {
        val items = m.optJSONArray("items") ?: JSONArray()
        return (0 until items.length()).mapNotNull { i ->
            val o = items.optJSONObject(i) ?: return@mapNotNull null
            val len = if (o.isNull("len")) null else o.optDouble("len", Double.NaN).takeIf { it.isFinite() && it >= 0 }
            PcTrack(o.optString("title"), len?.let { (it * 1000).toLong() }, o.optBoolean("station"))
        }
    }

    /** "192.168.1.20", "192.168.1.20:47900" or "studio-pc" → host and port; null when it cannot be an address. */
    fun parseAddress(typed: String): Pair<String, Int>? {
        val s = typed.trim()
        if (s.isEmpty() || s.any { it.isWhitespace() || it == '/' }) return null
        val colon = s.lastIndexOf(':')
        // One colon is host:port; more is a bare IPv6 address.
        if (colon > 0 && s.indexOf(':') == colon) {
            val port = s.substring(colon + 1).toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
            return s.substring(0, colon) to port
        }
        return s to TCP_PORT
    }

    fun decodeKey(b64: String): ByteArray? = try { Base64.getDecoder().decode(b64) } catch (_: IllegalArgumentException) { null }

    private fun hmac(key: ByteArray, data: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return hex(mac.doFinal(data))
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun Double.finiteOr0() = if (isFinite()) this else 0.0

    private fun JSONObject.optNullableString(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }
}

enum class PlayState {
    Playing, Paused, Stopped;

    companion object {
        fun of(s: String): PlayState = when (s) {
            "playing" -> Playing
            "paused" -> Paused
            else -> Stopped
        }
    }
}

/** The PC's repeat: off, the whole list, or one track. */
enum class PcRepeat(val wire: String) {
    Off("off"), All("all"), One("one");

    companion object {
        fun of(s: String): PcRepeat = entries.firstOrNull { it.wire == s } ?: Off
    }
}

/** The PC player's state as the phone shows it. */
data class PcState(
    val play: PlayState,
    val title: String?,
    val artist: String?,
    val station: Boolean,
    val positionMs: Long,
    val lengthMs: Long,
    val canSeek: Boolean,
    val volume: Float,
    val shuffle: Boolean,
    val repeat: PcRepeat,
    val crossfade: Boolean,
    val index: Int,
    val listVersion: Int,
)

/** One row of the PC's playlist; [lengthMs] is null when the PC does not know it yet. */
data class PcTrack(val title: String, val lengthMs: Long?, val station: Boolean)
