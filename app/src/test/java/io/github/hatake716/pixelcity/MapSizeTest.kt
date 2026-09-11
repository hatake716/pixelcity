package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * マップの広さに関する取り決め。
 *
 * 一辺を変えたときに、地形・初期の平地・計算量が破綻しないことを守る。
 */
class MapSizeTest {

    @Test
    fun `the default map is 128 tiles on a side`() {
        val c = City()
        assertEquals(128, c.width)
        assertEquals(128, c.height)
        assertEquals(128 * 128, c.tiles.size)
    }

    /** どの大きさでも、建てられる土地が十分に残ること。 */
    @Test
    fun `terrain leaves most of the map buildable at any size`() {
        for (size in listOf(32, 64, 128)) {
            for (seed in 1L..5L) {
                val c = City(size, size).apply { generateTerrain(seed) }
                val land = c.tiles.count { it.terrain != Terrain.WATER }
                val ratio = land.toFloat() / c.tiles.size
                assertTrue("size=$size seed=$seed land=${(ratio * 100).toInt()}%", ratio > 0.6f)
            }
        }
    }

    /** 広いマップでは、海と川もそれに応じた大きさになること。 */
    @Test
    fun `terrain features scale with the map`() {
        val small = City(32, 32).apply { generateTerrain(1L) }
        val big = City(128, 128).apply { generateTerrain(1L) }
        val smallWater = small.tiles.count { it.terrain == Terrain.WATER }.toFloat() / small.tiles.size
        val bigWater = big.tiles.count { it.terrain == Terrain.WATER }.toFloat() / big.tiles.size
        // 水の割合が、極端に減っていない（海が糸のように細くなっていない）
        assertTrue("small=$smallWater big=$bigWater", bigWater > smallWater * 0.5f)
    }

    /** 中心の平地も、マップに応じて広がること。 */
    @Test
    fun `the starting area scales with the map`() {
        for (size in listOf(32, 128)) {
            val c = City(size, size).apply { generateTerrain(3L); clearStartingArea() }
            val cx = size / 2
            val cy = size / 2
            val half = (size / 7).coerceAtLeast(5)
            for (y in (cy - half)..(cy + half)) {
                for (x in (cx - half)..(cx + half)) {
                    assertTrue(
                        "size=$size ($x,$y) is not buildable",
                        c.buildBlocker(x, y, TileKind.ROAD) == null,
                    )
                }
            }
        }
    }

    /**
     * 1か月の計算が、広いマップでも現実的な時間で終わること。
     * 1か月は8秒なので、数ミリ秒なら余裕がある。
     */
    @Test
    fun `a month is simulated quickly even on the big map`() {
        val c = City().apply { generateTerrain(9L); clearStartingArea() }
        c.funds = 100_000_000
        // 中央に大きな街を敷く
        for (y in 50 until 80 step 3) for (x in 50 until 80) c.build(x, y, TileKind.ROAD)
        for (x in 50 until 80 step 4) for (y in 50 until 80) c.build(x, y, TileKind.ROAD)
        for (y in 50 until 80) for (x in 50 until 80) {
            if (c.tileAt(x, y).kind == TileKind.EMPTY) c.build(x, y, TileKind.ZONE_R)
        }
        repeat(3) { c.step() }   // 暖機
        val start = System.nanoTime()
        repeat(12) { c.step() }
        val msPerMonth = (System.nanoTime() - start) / 1_000_000.0 / 12
        assertTrue("a month took %.1f ms".format(msPerMonth), msPerMonth < 200.0)
    }
}
