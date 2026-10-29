package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.*
import kotlin.math.*

/** CPU geometry and ray picking are independent of Android / GL and tested on the JVM. */
object PlanetGeometry {
    const val STRIDE = 11 // position, normal, RGB, tile index, terrain surface flag
    data class V(val x: Float, val y: Float, val z: Float) {
        operator fun plus(v: V) = V(x+v.x, y+v.y, z+v.z)
        operator fun minus(v: V) = V(x-v.x, y-v.y, z-v.z)
        operator fun times(k: Float) = V(x*k, y*k, z*k)
        fun dot(v: V) = x*v.x + y*v.y + z*v.z
        fun cross(v: V) = V(y*v.z-z*v.y, z*v.x-x*v.z, x*v.y-y*v.x)
        fun length() = sqrt(dot(this))
        fun unit(): V = this * (1f / length().coerceAtLeast(0.000001f))
    }
    data class Building(val index: Int,val stage: Int,val kind: TileKind)
    data class Snapshot(val biomes: ByteArray,val colors: IntArray,val buildings: List<Building>,val revision: Int, val seabed: Boolean = false) {
        companion object {
            fun of(planet: Planet): Snapshot {
                val biomes=ByteArray(planet.tileCount)
                val colors=IntArray(planet.tileCount)
                val buildings=ArrayList<Building>()
                for((i,t) in planet.city.tiles.withIndex()) {
                    biomes[i]=t.biome.id.toByte()
                    colors[i]=t.biome.color
                    if(t.kind.isBuilding && t.stage>0) buildings.add(Building(i,t.stage,t.kind))
                }
                return Snapshot(biomes,colors,buildings,planet.revision,planet.seabedVisible)
            }
        }
    }
    /** One RGBA texel per saved tile. Alpha encodes the stable terrain ID, not opacity. */
    fun terrainTexels(snapshot: Snapshot): ByteArray {
        require(snapshot.biomes.size==Planet.SIZE*Planet.SIZE)
        return ByteArray(snapshot.biomes.size*4).also { bytes ->
            for(i in snapshot.biomes.indices) {
                val terrain=PlanetTerrain.entries[snapshot.biomes[i].toInt()]
                bytes[i*4]=(terrain.color ushr 16).toByte()
                bytes[i*4+1]=(terrain.color ushr 8).toByte()
                bytes[i*4+2]=terrain.color.toByte()
                bytes[i*4+3]=terrain.id.toByte()
            }
        }
    }
    // Bounded 32×32 detailed objects. The full 512×512 attribute texture is always visible.
    const val OBJECT_RADIUS=16
    fun terrainObjects(snapshot: Snapshot,camera: Camera): List<Int> {
        if(camera.zoom<8f) return emptyList()
        val center=tileIndex(camera.focus);val cx=center%Planet.SIZE/2*2;val cy=center/Planet.SIZE/2*2
        return buildList {
            for(dy in -OBJECT_RADIUS until OBJECT_RADIUS) for(dx in -OBJECT_RADIUS until OBJECT_RADIUS) {
                val x=Math.floorMod(cx+dx,Planet.SIZE);val y=cy+dy
                if(y !in 0 until Planet.SIZE) continue
                val i=y*Planet.SIZE+x;val biome=PlanetTerrain.entries[snapshot.biomes[i].toInt()]
                if(biome!=PlanetTerrain.PLAIN && !(snapshot.seabed && biome.water)) add(i)
            }
        }
    }
    data class Camera(val longitude: Float, val latitude: Float, val zoom: Float, val focusRadius: Float = 1f,
                      val cityView: Boolean = false) {
        val focus: V get() = V(sin(longitude)*cos(latitude), sin(latitude), cos(longitude)*cos(latitude))
        val eye: V get() {
            val tilt=((zoom-4f)/8f).coerceIn(0f,1f)*(if(cityView) 1.0472f else .72f)
            val north=focus.cross(right)
            return focus*cos(tilt)-north*sin(tilt)
        }
        val right: V get() {
            val east=V(cos(longitude),0f,-sin(longitude))
            val angle=if(cityView) ((zoom-2f)/10f).coerceIn(0f,1f)*PI.toFloat()/4 else 0f
            return east*cos(angle)+focus.cross(east)*sin(angle)
        }
        val up: V get() = eye.cross(right)
        fun halfHeight(aspect: Float) = 1.48f / min(1f, aspect) / zoom
        fun ray(x: Float, y: Float, width: Int, height: Int): Pair<V, V> {
            val aspect = width.toFloat()/height.coerceAtLeast(1)
            val hh = halfHeight(aspect)
            val origin = focus*focusRadius + eye*5f + right*((2*x/width-1)*hh*aspect) + up*((1-2*y/height)*hh)
            return origin to eye * -1f
        }
    }
    class Mesh(val vertices: FloatArray) {
        val count get() = vertices.size / STRIDE
        /** Nearest rendered triangle wins, including measured relief and edited terrain. */
        fun pick(origin: V, direction: V): Int? = hit(origin,direction)?.second
        fun hit(origin: V,direction: V): Pair<Float,Int>? {
            var distance=Float.POSITIVE_INFINITY
            var i=0
            // Scalar intersection avoids allocating vectors for every triangle on each drag event.
            while(i<vertices.size) {
                val j=i+STRIDE;val k=j+STRIDE
                val ax=vertices[i];val ay=vertices[i+1];val az=vertices[i+2]
                val ex=vertices[j]-ax;val ey=vertices[j+1]-ay;val ez=vertices[j+2]-az
                val fx=vertices[k]-ax;val fy=vertices[k+1]-ay;val fz=vertices[k+2]-az
                val hx=direction.y*fz-direction.z*fy;val hy=direction.z*fx-direction.x*fz;val hz=direction.x*fy-direction.y*fx
                val det=ex*hx+ey*hy+ez*hz
                if(abs(det)>0.0000001f) {
                    val inv=1f/det;val sx=origin.x-ax;val sy=origin.y-ay;val sz=origin.z-az
                    val u=(sx*hx+sy*hy+sz*hz)*inv
                    if(u>=0f && u<=1f) {
                        val qx=sy*ez-sz*ey;val qy=sz*ex-sx*ez;val qz=sx*ey-sy*ex
                        val v=(direction.x*qx+direction.y*qy+direction.z*qz)*inv
                        if(v>=0f && u+v<=1f) {
                            val t=(fx*qx+fy*qy+fz*qz)*inv
                            if(t>0f && t<distance) distance=t
                        }
                    }
                }
                i+=STRIDE*3
            }
            return if(distance.isFinite()) distance to tileIndex(origin+direction*distance) else null
        }
    }

    fun normal(u: Float,v: Float): V {
        val y=(1-2*v).coerceIn(-1f,1f)
        val r=sqrt(max(0f,1-y*y)); val lon=(u*2-1)*PI.toFloat()
        return V(sin(lon)*r,y,cos(lon)*r)
    }
    fun center(index: Int): Pair<Float,Float> =
        ((index%Planet.SIZE+0.5f)/Planet.SIZE*2-1)*PI.toFloat() to asin(1-2*(index/Planet.SIZE+0.5f)/Planet.SIZE)
    fun tileIndex(p: V): Int {
        val n=p.unit()
        val x=floor((atan2(n.x,n.z)/(2*PI.toFloat())+0.5f)*Planet.SIZE).toInt().let { Math.floorMod(it,Planet.SIZE) }
        val y=floor((1-n.y)*0.5f*Planet.SIZE).toInt().coerceIn(0,Planet.SIZE-1)
        return y*Planet.SIZE+x
    }
    private class Buffer(capacity: Int) {
        private val data=FloatArray(capacity)
        private var size=0
        // Fixed arity avoids allocating a temporary FloatArray for every vertex.
        fun add(x: Float,y: Float,z: Float,nx: Float,ny: Float,nz: Float,r: Float,g: Float,b: Float,index: Float,surface: Float) {
            data[size++]=x;data[size++]=y;data[size++]=z
            data[size++]=nx;data[size++]=ny;data[size++]=nz
            data[size++]=r;data[size++]=g;data[size++]=b
            data[size++]=index;data[size++]=surface
        }
        fun finish(): FloatArray { check(size==data.size); return data }
    }
    /** Continuous, bilinearly sampled elevation: adjacent tiles share the same edge vertices. */
    class Landscape(val snapshot: Snapshot) {
        private val heights=FloatArray(Planet.SIZE*Planet.SIZE) { i ->
            val value=EarthTerrain.relief(i,PlanetTerrain.entries[snapshot.biomes[i].toInt()])
            if(snapshot.seabed) value else max(0f,value)
        }
        private val northHeight=(0 until Planet.SIZE).sumOf { heights[it].toDouble() }.toFloat()/Planet.SIZE
        private val southHeight=(heights.size-Planet.SIZE until heights.size).sumOf { heights[it].toDouble() }.toFloat()/Planet.SIZE
        fun index(x: Int,y: Int)=y.coerceIn(0,Planet.SIZE-1)*Planet.SIZE+Math.floorMod(x,Planet.SIZE)
        fun altitude(x: Float,y: Float): Float {
            val xx=x-0.5f; val yy=y-0.5f
            val x0=floor(xx).toInt(); val y0=floor(yy).toInt(); val tx=xx-x0; val ty=yy-y0
            val sampled=(heights[index(x0,y0)]*(1-tx)+heights[index(x0+1,y0)]*tx)*(1-ty)+
                (heights[index(x0,y0+1)]*(1-tx)+heights[index(x0+1,y0+1)]*tx)*ty
            // Every longitude meets at one vertex at each pole, including in seabed view.
            return when {
                y<0.5f -> northHeight+(sampled-northHeight)*(y*2).coerceIn(0f,1f)
                y>Planet.SIZE-0.5f -> southHeight+(sampled-southHeight)*((Planet.SIZE-y)*2).coerceIn(0f,1f)
                else -> sampled
            }
        }
        fun point(x: Float,y: Float,z: Float=0f)=normal(x/Planet.SIZE,y/Planet.SIZE)*(1f+altitude(x,y)+z)
        /** Position on the same two triangles used by the detailed terrain mesh. */
        fun ground(x: Float,y: Float): V {
            val xx=floor(x);val yy=floor(y);val u=x-xx;val v=y-yy
            val a=point(xx,yy);val c=point(xx+1,yy+1)
            return if(u>=v) a*(1-u)+point(xx+1,yy)*(u-v)+c*v
                else a*(1-v)+c*u+point(xx,yy+1)*(v-u)
        }
        fun surfaceNormal(x: Float,y: Float): V {
            val a=point(x+0.4f,y)-point(x-0.4f,y)
            val b=point(x,(y+0.4f).coerceAtMost(Planet.SIZE.toFloat()))-point(x,(y-0.4f).coerceAtLeast(0f))
            val n=b.cross(a).unit(); val radial=normal(x/Planet.SIZE,y/Planet.SIZE)
            return if(n.length()<0.5f) radial else if(n.dot(radial)<0) n * -1f else n
        }
    }
    /** Planet-wide relief, with full tile resolution around the magnified editing position. */
    fun build(snapshot: Snapshot,camera: Camera=Camera(0f,0f,1f),land: Landscape=Landscape(snapshot),includeBuildings: Boolean=true): Mesh {
        val size=Planet.SIZE
        val target=tileIndex(camera.focus); val cx=(target%size/2)*2; val cy=(target/size/2)*2
        val detail=camera.zoom>=8f
        fun detailed(x: Int,y: Int): Boolean {
            val dx=Math.floorMod(x-cx+size/2,size)-size/2
            return detail && dx in -24 until 24 && y-cy in -24 until 24
        }
        // Count exactly before allocating: no doubling buffers or final 17+ MB copy.
        var cells=size*size/4
        if(detail) for(y in 0 until size step 2) for(x in 0 until size step 2) if(detailed(x,y)) cells+=3
        val terrainObjects=terrainObjects(snapshot,camera)
        val farStride=max(1,snapshot.buildings.size/1500)
        val buildings=if(includeBuildings) snapshot.buildings.filterIndexed { k,b -> detailed(b.index%size,b.index/size) || k%farStride==0 } else emptyList()
        val objectVertices=terrainObjects.sumOf { (TerrainModels.models.getValue(PlanetTerrain.entries[snapshot.biomes[it].toInt()]).size-2)*3 }
        val out=Buffer((cells*6+objectVertices+buildings.size*30)*STRIDE)
        fun vertex(p: V,n: V,color: Int,index: Int,surface: Float) {
            out.add(p.x,p.y,p.z,n.x,n.y,n.z,(color shr 16 and 255)/255f,
                (color shr 8 and 255)/255f,(color and 255)/255f,index.toFloat(),surface)
        }
        fun triangle(a: V,b: V,c: V,color: Int,index: Int) {
            var n=(b-a).cross(c-a).unit()
            if(n.dot(a+b+c)<0f) n=n * -1f
            for(p in arrayOf(a,b,c)) vertex(p,n,color,index,0f)
        }
        fun cell(x: Int,y: Int,step: Int) {
            val i=y*size+x
            val color=snapshot.colors[i]
            val xs=floatArrayOf(x.toFloat(),x+step.toFloat(),x+step.toFloat(),x.toFloat())
            val ys=floatArrayOf(y.toFloat(),y.toFloat(),y+step.toFloat(),y+step.toFloat())
            fun position(k: Int): V {
                val xx=xs[k].toInt();val yy=ys[k].toInt()
                val dx=Math.floorMod(xx-cx+size/2,size)-size/2
                // Match the coarser edge exactly; no cracks where the detail patch ends.
                if(step==1 && abs(dx)==24 && yy%2==1)
                    return (land.point(xs[k],ys[k]-1)+land.point(xs[k],ys[k]+1))*0.5f
                if(step==1 && abs(yy-cy)==24 && xx%2==1)
                    return (land.point(xs[k]-1,ys[k])+land.point(xs[k]+1,ys[k]))*0.5f
                return land.point(xs[k],ys[k])
            }
            val positions=Array(4) { position(it) }
            val normals=Array(4) { land.surfaceNormal(xs[it],ys[it]) }
            for(k in intArrayOf(0,1,2,0,2,3)) vertex(positions[k],normals[k],color,i,1f)
        }
        for(y in 0 until size step 2) for(x in 0 until size step 2) {
            if(detailed(x,y)) {
                for(dy in 0..1) for(dx in 0..1) cell(x+dx,y+dy,1)
            } else cell(x,y,2)
        }
        // Every nearby tile uses its assigned model, including the initial terrain.
        for(i in terrainObjects) {
            val x=i%size;val y=i/size;val biome=PlanetTerrain.entries[snapshot.biomes[i].toInt()]
            // Attach to the rendered triangles, not a second interpolation of the heightfield.
            // Sampling the tall tile's center again stretched mountain prefabs into needles.
            val a=land.point(x.toFloat(),y.toFloat());val b=land.point(x+1f,y.toFloat())
            val c=land.point(x+1f,y+1f);val d=land.point(x.toFloat(),y+1f)
            fun p(v: TerrainModels.Point): V {
                val lower=v.x>=v.y
                val w0=if(lower) 1-v.x else 1-v.y
                val w1=if(lower) v.x-v.y else v.x
                val w2=if(lower) v.y else v.y-v.x
                val p1=if(lower) b else c;val p2=if(lower) c else d
                val n=normal((x+v.x)/size,(y+v.y)/size);val z=v.z*0.010f+0.00015f
                return V(a.x*w0+p1.x*w1+p2.x*w2+n.x*z,a.y*w0+p1.y*w1+p2.y*w2+n.y*z,a.z*w0+p1.z*w1+p2.z*w2+n.z*z)
            }
            val faces=TerrainModels.models.getValue(biome)
            // The existing tile material already draws the base; retain only raised features.
            for(f in 2 until faces.size) {
                val face=faces[f];triangle(p(face.a),p(face.b),p(face.c),face.color,i)
            }
        }
        for(b in buildings) {
            val x=b.index%size;val y=b.index/size
            val z=0.002f+b.stage*0.002f;val shade=if(b.kind==TileKind.ZONE_C) 0xff8ab3c1.toInt() else 0xffe1bb83.toInt()
            val bottom=arrayOf(land.point(x+0.15f,y+0.15f),land.point(x+0.85f,y+0.15f),land.point(x+0.85f,y+0.85f),land.point(x+0.15f,y+0.85f))
            val top=arrayOf(land.point(x+0.15f,y+0.15f,z),land.point(x+0.85f,y+0.15f,z),land.point(x+0.85f,y+0.85f,z),land.point(x+0.15f,y+0.85f,z))
            for(j in 0..3) { val n=(j+1)%4;triangle(bottom[j],bottom[n],top[n],shade,b.index);triangle(bottom[j],top[n],top[j],shade,b.index) }
            triangle(top[0],top[1],top[2],0xffc88270.toInt(),b.index);triangle(top[0],top[2],top[3],0xffc88270.toInt(),b.index)
        }
        return Mesh(out.finish())
    }

    fun atmosphere(): Mesh {
        val out=Buffer(64*128*6*STRIDE)
        for(y in 0 until 64) for(x in 0 until 128) {
            val points=arrayOf(normal(x/128f,y/64f),normal((x+1)/128f,y/64f),
                normal((x+1)/128f,(y+1)/64f),normal(x/128f,(y+1)/64f))
            for(k in intArrayOf(0,1,2,0,2,3)) {
                val n=points[k];val p=n*1.085f
                out.add(p.x,p.y,p.z,n.x,n.y,n.z,0.2f,0.5f,1f,-1f,0f)
            }
        }
        return Mesh(out.finish())
    }
}
