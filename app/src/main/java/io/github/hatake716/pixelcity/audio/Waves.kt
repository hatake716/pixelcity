package io.github.hatake716.pixelcity.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 音を式から作る。
 *
 * 絵と同じ考え方で、音も外部の素材を持たずコードで組み立てる。
 * 録音物・third-party の音源は一切使っていないので、配信時の権利の心配がない。
 *
 * 16ビット機の音源をまねて、次の波だけで作る。
 *  - 矩形波（[square]）: 主旋律と効果音。デューティ比で音色が変わる
 *  - 三角波（[triangle]）: 低音。柔らかく、輪郭がぼやける
 *  - 雑音（[noise]）: 打楽器と、壊す音
 *
 * すべて 22.05kHz・モノラル・16bit の PCM で返す。
 * 当時の機械に近い粗さで、データも小さくて済む。
 */
object Waves {

    /** 標本化周波数。当時の機械に近い粗さ。 */
    const val RATE = 22_050

    /** 音の名前から周波数へ。A4 = 440Hz を基準に、12平均律で並べる。 */
    fun freq(semitonesFromA4: Int): Float =
        440f * Math.pow(2.0, semitonesFromA4 / 12.0).toFloat()

    // 音名。C4 を中央のドとする。
    const val C3 = -21
    const val D3 = -19
    const val E3 = -17
    const val F3 = -16
    const val G3 = -14
    const val A3 = -12
    const val B3 = -10
    const val C4 = -9
    const val D4 = -7
    const val E4 = -5
    const val F4 = -4
    const val G4 = -2
    const val A4 = 0
    const val B4 = 2
    const val C5 = 3
    const val D5 = 5
    const val E5 = 7
    const val F5 = 8
    const val G5 = 10
    const val A5 = 12
    const val B5 = 14
    const val C6 = 15
    const val D6 = 17
    const val E6 = 19
    const val G6 = 22

    /** 休符。 */
    const val REST = Int.MIN_VALUE

    /**
     * 音を書き込む先。足し合わせられるよう、[Float] で持つ。
     * 最後に [toPcm] で 16bit に落とす。
     */
    class Track(val frames: Int) {
        val data = FloatArray(frames)

        fun add(at: Int, value: Float) {
            if (at in 0 until frames) data[at] += value
        }

        /**
         * 16bit PCM へ。音が重なって振り切れないよう、
         * 全体をならしてから丸める。
         */
        fun toPcm(gain: Float = 1f): ShortArray {
            var peak = 0f
            for (v in data) {
                val a = if (v < 0) -v else v
                if (a > peak) peak = a
            }
            // 振り切れるときだけ、全体を下げる
            val norm = if (peak > 1f) 1f / peak else 1f
            val g = gain * norm
            return ShortArray(frames) { i ->
                (data[i] * g * 32_000f).roundToInt().coerceIn(-32_767, 32_767).toShort()
            }
        }
    }

    /**
     * 音の包絡線。立ち上がりと減衰だけの簡単なもの。
     *
     * @param attack 立ち上がりにかける割合 0..1
     * @param decay 減衰の速さ。大きいほど早く消える
     */
    fun envelopeAt(t: Float, attack: Float, decay: Float): Float {
        if (t < attack) return t / attack
        return exp(-(t - attack) * decay)
    }

    /**
     * 矩形波。[duty] を変えると音色が変わる。
     * 0.5 = 太い音、0.25 = やや細い、0.125 = 細く硬い音。
     */
    fun square(
        track: Track, start: Int, frames: Int, hz: Float,
        amp: Float = 0.25f, duty: Float = 0.5f, decay: Float = 4f, attack: Float = 0.01f,
        /** 音の高さを時間で変える。効果音の「ピュン」に使う。 */
        bend: (Float) -> Float = { 1f },
    ) {
        var phase = 0f
        for (i in 0 until frames) {
            val t = i.toFloat() / frames
            val f = hz * bend(t)
            phase += f / RATE
            if (phase >= 1f) phase -= 1f
            val v = if (phase < duty) 1f else -1f
            track.add(start + i, v * amp * envelopeAt(t, attack, decay))
        }
    }

    /** 三角波。低音に使うと、矩形波とぶつからずに支えになる。 */
    fun triangle(
        track: Track, start: Int, frames: Int, hz: Float,
        amp: Float = 0.25f, decay: Float = 3f, attack: Float = 0.01f,
    ) {
        var phase = 0f
        for (i in 0 until frames) {
            val t = i.toFloat() / frames
            phase += hz / RATE
            if (phase >= 1f) phase -= 1f
            // 0..1 を -1..1 の三角に
            val v = 4f * kotlin.math.abs(phase - 0.5f) - 1f
            track.add(start + i, v * amp * envelopeAt(t, attack, decay))
        }
    }

    /** なめらかな正弦波。柔らかい音に使う。 */
    fun sine(
        track: Track, start: Int, frames: Int, hz: Float,
        amp: Float = 0.25f, decay: Float = 3f, attack: Float = 0.02f,
    ) {
        for (i in 0 until frames) {
            val t = i.toFloat() / frames
            val v = sin(2.0 * PI * hz * i / RATE).toFloat()
            track.add(start + i, v * amp * envelopeAt(t, attack, decay))
        }
    }

    /**
     * 雑音。打楽器や、壊す音に使う。
     *
     * 乱数の種を決め打ちにしているので、何度作っても同じ音になる。
     * 実行のたびに音が変わると、試験で確かめられない。
     */
    fun noise(
        track: Track, start: Int, frames: Int,
        amp: Float = 0.2f, decay: Float = 12f, seed: Int = 1,
        /** 低くすると、こもった音になる。 */
        smooth: Int = 1,
    ) {
        var r = seed * 1_103_515_245 + 12_345
        var held = 0f
        for (i in 0 until frames) {
            if (i % smooth == 0) {
                r = r * 1_103_515_245 + 12_345
                held = ((r ushr 16) and 0x7FFF) / 16_384f - 1f
            }
            val t = i.toFloat() / frames
            track.add(start + i, held * amp * envelopeAt(t, 0.005f, decay))
        }
    }

    /** 秒をフレーム数へ。 */
    fun frames(seconds: Float): Int = (seconds * RATE).roundToInt()
}
