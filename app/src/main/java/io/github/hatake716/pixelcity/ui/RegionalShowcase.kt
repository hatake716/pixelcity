package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.data.SaveGame
import io.github.hatake716.pixelcity.game.*
import java.util.ArrayDeque
import kotlin.math.*
import kotlin.random.Random

/** New examples grow along a connected piece of actual land. Existing saves are never rebuilt. */
object RegionalShowcase {
    const val WORLD_LABEL="発展した世界"
    const val WORLD_SEED=2026091304L
    const val WORLD_SUMMARY="港町・大都市・田園都市・工業都市が、一つの惑星に共存する世界。資金と市政を共有し、4つの街の続きを育てられます。画面上の「都市移動」から各地へ移動できます。"

    /** Allocate one Earth, then copy only occupied plots from the four small regional plans. */
    fun buildWorld(): SaveGame.Loaded {
        val plans=ShowcaseCity.Kind.entries.map(::build)
        val first=plans.first()
        val loaded=SaveGame.expandToPlanet(SaveGame.Loaded(first.city,Tutorial().apply { skip() },WORLD_SEED),first.originX,first.originY)
        val c=loaded.city
        c.style=City.Style.STANDARD
        c.funds=plans.sumOf { it.city.funds }
        for((order,plan) in plans.withIndex()) {
            val source=plan.city
            fun index(i: Int)=(plan.region.top+i/source.width)*Planet.SIZE+plan.region.left+i%source.width
            for((i,tile) in source.tiles.withIndex()) if(tile.kind!=TileKind.EMPTY) {
                val target=index(i)
                if(order>0) {
                    check(c.tiles[target].kind==TileKind.EMPTY) { "Overlapping showcase plot: $target" }
                    StoredBuilding.of(tile).copy(anchor=if(tile.monumentAnchor>=0) index(tile.monumentAnchor) else -1).restore(c.tiles[target])
                    c.tiles[target].biome=PlanetTerrain.PLAIN;c.tiles[target].terrain=Terrain.LAND
                }
                c.localStyles[target]=source.style
            }
            c.builtMonuments.addAll(source.builtMonuments)
            c.destinations.add(City.Destination(plan.kind.label,plan.region.label,plan.originX,plan.originY))
        }
        c.recomputeDerivedState();c.refreshBudgetForecast()
        loaded.planet!!.apply { introductionSeen=true;zoom=1f }
        return loaded
    }
    data class Region(val label: String,val west: Double,val east: Double,val south: Double,val north: Double,
                      val longitude: Double,val latitude: Double,val waypoints: List<Pair<Double,Double>>,val radius: Int) {
        val left get()=EarthTerrain.index(west,north)%512
        val top get()=EarthTerrain.index(west,north)/512
        val right get()=EarthTerrain.index(east,south)%512
        val bottom get()=EarthTerrain.index(east,south)/512
    }
    fun region(kind: ShowcaseCity.Kind): Region=when(kind) {
        ShowcaseCity.Kind.HARBOUR -> Region("イギリス",-6.4,2.0,50.0,58.6,-1.6,52.6,
            listOf(-3.0 to 53.4,-.2 to 51.5,-4.0 to 50.7,-1.0 to 54.0),2)
        ShowcaseCity.Kind.METROPOLIS -> Region("アメリカ東海岸",-81.5,-70.0,35.0,44.0,-75.1,40.0,
            listOf(-71.1 to 42.3,-74.0 to 40.7,-77.0 to 38.9,-77.4 to 37.6,-78.6 to 35.9),4)
        ShowcaseCity.Kind.GARDEN -> Region("ヨーロッパ大陸",-2.0,12.0,46.0,52.5,4.5,49.2,
            listOf(2.4 to 48.9,4.5 to 50.6,7.0 to 50.5,8.5 to 49.1,5.0 to 47.3),4)
        ShowcaseCity.Kind.INDUSTRIAL -> Region("中国大陸",112.0,123.0,28.0,35.0,118.5,32.1,
            listOf(114.4 to 30.7,117.3 to 32.9,120.1 to 31.6,121.4 to 31.2),4)
    }
    /** Small preview uses this crop; only the chosen example allocates a full planet. */
    data class Plan(val city: City,val region: Region,val kind: ShowcaseCity.Kind) {
        val originX get()=region.left+city.originX
        val originY get()=region.top+city.originY
        fun expand(): SaveGame.Loaded = SaveGame.expandToPlanet(
            SaveGame.Loaded(city,Tutorial().apply { skip() },kind.seed),originX,originY).also { it.planet!!.zoom=24f }
    }
    fun build(kind: ShowcaseCity.Kind): Plan {
        val region=region(kind);val c=City(region.right-region.left+1,region.bottom-region.top+1)
        fun global(i: Int)=(region.top+i/c.width)*512+region.left+i%c.width
        fun neighbors(i: Int): List<Int> {
            val x=i%c.width;val y=i/c.width
            return listOf(x-1 to y,x+1 to y,x to y-1,x to y+1).mapNotNull { (xx,yy) -> if(c.inBounds(xx,yy)) c.index(xx,yy) else null }
        }
        val eligible=BooleanArray(c.tiles.size)
        for(i in c.tiles.indices) {
            val g=global(i);val b=EarthTerrain.biome(g)
            c.tiles[i].biome=b;c.tiles[i].terrain=if(b.water) Terrain.WATER else Terrain.LAND
            val lon=((g%512+.5)/512-.5)*360;val lat=Math.toDegrees(asin(1-2*(g/512+.5)/512))
            // The coarse source grid can join across the Channel. Keep each bank in its own region.
            val bank=when(kind) {
                ShowcaseCity.Kind.HARBOUR -> lat >= if(lon>-.8) 50.9 else 50.0
                ShowcaseCity.Kind.GARDEN -> lon>=3.0 || lat<=50.5
                else -> true
            }
            eligible[i]=bank && b in setOf(PlanetTerrain.PLAIN,PlanetTerrain.FOREST,PlanetTerrain.RAINFOREST,PlanetTerrain.DESERT) && EarthTerrain.meters(g)<=900
        }
        fun distance(i: Int,g: Int): Int {
            val dx=region.left+i%c.width-g%512;val dy=region.top+i/c.width-g/512
            return dx*dx+dy*dy
        }
        val center=EarthTerrain.index(region.longitude,region.latitude)
        val root=c.tiles.indices.filter { eligible[it] }.minBy { distance(it,center) }
        // Islands and water-separated pockets cannot become disconnected suburbs.
        val land=BooleanArray(c.tiles.size);val queue=ArrayDeque<Int>();land[root]=true;queue.add(root)
        while(queue.isNotEmpty()) for(n in neighbors(queue.removeFirst())) if(eligible[n] && !land[n]) { land[n]=true;queue.add(n) }
        fun nearest(lon: Double,lat: Double)=c.tiles.indices.filter { land[it] }.minBy { distance(it,EarthTerrain.index(lon,lat)) }
        fun place(i: Int,k: TileKind,stage: Int=1) {
            check(land[i]);c.tiles[i].apply { biome=PlanetTerrain.PLAIN;terrain=Terrain.LAND;this.kind=k;this.stage=stage }
        }
        c.originX=root%c.width;c.originY=root/c.width
        place(root,TileKind.AVENUE)
        fun connect(target: Int,allowed: BooleanArray=land) {
            if(c.tiles[target].kind.isTransport) return
            val previous=IntArray(c.tiles.size) { -2 };val q=ArrayDeque<Int>();q.add(target);previous[target]=-1;var end=-1
            while(q.isNotEmpty()) {
                val i=q.removeFirst()
                if(c.tiles[i].kind.isTransport) { end=i;break }
                for(n in neighbors(i).sortedBy { EarthTerrain.meters(global(it)) }) if(allowed[n] && previous[n]==-2) { previous[n]=i;q.add(n) }
            }
            check(end>=0) { "Disconnected regional street" }
            var p=previous[end]
            while(p>=0) { place(p,TileKind.ROAD);p=previous[p] }
        }
        for((lon,lat) in region.waypoints) connect(nearest(lon,lat))
        fun coastal(i: Int): Boolean {
            val g=global(i)
            return intArrayOf(g-1,g+1,g-512,g+512).any { it in 0 until 512*512 && EarthTerrain.biome(it).water }
        }
        val port=if(kind==ShowcaseCity.Kind.HARBOUR) {
            c.tiles.indices.filter { land[it] && coastal(it) }.minBy { distance(it,EarthTerrain.index(-3.0,53.4)) }.also { connect(it) }
        } else null
        // Grow a neighbourhood around the trunk roads, rather than stamping a rectangular template.
        val dist=IntArray(c.tiles.size) { Int.MAX_VALUE };queue.clear()
        for(i in c.tiles.indices) if(c.tiles[i].kind.isTransport) { dist[i]=0;queue.add(i) }
        while(queue.isNotEmpty()) {
            val i=queue.removeFirst()
            for(n in neighbors(i)) if(land[n] && dist[n]>dist[i]+1) { dist[n]=dist[i]+1;queue.add(n) }
        }
        val footprint=BooleanArray(c.tiles.size) { land[it] && dist[it]<=region.radius }
        val spacing=if(kind==ShowcaseCity.Kind.GARDEN) 4 else 3
        val junctions=c.tiles.indices.filter { footprint[it] && it/c.width%spacing==1 && (it%c.width+(it/c.width/spacing)%2)%spacing==1 }
        for(i in junctions.sortedBy { distance(it,center) }) connect(i,footprint)
        // Port is the terminal of a connected land route; its adjacent sea remains untouched.
        port?.let { place(it,TileKind.SEAPORT) }
        val lots=c.tiles.indices.filter { footprint[it] && c.tiles[it].kind==TileKind.EMPTY && neighbors(it).any { n -> c.tiles[n].kind.isTransport } }
        fun free()=lots.filter { c.tiles[it].kind==TileKind.EMPTY }
        fun blockDistance(a: Int,b: Int)=abs(a%c.width-b%c.width)+abs(a/c.width-b/c.width)
        fun service(k: TileKind,count: Int=1) {
            repeat(count) {
                val peers=lots.filter { c.tiles[it].kind==k }
                val i=free().minByOrNull { tile ->
                    if(k==TileKind.WATER_PLANT)
                        -lots.count { p -> peers.none { blockDistance(p,it)<=14 } && blockDistance(p,tile)<=14 }*1000000.0+distance(tile,center)
                    else if(kind==ShowcaseCity.Kind.INDUSTRIAL && k in setOf(TileKind.POWER_COAL,TileKind.INCINERATOR,TileKind.SEWAGE_PLANT))
                        -(tile/c.width)*1000.0 + distance(tile,center)
                    else if(peers.isEmpty()) distance(tile,center).toDouble()
                    else -peers.minOf { p -> (tile%c.width-p%c.width).toDouble().pow(2)+(tile/c.width-p/c.width).toDouble().pow(2) }
                } ?: return
                place(i,k)
            }
        }
        val scale=(lots.size/55.0).coerceAtLeast(1.0)
        val powerAllowance=if(kind==ShowcaseCity.Kind.GARDEN) 24.0 else 40.0
        val plant=if(kind==ShowcaseCity.Kind.INDUSTRIAL) TileKind.POWER_COAL else TileKind.POWER_SOLAR
        val windOutput=if(kind==ShowcaseCity.Kind.GARDEN) 2*BuildCost.powerOutput(TileKind.POWER_WIND) else 0
        service(plant,ceil((lots.size*powerAllowance-windOutput)/BuildCost.powerOutput(plant)).toInt().coerceAtLeast(1))
        if(kind==ShowcaseCity.Kind.GARDEN) service(TileKind.POWER_WIND,2)
        service(TileKind.WATER_PLANT,ceil(lots.size*34.0/4000).toInt().coerceAtLeast(2))
        service(TileKind.SEWAGE_PLANT,1)
        val disposal=if(kind==ShowcaseCity.Kind.INDUSTRIAL) TileKind.INCINERATOR else TileKind.RECYCLING
        service(disposal,ceil(lots.size*(if(kind==ShowcaseCity.Kind.GARDEN) 2.5 else 6.0)/BuildCost.garbageCapacity(disposal)).toInt())
        for(k in listOf(TileKind.SCHOOL,TileKind.CLINIC,TileKind.POLICE,TileKind.FIRE,TileKind.BUS_STOP)) service(k,ceil(scale/2).toInt())
        // Water is delivered within a Manhattan radius, even when roads cross a long coastal region.
        while(true) {
            val water=lots.filter { c.tiles[it].kind in setOf(TileKind.WATER_PLANT,TileKind.WATER_TOWER) }
            val unserved=free().filter { p -> water.none { blockDistance(p,it)<=if(c.tiles[it].kind==TileKind.WATER_PLANT) 14 else 10 } }
            if(unserved.isEmpty()) break
            val tower=free().maxBy { i -> unserved.count { blockDistance(it,i)<=10 } }
            place(tower,TileKind.WATER_TOWER)
        }
        // Place the local landmark only where all four original cells are land and roads stay intact.
        val monument=when(kind) { ShowcaseCity.Kind.HARBOUR->Monument.BIG_BEN;ShowcaseCity.Kind.METROPOLIS->Monument.STATUE_OF_LIBERTY;ShowcaseCity.Kind.GARDEN->Monument.ARC_DE_TRIOMPHE;else->null }
        if(monument!=null) {
            val anchor=free().filter { i -> i%c.width<c.width-1 && i/c.width<c.height-1 &&
                listOf(i,i+1,i+c.width,i+c.width+1).all { land[it] && footprint[it] && c.tiles[it].kind==TileKind.EMPTY }
            }.minByOrNull { distance(it,center) }
            anchor?.let { a ->
                for(i in listOf(a,a+1,a+c.width,a+c.width+1)) { place(i,TileKind.MONUMENT);c.tiles[i].monumentAnchor=a }
                c.tiles[a].monument=monument;c.builtMonuments.add(monument)
            }
        }
        // Keep homes upstream of the industrial district, with shops and a green buffer between them.
        val zoned=free().shuffled(Random(kind.seed)).let { if(kind==ShowcaseCity.Kind.INDUSTRIAL) it.sortedBy { i -> i/c.width } else it }
        for((order,i) in zoned.withIndex()) {
            val n=order*100/zoned.size
            val k=when(kind) {
                ShowcaseCity.Kind.HARBOUR -> when { n<53->TileKind.ZONE_R;n<77->TileKind.ZONE_C;n<85->TileKind.ZONE_I;else->TileKind.PARK }
                ShowcaseCity.Kind.METROPOLIS -> when { n<51->TileKind.ZONE_R;n<78->TileKind.ZONE_C;n<93->TileKind.ZONE_I;else->TileKind.PARK }
                ShowcaseCity.Kind.GARDEN -> when { n<36->TileKind.ZONE_R;n<54->TileKind.ZONE_C;n<61->TileKind.ZONE_I;n<85->TileKind.FARM;else->TileKind.PARK }
                ShowcaseCity.Kind.INDUSTRIAL -> when { n<32->TileKind.ZONE_R;n<50->TileKind.ZONE_C;n<57->TileKind.PARK;else->TileKind.ZONE_I }
            }
            place(i,k,if(k.isZone) (if(kind==ShowcaseCity.Kind.GARDEN) 2 else 3) else 1)
        }
        if(kind==ShowcaseCity.Kind.GARDEN || kind==ShowcaseCity.Kind.INDUSTRIAL) {
            // Follow the existing connected streets, keeping rail on land and on the same service network.
            val line=c.tiles.indices.filter { c.tiles[it].kind==TileKind.ROAD && it%c.width==root%c.width }
            for(i in line) place(i,TileKind.RAIL)
        }
        c.style=when(kind) { ShowcaseCity.Kind.HARBOUR->City.Style.EUROPE;ShowcaseCity.Kind.METROPOLIS->City.Style.PRIME;ShowcaseCity.Kind.GARDEN->City.Style.RURAL;ShowcaseCity.Kind.INDUSTRIAL->City.Style.GRITTY }
        c.funds=when(kind) { ShowcaseCity.Kind.HARBOUR->100000;ShowcaseCity.Kind.METROPOLIS->250000;ShowcaseCity.Kind.GARDEN->180000;ShowcaseCity.Kind.INDUSTRIAL->600000 }
        c.month=120;c.taxRate=if(kind==ShowcaseCity.Kind.INDUSTRIAL) 10 else 9;c.ordinances.add(Ordinance.RECYCLING)
        c.recomputeDerivedState();c.refreshBudgetForecast()
        return Plan(c,region,kind)
    }
}
