package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.game.Monument
import io.github.hatake716.pixelcity.ui.Iso
import io.github.hatake716.pixelcity.ui.IsoBuildings
import io.github.hatake716.pixelcity.ui.MonumentSprites
import io.github.hatake716.pixelcity.ui.Palette
import io.github.hatake716.pixelcity.ui.Pix
import io.github.hatake716.pixelcity.ui.Sprite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ドット絵は生成しているので、寸法・階調・中身の有無を機械的に確かめる。
 * 立体に見えるかどうかは、面ごとの明るさが分かれているかで見る。
 */
class SpritesTest {

    private fun buildings(): List<Pair<String, Sprite>> = listOf(
        "HOUSE_1" to IsoBuildings.HOUSE_1,
        "HOUSE_2" to IsoBuildings.HOUSE_2,
        "HOUSE_3" to IsoBuildings.HOUSE_3,
        "SHOP_1" to IsoBuildings.SHOP_1,
        "SHOP_2" to IsoBuildings.SHOP_2,
        "SHOP_3" to IsoBuildings.SHOP_3,
        "FACTORY_1" to IsoBuildings.FACTORY_1,
        "FACTORY_2" to IsoBuildings.FACTORY_2,
        "FACTORY_3" to IsoBuildings.FACTORY_3,
        "POWER_COAL" to IsoBuildings.POWER_COAL,
        "POWER_SOLAR" to IsoBuildings.POWER_SOLAR,
        "PARK" to IsoBuildings.PARK,
        "POLICE" to IsoBuildings.POLICE,
        "FIRE" to IsoBuildings.FIRE,
        "SCHOOL" to IsoBuildings.SCHOOL,
        "HOSPITAL" to IsoBuildings.HOSPITAL,
    )

    @Test
    fun `buildings are one tile wide and taller than the ground`() {
        for ((name, s) in buildings()) {
            assertEquals("$name width", Iso.TILE_W, s.width)
            assertTrue("$name is not taller than a tile", s.height > Iso.TILE_H)
        }
    }

    @Test
    fun `every sprite uses only colours that exist in the palette`() {
        val all = buildings() + Monument.entries.map { it.name to MonumentSprites.of(it) }
        for ((name, s) in all) {
            for (v in s.data) {
                assertTrue(
                    "$name uses colour $v, outside the palette",
                    v == Pix.TRANSPARENT || v.toInt() in 0 until Palette.SIZE,
                )
            }
            assertTrue("$name is blank", s.inkCount > 30)
        }
    }

    /**
     * 立体に見えるには、面ごとに色が違う必要がある。
     * 使われている色が1〜2種類しかないと、のっぺりした板になる。
     * 高精細にしたぶん、以前より多くの色を使っているはず。
     */
    @Test
    fun `buildings are shaded with several colours`() {
        for ((name, s) in buildings()) {
            assertTrue("$name uses only ${s.colourCount} colours", s.colourCount >= 4)
        }
    }

    @Test
    fun `monuments occupy two tiles across and stand tall`() {
        for (m in Monument.entries) {
            val s = MonumentSprites.of(m)
            assertEquals("${m.name} width", MonumentSprites.W, s.width)
            assertTrue("${m.name} is too short (${s.height})", s.height >= Iso.TILE_H * 2)
            assertTrue("${m.name} uses only ${s.colourCount} colours", s.colourCount >= 3)
        }
    }

    /**
     * モニュメントが、細かく描かれていること。
     *
     * 以前は 64 ドットで描いてから4倍に引き伸ばしていたので、
     * どの点も 4×4 の塊になっていた。原寸で描いているなら、
     * 4の倍数でない位置にも色の変わり目があるはずで、
     * 引き伸ばしに戻ってしまったらこの試験が落ちる。
     */
    @Test
    fun `monuments are drawn at full resolution, not upscaled`() {
        for (m in Monument.entries) {
            val s = MonumentSprites.of(m)
            var offGrid = 0
            for (y in 0 until s.height) {
                for (x in 1 until s.width) {
                    if (s.at(x, y) != s.at(x - 1, y) && x % 4 != 0) offGrid++
                }
            }
            assertTrue(
                "${m.name} looks upscaled: only $offGrid colour changes off the 4px grid",
                offGrid > 200,
            )
        }
    }

    /**
     * 立体に見えるだけの階調があること。
     *
     * ドームや円柱は、段が足りないと輪切りに見える。
     * 石や銅の階調を使っているなら、どのモニュメントも
     * それなりの色数になるはず。
     */
    @Test
    fun `monuments are shaded with many colours`() {
        for (m in Monument.entries) {
            val s = MonumentSprites.of(m)
            assertTrue(
                "${m.name} uses only ${s.colourCount} colours; it will look flat",
                s.colourCount >= 8,
            )
        }
    }

    /**
     * どのモニュメントも、見分けがつくこと。
     *
     * 塗られた画素の並びで比べる。同じ絵を使い回していると落ちる。
     */
    @Test
    fun `each monument looks different from the others`() {
        val shapes = Monument.entries.associateWith { m ->
            val s = MonumentSprites.of(m)
            buildString {
                for (y in 0 until s.height step 4) {
                    for (x in 0 until s.width step 4) {
                        append(if (s.at(x, y) == Pix.TRANSPARENT) '.' else '#')
                    }
                }
            }
        }
        for (a in Monument.entries) for (bm in Monument.entries) {
            if (a >= bm) continue
            assertTrue("$a and $bm look the same", shapes[a] != shapes[bm])
        }
    }

    /**
     * 足元が広く、上にいくほど細いこと。
     *
     * クォータービューでは、地面に接する側が手前に来る。
     * 上が下より広いと、宙に浮いて見える。
     */
    @Test
    fun `monuments are wider at the foot than at the top`() {
        for (m in Monument.entries) {
            val s = MonumentSprites.of(m)
            fun widthAt(y: Int): Int {
                var lo = -1
                var hi = -1
                for (x in 0 until s.width) {
                    if (s.at(x, y) != Pix.TRANSPARENT) {
                        if (lo < 0) lo = x
                        hi = x
                    }
                }
                return if (lo < 0) 0 else hi - lo + 1
            }
            // 下から1割の位置と、上から1割の位置で比べる
            val low = widthAt(s.height - 1 - s.height / 10)
            val high = widthAt(s.height / 10)
            assertTrue("${m.name} is wider at the top ($high) than the foot ($low)", low >= high)
        }
    }

    /** 同じモニュメントを2回取っても同じ絵（生成結果を使い回している）。 */
    @Test
    fun `monument sprites are cached`() {
        for (m in Monument.entries) {
            assertTrue(MonumentSprites.of(m) === MonumentSprites.of(m))
        }
    }

    /** 建物どうしが見分けられること（同じ絵を使い回していない）。 */
    @Test
    fun `each building looks different`() {
        val seen = mutableMapOf<String, String>()
        for ((name, s) in buildings()) {
            val key = s.data.joinToString("") { it.toString() }
            val dup = seen[key]
            assertTrue("$name looks identical to $dup", dup == null)
            seen[key] = name
        }
    }
}
