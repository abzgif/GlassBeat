package com.bytegif.glassbeat.ui.equalizer

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tanh

data class EqualizerPreset(
    val name: String,
    val description: String,
    val levelsMilliBels: IntArray
)

object EqualizerSpecs {
    const val BANDS_COUNT = 5
    const val MIN_LEVEL_MB = -1500 // -15 dB
    const val MAX_LEVEL_MB = 1500  // +15 dB

    val bandCenterFreqsHz = listOf(60, 230, 910, 4000, 14000)

    val presets = listOf(
        EqualizerPreset("Bass Boost", "+6dB Sub-Bass", intArrayOf(600, 400, 0, 100, 200)),
        EqualizerPreset("Classical", "Natural Acoustic", intArrayOf(400, 200, 100, 200, 400)),
        EqualizerPreset("Dance", "Energetic Highs", intArrayOf(500, 200, 100, 400, 500)),
        EqualizerPreset("Folk", "Acoustic Warmth", intArrayOf(300, 100, 0, 200, -100)),
        EqualizerPreset("Heavy Metal", "Heavy Punch", intArrayOf(500, 200, 500, 200, 100)),
        EqualizerPreset("Hip Hop", "Deep Beats", intArrayOf(500, 400, 0, 200, 300)),
        EqualizerPreset("Jazz", "Warm Sax & Piano", intArrayOf(300, 200, -100, 200, 400)),
        EqualizerPreset("Pop", "Forward Vocals", intArrayOf(-100, 200, 400, 300, -100)),
        EqualizerPreset("Rock", "V-Shape Punch", intArrayOf(400, 200, -100, 200, 500)),
        EqualizerPreset("Custom", "Manual Tuning", intArrayOf(0, 0, 0, 0, 0))
    )

    fun formatFrequency(hz: Int): String =
        if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"

    fun formatDb(milliBels: Int): String {
        val db = milliBels / 100
        return if (db > 0) "+$db dB" else "$db dB"
    }
}

/**
 * Pure-Kotlin 5-Band Direct-Form II Transposed (DF2T) Biquad Cascade Equalizer Processor
 * with smooth parameter interpolation and tanh soft-clipping limiter.
 * Directly ported from GlassBeat Android's EqualizerAudioProcessor.
 */
class EqualizerAudioProcessor {

    @Volatile
    private var isEqEnabled = false
    private val bandGainsDb = FloatArray(5)

    private var targetPreampScale = 1.0f
    private var currentPreampScale = 1.0f

    private val bandCenterFreqs = floatArrayOf(60f, 230f, 910f, 4000f, 14000f)

    // DF2T state memory: 2 delay elements per band per stereo channel
    private val s1 = Array(5) { FloatArray(2) }
    private val s2 = Array(5) { FloatArray(2) }

    private val targetB0 = FloatArray(5) { 1f }
    private val targetB1 = FloatArray(5)
    private val targetB2 = FloatArray(5)
    private val targetA1 = FloatArray(5)
    private val targetA2 = FloatArray(5)

    private val currentB0 = FloatArray(5) { 1f }
    private val currentB1 = FloatArray(5)
    private val currentB2 = FloatArray(5)
    private val currentA1 = FloatArray(5)
    private val currentA2 = FloatArray(5)

    private var currentSampleRate = 44100

    fun configure(sampleRate: Int) {
        if (sampleRate > 0) {
            currentSampleRate = sampleRate
            recalculateCoefficients(currentSampleRate)
        }
    }

    fun updateSettings(enabled: Boolean, milliBelLevels: List<Int>) {
        isEqEnabled = enabled
        for (i in 0 until minOf(5, milliBelLevels.size)) {
            bandGainsDb[i] = milliBelLevels[i] / 100f
        }
        targetPreampScale = 1.0f
        recalculateCoefficients(currentSampleRate)
    }

    fun resetState() {
        for (b in 0 until 5) {
            s1[b][0] = 0f
            s1[b][1] = 0f
            s2[b][0] = 0f
            s2[b][1] = 0f
        }
    }

    private fun recalculateCoefficients(sampleRate: Int) {
        if (sampleRate <= 0) return
        val fs = sampleRate.toFloat()
        val q = 0.9f

        for (i in 0 until 5) {
            val gainDb = if (isEqEnabled) bandGainsDb[i] else 0f
            val f0 = bandCenterFreqs[i]

            if (gainDb == 0f || f0 >= fs / 2f) {
                targetB0[i] = 1f
                targetB1[i] = 0f
                targetB2[i] = 0f
                targetA1[i] = 0f
                targetA2[i] = 0f
                continue
            }

            val a = 10f.pow(gainDb / 40f)
            val w0 = (2f * Math.PI.toFloat() * f0 / fs)
            val alpha = sin(w0) / (2f * q)
            val cosW0 = cos(w0)

            val b0Raw = 1f + alpha * a
            val b1Raw = -2f * cosW0
            val b2Raw = 1f - alpha * a
            val a0Raw = 1f + alpha / a
            val a1Raw = -2f * cosW0
            val a2Raw = 1f - alpha / a

            targetB0[i] = b0Raw / a0Raw
            targetB1[i] = b1Raw / a0Raw
            targetB2[i] = b2Raw / a0Raw
            targetA1[i] = a1Raw / a0Raw
            targetA2[i] = a2Raw / a0Raw
        }
    }

    private fun softLimit(sample: Float): Float {
        val threshold = 26000f
        val maxAmp = 32767f
        return when {
            sample > threshold -> threshold + (maxAmp - threshold) * tanh((sample - threshold) / (maxAmp - threshold))
            sample < -threshold -> -threshold + (-32768f + threshold) * tanh((sample + threshold) / (-32768f - threshold))
            else -> sample
        }
    }

    /**
     * Processes 16-bit Little-Endian PCM samples in-place.
     */
    fun processPcm16Le(buffer: ByteArray, bytesRead: Int, channelCount: Int, volume: Float = 1.0f) {
        if (bytesRead <= 0 || channelCount !in 1..2) return

        val smoothingFactor = 0.15f
        currentPreampScale += smoothingFactor * (targetPreampScale - currentPreampScale)
        for (b in 0 until 5) {
            currentB0[b] += smoothingFactor * (targetB0[b] - currentB0[b])
            currentB1[b] += smoothingFactor * (targetB1[b] - currentB1[b])
            currentB2[b] += smoothingFactor * (targetB2[b] - currentB2[b])
            currentA1[b] += smoothingFactor * (targetA1[b] - currentA1[b])
            currentA2[b] += smoothingFactor * (targetA2[b] - currentA2[b])
        }

        val volScale = volume.coerceIn(0f, 1f)
        var p = 0
        while (p < bytesRead) {
            for (ch in 0 until channelCount) {
                if (p + 1 >= bytesRead) break
                val sampleShort = (buffer[p].toInt() and 0xFF) or (buffer[p + 1].toInt() shl 8)
                val sampleShortSigned = sampleShort.toShort()
                var sampleFloat = sampleShortSigned.toFloat() * currentPreampScale * volScale

                if (isEqEnabled) {
                    for (b in 0 until 5) {
                        val outFloat = currentB0[b] * sampleFloat + s1[b][ch]
                        s1[b][ch] = currentB1[b] * sampleFloat - currentA1[b] * outFloat + s2[b][ch]
                        s2[b][ch] = currentB2[b] * sampleFloat - currentA2[b] * outFloat
                        sampleFloat = outFloat
                    }
                }

                val limitedFloat = softLimit(sampleFloat)
                val outShortClamped = limitedFloat.coerceIn(-32768f, 32767f).toInt().toShort()
                buffer[p] = (outShortClamped.toInt() and 0xFF).toByte()
                buffer[p + 1] = ((outShortClamped.toInt() shr 8) and 0xFF).toByte()
                p += 2
            }
        }
    }
}
