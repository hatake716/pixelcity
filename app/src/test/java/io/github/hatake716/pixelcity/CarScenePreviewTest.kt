package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind
import io.github.hatake716.pixelcity.ui.CityRenderer
import io.github.hatake716.pixelcity.ui.Palette
import io.github.hatake716.pixelcity.ui.PixelCanvas
import io.github.hatake716.pixelcity.ui.TrafficAnimation
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import org.junit.Test

/** 道を走る車を、街のなかで見るための書き出し。道具であって試験ではない。 */
class CarScenePreviewTest {

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
            out.write(typed); int(CRC32().apply { update(typed) }.value.toInt())
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
    fun writeCarScene() {
        val base = System.getProperty("pixelcity.preview")
        if (base.isNullOrBlank()) return
        val dir = File(base, "cars"); dir.mkdirs()

        // CarRenderTest と同じ形の街。こちらは実際に育つ。
        val city = City(40, 40).apply {
            funds = 5_000_000
            for (t in tiles) t.terrain = Terrain.LAND
            for (x in 4..34) build(x, 20, TileKind.ROAD)
            build(3, 20, TileKind.POWER_COAL)
            for (x in 6..14) { build(x, 19, TileKind.ZONE_R); build(x, 21, TileKind.ZONE_R) }
            for (x in 24..32) { build(x, 19, TileKind.ZONE_C); build(x, 21, TileKind.ZONE_I) }
            repeat(90) { step() }
        }
        val anim = TrafficAnimation()
        anim.refill(city, 4, 16, 36, 24)

        val w = 900
        val h = 700
        val canvas = PixelCanvas(w, h)
        // 大通りと交差点のあたりを、寄って見る
        CityRenderer().draw(canvas, city, 11f, 20f, 1, 2, 0, h, cars = anim.all)
        val px = IntArray(w * h) { i ->
            val v = canvas.get(i % w, i / w)
            if (v < 0 || v >= Palette.SIZE) 0 else Palette.COLORS[v]
        }
        File(dir, "scene.png").writeBytes(png(w, h, px))
    }
}
