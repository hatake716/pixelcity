package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.game.Monument
import io.github.hatake716.pixelcity.ui.MonumentSprites
import io.github.hatake716.pixelcity.ui.Palette
import io.github.hatake716.pixelcity.ui.Pix
import io.github.hatake716.pixelcity.ui.Sprite
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import org.junit.Test

/**
 * 絵を目で確かめるための書き出し。テストではなく道具。
 * -Dpixelcity.preview=<書き出し先> を付けたときだけ動く。
 *
 * java.awt は Android の classpath にないので、PNG は自前で組み立てる。
 */
class MonumentPreviewTest {

    private fun png(w: Int, h: Int, argb: IntArray): ByteArray {
        // 各行の先頭にフィルタ種別 0 を置いた、生の RGBA
        val raw = ByteArray(h * (1 + w * 4))
        var p = 0
        for (y in 0 until h) {
            raw[p++] = 0
            for (x in 0 until w) {
                val c = argb[y * w + x]
                raw[p++] = ((c shr 16) and 0xFF).toByte()
                raw[p++] = ((c shr 8) and 0xFF).toByte()
                raw[p++] = (c and 0xFF).toByte()
                raw[p++] = ((c ushr 24) and 0xFF).toByte()
            }
        }
        val deflated = ByteArrayOutputStream().also { out ->
            val d = Deflater()
            d.setInput(raw); d.finish()
            val buf = ByteArray(16384)
            while (!d.finished()) out.write(buf, 0, d.deflate(buf))
            d.end()
        }.toByteArray()

        fun chunk(type: String, body: ByteArray): ByteArray {
            val out = ByteArrayOutputStream()
            fun int(v: Int) {
                out.write(v ushr 24); out.write((v shr 16) and 0xFF)
                out.write((v shr 8) and 0xFF); out.write(v and 0xFF)
            }
            int(body.size)
            val typed = type.toByteArray(Charsets.US_ASCII) + body
            out.write(typed)
            val crc = CRC32().apply { update(typed) }
            int(crc.value.toInt())
            return out.toByteArray()
        }

        val ihdr = ByteArrayOutputStream().apply {
            fun int(v: Int) {
                write(v ushr 24); write((v shr 16) and 0xFF)
                write((v shr 8) and 0xFF); write(v and 0xFF)
            }
            int(w); int(h)
            write(8); write(6); write(0); write(0); write(0)   // 8bit RGBA
        }.toByteArray()

        return byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10) +
            chunk("IHDR", ihdr) + chunk("IDAT", deflated) + chunk("IEND", ByteArray(0))
    }

    private fun colourOf(v: Byte): Int =
        if (v == Pix.TRANSPARENT) 0 else Palette.COLORS[v.toInt()]

    private fun write(file: File, w: Int, h: Int, argb: IntArray) {
        file.writeBytes(png(w, h, argb))
    }

    @Test
    fun writeMonumentSheet() {
        val out = System.getProperty("pixelcity.preview")
        if (out.isNullOrBlank()) return      // 書き出し先の指定がなければ何もしない
        File(out).mkdirs()

        for (m in Monument.entries) {
            val s: Sprite = MonumentSprites.of(m)
            val px = IntArray(s.width * s.height) { i ->
                colourOf(s.at(i % s.width, i / s.width))
            }
            write(File(out, "${m.name}.png"), s.width, s.height, px)
        }

        // 並べた一覧。地の色を敷いて、実際の見え方に近づける。
        val sprites = Monument.entries.map { MonumentSprites.of(it) }
        val gap = 8
        val sheetW = sprites.sumOf { it.width + gap } + gap
        val sheetH = sprites.maxOf { it.height } + gap * 2
        val sheet = IntArray(sheetW * sheetH) { 0xFF202840.toInt() }
        var ox = gap
        for (s in sprites) {
            val oy = sheetH - gap - s.height
            for (y in 0 until s.height) for (x in 0 until s.width) {
                val v = s.at(x, y)
                if (v != Pix.TRANSPARENT) {
                    sheet[(oy + y) * sheetW + ox + x] = Palette.COLORS[v.toInt()]
                }
            }
            ox += s.width + gap
        }
        write(File(out, "_sheet.png"), sheetW, sheetH, sheet)
    }
}
