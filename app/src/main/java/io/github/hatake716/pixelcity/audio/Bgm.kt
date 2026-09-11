package io.github.hatake716.pixelcity.audio

/**
 * 背景で流す曲。効果音と同じく、式から組み立てる。
 *
 * 街づくりは何時間も続く遊びなので、曲は
 *  - 主張しすぎない（聞き流せる和音の進行）
 *  - 繰り返しても角が立たない（8小節で無理なく戻る）
 *  - 街の様子で変わる（同じ曲を延々と聞かせない）
 * ことを狙っている。
 *
 * 曲は3つ。街の大きさと、うまくいっているかで選ぶ。
 */
enum class Bgm(val label: String) {
    /** 始まりの街。のんびりした明るい曲。 */
    DAWN("あさ"),

    /** 育ってきた街。動きのある曲。 */
    CITY("まち"),

    /** 苦しいとき。短調で、沈んだ曲。 */
    TROUBLE("くるしいとき"),
    ;

    /**
     * 1周ぶんの波形。そのまま繰り返して流す。
     *
     * 繋ぎ目で音が途切れないよう、最後の音は小節に収め、
     * 残響を次の周へまたがせない。
     */
    fun render(): ShortArray = when (this) {
        DAWN -> song(
            bpm = 96f,
            // ハ長調。C - Am - F - G の、よくある進行。
            chords = arrayOf(
                intArrayOf(Waves.C3, Waves.E3, Waves.G3),
                intArrayOf(Waves.A3 - 12, Waves.C3, Waves.E3),
                intArrayOf(Waves.F3, Waves.A3, Waves.C4),
                intArrayOf(Waves.G3, Waves.B3, Waves.D4),
            ),
            melody = intArrayOf(
                Waves.E5, Waves.REST, Waves.G5, Waves.REST,
                Waves.E5, Waves.D5, Waves.C5, Waves.REST,
                Waves.A4, Waves.REST, Waves.C5, Waves.REST,
                Waves.E5, Waves.REST, Waves.D5, Waves.REST,
                Waves.F5, Waves.REST, Waves.E5, Waves.D5,
                Waves.C5, Waves.REST, Waves.A4, Waves.REST,
                Waves.G4, Waves.B4, Waves.D5, Waves.REST,
                Waves.C5, Waves.REST, Waves.REST, Waves.REST,
            ),
            duty = 0.25f,
            drums = false,
        )

        CITY -> song(
            bpm = 112f,
            // ト長調。動きを出すため、少し速く、打楽器を入れる。
            chords = arrayOf(
                intArrayOf(Waves.G3, Waves.B3, Waves.D4),
                intArrayOf(Waves.D3, Waves.F3 + 1, Waves.A3),
                intArrayOf(Waves.E3, Waves.G3, Waves.B3),
                intArrayOf(Waves.C4, Waves.E4, Waves.G4),
            ),
            melody = intArrayOf(
                Waves.D5, Waves.G5, Waves.REST, Waves.G5,
                Waves.F5 + 1, Waves.REST, Waves.D5, Waves.REST,
                Waves.A4, Waves.D5, Waves.REST, Waves.D5,
                Waves.C5, Waves.REST, Waves.A4, Waves.REST,
                Waves.B4, Waves.E5, Waves.REST, Waves.E5,
                Waves.D5, Waves.REST, Waves.B4, Waves.REST,
                Waves.C5, Waves.E5, Waves.G5, Waves.REST,
                Waves.G5, Waves.REST, Waves.REST, Waves.REST,
            ),
            duty = 0.5f,
            drums = true,
        )

        TROUBLE -> song(
            bpm = 84f,
            // イ短調。重く、ゆっくり。
            chords = arrayOf(
                intArrayOf(Waves.A3 - 12, Waves.C3, Waves.E3),
                intArrayOf(Waves.F3 - 12, Waves.A3 - 12, Waves.C3),
                intArrayOf(Waves.D3 - 12, Waves.F3, Waves.A3),
                intArrayOf(Waves.E3 - 12, Waves.G3, Waves.B3),
            ),
            melody = intArrayOf(
                Waves.A4, Waves.REST, Waves.REST, Waves.REST,
                Waves.C5, Waves.REST, Waves.B4, Waves.REST,
                Waves.A4, Waves.REST, Waves.REST, Waves.REST,
                Waves.F4, Waves.REST, Waves.G4, Waves.REST,
                Waves.A4, Waves.REST, Waves.C5, Waves.REST,
                Waves.D5, Waves.REST, Waves.C5, Waves.REST,
                Waves.B4, Waves.REST, Waves.A4, Waves.REST,
                Waves.A4, Waves.REST, Waves.REST, Waves.REST,
            ),
            duty = 0.5f,
            drums = false,
        )
    }

    /**
     * 曲を組み立てる。
     *
     * [melody] は16分ではなく8分音符で並べる（1小節8つ）。
     * [chords] は1小節に1つ。4小節を2回まわして8小節にする。
     */
    private fun song(
        bpm: Float,
        chords: Array<IntArray>,
        melody: IntArray,
        duty: Float,
        drums: Boolean,
    ): ShortArray {
        /** 8分音符1つぶんのフレーム数。 */
        val step = Waves.frames(30f / bpm)
        val total = step * melody.size
        val track = Waves.Track(total)

        // --- 伴奏。小節ごとに和音を刻む ---
        val perBar = 8
        for (bar in 0 until melody.size / perBar) {
            val chord = chords[bar % chords.size]
            val at = bar * perBar * step
            // 低音。小節のあたまと、半分のところ。
            for (half in 0 until 2) {
                Waves.triangle(
                    track, at + half * perBar / 2 * step, step * 3,
                    Waves.freq(chord[0] - 12), amp = 0.17f, decay = 2.6f,
                )
            }
            // 和音。裏拍で軽く刻む。
            for (k in 0 until perBar) {
                if (k % 2 == 0) continue
                for (n in chord) {
                    Waves.square(
                        track, at + k * step, (step * 0.9f).toInt(),
                        Waves.freq(n), amp = 0.045f, duty = 0.25f, decay = 9f,
                    )
                }
            }
        }

        // --- 主旋律 ---
        for ((i, note) in melody.withIndex()) {
            if (note == Waves.REST) continue
            // 次が休符なら音を伸ばす（切れ切れにならないように）
            var len = 1
            while (i + len < melody.size && melody[i + len] == Waves.REST) len++
            Waves.square(
                track, i * step, (step * len * 0.92f).toInt(),
                Waves.freq(note), amp = 0.16f, duty = duty, decay = 2.2f, attack = 0.02f,
            )
        }

        // --- 打楽器 ---
        if (drums) {
            for (k in 0 until melody.size) {
                val at = k * step
                when (k % 8) {
                    0, 4 -> Waves.noise(track, at, step / 2, amp = 0.14f, decay = 30f, smooth = 9)
                    2, 6 -> Waves.noise(track, at, step / 3, amp = 0.09f, decay = 44f, smooth = 2)
                }
            }
        }
        // 繰り返しの継ぎ目をなめらかにする。
        // 頭と尻が急に鳴り出す/切れると、一周ごとにプチッと鳴る。
        val edge = Waves.frames(0.012f)
        for (i in 0 until edge) {
            val k = i.toFloat() / edge
            track.data[i] *= k
            track.data[total - 1 - i] *= k
        }
        return track.toPcm(gain = 0.85f)
    }
}
