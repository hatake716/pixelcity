package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.ui.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 配色の約束。
 *
 * 索引と色の並びがずれると、絵全体の色が入れ替わって壊れる。
 * 名前つきの索引が、実際に意図した色を指していることを確かめる。
 */
class PaletteTest {

    private fun red(c: Int) = (c shr 16) and 0xFF
    private fun green(c: Int) = (c shr 8) and 0xFF
    private fun blue(c: Int) = c and 0xFF

    @Test
    fun `every named index is inside the table`() {
        val named = listOf(
            Palette.GRASS_LIT, Palette.GRASS, Palette.GRASS_DARK, Palette.GRASS_EDGE,
            Palette.WATER_LIT, Palette.WATER, Palette.WATER_DARK, Palette.WATER_FOAM,
            Palette.SAND_LIT, Palette.SAND, Palette.SAND_DARK,
            Palette.ROAD_LIT, Palette.ROAD, Palette.ROAD_DARK, Palette.ROAD_LINE, Palette.KERB,
            Palette.WALL_ROOF, Palette.WALL_LEFT, Palette.WALL_RIGHT, Palette.WALL_EDGE,
            Palette.HOUSE_ROOF, Palette.HOUSE_ROOF_DARK, Palette.HOUSE_LEFT, Palette.HOUSE_RIGHT,
            Palette.OFFICE_ROOF, Palette.OFFICE_LEFT, Palette.OFFICE_RIGHT,
            Palette.GLASS_LIT, Palette.GLASS,
            Palette.FACTORY_ROOF, Palette.FACTORY_LEFT, Palette.FACTORY_RIGHT,
            Palette.WINDOW_LIT, Palette.WINDOW_DARK,
            Palette.TREE_LIT, Palette.TREE, Palette.TREE_DARK, Palette.TRUNK,
            Palette.STONE_LIT, Palette.STONE, Palette.STONE_DARK, Palette.STONE_EDGE,
            Palette.METAL_LIT, Palette.METAL, Palette.METAL_DARK,
            Palette.RED, Palette.RED_DARK, Palette.GOLD, Palette.GOLD_DARK,
            Palette.WHITE, Palette.BLACK,
            Palette.UI_BG, Palette.UI_BG_LIGHT, Palette.UI_LINE, Palette.UI_TEXT,
            Palette.UI_DIM, Palette.UI_ACCENT, Palette.SKY, Palette.SKY_DEEP,
            Palette.RURAL_ROOF, Palette.RURAL_ROOF_DARK,
            Palette.RURAL_WALL, Palette.RURAL_WALL_DARK,
            Palette.GRIT_ROOF, Palette.GRIT_ROOF_DARK,
            Palette.GRIT_WALL, Palette.GRIT_WALL_DARK,
            Palette.PRIME_ROOF, Palette.PRIME_LEFT,
            Palette.PRIME_RIGHT, Palette.PRIME_GLASS,
            Palette.ANCIENT_ROOF, Palette.ANCIENT_ROOF_DARK,
            Palette.ANCIENT_WALL, Palette.ANCIENT_WALL_DARK,
            Palette.FUTURE_ROOF, Palette.FUTURE_WALL,
            Palette.FUTURE_WALL_DARK, Palette.FUTURE_GLOW,
            Palette.EURO_ROOF, Palette.EURO_ROOF_DARK,
            Palette.EURO_WALL, Palette.EURO_WALL_DARK,
            Palette.JP_ROOF, Palette.JP_ROOF_DARK,
            Palette.JP_WALL, Palette.JP_WALL_DARK,
            Palette.MARBLE_HI, Palette.MARBLE_LIT, Palette.MARBLE,
            Palette.MARBLE_MID, Palette.MARBLE_DARK, Palette.MARBLE_EDGE,
            Palette.PATINA_HI, Palette.PATINA_LIT, Palette.PATINA,
            Palette.PATINA_DARK, Palette.PATINA_EDGE,
            Palette.TRAVERTINE_HI, Palette.TRAVERTINE_LIT, Palette.TRAVERTINE,
            Palette.TRAVERTINE_DARK, Palette.TRAVERTINE_EDGE,
            Palette.LIMESTONE_HI, Palette.LIMESTONE_LIT, Palette.LIMESTONE,
            Palette.LIMESTONE_DARK, Palette.LIMESTONE_EDGE,
            Palette.TOWER_ORANGE_LIT, Palette.TOWER_ORANGE,
            Palette.TOWER_ORANGE_DARK, Palette.TOWER_WHITE,
            Palette.LONDON_LIT, Palette.LONDON, Palette.LONDON_DARK, Palette.LONDON_EDGE,
            Palette.GILT_LIT, Palette.GILT, Palette.GILT_DARK,
            Palette.SHADOW, Palette.OPENING,
            Palette.SELECT, Palette.SELECT_EDGE,
        )
        // 名前の数だけ色があること（並びがずれていない）
        assertEquals("palette size", named.size, Palette.SIZE)
        for ((i, v) in named.withIndex()) {
            assertEquals("index $i is out of order", i, v)
        }
    }

    /** 16ビット機に合わせて 5bit/ch へ丸めていること。 */
    @Test
    fun `colours are quantised to five bits per channel`() {
        for (c in Palette.COLORS) {
            assertTrue("red not quantised", red(c) and 0x07 == 0)
            assertTrue("green not quantised", green(c) and 0x07 == 0)
            assertTrue("blue not quantised", blue(c) and 0x07 == 0)
        }
    }

    /** 草は緑、水は青、といった色の役割が合っていること。 */
    @Test
    fun `colours match the roles they are named for`() {
        val grass = Palette.of(Palette.GRASS)
        assertTrue("grass is not green", green(grass) > red(grass) && green(grass) > blue(grass))

        val water = Palette.of(Palette.WATER)
        assertTrue("water is not blue", blue(water) > red(water) && blue(water) > green(water))

        val lit = Palette.of(Palette.WINDOW_LIT)
        assertTrue("lit window is not bright", red(lit) > 200 && green(lit) > 180)

        val tree = Palette.of(Palette.TREE)
        assertTrue("tree is not green", green(tree) > red(tree))
    }

    /** 面の明暗が、光の向き（左上から）と矛盾しないこと。 */
    @Test
    fun `lit faces are brighter than shaded ones`() {
        fun luma(i: Int): Int {
            val c = Palette.of(i)
            return (red(c) * 299 + green(c) * 587 + blue(c) * 114) / 1000
        }
        // 屋根 > 左面 > 右面 > 輪郭
        assertTrue(luma(Palette.WALL_ROOF) > luma(Palette.WALL_LEFT))
        assertTrue(luma(Palette.WALL_LEFT) > luma(Palette.WALL_RIGHT))
        assertTrue(luma(Palette.WALL_RIGHT) > luma(Palette.WALL_EDGE))
        assertTrue(luma(Palette.GRASS_LIT) > luma(Palette.GRASS))
        assertTrue(luma(Palette.GRASS) > luma(Palette.GRASS_DARK))
        assertTrue(luma(Palette.STONE_LIT) > luma(Palette.STONE))
        assertTrue(luma(Palette.WATER_LIT) > luma(Palette.WATER_DARK))
    }

    /** 画面の文字が、下地に対して十分な明暗差を持つこと。 */
    @Test
    fun `ui text is readable against its background`() {
        fun luma(i: Int): Int {
            val c = Palette.of(i)
            return (red(c) * 299 + green(c) * 587 + blue(c) * 114) / 1000
        }
        val contrast = luma(Palette.UI_TEXT) - luma(Palette.UI_BG)
        assertTrue("contrast is only $contrast", contrast > 100)
    }
}
