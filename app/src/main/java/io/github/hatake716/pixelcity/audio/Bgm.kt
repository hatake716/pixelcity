package io.github.hatake716.pixelcity.audio

import io.github.hatake716.pixelcity.game.Campaign
import java.util.concurrent.CancellationException

/** Original 32-bar A–A′–B–A″ arrangements for a warm, thoughtful city simulation. */
enum class Bgm(val label: String, val bpm: Int) {
    DAWN("市庁舎の朝", 96),
    CITY("並木通りの午後", 108),
    GARDEN("風の通る田園", 84),
    INDUSTRY("明日をつくる街", 112),
    TROUBLE("灯りの残る執務室", 78);

    val durationSeconds: Float get() = 32 * 4 * 60f / bpm

    // Scores use MIDI pitches; no transcriptions or recordings from other games.
    private data class Bar(val bass: Int, val chord: IntArray, val lead: IntArray)
    private fun bar(bass: Int, vararg notes: Int): Bar =
        Bar(bass, notes.take(4).toIntArray(), notes.drop(4).toIntArray())

    fun render(): ShortArray {
        val verse = when (this) {
            DAWN -> arrayOf(
                bar(48, 64,67,71,74, 76,74,71), bar(45, 60,64,67,71, 72,76),
                bar(50, 65,69,72,76, 77,76,72), bar(43, 65,69,71,76, 74,71),
                bar(41, 64,67,69,72, 72,69,67), bar(40, 62,67,71,74, 71,74),
                bar(50, 60,65,69,76, 77,74,72), bar(43, 62,65,69,71, 71,69,74))
            CITY -> arrayOf(
                bar(41, 57,60,64,67, 76,72,74), bar(43, 59,62,65,69, 77,74),
                bar(40, 55,59,62,66, 74,71,69), bar(45, 55,60,64,71, 72,76),
                bar(50, 60,65,69,72, 77,76,72), bar(43, 59,65,69,76, 74,77),
                bar(48, 59,64,67,74, 76,74,71), bar(48, 58,64,67,69, 72,69))
            GARDEN -> arrayOf(
                bar(50, 66,69,73,76, 78,76,73), bar(47, 62,66,69,73, 74,78),
                bar(43, 59,62,66,69, 78,76,74), bar(45, 61,64,67,71, 76,73),
                bar(54, 61,64,69,73, 73,69,71), bar(47, 62,66,69,73, 74,73),
                bar(40, 59,62,66,69, 71,74,78), bar(45, 59,62,64,69, 76,74))
            INDUSTRY -> arrayOf(
                bar(45, 60,64,67,71, 72,71,67), bar(41, 57,60,64,67, 69,72),
                bar(48, 59,62,64,67, 71,74,76), bar(43, 59,62,67,69, 74,71),
                bar(50, 60,64,65,69, 77,76,72), bar(45, 60,64,67,71, 76,72),
                bar(41, 57,60,64,67, 69,67,72), bar(40, 59,62,64,68, 71,68))
            TROUBLE -> arrayOf(
                bar(50, 60,64,65,69, 69,72), bar(46, 57,60,62,65, 65,69),
                bar(43, 58,62,65,69, 70,69), bar(45, 55,61,64,70, 73,70),
                bar(50, 60,64,65,69, 72,69), bar(41, 57,60,64,67, 67,64),
                bar(43, 58,62,65,69, 65,69), bar(45, 55,61,64,70, 70,73))
        }
        val bridge = when (this) {
            GARDEN -> arrayOf(
                bar(43, 59,62,66,69, 74,78), bar(45, 61,64,67,71, 76,73),
                bar(54, 61,64,69,73, 73,76), bar(47, 57,62,66,69, 74,71))
            TROUBLE -> arrayOf(
                bar(46, 57,60,62,65, 74,72), bar(48, 58,62,65,69, 74,77),
                bar(41, 57,60,64,67, 76,72), bar(45, 55,61,64,70, 70,69))
            else -> arrayOf(
                bar(46, 57,60,62,65, 74,72), bar(43, 58,62,65,69, 77,74),
                bar(44, 60,63,67,70, 75,79), bar(43, 59,62,65,69, 77,74))
        }
        val beat = 60f / bpm
        val track = Waves.Track(Waves.frames(durationSeconds))
        val leadVoice = when (this) {
            GARDEN -> Instruments.Voice.FLUTE
            INDUSTRY -> Instruments.Voice.MALLET
            TROUBLE -> Instruments.Voice.FELT
            else -> Instruments.Voice.KEYS
        }
        fun note(at: Float, length: Float, midi: Int, voice: Instruments.Voice, amp: Float) =
            Instruments.note(track, at * beat, length * beat, midi - 69, voice, amp)
        for (b in 0 until 32) {
            if (Thread.currentThread().isInterrupted) throw CancellationException()
            val section = b / 8
            val score = if (section == 2) bridge[(b % 8) / 2] else verse[b % 8]
            val at = b * 4f
            // Warm extended harmony, gently voiced; the bridge opens up into sustained pads.
            for ((i, pitch) in score.chord.withIndex()) {
                note(at + i * 0.035f, 3.85f, pitch, Instruments.Voice.PAD, 0.018f)
                note(at + 0.08f + i * 0.045f, 1.8f, pitch, Instruments.Voice.KEYS, 0.033f)
                if (this != TROUBLE) note(at + 2.55f + i * 0.025f, 1.25f, pitch,
                    Instruments.Voice.KEYS, if (section == 2) 0.020f else 0.032f)
            }
            note(at, 1.7f, score.bass, Instruments.Voice.BASS, 0.14f)
            note(at + 2f, 1.65f, score.bass + if (b % 2 == 0) 7 else 12,
                Instruments.Voice.BASS, 0.10f)
            if (this == CITY || this == INDUSTRY) note(at + 3.5f, 0.42f,
                score.bass + 2, Instruments.Voice.BASS, 0.07f)

            // Original short phrases leave rests; A′/A″ answer with a different ordering.
            if (b % 4 != 3 || section == 3) {
                val phrase = if (section == 1) score.lead.reversedArray() else score.lead
                for ((i, pitch) in phrase.withIndex()) {
                    val onset = at + floatArrayOf(0.5f, 1.75f, 3f)[i]
                    note(onset, if (i == phrase.lastIndex) 1.15f else 0.9f, pitch,
                        leadVoice, if (section == 2) 0.075f else 0.10f)
                }
            }
            if (section == 1 || section == 3) {
                note(at + 3.35f, 0.55f, score.chord[(b + 1) % 4] + 12,
                    Instruments.Voice.MALLET, 0.030f)
            }
            if (this == INDUSTRY) {
                for (k in 0..7) note(at + k * 0.5f, 0.4f,
                    score.chord[(k + b) % 4], Instruments.Voice.MALLET, 0.025f)
            }
            // Brushes and a small kick, with a tiny swing on the offbeats.
            if (this != TROUBLE) {
                val gentle = if (this == GARDEN) 0.55f else 1f
                Instruments.kick(track, at * beat, 0.13f * gentle)
                if (section != 2) Instruments.kick(track, (at + 2.5f) * beat, 0.085f * gentle)
                for (k in 0..7) {
                    val swing = if (k % 2 == 1 && this != INDUSTRY) 0.06f else 0f
                    Instruments.texture(track, (at + k * 0.5f + swing) * beat, 0.09f,
                        (if (k % 2 == 0) 0.055f else 0.035f) * gentle, b * 11 + k, 0.7f)
                }
                for (k in listOf(1f, 3f)) Instruments.texture(track, (at + k) * beat,
                    0.18f, 0.18f * gentle, b + 77, 0.25f, 4.5f)
            }
        }
        return Instruments.finish(track, loop = true, targetRms = if (this == TROUBLE) 0.095f else 0.115f)
    }

    companion object {
        /** Financial pressure takes priority; route identity survives scenario completion. */
        fun forCity(population: Int, funds: Int, monthsInDebt: Int, route: Campaign.Route?): Bgm = when {
            funds < 0 || monthsInDebt > 0 -> TROUBLE
            route == Campaign.Route.GARDEN -> GARDEN
            route == Campaign.Route.INDUSTRIAL -> INDUSTRY
            route == Campaign.Route.METROPOLIS || population >= 2_000 -> CITY
            else -> DAWN
        }
    }
}
