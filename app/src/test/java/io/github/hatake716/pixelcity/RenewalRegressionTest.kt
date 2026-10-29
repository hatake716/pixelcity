package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.data.SaveGame
import io.github.hatake716.pixelcity.game.*
import io.github.hatake716.pixelcity.ui.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RenewalRegressionTest {
    private fun strip(kind: TileKind): City = City(64, 8).apply {
        tileAt(0,3).kind=TileKind.POWER_COAL
        for(x in 1..52)tileAt(x,3).kind=kind
        for(x in 1..50)tileAt(x,2).apply { this.kind=TileKind.ZONE_R;stage=3 }
        recomputeDerivedState()
    }
    @Test fun avenuesSupplyRoadAccessAndHighwaysNeedLocalConnections() {
        val avenue=strip(TileKind.AVENUE)
        assertTrue(avenue.tiles.any { it.kind==TileKind.ZONE_R && it.connected && it.powered })
        assertTrue(avenue.touchesRoad(20,2))
        val highway=strip(TileKind.HIGHWAY)
        assertTrue(highway.tileAt(20,3).connected)
        assertFalse(highway.tileAt(20,2).connected)
        assertFalse(highway.touchesRoad(20,2))
        highway.tileAt(20,3).kind=TileKind.ROAD
        highway.recomputeDerivedState()
        assertTrue(highway.tileAt(20,2).connected)
    }
    @Test fun energySavingActuallyRestoresPowerToBrownedOutHomes() {
        val city=strip(TileKind.ROAD)
        val before=city.tiles.count { it.kind.isZone && it.powered }
        assertEquals(2200,city.powerDemand)
        city.ordinances.add(Ordinance.ENERGY_SAVING)
        city.recomputeDerivedState()
        assertTrue(city.powerDemand<=city.powerSupply)
        assertTrue(city.tiles.count { it.kind.isZone && it.powered }>before)
        assertTrue(city.tiles.filter { it.kind.isZone }.all { it.powered })
    }
    @Test fun remotePlantCapacityCannotBeBorrowedWithoutAConnection() {
        val city=strip(TileKind.ROAD)
        val before=city.tiles.count { it.kind.isZone && it.powered }
        city.tileAt(61,6).kind=TileKind.POWER_COAL
        city.recomputeDerivedState()
        assertEquals(before,city.tiles.count { it.kind.isZone && it.powered })
        for(x in 52..61)city.tileAt(x,3).kind=TileKind.POWER_LINE
        for(y in 3..5)city.tileAt(61,y).kind=TileKind.POWER_LINE
        city.recomputeDerivedState()
        assertTrue(city.tiles.filter { it.kind.isZone }.all { it.powered })
    }
    @Test fun loadingRestoresDerivedServicesAndResumesTheSameSimulation() {
        val city=ShowcaseCity.build(ShowcaseCity.Kind.HARBOUR)
        city.disasterLevel=City.DisasterLevel.NONE
        repeat(8){city.step()}
        val tutorial=Tutorial().apply { skip() }
        val raw=SaveGame.encode(city,tutorial,19L)
        val loaded=SaveGame.parse(raw).city
        fun assertState() {
            assertEquals(city.funds,loaded.funds);assertEquals(city.month,loaded.month)
            assertEquals(city.population,loaded.population);assertEquals(city.demandR,loaded.demandR)
            assertEquals(city.waterDemand,loaded.waterDemand);assertEquals(city.waterSupply,loaded.waterSupply)
            assertEquals(city.garbageBacklog,loaded.garbageBacklog);assertEquals(city.infection,loaded.infection)
            assertEquals(city.garbageCapacity,loaded.garbageCapacity);assertEquals(city.garbageProduced,loaded.garbageProduced)
            assertEquals(city.lastIncomeBreakdown,loaded.lastIncomeBreakdown);assertEquals(city.lastSpending,loaded.lastSpending)
            assertEquals(city.history,loaded.history)
            for(i in city.tiles.indices) {
                val a=city.tiles[i];val b=loaded.tiles[i]
                assertEquals("water $i",a.watered,b.watered);assertEquals("power $i",a.powered,b.powered)
                assertEquals("traffic $i",a.traffic,b.traffic);assertEquals("crime $i",a.crime,b.crime)
                assertEquals("stage $i",a.stage,b.stage);assertEquals("landfill $i",a.landfillFill,b.landfillFill)
            }
        }
        assertState()
        repeat(12){city.step();loaded.step();assertState()}
    }
    @Test fun restoringLegacyDataRebuildsWaterWithoutAdvancingTime() {
        val city=strip(TileKind.ROAD)
        city.tileAt(10,4).kind=TileKind.WATER_TOWER
        val json=JSONObject(SaveGame.encode(city,Tutorial(),0L)).put("version",3)
        json.remove("demand");json.remove("income");json.remove("spending")
        val loaded=SaveGame.parse(json.toString()).city
        assertEquals(city.month,loaded.month);assertEquals(city.funds,loaded.funds)
        assertEquals(1200,loaded.waterSupply);assertTrue(loaded.tileAt(10,2).watered)
    }
    @Test fun saveIdentifiersAreExplicitUniqueAndKeepLegacyValues() {
        assertEquals(TileKind.entries.size,TileKind.entries.map { it.saveId }.toSet().size)
        assertEquals(16,TileKind.AVENUE.saveId);assertEquals(29,TileKind.POWER_LINE.saveId)
        assertEquals(30,TileKind.CLINIC.saveId);assertEquals(2,Terrain.SHORE.saveId)
    }
    @Test fun malformedAndFutureSavesAreRejectedBeforeAllocatingAMap() {
        val raw=SaveGame.encode(City(4,4),Tutorial(),0L)
        for(json in listOf(JSONObject(raw).put("version",999),JSONObject(raw).put("width",Int.MAX_VALUE),JSONObject(raw).put("kinds",""),JSONObject(raw).put("height",-4))) {
            assertThrows(IllegalArgumentException::class.java){SaveGame.parse(json.toString())}
        }
    }
    @Test fun recyclingDoesNotConsumeLandfillAndBulldozingResetsFill() {
        val city=strip(TileKind.ROAD)
        city.tileAt(10,4).apply { kind=TileKind.LANDFILL;landfillFill=11995 }
        for(x in 15..20)city.tileAt(x,4).kind=TileKind.RECYCLING
        city.step()
        assertEquals(11995,city.tileAt(10,4).landfillFill)
        assertTrue(city.bulldoze(10,4))
        assertTrue(city.build(10,4,TileKind.LANDFILL))
        assertEquals(0,city.tileAt(10,4).landfillFill)
    }
    @Test fun inspectionExplainsMissingInfrastructure() {
        val city=strip(TileKind.AVENUE)
        city.tileAt(10,2).stage=1
        city.recomputeDerivedState()
        assertTrue(city.growthStatus(10,2).contains("水"))
        city.tileAt(10,3).kind=TileKind.EMPTY
        city.recomputeDerivedState()
        assertTrue(city.growthStatus(10,2).contains("道路"))
    }
    @Test fun inspectingAVisibleRoofFindsItsBuildingRatherThanTheGroundBehindIt() {
        val city=City(12,12)
        city.tileAt(6,6).apply {kind=TileKind.ZONE_R;stage=3}
        val renderer=CityRenderer()
        val canvas=PixelCanvas(600,600)
        renderer.draw(canvas,city,6f,6f,1,2,0,600)
        // Search the area above this plot's ground row. A roof pixel must select its own plot.
        var hits=0
        for(y in 100..270 step 10)for(x in 270..530 step 10)
            if(renderer.buildingAtScreen(city,6f,6f,1,2,600,0,600,x,y)==city.index(6,6))hits++
        assertTrue("roof hit testing missed the building",hits>5)
        assertNull(renderer.buildingAtScreen(city,6f,6f,1,2,600,0,600,10,10))
    }

    @Test fun cachedScenesRefreshWhenPlotsAndCameraChange() {
        val city=City(12,12)
        val renderer=CityRenderer()
        val canvas=PixelCanvas(600,500)
        fun draw(x:Float=6f) = renderer.draw(canvas,city,x,6f,1,4,0,500)
        draw();val ground=canvas.pixels.copyOf()
        city.tileAt(6,6).apply {kind=TileKind.ZONE_R;stage=3;powered=true}
        draw();assertFalse(ground.contentEquals(canvas.pixels))
        val built=canvas.pixels.copyOf()
        draw();assertArrayEquals(built,canvas.pixels)
        draw(5f);assertFalse(built.contentEquals(canvas.pixels))
        city.tileAt(6,6).clearForBulldoze()
        draw();assertArrayEquals(ground,canvas.pixels)
    }

}
