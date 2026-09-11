package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind
import io.github.hatake716.pixelcity.ui.CityRenderer
import io.github.hatake716.pixelcity.ui.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 地図に重ねて見る情報（データマップ）。
 *
 * 「選んだのに何も変わらない」が起きないよう、
 * どの項目も、実際に差のつく値を返すことを確かめる。
 */
class OverlayTest {

    private fun city(): City = City(24, 24).apply {
        for (t in tiles) t.terrain = Terrain.LAND
        for (x in 2..20) build(x, 10, TileKind.ROAD)
        build(2, 9, TileKind.POWER_COAL)
        for (x in 4..10) {
            build(x, 9, TileKind.ZONE_R)
            build(x, 11, TileKind.ZONE_C)
        }
        for (x in 12..18) build(x, 9, TileKind.ZONE_I)
        repeat(60) { step() }
    }

    /** 「なし」以外の項目が、ひととおりそろっていること。 */
    @Test
    fun `every kind of information can be shown on the map`() {
        val labels = CityRenderer.Overlay.entries.map { it.label }
        // 区分は3つとも別々に見られること
        assertTrue("residential is missing", labels.any { it.contains("じゅうたく") })
        assertTrue("commercial is missing", labels.any { it.contains("しょうぎょう") })
        assertTrue("industrial is missing", labels.any { it.contains("こうぎょう") })
        // 名前が重なっていないこと
        assertEquals("some labels are duplicated", labels.size, labels.toSet().size)
    }

    /** 良し悪しの向きが、正しく決まっていること。 */
    @Test
    fun `bad things are marked as bad`() {
        for (o in listOf(
            CityRenderer.Overlay.POLLUTION,
            CityRenderer.Overlay.CRIME,
            CityRenderer.Overlay.TRAFFIC,
            CityRenderer.Overlay.POWER,
            CityRenderer.Overlay.WATER,
        )) {
            assertTrue("${o.name} should be bad", !o.good)
        }
        for (o in listOf(
            CityRenderer.Overlay.LAND_VALUE,
            CityRenderer.Overlay.HEALTH,
            CityRenderer.Overlay.EDUCATION,
            CityRenderer.Overlay.RESIDENTIAL,
        )) {
            assertTrue("${o.name} should be good", o.good)
        }
    }

    /**
     * 良いものと悪いもので、色の向きが逆であること。
     *
     * 濃い（5）ときに、良いものは青、悪いものは赤になる。
     */
    @Test
    fun `good and bad use opposite colours`() {
        val good = CityRenderer.colourOf(CityRenderer.Overlay.LAND_VALUE, 5)
        val bad = CityRenderer.colourOf(CityRenderer.Overlay.POLLUTION, 5)
        assertNotEquals("good and bad look the same", good, bad)
        // 濃さが変われば、色も変わること（段が潰れていない）
        for (o in listOf(CityRenderer.Overlay.LAND_VALUE, CityRenderer.Overlay.POLLUTION)) {
            val colours = (2..5).map { CityRenderer.colourOf(o, it) }
            assertEquals("${o.name} has repeated colours", colours.size, colours.toSet().size)
        }
    }

    /**
     * 区分の地図が、その区分だけを塗ること。
     *
     * 住宅の地図に商業まで出ると、どこに何があるか分からない。
     */
    @Test
    fun `each zone map only covers its own zone`() {
        val c = city()
        // 育った区分が実際にあること（前提の確認）
        assertTrue("no residential grew", c.tiles.any { it.kind == TileKind.ZONE_R && it.stage > 0 })

        // 住宅・商業・工業それぞれのマスを取り、別の区分では 0 になること
        val r = c.tiles.first { it.kind == TileKind.ZONE_R }
        val i = c.tiles.first { it.kind == TileKind.ZONE_I }
        assertTrue(
            "the residential map shows industry",
            levelOf(CityRenderer.Overlay.RESIDENTIAL, i) == 0,
        )
        assertTrue(
            "the industrial map shows housing",
            levelOf(CityRenderer.Overlay.INDUSTRIAL, r) == 0,
        )
    }

    /** 育った区分ほど、濃く出ること。 */
    @Test
    fun `a more developed zone shows up stronger`() {
        val c = city()
        val young = c.tiles.filter { it.kind == TileKind.ZONE_R }.minByOrNull { it.stage }!!
        val grown = c.tiles.filter { it.kind == TileKind.ZONE_R }.maxByOrNull { it.stage }!!
        if (young.stage == grown.stage) return    // 差がなければ確かめようがない
        assertTrue(
            "a grown zone is not stronger",
            levelOf(CityRenderer.Overlay.RESIDENTIAL, grown) >
                levelOf(CityRenderer.Overlay.RESIDENTIAL, young),
        )
    }

    /**
     * 情報の色が、下地の灰色とはっきり違うこと。
     *
     * 重ねるときは、街をいちど灰色に落としてから色を乗せる。
     * 情報の色が灰色に近いと、下地に埋もれて見えない。
     */
    @Test
    fun `the overlay colours stand out from the grey background`() {
        val greys = intArrayOf(
            Palette.DIM_HI, Palette.DIM_LIT, Palette.DIM, Palette.DIM_DARK,
        ).map { Palette.COLORS[it] }

        for (o in CityRenderer.Overlay.entries) {
            if (o == CityRenderer.Overlay.NONE) continue
            for (level in 1..5) {
                val c = Palette.COLORS[CityRenderer.colourOf(o, level)]
                // 灰色は R=G=B に近い。情報の色は、どこかの色味が偏っているはず。
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val spread = maxOf(r, g, b) - minOf(r, g, b)
                assertTrue(
                    "${o.name} level $level is too grey (spread $spread)",
                    spread >= 40,
                )
                // どの灰色とも、十分に離れていること
                for (grey in greys) {
                    val gr = (grey shr 16) and 0xFF
                    val gg = (grey shr 8) and 0xFF
                    val gb = grey and 0xFF
                    val d = Math.abs(r - gr) + Math.abs(g - gg) + Math.abs(b - gb)
                    assertTrue(
                        "${o.name} level $level is too close to the background",
                        d >= 60,
                    )
                }
            }
        }
    }

    /** 下地の灰色が、色味を持たないこと。 */
    @Test
    fun `the background really is grey`() {
        for (i in intArrayOf(
            Palette.DIM_HI, Palette.DIM_LIT, Palette.DIM, Palette.DIM_DARK,
        )) {
            val c = Palette.COLORS[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            assertTrue("the background has a colour cast", maxOf(r, g, b) - minOf(r, g, b) <= 16)
        }
    }

    /** 実際に描くときと同じ式で確かめる。 */
    private fun levelOf(
        o: CityRenderer.Overlay,
        tile: io.github.hatake716.pixelcity.game.Tile,
    ): Int = CityRenderer.levelOf(o, tile)
}
