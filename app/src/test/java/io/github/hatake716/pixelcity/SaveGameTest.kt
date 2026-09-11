package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.data.SaveGame
import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Monument
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind
import io.github.hatake716.pixelcity.game.Tutorial
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 保存と復元。Android に依存しない [SaveGame.parse] を通して確かめる。
 * 書き出し側は同じ JSON を組み立てるので、ここで形を固定しておく。
 */
class SaveGameTest {

    /** 保存される JSON を、Context なしで組み立てる（SaveGame.save と同じ形）。 */
    private fun serialize(city: City, tutorial: Tutorial, seed: Long): String {
        val json = JSONObject().apply {
            put("version", 1)
            put("seed", seed)
            put("width", city.width)
            put("height", city.height)
            put("funds", city.funds)
            put("month", city.month)
            put("taxRate", city.taxRate)
            put("monthsInDebt", city.monthsInDebt)
            put("gameOver", city.gameOver)
            val terrain = StringBuilder()
            val kinds = StringBuilder()
            val stages = StringBuilder()
            for (t in city.tiles) {
                terrain.append(('0' + t.terrain.ordinal))
                kinds.append(('0' + t.kind.ordinal))
                stages.append(('0' + t.stage))
            }
            put("terrain", terrain.toString())
            put("kinds", kinds.toString())
            put("stages", stages.toString())
            val monuments = JSONArray()
            for ((i, t) in city.tiles.withIndex()) {
                val m = t.monument ?: continue
                monuments.put(JSONObject().apply { put("i", i); put("m", m.name) })
            }
            put("monuments", monuments)
            put("tutorial", JSONArray().apply { tutorial.saveState().forEach { put(it) } })
        }
        return json.toString()
    }

    private fun sampleCity(): City = City().apply {
        generateTerrain(1234L)
        funds = 12_345
        month = 42
        taxRate = 11
        for (x in 4..12) build(x, 8, TileKind.ROAD)
        build(4, 7, TileKind.POWER_COAL)
        build(6, 7, TileKind.ZONE_R)
        build(7, 7, TileKind.ZONE_C)
        build(8, 7, TileKind.ZONE_I)
        build(9, 7, TileKind.PARK)
        tileAt(6, 7).stage = 3
        tileAt(7, 7).stage = 2
    }

    @Test
    fun `a city round trips through save and load`() {
        val city = sampleCity()
        val tutorial = Tutorial()
        val restored = SaveGame.parse(serialize(city, tutorial, 1234L))

        assertEquals(city.funds, restored.city.funds)
        assertEquals(city.month, restored.city.month)
        assertEquals(city.taxRate, restored.city.taxRate)
        assertEquals(1234L, restored.seed)
        for (i in city.tiles.indices) {
            assertEquals("tile $i terrain", city.tiles[i].terrain, restored.city.tiles[i].terrain)
            assertEquals("tile $i kind", city.tiles[i].kind, restored.city.tiles[i].kind)
            assertEquals("tile $i stage", city.tiles[i].stage, restored.city.tiles[i].stage)
        }
    }

    @Test
    fun `monuments survive a round trip and stay linked`() {
        val city = sampleCity()
        city.funds = 100_000
        city.population = 99_999
        assertTrue(city.buildMonument(20, 20, Monument.COLOSSEUM))

        val restored = SaveGame.parse(serialize(city, Tutorial(), 1L)).city
        assertEquals(Monument.COLOSSEUM, restored.tileAt(20, 20).monument)
        assertTrue(Monument.COLOSSEUM in restored.builtMonuments)
        assertEquals(4, restored.tiles.count { it.kind == TileKind.MONUMENT })
        // 占有タイルから本体を辿れる
        val anchor = restored.index(20, 20)
        assertEquals(anchor, restored.tileAt(21, 21).monumentAnchor)
        // 壊せば4タイルとも消える
        restored.bulldoze(21, 21)
        assertEquals(0, restored.tiles.count { it.kind == TileKind.MONUMENT })
    }

    @Test
    fun `tutorial progress survives a round trip`() {
        val tutorial = Tutorial()
        tutorial.start()
        tutorial.onContinuePressed()
        tutorial.onContinuePressed()
        tutorial.onBuilt(TileKind.ROAD)
        val restored = SaveGame.parse(serialize(sampleCity(), tutorial, 0L)).tutorial
        assertTrue(restored.active)
        assertEquals(tutorial.stepIndex, restored.stepIndex)
        assertEquals(tutorial.remaining(), restored.remaining())
    }

    /** 復元した街が、そのまま問題なく動き続けること。 */
    @Test
    fun `a restored city keeps simulating`() {
        val city = sampleCity()
        repeat(10) { city.step() }
        val restored = SaveGame.parse(serialize(city, Tutorial(), 0L)).city
        repeat(10) { restored.step() }
        assertEquals(city.month + 10, restored.month)
    }

    @Test
    fun `water tiles stay unbuildable after loading`() {
        val city = sampleCity()
        val restored = SaveGame.parse(serialize(city, Tutorial(), 0L)).city
        val water = restored.tiles.indexOfFirst { it.terrain == Terrain.WATER }
        assertTrue("no water in sample", water >= 0)
        val wx = water % restored.width
        val wy = water / restored.width
        assertTrue(restored.buildBlocker(wx, wy, TileKind.ROAD) != null)
    }
}
