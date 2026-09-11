package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.game.Monument
import io.github.hatake716.pixelcity.game.TileKind
import io.github.hatake716.pixelcity.ui.CityRenderer
import io.github.hatake716.pixelcity.ui.PixelCanvas
import io.github.hatake716.pixelcity.ui.ShowcaseCity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * タイトルの背景に置く大都市。
 * 「このゲームで行き着く先」を見せるものなので、
 * 密度と見どころが十分にあることを確かめる。
 */
class ShowcaseCityTest {

    private val city by lazy { ShowcaseCity.build() }

    @Test
    fun `the showcase is densely built`() {
        val built = city.tiles.count { it.kind.isZone && it.stage > 0 }
        assertTrue("only $built developed tiles", built > 900)
    }

    @Test
    fun `it shows every kind of zone at its highest stage`() {
        for (kind in listOf(TileKind.ZONE_R, TileKind.ZONE_C, TileKind.ZONE_I)) {
            val top = city.tiles.count { it.kind == kind && it.stage == 3 }
            assertTrue("$kind has no stage-3 tiles", top > 0)
        }
    }

    @Test
    fun `every monument is on display`() {
        for (m in Monument.entries) {
            assertTrue("${m.label} is missing", m in city.builtMonuments)
        }
        // 本体タイルが1つずつあること
        for (m in Monument.entries) {
            val anchors = city.tiles.count { it.monument == m }
            assertEquals("${m.label} anchors", 1, anchors)
        }
    }

    @Test
    fun `it has roads, water and public services`() {
        assertTrue(city.tiles.count { it.kind == TileKind.ROAD } > 400)
        assertTrue(city.tiles.count { it.terrain == io.github.hatake716.pixelcity.game.Terrain.WATER } > 40)
        assertTrue(city.tiles.count { it.kind == TileKind.PARK } > 5)
    }

    /** 同じ見た目が毎回出ること（シードを固定しているため）。 */
    @Test
    fun `the showcase is the same every time`() {
        val a = ShowcaseCity.build()
        val b = ShowcaseCity.build()
        for (i in a.tiles.indices) {
            assertEquals("tile $i", a.tiles[i].kind, b.tiles[i].kind)
            assertEquals("stage $i", a.tiles[i].stage, b.tiles[i].stage)
        }
    }

    /** 背景として描いたとき、画面が十分に埋まること。 */
    @Test
    fun `it fills the screen when drawn`() {
        val w = 480
        val h = 900
        val canvas = PixelCanvas(w, h)
        CityRenderer().draw(canvas, city, 31f, 31f, 1, 1, 0, h)
        // 地面そのままの明るさでない画素の割合を見る
        val painted = canvas.pixels.count { it.toInt() > 4 }
        val ratio = painted.toFloat() / (w * h)
        assertTrue("only ${(ratio * 100).toInt()}% of the screen is city", ratio > 0.45f)
    }
}
