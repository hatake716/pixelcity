package io.github.hatake716.pixelcity.audio

/** Tactile city-building sounds and a related family of soft civic notification chimes. */
enum class Sfx {
    ROAD, ZONE_R, ZONE_C, ZONE_I, BUILD, BULLDOZE, DENIED, TAP, OPEN, CLOSE,
    MONUMENT, MILESTONE, DISASTER, BANKRUPT, PROGRESS;

    fun render(): ShortArray = when (this) {
        ROAD -> build(0.20f) { t ->
            Instruments.texture(t, 0f, 0.16f, 0.28f, 71, 0.16f, 5f)
            Instruments.kick(t, 0.012f, 0.20f)
            tone(t, 0.045f, 0.11f, 48, Instruments.Voice.MALLET, 0.12f)
        }
        ZONE_R -> zone(64)
        ZONE_C -> zone(69)
        ZONE_I -> zone(57)
        BUILD -> build(0.32f) { t ->
            Instruments.kick(t, 0f, 0.30f)
            Instruments.texture(t, 0f, 0.18f, 0.3f, 91, 0.12f, 6f)
            tone(t, 0.045f, 0.22f, 55, Instruments.Voice.MALLET, 0.16f)
            tone(t, 0.085f, 0.20f, 62, Instruments.Voice.KEYS, 0.11f)
        }
        BULLDOZE -> build(0.48f) { t ->
            Instruments.texture(t, 0f, 0.42f, 0.48f, 23, 0.06f, 3f)
            Instruments.kick(t, 0.015f, 0.27f)
            for (i in 0..3) tone(t, i * 0.065f, 0.17f, 48 - i * 3, Instruments.Voice.MALLET, 0.09f)
        }
        DENIED -> chime(0.27f, intArrayOf(57, 53), 0.085f, Instruments.Voice.FELT)
        TAP -> build(0.075f) { t ->
            tone(t, 0f, 0.060f, 76, Instruments.Voice.MALLET, 0.26f)
            Instruments.texture(t, 0f, 0.025f, 0.10f, 4, 0.3f)
        }
        OPEN -> chime(0.28f, intArrayOf(64, 71), 0.055f)
        CLOSE -> chime(0.24f, intArrayOf(71, 64), 0.045f, Instruments.Voice.FELT)
        PROGRESS -> chime(0.48f, intArrayOf(67, 71, 74), 0.085f)
        MILESTONE -> chime(0.95f, intArrayOf(64, 67, 74, 76), 0.12f)
        MONUMENT -> build(1.75f) { t ->
            for ((i, n) in intArrayOf(60, 67, 71, 74, 76).withIndex()) {
                tone(t, i * 0.14f, 0.70f, n, Instruments.Voice.KEYS, 0.13f)
            }
            for (n in intArrayOf(48, 55, 59, 64)) tone(t, 0.54f, 1.12f, n, Instruments.Voice.BRASS, 0.065f)
        }
        DISASTER -> build(1.25f) { t ->
            // A restrained two-pulse alert, distinct from celebrations and continuous sirens.
            for (at in listOf(0f, 0.40f)) {
                tone(t, at, 0.35f, 57, Instruments.Voice.BRASS, 0.14f)
                tone(t, at, 0.35f, 63, Instruments.Voice.BRASS, 0.09f)
            }
            Instruments.texture(t, 0.03f, 1.12f, 0.22f, 51, 0.04f, 4f)
        }
        BANKRUPT -> build(1.7f) { t ->
            for ((i, n) in intArrayOf(65, 64, 60, 57).withIndex()) {
                tone(t, i * 0.24f, 0.72f, n, Instruments.Voice.FELT, 0.15f)
            }
            tone(t, 0f, 1.55f, 38, Instruments.Voice.PAD, 0.11f)
        }
    }

    private fun tone(t: Waves.Track, at: Float, seconds: Float, midi: Int,
                     voice: Instruments.Voice = Instruments.Voice.KEYS, amp: Float = 0.18f) =
        Instruments.note(t, at, seconds, midi - 69, voice, amp)

    private fun zone(midi: Int) = build(0.23f) { t ->
        Instruments.texture(t, 0f, 0.045f, 0.18f, midi, 0.22f)
        tone(t, 0f, 0.17f, midi, Instruments.Voice.MALLET, 0.19f)
        tone(t, 0.045f, 0.16f, midi + 7, Instruments.Voice.KEYS, 0.12f)
    }

    private fun chime(seconds: Float, notes: IntArray, spacing: Float,
                      voice: Instruments.Voice = Instruments.Voice.KEYS) = build(seconds) { t ->
        for ((i, n) in notes.withIndex()) tone(t, i * spacing,
            (seconds - i * spacing) * 0.85f, n, voice, 0.18f)
    }

    private fun build(seconds: Float, body: (Waves.Track) -> Unit): ShortArray {
        val t = Waves.Track(Waves.frames(seconds)); body(t)
        // Common sounds remain short and quieter than major notifications.
        return Instruments.finish(t, loop = false, targetRms = if (this == TAP) 0.085f else 0.13f)
    }
}
