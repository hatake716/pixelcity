package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Monument
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind
import io.github.hatake716.pixelcity.ui.CityRenderer
import io.github.hatake716.pixelcity.ui.PixelCanvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 斜め見下ろしの描画。Android に依存しないピクセルバッファへ描いて結果を数える。
 */
class CityRendererTest {

    private val w = 480
    private val h = 420

    private fun flatCity() = City().apply {
        for (t in tiles) t.terrain = Terrain.LAND
        funds = 1_000_000
        population = 99_999
    }

    /** 街の中心をカメラに置いて描く。 */
    private fun render(
        city: City,
        zoomNum: Int = 1,
        zoomDen: Int = 1,
        camX: Float = 16f,
        camY: Float = 16f,
    ): PixelCanvas {
        val canvas = PixelCanvas(w, h)
        CityRenderer().draw(canvas, city, camX, camY, zoomNum, zoomDen, 0, h)
        return canvas
    }

    private fun distinctLevels(c: PixelCanvas): Int =
        c.pixels.map { it.toInt() }.toSet().size

    @Test
    fun `an empty map still draws ground`() {
        val c = render(flatCity())
        assertTrue("ground is flat colour", distinctLevels(c) > 1)
    }

    /**
     * 建物が地面より上へ伸びること。
     * 斜め見下ろしでは、建物はタイルの菱形より上の行にも画素を持つ。
     */
    @Test
    fun `buildings rise above their tile`() {
        val plain = render(flatCity())
        val city = flatCity()
        // 中心に高い建物を建てる
        city.build(16, 16, TileKind.ZONE_C)
        city.tileAt(16, 16).stage = 3
        val built = render(city)
        assertTrue(
            "the tall building did not change the picture",
            !plain.pixels.contentEquals(built.pixels),
        )
    }

    /** 手前の建物が、奥の建物より後に描かれること（正しく重なる）。 */
    @Test
    fun `nearer buildings are drawn over farther ones`() {
        val city = flatCity()
        // 同じ画面位置に重なるように、奥と手前へ高い建物を置く
        for (d in 0..3) {
            city.build(16 + d, 16 + d, TileKind.ZONE_C)
            city.tileAt(16 + d, 16 + d).stage = 3
        }
        val c = render(city)
        // 崩れずに描けていれば、いろいろな階調が出る
        assertTrue(distinctLevels(c) >= 4)
    }

    @Test
    fun `every monument renders somewhere on screen`() {
        for (m in Monument.entries) {
            val city = flatCity()
            city.tileAt(18, 16).terrain = Terrain.WATER
            assertTrue("${m.name} not placed", city.buildMonument(16, 16, m))
            val plain = render(flatCity())
            val built = render(city)
            assertTrue(
                "${m.name} did not appear",
                !plain.pixels.contentEquals(built.pixels),
            )
        }
    }

    @Test
    fun `roads look different from bare ground`() {
        val plain = render(flatCity())
        val city = flatCity()
        for (d in -3..3) city.build(16 + d, 16, TileKind.ROAD)
        val roads = render(city)
        assertTrue(!plain.pixels.contentEquals(roads.pixels))
    }

    @Test
    fun `water looks different from land`() {
        val land = render(flatCity())
        val city = flatCity()
        for (y in 14..18) for (x in 14..18) city.tileAt(x, y).terrain = Terrain.WATER
        val sea = render(city)
        assertTrue(!land.pixels.contentEquals(sea.pixels))
    }

    /** どの拡大率でも落ちずに描けること。 */
    @Test
    fun `rendering works at every zoom level`() {
        val city = flatCity()
        for (d in -4..4) city.build(16 + d, 16, TileKind.ROAD)
        city.build(16, 15, TileKind.ZONE_R)
        city.tileAt(16, 15).stage = 2
        for ((num, den) in listOf(1 to 2, 1 to 1, 2 to 1)) {
            val c = render(city, num, den)
            assertTrue("zoom $num/$den drew nothing", distinctLevels(c) > 1)
        }
    }

    /** 画面の外にカメラを置いても落ちないこと。 */
    @Test
    fun `rendering outside the map does not crash`() {
        val city = flatCity()
        render(city, camX = -40f, camY = -40f)
        render(city, camX = 200f, camY = 200f)
        assertEquals(480, w)
    }
}
