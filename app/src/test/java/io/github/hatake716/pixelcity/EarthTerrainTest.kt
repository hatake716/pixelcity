package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.data.SaveGame
import io.github.hatake716.pixelcity.game.*
import io.github.hatake716.pixelcity.ui.PlanetGeometry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EarthTerrainTest {
    private fun world()=SaveGame.expandToPlanet(SaveGame.Loaded(City(),Tutorial().apply { start() },1L))
    @Test fun `photographic geography and signed source heights match known land and ocean locations`() {
        val tibet=EarthTerrain.index(85.0,30.0)
        val pacific=EarthTerrain.index(-140.0,0.0)
        assertTrue(EarthTerrain.meters(tibet)>4000)
        assertTrue(EarthTerrain.meters(pacific)<-3000)
        assertEquals(PlanetTerrain.RANGE,EarthTerrain.biome(tibet))
        assertEquals(PlanetTerrain.SEA,EarthTerrain.biome(pacific))
        assertEquals(PlanetTerrain.DESERT,EarthTerrain.biome(EarthTerrain.index(30.0,25.0)))
        assertEquals(PlanetTerrain.RAINFOREST,EarthTerrain.biome(EarthTerrain.index(-65.0,-3.0)))
        assertEquals(EarthTerrain.index(-180.0,0.0),EarthTerrain.index(180.0,0.0))
    }
    @Test fun `actual vertex radii expose mountains and ocean floor instead of color-only relief`() {
        val p=world().planet!!
        val tibet=EarthTerrain.index(85.0,30.0);val pacific=EarthTerrain.index(-140.0,0.0)
        fun radius(land: PlanetGeometry.Landscape,i: Int)=land.point(i%512+0.5f,i/512+0.5f).length()
        val land=PlanetGeometry.Landscape(PlanetGeometry.Snapshot.of(p))
        assertTrue(radius(land,tibet)>1.04f)
        assertEquals(1f,radius(land,pacific),0.00001f)
        p.seabedVisible=true
        val bottom=PlanetGeometry.Landscape(PlanetGeometry.Snapshot.of(p))
        assertTrue(radius(bottom,pacific)<0.965f)
        assertEquals(radius(land,tibet),radius(bottom,tibet),0.00001f)
        val borderA=bottom.point(0f,200f);val borderB=bottom.point(512f,200f)
        assertTrue((borderA-borderB).length()<0.00001f)
        for(y in listOf(0f,512f)) for(x in listOf(0f,128f,256f,384f,512f))
            assertTrue("Pole vertices must coincide",(bottom.point(0f,y)-bottom.point(x,y)).length()<0.00001f)
    }
    @Test fun `old fictional planetary terrain is regenerated once with no empty rectangular copy`() {
        val old=City(512,512)
        old.tileAt(256,256).kind=TileKind.ROAD;old.funds=12345
        val oldPlanet=Planet(old,8L).apply { earthVersion=0;introductionSeen=true }
        val encoded=JSONObject(SaveGame.encode(old,Tutorial().apply { skip() },8L,oldPlanet)).put("version",6)
        encoded.getJSONObject("planet").remove("earthVersion")
        val fresh=SaveGame.expandToPlanet(SaveGame.parse(encoded.toString()))
        assertEquals(12345,fresh.city.funds)
        assertEquals(TileKind.ROAD,fresh.city.tileAt(EarthTerrain.startX,EarthTerrain.startY).kind)
        assertEquals(PlanetTerrain.SEA,fresh.city.tileAt(256,256).biome)
        assertTrue(fresh.planet!!.introductionSeen)
        val reopened=SaveGame.parse(SaveGame.encode(fresh.city,fresh.tutorial,fresh.seed,fresh.planet))
        assertSame(reopened,SaveGame.expandToPlanet(reopened))
    }
    @Test fun `terraform and undo change relief and restore the source topography`() {
        val p=world().planet!!;val i=EarthTerrain.index(85.0,30.0)
        val original=EarthTerrain.relief(i,p.city.tiles[i].biome)
        p.terraform(i%512,i/512,PlanetTerrain.PLAIN,1)
        assertTrue(EarthTerrain.relief(i,p.city.tiles[i].biome)<0.002f)
        assertTrue(p.undo())
        assertEquals(original,EarthTerrain.relief(i,p.city.tiles[i].biome),0f)
    }
}
