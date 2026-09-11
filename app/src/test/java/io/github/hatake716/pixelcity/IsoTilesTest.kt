package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.ui.Iso
import io.github.hatake716.pixelcity.ui.IsoTiles
import io.github.hatake716.pixelcity.ui.Pix
import io.github.hatake716.pixelcity.ui.Sprite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 地面のタイルが、ちゃんと菱形になっていることを確かめる。
 * 菱形が崩れていると、敷き詰めたときに隙間や重なりが出る。
 */
class IsoTilesTest {

    private fun allTiles(): List<Pair<String, Sprite>> = listOf(
        "GRASS" to IsoTiles.GRASS,
        "WATER" to IsoTiles.WATER,
        "SHORE" to IsoTiles.SHORE,
        "ROAD_X" to IsoTiles.ROAD_X,
        "ROAD_Y" to IsoTiles.ROAD_Y,
        "ROAD_CROSS" to IsoTiles.ROAD_CROSS,
        "ZONE_R_EMPTY" to IsoTiles.ZONE_R_EMPTY,
        "ZONE_C_EMPTY" to IsoTiles.ZONE_C_EMPTY,
        "ZONE_I_EMPTY" to IsoTiles.ZONE_I_EMPTY,
    )

    @Test
    fun `every ground tile is the size of one diamond`() {
        for ((name, s) in allTiles()) {
            assertEquals("$name width", Iso.TILE_W, s.width)
            assertEquals("$name height", Iso.TILE_H, s.height)
        }
    }

    @Test
    fun `every ground tile has ink`() {
        for ((name, s) in allTiles()) {
            assertTrue("$name is blank", s.inkCount > 40)
        }
    }

    /**
     * 菱形の外側は透明であること。
     * ここが塗られていると、隣のタイルを四角く覆ってしまう。
     */
    @Test
    fun `ground tiles are transparent outside the diamond`() {
        for ((name, s) in allTiles()) {
            for (y in 0 until s.height) for (x in 0 until s.width) {
                // 菱形の中心からの距離
                val dx = x - (Iso.TILE_W - 1) / 2f
                val dy = y - (Iso.TILE_H - 1) / 2f
                val outside = Math.abs(dx) / (Iso.TILE_W / 2f) + Math.abs(dy) / (Iso.TILE_H / 2f) > 1.15f
                if (outside) {
                    assertEquals(
                        "$name has ink outside the diamond at ($x,$y)",
                        Pix.TRANSPARENT, s.at(x, y),
                    )
                }
            }
        }
    }

    /** 菱形の内側は、おおむね塗られていること（隙間が空かない）。 */
    @Test
    fun `ground tiles fill the inside of the diamond`() {
        for ((name, s) in allTiles()) {
            if (name.endsWith("EMPTY")) continue  // 区分の印は枠だけなので除く
            var inside = 0
            var painted = 0
            for (y in 0 until s.height) for (x in 0 until s.width) {
                val dx = x - (Iso.TILE_W - 1) / 2f
                val dy = y - (Iso.TILE_H - 1) / 2f
                if (Math.abs(dx) / (Iso.TILE_W / 2f) + Math.abs(dy) / (Iso.TILE_H / 2f) <= 0.7f) {
                    inside++
                    if (s.at(x, y) != Pix.TRANSPARENT) painted++
                }
            }
            assertTrue("$name fills only $painted of $inside", painted >= inside * 0.9)
        }
    }

    @Test
    fun `road tiles differ by direction`() {
        assertTrue(!IsoTiles.ROAD_X.data.contentEquals(IsoTiles.ROAD_Y.data))
        assertTrue(!IsoTiles.ROAD_X.data.contentEquals(IsoTiles.ROAD_CROSS.data))
    }
}
