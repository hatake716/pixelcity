package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.*
import kotlin.math.*

/** Immutable simulation snapshot. GL never reads the mutable City on its render thread. */
class GlobeTown(val kinds: ByteArray,val stages: ByteArray,val monuments: ByteArray,
                val surface: ByteArray,val style: City.Style,val overlayColors: FloatArray,val tints: IntArray,
                private val localStyles: Map<Int,City.Style> = emptyMap()) {
    companion object {
        fun of(city: City,overlay: CityRenderer.Overlay=CityRenderer.Overlay.NONE): GlobeTown {
            val n=city.tiles.size;val kinds=ByteArray(n);val stages=ByteArray(n);val monuments=ByteArray(n) { -1 }
            val surface=ByteArray(n*4)
            for(i in 0 until n) {
                val t=city.tiles[i];kinds[i]=t.kind.ordinal.toByte();stages[i]=t.stage.toByte()
                monuments[i]=t.monument?.ordinal?.toByte() ?: -1
                surface[i*4]=t.kind.ordinal.toByte();surface[i*4+2]=t.stage.toByte()
                if(t.kind.isTransport) {
                    val x=i%city.width;val y=i/city.width
                    var mask=0
                    for(d in 0..3) {
                        val nx=x+when(d) { 0->-1;1->1;else->0 };val ny=y+when(d) { 2->-1;3->1;else->0 }
                        val other=city.tileOrNull(nx,ny)?.kind
                        if(other!=null && if(t.kind.isRoad) other.isRoad else other==t.kind) mask=mask or (1 shl d)
                    }
                    surface[i*4+1]=mask.toByte()
                }
                surface[i*4+3]=(CityRenderer.levelOf(overlay,t).coerceIn(0,5)*255/5).toByte()
            }
            val colors=FloatArray(18)
            for(level in 0..5) {
                val c=Palette.of(CityRenderer.colourOf(overlay,level))
                colors[level*3]=(c ushr 16 and 255)/255f;colors[level*3+1]=(c ushr 8 and 255)/255f;colors[level*3+2]=(c and 255)/255f
            }
            val slots=listOf(CustomStyle.Slot.HOUSE_WALL,CustomStyle.Slot.OFFICE_WALL,CustomStyle.Slot.FACTORY_WALL,CustomStyle.Slot.GENERIC_WALL)
            val tints=IntArray(4) { i -> val slot=slots[i];val value=city.customStyle[slot]
                if(city.style!=City.Style.CUSTOM || value==slot.default) 0 else Palette.of(value) }
            return GlobeTown(kinds,stages,monuments,surface,city.style,colors,tints,city.localStyles.toMap())
        }
        fun variant(x: Int,y: Int): Int {
            var h=x*374761393+y*668265263;h=(h xor (h shr 13))*1274126177
            return (h xor (h shr 16)) and Int.MAX_VALUE
        }
    }
    fun model(index: Int): String? {
        val kind=TileKind.entries[kinds[index].toInt()];val x=index%512;val y=index/512
        val monument=monuments[index].toInt()
        if(monument>=0) return Monument.entries[monument].name
        if(kind==TileKind.EMPTY || kind.isTransport || kind==TileKind.MONUMENT) return null
        val style=localStyles[index] ?: style
        val themed=style!=City.Style.STANDARD && style!=City.Style.CUSTOM
        if(kind.isZone) {
            val stage=stages[index].toInt();if(stage==0) return null
            return if(themed) "${style.name}_${kind.name}_${stage.coerceIn(1,3)}_${variant(x,y)%2}"
                else "${kind.name}_${stage.coerceIn(1,3)}_${variant(x,y)%6}"
        }
        return if(themed && kind.name in setOf("PARK","GROVE","FARM","WATER_TOWER","SCHOOL")) "${style.name}_${kind.name}" else kind.name
    }
    fun tint(index: Int): Int {
        if(index<0 || monuments[index].toInt()>=0) return 0
        return tints[when(TileKind.entries[kinds[index].toInt()]) { TileKind.ZONE_R->0;TileKind.ZONE_C->1;TileKind.ZONE_I->2;else->3 }]
    }
    /** Nearby cell traversal is bounded independently of total population/building count. */
    fun nearby(camera: PlanetGeometry.Camera): List<Int> {
        if(camera.zoom<3f) return emptyList()
        val i=PlanetGeometry.tileIndex(camera.focus);val cx=i%512;val cy=i/512
        val radius=if(camera.zoom>=12f) 24 else 40
        return buildList {
            for(dy in -radius..radius) for(dx in -radius..radius) {
                val y=cy+dy;if(y !in 0..511) continue
                val index=y*512+Math.floorMod(cx+dx,512)
                if(kinds[index].toInt()!=0) add(index)
            }
        }.sortedBy { index ->
            val dx=Math.floorMod(index%512-cx+256,512)-256;val dy=index/512-cy
            dx*dx+dy*dy
        }
    }
}

data class GlobeMover(val model: String,val x: Float,val y: Float)
