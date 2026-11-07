package net.codekunde.fernplay.android.eq

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

/**
 * The EQ's sound: one peaking biquad per band and channel (RBJ cookbook, Q 1.4)
 * plus the preamp, the same maths as Fernplay for Windows. Works on interleaved
 * float samples in place.
 *
 * Filter memory is kept when the settings change, so moving a slider does not
 * click; [reset] clears it (after a seek, the old sound should not ring on).
 */
class EqFilter(val sampleRate: Int, val channels: Int) {

    private var seen: EqSettings? = null
    private var coef: Array<Biquad> = emptyArray() // only bands that do something
    private var preampGain = 1.0

    // State per band and channel: x1, x2, y1, y2.
    private val state = Array(EqSettings.BAND_COUNT) { Array(channels) { DoubleArray(4) } }

    /** True when [settings] change the sound at all (off, or flat with 0 dB preamp, does not). */
    fun isActive(settings: EqSettings): Boolean {
        update(settings)
        return coef.isNotEmpty() || preampGain != 1.0
    }

    fun reset() {
        for (band in state) for (ch in band) ch.fill(0.0)
    }

    /** Filters [frames] frames of interleaved samples in [buffer], in place. */
    fun process(buffer: FloatArray, frames: Int, settings: EqSettings) {
        update(settings)
        val bq = coef
        val pre = preampGain
        if (bq.isEmpty() && pre == 1.0) return
        var i = 0
        for (f in 0 until frames) {
            for (ch in 0 until channels) {
                var x = buffer[i] * pre
                for (c in bq) {
                    val s = state[c.slot][ch]
                    val y = c.b0 * x + c.b1 * s[0] + c.b2 * s[1] - c.a1 * s[2] - c.a2 * s[3]
                    s[1] = s[0]; s[0] = x
                    s[3] = s[2]; s[2] = y
                    x = y
                }
                buffer[i] = x.toFloat()
                i++
            }
        }
    }

    private fun update(settings: EqSettings) {
        if (settings === seen) return
        seen = settings
        coef = biquads(settings, sampleRate.toDouble())
        preampGain = if (settings.enabled) 10.0.pow(settings.preamp / 20) else 1.0
    }

    internal class Biquad(val slot: Int, val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double)

    companion object {
        const val Q = 1.4

        /** Bands at or above this share of the sample rate are skipped (too close to Nyquist to shape). */
        const val NYQUIST_SHARE = 0.45

        internal fun biquads(settings: EqSettings, rate: Double): Array<Biquad> {
            if (!settings.enabled) return emptyArray()
            val list = ArrayList<Biquad>(EqSettings.BAND_COUNT)
            for (b in 0 until EqSettings.BAND_COUNT) {
                val db = settings.bands[b]
                val f = EqSettings.FREQUENCIES[b]
                // A band at 0 dB, or too close to Nyquist for this sample rate, does nothing.
                if (db == 0.0 || f >= rate * NYQUIST_SHARE) continue
                list += peaking(b, f, db, rate)
            }
            return list.toTypedArray()
        }

        private fun peaking(slot: Int, f: Double, db: Double, rate: Double): Biquad {
            val a = 10.0.pow(db / 40)
            val w0 = 2 * PI * f / rate
            val alpha = sin(w0) / (2 * Q)
            val cos = cos(w0)
            val a0 = 1 + alpha / a
            return Biquad(slot, (1 + alpha * a) / a0, -2 * cos / a0, (1 - alpha * a) / a0, -2 * cos / a0, (1 - alpha / a) / a0)
        }

        /**
         * The gain in dB the EQ gives a tone at [hz]: preamp plus every band's
         * filter, worked out from the same coefficients the sound goes through.
         * Draws the curve on the EQ screen.
         */
        fun responseDb(settings: EqSettings, hz: Double, rate: Double = 48000.0): Double {
            if (!settings.enabled) return 0.0
            val w = 2 * PI * hz / rate
            val c1 = cos(w); val s1 = sin(w)
            val c2 = cos(2 * w); val s2 = sin(2 * w)
            var db = settings.preamp
            for (q in biquads(settings, rate)) {
                // H(e^jw) = (b0 + b1 e^-jw + b2 e^-2jw) / (1 + a1 e^-jw + a2 e^-2jw)
                val nr = q.b0 + q.b1 * c1 + q.b2 * c2
                val ni = -q.b1 * s1 - q.b2 * s2
                val dr = 1 + q.a1 * c1 + q.a2 * c2
                val di = -q.a1 * s1 - q.a2 * s2
                db += 20 * log10(hypot(nr, ni) / hypot(dr, di))
            }
            return db
        }
    }
}
