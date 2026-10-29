package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.data.SaveGame
import io.github.hatake716.pixelcity.game.*
import io.github.hatake716.pixelcity.ui.*
import org.junit.Assert.*
import org.junit.Test

class DevelopedWorldTest {
    @Test fun allFourCompleteCitiesShareOneEarthWithTheirOwnModelsAndWorkingUtilities() {
        val world=RegionalShowcase.buildWorld();val city=world.city;val town=GlobeTown.of(city)
        assertSame(city,world.planet!!.city)
        val expected=mutableSetOf<Int>();var population=0;var funds=0
        for(kind in ShowcaseCity.Kind.entries) {
            val plan=RegionalShowcase.build(kind);val crop=plan.city
            population+=crop.population;funds+=crop.funds
            val destination=city.destinations.single { it.name==kind.label }
            assertEquals(plan.originX,destination.x);assertEquals(plan.originY,destination.y)
            fun index(i: Int)=(plan.region.top+i/crop.width)*512+plan.region.left+i%crop.width
            for((i,t) in crop.tiles.withIndex()) if(t.kind!=TileKind.EMPTY) {
                val global=index(i);assertTrue("Overlapping regions",expected.add(global))
                val merged=city.tiles[global]
                assertEquals(t.kind,merged.kind);assertEquals(t.stage,merged.stage);assertEquals(t.monument,merged.monument)
                assertEquals(if(t.monumentAnchor<0) -1 else index(t.monumentAnchor),merged.monumentAnchor)
                assertEquals(crop.style,city.styleAt(global%512,global/512))
                if(t.kind.isZone) {
                    assertTrue(merged.connected);assertTrue(merged.powered);assertTrue(merged.watered)
                    assertTrue(town.model(global)!!.startsWith(crop.style.name+"_"))
                }
            }
        }
        assertEquals(4,city.destinations.size);assertEquals(population,city.population);assertEquals(funds,city.funds)
        assertEquals(expected,city.tiles.indices.filter { city.tiles[it].kind!=TileKind.EMPTY }.toSet())
        for(i in city.tiles.indices) {
            if(i !in expected) assertEquals(EarthTerrain.biome(i),city.tiles[i].biome)
            else { assertFalse(EarthTerrain.biome(i).water);assertEquals(PlanetTerrain.PLAIN,city.tiles[i].biome) }
        }
    }
    @Test fun worldSavesAllDestinationsStylesAndPlayerEditsWithoutRebuildingAnything() {
        val world=RegionalShowcase.buildWorld();val city=world.city
        val originalStyles=city.localStyles.toMap();val originalDestinations=city.destinations.toList()
        val town=GlobeTown.of(city)
        val index=city.tiles.indices.first { city.tiles[it].kind==TileKind.ZONE_R }
        val model=town.model(index)
        city.funds=1234567;world.planet!!.terraform(index%512,index/512,PlanetTerrain.FOREST,1)
        val saved=SaveGame.parse(SaveGame.encode(city,world.tutorial,world.seed,world.planet))
        assertEquals(originalStyles,saved.city.localStyles);assertEquals(originalDestinations,saved.city.destinations)
        assertEquals(1234567,saved.city.funds);assertEquals(PlanetTerrain.FOREST,saved.city.tiles[index].biome)
        assertSame(saved,SaveGame.expandToPlanet(saved))
        saved.planet!!.terraform(index%512,index/512,PlanetTerrain.PLAIN,1)
        assertEquals(model,GlobeTown.of(saved.city).model(index))
        city.style=City.Style.JAPAN
        assertTrue(city.localStyles.isEmpty());assertEquals(originalDestinations,city.destinations)
        assertEquals("Render snapshot must remain immutable",model,town.model(index))
    }
    @Test fun sharedBudgetCanSustainFourCitiesForTwoYears() {
        val city=RegionalShowcase.buildWorld().city
        val population=city.population;val funds=city.funds
        city.disasterLevel=City.DisasterLevel.NONE
        repeat(24) { city.step() }
        println("WORLD after 24 months: population=$population -> ${city.population}, funds=$funds -> ${city.funds}, power=${city.powerSupply}/${city.powerDemand}, water=${city.waterSupply}/${city.waterDemand}")
        assertFalse(city.gameOver);assertTrue(city.population>=population*.6);assertTrue(city.funds>=funds)
        assertTrue(city.powerSupply>=city.powerDemand);assertTrue(city.waterSupply>=city.waterDemand)
    }
}
