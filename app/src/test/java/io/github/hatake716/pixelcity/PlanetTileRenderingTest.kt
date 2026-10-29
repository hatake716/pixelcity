package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.data.SaveGame
import io.github.hatake716.pixelcity.game.*
import io.github.hatake716.pixelcity.ui.*
import org.junit.Assert.*
import org.junit.Test

class PlanetTileRenderingTest {
    private fun world()=SaveGame.expandToPlanet(SaveGame.Loaded(City(),Tutorial().apply { skip() },47L)).planet!!
    @Test fun `all cells encode current terrain including odd coordinates and longitude seam`() {
        val p=world()
        for((n,t) in PlanetTerrain.entries.withIndex()) p.terraform(if(n%2==0) 511 else 0,181+n,t,1)
        val snapshot=PlanetGeometry.Snapshot.of(p);val bytes=PlanetGeometry.terrainTexels(snapshot)
        assertEquals(512*512*4,bytes.size)
        for(i in p.city.tiles.indices) {
            val t=p.city.tiles[i].biome
            assertEquals(t.id,bytes[i*4+3].toInt() and 255)
            val rgb=((bytes[i*4].toInt() and 255) shl 16) or ((bytes[i*4+1].toInt() and 255) shl 8) or (bytes[i*4+2].toInt() and 255)
            assertEquals(t.color and 0xffffff,rgb)
        }
    }
    @Test fun `one cell edit changes exactly one texel and undo restores all texels`() {
        val p=world();val index=p.city.index(321,211)
        val before=PlanetGeometry.terrainTexels(PlanetGeometry.Snapshot.of(p))
        val next=PlanetTerrain.entries[(p.city.tiles[index].biome.id+1)%11]
        p.terraform(321,211,next,1)
        val after=PlanetGeometry.terrainTexels(PlanetGeometry.Snapshot.of(p))
        assertEquals(listOf(index),(p.city.tiles.indices).filter { i -> (0..3).any { before[i*4+it]!=after[i*4+it] } })
        assertTrue(p.undo());assertArrayEquals(before,PlanetGeometry.terrainTexels(PlanetGeometry.Snapshot.of(p)))
    }
    @Test fun `initial unedited terrain gets detailed models and distant objects stay bounded`() {
        val p=world();val index=p.city.tiles.indexOfFirst { it.biome==PlanetTerrain.FOREST }
        val center=PlanetGeometry.center(index);val snapshot=PlanetGeometry.Snapshot.of(p)
        val near=PlanetGeometry.Camera(center.first,center.second,24f)
        assertTrue(index in PlanetGeometry.terrainObjects(snapshot,near))
        assertTrue(PlanetGeometry.terrainObjects(snapshot,near).size<=1024)
        assertTrue(PlanetGeometry.terrainObjects(snapshot,near.copy(zoom=1f)).isEmpty())
    }
    @Test fun `mountain prefabs follow the rendered ground without duplicating its relief`() {
        val p=world();p.terraform(256,256,PlanetTerrain.PLAIN,32);p.terraform(256,256,PlanetTerrain.MOUNTAIN,1)
        val index=p.city.index(256,256);val center=PlanetGeometry.center(index)
        val snapshot=PlanetGeometry.Snapshot.of(p);val land=PlanetGeometry.Landscape(snapshot)
        val mesh=PlanetGeometry.build(snapshot,PlanetGeometry.Camera(center.first,center.second,24f),land)
        val base=listOf(land.point(256f,256f),land.point(257f,256f),land.point(257f,257f),land.point(256f,257f)).maxOf { it.length() }
        var top=0f
        for(v in 0 until mesh.count) {
            val o=v*PlanetGeometry.STRIDE
            if(mesh.vertices[o+9].toInt()==index && mesh.vertices[o+10]==0f) {
                top=maxOf(top,PlanetGeometry.V(mesh.vertices[o],mesh.vertices[o+1],mesh.vertices[o+2]).length())
            }
        }
        assertTrue(top>base+0.005f);assertTrue("Mountain relief was counted twice",top<base+0.012f)
    }
    @Test fun `dense rainforest detail stays under a 64 MiB mesh budget`() {
        val snapshot=PlanetGeometry.Snapshot(ByteArray(512*512) { PlanetTerrain.RAINFOREST.id.toByte() },IntArray(512*512) { PlanetTerrain.RAINFOREST.color },emptyList(),0)
        val mesh=PlanetGeometry.build(snapshot,PlanetGeometry.Camera(0f,0f,24f))
        assertTrue(mesh.vertices.size.toLong()*4 < 64L*1024*1024)
        assertTrue(mesh.vertices.all { it.isFinite() })
        val far=PlanetGeometry.build(snapshot)
        assertEquals(512*512/4*6,far.count)
        for(v in 0 until far.count) assertEquals(1f,far.vertices[v*PlanetGeometry.STRIDE+10],0f)
    }
}
