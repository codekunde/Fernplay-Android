package net.codekunde.fernplay.android.eq

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The 10-band graphic EQ settings, as on Fernplay for Windows: band gains and
 * preamp in dB (±12), on/off. Immutable; a change makes a new value.
 */
data class EqSettings(
    val enabled: Boolean = true,
    val preamp: Double = 0.0,
    val bands: List<Double> = FLAT,
) {
    init {
        require(bands.size == BAND_COUNT) { "An EQ has $BAND_COUNT bands, not ${bands.size}" }
    }

    fun withBand(index: Int, db: Double) = copy(bands = bands.toMutableList().also { it[index] = clamp(db) })

    fun withPreamp(db: Double) = copy(preamp = clamp(db))

    fun apply(preset: EqPreset) = copy(preamp = clamp(preset.preamp), bands = preset.bands.map(::clamp))

    /** True when the curve is the one [preset] sets (on/off does not count). */
    fun matches(preset: EqPreset): Boolean =
        same(preamp, preset.preamp) && bands.indices.all { same(bands[it], preset.bands[it]) }

    companion object {
        const val BAND_COUNT = 10
        const val MAX_DB = 12.0

        /** The classic band centres, in Hz. */
        val FREQUENCIES: List<Double> = listOf(60.0, 170.0, 310.0, 600.0, 1000.0, 3000.0, 6000.0, 12000.0, 14000.0, 16000.0)

        val FLAT: List<Double> = List(BAND_COUNT) { 0.0 }

        /** Within ±12 dB, rounded to 0.1 dB (as the desktop keeps it). */
        fun clamp(db: Double): Double =
            if (db.isNaN()) 0.0 else (db.coerceIn(-MAX_DB, MAX_DB) * 10).roundToInt() / 10.0

        private fun same(a: Double, b: Double) = abs(clamp(a) - clamp(b)) < 0.05

        /** Short label under a band slider: "60", "1K", "16K". */
        fun label(index: Int): String {
            val f = FREQUENCIES[index]
            return if (f >= 1000) "${(f / 1000).roundToInt()}K" else f.roundToInt().toString()
        }

        /** What TalkBack says for a band: "60 hertz", "1 kilohertz". */
        fun spokenFrequency(index: Int): String {
            val f = FREQUENCIES[index]
            return if (f >= 1000) "${(f / 1000).roundToInt()} kilohertz" else "${f.roundToInt()} hertz"
        }

        /** "+3.5 dB", "0 dB", "−6 dB" (a real minus sign). */
        fun dbText(db: Double): String {
            val v = clamp(db)
            val body = if (v == v.roundToInt().toDouble()) abs(v).roundToInt().toString() else "%.1f".format(java.util.Locale.ROOT, abs(v))
            return when {
                v > 0 -> "+$body dB"
                v < 0 -> "−$body dB"
                else -> "0 dB"
            }
        }
    }
}

/** A named EQ curve: preamp and ten bands, in dB. */
data class EqPreset(val name: String, val preamp: Double, val bands: List<Double>) {
    companion object {
        /** The desktop's built-in presets, same numbers. */
        val BUILT_IN: List<EqPreset> = listOf(
            EqPreset("Flat", 0.0, listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
            EqPreset("Rock", -2.0, listOf(5.0, 3.0, -2.0, -4.0, -1.0, 2.0, 5.0, 6.0, 6.0, 6.0)),
            EqPreset("Pop", -1.0, listOf(-1.0, 2.0, 4.0, 5.0, 3.0, 0.0, -1.0, -1.0, -1.0, -1.0)),
            EqPreset("Classical", 0.0, listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -4.0, -4.0, -4.0, -6.0)),
            EqPreset("Dance", -2.0, listOf(7.0, 5.0, 2.0, 0.0, 0.0, -3.0, -4.0, -4.0, 0.0, 0.0)),
            EqPreset("Bass boost", -4.0, listOf(8.0, 6.0, 4.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
            EqPreset("Treble boost", -4.0, listOf(0.0, 0.0, 0.0, 0.0, 0.0, 2.0, 5.0, 7.0, 8.0, 8.0)),
            EqPreset("Vocal", -2.0, listOf(-3.0, -3.0, -1.0, 2.0, 4.0, 4.0, 2.0, 0.0, -1.0, -2.0)),
        )

        fun from(name: String, eq: EqSettings) = EqPreset(name.trim(), eq.preamp, eq.bands)
    }
}

/** The user's own presets: rules for names, saving, deleting and reading them back. */
object UserPresets {
    const val MAX_NAME_LENGTH = 30

    /** Why a name cannot be used, or null when it can. A name already among the user's presets is fine (it replaces). */
    fun nameProblem(name: String): String? {
        val n = name.trim()
        return when {
            n.isEmpty() -> "Type a name for the preset."
            n.length > MAX_NAME_LENGTH -> "Keep the name to $MAX_NAME_LENGTH characters."
            n.any { it == '\t' || it == '\n' || it == '\r' } -> "A name cannot hold tabs or line breaks."
            EqPreset.BUILT_IN.any { it.name.equals(n, ignoreCase = true) } -> "$n is a built-in preset. Pick another name."
            else -> null
        }
    }

    /** True when saving under [name] would replace one of [list]. */
    fun replaces(list: List<EqPreset>, name: String) = list.any { it.name.equals(name.trim(), ignoreCase = true) }

    /** Adds the preset, or replaces the one with the same name (any case) in its place. */
    fun save(list: List<EqPreset>, preset: EqPreset): List<EqPreset> {
        val at = list.indexOfFirst { it.name.equals(preset.name, ignoreCase = true) }
        return if (at >= 0) list.toMutableList().also { it[at] = preset } else list + preset
    }

    fun delete(list: List<EqPreset>, name: String): List<EqPreset> = list.filterNot { it.name.equals(name, ignoreCase = true) }

    /** One line per preset: name, preamp, ten bands, tab-separated. Names cannot hold tabs or line breaks. */
    fun encode(list: List<EqPreset>): String =
        list.joinToString("\n") { p -> (listOf(p.name, num(p.preamp)) + p.bands.map(::num)).joinToString("\t") }

    /** Reads [encode]'s text back; lines that are damaged (no name, not ten bands, not numbers) are dropped, as are repeated names. */
    fun decode(text: String?): List<EqPreset> {
        if (text.isNullOrBlank()) return emptyList()
        val out = mutableListOf<EqPreset>()
        for (line in text.lineSequence()) {
            val parts = line.split('\t')
            if (parts.size != 2 + EqSettings.BAND_COUNT) continue
            val name = parts[0].trim()
            if (name.isEmpty() || out.any { it.name.equals(name, ignoreCase = true) }) continue
            val numbers = parts.drop(1).map { it.toDoubleOrNull() ?: Double.NaN }
            if (numbers.any { it.isNaN() || it.isInfinite() }) continue
            out += EqPreset(name, EqSettings.clamp(numbers[0]), numbers.drop(1).map(EqSettings::clamp))
        }
        return out
    }

    private fun num(d: Double) = EqSettings.clamp(d).toString()
}
