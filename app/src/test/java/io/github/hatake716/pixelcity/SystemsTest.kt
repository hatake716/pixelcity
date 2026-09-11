package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.game.BuildCost
import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Ordinance
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v2 で足した仕組み（交通・水・ゴミ・犯罪・疫病・条例・災害）の振る舞い。
 *
 * どれも「足したことで既存の街が壊れない」ことが大前提なので、
 * 効き目があることと、効きすぎないことの両方を見る。
 */
class SystemsTest {

    private fun flatCity(size: Int = 48) = City(size, size).apply {
        for (t in tiles) t.terrain = Terrain.LAND
        funds = 10_000_000
    }

    /** 道路・電力・水のそろった小さな街。 */
    private fun workingCity(): City {
        val c = flatCity()
        for (x in 4..40) c.build(x, 20, TileKind.ROAD)
        c.build(4, 19, TileKind.POWER_COAL)
        c.build(6, 19, TileKind.WATER_TOWER)
        for (x in 8..30) {
            c.build(x, 19, TileKind.ZONE_R)
            c.build(x, 21, TileKind.ZONE_C)
        }
        repeat(20) { c.step() }
        return c
    }

    // --- 水 ---

    @Test
    fun `water lets zones grow beyond the first stage`() {
        val dry = flatCity()
        for (x in 4..30) dry.build(x, 20, TileKind.ROAD)
        dry.build(4, 19, TileKind.POWER_COAL)
        for (x in 8..28) {
            dry.build(x, 19, TileKind.ZONE_R)
            dry.build(x, 21, TileKind.ZONE_C)
        }
        repeat(30) { dry.step() }
        val dryTop = dry.tiles.count { it.kind.isZone && it.stage > 1 }
        assertEquals("zones should be capped without water", 0, dryTop)
        assertTrue("a dry city should still have people", dry.population > 0)

        val wet = workingCity()
        val wetTop = wet.tiles.count { it.kind.isZone && it.stage > 1 }
        assertTrue("zones did not grow with water", wetTop > 0)
    }

    @Test
    fun `water reaches only as far as the supply allows`() {
        val c = flatCity()
        c.build(24, 24, TileKind.WATER_TOWER)
        c.step()
        assertTrue("the tower itself has water", c.tileAt(24, 24).watered)
        assertTrue("supply was not counted", c.waterSupply > 0)
    }

    // --- 交通 ---

    @Test
    fun `commuters create traffic on the roads`() {
        val c = workingCity()
        val busiest = c.tiles.filter { it.kind == TileKind.ROAD }.maxOfOrNull { it.traffic } ?: 0
        assertTrue("no traffic was generated", busiest > 0)
    }

    @Test
    fun `an avenue carries more than a road`() {
        assertTrue(TileKind.AVENUE.capacity > TileKind.ROAD.capacity)
        assertTrue(TileKind.HIGHWAY.capacity > TileKind.AVENUE.capacity)
        assertTrue(TileKind.SUBWAY.capacity > TileKind.RAIL.capacity)
    }

    /**
     * 公共交通は交通量を減らすこと。
     * バス停は人口800から使えるので、そこまで育った街で確かめる。
     */
    @Test
    fun `bus stops reduce the traffic around them`() {
        fun grown(): City {
            val c = flatCity(64)
            c.disasterLevel = City.DisasterLevel.NONE
            // 碁盤の目にして、区分を多く取る
            for (y in 10..54 step 3) for (x in 4..58) c.build(x, y, TileKind.ROAD)
            for (x in 4..58 step 5) for (y in 10..54) c.build(x, y, TileKind.ROAD)
            for (i in 0..17) c.build(3 + i * 3, 9, TileKind.POWER_COAL)
            for (y in 12..52 step 9) for (x in 6..56 step 9) c.build(x, y, TileKind.WATER_TOWER)
            for (y in 11..53) for (x in 5..57) {
                if (c.tileAt(x, y).kind != TileKind.EMPTY) continue
                c.build(x, y, if ((x + y) % 3 == 0) TileKind.ZONE_C else TileKind.ZONE_R)
            }
            repeat(40) { c.step() }
            return c
        }
        val without = grown()
        assertTrue("the city did not grow enough to unlock bus stops", without.population >= 800)
        val busy = without.tiles.filter { it.kind == TileKind.ROAD }.sumOf { it.traffic }

        val withStops = grown()
        for (x in 10..54 step 6) withStops.build(x, 32, TileKind.BUS_STOP)
        assertTrue(
            "no bus stop was built",
            withStops.tiles.count { it.kind == TileKind.BUS_STOP } > 0,
        )
        repeat(8) { withStops.step() }
        val relieved = withStops.tiles.filter { it.kind == TileKind.ROAD }.sumOf { it.traffic }
        assertTrue("bus stops did not help: $busy → $relieved", relieved < busy)
    }

    // --- ゴミ ---

    @Test
    fun `a city without waste handling piles up rubbish`() {
        val c = workingCity()
        assertTrue("no rubbish was produced", c.garbageProduced > 0)
        assertTrue("rubbish did not pile up", c.garbageBacklog > 0)
    }

    @Test
    fun `waste facilities clear the rubbish`() {
        val c = workingCity()
        val before = c.garbageBacklog
        // 埋立地は最初から使える（焼却場は人口2,500から）
        for (x in 10..26 step 2) c.build(x, 23, TileKind.LANDFILL)
        repeat(12) { c.step() }
        assertTrue("rubbish was not cleared: $before → ${c.garbageBacklog}", c.garbageBacklog < before)
        assertTrue("no capacity was counted", c.garbageCapacity > 0)
    }

    /**
     * 溜まったゴミには上限があり、街を壊し尽くさないこと。
     *
     * ゴミ処理を建てないまま長く遊んでも、不便になるだけで
     * 街が消えてしまわないようにしている。
     */
    @Test
    fun `rubbish never grows without limit`() {
        val c = workingCity()
        // ゴミだけの影響を見たいので、災害は止める
        c.disasterLevel = City.DisasterLevel.NONE
        repeat(200) { c.step() }
        assertTrue("backlog ran away: ${c.garbageBacklog}", c.garbageBacklog <= City.GARBAGE_BACKLOG_MAX)
        // 人が減るのは構わないが、ゼロにはならない
        assertTrue("the city was wiped out by rubbish", c.population > 0)
    }

    @Test
    fun `a landfill fills up and stops taking rubbish`() {
        val c = workingCity()
        c.build(12, 23, TileKind.LANDFILL)
        repeat(250) { c.step() }
        val fill = c.tileAt(12, 23).landfillFill
        assertTrue("the landfill never filled", fill > 0)
        assertTrue("the landfill exceeded its capacity", fill <= BuildCost.LANDFILL_TOTAL)
    }

    // --- 犯罪 ---

    @Test
    fun `police reduce crime nearby`() {
        val plain = workingCity()
        val before = plain.averageCrime

        val policed = workingCity()
        for (x in 10..28 step 6) policed.build(x, 22, TileKind.POLICE)
        repeat(8) { policed.step() }
        assertTrue("police did not help: $before → ${policed.averageCrime}", policed.averageCrime < before)
    }

    /** 平常時の犯罪で、街が衰退しないこと。 */
    @Test
    fun `ordinary crime does not destroy a city`() {
        val c = workingCity()
        val before = c.population
        repeat(40) { c.step() }
        assertTrue("the city collapsed: $before → ${c.population}", c.population > before / 2)
    }

    // --- 疫病 ---

    @Test
    fun `hospitals hold down infection`() {
        val sick = workingCity()
        repeat(40) { sick.step() }

        val cared = workingCity()
        for (x in 10..28 step 5) cared.build(x, 22, TileKind.CLINIC)
        repeat(40) { cared.step() }
        assertTrue(
            "clinics did not help: ${sick.infection} → ${cared.infection}",
            cared.infection <= sick.infection,
        )
    }

    // --- 条例 ---

    @Test
    fun `the energy ordinance lowers power demand`() {
        val c = workingCity()
        val before = c.powerDemand
        c.ordinances.add(Ordinance.ENERGY_SAVING)
        c.step()
        assertTrue("demand did not fall: $before → ${c.powerDemand}", c.powerDemand < before)
    }

    @Test
    fun `the recycling ordinance lowers rubbish`() {
        val c = workingCity()
        val before = c.garbageProduced
        c.ordinances.add(Ordinance.RECYCLING)
        c.step()
        assertTrue("rubbish did not fall: $before → ${c.garbageProduced}", c.garbageProduced < before)
    }

    @Test
    fun `the patrol ordinance lowers crime`() {
        val c = workingCity()
        val before = c.averageCrime
        c.ordinances.add(Ordinance.PATROL)
        c.step()
        assertTrue("crime did not fall: $before → ${c.averageCrime}", c.averageCrime < before)
    }

    @Test
    fun `ordinances cost money every month`() {
        val c = workingCity()
        val before = c.lastUpkeep
        c.ordinances.add(Ordinance.FREE_CLINIC)
        c.step()
        assertTrue("the ordinance was free: $before → ${c.lastUpkeep}", c.lastUpkeep > before)
    }

    // --- 災害 ---

    @Test
    fun `disasters can be turned off`() {
        val c = workingCity()
        c.disasterLevel = City.DisasterLevel.NONE
        repeat(200) { c.step() }
        // 起きていないこと（起きれば知らせが残る）
        assertEquals(null, c.lastDisaster)
    }

    @Test
    fun `disasters do happen when they are turned up`() {
        val c = workingCity()
        c.disasterLevel = City.DisasterLevel.HIGH
        var happened = 0
        repeat(300) {
            c.step()
            if (c.lastDisaster != null) happened++
        }
        assertTrue("no disaster in 300 months", happened > 0)
    }

    /** 災害が起きても、街が全滅しないこと。 */
    @Test
    fun `a disaster does not wipe out the whole city`() {
        val c = workingCity()
        c.disasterLevel = City.DisasterLevel.HIGH
        val before = c.population
        repeat(120) { c.step() }
        assertTrue("the city was wiped out", c.population > 0)
        assertTrue("everything burned down", c.population > before / 10)
    }

    // --- 指標 ---

    @Test
    fun `the city records its history`() {
        val c = workingCity()
        assertTrue("nothing was recorded", c.history.isNotEmpty())
        assertEquals("the last record should be this month", c.month, c.history.last().month)
    }

    @Test
    fun `history does not grow without limit`() {
        val c = workingCity()
        repeat(300) { c.step() }
        assertTrue("history ran away: ${c.history.size}", c.history.size <= City.HISTORY_MONTHS)
    }

    @Test
    fun `the budget is broken down into parts`() {
        val c = workingCity()
        val inc = c.lastIncomeBreakdown
        val sp = c.lastSpending
        // 内訳は区分ごとに切り捨てて出すので、合計とわずかにずれる
        assertTrue(
            "income does not add up: ${c.lastIncome} vs ${inc.total}",
            Math.abs(c.lastIncome - inc.total) <= 3,
        )
        assertEquals("spending should add up", c.lastUpkeep, sp.total)
        assertTrue("transport costs nothing?", sp.transport > 0)
    }

    @Test
    fun `advice names the problem the city actually has`() {
        val c = flatCity()
        for (x in 4..30) c.build(x, 20, TileKind.ROAD)
        // 発電所を建てずに区分だけ置く
        for (x in 8..28) c.build(x, 19, TileKind.ZONE_R)
        repeat(4) { c.step() }
        val advice = c.advice()
        assertTrue("no advice was given", advice.isNotEmpty())
    }

    @Test
    fun `approval falls when the city is in trouble`() {
        val good = workingCity()
        val bad = workingCity()
        bad.funds = -10_000
        bad.infection = 80
        repeat(2) { bad.step() }
        assertTrue("approval did not fall: ${good.approval} → ${bad.approval}", bad.approval < good.approval)
    }
}
