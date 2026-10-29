package io.github.hatake716.pixelcity.ui

import android.content.Context
import android.graphics.*
import android.util.LruCache
import io.github.hatake716.pixelcity.game.*
import org.json.JSONArray
import kotlin.math.*

/** Wool-felt models baked at a 30° orthographic camera; transforms keep subpixel precision. */
class RealisticCityRenderer(context: Context) {
    private val assets = context.applicationContext.assets
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val ground = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val textures = HashMap<String, Bitmap>()
    private val appearance = CityAppearance(assets)
    private val walkers = ArrayList<Pedestrians.Pose>(8)
    private var groundStyle: City.Style? = null
    private data class Sprite(val bitmap: Bitmap, val ax: Float, val ay: Float, val width: Int, val height: Int)
    private data class Item(val id: String, val x: Float, val y: Float, val index: Int, val depth: Float)
    private val items = ArrayList<Item>()
    private val visible = ArrayList<Int>()
    private val metadata by lazy { JSONArray(assets.open("realistic/manifest.json").bufferedReader().use { it.readText() }).let { a ->
        (0 until a.length()).associate { i -> val o=a.getJSONObject(i); o.getString("id") to o }
    } }
    private val sprites get() = sharedSprites
    private val deviceMatrix = Matrix()
    private val matrixValues = FloatArray(9)
    private var sceneSample = 1
    init { renderers[this] = true }

    @Suppress("DEPRECATION") // Includes the caller's logical-canvas scale, not just the camera zoom.
    private fun deviceScale(c: Canvas): Float {
        c.getMatrix(deviceMatrix); deviceMatrix.getValues(matrixValues)
        return max(hypot(matrixValues[0],matrixValues[3]), hypot(matrixValues[1],matrixValues[4]))
    }
    private fun sampleFor(scale: Float): Int {
        var sample=1
        while(sample<16 && sample*2*scale<=1f) sample*=2
        return sample
    }
    private fun sprite(id: String, sample: Int): Sprite? {
        val key="$id:$sample"
        sprites.get(key)?.let { return it }
        if(id.startsWith("TERRAIN_")) {
            val terrain=PlanetTerrain.entries.firstOrNull { "TERRAIN_${it.name}"==id } ?: return null
            return Sprite(FeltTerrainSprite.bake(terrain,sample),FeltTerrainSprite.ANCHOR_X,FeltTerrainSprite.ANCHOR_Y,512,640).also { sprites.put(key,it) }
        }
        val m=metadata[id] ?: return null
        val options=BitmapFactory.Options().apply { inSampleSize=sample }
        val b=assets.open("realistic/$id.webp").use { BitmapFactory.decodeStream(it,null,options) } ?: return null
        return Sprite(b,m.getDouble("anchorX").toFloat(),m.getDouble("anchorY").toFloat(),m.getInt("width"),m.getInt("height")).also { sprites.put(key,it) }
    }

    /** Drop references only: Android may still use these bitmaps in a recorded display list. */
    fun releaseCaches() { ground.evictAll(); textures.clear(); grades.clear() }
    private fun variant(x: Int, y: Int): Int {
        var h=x*374761393+y*668265263;h=(h xor (h shr 13))*1274126177
        return (h xor (h shr 16)) and 0x7fffffff
    }
    private fun model(city: City, x: Int, y: Int): String? {
        val t=city.tileAt(x,y)
        if(t.biome!=PlanetTerrain.PLAIN) return "TERRAIN_${t.biome.name}"
        val id = when {
            t.monument!=null -> t.monument!!.name
            t.kind==TileKind.MONUMENT -> null
            t.kind.isZone -> if(t.stage==0) null else "${t.kind.name}_${t.stage.coerceIn(1,3)}_${variant(x,y)%6}"
            t.kind==TileKind.EMPTY && t.terrain==Terrain.WATER -> if(variant(x,y)%59==0) "SAILBOAT" else null
            t.kind==TileKind.EMPTY -> if(t.terrain==Terrain.LAND && variant(x,y)%11==0 && (abs(x-city.width/2)>5 || abs(y-city.height/2)>5)) "GROVE" else null
            t.kind.isTransport -> null
            else -> t.kind.name
        }
        return id?.let { appearance.model(city.styleAt(x,y), it) }
    }

    /** A single affine camera is shared by drawing, tile picking, and gesture anchoring. */
    fun draw(c: Canvas, city: City, camX: Float, camY: Float, zoom: Float, width: Int,
             top: Int, height: Int, cars: List<TrafficAnimation.Car> = emptyList(),
             overlay: CityRenderer.Overlay = CityRenderer.Overlay.NONE, selected: Set<Int> = emptySet(),
             highlight: IntArray? = null, suggest: ((Int,Int)->Boolean)? = null, suggestOn: Boolean = false,
             time: Float = 0f, streetTime: Double = time.toDouble()) {
        if(height<=0) return
        sceneSample=sampleFor(zoom*deviceScale(c))
        c.save();c.clipRect(0,top,width,top+height)
        c.drawColor(appearance.color(city.style, "grass"))
        val ox=width/2f-(camX-camY)*256f*zoom
        val oy=top+height/2f-(camX+camY)*128f*zoom
        visible.clear();items.clear()
        val bounds=listOf(0f to top.toFloat(),width.toFloat() to top.toFloat(),0f to (top+height+1400*zoom),width.toFloat() to (top+height+1400*zoom))
            .map { unproject(it.first,it.second,camX,camY,zoom,width,top,height) }
        val minX=floor(bounds.minOf { it.first }-3).toInt().coerceAtLeast(0)
        val maxX=ceil(bounds.maxOf { it.first }+3).toInt().coerceAtMost(city.width-1)
        val minY=floor(bounds.minOf { it.second }-3).toInt().coerceAtLeast(0)
        val maxY=ceil(bounds.maxOf { it.second }+3).toInt().coerceAtMost(city.height-1)
        var pedestrianCount = 0
        for(y in minY..maxY) for(x in minX..maxX) {
            val sx=ox+(x-y)*256f*zoom;val sy=oy+(x+y)*128f*zoom
            if(sx+512*zoom<0 || sx>width || sy+256*zoom<top || sy-1400*zoom>top+height) continue
            val index=city.index(x,y);visible.add(index)
            val t=city.tileAt(x,y)
            if(sy<=top+height) {
                rect.set(sx,sy,sx+512*zoom,sy+256*zoom)
                paint.color=Color.WHITE;paint.alpha=255;paint.colorFilter=null
                c.drawBitmap(groundTile(city,x,y,sceneSample),null,rect,paint)
                if(t.terrain==Terrain.WATER && zoom>.055f && variant(x,y)%4==0) {
                    paint.color=0x407eaca9;paint.strokeWidth=.7f
                    val drift=sin(time*.8f+x*.4f+y)*7*zoom
                    c.drawLine(sx+190*zoom+drift,sy+120*zoom,sx+290*zoom+drift,sy+128*zoom,paint)
                }
            }
            model(city,x,y)?.let { id ->
                val shift=if(t.monument!=null) .5f else 0f
                items.add(Item(id,x+shift,y+shift,index,x+y+shift*2+.35f))
            }
            if (zoom > .045f && sy >= top-256*zoom && sy <= top+height+256*zoom && pedestrianCount < 240) {
                walkers.clear()
                Pedestrians.onTile(city,x,y,streetTime,walkers)
                for (p in walkers) {
                    items.add(Item(p.spriteId,p.x,p.y,-1,p.x+p.y+.015f))
                    pedestrianCount++
                }
            }
        }
        for(car in cars) {
            val p=car.progress-.5f;val lane=.16f
            val (dx,dy)=when(car.dir) { Vehicles.Dir.EAST -> p to lane;Vehicles.Dir.WEST -> -p to -lane;Vehicles.Dir.SOUTH -> -lane to p;Vehicles.Dir.NORTH -> lane to -p }
            val direction=when(car.dir) { Vehicles.Dir.EAST->0;Vehicles.Dir.WEST->2;Vehicles.Dir.SOUTH->1;Vehicles.Dir.NORTH->3 }
            items.add(Item("CAR_${car.kind.ordinal}_$direction",car.tx+dx,car.ty+dy,-1,car.tx+car.ty+dx+dy))
        }
        items.sortBy { it.depth }
        for(item in items) {
            val sx=ox+(item.x-item.y)*256*zoom+256*zoom
            val sy=oy+(item.x+item.y)*128*zoom+128*zoom
            if(sx< -1100*zoom || sx>width+1100*zoom || sy<top-256*zoom || sy>top+height+1400*zoom) continue
            val s=sprite(item.id,sceneSample) ?: continue
            rect.set(sx-s.ax*zoom,sy-s.ay*zoom,sx+(s.width-s.ax)*zoom,sy+(s.height-s.ay)*zoom)
            if(rect.bottom<top || rect.top>top+height || rect.right<0 || rect.left>width) continue
            paint.color=Color.WHITE;paint.alpha=255
            paint.colorFilter=if(item.id.startsWith("TERRAIN_")) null else modelFilter(city,item.id)
            c.drawBitmap(s.bitmap,null,rect,paint)
            paint.colorFilter=null
        }
        for(index in visible) {
            val x=index%city.width;val y=index/city.width
            val sx=ox+(x-y)*256f*zoom;val sy=oy+(x+y)*128f*zoom
            val t=city.tiles[index]
            val level=CityRenderer.levelOf(overlay,t)
            if(overlay!=CityRenderer.Overlay.NONE && level>0) {
                paint.color=(Palette.of(CityRenderer.colourOf(overlay,level)) and 0xffffff) or 0x99000000.toInt()
                diamond(c,sx,sy,zoom,false)
            }
            if(suggestOn && suggest?.invoke(x,y)==true) { paint.color=0xfff2d482.toInt();diamond(c,sx,sy,zoom,true) }
            if(index in selected) { paint.color=0xd114337a.toInt();diamond(c,sx,sy,zoom,false);paint.color=0xff12306e.toInt();diamond(c,sx,sy,zoom,true) }
        }
        if(highlight!=null && highlight.size>=2) for(dy in 0 until (highlight.getOrNull(2)?:1)) for(dx in 0 until (highlight.getOrNull(2)?:1)) {
            val x=highlight[0]+dx;val y=highlight[1]+dy
            paint.color=0xff12306e.toInt();diamond(c,ox+(x-y)*256*zoom,oy+(x+y)*128*zoom,zoom,true)
        }
        paint.alpha=255;paint.style=Paint.Style.FILL;c.restore()
    }

    private val grades=HashMap<Int,ColorMatrixColorFilter>()
    private fun modelFilter(city: City,id: String): ColorMatrixColorFilter? {
        if(city.style!=City.Style.CUSTOM) return null
        if(id.startsWith("CAR_") || id.startsWith("PERSON_") || id in landmarkIds || id=="GROVE" || id=="SAILBOAT") return null
        val slot=when { id.startsWith("ZONE_R")->CustomStyle.Slot.HOUSE_WALL;id.startsWith("ZONE_C")->CustomStyle.Slot.OFFICE_WALL;id.startsWith("ZONE_I")->CustomStyle.Slot.FACTORY_WALL;else->CustomStyle.Slot.GENERIC_WALL }
        val chosen=city.customStyle[slot]
        if(chosen==slot.default) return null
        return grades.getOrPut(chosen) {
            val color=Palette.of(chosen)
            val r=Color.red(color)/255f;val g=Color.green(color)/255f;val b=Color.blue(color)/255f
            val amount=.65f;val keep=1-amount
            ColorMatrixColorFilter(floatArrayOf(keep+.32f*r*amount,.6f*r*amount,.08f*r*amount,0f,0f,.32f*g*amount,keep+.6f*g*amount,.08f*g*amount,0f,0f,.32f*b*amount,.6f*b*amount,keep+.08f*b*amount,0f,0f,0f,0f,0f,1f,0f))
        }
    }

    private fun diamond(c: Canvas,x: Float,y: Float,z: Float,line: Boolean) {
        path.rewind();path.moveTo(x+256*z,y);path.lineTo(x+512*z,y+128*z);path.lineTo(x+256*z,y+256*z);path.lineTo(x,y+128*z);path.close()
        paint.style=if(line) Paint.Style.STROKE else Paint.Style.FILL;paint.strokeWidth=1.8f
        c.drawPath(path,paint);paint.style=Paint.Style.FILL
    }
    private fun groundTile(city: City,x: Int,y: Int,sample: Int): Bitmap {
        if (groundStyle != city.style) { ground.evictAll(); groundStyle = city.style }
        val t=city.tileAt(x,y)
        var mask=0
        if(t.kind.isTransport) for((i,d) in arrayOf(-1 to 0,1 to 0,0 to -1,0 to 1).withIndex()) {
            val neighbor=city.tileOrNull(x+d.first,y+d.second)?.kind
            if(neighbor!=null && (if(t.kind.isRoad) neighbor.isRoad else neighbor==t.kind)) mask=mask or (1 shl i)
        }
        val type=when { t.kind.isTransport->t.kind.name;t.kind.isZone && t.stage==0->t.kind.name;t.terrain==Terrain.WATER->"water";t.terrain==Terrain.SHORE->"sand";else->"grass" }
        val key="$type:$mask:$sample"
        ground.get(key)?.let { return it }
        return run {
            val b=Bitmap.createBitmap(512/sample,256/sample,Bitmap.Config.ARGB_8888);val c=Canvas(b)
            c.scale(1f/sample,1f/sample)
            val m=Matrix().apply { setPolyToPoly(floatArrayOf(0f,0f,512f,0f,0f,512f),0,floatArrayOf(256f,0f,512f,128f,0f,128f),0,3) }
            c.concat(m)
            // Cloth edging must stop at this diamond: neighbouring road patches join here.
            c.clipRect(0f,0f,512f,512f)
            val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            // Preserve the yarn's luminance and fibre detail while dyeing its colour.
            val dyeSlot = if(type=="water") "water" else "grass"
            if (type!="sand") {
                val col=appearance.color(city.style,dyeSlot)
                val r=Color.red(col)/150f;val g=Color.green(col)/150f;val b=Color.blue(col)/150f
                p.colorFilter=ColorMatrixColorFilter(floatArrayOf(.28f*r,.60f*r,.12f*r,0f,0f,.28f*g,.60f*g,.12f*g,0f,0f,.28f*b,.60f*b,.12f*b,0f,0f,0f,0f,0f,1f,0f))
            }
            if(type=="water") {
                val tex=textures.getOrPut("water") { assets.open("realistic/ground_water.webp").use { BitmapFactory.decodeStream(it)!! } }
                c.drawBitmap(tex,null,RectF(0f,0f,512f,512f),p)
            } else {
                val name=if(type=="sand") "sand" else "grass"
                val tex=textures.getOrPut(name) { assets.open("realistic/ground_$name.webp").use { BitmapFactory.decodeStream(it)!! } }
                c.drawBitmap(tex,null,RectF(0f,0f,512f,512f),p)
            }
            p.colorFilter=null
            // A running stitch around cut felt plots, drawn once into the ground cache.
            if(type=="grass" || type=="sand" || (t.kind.isZone && t.stage==0)) {
                p.style=Paint.Style.STROKE;p.strokeWidth=2.5f;p.color=0x65f4e6c7
                p.pathEffect=DashPathEffect(floatArrayOf(8f,7f),0f)
                c.drawRoundRect(11f,11f,501f,501f,16f,16f,p)
                p.pathEffect=null;p.style=Paint.Style.FILL
            }
            if(t.kind.isZone && t.stage==0) {
                p.color=when(t.kind) { TileKind.ZONE_R->0x5071aa67;TileKind.ZONE_C->0x506298b4;else->0x50d6bb68 };c.drawRect(0f,0f,512f,512f,p)
                p.style=Paint.Style.STROKE;p.strokeWidth=4f;p.color=0x99e4ddbe.toInt();c.drawRect(18f,18f,494f,494f,p);p.style=Paint.Style.FILL
            }
            if(t.kind.isTransport) {
                val road=Path();val track=t.kind.isTrack
                val links=if(mask==0) 3 else mask
                for(i in 0..3) if(links and (1 shl i)!=0) {
                    road.moveTo(256f,256f);road.lineTo(if(i==0) 0f else if(i==1) 512f else 256f,if(i==2) 0f else if(i==3) 512f else 256f)
                }
                val size=if(t.kind==TileKind.HIGHWAY || t.kind==TileKind.AVENUE) 330f else 265f
                p.style=Paint.Style.STROKE;p.strokeCap=Paint.Cap.ROUND;p.strokeJoin=Paint.Join.ROUND
                p.color=appearance.color(city.style,"paving");p.strokeWidth=size+68;c.drawPath(road,p)
                val asphalt=textures.getOrPut("asphalt") { assets.open("realistic/ground_asphalt.webp").use { BitmapFactory.decodeStream(it)!! } }
                p.shader=BitmapShader(asphalt,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT);p.strokeWidth=size;c.drawPath(road,p);p.shader=null
                p.strokeWidth=track.let { if(it) 9f else 5f };p.color=if(track) 0xffadb3b0.toInt() else 0xffe3d8b2.toInt()
                if(!track) p.pathEffect=DashPathEffect(floatArrayOf(32f,26f),0f)
                c.drawPath(road,p);p.pathEffect=null
                if (!track && t.kind!=TileKind.HIGHWAY && Integer.bitCount(mask)>=3) {
                    p.strokeWidth=9f;p.strokeCap=Paint.Cap.BUTT
                    for (i in 0..3) if (links and (1 shl i)!=0) {
                        for (stripe in -5..5) {
                            val across=256f+stripe*25f
                            // Continue the pavement line through the junction.
                            val crossing=if(t.kind==TileKind.AVENUE) .355f else .292f
                            val along=256f+(if(i==0 || i==2) -1 else 1)*crossing*512f
                            if(i<2)c.drawLine(along-18,across,along+18,across,p)
                            else c.drawLine(across,along-18,across,along+18,p)
                        }
                    }
                }
                if(track) { c.save();c.translate(30f,-30f);c.drawPath(road,p);c.restore();c.save();c.translate(-30f,30f);c.drawPath(road,p);c.restore() }
            }
            ground.put(key,b)
            b
        }
    }
    fun buildingAtScreen(city: City,camX: Float,camY: Float,zoom: Float,width: Int,top: Int,height: Int,px: Int,py: Int): Int? {
        val ox=width/2f-(camX-camY)*256*zoom;val oy=top+height/2f-(camX+camY)*128*zoom
        // The exact transparent sprite mask, in the same depth order as the last frame.
        for(item in items.asReversed()) {
            if(item.index<0 || !city.tiles[item.index].kind.isBuilding) continue
            val s=sprite(item.id,sceneSample) ?: continue
            val x=((px-ox)/zoom-(item.x-item.y)*256-256+s.ax).toInt()
            val y=((py-oy)/zoom-(item.x+item.y)*128-128+s.ay).toInt()
            if(x in 0 until s.width && y in 0 until s.height && Color.alpha(s.bitmap.getPixel(x*s.bitmap.width/s.width,y*s.bitmap.height/s.height))>80) return item.index
        }
        return null
    }
    private val iconCity by lazy { City(3,3) }
    fun drawTransportIcon(c: Canvas,kind: TileKind,bounds: RectF) {
        iconCity.style=groundStyle ?: City.Style.STANDARD
        for(tile in iconCity.tiles) { tile.kind=kind;tile.terrain=Terrain.LAND }
        val b=groundTile(iconCity,1,1,sampleFor(bounds.width()/512f*deviceScale(c)))
        paint.color=Color.WHITE;paint.alpha=255;paint.colorFilter=null
        c.drawBitmap(b,null,bounds,paint)
    }
    fun drawIcon(c: Canvas,id: String,bounds: RectF) {
        val model=appearance.model(groundStyle ?: City.Style.STANDARD,id)
        val m=metadata[model] ?: return
        val w=m.getInt("width");val h=m.getInt("height")
        val f=min(bounds.width()/w,bounds.height()/h)
        val s=sprite(model,sampleFor(f*deviceScale(c))) ?: return
        rect.set(bounds.centerX()-w*f/2,bounds.bottom-h*f,bounds.centerX()+w*f/2,bounds.bottom)
        paint.color=Color.WHITE;paint.alpha=255;paint.colorFilter=null;c.drawBitmap(s.bitmap,null,rect,paint)
    }
    companion object {
        private val landmarkIds=Monument.entries.map { it.name }.toSet()
        // Budget follows the app heap, not the phone's advertised total RAM.
        private val spriteBudget=(Runtime.getRuntime().maxMemory()/8).coerceIn(8L*1024*1024,32L*1024*1024).toInt()
        private val sharedSprites = object : LruCache<String, Sprite>(spriteBudget) {
            override fun sizeOf(key: String, value: Sprite) = value.bitmap.allocationByteCount
        }
        private val renderers=java.util.WeakHashMap<RealisticCityRenderer,Boolean>()
        /** Called on the main thread when the app is hidden or the OS requests memory. */
        fun trimMemory() {
            sharedSprites.evictAll()
            renderers.keys.toList().forEach { it.releaseCaches() }
        }
        fun cachedSpriteBytes(): Int = sharedSprites.size()
        fun spriteCacheLimitBytes(): Int = sharedSprites.maxSize()

        fun unproject(px: Float,py: Float,camX: Float,camY: Float,zoom: Float,width: Int,top: Int,height: Int): Pair<Float,Float> {
            val a=(px-width/2f)/(256*zoom)+camX-camY-1f
            val b=(py-top-height/2f)/(128*zoom)+camX+camY-1f
            return (a+b)/2 to (b-a)/2
        }
    }
}
