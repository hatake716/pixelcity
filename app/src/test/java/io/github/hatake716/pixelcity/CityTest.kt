package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.game.BuildCost
import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Monument
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CityTest {

    /** 全面陸地の都市。地形生成の揺らぎを排して検証する。 */
    private fun flatCity(): City = City().apply {
        for (t in tiles) t.terrain = Terrain.LAND
    }

    @Test
    fun `terrain generation is deterministic for a seed`() {
        val a = City().apply { generateTerrain(42L) }
        val b = City().apply { generateTerrain(42L) }
        for (i in a.tiles.indices) {
            assertEquals("tile $i", a.tiles[i].terrain, b.tiles[i].terrain)
        }
    }

    @Test
    fun `terrain generation leaves plenty of buildable land`() {
        val c = City().apply { generateTerrain(7L) }
        val land = c.tiles.count { it.terrain != Terrain.WATER }
        assertTrue("buildable=$land", land > c.tiles.size / 2)
    }

    @Test
    fun `building deducts funds and sets the tile`() {
        val c = flatCity()
        val before = c.funds
        assertTrue(c.build(4, 4, TileKind.ROAD))
        assertEquals(TileKind.ROAD, c.tileAt(4, 4).kind)
        assertEquals(before - BuildCost.cost(TileKind.ROAD), c.funds)
    }

    @Test
    fun `cannot build on water`() {
        val c = flatCity()
        c.tileAt(2, 2).terrain = Terrain.WATER
        assertNotNull(c.buildBlocker(2, 2, TileKind.ROAD))
        assertFalse(c.build(2, 2, TileKind.ROAD))
    }

    @Test
    fun `cannot build without funds`() {
        val c = flatCity()
        c.funds = 0
        assertNotNull(c.buildBlocker(4, 4, TileKind.POWER_COAL))
        assertFalse(c.build(4, 4, TileKind.POWER_COAL))
    }

    @Test
    fun `replacing a tile also charges the bulldoze fee`() {
        val c = flatCity()
        c.build(4, 4, TileKind.ROAD)
        val before = c.funds
        assertTrue(c.build(4, 4, TileKind.ZONE_R))
        assertEquals(before - BuildCost.cost(TileKind.ZONE_R) - BuildCost.BULLDOZE, c.funds)
    }

    @Test
    fun `bulldoze clears the tile`() {
        val c = flatCity()
        c.build(4, 4, TileKind.ZONE_R)
        assertTrue(c.bulldoze(4, 4))
        assertEquals(TileKind.EMPTY, c.tileAt(4, 4).kind)
        assertEquals(0, c.tileAt(4, 4).stage)
    }

    @Test
    fun `solar plant is locked until population 3000`() {
        val c = flatCity()
        assertFalse(BuildCost.isUnlocked(TileKind.POWER_SOLAR, 0))
        assertNotNull(c.buildBlocker(4, 4, TileKind.POWER_SOLAR))
        assertTrue(BuildCost.isUnlocked(TileKind.POWER_SOLAR, 3_000))
    }

    /** 道路・発電所・住宅・商業・工業を敷いた、成長できる小さな街。 */
    private fun seededCity(): City {
        val c = flatCity()
        c.funds = 100_000
        // 横一本の道路
        for (x in 2..20) c.build(x, 10, TileKind.ROAD)
        c.build(2, 9, TileKind.POWER_COAL)
        for (x in 4..9) {
            c.build(x, 9, TileKind.ZONE_R)
            c.build(x, 11, TileKind.ZONE_R)
        }
        for (x in 11..14) c.build(x, 9, TileKind.ZONE_C)
        for (x in 16..19) c.build(x, 11, TileKind.ZONE_I)
        return c
    }

    @Test
    fun `a well laid out city grows its population`() {
        val c = seededCity()
        repeat(24) { c.step() }
        assertTrue("population=${c.population}", c.population > 0)
    }

    @Test
    fun `zones do not grow without a road connection`() {
        val c = flatCity()
        c.funds = 100_000
        c.build(2, 2, TileKind.POWER_COAL)
        // 道路にまったく触れていない住宅
        for (x in 10..14) c.build(x, 20, TileKind.ZONE_R)
        repeat(12) { c.step() }
        assertEquals(0, c.population)
    }

    @Test
    fun `zones do not grow without power`() {
        val c = flatCity()
        c.funds = 100_000
        for (x in 2..20) c.build(x, 10, TileKind.ROAD)
        for (x in 4..9) c.build(x, 9, TileKind.ZONE_R)
        for (x in 11..14) c.build(x, 9, TileKind.ZONE_C)
        repeat(12) { c.step() }
        assertEquals(0, c.population)
    }

    @Test
    fun `power demand is met by a coal plant in a small city`() {
        val c = seededCity()
        repeat(6) { c.step() }
        assertTrue(c.powerSupply >= c.powerDemand)
        assertEquals(1f, c.powerRatio, 0.001f)
    }

    @Test
    fun `a punitive tax rate suppresses demand`() {
        val low = seededCity().apply { taxRate = 5 }
        val high = seededCity().apply { taxRate = 20 }
        repeat(24) { low.step(); high.step() }
        assertTrue(
            "low=${low.population} high=${high.population}",
            low.population > high.population,
        )
    }

    @Test
    fun `parks raise land value nearby`() {
        val c = seededCity()
        repeat(6) { c.step() }
        val before = c.tileAt(6, 9).landValue
        c.build(6, 8, TileKind.PARK)
        c.step()
        assertTrue("before=$before after=${c.tileAt(6, 9).landValue}", c.tileAt(6, 9).landValue > before)
    }

    @Test
    fun `a coal plant pollutes its surroundings`() {
        val c = flatCity()
        c.funds = 100_000
        c.build(15, 15, TileKind.POWER_COAL)
        c.step()
        assertTrue(c.tileAt(16, 15).pollution > 0)
        // 遠方は汚染されない
        assertEquals(0, c.tileAt(29, 29).pollution)
    }

    @Test
    fun `running a deficit for twelve months ends the game`() {
        val c = flatCity()
        c.funds = -1
        repeat(City.BANKRUPT_MONTHS) { c.step() }
        assertTrue(c.gameOver)
    }

    @Test
    fun `a warning appears before bankruptcy`() {
        val c = flatCity()
        c.funds = -1
        assertNull(c.bankruptcyWarning())
        repeat(City.BANKRUPT_WARN_MONTHS) { c.step() }
        assertNotNull(c.bankruptcyWarning())
        assertFalse(c.gameOver)
    }

    @Test
    fun `recovering from debt resets the bankruptcy countdown`() {
        val c = flatCity()
        c.funds = -1
        repeat(3) { c.step() }
        assertTrue(c.monthsInDebt > 0)
        c.funds = 5_000
        c.step()
        assertEquals(0, c.monthsInDebt)
    }

    @Test
    fun `monuments are locked until their population threshold`() {
        val c = flatCity()
        c.funds = 100_000
        c.population = 0
        assertNotNull(c.monumentBlocker(5, 5, Monument.TOKYO_TOWER))
        c.population = Monument.TOKYO_TOWER.unlockPopulation
        assertNull(c.monumentBlocker(5, 5, Monument.TOKYO_TOWER))
    }

    @Test
    fun `a monument occupies four tiles and can only be built once`() {
        val c = flatCity()
        c.funds = 100_000
        c.population = 99_999
        assertTrue(c.buildMonument(5, 5, Monument.TOKYO_TOWER))
        val occupied = c.tiles.count { it.kind == TileKind.MONUMENT }
        assertEquals(4, occupied)
        assertEquals(Monument.TOKYO_TOWER, c.tileAt(5, 5).monument)
        assertNotNull(c.monumentBlocker(20, 20, Monument.TOKYO_TOWER))
    }

    @Test
    fun `bulldozing any monument tile removes the whole monument`() {
        val c = flatCity()
        c.funds = 100_000
        c.population = 99_999
        c.buildMonument(5, 5, Monument.TOKYO_TOWER)
        // 左上ではなく右下のタイルを壊す
        assertTrue(c.bulldoze(6, 6))
        assertEquals(0, c.tiles.count { it.kind == TileKind.MONUMENT })
        assertFalse(Monument.TOKYO_TOWER in c.builtMonuments)
        assertTrue(c.monumentBlocker(5, 5, Monument.TOKYO_TOWER) == null)
    }

    @Test
    fun `the statue of liberty requires a waterfront`() {
        val c = flatCity()
        c.funds = 100_000
        c.population = 99_999
        assertNotNull(c.monumentBlocker(5, 5, Monument.STATUE_OF_LIBERTY))
        c.tileAt(7, 5).terrain = Terrain.WATER
        assertNull(c.monumentBlocker(5, 5, Monument.STATUE_OF_LIBERTY))
    }

    @Test
    fun `monuments raise land value and pay tourism income`() {
        val c = seededCity()
        repeat(6) { c.step() }
        val before = c.tileAt(8, 9).landValue
        c.population = 99_999
        assertTrue(c.buildMonument(7, 7, Monument.TOKYO_TOWER))
        c.step()
        assertTrue(c.tileAt(8, 9).landValue > before)
        assertEquals(Monument.TOKYO_TOWER.tourismIncome, c.tourismIncome)
    }

    @Test
    fun `next locked monument is the cheapest unmet goal`() {
        val c = flatCity()
        assertEquals(Monument.TOKYO_TOWER, c.nextLockedMonument())
    }
}
