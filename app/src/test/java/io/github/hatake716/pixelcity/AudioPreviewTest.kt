package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.audio.Bgm
import io.github.hatake716.pixelcity.audio.Sfx
import io.github.hatake716.pixelcity.audio.Waves
import java.io.File
import org.junit.Test

/**
 * 音を耳で確かめるための書き出し。試験ではなく道具。
 * -Ppixelcity.preview=<書き出し先> を付けたときだけ動く。
 */
class AudioPreviewTest {

    /** 16bit モノラルの WAV を組み立てる。 */
    private fun wav(pcm: ShortArray): ByteArray {
        val dataSize = pcm.size * 2
        val out = java.io.ByteArrayOutputStream()
        fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun le32(v: Int) {
            out.write(v and 0xFF); out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF); out.write((v ushr 24) and 0xFF)
        }
        fun le16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }

        ascii("RIFF"); le32(36 + dataSize); ascii("WAVE")
        ascii("fmt "); le32(16); le16(1); le16(1)
        le32(Waves.RATE); le32(Waves.RATE * 2); le16(2); le16(16)
        ascii("data"); le32(dataSize)
        for (v in pcm) le16(v.toInt() and 0xFFFF)
        return out.toByteArray()
    }

    @Test
    fun writeAudioFiles() {
        val base = System.getProperty("pixelcity.preview")
        if (base.isNullOrBlank()) return
        val dir = File(base, "audio")
        dir.mkdirs()
        for (sfx in Sfx.entries) {
            File(dir, "sfx_${sfx.name}.wav").writeBytes(wav(sfx.render()))
        }
        for (bgm in Bgm.entries) {
            File(dir, "bgm_${bgm.name}.wav").writeBytes(wav(bgm.render()))
        }
    }
}
