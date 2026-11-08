package net.codekunde.fernplay.android

import net.codekunde.fernplay.android.remote.PairedPc
import net.codekunde.fernplay.android.remote.PairedPcs
import net.codekunde.fernplay.android.remote.PcFinder
import net.codekunde.fernplay.android.remote.PcRepeat
import net.codekunde.fernplay.android.remote.PlayState
import net.codekunde.fernplay.android.remote.RemoteProtocol
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The wire format against desktop Fernplay; the proof vectors were made with .NET's HMACSHA256, as the PC does. */
@RunWith(RobolectricTestRunner::class)
class RemoteProtocolTest {

    private val fp = (0 until 32).joinToString("") { "%02x".format(it * 7 % 256) }

    @Test
    fun `pair proof matches the PC's formula`() {
        assertEquals("5a43ae1a151c16c75db1229573e68e59d3a8d902e18789aa0fb782e67c1ea20a", RemoteProtocol.pairProof("042917", fp))
        // Another code or another certificate gives another proof.
        assertTrue(RemoteProtocol.pairProof("042918", fp) != RemoteProtocol.pairProof("042917", fp))
        assertTrue(RemoteProtocol.pairProof("042917", fp.replaceFirst('0', '1')) != RemoteProtocol.pairProof("042917", fp))
    }

    @Test
    fun `auth proof matches the PC's formula`() {
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(32) { (100 + it).toByte() }
        assertEquals("999fab48b0620379801cd5de2b126ef8d3a4c49015431f294d91295fee6d7872", RemoteProtocol.authProof(key, nonce, fp))
    }

    @Test
    fun `fingerprint is lower-case SHA-256 hex`() {
        assertEquals("06298432e8066b29e2223bcc23aa9504b56ae508fabf3435508869b9c3190e22", RemoteProtocol.fingerprint("cert".toByteArray()))
    }

    @Test
    fun `code must be six digits`() {
        assertEquals("042917", RemoteProtocol.cleanCode("042 917"))
        assertNull(RemoteProtocol.cleanCode("04291"))
        assertNull(RemoteProtocol.cleanCode("0429171"))
        assertNull(RemoteProtocol.cleanCode("04291a"))
    }

    @Test
    fun `state message is read as the PC sends it`() {
        val m = JSONObject(
            """{"t":"state","play":"playing","title":"Song","artist":"Band","station":false,"pos":12.3,"len":200.5,""" +
                """"canSeek":true,"vol":0.42,"shuffle":true,"repeat":"one","xfade":true,"index":2,"list":7}"""
        )
        val s = RemoteProtocol.state(m)
        assertEquals(PlayState.Playing, s.play)
        assertEquals("Song", s.title)
        assertEquals("Band", s.artist)
        assertEquals(12_300L, s.positionMs)
        assertEquals(200_500L, s.lengthMs)
        assertEquals(0.42f, s.volume, 0.0001f)
        assertTrue(s.shuffle && s.crossfade && s.canSeek)
        assertEquals(PcRepeat.One, s.repeat)
        assertEquals(2, s.index)
        assertEquals(7, s.listVersion)
    }

    @Test
    fun `missing and odd state fields fall back safely`() {
        val s = RemoteProtocol.state(JSONObject("""{"t":"state","play":"weird","title":null,"vol":7,"repeat":"x","pos":-3}"""))
        assertEquals(PlayState.Stopped, s.play)
        assertNull(s.title)
        assertEquals(1f, s.volume)
        assertEquals(PcRepeat.Off, s.repeat)
        assertEquals(0L, s.positionMs)
        assertEquals(-1, s.index)
    }

    @Test
    fun `playlist rows keep unknown lengths and stations`() {
        val list = RemoteProtocol.playlist(
            JSONObject("""{"t":"playlist","version":3,"items":[{"title":"A","len":61.5,"station":false},{"title":"Radio X","len":null,"station":true}]}""")
        )
        assertEquals(2, list.size)
        assertEquals(61_500L, list[0].lengthMs)
        assertNull(list[1].lengthMs)
        assertTrue(list[1].station)
    }

    @Test
    fun `commands carry the value type the PC checks`() {
        assertEquals("""{"t":"cmd","do":"toggle"}""", RemoteProtocol.command("toggle"))
        assertEquals(0.5, JSONObject(RemoteProtocol.command("volume", 0.5)).getDouble("value"), 0.0)
        assertEquals(true, JSONObject(RemoteProtocol.command("shuffle", true)).getBoolean("value"))
        assertEquals("one", JSONObject(RemoteProtocol.command("repeat", "one")).getString("value"))
        assertEquals(3, JSONObject(RemoteProtocol.command("playIndex", 3)).getInt("value"))
    }

    @Test
    fun `typed addresses`() {
        assertEquals("192.168.1.20" to 47800, RemoteProtocol.parseAddress(" 192.168.1.20 "))
        assertEquals("192.168.1.20" to 47900, RemoteProtocol.parseAddress("192.168.1.20:47900"))
        assertEquals("studio-pc" to 47800, RemoteProtocol.parseAddress("studio-pc"))
        assertNull(RemoteProtocol.parseAddress(""))
        assertNull(RemoteProtocol.parseAddress("192.168.1.20:99999"))
        assertNull(RemoteProtocol.parseAddress("http://pc"))
        assertNull(RemoteProtocol.parseAddress("a b"))
    }

    @Test
    fun `discovery answer must look like the PC's`() {
        val ok = PcFinder.parseAnswer("""{"fernplay":1,"name":"STUDIO","port":47800,"fp":"$fp"}""", "192.168.1.5")
        assertEquals("STUDIO", ok?.name)
        assertEquals("192.168.1.5", ok?.host)
        assertNull(PcFinder.parseAnswer("""{"fernplay":2,"name":"X","port":47800,"fp":"$fp"}""", "h"))
        assertNull(PcFinder.parseAnswer("""{"fernplay":1,"name":"X","port":0,"fp":"$fp"}""", "h"))
        assertNull(PcFinder.parseAnswer("""{"fernplay":1,"name":"X","port":47800,"fp":"zz"}""", "h"))
        assertNull(PcFinder.parseAnswer("not json", "h"))
    }

    @Test
    fun `subnet broadcast address`() {
        assertArrayEquals(byteArrayOf(192.toByte(), 168.toByte(), 1, 255.toByte()), PcFinder.broadcastOf(byteArrayOf(192.toByte(), 168.toByte(), 1, 37), 24))
        assertArrayEquals(byteArrayOf(10, 0, 3, 255.toByte()), PcFinder.broadcastOf(byteArrayOf(10, 0, 2, 15), 23))
        assertNull(PcFinder.broadcastOf(byteArrayOf(10, 0, 0, 1), 32))
    }

    @Test
    fun `paired PCs survive a round trip and bad entries are dropped`() {
        val pc = PairedPc(fp, "STUDIO", "192.168.1.5", 47800, "abc", "wrapped", 123L)
        assertEquals(listOf(pc), PairedPcs.decode(PairedPcs.encode(listOf(pc))))
        assertEquals(emptyList<PairedPc>(), PairedPcs.decode("""[{"fp":"","host":"h","id":"i","key":"k"}]"""))
        assertEquals(emptyList<PairedPc>(), PairedPcs.decode("garbage"))
        assertFalse(PairedPcs.decode(null).isNotEmpty())
    }
}
