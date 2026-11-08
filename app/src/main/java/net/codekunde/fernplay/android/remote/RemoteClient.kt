package net.codekunde.fernplay.android.remote

import android.annotation.SuppressLint
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/** Why talking to the PC failed. [pairAgain] means retrying will not help: the phone must pair again. */
class RemoteException(val reason: Reason, message: String? = null, cause: Throwable? = null) : IOException(message ?: reason.name, cause) {
    enum class Reason(val pairAgain: Boolean) {
        /** No connection: PC off, remote off, other network, firewall. */
        Unreachable(false),
        /** The PC answered with another certificate than the one pinned (its Fernplay data was reset, or someone else). */
        CertificateChanged(true),
        /** The PC no longer knows this phone (removed in its Settings). */
        Forgotten(true),
        WrongCode(false),
        TooManyTries(false),
        Expired(false),
        NotPairing(false),
        /** The PC said something this app does not understand. */
        Protocol(false),
    }
}

/**
 * Accepts the PC's self-signed certificate only when its fingerprint is [expected]. With no fingerprint yet
 * (pairing from a typed address) any certificate passes the handshake: the pairing proof binds the code to the
 * fingerprint seen here, so the PC refuses it when someone else's certificate was in between.
 * Lint's CustomX509TrustManager is expected: the PC's certificate is self-signed, so pinning replaces the CA check.
 */
@SuppressLint("CustomX509TrustManager")
internal class PinnedTrust(private val expected: String?) : X509TrustManager {
    @Volatile var seen: String? = null
        private set

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("no certificate")
        val fp = RemoteProtocol.fingerprint(leaf.encoded)
        seen = fp
        if (expected != null && fp != expected) throw CertificateException("certificate changed")
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        throw CertificateException("not a server")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/** One TLS connection to the PC, one JSON object per line. Blocking: use it from Dispatchers.IO. */
class LineSocket private constructor(private val socket: SSLSocket, private val raw: Socket, val fingerprint: String) : Closeable {
    private val input = BufferedInputStream(socket.getInputStream())
    private val output = socket.getOutputStream()
    private val writeLock = Any()

    fun send(line: String) {
        val bytes = (line + "\n").toByteArray()
        synchronized(writeLock) {
            output.write(bytes)
            output.flush()
        }
    }

    /** The next non-empty line, or null when the PC closed the connection. */
    fun read(): String? {
        val line = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b < 0) return null
            if (b == '\n'.code) {
                val s = line.toString(Charsets.UTF_8.name()).trimEnd('\r')
                if (s.isBlank()) { line.reset(); continue }
                return s
            }
            line.write(b)
            if (line.size() > MAX_LINE) throw RemoteException(RemoteException.Reason.Protocol, "line too long")
        }
    }

    fun setReadTimeout(ms: Int) { socket.soTimeout = ms }

    /**
     * Ends the connection without a TLS goodbye: closing the TLS socket would send one, which is network work and
     * crashes when called from the main thread (leaving the screen). The PC sees the connection end either way.
     */
    override fun close() {
        try { raw.close() } catch (_: IOException) { }
    }

    companion object {
        /** A playlist of many thousand rows is a few MB; anything beyond this is not the PC. */
        private const val MAX_LINE = 8 * 1024 * 1024
        private const val CONNECT_TIMEOUT = 5_000
        private const val HANDSHAKE_TIMEOUT = 10_000

        /** Connects and finishes the TLS handshake; the certificate must match [pinned] when one is given. */
        fun open(host: String, port: Int, pinned: String?): LineSocket {
            val trust = PinnedTrust(pinned)
            val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
            val raw = Socket()
            try {
                raw.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT)
            } catch (e: IOException) {
                raw.close()
                throw RemoteException(RemoteException.Reason.Unreachable, e.message, e)
            }
            val ssl = context.socketFactory.createSocket(raw, host, port, true) as SSLSocket
            try {
                ssl.enabledProtocols = ssl.supportedProtocols.filter { it == "TLSv1.2" || it == "TLSv1.3" }.toTypedArray()
                ssl.soTimeout = HANDSHAKE_TIMEOUT
                ssl.startHandshake()
            } catch (e: SSLException) {
                ssl.close()
                val changed = pinned != null && trust.seen != null && trust.seen != pinned
                throw RemoteException(if (changed) RemoteException.Reason.CertificateChanged else RemoteException.Reason.Unreachable, e.message, e)
            } catch (e: IOException) {
                ssl.close()
                throw RemoteException(RemoteException.Reason.Unreachable, e.message, e)
            }
            return LineSocket(ssl, raw, trust.seen ?: run { raw.close(); throw RemoteException(RemoteException.Reason.Protocol, "no certificate") })
        }
    }
}

/** What the PC gave on pairing. */
data class PairedReply(val phoneId: String, val key: ByteArray, val pcName: String, val fingerprint: String)

/** The two conversations with the PC: pairing once, and logging in each time the remote opens. */
object RemoteClient {

    /** Pairs with the PC at [host]:[port] using the [code] shown there. Blocking. */
    fun pair(host: String, port: Int, code: String, phoneName: String, pinned: String?): PairedReply {
        LineSocket.open(host, port, pinned).use { s ->
            s.send(RemoteProtocol.pair(RemoteProtocol.pairProof(code, s.fingerprint), phoneName))
            val m = RemoteProtocol.parse(s.read() ?: throw RemoteException(RemoteException.Reason.Unreachable, "closed"))
            when (RemoteProtocol.type(m)) {
                "paired" -> {
                    val id = m!!.optString("id")
                    val key = RemoteProtocol.decodeKey(m.optString("key"))
                    if (id.isEmpty() || key == null || key.size < 16) throw RemoteException(RemoteException.Reason.Protocol, "bad pairing reply")
                    return PairedReply(id, key, m.optString("pc").ifBlank { host }, s.fingerprint)
                }
                "error" -> throw RemoteException(
                    when (m!!.optString("error")) {
                        "wrong-code" -> RemoteException.Reason.WrongCode
                        "too-many-tries" -> RemoteException.Reason.TooManyTries
                        "expired" -> RemoteException.Reason.Expired
                        else -> RemoteException.Reason.NotPairing
                    }
                )
                else -> throw RemoteException(RemoteException.Reason.Protocol, "unexpected reply")
            }
        }
    }

    /**
     * Logs in as a paired phone and returns the open connection after the PC's welcome; the PC then pushes
     * its state and playlist. Blocking.
     */
    fun login(host: String, port: Int, fingerprint: String, phoneId: String, key: ByteArray): LineSocket {
        val s = LineSocket.open(host, port, fingerprint)
        try {
            s.send(RemoteProtocol.hello(phoneId))
            val challenge = RemoteProtocol.parse(s.read() ?: throw RemoteException(RemoteException.Reason.Unreachable, "closed"))
            if (RemoteProtocol.type(challenge) != "challenge") throw RemoteException(RemoteException.Reason.Protocol, "no challenge")
            val nonce = RemoteProtocol.decodeKey(challenge!!.optString("nonce"))
                ?.takeIf { it.size >= 16 } ?: throw RemoteException(RemoteException.Reason.Protocol, "bad nonce")
            s.send(RemoteProtocol.auth(RemoteProtocol.authProof(key, nonce, s.fingerprint)))
            val answer = RemoteProtocol.parse(s.read() ?: throw RemoteException(RemoteException.Reason.Unreachable, "closed"))
            when (RemoteProtocol.type(answer)) {
                "welcome" -> return s
                "error" -> throw RemoteException(
                    when (answer!!.optString("error")) {
                        "unknown-phone", "bad-proof" -> RemoteException.Reason.Forgotten
                        else -> RemoteException.Reason.Protocol
                    }
                )
                else -> throw RemoteException(RemoteException.Reason.Protocol, "unexpected reply")
            }
        } catch (e: IOException) {
            s.close()
            throw e
        }
    }
}
