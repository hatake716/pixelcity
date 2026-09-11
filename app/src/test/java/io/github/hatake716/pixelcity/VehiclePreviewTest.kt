package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.ui.Palette
import io.github.hatake716.pixelcity.ui.Pix
import io.github.hatake716.pixelcity.ui.Vehicles
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import org.junit.Test

/** 車の絵を目で確かめる道具。試験ではない。 */
class VehiclePreviewTest {

    private fun png(w: Int, h: Int, argb: IntArray): ByteArray {
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
            val d = Deflater(); d.setInput(raw); d.finish()
            val buf = ByteArray(65536)
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
            int(CRC32().apply { update(typed) }.value.toInt())
            return out.toByteArray()
        }
        val ihdr = ByteArrayOutputStream().apply {
            fun int(v: Int) {
                write(v ushr 24); write((v shr 16) and 0xFF)
                write((v shr 8) and 0xFF); write(v and 0xFF)
            }
            int(w); int(h); write(8); write(6); write(0); write(0); write(0)
        }.toByteArray()
        return byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10) +
            chunk("IHDR", ihdr) + chunk("IDAT", deflated) + chunk("IEND", ByteArray(0))
    }

    @Test
    fun writeVehicleSheet() {
        val base = System.getProperty("pixelcity.preview")
        if (base.isNullOrBlank()) return
        val dir = File(base, "vehicles"); dir.mkdirs()

        val kinds = Vehicles.Kind.entries
        val dirs = Vehicles.Dir.entries
        val gap = 6
        val sheetW = (Vehicles.W + gap) * kinds.size + gap
        val sheetH = (Vehicles.H + gap) * dirs.size + gap
        val sheet = IntArray(sheetW * sheetH) { 0xFF5C5C5A.toInt() }   // 舗装の色
        for ((di, d) in dirs.withIndex()) for ((ki, k) in kinds.withIndex()) {
            val s = Vehicles.of(k, d)
            val ox = gap + ki * (Vehicles.W + gap)
            val oy = gap + di * (Vehicles.H + gap)
            for (y in 0 until s.height) for (x in 0 until s.width) {
                val v = s.at(x, y)
                if (v != Pix.TRANSPARENT) {
                    sheet[(oy + y) * sheetW + ox + x] = Palette.COLORS[v.toInt()]
                }
            }
        }
        File(dir, "_sheet.png").writeBytes(png(sheetW, sheetH, sheet))
    }
}
