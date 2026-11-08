package net.codekunde.fernplay.android.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.remoteStore by preferencesDataStore(name = "remote")

/**
 * A PC this phone paired with. [fingerprint] identifies it (its certificate); [wrappedKey] is the key the PC
 * gave, encrypted by [KeyVault] so the stored file alone does not give it away.
 */
data class PairedPc(
    val fingerprint: String,
    val name: String,
    val host: String,
    val port: Int,
    val phoneId: String,
    val wrappedKey: String,
    val lastUsed: Long,
)

/** Encrypts the PCs' keys at rest. */
interface KeyVault {
    fun wrap(key: ByteArray): String
    /** Null when the key cannot be read any more (Keystore key gone after a restore to another phone). */
    fun unwrap(wrapped: String): ByteArray?
}

/** AES-GCM with a key that stays inside the Android Keystore. */
class AndroidKeyVault : KeyVault {
    private fun secret(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    override fun wrap(key: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, secret()) }
        val sealed = cipher.doFinal(key)
        return Base64.getEncoder().encodeToString(cipher.iv + sealed)
    }

    override fun unwrap(wrapped: String): ByteArray? = try {
        val bytes = Base64.getDecoder().decode(wrapped)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, bytes, 0, IV))
        cipher.doFinal(bytes, IV, bytes.size - IV)
    } catch (_: GeneralSecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "fernplay-remote"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV = 12
    }
}

/** The paired PCs, newest use first. */
class PairedPcs(private val context: Context, private val vault: KeyVault) {

    val all: Flow<List<PairedPc>> = context.remoteStore.data.map { p -> decode(p[PCS]).sortedByDescending { it.lastUsed } }

    suspend fun add(reply: PairedReply, host: String, port: Int, now: Long = System.currentTimeMillis()): PairedPc {
        val pc = PairedPc(reply.fingerprint, reply.pcName, host, port, reply.phoneId, vault.wrap(reply.key), now)
        // Pairing again replaces the old entry: same certificate, or same address (the PC's Fernplay was reset).
        update { list -> list.filterNot { it.fingerprint == pc.fingerprint || (it.host == host && it.port == port) } + pc }
        return pc
    }

    suspend fun touch(fingerprint: String, now: Long = System.currentTimeMillis()) =
        update { list -> list.map { if (it.fingerprint == fingerprint) it.copy(lastUsed = now) else it } }

    /** The PC was found at a new address (DHCP gave it another one). */
    suspend fun moved(fingerprint: String, host: String, port: Int) =
        update { list -> list.map { if (it.fingerprint == fingerprint) it.copy(host = host, port = port) else it } }

    suspend fun remove(fingerprint: String) = update { list -> list.filterNot { it.fingerprint == fingerprint } }

    fun key(pc: PairedPc): ByteArray? = vault.unwrap(pc.wrappedKey)

    suspend fun snapshot(): List<PairedPc> = all.first()

    private suspend fun update(change: (List<PairedPc>) -> List<PairedPc>) {
        context.remoteStore.edit { p -> p[PCS] = encode(change(decode(p[PCS]))) }
    }

    companion object {
        private val PCS = stringPreferencesKey("pcs")

        internal fun encode(list: List<PairedPc>): String = JSONArray(
            list.map {
                JSONObject()
                    .put("fp", it.fingerprint).put("name", it.name).put("host", it.host).put("port", it.port)
                    .put("id", it.phoneId).put("key", it.wrappedKey).put("used", it.lastUsed)
            }
        ).toString()

        internal fun decode(json: String?): List<PairedPc> {
            if (json.isNullOrEmpty()) return emptyList()
            return try {
                val a = JSONArray(json)
                (0 until a.length()).mapNotNull { i ->
                    val o = a.optJSONObject(i) ?: return@mapNotNull null
                    PairedPc(
                        o.optString("fp"), o.optString("name"), o.optString("host"), o.optInt("port", RemoteProtocol.TCP_PORT),
                        o.optString("id"), o.optString("key"), o.optLong("used"),
                    ).takeIf { it.fingerprint.isNotEmpty() && it.host.isNotEmpty() && it.phoneId.isNotEmpty() && it.wrappedKey.isNotEmpty() }
                }
            } catch (_: JSONException) {
                emptyList()
            }
        }
    }
}
