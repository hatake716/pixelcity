package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.game.*
import io.github.hatake716.pixelcity.ui.Pedestrians
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.floor

class PedestriansTest {
    private fun street(kind: TileKind = TileKind.ROAD) = City(16,16).apply {
        for(t in tiles) { t.terrain=Terrain.LAND;t.kind=TileKind.EMPTY }
        for(x in 2..13) {
            tileAt(x,7).kind=kind
            tileAt(x,6).apply { this.kind=TileKind.ZONE_R;stage=2 }
        }
    }
    private fun people(c: City,t: Double) = mutableListOf<Pedestrians.Pose>().also { list ->
        for(y in 0 until c.height) for(x in 0 until c.width) Pedestrians.onTile(c,x,y,t,list)
    }
    @Test fun walkersMoveInTheirFacingDirectionAndAlternateTheirStride() {
        val c=street();val before=people(c,12.0);val after=people(c,12.03)
        assertTrue(before.size>5);assertEquals(before.size,after.size)
        var moved=0
        for((a,b) in before.zip(after)) if(a.facing==b.facing) {
            val dx=b.x-a.x;val dy=b.y-a.y
            val forward=when(a.facing) { 0->dx;1->dy;2->-dx;else->-dy }
            assertTrue("walker faces away from its motion",forward>0f);moved++
        }
        assertTrue(moved>5)
        assertTrue(before.zip(people(c,12.4)).any { it.first.step!=it.second.step })
    }
    @Test fun walkersRemainOnConnectedPavementsAndAreStableWhenRedrawn() {
        val c=street()
        for(t in listOf(0.0,14.5,1234.567,999999.23)) {
            val poses=people(c,t);assertEquals(poses,people(c,t))
            for(p in poses) {
                val x=floor(p.x+.5f).toInt();val y=floor(p.y+.5f).toInt()
                assertEquals(TileKind.ROAD,c.tileAt(x,y).kind)
                assertEquals(.292f,kotlin.math.abs(p.y-7),.00001f)
            }
        }
    }
    @Test fun noWalkersOnHighwaysWaterOrStreetsWithoutResidents() {
        assertTrue(people(street(TileKind.HIGHWAY),5.0).isEmpty())
        val c=street();for(x in 2..13)c.tileAt(x,7).terrain=Terrain.WATER
        assertTrue(people(c,5.0).isEmpty())
        val empty=street();for(x in 2..13)empty.tileAt(x,6).kind=TileKind.EMPTY
        assertTrue(people(empty,5.0).isEmpty())
    }
    @Test fun roadRemovalRemovesItsWalkersImmediatelyAndDoesNotChangeTheCity() {
        val c=street();val funds=c.funds;val population=c.population
        assertTrue(people(c,5.0).isNotEmpty())
        for(x in 2..13)c.tileAt(x,7).kind=TileKind.EMPTY
        assertTrue(people(c,5.0).isEmpty());assertEquals(funds,c.funds);assertEquals(population,c.population)
    }
    @Test fun northSouthAvenuesUseTheirWiderPavements() {
        val c=City(16,16)
        for(t in c.tiles) { t.terrain=Terrain.LAND;t.kind=TileKind.EMPTY }
        for(y in 2..13) {
            c.tileAt(7,y).kind=TileKind.AVENUE
            c.tileAt(6,y).apply { kind=TileKind.ZONE_C;stage=2 }
        }
        val a=people(c,5.0);val b=people(c,5.02);assertTrue(a.size>5)
        for((p,q) in a.zip(b)) {
            assertEquals(.355f,kotlin.math.abs(p.x-7),.00001f)
            assertTrue(p.facing==1 || p.facing==3)
            if(p.facing==q.facing)assertTrue(if(p.facing==1)q.y>p.y else q.y<p.y)
        }
    }
}
