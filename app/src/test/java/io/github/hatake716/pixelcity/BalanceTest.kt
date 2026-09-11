package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 遊びとして成立しているかを守るテスト。
 *
 * 「動く」だけでは不十分で、街が成長し、落ち着き、良い経営が報われる必要がある。
 * ここが壊れたらゲームが破綻しているので、係数を触ったら必ず通すこと。
 */
class BalanceTest {

    private fun blankCity(funds: Int = 10_000_000) =
        City().apply {
            for (t in tiles) t.terrain = Terrain.LAND
            this.funds = funds
        }

    /** 碁盤の目の道路と、需要をまかなう発電所を敷く。 */
    private fun layGrid(c: City) {
        for (y in 4..28 step 4) for (x in 2..29) c.build(x, y, TileKind.ROAD)
        for (x in 2..29 step 6) for (y in 4..28) c.build(x, y, TileKind.ROAD)
        for (i in 0..3) {
            c.build(3 + i * 7, 2, TileKind.POWER_COAL)
            c.build(3 + i * 7, 3, TileKind.ROAD)
        }
    }

    /** 建設を終えてから、所持金を通常の初期値へ戻す。経営だけを観察するため。 */
    private fun startOperating(c: City) {
        c.funds = City.STARTING_FUNDS
        c.gameOver = false
        c.monthsInDebt = 0
    }

    private fun zonedCity(): City {
        val c = blankCity()
        layGrid(c)
        for (y in 5..27) for (x in 2..29) {
            if (c.tileAt(x, y).kind != TileKind.EMPTY) continue
            val k = when {
                y >= 23 -> TileKind.ZONE_I
                y >= 17 -> TileKind.ZONE_C
                else -> TileKind.ZONE_R
            }
            c.build(x, y, k)
        }
        startOperating(c)
        return c
    }

    /** 公園・学校・病院・警察を備えた街。 */
    private fun servicedCity(): City {
        val c = blankCity()
        layGrid(c)
        for (y in 5..27) for (x in 2..29) {
            if (c.tileAt(x, y).kind != TileKind.EMPTY) continue
            val k = when {
                x % 6 == 3 && y % 5 == 1 -> TileKind.PARK
                (x == 8 && y == 7) || (x == 20 && y == 19) -> TileKind.SCHOOL
                (x == 8 && y == 19) || (x == 20 && y == 7) -> TileKind.HOSPITAL
                x == 14 && y == 13 -> TileKind.POLICE
                y >= 23 -> TileKind.ZONE_I
                y >= 17 -> TileKind.ZONE_C
                else -> TileKind.ZONE_R
            }
            c.build(x, y, k)
        }
        startOperating(c)
        return c
    }

    @Test
    fun `a zoned and powered city reaches a substantial population`() {
        val c = zonedCity()
        repeat(60) { c.step() }
        assertTrue("population=${c.population}", c.population > 1_500)
    }

    @Test
    fun `a zoned city stays solvent`() {
        val c = zonedCity()
        repeat(120) { c.step() }
        assertTrue("funds=${c.funds}", c.funds > City.STARTING_FUNDS)
        assertTrue(!c.gameOver)
    }

    /**
     * 街が振動しないこと。以前は全区分が足並みを揃えて成長・衰退し、
     * 人口が 968 と 2516 を往復していた。
     */
    @Test
    fun `population settles instead of oscillating`() {
        val c = zonedCity()
        repeat(80) { c.step() }
        val samples = (0 until 12).map { c.step(); c.population }
        val avg = samples.average()
        val worst = samples.maxOf { abs(it - avg) }
        assertTrue(
            "population swung by $worst around $avg: $samples",
            worst < avg * 0.15,
        )
    }

    /** 公共サービスへの投資が、人口という形で報われること。 */
    @Test
    fun `investing in public services grows a larger city`() {
        val plain = zonedCity()
        val serviced = servicedCity()
        repeat(150) { plain.step(); serviced.step() }
        assertTrue(
            "plain=${plain.population} serviced=${serviced.population}",
            serviced.population > plain.population * 1.1,
        )
    }

    /** 最高段階まで育つ余地があること。天井が低すぎると育てる楽しみがない。 */
    @Test
    fun `a well served city reaches the top development stage`() {
        val c = servicedCity()
        repeat(150) { c.step() }
        val topStage = c.tiles.count { it.kind == TileKind.ZONE_R && it.stage == 3 }
        assertTrue("stage3 tiles=$topStage", topStage > 0)
    }

    /** 電力が足りない街は、一部が停電して育たないが、全滅はしないこと。 */
    @Test
    fun `an underpowered city browns out partially rather than entirely`() {
        val c = blankCity()
        for (y in 4..28 step 4) for (x in 2..29) c.build(x, y, TileKind.ROAD)
        for (x in 2..29 step 6) for (y in 4..28) c.build(x, y, TileKind.ROAD)
        // 発電所は1つだけ。明らかに需要に足りない。
        c.build(3, 2, TileKind.POWER_COAL)
        c.build(3, 3, TileKind.ROAD)
        for (y in 5..27) for (x in 2..29) {
            if (c.tileAt(x, y).kind != TileKind.EMPTY) continue
            c.build(x, y, if (y >= 20) TileKind.ZONE_C else TileKind.ZONE_R)
        }
        startOperating(c)
        repeat(40) { c.step() }
        assertTrue("supply=${c.powerSupply} demand=${c.powerDemand}", c.powerSupply < c.powerDemand)
        val powered = c.tiles.count { it.powered && it.kind.isZone }
        val dark = c.tiles.count { !it.powered && it.kind.isZone }
        assertTrue("powered=$powered", powered > 0)
        assertTrue("dark=$dark", dark > 0)
    }

    /** 税率0%では収入がほぼ無く、維持費で赤字に沈むこと。 */
    @Test
    fun `a zero tax rate drives the city into debt`() {
        val c = zonedCity()
        c.taxRate = 0
        repeat(60) { c.step() }
        assertTrue("funds=${c.funds}", c.funds < City.STARTING_FUNDS)
    }
}
