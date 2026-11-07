package net.codekunde.fernplay.android.eq

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * The EQ in ExoPlayer's audio chain (decoder → this → output). It reads
 * [settings] for every buffer, so a slider moves the sound at once without
 * rebuilding the player.
 *
 * It takes 16-bit and float PCM, what Android's decoders give, and stays in the
 * chain while the EQ is off (switching it on mid-song must not need a new
 * format); off or flat, samples are copied unchanged.
 */
@UnstableApi
class EqualizerProcessor(private val settings: () -> EqSettings) : BaseAudioProcessor() {

    private var filter: EqFilter? = null
    private var scratch = FloatArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val e = inputAudioFormat.encoding
        if (e != C.ENCODING_PCM_16BIT && e != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val bytes = inputBuffer.remaining()
        if (bytes == 0) return
        val out = replaceOutputBuffer(bytes)
        val f = filter
        val eq = settings()
        if (f == null || !f.isActive(eq)) {
            out.put(inputBuffer)
            out.flip()
            return
        }
        val float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val samples = bytes / (if (float) 4 else 2)
        if (scratch.size < samples) scratch = FloatArray(samples)
        val s = scratch
        val start = inputBuffer.position()
        if (float) {
            for (i in 0 until samples) s[i] = inputBuffer.getFloat(start + i * 4)
        } else {
            for (i in 0 until samples) s[i] = inputBuffer.getShort(start + i * 2) / 32768f
        }
        f.process(s, samples / f.channels, eq)
        if (float) {
            for (i in 0 until samples) out.putFloat(s[i])
        } else {
            for (i in 0 until samples) out.putShort((s[i].coerceIn(-1f, 1f) * 32767f).roundToInt().toShort())
        }
        inputBuffer.position(inputBuffer.limit())
        out.flip()
    }

    override fun onFlush() {
        // After a flush the configured format is the one in use; a seek also lands here.
        val fmt = inputAudioFormat
        val f = filter
        filter = when {
            fmt.sampleRate <= 0 || fmt.channelCount <= 0 -> null
            f != null && f.sampleRate == fmt.sampleRate && f.channels == fmt.channelCount -> f.also { it.reset() }
            else -> EqFilter(fmt.sampleRate, fmt.channelCount)
        }
    }

    override fun onReset() {
        filter = null
        scratch = FloatArray(0)
    }
}
