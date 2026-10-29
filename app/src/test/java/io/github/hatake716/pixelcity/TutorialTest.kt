package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.data.SaveGame
import io.github.hatake716.pixelcity.game.*
import org.junit.Assert.*
import org.junit.Test

class TutorialTest {
    private fun city(size: Int = 32) = City(size, size).apply {
        tiles.forEach { it.terrain = Terrain.LAND }
        disasterLevel = City.DisasterLevel.NONE
    }
    private fun put(c: City, t: Tutorial, x: Int, y: Int, kind: TileKind) {
        assertNull("proposal cannot be built", TutorialDistrict.placementBlocker(c, x, y, kind, emptySet()))
        assertTrue("build failed: $kind ($x,$y): ${c.buildBlocker(x,y,kind)}", c.build(x, y, kind))
        c.recomputeDerivedState(); t.onCityChanged(c)
    }
    private fun finishStep(c: City, t: Tutorial, priority: Tutorial.Priority) {
        val before = t.stepIndex
        val goal = t.step!!.goal
        if (goal is Tutorial.Goal.ChoosePriority) { t.choose(priority, c); return }
        if (!t.briefingRead) t.acknowledge(c)
        if (!t.active || before != t.stepIndex) return
        when (goal) {
            is Tutorial.Goal.Place, Tutorial.Goal.KeepPromise -> {
                val kind = t.neededTool!!
                val spots = TutorialDistrict.suggestions(c, t)
                for (i in spots) {
                    if (t.stepIndex != before) break
                    put(c, t, i % c.width, i / c.width, kind)
                }
            }
            is Tutorial.Goal.Population, Tutorial.Goal.GrowingStreet, is Tutorial.Goal.StableBudget -> {
                repeat(24) { if (t.stepIndex == before) { c.step(); t.onMonthPassed(c) } }
            }
            Tutorial.Goal.InspectHome -> t.onHomeInspected(c, c.tiles.indexOfFirst { it.kind == TileKind.ZONE_R && it.stage > 0 })
            Tutorial.Goal.OpenInfo -> t.onInfoOpened(c)
            Tutorial.Goal.OpenBudget -> {
                c.refreshBudgetForecast()
                // Exercise a modest tax decision with room for changes in monthly demand.
                c.taxRate = if (priority == Tutorial.Priority.CLINIC) 9 else 8
                c.refreshBudgetForecast()
                t.onBudgetOpened(c)
            }
            else -> error("Unexpected goal $goal")
        }
        assertNotEquals("Stuck at ${t.step?.title}: pop=${c.population} jobs=${c.jobs} funds=${c.funds} income=${c.lastIncome} upkeep=${c.lastUpkeep} power=${c.powerSupply}/${c.powerDemand} advice=${t.advice(c)}", before, t.stepIndex)
    }
    private fun reach(c: City, t: Tutorial, id: Int, priority: Tutorial.Priority = Tutorial.Priority.PARKS) {
        while (t.active && t.step!!.id != id) finishStep(c, t, priority)
        assertEquals(id, t.step?.id)
    }

    @Test fun bothPromisesReachARealSelfSupportingTownWithoutGrantsOrUnlockBypasses() {
        for (size in listOf(32, 128)) for (choice in Tutorial.Priority.entries) {
            val c = city(size); val t = Tutorial().apply { start() }
            while (t.active) finishStep(c, t, choice)
            assertTrue(t.finished)
            assertEquals(choice, t.priority)
            assertTrue("too long: ${c.month} months", c.month <= 18)
            assertTrue(c.funds > 0 && c.population >= 120 && c.lastIncome > c.lastUpkeep)
            assertTrue(c.tiles.count { it.kind == choice.kind } >= choice.count)
            println("STORY $choice size=$size: month=${c.month}, population=${c.population}, funds=${c.funds}, balance=${c.lastIncome-c.lastUpkeep}")
            repeat(24) { c.step() }
            assertFalse(c.gameOver)
            assertTrue("town collapsed: ${c.population}, ${c.funds}", c.population >= 120 && c.funds > 0)
            assertTrue("town needs subsidy", c.lastIncome > c.lastUpkeep)
        }
    }
    @Test fun bothPromisesCanCompleteOnNaturalEarthWithoutClearingARectangle() {
        for(choice in Tutorial.Priority.entries) {
            val save=SaveGame.expandToPlanet(SaveGame.Loaded(City(),Tutorial().apply { start() },1L))
            val c=save.city;val t=save.tutorial;c.disasterLevel=City.DisasterLevel.NONE
            while(t.active) finishStep(c,t,choice)
            assertTrue(t.finished)
            assertTrue(c.population>=120 && c.funds>0 && c.lastIncome>c.lastUpkeep)
            assertEquals(PlanetTerrain.SEA,c.tileAt(256,256).biome)
        }
    }

    @Test fun calendarTimeAloneCannotWelcomeResidents() {
        val c = city(); val t = Tutorial().apply { start() }
        reach(c, t, 4)
        t.acknowledge(c)
        val plant = c.tiles.indexOfFirst { it.kind == TileKind.POWER_COAL }
        c.bulldoze(plant%c.width,plant/c.width); c.recomputeDerivedState()
        repeat(12) { c.step(); t.onMonthPassed(c) }
        assertEquals(4,t.step?.id)
        assertEquals(0,c.population)
        assertTrue(t.advice(c).contains("電気") || t.advice(c).contains("道"))
        put(c,t,plant%c.width,plant/c.width,TileKind.POWER_COAL)
        repeat(6) { if (t.step?.id == 4) { c.step();t.onMonthPassed(c) } }
        assertEquals(5,t.step?.id)
    }
    @Test fun briefingRequiresAnExplicitReplyAndDoesNotConsumeTimeOrFunds() {
        val c=city();val t=Tutorial().apply { start() }
        assertFalse(t.allowsBuild(TileKind.ROAD));assertFalse(t.allowsSpeedChange())
        repeat(6) { t.onMonthPassed(c);t.onCityChanged(c) }
        assertEquals(0,t.stepIndex)
        t.acknowledge(c)
        assertFalse(t.briefingRead)
        t.acknowledge(c)
        assertTrue(t.allowsBuild(TileKind.ROAD))
        assertFalse(t.allowsBuild(TileKind.HOSPITAL))
        assertEquals(City.STARTING_FUNDS,c.funds);assertEquals(0,c.month)
        assertTrue(t.allowsBudget());assertTrue(t.allowsInfo())
    }
    @Test fun demolishedOrUnpoweredPlotsDoNotCountAsWorkingHomes() {
        val c=city();val t=Tutorial().apply { start() };reach(c,t,3);t.acknowledge(c)
        val spots=TutorialDistrict.suggestions(c,t)
        put(c,t,spots[0]%c.width,spots[0]/c.width,TileKind.ZONE_R)
        assertEquals(1,t.progress)
        c.bulldoze(spots[0]%c.width,spots[0]/c.width);c.recomputeDerivedState();t.onCityChanged(c)
        assertEquals(0,t.progress)
        val far=c.index(2,2);assertTrue(c.build(2,2,TileKind.ZONE_R));c.recomputeDerivedState();t.onCityChanged(c)
        assertFalse(c.tiles[far].powered);assertEquals(0,t.progress)
    }
    @Test fun disconnectedRoadFragmentsCannotCompleteTheFirstStreet() {
        val c=city();val t=Tutorial().apply { start() };t.acknowledge(c);t.acknowledge(c)
        for(x in 0..22 step 2) assertTrue(c.build(x,2,TileKind.ROAD))
        c.recomputeDerivedState();t.onCityChanged(c)
        assertEquals(1,t.progress);assertEquals(1,t.stepIndex)
        assertNotNull(TutorialDistrict.placementBlocker(c,20,20,TileKind.ROAD,emptySet()))
        assertNull(TutorialDistrict.placementBlocker(c,1,2,TileKind.ROAD,emptySet()))
    }
    @Test fun inspectionNeedsAnOccupiedHomeAndIsSavedAtTheNextBriefing() {
        val c=city();val t=Tutorial().apply { start() };reach(c,t,6);t.acknowledge(c)
        t.onHomeInspected(c,c.index(0,0));assertEquals(6,t.stepIndex)
        val home=c.tiles.indexOfFirst { it.kind==TileKind.ZONE_R && it.stage>0 }
        t.onHomeInspected(c,home);assertEquals(7,t.stepIndex)
        val loaded=SaveGame.parse(SaveGame.encode(c,t,23))
        assertEquals(7,loaded.tutorial.stepIndex);assertFalse(loaded.tutorial.briefingRead)
    }
    @Test fun decisionAndConsecutiveProfitSurviveARealSave() {
        val c=city();val t=Tutorial().apply { start() };reach(c,t,16,Tutorial.Priority.CLINIC);t.acknowledge(c)
        c.step();t.onMonthPassed(c)
        assertEquals(1,t.progress)
        val loaded=SaveGame.parse(SaveGame.encode(c,t,23))
        assertEquals(Tutorial.Priority.CLINIC,loaded.tutorial.priority)
        assertEquals(1,loaded.tutorial.progress)
        loaded.tutorial.onMonthPassed(loaded.city)
        assertEquals("same month's profit counted twice",1,loaded.tutorial.progress)
        repeat(2) { loaded.city.step();loaded.tutorial.onMonthPassed(loaded.city) }
        assertEquals(17,loaded.tutorial.stepIndex)
    }
    @Test fun serviceFailureOrDeficitBreaksTheProfitStreak() {
        val c=city();val t=Tutorial().apply { start() };reach(c,t,16);t.acknowledge(c)
        c.step();t.onMonthPassed(c);assertEquals(1,t.progress)
        c.lastIncome=0;c.month++;t.onMonthPassed(c);assertEquals(0,t.progress)
        c.step();t.onMonthPassed(c);assertEquals(1,t.progress)
        val water=c.tiles.indexOfFirst { it.kind==TileKind.WATER_TOWER }
        c.bulldoze(water%c.width,water/c.width);c.recomputeDerivedState();t.onCityChanged(c)
        assertEquals(0,t.progress)
        assertTrue(t.advice(c).contains("給水塔"))
    }
    @Test fun choiceChangesCostBuildingsAndTheClosingLetter() {
        val results=Tutorial.Priority.entries.map { choice ->
            val c=city();val t=Tutorial().apply { start() };reach(c,t,11)
            val money=c.funds;t.choose(choice,c);finishStep(c,t,choice)
            assertEquals(BuildCost.cost(choice.kind)*choice.count,money-c.funds)
            reach(c,t,17,choice)
            t.briefing(c)
        }
        assertTrue(results[0].contains("広場"));assertTrue(results[1].contains("診療所"))
    }
    @Test fun legacyCourseRestartsOnlyTheJournalAndPreservesAnExistingCity() {
        val c=city();assertTrue(c.build(4,4,TileKind.ROAD));c.funds=12345;c.month=100
        val t=Tutorial().apply { restore(intArrayOf(1,23,17,0)) }
        val loaded=SaveGame.parse(SaveGame.encode(c,t,99))
        assertTrue(loaded.tutorial.inheritedCity);assertEquals(0,loaded.tutorial.stepIndex)
        assertEquals(12345,loaded.city.funds);assertEquals(100,loaded.city.month)
        assertEquals(TileKind.ROAD,loaded.city.tileAt(4,4).kind)
        assertTrue(loaded.tutorial.briefing(loaded.city).contains("そのまま"))
    }
    @Test fun optionalInformationNeverSkipsAnUnreadStoryBeat() {
        val c=city();val t=Tutorial().apply { start() };reach(c,t,14)
        t.onInfoOpened(c);assertEquals(14,t.stepIndex)
        t.acknowledge(c);t.onInfoOpened(c);assertEquals(15,t.stepIndex)
        t.onBudgetOpened(c);assertEquals(15,t.stepIndex)
        t.acknowledge(c);t.onBudgetOpened(c);assertEquals(16,t.stepIndex)
    }
    @Test fun skipAndOldFinishedSavesStayPlayable() {
        val t=Tutorial().apply { start();skip() }
        assertFalse(t.active);assertTrue(t.allowsBuild(TileKind.SCHOOL));assertTrue(t.allowsSpeedChange())
        t.restore(intArrayOf(0,26,0,1));assertTrue(t.finished);assertFalse(t.active)
    }
    @Test fun suggestionsFollowAStreetBuiltAwayFromTheDefaultPlan() {
        val c=city();val t=Tutorial().apply { start() };t.acknowledge(c);t.acknowledge(c)
        assertTrue(c.build(2,3,TileKind.ROAD));c.recomputeDerivedState();t.onCityChanged(c)
        assertTrue(TutorialDistrict.suggestions(c,t).all { c.touchesRoad(it%c.width,it/c.width) })
        for(x in 3..13) assertTrue(c.build(x,3,TileKind.ROAD))
        c.recomputeDerivedState();t.onCityChanged(c)
        assertEquals(2,t.step?.id);t.acknowledge(c)
        val suggestions=TutorialDistrict.suggestions(c,t)
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.all { c.touchesRoad(it%c.width,it/c.width) })
    }

}
