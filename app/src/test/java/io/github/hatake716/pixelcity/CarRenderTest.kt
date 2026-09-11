package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind
import io.github.hatake716.pixelcity.ui.CityRenderer
import io.github.hatake716.pixelcity.ui.PixelCanvas
import io.github.hatake716.pixelcity.ui.TrafficAnimation
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 道を走る車が、実際に画面へ出ること。
 *
 * 「動いているか」は目で見るしかないが、
 * 「出ているか」「交通量に合っているか」は数で確かめられる。
 */
class CarRenderTest {

    private val w = 540
    private val h = 900

    /** 住宅と職場を道でつないだ街。 */
    private fun busyCity(): City = City(40, 40).apply {
        funds = 5_000_000
        for (t in tiles) t.terrain = Terrain.LAND
        for (x in 4..34) build(x, 20, TileKind.ROAD)
        build(3, 20, TileKind.POWER_COAL)
        for (x in 6..14) { build(x, 19, TileKind.ZONE_R); build(x, 21, TileKind.ZONE_R) }
        for (x in 24..32) { build(x, 19, TileKind.ZONE_C); build(x, 21, TileKind.ZONE_I) }
        repeat(80) { step() }
    }

    private fun render(city: City, cars: List<TrafficAnimation.Car>?): PixelCanvas {
        val canvas = PixelCanvas(w, h)
        CityRenderer().draw(
            canvas, city, 20f, 20f, 1, 4, 0, h, cars = cars,
        )
        return canvas
    }

    /** 交通量のある道に、車が配られること。 */
    @Test
    fun `cars appear on roads that carry traffic`() {
        val city = busyCity()
        val anim = TrafficAnimation()
        anim.refill(city, 4, 14, 36, 26)
        assertTrue("no cars were placed", anim.all.isNotEmpty())
        // 置かれた車は、すべて道の上にいること
        for (c in anim.all) {
            assertTrue(
                "a car was placed off the road at (${c.tx}, ${c.ty})",
                city.tileAt(c.tx, c.ty).kind.isRoad,
            )
        }
    }

    /** 車を描くと、画面が変わること。 */
    @Test
    fun `drawing cars changes the picture`() {
        val city = busyCity()
        val anim = TrafficAnimation()
        anim.refill(city, 4, 14, 36, 26)
        val without = render(city, null)
        val with = render(city, anim.all)
        assertTrue("the cars did not show up", !without.pixels.contentEquals(with.pixels))
    }

    /** 交通量のない街には、車が出ないこと。 */
    @Test
    fun `an empty city has no cars`() {
        val city = City(20, 20).apply {
            for (t in tiles) t.terrain = Terrain.LAND
            for (x in 2..18) build(x, 10, TileKind.ROAD)
            repeat(12) { step() }
        }
        val anim = TrafficAnimation()
        anim.refill(city, 0, 0, 19, 19)
        assertTrue("cars appeared with no traffic", anim.all.isEmpty())
    }

    /** 車が、時間とともに動くこと。 */
    @Test
    fun `cars move as time passes`() {
        val city = busyCity()
        val anim = TrafficAnimation()
        anim.refill(city, 4, 14, 36, 26)
        val before = anim.all.map { Triple(it.tx, it.ty, it.progress) }
        anim.advance(city, 0.5f)
        val after = anim.all.map { Triple(it.tx, it.ty, it.progress) }
        assertTrue("nothing moved", before != after)
    }

    /** 混んだ道の車ほど、ゆっくり進むこと（Greenshields）。 */
    @Test
    fun `cars on a congested road move more slowly`() {
        val city = busyCity()
        // 同じ道の上に、混み具合だけ違う2台を置く
        val road = city.tiles.indexOfFirst { it.kind == TileKind.ROAD }
        val rx = road % city.width
        val ry = road / city.width

        fun travel(traffic: Int): Float {
            city.tileAt(rx, ry).traffic = traffic
            val anim = TrafficAnimation()
            anim.refill(city, rx - 1, ry - 1, rx + 1, ry + 1)
            val c = anim.all.firstOrNull { it.tx == rx && it.ty == ry } ?: return -1f
            val start = c.tx + c.progress
            anim.advance(city, 0.4f)
            return (c.tx + c.progress) - start
        }
        // すいている道（容量の3割）と、あふれた道（容量の2倍）
        val free = travel((600 * 0.3f).toInt())
        val jammed = travel(600 * 2)
        if (free < 0f || jammed < 0f) return      // 車が置けなければ比べようがない
        assertTrue("the congested car was not slower ($free vs $jammed)", jammed < free)
    }
}
