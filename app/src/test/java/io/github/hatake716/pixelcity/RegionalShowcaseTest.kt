package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.data.SaveGame
import io.github.hatake716.pixelcity.game.*
import io.github.hatake716.pixelcity.ui.*
import org.junit.Assert.*
import org.junit.Test

class RegionalShowcaseTest {
    @Test fun newExamplesRespectTheirOwnCoastAndKeepTheRestOfEarthUntouched() {
        val origins=mutableSetOf<Int>()
        for(kind in ShowcaseCity.Kind.entries) {
            val plan=RegionalShowcase.build(kind);val loaded=plan.expand();val city=loaded.city
            origins.add(city.index(city.originX,city.originY))
            assertEquals(plan.originX,city.originX);assertEquals(plan.originY,city.originY)
            val built=city.tiles.indices.filter { city.tiles[it].kind!=TileKind.EMPTY }
            assertTrue("${kind.name} empty",built.size>40)
            for(tile in city.tiles.filter { it.kind.isZone }) {
                assertTrue("${kind.name} disconnected neighbourhood",tile.connected)
                assertTrue("${kind.name} unpowered neighbourhood",tile.powered)
                assertTrue("${kind.name} neighbourhood has no water",tile.watered)
            }
            for(i in city.tiles.indices) {
                val tile=city.tiles[i];val source=EarthTerrain.biome(i)
                if(tile.kind==TileKind.EMPTY) assertEquals("${kind.name} unused cell $i",source,tile.biome)
                else {
                    assertFalse("${kind.name} fills the sea $i",source.water)
                    assertTrue("${kind.name} builds on a mountain",EarthTerrain.meters(i)<=900)
                    assertEquals(PlanetTerrain.PLAIN,tile.biome)
                    assertTrue(i%512 in plan.region.left..plan.region.right && i/512 in plan.region.top..plan.region.bottom)
                }
            }
            val w=built.maxOf { it%512 }-built.minOf { it%512 }+1
            val h=built.maxOf { it/512 }-built.minOf { it/512 }+1
            assertTrue("${kind.name} became a solid rectangle",built.size<w*h*.9)
            val restored=SaveGame.parse(SaveGame.encode(city,loaded.tutorial,loaded.seed,loaded.planet))
            assertEquals(city.originX,restored.city.originX);assertEquals(city.originY,restored.city.originY)
            assertSame(restored,SaveGame.expandToPlanet(restored,EarthTerrain.startX,EarthTerrain.startY))
        }
        assertEquals(4,origins.size)
    }
    @Test fun harbourPortActuallyTouchesBritishSeaAndExamplesRemainPlayable() {
        for(kind in ShowcaseCity.Kind.entries) {
            val plan=RegionalShowcase.build(kind);val c=plan.expand().city
            val start=c.population;val funds=c.funds
            if(kind==ShowcaseCity.Kind.HARBOUR) {
                val port=c.tiles.indices.single { c.tiles[it].kind==TileKind.SEAPORT }
                val g=port
                assertTrue(intArrayOf(g-1,g+1,g-512,g+512).any { EarthTerrain.biome(it).water })
                assertTrue(c.touchesRoad(port%c.width,port/c.width))
            }
            println("REGIONAL ${kind.name}: ${c.width}x${c.height}, population=$start, income=${c.lastIncome}, upkeep=${c.lastUpkeep}, zones=${c.tiles.count { it.kind.isZone }}, power=${c.powerSupply}/${c.powerDemand}, water=${c.waterSupply}/${c.waterDemand}, monuments=${c.builtMonuments}")
            c.disasterLevel=City.DisasterLevel.NONE
            repeat(24) { c.step() }
            println("REGIONAL AFTER ${kind.name}: population=${c.population}, funds=${c.funds}, income=${c.lastIncome}, upkeep=${c.lastUpkeep}, garbage=${c.garbageProduced}/${c.garbageCapacity}")
            assertTrue("${kind.name} lost its population $start -> ${c.population}",c.population>=start*.6)
            assertTrue("${kind.name} cannot support its services $funds -> ${c.funds}",c.funds>=funds)
            assertFalse(c.gameOver)
            assertTrue(c.powerSupply>=c.powerDemand)
            assertTrue(c.waterSupply>=c.waterDemand)
        }
    }
    @Test fun eachPreviewIsSmallAndDeterministicWithItsOwnCityCharacter() {
        for(kind in ShowcaseCity.Kind.entries) {
            val a=RegionalShowcase.build(kind).city;val b=RegionalShowcase.build(kind).city
            assertTrue(a.tiles.size<1000)
            assertEquals(a.tiles.map { it.kind to it.stage },b.tiles.map { it.kind to it.stage })
            if(kind==ShowcaseCity.Kind.GARDEN) { assertTrue(a.tiles.any { it.kind==TileKind.FARM });assertTrue(a.tiles.any { it.kind==TileKind.POWER_WIND });assertFalse(a.tiles.any { it.kind==TileKind.POWER_COAL }) }
            if(kind==ShowcaseCity.Kind.INDUSTRIAL) assertTrue(a.tiles.count { it.kind==TileKind.ZONE_I }>a.tiles.count { it.kind==TileKind.ZONE_R })
        }
    }
}
