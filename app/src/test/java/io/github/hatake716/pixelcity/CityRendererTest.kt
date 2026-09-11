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
 * 描画の検証。Android に依存しないピクセルバッファへ描いて、結果を数える。
 * 実機で見つけた「モニュメントが欠ける」不具合を、ここで再発させない。
 */
class CityRendererTest {

    private val tileSize = 16

    private fun flatCity() = City().apply {
        for (t in tiles) t.terrain = Terrain.LAND
        funds = 1_000_000
        population = 99_999
    }

    private fun render(city: City, w: Int = 320, h: Int = 288): PixelCanvas {
        val canvas = PixelCanvas(w, h)
        CityRenderer().draw(canvas, city, 0f, 0f, tileSize, 0, h)
        return canvas
    }

    /** 描いた範囲に、そのパレット値が何画素あるか。 */
    private fun count(canvas: PixelCanvas, x: Int, y: Int, w: Int, h: Int, value: Int): Int {
        var n = 0
        for (yy in y until y + h) for (xx in x until x + w) if (canvas.get(xx, yy) == value) n++
        return n
    }

    /**
     * モニュメントは 2×2 タイルいっぱいに描かれること。
     *
     * 地形と同じ周回で描いていたころは、右と下のタイルの地形が
     * スプライトの上に重なって、右下の4分の3が欠けていた。
     */
    @Test
    fun `a monument is drawn across its whole two by two area`() {
        val city = flatCity()
        assertTrue(city.buildMonument(2, 2, Monument.TOKYO_TOWER))
        val canvas = render(city)

        val x = 2 * tileSize
        val y = 2 * tileSize
        val span = tileSize * 2
        // 4つの象限すべてに、建物の画素（最も濃い値）があること
        for (qy in 0..1) for (qx in 0..1) {
            val dark = count(canvas, x + qx * tileSize, y + qy * tileSize, tileSize, tileSize, 3)
            assertTrue("quadrant ($qx,$qy) is empty", dark > 0)
        }
    }

    @Test
    fun `every monument renders without being clipped`() {
        for (m in Monument.entries) {
            val city = flatCity()
            // 水辺を要求するものにも応えられるよう、2×2 の隣に水を置く
            city.tileAt(4, 2).terrain = Terrain.WATER
            assertTrue("${m.name} not placed", city.buildMonument(2, 2, m))
            val canvas = render(city)
            val dark = count(canvas, 2 * tileSize, 2 * tileSize, tileSize * 2, tileSize * 2, 3)
            assertTrue("${m.name} drew only $dark pixels", dark > 40)
        }
    }

    @Test
    fun `roads and zones are drawn where they were built`() {
        val city = flatCity()
        city.build(1, 1, TileKind.ROAD)
        city.build(3, 1, TileKind.ZONE_R)
        city.tileAt(3, 1).stage = 2
        val canvas = render(city)
        assertTrue(count(canvas, tileSize, tileSize, tileSize, tileSize, 3) > 0)
        assertTrue(count(canvas, 3 * tileSize, tileSize, tileSize, tileSize, 3) > 0)
    }

    /** 水は陸と違う見た目になること。 */
    @Test
    fun `water looks different from land`() {
        val city = flatCity()
        city.tileAt(1, 1).terrain = Terrain.WATER
        val canvas = render(city)
        val water = count(canvas, tileSize, tileSize, tileSize, tileSize, 2)
        val land = count(canvas, 5 * tileSize, 5 * tileSize, tileSize, tileSize, 2)
        assertTrue("water=$water land=$land", water != land)
    }

    /** 画面の外を指していても落ちないこと。 */
    @Test
    fun `rendering outside the map does not crash`() {
        val city = flatCity()
        val canvas = PixelCanvas(320, 288)
        CityRenderer().draw(canvas, city, -5f, -5f, tileSize, 0, 288)
        CityRenderer().draw(canvas, city, 100f, 100f, tileSize, 0, 288)
        assertEquals(320, canvas.width)
    }
}
