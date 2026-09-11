package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.ui.IsoBuildings
import io.github.hatake716.pixelcity.ui.Palette
import io.github.hatake716.pixelcity.ui.Pix
import io.github.hatake716.pixelcity.ui.Sprite
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.Deflater
import org.junit.Test

/**
 * 建物の絵を目で確かめるための書き出し。試験ではなく道具。
 * -Ppixelcity.preview=<書き出し先> を付けたときだけ動く。
 */
class BuildingPreviewTest {

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
            val d = Deflater()
            d.setInput(raw); d.finish()
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
    fun writeBuildingSheet() {
        val base = System.getProperty("pixelcity.preview")
        if (base.isNullOrBlank()) return
        val dir = File(base, "buildings")
        dir.mkdirs()

        // 区分ごと・段階ごとに、用意したバリエーションを全部並べる
        val all: List<Pair<String, Sprite>> = buildList {
            for (kind in listOf(
                io.github.hatake716.pixelcity.game.TileKind.ZONE_R,
                io.github.hatake716.pixelcity.game.TileKind.ZONE_C,
                io.github.hatake716.pixelcity.game.TileKind.ZONE_I,
            )) {
                for (stage in 1..3) {
                    for (v in 0 until IsoBuildings.VARIANTS) {
                        add("${kind.name}_${stage}_$v" to IsoBuildings.zoneBuilding(kind, stage, v))
                    }
                }
            }
        }
        for ((name, s) in all) {
            val px = IntArray(s.width * s.height) { i ->
                val v = s.at(i % s.width, i / s.width)
                if (v == Pix.TRANSPARENT) 0 else Palette.COLORS[v.toInt()]
            }
            File(dir, "$name.png").writeBytes(png(s.width, s.height, px))
        }

        // 並べた一覧。1行に同じ段階のバリエーションを並べる。
        val perRow = IsoBuildings.VARIANTS
        val rows = (all.size + perRow - 1) / perRow
        val gap = 16
        val cellW = all.maxOf { it.second.width } + gap
        val cellH = all.maxOf { it.second.height } + gap
        val sheetW = cellW * perRow + gap
        val sheetH = cellH * rows + gap
        val sheet = IntArray(sheetW * sheetH) { 0xFF3A5A2A.toInt() }
        for ((i, item) in all.withIndex()) {
            val sp = item.second
            val col = i % perRow
            val row = i / perRow
            val ox = gap + col * cellW
            val oy = gap + row * cellH + (cellH - gap - sp.height)
            for (y in 0 until sp.height) for (x in 0 until sp.width) {
                val v = sp.at(x, y)
                if (v != Pix.TRANSPARENT) {
                    val px = ox + x
                    val py = oy + y
                    if (px in 0 until sheetW && py in 0 until sheetH) {
                        sheet[py * sheetW + px] = Palette.COLORS[v.toInt()]
                    }
                }
            }
        }
        File(dir, "_sheet.png").writeBytes(png(sheetW, sheetH, sheet))
    }
}
