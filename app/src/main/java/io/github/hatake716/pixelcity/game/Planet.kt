package io.github.hatake716.pixelcity.game

import kotlin.math.*

/** Stable IDs, one terrain per individual city tile. */
enum class PlanetTerrain(val id: Int, val label: String, val description: String, val color: Int) {
    PLAIN(0,"平野","都市を建設できる平らな土地。",0xffa4c87c.toInt()),
    SEA(1,"海","青い海と柔らかな波。",0xff438eac.toInt()),
    RIVER(2,"川","大地を流れる水の道。",0xff6db4c0.toInt()),
    MOUNTAIN(3,"山","頂上に雪を頂く独立峰。",0xffa8a28c.toInt()),
    RANGE(4,"山脈","いくつもの峰が連なる山並み。",0xff909e9f.toInt()),
    VOLCANO(5,"火山","火口と溶岩を持つ火山。観賞用の地形です。",0xffb57969.toInt()),
    FOREST(6,"森林","幹と樹冠を持つ緑豊かな森。",0xff4b8767.toInt()),
    RAINFOREST(7,"熱帯雨林","多層の樹冠が重なる熱帯の森。",0xff3d9c72.toInt()),
    DESERT(8,"砂漠","風に刻まれた金色の砂丘。",0xffdab274.toInt()),
    SNOW(9,"雪原","白い雪に覆われた穏やかな丘。",0xffe5efeb.toInt()),
    GLACIER(10,"氷河","青白い氷の稜線と深い谷。",0xffaddce1.toInt());
    val water: Boolean get() = this==SEA || this==RIVER
}

/** A building is kept losslessly when terrain editing changes its tile to a non-buildable terrain. */
data class StoredBuilding(val kind: TileKind, val stage: Int, val monument: Monument?, val anchor: Int, val landfillFill: Int) {
    companion object { fun of(tile: Tile)=StoredBuilding(tile.kind,tile.stage,tile.monument,tile.monumentAnchor,tile.landfillFill) }
    fun restore(tile: Tile) { tile.kind=kind; tile.stage=stage; tile.monument=monument; tile.monumentAnchor=anchor; tile.landfillFill=landfillFill }
}

/** One continuous 512 × 512 city. Terrain editing never accesses the municipal budget. */
class Planet(val city: City, val seed: Long) {
    companion object {
        const val SIZE=512
        @Suppress("UNUSED_PARAMETER")
        fun initialTerrain(seed: Long): Array<PlanetTerrain> = Array(SIZE*SIZE) { EarthTerrain.biome(it) }
    }
    init { require(city.width==SIZE && city.height==SIZE) }
    val width get()=city.width
    val height get()=city.height
    val tileCount get()=city.tiles.size
    var earthVersion=EarthTerrain.VERSION
    var seabedVisible=false
    var introductionSeen=false
    var longitude=((city.originX+0.5f)/SIZE*2-1)*PI.toFloat()
    var latitude=asin(1-2*(city.originY+0.5f)/SIZE)
    var zoom=1f
    var selectedX=city.originX
    var selectedY=city.originY
    var revision=0
        private set
    var brushSize=1
    val selectedIndex get()=city.index(selectedX,selectedY)
    fun terrainAt(x: Int,y: Int)=city.tileAt(x,y).biome
    fun canBuild(x: Int,y: Int)=city.canBuildOn(x,y)
    fun select(x: Int,y: Int) { selectedX=x.coerceIn(0,SIZE-1); selectedY=y.coerceIn(0,SIZE-1) }
    data class State(val biome: PlanetTerrain,val ground: Terrain,val building: StoredBuilding,val stored: StoredBuilding?)
    private val undo=ArrayDeque<Map<Int,State>>()
    val canUndo get()=undo.isNotEmpty()
    private fun state(t: Tile)=State(t.biome,t.terrain,StoredBuilding.of(t),t.storedBuilding)

    /** Brush widths are measured in actual individual tiles, and wrap at the longitude seam. */
    fun brushIndices(x: Int,y: Int,size: Int=brushSize): Set<Int> {
        require(size in 1..32)
        val result=LinkedHashSet<Int>()
        val startX=x-size/2; val startY=y-size/2
        for(dy in 0 until size) for(dx in 0 until size) {
            val yy=startY+dy
            if(yy in 0 until SIZE) result.add(city.index(Math.floorMod(startX+dx,SIZE),yy))
        }
        return result
    }
    fun terraform(x: Int,y: Int,terrain: PlanetTerrain,size: Int=brushSize): Int {
        if(!city.inBounds(x,y)) return 0
        val indices=brushIndices(x,y,size).toMutableSet()
        // A 2 × 2 monument remains a single object, including while it is stored.
        for(i in indices.toList()) {
            val t=city.tiles[i]
            val anchor=t.monumentAnchor.takeIf { it>=0 } ?: t.storedBuilding?.anchor ?: -1
            if(anchor>=0) for(dy in 0..1) for(dx in 0..1) {
                val xx=anchor%SIZE+dx; val yy=anchor/SIZE+dy
                if(city.inBounds(xx,yy)) indices.add(city.index(xx,yy))
            }
        }
        val previous=LinkedHashMap<Int,State>()
        for(i in indices) {
            val t=city.tiles[i]
            if(t.biome==terrain && (terrain!=PlanetTerrain.PLAIN || t.terrain==Terrain.LAND)) continue
            previous[i]=state(t)
            if(terrain!=PlanetTerrain.PLAIN && t.kind!=TileKind.EMPTY) {
                t.storedBuilding=StoredBuilding.of(t); t.clearForBulldoze()
            }
            t.biome=terrain; t.terrain=if(terrain.water) Terrain.WATER else Terrain.LAND
            if(terrain==PlanetTerrain.PLAIN) { t.storedBuilding?.restore(t); t.storedBuilding=null }
        }
        if(previous.isNotEmpty()) {
            if(undo.size>=32) undo.removeFirst()
            undo.addLast(previous); revision++
        }
        return previous.size
    }
    fun undo(): Boolean {
        if(undo.isEmpty()) return false
        for((i,s) in undo.removeLast()) city.tiles[i].apply {
            biome=s.biome; terrain=s.ground; s.building.restore(this); storedBuilding=s.stored
        }
        revision++; return true
    }
}
