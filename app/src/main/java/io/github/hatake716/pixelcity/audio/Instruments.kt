package io.github.hatake716.pixelcity.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Original, band-limited additive timbres. No recordings or sampled instruments. */
object Instruments {
    enum class Voice { KEYS, FELT, MALLET, BASS, PAD, FLUTE, BRASS }

    // A lookup table keeps long arrangements cheap enough to render off the UI thread.
    private val sine = FloatArray(16_384) { sin(2.0 * PI * it / 16_384).toFloat() }
    private fun osc(phase: Float): Float = sine[(phase * 16_384).toInt() and 16_383]

    fun note(t: Waves.Track, at: Float, seconds: Float, pitch: Int, voice: Voice, level: Float) {
        val start = Waves.frames(at)
        val count = Waves.frames(seconds)
        val hz = Waves.freq(pitch)
        val attack = when (voice) { Voice.PAD -> 0.28f; Voice.FLUTE, Voice.BRASS -> 0.045f; else -> 0.006f }
        val release = min(seconds * 0.3f, if (voice == Voice.PAD) 0.6f else 0.12f)
        val harmonics = when (voice) {
            Voice.KEYS -> floatArrayOf(1f, 0.30f, 0.12f, 0.07f)
            Voice.FELT -> floatArrayOf(1f, 0.22f, 0.08f)
            Voice.MALLET -> floatArrayOf(1f, 0.18f, 0.32f)
            Voice.BASS -> floatArrayOf(1f, 0.36f, 0.12f)
            Voice.PAD -> floatArrayOf(1f, 0.30f, 0.13f)
            Voice.FLUTE -> floatArrayOf(1f, 0.14f, 0.06f)
            Voice.BRASS -> floatArrayOf(1f, 0.42f, 0.23f, 0.10f)
        }
        val ratios = if (voice == Voice.MALLET) floatArrayOf(1f, 2.76f, 4f)
            else FloatArray(harmonics.size) { it + 1f }
        val phases = FloatArray(harmonics.size)
        val steps = FloatArray(harmonics.size) { hz * ratios[it] / Waves.RATE }
        val decays = FloatArray(harmonics.size) {
            val speed = when (voice) {
                Voice.PAD, Voice.FLUTE, Voice.BRASS -> 0.25f
                Voice.BASS -> 2.5f
                Voice.MALLET -> 3.5f + it * 3f
                else -> 1.5f + it * 3.2f
            }
            exp(-speed / Waves.RATE)
        }
        val amplitudes = harmonics.copyOf()
        var detune = 0f
        for (i in 0 until count) {
            val time = i.toFloat() / Waves.RATE
            val env = min(1f, time / attack) * min(1f, (seconds - time) / release).coerceAtLeast(0f)
            var sample = 0f
            for (h in harmonics.indices) {
                // Do not synthesize a partial above Nyquist.
                if (hz * ratios[h] < Waves.RATE * 0.45f) sample += osc(phases[h]) * amplitudes[h]
                phases[h] = (phases[h] + steps[h]) % 1f
                amplitudes[h] *= decays[h]
            }
            if (voice == Voice.PAD || voice == Voice.KEYS) {
                detune = (detune + hz * 1.002f / Waves.RATE) % 1f
                sample += osc(detune) * amplitudes[0] * 0.16f
            }
            t.add(start + i, sample * env * level)
        }
    }

    fun kick(t: Waves.Track, at: Float, level: Float) {
        val start = Waves.frames(at); val n = Waves.frames(0.22f)
        var phase = 0f
        for (i in 0 until n) {
            val seconds = i.toFloat() / Waves.RATE
            phase += (48f + 75f * exp(-seconds * 35f)) / Waves.RATE
            phase %= 1f
            val edge = min(1f, i / 80f) * min(1f, (n - 1 - i) / 240f)
            t.add(start + i, osc(phase) * exp(-seconds * 22f) * edge * level)
        }
    }

    /** Filtered noise, from soft brushes to grit; never sample-and-hold aliasing. */
    fun texture(t: Waves.Track, at: Float, seconds: Float, level: Float, seed: Int,
                brightness: Float = 0.3f, decay: Float = 6f) {
        var rng = seed + 1
        var filtered = 0f
        val n = Waves.frames(seconds); val start = Waves.frames(at)
        for (i in 0 until n) {
            rng = rng * 1_664_525 + 1_013_904_223
            val white = ((rng ushr 8) / 8_388_608f - 1f)
            filtered += brightness * (white - filtered)
            val env = min(1f, i / 64f) * min(1f, (n - 1 - i) / 160f) * exp(-decay * i / n)
            t.add(start + i, filtered * env * level)
        }
    }

    /** Quiet room reflections wrap around the loop so reverb does not disappear at the seam. */
    fun finish(t: Waves.Track, loop: Boolean, targetRms: Float = 0.11f): ShortArray {
        val dry = t.data.copyOf()
        for ((seconds, level) in listOf(0.113f to 0.13f, 0.197f to 0.09f, 0.307f to 0.055f)) {
            val delay = Waves.frames(seconds)
            for (i in dry.indices) {
                val dest = i + delay
                if (dest < t.frames) t.data[dest] += dry[i] * level
                else if (loop) t.data[dest % t.frames] += dry[i] * level
            }
        }
        val edge = Waves.frames(if (loop) 0.009f else 0.006f).coerceAtMost(t.frames / 2)
        for (i in 0 until edge) {
            val fade = i.toFloat() / edge
            t.data[i] *= fade; t.data[t.frames - i - 1] *= fade
        }
        val rms = sqrt(t.data.sumOf { it.toDouble() * it } / t.frames).toFloat()
        return t.toPcm(if (rms > 0f) (targetRms / rms).coerceAtMost(2.5f) else 1f)
    }
}
