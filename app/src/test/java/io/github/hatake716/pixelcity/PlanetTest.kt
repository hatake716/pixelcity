package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.data.SaveGame
import io.github.hatake716.pixelcity.game.*
import io.github.hatake716.pixelcity.ui.PlanetGeometry
import io.github.hatake716.pixelcity.ui.TerrainModels
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlanetTest {
    private fun world(): SaveGame.Loaded = SaveGame.expandToPlanet(SaveGame.Loaded(City(),Tutorial().apply { start() },192L))
    private fun encode(save: SaveGame.Loaded)=SaveGame.encode(save.city,save.tutorial,save.seed,save.planet)
    @Test fun `continuous Earth starts on natural lowlands without an artificial central square`() {
        val saved=world();val planet=saved.planet!!
        assertSame(saved.city,planet.city)
        assertEquals(512,saved.city.width);assertEquals(512,saved.city.height)
        assertEquals(262144,saved.city.tiles.size)
        for(i in saved.city.tiles.indices) assertEquals(EarthTerrain.biome(i),saved.city.tiles[i].biome)
        for(dy in -3..3) for(dx in -8..8) assertTrue(planet.canBuild(saved.city.originX+dx,saved.city.originY+dy))
        assertEquals(PlanetTerrain.SEA,planet.terrainAt(256,256))
        assertTrue(saved.city.tiles.map { it.biome }.toSet().size>=9)
        saved.tutorial.acknowledge(saved.city)
        assertEquals(12,TutorialDistrict.suggestions(saved.city,saved.tutorial).size)
        assertEquals(City.STARTING_FUNDS,saved.city.funds)
    }
    @Test fun `legacy city buildings finances and story survive translation into the same world`() {
        val old=City(60,60).apply {
            generateTerrain(451);clearStartingArea();funds=1_000_000;population=99_999;month=42;taxRate=11
            for(y in 15..25) for(x in 15..25) tileAt(x,y).terrain=Terrain.LAND
            assertTrue(buildMonument(20,20,Monument.COLOSSEUM));build(16,16,TileKind.ZONE_R);tileAt(16,16).stage=3
            tileAt(16,17).kind=TileKind.LANDFILL;tileAt(16,17).landfillFill=150
            recomputeDerivedState();refreshBudgetForecast()
        }
        val tutorial=Tutorial().apply { start() }
        val legacy=SaveGame.parse(SaveGame.encode(old,tutorial,123))
        assertNull(legacy.planet);assertEquals(60,legacy.city.width)
        val migrated=SaveGame.expandToPlanet(legacy);val offsetX=EarthTerrain.startX-30;val offsetY=EarthTerrain.startY-30
        assertEquals(old.funds,migrated.city.funds);assertEquals(42,migrated.city.month);assertEquals(11,migrated.city.taxRate)
        assertArrayEquals(tutorial.saveState(),migrated.tutorial.saveState())
        for(y in 0 until 60) for(x in 0 until 60) {
            val before=old.tileAt(x,y);val after=migrated.city.tileAt(x+offsetX,y+offsetY)
            assertEquals(before.kind,after.kind);assertEquals(before.stage,after.stage)
            if(before.kind!=TileKind.EMPTY) assertEquals(Terrain.LAND,after.terrain)
            else assertEquals(EarthTerrain.biome(migrated.city.index(x+offsetX,y+offsetY)),after.biome)
            assertEquals(before.landfillFill,after.landfillFill)
        }
        assertEquals(migrated.city.index(20+offsetX,20+offsetY),migrated.city.tileAt(21+offsetX,21+offsetY).monumentAnchor)
        assertSame(migrated,SaveGame.expandToPlanet(migrated))
    }
    @Test fun `one tile can become buildable beyond the initial field using the same municipal funds`() {
        val save=world();val planet=save.planet!!;val city=save.city
        val x=405;val y=76;val neighbor=planet.terrainAt(x+1,y);val funds=city.funds
        assertEquals(1,planet.terraform(x,y,PlanetTerrain.VOLCANO,1))
        assertFalse(city.build(x,y,TileKind.ROAD));assertFalse(city.canBuildOn(x,y))
        assertEquals(1,planet.terraform(x,y,PlanetTerrain.PLAIN,1))
        assertEquals(funds,city.funds);assertEquals(neighbor,planet.terrainAt(x+1,y))
        assertTrue(city.build(x,y,TileKind.ROAD));assertEquals(funds-BuildCost.cost(TileKind.ROAD),city.funds)
        assertTrue(city.build(city.originX,city.originY,TileKind.ROAD))
        assertEquals(funds-BuildCost.cost(TileKind.ROAD)*2,city.funds)
    }
    @Test fun `every individual nonplain terrain blocks buildings without any terraforming charge`() {
        val save=world();val p=save.planet!!;val city=save.city;val funds=city.funds
        for(terrain in PlanetTerrain.entries) {
            p.terraform(256,256,terrain,1)
            assertEquals(terrain,p.terrainAt(256,256));assertEquals(funds,city.funds);assertEquals(0,city.month)
            if(terrain!=PlanetTerrain.PLAIN) {
                assertFalse(city.canBuildOn(256,256));assertFalse(city.build(256,256,TileKind.ROAD))
                assertNotNull(city.monumentBlocker(256,256,Monument.COLOSSEUM))
            }
        }
    }
    @Test fun `buildings and full monument footprints are stored restored and undone losslessly`() {
        val save=world();val p=save.planet!!;val city=save.city
        city.funds=1_000_000;city.population=99_999
        p.terraform(251,251,PlanetTerrain.PLAIN,4)
        assertTrue(city.buildMonument(250,250,Monument.COLOSSEUM));val funds=city.funds
        assertEquals(4,p.terraform(251,251,PlanetTerrain.GLACIER,1))
        assertEquals(funds,city.funds)
        for(y in 250..251) for(x in 250..251) {
            assertEquals(TileKind.EMPTY,city.tileAt(x,y).kind);assertNotNull(city.tileAt(x,y).storedBuilding)
        }
        assertTrue(Monument.COLOSSEUM in city.builtMonuments)
        val restored=SaveGame.parse(encode(save));val rp=restored.planet!!
        assertEquals(funds,restored.city.funds)
        assertTrue(Monument.COLOSSEUM in restored.city.builtMonuments)
        assertEquals(4,rp.terraform(250,251,PlanetTerrain.PLAIN,1))
        assertEquals(Monument.COLOSSEUM,restored.city.tileAt(250,250).monument)
        assertEquals(restored.city.index(250,250),restored.city.tileAt(251,251).monumentAnchor)
        assertTrue(rp.undo());assertEquals(TileKind.EMPTY,restored.city.tileAt(250,250).kind)
        assertTrue(p.undo());assertEquals(Monument.COLOSSEUM,city.tileAt(250,250).monument)
    }
    @Test fun `brush covers actual tiles and wraps only at the longitude seam`() {
        val p=world().planet!!
        val indices=p.brushIndices(0,50,4)
        assertEquals(16,indices.size);assertTrue(indices.any { it%512==511 });assertTrue(indices.any { it%512==0 })
        assertEquals(8,p.brushIndices(0,0,4).size)
        val original=indices.associateWith { p.city.tiles[it].biome }
        p.terraform(0,50,PlanetTerrain.VOLCANO,4)
        assertTrue(indices.all { p.city.tiles[it].biome==PlanetTerrain.VOLCANO })
        assertTrue(p.undo());assertTrue(indices.all { p.city.tiles[it].biome==original[it] })
    }
    @Test fun `all individual terrain cells camera and shared municipal data round trip`() {
        val save=world();val p=save.planet!!
        for(t in PlanetTerrain.entries) p.terraform(400+t.id,80,t,1)
        p.seabedVisible=true;p.select(405,80);p.zoom=24f;p.latitude=0.4f;p.longitude=1.1f;p.introductionSeen=true;p.brushSize=16
        val before=save.city.funds;val restored=SaveGame.parse(encode(save))
        assertEquals(before,restored.city.funds);assertEquals(512,restored.city.width)
        assertEquals(EarthTerrain.startX,restored.city.originX);assertEquals(EarthTerrain.startY,restored.city.originY)
        assertTrue(restored.planet!!.seabedVisible);assertEquals(EarthTerrain.VERSION,restored.planet!!.earthVersion)
        assertEquals(405,restored.planet!!.selectedX);assertEquals(80,restored.planet!!.selectedY)
        assertEquals(24f,restored.planet!!.zoom,0.001f);assertTrue(restored.planet!!.introductionSeen)
        assertEquals(save.city.tiles.map { it.biome },restored.city.tiles.map { it.biome })
        assertArrayEquals(save.tutorial.saveState(),restored.tutorial.saveState())
    }
    @Test fun `incomplete or contradictory planet data is rejected`() {
        val base=encode(world())
        fun reject(json: JSONObject) {
            try { SaveGame.parse(json.toString());fail("Malformed save accepted") } catch(_: IllegalArgumentException) { }
        }
        reject(JSONObject(base).apply { remove("planet") })
        reject(JSONObject(base).apply { getJSONObject("planet").put("size",16) })
        reject(JSONObject(base).apply { put("biomes","a") })
        reject(JSONObject(base).apply { put("biomes","") })
        reject(JSONObject(base).apply { remove("biomes") })
        reject(JSONObject(base).apply { put("terrain","0".repeat(512*512)) })
        reject(JSONObject(base).apply { put("biomes","z"+getString("biomes").drop(1)) })
        reject(JSONObject(base).apply { put("version",999) })
        reject(JSONObject(base).apply { getJSONObject("planet").remove("earthVersion") })
        reject(JSONObject(base).apply { getJSONObject("planet").put("earthVersion",999) })
    }
    @Test fun `all eleven 3D terrain models have finite geometry and relief where appropriate`() {
        for(terrain in PlanetTerrain.entries) {
            val faces=TerrainModels.models.getValue(terrain)
            assertTrue(faces.isNotEmpty())
            val vertices=faces.flatMap { listOf(it.a,it.b,it.c) }
            assertTrue(vertices.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
            if(terrain!=PlanetTerrain.PLAIN) assertTrue("$terrain needs relief",vertices.maxOf { it.z }>0f)
        }
    }
    @Test fun `globe picking resolves individual tile coordinates on both hemispheres`() {
        val p=world().planet!!;val snapshot=PlanetGeometry.Snapshot.of(p)
        val mesh=PlanetGeometry.build(snapshot)
        for((x,y) in listOf(256 to 256,400 to 80,10 to 360,500 to 450,120 to 30)) {
            val index=y*512+x;val center=PlanetGeometry.center(index)
            val camera=PlanetGeometry.Camera(center.first,center.second,1f)
            val ray=camera.ray(300f,300f,600,600)
            assertEquals(index,mesh.pick(ray.first,ray.second))
        }
        val point=PlanetGeometry.normal(0.7f,0.4f)
        assertEquals(1f,point.length(),0.00001f)
    }
}
