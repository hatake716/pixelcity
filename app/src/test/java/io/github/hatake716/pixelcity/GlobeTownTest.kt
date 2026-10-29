package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.game.*
import io.github.hatake716.pixelcity.ui.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.DataInputStream
import java.util.zip.GZIPInputStream

class GlobeTownTest {
    private val assets=File("src/main/assets/globe")
    @Test fun `every city style facility and landmark resolves to an actual bounded mesh`() {
        val manifest=JSONObject(File(assets,"manifest.json").readText())
        val city=City(512,512)
        for((i,kind) in TileKind.entries.withIndex()) city.tiles[i].apply { this.kind=kind;stage=if(kind.isZone) 3 else 0 }
        for(style in City.Style.entries) {
            city.style=style;val town=GlobeTown.of(city)
            for(i in TileKind.entries.indices) town.model(i)?.let { assertTrue("Missing $it",manifest.has(it)) }
        }
        for((i,m) in Monument.entries.withIndex()) { city.tiles[100+i].kind=TileKind.MONUMENT;city.tiles[100+i].monument=m }
        val town=GlobeTown.of(city)
        for((i,m) in Monument.entries.withIndex()) assertEquals(m.name,town.model(100+i))
        for(id in manifest.keys()) for(suffix in listOf("", ".lod")) {
            DataInputStream(GZIPInputStream(File(assets,"$id$suffix.meshdata").inputStream())).use { input ->
                val count=input.readInt();assertTrue(count in 3..150000 && count%3==0)
                assertEquals(manifest.getJSONObject(id).getInt(if(suffix.isEmpty()) "vertices" else "lodVertices"),count)
                assertEquals(count*36,input.readBytes().size)
                if(suffix.isNotEmpty()) assertTrue(count<=1800)
            }
        }
    }
    @Test fun `road joins and service overlay levels match the simulation without retaining mutable tiles`() {
        val city=City(512,512);val i=city.index(100,100)
        city.tiles[i].kind=TileKind.ROAD
        city.tiles[i-1].kind=TileKind.ROAD;city.tiles[i+1].kind=TileKind.AVENUE;city.tiles[i-512].kind=TileKind.RAIL
        val town=GlobeTown.of(city,CityRenderer.Overlay.POWER)
        assertEquals(3,town.surface[i*4+1].toInt())
        assertEquals(255,town.surface[i*4+3].toInt() and 255)
        city.tiles[i].kind=TileKind.EMPTY;city.tiles[i].powered=true
        assertEquals(TileKind.ROAD.ordinal,town.kinds[i].toInt())
        assertEquals(255,town.surface[i*4+3].toInt() and 255)
    }
    @Test fun `nearby geometry stays bounded at the longitude seam and contains either side`() {
        val city=City(512,512)
        city.tiles[200*512].kind=TileKind.ZONE_R;city.tiles[200*512+511].kind=TileKind.ZONE_R
        val town=GlobeTown.of(city);val center=PlanetGeometry.center(200*512)
        val camera=PlanetGeometry.Camera(center.first,center.second,32f,cityView=true)
        val cells=town.nearby(camera)
        assertTrue(200*512 in cells);assertTrue(200*512+511 in cells);assertTrue(cells.size<=49*49)
        assertTrue(town.nearby(camera.copy(zoom=1f)).isEmpty())
    }
    @Test fun `city camera rays select the same nearby ground on both hemispheres`() {
        val city=City(512,512);val p=Planet(city,1L)
        for(index in listOf(102*512+121,220*512+460,310*512+22)) {
            val point=PlanetGeometry.center(index);val snapshot=PlanetGeometry.Snapshot.of(p);val land=PlanetGeometry.Landscape(snapshot)
            val focus=land.ground(index%512+.5f,index/512+.5f).length()
            val camera=PlanetGeometry.Camera(point.first,point.second,32f,focus,cityView=true)
            val mesh=PlanetGeometry.build(snapshot,camera,land,false);val ray=camera.ray(400f,400f,800,800)
            assertEquals(index,mesh.pick(ray.first,ray.second))
        }
    }
}
