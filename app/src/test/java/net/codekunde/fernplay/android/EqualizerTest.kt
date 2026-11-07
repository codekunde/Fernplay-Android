package net.codekunde.fernplay.android

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import net.codekunde.fernplay.android.eq.EqFilter
import net.codekunde.fernplay.android.eq.EqPreset
import net.codekunde.fernplay.android.eq.EqSettings
import net.codekunde.fernplay.android.eq.EqualizerProcessor
import net.codekunde.fernplay.android.eq.UserPresets
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The EQ's maths, presets and the ExoPlayer processor, on the JVM. */
class EqualizerTest {

    /** Gain in dB the filter gives a sine at [hz] (stereo, measured after it settles). */
    private fun measuredDb(eq: EqSettings, hz: Double, rate: Int = 44100): Double {
        val frames = rate // one second
        val buf = FloatArray(frames * 2)
        for (i in 0 until frames) {
            val v = (0.1 * sin(2 * PI * hz * i / rate)).toFloat()
            buf[2 * i] = v; buf[2 * i + 1] = v
        }
        val input = rms(buf, frames / 2, frames)
        EqFilter(rate, 2).process(buf, frames, eq)
        return 20 * log10(rms(buf, frames / 2, frames) / input)
    }

    private fun rms(buf: FloatArray, from: Int, to: Int): Double {
        var sum = 0.0
        for (i in from until to) sum += buf[2 * i].toDouble() * buf[2 * i]
        return sqrt(sum / (to - from))
    }

    @Test
    fun eachBandGivesItsGainAtItsCentre() {
        for (b in 0 until EqSettings.BAND_COUNT) {
            val f = EqSettings.FREQUENCIES[b]
            for (db in listOf(12.0, -12.0, 6.0)) {
                val got = measuredDb(EqSettings().withBand(b, db), f)
                assertEquals("band ${EqSettings.label(b)} at $db dB", db, got, 0.5)
            }
        }
    }

    @Test
    fun preampMovesEverything() {
        assertEquals(-6.0, measuredDb(EqSettings().withPreamp(-6.0), 1000.0), 0.05)
        assertEquals(-6.0, measuredDb(EqSettings().withPreamp(-6.0), 100.0), 0.05)
    }

    @Test
    fun offOrFlatLeavesSamplesAlone() {
        val rock = EqSettings().apply(EqPreset.BUILT_IN.first { it.name == "Rock" })
        val samples = FloatArray(200) { (it % 7) * 0.01f }
        val copy = samples.copyOf()
        EqFilter(48000, 2).process(samples, 100, rock.copy(enabled = false))
        assertArrayEquals(copy, samples, 0f)
        assertFalse(EqFilter(48000, 2).isActive(EqSettings()))
        assertFalse(EqFilter(48000, 2).isActive(rock.copy(enabled = false)))
        assertTrue(EqFilter(48000, 2).isActive(rock))
    }

    @Test
    fun bandsNearNyquistAreSkipped() {
        // At 32 kHz, 0.45 × rate = 14.4 kHz: the 16K band does nothing, 14K still works.
        val top = EqSettings(bands = List(10) { if (it == 9) 12.0 else 0.0 })
        val next = EqSettings(bands = List(10) { if (it == 8) 12.0 else 0.0 })
        assertEquals(0.0, EqFilter.responseDb(top, 15000.0, 32000.0), 1e-9)
        assertFalse(EqFilter(32000, 2).isActive(top))
        assertTrue(EqFilter(44100, 2).isActive(top))
        assertTrue(EqFilter.responseDb(next, 14000.0, 32000.0) > 6)
    }

    @Test
    fun curveMatchesTheSound() {
        val pop = EqSettings().apply(EqPreset.BUILT_IN.first { it.name == "Pop" })
        for (hz in listOf(80.0, 450.0, 2000.0, 9000.0)) {
            assertEquals("at $hz Hz", EqFilter.responseDb(pop, hz, 44100.0), measuredDb(pop, hz), 0.2)
        }
        assertEquals(0.0, EqFilter.responseDb(pop.copy(enabled = false), 600.0), 0.0)
    }

    @Test
    fun valuesAreClampedAndRounded() {
        assertEquals(12.0, EqSettings.clamp(20.0), 0.0)
        assertEquals(-12.0, EqSettings.clamp(-13.0), 0.0)
        assertEquals(3.5, EqSettings.clamp(3.46), 0.0)
        assertEquals(0.0, EqSettings.clamp(Double.NaN), 0.0)
        assertEquals(12.0, EqSettings().withBand(3, 99.0).bands[3], 0.0)
    }

    @Test
    fun labelsAndText() {
        assertEquals(listOf("60", "170", "310", "600", "1K", "3K", "6K", "12K", "14K", "16K"), (0 until 10).map(EqSettings::label))
        assertEquals("1 kilohertz", EqSettings.spokenFrequency(4))
        assertEquals("+3 dB", EqSettings.dbText(3.0))
        assertEquals("−2.5 dB", EqSettings.dbText(-2.5))
        assertEquals("0 dB", EqSettings.dbText(-0.01))
    }

    @Test
    fun presetMatching() {
        val dance = EqPreset.BUILT_IN.first { it.name == "Dance" }
        val eq = EqSettings().apply(dance)
        assertTrue(eq.matches(dance))
        assertTrue(eq.copy(enabled = false).matches(dance))
        assertFalse(eq.withBand(0, 6.5).matches(dance))
        assertEquals(8, EqPreset.BUILT_IN.size)
        assertTrue(EqPreset.BUILT_IN.all { it.bands.size == 10 })
    }

    @Test
    fun presetNames() {
        assertNotNull(UserPresets.nameProblem("  "))
        assertNotNull(UserPresets.nameProblem("rock"))
        assertNotNull(UserPresets.nameProblem("x".repeat(31)))
        assertNotNull(UserPresets.nameProblem("a\tb"))
        assertNull(UserPresets.nameProblem("Gym"))
    }

    @Test
    fun saveReplacesInPlaceAndDeleteIgnoresCase() {
        val a = EqPreset("Gym", 0.0, EqSettings.FLAT)
        val b = EqPreset("Car", -3.0, EqSettings.FLAT)
        var list = UserPresets.save(UserPresets.save(emptyList(), a), b)
        assertTrue(UserPresets.replaces(list, " gym "))
        list = UserPresets.save(list, EqPreset("GYM", 2.0, EqSettings.FLAT))
        assertEquals(listOf("GYM", "Car"), list.map { it.name })
        assertEquals(2.0, list[0].preamp, 0.0)
        assertEquals(listOf("Car"), UserPresets.delete(list, "gym").map { it.name })
    }

    @Test
    fun presetsSurviveSaving() {
        val list = listOf(
            EqPreset("Gym", -2.5, listOf(6.0, 4.0, 1.5, 0.0, 0.0, 0.0, -1.0, 0.0, 2.0, 3.0)),
            EqPreset("Café · night", 0.0, EqSettings.FLAT),
        )
        assertEquals(list, UserPresets.decode(UserPresets.encode(list)))
        assertEquals(emptyList<EqPreset>(), UserPresets.decode(""))
        assertEquals(emptyList<EqPreset>(), UserPresets.decode(null))
    }

    @Test
    fun damagedPresetLinesAreDropped() {
        val good = "Gym\t0.0\t" + List(10) { "1.0" }.joinToString("\t")
        val text = listOf(
            good,
            "gym\t0.0\t" + List(10) { "2.0" }.joinToString("\t"), // same name again
            "Short\t0.0\t1.0\t2.0", // not ten bands
            "Bad\tx\t" + List(10) { "1.0" }.joinToString("\t"), // not a number
            "\t0.0\t" + List(10) { "1.0" }.joinToString("\t"), // no name
            "Loud\t0.0\t" + List(10) { "40" }.joinToString("\t"), // clamped
        ).joinToString("\n")
        val got = UserPresets.decode(text)
        assertEquals(listOf("Gym", "Loud"), got.map { it.name })
        assertEquals(1.0, got[0].bands[0], 0.0)
        assertEquals(12.0, got[1].bands[9], 0.0)
    }

    // --- The ExoPlayer processor ---

    private fun processor(eq: EqSettings, encoding: Int, rate: Int = 44100): EqualizerProcessor {
        val p = EqualizerProcessor { eq }
        p.configure(AudioProcessor.AudioFormat(rate, 2, encoding))
        p.flush()
        return p
    }

    private fun sine16(hz: Double, frames: Int, rate: Int = 44100): ByteBuffer {
        val b = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            val v = (3000 * sin(2 * PI * hz * i / rate)).toInt().toShort()
            b.putShort(v); b.putShort(v)
        }
        b.flip()
        return b
    }

    private fun rms16(b: ByteBuffer, fromFrame: Int): Double {
        var sum = 0.0
        val frames = b.remaining() / 4
        for (i in fromFrame until frames) {
            val s = b.getShort(b.position() + i * 4).toDouble()
            sum += s * s
        }
        return sqrt(sum / (frames - fromFrame))
    }

    @Test
    fun processorPassesSamplesThroughWhenOff() {
        val p = processor(EqSettings(enabled = false, bands = List(10) { 12.0 }), C.ENCODING_PCM_16BIT)
        val input = sine16(1000.0, 2000)
        val expected = ByteArray(input.remaining()).also { input.duplicate().get(it) }
        p.queueInput(input)
        val out = p.output
        val got = ByteArray(out.remaining()).also { out.get(it) }
        assertArrayEquals(expected, got)
        assertTrue(p.isActive)
    }

    @Test
    fun processorBoosts16BitPcm() {
        val p = processor(EqSettings().withBand(4, 12.0), C.ENCODING_PCM_16BIT)
        val input = sine16(1000.0, 22050)
        val before = rms16(input.duplicate().order(ByteOrder.nativeOrder()), 11025) // duplicate() drops the byte order
        p.queueInput(input)
        val out = p.output
        assertEquals(22050 * 4, out.remaining())
        assertEquals(12.0, 20 * log10(rms16(out, 11025) / before), 0.5)
    }

    @Test
    fun processorCutsFloatPcm() {
        val p = processor(EqSettings().withPreamp(-6.0), C.ENCODING_PCM_FLOAT)
        val b = ByteBuffer.allocateDirect(8 * 100).order(ByteOrder.nativeOrder())
        repeat(200) { b.putFloat(0.5f) }
        b.flip()
        p.queueInput(b)
        val out = p.output
        assertEquals(800, out.remaining())
        assertEquals(0.5 * 0.501187, out.getFloat(out.position()).toDouble(), 1e-4)
    }

    @Test(expected = AudioProcessor.UnhandledAudioFormatException::class)
    fun processorRefusesOtherFormats() {
        EqualizerProcessor { EqSettings() }.configure(AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_24BIT))
    }
}
