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

    /** その手順が求める設置数。数を変えてもテストが追随するように定義から読む。 */
    private fun requiredCount(kind: TileKind): Int =
        Tutorial.STEPS.mapNotNull { it.goal as? Tutorial.Goal.Place }
            .first { it.kind == kind }.count

    @Test
    fun `building the required count advances the step`() {
        val t = Tutorial()
        t.start()
        t.onContinuePressed()
        t.onContinuePressed()
        val before = t.stepIndex
        repeat(requiredCount(TileKind.ROAD)) { t.onBuilt(TileKind.ROAD) }
        assertEquals(before + 1, t.stepIndex)
    }

    @Test
    fun `irrelevant construction does not count toward progress`() {
        val t = Tutorial()
        t.start()
        t.onContinuePressed()
        t.onContinuePressed()
        val step = t.stepIndex
        // いまの手順が求めている数を、定義から読む
        val want = (t.step?.goal as? Tutorial.Goal.Place)?.count
        repeat(5) { t.onBuilt(TileKind.ZONE_R) }
        assertEquals("an unrelated build advanced the step", step, t.stepIndex)
        assertEquals("an unrelated build counted as progress", want, t.remaining())
    }

    @Test
    fun `advancing months satisfies the time step`() {
        val t = Tutorial()
        t.start()
        runThrough(t, upToGoalOfType = "advance")
        assertTrue(t.allowsSpeedChange())
        val step = t.stepIndex
        // 進める月数は手順しだいなので、定義から読む
        val months = (t.step?.goal as? Tutorial.Goal.Advance)?.months ?: 0
        assertTrue("this step does not advance time", months > 0)
        repeat(months) { t.onMonthPassed() }
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

    /** 求められた数だけ置いたら、その先は数えないこと（remaining が 0 で止まる）。 */
    @Test
    fun `remaining stops at zero and the step advances exactly once`() {
        val t = Tutorial()
        t.start()
        t.onContinuePressed()
        t.onContinuePressed()
        val step = t.stepIndex
        repeat(requiredCount(TileKind.ROAD)) { t.onBuilt(TileKind.ROAD) }
        assertEquals(step + 1, t.stepIndex)
        // 次のステップの目標に、前のステップの分が持ち越されていない
        val nextGoal = t.step?.goal as? Tutorial.Goal.Place
        assertEquals("progress carried over", nextGoal?.count, t.remaining())
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
     * チュートリアルが要求する設置数が、自分で敷かせた道路の周りに収まること。
     *
     * 道路6マスに対して区分16マスを求めていた時期があり、指示どおりに進めると
     * 置く場所が足りなくなって、そこから先へ進めなくなっていた。
     */
    @Test
    fun `the tutorial asks for no more tiles than its own road can host`() {
        var roads = 0
        var needed = 0
        for (step in Tutorial.STEPS) {
            val goal = step.goal
            if (goal !is Tutorial.Goal.Place) continue
            if (goal.kind == TileKind.ROAD) roads += goal.count else needed += goal.count
        }
        // 一直線の道路に接するマス: 上下に roads ずつ、両端に1つずつ
        val hostable = roads * 2 + 2
        assertTrue(
            "needs $needed tiles but a $roads-tile road hosts only $hostable",
            needed <= hostable,
        )
    }

    /**
     * 仕様の要: チュートリアルを完走したら、
     * **財政が健全な中規模都市**が残ること。
     *
     * 手順どおりに建てた街を実際にシミュレートして確かめる。
     * ここが崩れると、この手引きの意味がなくなる。
     */
    @Test
    fun `a city built by following the tutorial is a healthy medium sized city`() {
        val c = City().apply {
            for (t in tiles) t.terrain = Terrain.LAND
            clearStartingArea()
        }
        c.funds = Tutorial.GRANT
        c.disasterLevel = City.DisasterLevel.NONE
        val t = Tutorial().apply { start() }

        val cx = 64
        val cy = 64

        /** 本編と同じく、チュートリアル中は資金が尽きないよう補填する。 */
        fun grant() { if (c.funds < 2_000) c.funds += Tutorial.GRANT }
        fun put(x: Int, y: Int, k: TileKind) {
            grant()
            if (c.build(x, y, k)) t.onBuilt(k)
        }
        fun months(n: Int) = repeat(n) { c.step(); t.onMonthPassed(); grant() }

        /** 道路に接した空きマスへ、散らして置く。 */
        fun spread(k: TileKind, n: Int) {
            val spots = mutableListOf<Pair<Int, Int>>()
            for (y in cy - 13..cy + 13) for (x in cx - 13..cx + 13) {
                val tile = c.tileOrNull(x, y) ?: continue
                if (tile.kind == TileKind.EMPTY && c.touchesRoad(x, y)) spots.add(x to y)
            }
            var done = 0
            val stride = (spots.size / n.coerceAtLeast(1)).coerceAtLeast(1)
            for (pass in 0..1) {
                var i = 0
                while (done < n && i < spots.size) {
                    val (x, y) = spots[i]
                    if (c.tileAt(x, y).kind == TileKind.EMPTY) { put(x, y, k); done++ }
                    i += if (pass == 0) stride else 1
                }
            }
        }

        t.onContinuePressed(); t.onContinuePressed()

        // 1章: 碁盤の目の道路、電気、水、区分
        for (y in cy - 11..cy + 11 step 3) for (x in cx - 11..cx + 11) put(x, y, TileKind.ROAD)
        for (x in cx - 11..cx + 11 step 4) for (y in cy - 11..cy + 11) put(x, y, TileKind.ROAD)
        spread(TileKind.POWER_COAL, 3)
        spread(TileKind.WATER_TOWER, 7)
        spread(TileKind.ZONE_R, 70)
        spread(TileKind.ZONE_C, 45)
        spread(TileKind.ZONE_I, 20)
        months(24)

        // 2章: ゴミ、きれいな電気、交通
        spread(TileKind.LANDFILL, 6)
        if (c.population >= 1_000) spread(TileKind.POWER_WIND, 2)
        else repeat(2) { t.onBuilt(TileKind.POWER_WIND) }
        if (c.population >= 500) spread(TileKind.AVENUE, 6)
        else repeat(6) { t.onBuilt(TileKind.AVENUE) }
        if (c.population >= 800) spread(TileKind.BUS_STOP, 2)
        else repeat(2) { t.onBuilt(TileKind.BUS_STOP) }
        months(24)

        // 3章: くらし
        spread(TileKind.PARK, 8)
        spread(TileKind.FARM, 8)
        spread(TileKind.POLICE, 2)
        spread(TileKind.FIRE, 2)
        spread(TileKind.HOSPITAL, 2)
        spread(TileKind.SCHOOL, 2)
        months(48)

        // 4章: しちょうの しごと
        c.taxRate = 12
        t.onBudgetOpened()
        t.onInfoOpened()
        t.onOrdinanceEnabled()
        months(60)
        t.onContinuePressed()

        assertTrue("the tutorial did not finish", t.finished)

        // --- 中規模都市であること ---
        assertTrue("population is only ${c.population}", c.population >= 1_000)

        // --- 財政が健全であること ---
        assertTrue("the city is broke: ${c.funds}", c.funds > 0)
        assertFalse("the city went bankrupt", c.gameOver)

        // --- インフラが足りていること ---
        assertTrue("not enough power: ${c.powerSupply}/${c.powerDemand}", c.powerSupply >= c.powerDemand)
        assertTrue("not enough water: ${c.waterSupply}/${c.waterDemand}", c.waterSupply >= c.waterDemand)

        // --- そのあと自走できること ---
        repeat(36) { c.step() }
        assertTrue("the city collapsed after the tutorial", c.population >= 1_000)
        assertTrue(
            "the city runs a deficit: income=${c.lastIncome} upkeep=${c.lastUpkeep}",
            c.lastIncome > c.lastUpkeep,
        )
        assertTrue("funds ran dry", c.funds > 0)
    }

    /** 手引きが、ゲームの要素をひととおり扱っていること。 */
    @Test
    fun `the tutorial covers every part of the game`() {
        val placed = Tutorial.STEPS.mapNotNull { (it.goal as? Tutorial.Goal.Place)?.kind }.toSet()
        // 区分
        assertTrue(TileKind.ZONE_R in placed)
        assertTrue(TileKind.ZONE_C in placed)
        assertTrue(TileKind.ZONE_I in placed)
        // 交通
        assertTrue(TileKind.ROAD in placed)
        assertTrue(TileKind.AVENUE in placed)
        assertTrue(TileKind.BUS_STOP in placed)
        // 電力（火力と、公害のないもの）
        assertTrue(TileKind.POWER_COAL in placed)
        assertTrue(TileKind.POWER_WIND in placed)
        // 水とゴミ
        assertTrue(TileKind.WATER_TOWER in placed)
        assertTrue(TileKind.LANDFILL in placed)
        // サービス
        assertTrue(TileKind.POLICE in placed)
        assertTrue(TileKind.FIRE in placed)
        assertTrue(TileKind.HOSPITAL in placed)
        assertTrue(TileKind.SCHOOL in placed)
        // みどり
        assertTrue(TileKind.PARK in placed)
        assertTrue(TileKind.FARM in placed)

        // 画面まわり
        val goals = Tutorial.STEPS.map { it.goal }
        assertTrue("the budget screen is never shown", goals.any { it is Tutorial.Goal.OpenBudget })
        assertTrue("the info screen is never shown", goals.any { it is Tutorial.Goal.OpenInfo })
        assertTrue("ordinances are never tried", goals.any { it is Tutorial.Goal.EnableOrdinance })
    }

    /**
     * 手順の文に書いた数と、実際の目標の数が食い違わないこと。
     *
     * 文だけ直して目標を直し忘れると、「10マス」と書いてあるのに
     * 14マス求められる、という食い違いが起きる。
     */
    @Test
    fun `the numbers in the text match the goals`() {
        val digits = Regex("(\\d+)\\s*(マス|つ|こ|かげつ)")
        for (step in Tutorial.STEPS) {
            val stated = digits.findAll(step.body).map { it.groupValues[1].toInt() }.toList()
            if (stated.isEmpty()) continue
            val want = when (val g = step.goal) {
                is Tutorial.Goal.Place -> g.count
                is Tutorial.Goal.Advance -> g.months
                else -> continue
            }
            assertTrue(
                "「${step.title}」says ${stated} but asks for $want",
                want in stated,
            )
        }
    }

    /** 4つの章が、順に並んでいること。 */
    @Test
    fun `the steps are grouped into chapters in order`() {
        val chapters = Tutorial.STEPS.map { it.chapter }
        assertEquals("every chapter should be used", Tutorial.Chapter.entries.toSet(), chapters.toSet())
        // 章が行ったり来たりしない
        var last = -1
        for (ch in chapters) {
            assertTrue("chapters jump around: $ch after index $last", ch.ordinal >= last)
            last = ch.ordinal
        }
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
            is Tutorial.Goal.OpenInfo -> t.onInfoOpened()
            is Tutorial.Goal.EnableOrdinance -> t.onOrdinanceEnabled()
        }
    }
}
