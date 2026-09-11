package io.github.hatake716.pixelcity.audio

/**
 * 効果音。どれも [Waves] の式から作る。
 *
 * 街づくりの音は一日に何百回も鳴るので、
 *  - 短く（0.1〜0.3秒）
 *  - 音量は控えめに
 *  - 高すぎず、耳に刺さらないように
 * している。逆に、めったに鳴らない音（モニュメント完成・破産）は
 * 長めにして、出来事の大きさを伝える。
 */
enum class Sfx {
    /** 道を敷く。短く、軽く。 */
    ROAD,
    /** 区分を指定する。用途で高さを変え、住宅・商業・工業を聞き分けられるようにする。 */
    ZONE_R,
    ZONE_C,
    ZONE_I,
    /** 施設を建てる。区分より重い音。 */
    BUILD,
    /** 壊す。崩れる感じの雑音。 */
    BULLDOZE,
    /** 置けなかった。低く短い、拒む音。 */
    DENIED,
    /** 画面の釦を押す。 */
    TAP,
    /** 情報や予算の画面を開く。 */
    OPEN,
    /** 画面を閉じる。 */
    CLOSE,
    /** モニュメントが完成した。短い上昇音。 */
    MONUMENT,
    /** 人口の節目を越えた。 */
    MILESTONE,
    /** 災害が起きた。低くうなる。 */
    DISASTER,
    /** 破産。沈んでいく音。 */
    BANKRUPT,
    /** チュートリアルが1段進んだ。 */
    PROGRESS,
    ;

    /** この効果音の波形。 */
    fun render(): ShortArray = when (this) {
        ROAD -> build(0.10f) { t ->
            // 砂利を撒くような、短い雑音
            Waves.noise(t, 0, Waves.frames(0.08f), amp = 0.16f, decay = 34f, smooth = 3)
            Waves.square(t, 0, Waves.frames(0.05f), Waves.freq(Waves.A3),
                amp = 0.10f, duty = 0.25f, decay = 30f)
        }

        // 区分の3つは、同じ形で高さだけ変える。並べて聞くと和音になる。
        ZONE_R -> zoneTone(Waves.E4)
        ZONE_C -> zoneTone(Waves.A4)
        ZONE_I -> zoneTone(Waves.C4)

        BUILD -> build(0.22f) { t ->
            // 柱を打ち込むような、低い音の上に金属の響き
            Waves.triangle(t, 0, Waves.frames(0.18f), Waves.freq(Waves.C3),
                amp = 0.26f, decay = 14f)
            Waves.noise(t, 0, Waves.frames(0.06f), amp = 0.14f, decay = 40f, smooth = 2)
            Waves.square(t, Waves.frames(0.02f), Waves.frames(0.12f), Waves.freq(Waves.G4),
                amp = 0.10f, duty = 0.125f, decay = 16f)
        }

        BULLDOZE -> build(0.28f) { t ->
            // 崩れて散らばる音。こもった雑音を、だんだん静かに。
            Waves.noise(t, 0, Waves.frames(0.26f), amp = 0.24f, decay = 9f, smooth = 5)
            Waves.triangle(t, 0, Waves.frames(0.14f), Waves.freq(Waves.C3 - 5),
                amp = 0.20f, decay = 12f)
        }

        DENIED -> build(0.14f) { t ->
            // 下がる2音。短く断る感じ。
            Waves.square(t, 0, Waves.frames(0.06f), Waves.freq(Waves.E3),
                amp = 0.18f, duty = 0.5f, decay = 10f)
            Waves.square(t, Waves.frames(0.06f), Waves.frames(0.07f), Waves.freq(Waves.C3),
                amp = 0.18f, duty = 0.5f, decay = 10f)
        }

        TAP -> build(0.06f) { t ->
            Waves.square(t, 0, Waves.frames(0.045f), Waves.freq(Waves.A5),
                amp = 0.12f, duty = 0.25f, decay = 36f)
        }

        OPEN -> build(0.16f) { t ->
            // 上がる2音
            Waves.square(t, 0, Waves.frames(0.05f), Waves.freq(Waves.E5),
                amp = 0.13f, duty = 0.25f, decay = 20f)
            Waves.square(t, Waves.frames(0.05f), Waves.frames(0.09f), Waves.freq(Waves.A5),
                amp = 0.13f, duty = 0.25f, decay = 16f)
        }

        CLOSE -> build(0.16f) { t ->
            // 下がる2音。開くの逆。
            Waves.square(t, 0, Waves.frames(0.05f), Waves.freq(Waves.A5),
                amp = 0.13f, duty = 0.25f, decay = 20f)
            Waves.square(t, Waves.frames(0.05f), Waves.frames(0.09f), Waves.freq(Waves.E5),
                amp = 0.13f, duty = 0.25f, decay = 16f)
        }

        MONUMENT -> build(0.90f) { t ->
            // ドミソド。短いファンファーレ。
            val notes = intArrayOf(Waves.C5, Waves.E5, Waves.G5, Waves.C6)
            for ((i, n) in notes.withIndex()) {
                val at = Waves.frames(0.11f * i)
                val len = if (i == notes.size - 1) Waves.frames(0.52f) else Waves.frames(0.16f)
                Waves.square(t, at, len, Waves.freq(n), amp = 0.20f, duty = 0.5f, decay = 3.4f)
                Waves.square(t, at, len, Waves.freq(n + 12), amp = 0.07f, duty = 0.25f, decay = 4f)
            }
            Waves.triangle(t, 0, Waves.frames(0.86f), Waves.freq(Waves.C3), amp = 0.20f, decay = 2.2f)
        }

        MILESTONE -> build(0.52f) { t ->
            val notes = intArrayOf(Waves.G4, Waves.C5, Waves.E5)
            for ((i, n) in notes.withIndex()) {
                val at = Waves.frames(0.09f * i)
                Waves.square(t, at, Waves.frames(0.30f), Waves.freq(n),
                    amp = 0.17f, duty = 0.25f, decay = 5f)
            }
        }

        DISASTER -> build(0.80f) { t ->
            // 低くうなる音と、割れるような雑音
            Waves.square(t, 0, Waves.frames(0.76f), Waves.freq(Waves.C3 - 12),
                amp = 0.22f, duty = 0.5f, decay = 1.6f) { p -> 1f - p * 0.25f }
            Waves.noise(t, 0, Waves.frames(0.70f), amp = 0.16f, decay = 3.4f, smooth = 7)
        }

        BANKRUPT -> build(1.10f) { t ->
            // 半音ずつ下がっていく。沈む感じ。
            for (i in 0 until 6) {
                Waves.square(t, Waves.frames(0.16f * i), Waves.frames(0.26f),
                    Waves.freq(Waves.A3 - i * 2), amp = 0.18f, duty = 0.5f, decay = 5f)
            }
            Waves.triangle(t, 0, Waves.frames(1.05f), Waves.freq(Waves.C3 - 12),
                amp = 0.18f, decay = 1.4f)
        }

        PROGRESS -> build(0.30f) { t ->
            Waves.square(t, 0, Waves.frames(0.08f), Waves.freq(Waves.C5),
                amp = 0.15f, duty = 0.25f, decay = 14f)
            Waves.square(t, Waves.frames(0.08f), Waves.frames(0.20f), Waves.freq(Waves.G5),
                amp = 0.15f, duty = 0.25f, decay = 7f)
        }
    }

    /** 区分の音。用途ごとに高さだけ変える。 */
    private fun zoneTone(note: Int): ShortArray = build(0.16f) { t ->
        Waves.square(t, 0, Waves.frames(0.10f), Waves.freq(note),
            amp = 0.16f, duty = 0.25f, decay = 16f)
        Waves.square(t, Waves.frames(0.04f), Waves.frames(0.10f), Waves.freq(note + 7),
            amp = 0.10f, duty = 0.125f, decay = 16f)
        Waves.noise(t, 0, Waves.frames(0.03f), amp = 0.08f, decay = 50f, smooth = 2)
    }

    private fun build(seconds: Float, body: (Waves.Track) -> Unit): ShortArray {
        val track = Waves.Track(Waves.frames(seconds))
        body(track)
        // 音量をそろえる。式のままだと控えめすぎて、
        // 端末の音量を上げないと聞こえない。
        return track.toPcm(gain = LOUDNESS)
    }

    private companion object {
        /**
         * 効果音全体の音量。
         *
         * 個々の音の振幅は「重ねたときに割れない」ことを優先して小さめにしてある。
         * そのぶんを最後にまとめて持ち上げる。
         * [Waves.Track.toPcm] が振り切れる前にならすので、割れることはない。
         */
        const val LOUDNESS = 2.6f
    }
}
