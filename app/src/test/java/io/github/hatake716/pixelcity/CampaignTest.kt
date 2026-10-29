package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.data.SaveGame
import io.github.hatake716.pixelcity.game.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class CampaignTest {
    /** Endgame fixture; only this setup seeds mature tiles. Every later month uses City.step. */
    private fun matureCity(route: Campaign.Route): City {
        val city=City(128,128)
        val cols=if(route==Campaign.Route.GARDEN)3 else 6
        val rows=if(route==Campaign.Route.GARDEN)3 else 5
        val services=mapOf(
            (1 to 1) to TileKind.POWER_SOLAR,(2 to 1) to TileKind.POWER_SOLAR,(4 to 1) to TileKind.POWER_SOLAR,
            (5 to 5) to TileKind.WATER_PLANT,(7 to 5) to TileKind.FIRE,(8 to 5) to TileKind.POLICE,
            (4 to 5) to TileKind.SCHOOL,(10 to 5) to TileKind.HOSPITAL,
            (1 to 10) to TileKind.RECYCLING,(2 to 10) to TileKind.RECYCLING,(4 to 10) to TileKind.RECYCLING,(5 to 10) to TileKind.RECYCLING,
            (8 to 10) to TileKind.SEWAGE_PLANT,(10 to 2) to TileKind.BUS_STOP,(10 to 8) to TileKind.BUS_STOP)
        for(by in 0 until rows)for(bx in 0 until cols) {
            var n=0
            for(y in 0..11)for(x in 0..11) {
                val tile=city.tileAt(8+bx*12+x,8+by*12+y)
                tile.kind=when {
                    x==0 || y%3==0 -> TileKind.AVENUE
                    (x to y) in services -> services.getValue(x to y)
                    route==Campaign.Route.GARDEN -> when(n++%7) {0,1->TileKind.FARM;2,3->TileKind.PARK;4,5->TileKind.ZONE_R;else->if((x+y)%2==0)TileKind.ZONE_C else TileKind.ZONE_I}
                    route==Campaign.Route.INDUSTRIAL && bx>=cols-2 -> if(n++%3==0)TileKind.PARK else TileKind.ZONE_I
                    bx==cols-1 -> if(n++%5==0)TileKind.PARK else TileKind.ZONE_I
                    else -> when(n++%6) {0->TileKind.PARK;1->TileKind.ZONE_C;else->TileKind.ZONE_R}
                }
                tile.stage=if(tile.kind.isZone)3 else 0
            }
        }
        for(x in 8..8+cols*12)city.tileAt(x,8+rows*12).kind=TileKind.AVENUE
        for(y in 8..8+rows*12)city.tileAt(8+cols*12,y).kind=TileKind.AVENUE
        if(route==Campaign.Route.GARDEN) {
            city.tiles.filter { it.kind==TileKind.AVENUE }.forEach { it.kind=TileKind.ROAD }
            for(by in 0 until rows)for(bx in 0 until cols) {
                for((x,y) in listOf(4 to 1,4 to 10,5 to 10)) city.tileAt(8+bx*12+x,8+by*12+y).kind=TileKind.PARK
            }
        }
        city.funds=250_000;city.taxRate=9;city.disasterLevel=City.DisasterLevel.NONE
        city.recomputeDerivedState();city.refreshBudgetForecast()
        city.campaign.offer();assertTrue(city.campaign.select(route,city))
        return city
    }
    private fun describe(city: City)= "${city.campaign.route} chapter=${city.campaign.chapter} month=${city.month} pop=${city.population} funds=${city.funds} balance=${city.lastIncome-city.lastUpkeep}: ${city.campaign.objectives(city).filter { !it.met }}"

    @Test fun allRoutesAndBothPolicyPathsCompleteWithRealCityConditionsAndKeepRunning() {
        for(route in Campaign.Route.entries)for(choice in 0..1) {
            val city=matureCity(route)
            repeat(120) {
                if(!city.campaign.completed) {
                    if(!city.campaign.chosen)assertTrue(describe(city),city.campaign.choose(choice,city))
                    city.campaign.acknowledge();city.step()
                }
            }
            if(!city.campaign.completed) {
                println("END metrics=${city.campaign.metrics(city)} demand=${city.demandR}/${city.demandC}/${city.demandI} income=${city.lastIncomeBreakdown} upkeep=${city.lastSpending}")
                city.tiles.withIndex().filter { it.value.kind==TileKind.ZONE_R }.take(4).forEach { println("HOME ${it.value.stage} p=${it.value.pollution} land=${it.value.landValue} status=${city.growthStatus(it.index%city.width,it.index/city.width)}") }
            }
            assertTrue(describe(city),city.campaign.completed)
            assertTrue(city.campaign.objectives(city).all { it.met })
            if(choice==0) System.getProperty("pixelcity.preview", "").orEmpty().takeIf { it.isNotBlank() }?.let { destination ->
                val tutorial=Tutorial().apply { restore(intArrayOf(0,18,0,1,2,-1,1,-1,0)) }
                val file=java.io.File(destination,"${route.name.lowercase()}-completed.json")
                file.parentFile?.mkdirs();file.writeText(SaveGame.encode(city,tutorial,20260912))
            }
            val loaded=SaveGame.parse(SaveGame.encode(city,Tutorial(),42)).city
            assertTrue(loaded.campaign.completed);assertEquals(city.campaign.completedPopulation,loaded.campaign.completedPopulation)
            val before=loaded.campaign.completedMonth
            repeat(12){ loaded.step() }
            assertFalse(describe(loaded),loaded.gameOver)
            assertTrue(describe(loaded),loaded.funds>0 && loaded.population>=route.population)
            assertEquals(before,loaded.campaign.completedMonth)
            println("CAMPAIGN $route choice=$choice completed month=${city.month} pop=${city.population} balance=${city.lastIncome-city.lastUpkeep}; laterPop=${loaded.population}")
        }
    }
    @Test fun routeChoicePreservesCityAndCannotBeReplaced() {
        val city=City(32,32);city.build(3,3,TileKind.ROAD);city.month=6
        val before=city.tiles.map { it.kind };val funds=city.funds
        assertFalse(city.campaign.select(Campaign.Route.GARDEN,city))
        city.campaign.offer();assertTrue(city.campaign.select(Campaign.Route.GARDEN,city))
        assertEquals(before,city.tiles.map { it.kind });assertEquals(funds,city.funds);assertEquals(6,city.month)
        assertFalse(city.campaign.select(Campaign.Route.METROPOLIS,city))
        city.campaign.acknowledge();city.campaign.offer();assertFalse(city.campaign.needsBriefing)
    }
    @Test fun policiesChargeOnceHaveRecurringCostsAndCannotSpendMissingFunds() {
        val city=City(32,32);city.campaign.offer();city.campaign.select(Campaign.Route.METROPOLIS,city)
        city.funds=299;assertFalse(city.campaign.choose(0,city));assertEquals(299,city.funds)
        city.funds=2000;assertTrue(city.campaign.choose(0,city));assertEquals(1700,city.funds)
        assertFalse(city.campaign.choose(1,city));city.refreshBudgetForecast();city.refreshBudgetForecast()
        assertEquals(1700,city.funds);assertEquals(8,city.lastSpending.ordinances)
        city.campaign.acknowledge();city.step();assertEquals(1692,city.funds)
    }
    @Test fun emptyCityAndPopulationNumberAloneCannotWin() {
        for(r in Campaign.Route.entries) {
            val city=City(32,32);city.campaign.offer();city.campaign.select(r,city);city.campaign.choose(0,city);city.campaign.acknowledge()
            city.population=100000
            repeat(30){city.month++;city.campaign.onMonthPassed(city)}
            assertEquals(0,city.campaign.chapter);assertFalse(city.campaign.completed)
        }
    }
    @Test fun readingsChoicesTrustAndWeatherReportSurviveRealSerialization() {
        val city=matureCity(Campaign.Route.GARDEN);city.campaign.choose(1,city);city.campaign.acknowledge();city.step()
        val state=city.campaign.saveState();val raw=SaveGame.encode(city,Tutorial(),789)
        val loaded=SaveGame.parse(raw).city
        assertArrayEquals(state,loaded.campaign.saveState());assertEquals(city.campaign.lastEvent,loaded.campaign.lastEvent)
        loaded.campaign.onMonthPassed(loaded)
        assertArrayEquals("reloading must not count a month twice",state,loaded.campaign.saveState())
    }
    @Test fun deficitAndServiceFailureResetFinalStreak() {
        val city=matureCity(Campaign.Route.METROPOLIS)
        repeat(90) {
            if(city.campaign.chapter<4 || city.campaign.stableMonths<2) {
                if(!city.campaign.chosen)city.campaign.choose(0,city)
                city.campaign.acknowledge();city.step()
            }
        }
        assertEquals(describe(city),2,city.campaign.stableMonths)
        city.lastIncome=0;city.month++;city.campaign.onMonthPassed(city);assertEquals(0,city.campaign.stableMonths)
        city.step();assertEquals(1,city.campaign.stableMonths)
        city.tiles.filter { it.kind==TileKind.WATER_PLANT }.forEach { it.clearForBulldoze() }
        city.recomputeDerivedState();city.month++;city.campaign.onMonthPassed(city)
        assertEquals(0,city.campaign.stableMonths);assertFalse(city.campaign.completed)
    }
    @Test fun unconnectedFacilitiesAndVacantMapDoNotDiluteResidentialProblems() {
        val city=matureCity(Campaign.Route.METROPOLIS)
        val before=city.campaign.metrics(city)
        for(x in 100..120) { city.tileAt(x,120).kind=TileKind.PARK }
        city.recomputeDerivedState();val after=city.campaign.metrics(city)
        assertEquals(before.pollution,after.pollution)
        assertEquals(before.count(TileKind.PARK),after.count(TileKind.PARK))
        assertTrue(after.green<=before.green)
    }
    private class AlwaysEvent : Random() { override fun nextBits(bitCount: Int) = 0 }
    @Test fun ruralPreparednessReducesRealStormDamageAndCostsButNeverMakesWeatherImpossible() {
        val bare=matureCity(Campaign.Route.GARDEN);val prepared=SaveGame.parse(SaveGame.encode(bare,Tutorial(),1)).city
        val state=prepared.campaign.saveState().apply { this[3]=2;this[14]=0;this[15]=0 }
        prepared.campaign.restore(state);assertTrue(prepared.campaign.choose(0,prepared));prepared.campaign.acknowledge()
        bare.month=12;prepared.month=12
        val countA=bare.tiles.sumOf { it.stage };val countB=prepared.tiles.sumOf { it.stage }
        val fundsA=bare.funds;val fundsB=prepared.funds
        val upkeepA=bare.lastUpkeep
        bare.campaign.weather(bare,AlwaysEvent());prepared.campaign.weather(prepared,AlwaysEvent())
        val damageA=countA-bare.tiles.sumOf { it.stage };val damageB=countB-prepared.tiles.sumOf { it.stage }
        assertTrue(damageA>damageB);assertTrue(damageB>0)
        assertTrue(fundsA-bare.funds>fundsB-prepared.funds)
        assertEquals(fundsA-bare.funds,bare.lastUpkeep-upkeepA)
        assertEquals(bare.lastUpkeep,bare.lastSpending.total)
        assertTrue(prepared.campaign.lastEvent.contains("竜巻"))
        val restored=SaveGame.parse(SaveGame.encode(prepared,Tutorial(),1)).city
        assertEquals(prepared.campaign.lastEvent,restored.campaign.lastEvent)
    }
    @Test fun industryContractNeedsRealEmploymentAndWorkingInfrastructure() {
        val city=matureCity(Campaign.Route.INDUSTRIAL);city.campaign.choose(0,city)
        assertTrue(city.campaign.contractIncome(city)>0)
        val contracted=city.campaign.contractIncome(city);city.refreshBudgetForecast()
        assertTrue(city.lastIncomeBreakdown.industrial>=contracted)
        city.tiles.filter { it.kind.isPowerPlant }.forEach { it.clearForBulldoze() }
        city.recomputeDerivedState();assertEquals(0,city.campaign.contractIncome(city))
        assertTrue(city.campaign.industrialOrders>0)
    }
    @Test fun olderCityLoadsWithoutCreatingOrDestroyingACampaign() {
        val city=City(32,32);city.build(1,1,TileKind.ROAD)
        val json=JSONObject(SaveGame.encode(city,Tutorial(),1)).apply { put("version",4);remove("campaign");remove("campaignEvent") }
        val loaded=SaveGame.parse(json.toString()).city
        assertFalse(loaded.campaign.offered);assertEquals(city.funds,loaded.funds);assertEquals(TileKind.ROAD,loaded.tileAt(1,1).kind)
    }
    @Test(expected=IllegalArgumentException::class) fun futurePoliciesAreRejectedInsteadOfActivatingFreeEffects() {
        val city=City();val state=city.campaign.saveState();state[1]=1;state[2]=1;state[18]=0
        city.campaign.restore(state)
    }
    @Test(expected=IllegalArgumentException::class) fun currentSavesCannotSilentlyLoseTheirPolicyCosts() {
        val raw=JSONObject(SaveGame.encode(City(),Tutorial(),1)).apply { remove("campaign") }
        SaveGame.parse(raw.toString())
    }
}
