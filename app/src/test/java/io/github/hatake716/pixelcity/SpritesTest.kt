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
