package io.github.hatake716.pixelcity.data

import android.content.Context
import io.github.hatake716.pixelcity.game.Planet
import io.github.hatake716.pixelcity.game.EarthTerrain
import io.github.hatake716.pixelcity.game.PlanetTerrain
import io.github.hatake716.pixelcity.game.Income
import io.github.hatake716.pixelcity.game.Spending
import io.github.hatake716.pixelcity.game.BuildCost
import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Monument
import io.github.hatake716.pixelcity.game.MonthlyStat
import io.github.hatake716.pixelcity.game.Ordinance
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind
import io.github.hatake716.pixelcity.game.Tutorial
import org.json.JSONArray
import org.json.JSONObject

/**
 * 都市の保存と復元。SharedPreferences に JSON で持つ。
 *
 * 保存先は [SLOT_COUNT] 個の「スロット」。遊んでいるスロットへ自動で保存し続け、
 * タイトルの「つづきから」で選んで再開する。別のスロットを使えば、
 * 前の街を消さずに新しい街を始められる。
 *
 * タイルは1文字ずつの文字列にまとめて、大きなマップでも小さく収まるようにしている。
 * [TileKind] と [Terrain] の固定 saveId を使い、v1〜v5 のデータも引き継ぐ。
 */
object SaveGame {
    private const val PREFS = "pixelcity"
    /** 保存できる街の数。 */
    const val SLOT_COUNT = 10
    private const val VERSION = 7

    /** 旧版（1スロットだけだったころ）の保存先。読み込んで引き継ぐために残す。 */
    private const val LEGACY_KEY = "city"

    private fun key(slot: Int) = "city_$slot"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, slot: Int, city: City, tutorial: Tutorial, seed: Long, planet: Planet? = null) {
        require(slot in 0 until SLOT_COUNT)
        prefs(context).edit().putString(key(slot), encode(city, tutorial, seed, planet)).apply()
    }

    fun encode(city: City, tutorial: Tutorial, seed: Long, planet: Planet? = null): String {
        val json=encodeCity(city,tutorial,seed)
        json.put("version",if(planet==null) 5 else VERSION)
        if(planet!=null) {
            require(planet.city===city)
            json.put("planet",JSONObject().apply {
                put("size",Planet.SIZE); put("intro",planet.introductionSeen)
                put("earthVersion",planet.earthVersion); put("seabed",planet.seabedVisible)
                put("longitude",planet.longitude.toDouble()); put("latitude",planet.latitude.toDouble()); put("zoom",planet.zoom.toDouble())
                put("x",planet.selectedX); put("y",planet.selectedY); put("brush",planet.brushSize)
            })
        }
        return json.toString()
    }

    private fun encodeCity(city: City, tutorial: Tutorial, seed: Long): JSONObject {
        val json = JSONObject().apply {
            put("version", VERSION)
            put("seed", seed)
            put("width", city.width)
            put("height", city.height)
            put("originX",city.originX); put("originY",city.originY)
            put("funds", city.funds)
            put("month", city.month)
            put("taxRate", city.taxRate)
            put("monthsInDebt", city.monthsInDebt)
            put("gameOver", city.gameOver)
            put("demand", JSONArray(listOf(city.demandR, city.demandC, city.demandI)))
            put("income", JSONArray(city.lastIncomeBreakdown.run { listOf(residential, commercial, industrial, tourism) }))
            put("spending", JSONArray(city.lastSpending.run { listOf(transport, power, water, garbage, safety, health, education, parks, ordinances) }))
            put("lastIncome", city.lastIncome)
            put("lastUpkeep", city.lastUpkeep)

            val terrain = StringBuilder(city.tiles.size)
            val kinds = StringBuilder(city.tiles.size)
            val stages = StringBuilder(city.tiles.size)
            for (t in city.tiles) {
                terrain.append(('0' + t.terrain.saveId))
                kinds.append(('0' + t.kind.saveId))
                stages.append(('0' + t.stage))
            }
            put("terrain", terrain.toString())
            put("kinds", kinds.toString())
            put("stages", stages.toString())
            put("biomes",String(CharArray(city.tiles.size) { 'a'+city.tiles[it].biome.id }))
            put("storedBuildings",JSONArray().apply {
                for((i,t) in city.tiles.withIndex()) t.storedBuilding?.let { b ->
                    put(JSONObject().apply {
                        put("i",i); put("k",b.kind.saveId); put("s",b.stage); put("a",b.anchor); put("f",b.landfillFill)
                        b.monument?.let { put("m",it.name) }
                    })
                }
            })

            // モニュメントは、本体タイルの索引と種類だけを持つ。
            val monuments = JSONArray()
            for ((i, t) in city.tiles.withIndex()) {
                val m = t.monument ?: continue
                monuments.put(JSONObject().apply {
                    put("i", i)
                    put("m", m.name)
                })
            }
            put("monuments", monuments)

            put("tutorial", JSONArray().apply { tutorial.saveState().forEach { put(it) } })
            put("campaign", JSONArray().apply { city.campaign.saveState().forEach { put(it) } })
            put("campaignEvent", city.campaign.lastEvent)

            // v2: 条例・災害の設定・溜まったゴミ・感染
            put("ordinances", JSONArray().apply { city.ordinances.forEach { put(it.name) } })
            put("disasterLevel", city.disasterLevel.name)
            put("style", city.style.name)
            put("localStyles", JSONArray().apply {
                for((index,style) in city.localStyles) put(JSONObject().put("i",index).put("style",style.name))
            })
            put("destinations", JSONArray().apply {
                for(d in city.destinations) put(JSONObject().put("name",d.name).put("region",d.region).put("x",d.x).put("y",d.y))
            })
            put("customStyle", JSONArray().apply { city.customStyle.save().forEach { put(it) } })
            put("garbageBacklog", city.garbageBacklog)
            put("infection", city.infection)
            // 埋立地の埋まり具合は、タイルごとに持つ
            val fills = JSONArray()
            for ((i, t) in city.tiles.withIndex()) {
                if (t.landfillFill > 0) {
                    fills.put(JSONObject().apply { put("i", i); put("f", t.landfillFill) })
                }
            }
            put("landfills", fills)
            // 推移の記録
            put("history", JSONArray().apply {
                for (h in city.history) {
                    put(JSONObject().apply {
                        put("m", h.month); put("p", h.population); put("f", h.funds)
                        put("b", h.balance); put("o", h.pollution); put("c", h.crime)
                        put("u", h.unemployment); put("h", h.health); put("t", h.traffic)
                    })
                }
            })

            // 一覧に出すための要約。街全体を読まずに済ませる。
            put("population", city.population)
            put("savedAt", System.currentTimeMillis())
        }
        return json
    }

    /** そのスロットに街があるか。 */
    fun hasSave(context: Context, slot: Int): Boolean = prefs(context).contains(key(slot))

    /** どれか1つでも街があるか。「つづきから」を出すかの判定に使う。 */
    fun hasAnySave(context: Context): Boolean =
        (0 until SLOT_COUNT).any { hasSave(context, it) }

    fun clear(context: Context, slot: Int) {
        prefs(context).edit().remove(key(slot)).apply()
    }

    /** 空いている最初のスロット。すべて埋まっていれば null。 */
    fun firstEmptySlot(context: Context): Int? =
        (0 until SLOT_COUNT).firstOrNull { !hasSave(context, it) }

    /**
     * 一覧に出すための、スロットの要約。
     * 街の中身は読まないので、10個ぶん集めても軽い。
     */
    data class SlotInfo(
        val slot: Int,
        val population: Int,
        val month: Int,
        val funds: Int,
        val savedAt: Long,
        val tutorialActive: Boolean,
        val readable: Boolean = true,
        val campaignLabel: String = "",
        val planetary: Boolean = false,
    ) {
        /** 「1905ねん7がつ」の形。 */
        val dateLabel: String get() = "${1900 + month / 12}年${month % 12 + 1}月"
    }

    /** 全スロットの要約。街のないスロットは null。 */
    fun listSlots(context: Context): List<SlotInfo?> =
        (0 until SLOT_COUNT).map { slotInfo(context, it) }

    fun slotInfo(context: Context, slot: Int): SlotInfo? {
        val raw = prefs(context).getString(key(slot), null) ?: return null
        return try {
            val json = JSONObject(raw)
            val ts = json.optJSONArray("tutorial")
            SlotInfo(
                slot = slot,
                population = json.optInt("population", 0),
                month = json.optInt("month", 0),
                funds = json.optInt("funds", 0),
                savedAt = json.optLong("savedAt", 0L),
                tutorialActive = ts != null && ts.length() > 0 && ts.optInt(0) == 1,
                planetary = json.optJSONObject("planet") != null,
                campaignLabel = json.optJSONArray("campaign")?.let { c ->
                    io.github.hatake716.pixelcity.game.Campaign.Route.entries.firstOrNull { it.id == c.optInt(2) }
                        ?.let { it.label + if(c.optInt(5)==1) "・完成" else "・第${c.optInt(3)+1}章" }
                }.orEmpty(),
            )
        } catch (e: Exception) {
            SlotInfo(slot, 0, 0, 0, 0L, false, readable = false)
        }
    }

    /** 読み込めなければ null。壊れた保存で落ちないようにする。 */
    fun load(context: Context, slot: Int): Loaded? {
        val raw = prefs(context).getString(key(slot), null) ?: return null
        return try {
            parse(raw)
        } catch (e: Exception) {
            // 将来の形式や破損データも、明示的な削除までは保持する。
            null
        }
    }

    /**
     * 旧版（1スロットだけ）の保存を、最初の空きスロットへ移す。
     * すでに移してあるか、旧版の保存がなければ何もしない。
     * これがないと、更新した人の街が消えてしまう。
     */
    fun migrateLegacySave(context: Context) {
        val p = prefs(context)
        val legacy = p.getString(LEGACY_KEY, null) ?: return
        val target = firstEmptySlot(context) ?: return
        p.edit().putString(key(target), legacy).remove(LEGACY_KEY).apply()
    }

    data class Loaded(val city: City, val tutorial: Tutorial, val seed: Long, val planet: Planet? = null)
    private data class RegionalSave(val city: City, val tutorial: Tutorial, val seed: Long)

    /** テストから直接呼べるように、文字列の解釈を分けてある。 */
    fun parse(raw: String): Loaded {
        val json=JSONObject(raw)
        val main=parseCity(json)
        val data=json.optJSONObject("planet")
        require(json.optInt("version",1)<6 || data!=null) { "Missing planet state" }
        val planet=if(data==null) null else Planet(main.city,main.seed).apply {
            require(data.getInt("size")==Planet.SIZE) { "Unsupported planet size" }
            require(json.optString("biomes","").length==main.city.tiles.size) { "Missing individual terrain tiles" }
            require(main.city.tiles.all { it.biome.water==(it.terrain==Terrain.WATER) &&
                (it.biome==PlanetTerrain.PLAIN || it.kind==TileKind.EMPTY) }) { "Contradictory planet terrain" }
            introductionSeen=data.optBoolean("intro",false)
            earthVersion=data.optInt("earthVersion",0)
            require(earthVersion in 0..EarthTerrain.VERSION) { "Unsupported Earth data version" }
            require(json.optInt("version",1)<7 || earthVersion==EarthTerrain.VERSION) { "Missing Earth data version" }
            seabedVisible=data.optBoolean("seabed",false)
            longitude=data.optDouble("longitude",0.0).toFloat().takeIf { it.isFinite() }?.coerceIn(-Math.PI.toFloat(),Math.PI.toFloat()) ?: 0f
            latitude=data.optDouble("latitude",0.0).toFloat().takeIf { it.isFinite() }?.coerceIn(-1.56f,1.56f) ?: 0f
            zoom=data.optDouble("zoom",1.0).toFloat().takeIf { it.isFinite() }?.coerceIn(0.75f,32f) ?: 1f
            select(data.optInt("x",Planet.SIZE/2),data.optInt("y",Planet.SIZE/2))
            brushSize=data.optInt("brush",1).coerceIn(1,32)
        }
        return Loaded(main.city,main.tutorial,main.seed,planet)
    }

    /** Upgrade only when opening a city in the app; the legacy codec remains independently usable. */
    fun expandToPlanet(loaded: Loaded, originX: Int=EarthTerrain.startX, originY: Int=EarthTerrain.startY): Loaded {
        if(loaded.planet?.earthVersion==EarthTerrain.VERSION) return loaded
        val old=loaded.city
        require(old.width<=Planet.SIZE && old.height<=Planet.SIZE)
        require(originX in 0 until Planet.SIZE && originY in 0 until Planet.SIZE)
        val ox=originX-old.originX; val oy=originY-old.originY
        val biomes=Planet.initialTerrain(loaded.seed)
        val terrain=CharArray(Planet.SIZE*Planet.SIZE) { if(biomes[it].water) '1' else '0' }
        val kinds=CharArray(terrain.size) { '0' }; val stages=CharArray(terrain.size) { '0' }
        fun newIndex(i: Int): Int {
            val y=i/old.width+oy
            return if(y in 0 until Planet.SIZE) y*Planet.SIZE+Math.floorMod(i%old.width+ox,Planet.SIZE) else -1
        }
        for((i,t) in old.tiles.withIndex()) {
            // Retain occupied urban plots, never stamp the old rectangular field or coastline.
            if(t.kind==TileKind.EMPTY && t.storedBuilding==null) continue
            val n=newIndex(i)
            if(n<0) continue
            val building=t.storedBuilding
            if((building?.kind ?: t.kind)==TileKind.MONUMENT) {
                val anchor=building?.anchor ?: t.monumentAnchor
                val mapped=if(anchor>=0) newIndex(anchor) else -1
                if(mapped<0 || mapped%Planet.SIZE==Planet.SIZE-1 || mapped/Planet.SIZE==Planet.SIZE-1) continue
            }
            terrain[n]='0'; kinds[n]='0'+(building?.kind ?: t.kind).saveId; stages[n]='0'+(building?.stage ?: t.stage)
            biomes[n]=PlanetTerrain.PLAIN
        }
        val json=encodeCity(old,loaded.tutorial,loaded.seed)
        json.put("version",5); json.put("width",Planet.SIZE); json.put("height",Planet.SIZE)
        json.put("originX",originX); json.put("originY",originY)
        json.put("terrain",String(terrain)); json.put("kinds",String(kinds)); json.put("stages",String(stages))
        json.put("biomes",String(CharArray(biomes.size) { 'a'+biomes[it].id }))
        for(key in listOf("monuments","landfills")) json.optJSONArray(key)?.let { a ->
            val mapped=JSONArray()
            for(i in 0 until a.length()) {
                val entry=a.getJSONObject(i); val n=newIndex(entry.getInt("i"))
                if(n>=0 && (key!="monuments" || n%Planet.SIZE<Planet.SIZE-1 && n/Planet.SIZE<Planet.SIZE-1)) {
                    entry.put("i",n); mapped.put(entry)
                }
            }
            json.put(key,mapped)
        }
        // Restored hidden buildings are now ordinary plots on the regenerated landscape.
        val monuments=json.getJSONArray("monuments")
        for((i,t) in old.tiles.withIndex()) t.storedBuilding?.monument?.let { m ->
            val n=newIndex(i)
            if(n>=0 && n%Planet.SIZE<Planet.SIZE-1 && n/Planet.SIZE<Planet.SIZE-1)
                monuments.put(JSONObject().put("i",n).put("m",m.name))
        }
        val fills=json.getJSONArray("landfills")
        for((i,t) in old.tiles.withIndex()) t.storedBuilding?.let { building ->
            val n=newIndex(i)
            if(n>=0 && building.landfillFill>0) fills.put(JSONObject().put("i",n).put("f",building.landfillFill))
        }
        json.put("storedBuildings",JSONArray())
        val expanded=parseCity(json)
        return Loaded(expanded.city,expanded.tutorial,expanded.seed,Planet(expanded.city,expanded.seed).apply {
            introductionSeen=loaded.planet?.introductionSeen ?: false
        })
    }

    private fun parseCity(json: JSONObject): RegionalSave {
        require(json.optInt("version", 1) in 1..VERSION) { "Unsupported save version" }
        val width = json.getInt("width")
        val height = json.getInt("height")
        require(width in 1..Planet.SIZE && height in 1..Planet.SIZE) { "Invalid map size" }
        val city = City(width, height)
        city.originX=json.optInt("originX",width/2).coerceIn(0,width-1)
        city.originY=json.optInt("originY",height/2).coerceIn(0,height-1)
        city.funds = json.getInt("funds")
        city.month = json.getInt("month")
        city.taxRate = json.getInt("taxRate")
        city.monthsInDebt = json.optInt("monthsInDebt", 0)
        city.gameOver = json.optBoolean("gameOver", false)

        val terrain = json.getString("terrain")
        val kinds = json.getString("kinds")
        val stages = json.getString("stages")
        require(terrain.length == city.tiles.size && kinds.length == city.tiles.size && stages.length == city.tiles.size) { "Incomplete map" }
        val biomes=json.optString("biomes","")
        require(biomes.isEmpty() || biomes.length==city.tiles.size) { "Incomplete planet terrain" }
        val terrainValues = Terrain.entries.associateBy { it.saveId }
        val kindValues = TileKind.entries.associateBy { it.saveId }
        for (i in city.tiles.indices) {
            val t = city.tiles[i]
            t.terrain = requireNotNull(terrainValues[terrain[i] - '0']) { "Unknown terrain" }
            t.kind = requireNotNull(kindValues[kinds[i] - '0']) { "Unknown building" }
            t.stage = (stages[i] - '0').coerceIn(0, 3)
            if(biomes.isNotEmpty()) {
                t.biome=requireNotNull(PlanetTerrain.entries.firstOrNull { it.id==biomes[i]-'a' }) { "Unknown planet terrain" }
                require(t.biome==PlanetTerrain.PLAIN || t.kind==TileKind.EMPTY) { "Building on non-plain terrain" }
            }
        }

        val monuments = json.optJSONArray("monuments")
        if (monuments != null) {
            for (n in 0 until monuments.length()) {
                val entry = monuments.getJSONObject(n)
                val anchor = entry.getInt("i")
                val m = Monument.entries.firstOrNull { it.name == entry.getString("m") } ?: continue
                require(anchor in city.tiles.indices) { "Invalid monument anchor" }
                val ax = anchor % width
                val ay = anchor / width
                for (dy in 0..1) for (dx in 0..1) {
                    if (!city.inBounds(ax + dx, ay + dy)) continue
                    val tile = city.tileAt(ax + dx, ay + dy)
                    tile.kind = TileKind.MONUMENT
                    tile.monumentAnchor = anchor
                }
                city.tiles[anchor].monument = m
                city.builtMonuments.add(m)
            }
        }

        json.optJSONArray("storedBuildings")?.let { a ->
            for(n in 0 until a.length()) {
                val e=a.getJSONObject(n); val i=e.getInt("i")
                require(i in city.tiles.indices) { "Invalid stored building" }
                val kind=requireNotNull(kindValues[e.getInt("k")]) { "Unknown stored building" }
                val m=e.optString("m","").takeIf { it.isNotEmpty() }?.let { name ->
                    requireNotNull(Monument.entries.firstOrNull { it.name==name }) { "Unknown stored monument" }
                }
                val anchor=e.optInt("a",-1)
                require(anchor==-1 || anchor in city.tiles.indices) { "Invalid stored monument anchor" }
                val tile=city.tiles[i]
                require(tile.kind==TileKind.EMPTY && tile.biome!=PlanetTerrain.PLAIN && tile.storedBuilding==null) { "Conflicting stored building" }
                tile.storedBuilding=io.github.hatake716.pixelcity.game.StoredBuilding(kind,e.getInt("s").coerceIn(0,3),m,anchor,e.optInt("f",0))
                m?.let { city.builtMonuments.add(it) }
            }
        }

        val tutorial = Tutorial()
        val ts = json.optJSONArray("tutorial")
        if (ts != null) {
            tutorial.restore(IntArray(ts.length()) { ts.getInt(it) })
        }

        // v2 の状態。古いセーブには入っていないので、なければ既定値のまま。
        json.optJSONArray("ordinances")?.let { arr ->
            for (n in 0 until arr.length()) {
                Ordinance.entries.firstOrNull { it.name == arr.getString(n) }
                    ?.let { city.ordinances.add(it) }
            }
        }
        json.optString("disasterLevel", "").takeIf { it.isNotEmpty() }?.let { name ->
            City.DisasterLevel.entries.firstOrNull { it.name == name }
                ?.let { city.disasterLevel = it }
        }
        json.optJSONArray("customStyle")?.let { arr ->
            city.customStyle.restore(IntArray(arr.length()) { arr.getInt(it) })
        }
        json.optString("style", "").takeIf { it.isNotEmpty() }?.let { name ->
            City.Style.entries.firstOrNull { it.name == name }?.let { city.style = it }
        }
        json.optJSONArray("localStyles")?.let { arr ->
            for(n in 0 until minOf(arr.length(),city.tiles.size)) {
                val e=arr.optJSONObject(n) ?: continue
                val i=e.optInt("i",-1)
                val style=City.Style.entries.firstOrNull { it.name==e.optString("style") } ?: continue
                if(i in city.tiles.indices) city.localStyles[i]=style
            }
        }
        json.optJSONArray("destinations")?.let { arr ->
            for(n in 0 until minOf(arr.length(),32)) {
                val e=arr.optJSONObject(n) ?: continue
                val x=e.optInt("x",-1);val y=e.optInt("y",-1)
                val name=e.optString("name").take(80);val region=e.optString("region").take(80)
                if(city.inBounds(x,y) && name.isNotBlank()) city.destinations.add(City.Destination(name,region,x,y))
            }
        }
        city.garbageBacklog = json.optInt("garbageBacklog", 0).coerceIn(0, City.GARBAGE_BACKLOG_MAX)
        city.infection = json.optInt("infection", 0).coerceIn(0, 100)
        json.optJSONArray("landfills")?.let { arr ->
            for (n in 0 until arr.length()) {
                val e = arr.getJSONObject(n)
                val i = e.getInt("i")
                if (i in city.tiles.indices) city.tiles[i].landfillFill = e.getInt("f").coerceIn(0, BuildCost.LANDFILL_TOTAL)
            }
        }
        json.optJSONArray("history")?.let { arr ->
            for (n in 0 until arr.length()) {
                val e = arr.getJSONObject(n)
                city.history.add(
                    MonthlyStat(
                        month = e.optInt("m"), population = e.optInt("p"),
                        funds = e.optInt("f"), balance = e.optInt("b"),
                        pollution = e.optInt("o"), crime = e.optInt("c"),
                        unemployment = e.optInt("u"), health = e.optInt("h"),
                        traffic = e.optInt("t"),
                    ),
                )
            }
        }

        // 人口などはタイルから導かれる値なので、保存せずに組み直す。
        require(json.optInt("version", 1) < 5 || json.optJSONArray("campaign") != null) { "Missing campaign state" }
        json.optJSONArray("campaign")?.let { arr ->
            city.campaign.restore(IntArray(arr.length()) { arr.getInt(it) }, json.optString("campaignEvent", ""))
        }
        city.recomputeDerivedState()
        city.refreshBudgetForecast()
        json.optJSONArray("demand")?.let {
            city.demandR = it.optInt(0).coerceIn(-100, 100)
            city.demandC = it.optInt(1).coerceIn(-100, 100)
            city.demandI = it.optInt(2).coerceIn(-100, 100)
        }
        json.optJSONArray("income")?.let { city.lastIncomeBreakdown = Income(it.optInt(0), it.optInt(1), it.optInt(2), it.optInt(3)) }
        json.optJSONArray("spending")?.let { city.lastSpending = Spending(it.optInt(0), it.optInt(1), it.optInt(2), it.optInt(3), it.optInt(4), it.optInt(5), it.optInt(6), it.optInt(7), it.optInt(8)) }
        city.lastIncome = json.optInt("lastIncome", city.lastIncomeBreakdown.total)
        city.lastUpkeep = json.optInt("lastUpkeep", city.lastSpending.total)
        if (city.history.size > City.HISTORY_MONTHS) city.history.subList(0, city.history.size - City.HISTORY_MONTHS).clear()

        return RegionalSave(city, tutorial, json.optLong("seed", 0L))
    }
}
