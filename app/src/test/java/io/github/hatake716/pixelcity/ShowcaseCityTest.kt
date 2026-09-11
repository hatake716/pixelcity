package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Monument
import io.github.hatake716.pixelcity.game.TileKind
import io.github.hatake716.pixelcity.ui.CityRenderer
import io.github.hatake716.pixelcity.ui.PixelCanvas
import io.github.hatake716.pixelcity.ui.ShowcaseCity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * お手本の街。
 *
 * 「大都市」「田園都市」「工業都市」は、それぞれ説明どおりの性格を
 * 持っていなければならない。ここが崩れると、選ぶ意味がなくなる。
 */
class ShowcaseCityTest {

    private val metropolis by lazy { ShowcaseCity.build(ShowcaseCity.Kind.METROPOLIS) }
    private val garden by lazy { ShowcaseCity.build(ShowcaseCity.Kind.GARDEN) }
    private val industrial by lazy { ShowcaseCity.build(ShowcaseCity.Kind.INDUSTRIAL) }

    /** 12か月まわしたあとの街。そのまま遊べるかを見るため。 */
    private fun runFor(kind: ShowcaseCity.Kind, months: Int = 12): City =
        ShowcaseCity.build(kind).apply { repeat(months) { step() } }

    private fun avgPollution(c: City) = c.tiles.sumOf { it.pollution } / c.tiles.size

    @Test
    fun `there are three showcases`() {
        assertEquals(3, ShowcaseCity.Kind.entries.size)
        for (k in ShowcaseCity.Kind.entries) {
            assertTrue("${k.name} has no label", k.label.isNotBlank())
            assertTrue("${k.name} has no summary", k.summary.isNotBlank())
        }
    }

    /** どれも、選んだ瞬間から黒字で回ること。 */
    @Test
    fun `every showcase runs a surplus`() {
        for (k in ShowcaseCity.Kind.entries) {
            val c = runFor(k)
            assertTrue("${k.label} is in the red", c.lastIncome > c.lastUpkeep)
            assertTrue("${k.label} went bankrupt", !c.gameOver)
            assertTrue("${k.label} lost money", c.funds > 0)
        }
    }

    /** どれも電力が足りていること。足りないと街が衰退する。 */
    @Test
    fun `every showcase has enough power`() {
        for (k in ShowcaseCity.Kind.entries) {
            val c = runFor(k)
            assertTrue(
                "${k.label}: ${c.powerSupply}/${c.powerDemand}",
                c.powerSupply >= c.powerDemand,
            )
        }
    }

    /** どれも、12か月まわしても人口が大きく崩れないこと。 */
    @Test
    fun `no showcase collapses when it is played`() {
        for (k in ShowcaseCity.Kind.entries) {
            val before = ShowcaseCity.build(k).population
            val after = runFor(k).population
            assertTrue(
                "${k.label} fell from $before to $after",
                after > before * 0.75,
            )
        }
    }

    /** 大都市がいちばん人口が多いこと。 */
    @Test
    fun `the metropolis has the largest population`() {
        val m = runFor(ShowcaseCity.Kind.METROPOLIS).population
        val g = runFor(ShowcaseCity.Kind.GARDEN).population
        val i = runFor(ShowcaseCity.Kind.INDUSTRIAL).population
        assertTrue("metropolis=$m garden=$g", m > g)
        assertTrue("metropolis=$m industrial=$i", m > i)
    }

    /**
     * 田園都市は、農地・風力・鉄道を備え、火力発電を使わず、
     * 公害がいちばん少ないこと。
     */
    @Test
    fun `the garden city is green`() {
        val c = garden
        assertTrue("no farmland", c.tiles.count { it.kind == TileKind.FARM } > 100)
        assertTrue("no wind power", c.tiles.count { it.kind == TileKind.POWER_WIND } > 10)
        assertTrue("no railway", c.tiles.count { it.kind == TileKind.RAIL } > 50)
        assertEquals(
            "the garden city should not burn coal",
            0, c.tiles.count { it.kind == TileKind.POWER_COAL },
        )

        val gardenPollution = avgPollution(runFor(ShowcaseCity.Kind.GARDEN))
        val metroPollution = avgPollution(runFor(ShowcaseCity.Kind.METROPOLIS))
        val industrialPollution = avgPollution(runFor(ShowcaseCity.Kind.INDUSTRIAL))
        assertTrue("garden=$gardenPollution metro=$metroPollution", gardenPollution < metroPollution)
        assertTrue(
            "garden=$gardenPollution industrial=$industrialPollution",
            gardenPollution < industrialPollution,
        )
    }

    /** 工業都市は工業が主役で、資金がいちばん厚いこと。 */
    @Test
    fun `the industrial city is built on industry and is the richest`() {
        val c = industrial
        val industry = c.tiles.count { it.kind == TileKind.ZONE_I }
        val residential = c.tiles.count { it.kind == TileKind.ZONE_R }
        assertTrue("industry=$industry residential=$residential", industry > residential)

        // 他のどの街より資金を持っている
        for (k in listOf(ShowcaseCity.Kind.METROPOLIS, ShowcaseCity.Kind.GARDEN)) {
            assertTrue(
                "industrial=${c.funds} ${k.label}=${ShowcaseCity.build(k).funds}",
                c.funds > ShowcaseCity.build(k).funds,
            )
        }
    }

    /** 大都市は密度が高く、最高段階の建物が多いこと。 */
    @Test
    fun `the metropolis is densely developed`() {
        val built = metropolis.tiles.count { it.kind.isZone && it.stage > 0 }
        assertTrue("only $built developed tiles", built > 1_000)
        val top = metropolis.tiles.count { it.kind.isZone && it.stage == 3 }
        assertTrue("only $top top-stage tiles", top > 400)
    }

    /** 同じ見本からは、必ず同じ街ができること。 */
    @Test
    fun `each showcase is the same every time`() {
        for (k in ShowcaseCity.Kind.entries) {
            val a = ShowcaseCity.build(k)
            val b = ShowcaseCity.build(k)
            for (i in a.tiles.indices) {
                assertEquals("${k.label} tile $i", a.tiles[i].kind, b.tiles[i].kind)
                assertEquals("${k.label} stage $i", a.tiles[i].stage, b.tiles[i].stage)
            }
        }
    }

    /** 見本には見どころ（モニュメント）があること。 */
    @Test
    fun `every showcase has monuments on display`() {
        for (k in ShowcaseCity.Kind.entries) {
            val c = ShowcaseCity.build(k)
            assertTrue("${k.label} has no monument", c.builtMonuments.isNotEmpty())
            for (m in c.builtMonuments) {
                assertEquals(
                    "${k.label}: ${m.label} should have exactly one anchor",
                    1, c.tiles.count { it.monument == m },
                )
            }
        }
    }

    /** 大都市はすべてのモニュメントを見せること。 */
    @Test
    fun `the metropolis shows every monument`() {
        for (m in Monument.entries) {
            assertTrue("${m.label} is missing", m in metropolis.builtMonuments)
        }
    }

    /** 背景として描いたとき、画面が十分に埋まること。 */
    @Test
    fun `the metropolis fills the screen when drawn`() {
        val w = 480
        val h = 900
        val canvas = PixelCanvas(w, h)
        CityRenderer().draw(canvas, metropolis, 31f, 31f, 1, 3, 0, h)
        val painted = canvas.pixels.count { it.toInt() != io.github.hatake716.pixelcity.ui.Palette.SKY }
        val ratio = painted.toFloat() / (w * h)
        assertTrue("only ${(ratio * 100).toInt()}% of the screen is city", ratio > 0.45f)
    }
}
