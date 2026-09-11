package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind
import io.github.hatake716.pixelcity.game.Tutorial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialTest {

    @Test
    fun `tutorial starts at the first step`() {
        val t = Tutorial()
        t.start()
        assertTrue(t.active)
        assertEquals(0, t.stepIndex)
        assertNotNull(t.step)
    }

    @Test
    fun `skipping leaves the tutorial inactive`() {
        val t = Tutorial()
        t.start()
        t.skip()
        assertFalse(t.active)
        assertFalse(t.finished)
        // スキップ後は何でも建てられる
        assertTrue(t.allowsBuild(TileKind.ZONE_R))
        assertTrue(t.allowsSpeedChange())
    }

    /** 関係のない操作を弾くこと。初心者が手順から外れて詰まらないようにするため。 */
    @Test
    fun `only the step's own action is allowed`() {
        val t = Tutorial()
        t.start()
        t.onContinuePressed()  // step0 -> 1
        t.onContinuePressed()  // step1 -> 2 (道路のステップ)
        assertTrue(t.allowsBuild(TileKind.ROAD))
        assertFalse(t.allowsBuild(TileKind.ZONE_R))
        assertFalse(t.allowsSpeedChange())
        assertFalse(t.allowsBudget())
    }

    @Test
    fun `building the required count advances the step`() {
        val t = Tutorial()
        t.start()
        t.onContinuePressed()
        t.onContinuePressed()
        val before = t.stepIndex
        repeat(6) { t.onBuilt(TileKind.ROAD) }
        assertEquals(before + 1, t.stepIndex)
    }

    @Test
    fun `irrelevant construction does not count toward progress`() {
        val t = Tutorial()
        t.start()
        t.onContinuePressed()
        t.onContinuePressed()
        val step = t.stepIndex
        repeat(5) { t.onBuilt(TileKind.ZONE_R) }
        assertEquals(step, t.stepIndex)
        assertEquals(6, t.remaining())
    }

    @Test
    fun `advancing months satisfies the time step`() {
        val t = Tutorial()
        t.start()
        runThrough(t, upToGoalOfType = "advance")
        assertTrue(t.allowsSpeedChange())
        val step = t.stepIndex
        repeat(3) { t.onMonthPassed() }
        assertEquals(step + 1, t.stepIndex)
    }

    @Test
    fun `completing every step finishes the tutorial`() {
        val t = Tutorial()
        t.start()
        completeAll(t)
        assertTrue(t.finished)
        assertFalse(t.active)
        // 卒業後は自由に操作できる
        assertTrue(t.allowsBuild(TileKind.HOSPITAL))
        assertTrue(t.allowsBudget())
    }

    @Test
    fun `state survives save and restore`() {
        val t = Tutorial()
        t.start()
        t.onContinuePressed()
        t.onContinuePressed()
        t.onBuilt(TileKind.ROAD)
        t.onBuilt(TileKind.ROAD)
        val saved = t.saveState()

        val restored = Tutorial()
        restored.restore(saved)
        assertTrue(restored.active)
        assertEquals(t.stepIndex, restored.stepIndex)
        assertEquals(t.remaining(), restored.remaining())
    }

    /**
     * 仕様の要: チュートリアルを完走したら、そのまま回り始める街が残ること。
     * 手順どおりに建てた街を実際にシミュレートして確かめる。
     */
    @Test
    fun `a city built by following the tutorial grows and turns a profit`() {
        val c = City().apply { for (t in tiles) t.terrain = Terrain.LAND }
        c.funds = City.STARTING_FUNDS
        val t = Tutorial()
        t.start()

        t.onContinuePressed()
        t.onContinuePressed()

        // 手順3: 道路を6マス
        for (x in 8..13) { assertTrue(c.build(x, 12, TileKind.ROAD)); t.onBuilt(TileKind.ROAD) }
        // 手順4: 発電所
        assertTrue(c.build(8, 10, TileKind.POWER_COAL)); t.onBuilt(TileKind.POWER_COAL)
        assertTrue(c.build(8, 11, TileKind.ROAD))
        // 手順5: 住宅8マス
        for (x in 9..12) {
            c.build(x, 11, TileKind.ZONE_R); t.onBuilt(TileKind.ZONE_R)
            c.build(x, 13, TileKind.ZONE_R); t.onBuilt(TileKind.ZONE_R)
        }
        // 手順6: 商業4マス
        for (x in 14..15) {
            c.build(x, 12, TileKind.ROAD)
            c.build(x, 11, TileKind.ZONE_C); t.onBuilt(TileKind.ZONE_C)
            c.build(x, 13, TileKind.ZONE_C); t.onBuilt(TileKind.ZONE_C)
        }
        // 手順7: 工業4マス（住宅から離す）
        for (x in 16..17) {
            c.build(x, 12, TileKind.ROAD)
            c.build(x, 11, TileKind.ZONE_I); t.onBuilt(TileKind.ZONE_I)
            c.build(x, 13, TileKind.ZONE_I); t.onBuilt(TileKind.ZONE_I)
        }
        // 手順8: 3か月
        repeat(3) { c.step(); t.onMonthPassed() }
        // 手順9: 公園2つ
        c.build(10, 10, TileKind.PARK); t.onBuilt(TileKind.PARK)
        c.build(11, 14, TileKind.PARK); t.onBuilt(TileKind.PARK)
        // 手順10: 予算を開く
        t.onBudgetOpened()
        // 手順11: 卒業
        t.onContinuePressed()
        assertTrue("tutorial should be finished", t.finished)

        // ここから自走できるか
        repeat(36) { c.step() }
        assertTrue("population=${c.population}", c.population > 100)
        assertTrue("funds=${c.funds}", c.funds > 0)
        assertFalse("should not be bankrupt", c.gameOver)
        assertTrue(
            "income=${c.lastIncome} upkeep=${c.lastUpkeep}",
            c.lastIncome > c.lastUpkeep,
        )
    }

    // --- helpers ---

    private fun runThrough(t: Tutorial, upToGoalOfType: String) {
        var guard = 0
        while (t.active && guard++ < 100) {
            val g = t.step?.goal ?: break
            if (upToGoalOfType == "advance" && g is Tutorial.Goal.Advance) return
            satisfy(t, g)
        }
    }

    private fun completeAll(t: Tutorial) {
        var guard = 0
        while (t.active && guard++ < 100) {
            satisfy(t, t.step?.goal ?: break)
        }
    }

    private fun satisfy(t: Tutorial, g: Tutorial.Goal) {
        when (g) {
            is Tutorial.Goal.Continue -> t.onContinuePressed()
            is Tutorial.Goal.Place -> repeat(g.count) { t.onBuilt(g.kind) }
            is Tutorial.Goal.Advance -> repeat(g.months) { t.onMonthPassed() }
            is Tutorial.Goal.OpenBudget -> t.onBudgetOpened()
        }
    }
}
