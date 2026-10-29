package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.CustomStyle
import io.github.hatake716.pixelcity.game.TileKind


/** オリジナル建築の共有キャッシュと配色。形状は [Architecture] で描く。 */
object IsoBuildings {

    /**
     * 色を入れ替えた同じ絵を作る。
     *
     * 街の様式ごとに、同じ建物を別の色で見せるために使う。
     * 形はそのままなので、絵を描き直す必要がない。
     */
    private fun recolour(src: Sprite, map: Map<Int, Int>): Sprite {
        val data = ByteArray(src.data.size)
        for (i in src.data.indices) {
            val v = src.data[i]
            data[i] = if (v == Pix.TRANSPARENT) v
            else (map[v.toInt()] ?: v.toInt()).toByte()
        }
        return Sprite(src.width, src.height, data)
    }

    /** 田園都市の色づかい。こげ茶の瓦と、生成りの漆喰。 */
    private val RURAL_MAP = mapOf(
        Palette.HOUSE_ROOF to Palette.RURAL_ROOF,
        Palette.HOUSE_ROOF_DARK to Palette.RURAL_ROOF_DARK,
        Palette.HOUSE_LEFT to Palette.RURAL_WALL,
        Palette.HOUSE_RIGHT to Palette.RURAL_WALL_DARK,
        Palette.RED_DARK to Palette.RURAL_ROOF_DARK,
        Palette.WALL_ROOF to Palette.RURAL_WALL,
        Palette.OFFICE_ROOF to Palette.RURAL_ROOF,
        Palette.OFFICE_LEFT to Palette.RURAL_WALL,
        Palette.OFFICE_RIGHT to Palette.RURAL_WALL_DARK,
        Palette.GLASS to Palette.RURAL_WALL_DARK,
        Palette.GLASS_LIT to Palette.RURAL_WALL,
    )

    /** 工業都市の色づかい。すすけたレンガとトタン。 */
    private val GRITTY_MAP = mapOf(
        Palette.HOUSE_ROOF to Palette.GRIT_ROOF,
        Palette.HOUSE_ROOF_DARK to Palette.GRIT_ROOF_DARK,
        Palette.HOUSE_LEFT to Palette.GRIT_WALL,
        Palette.HOUSE_RIGHT to Palette.GRIT_WALL_DARK,
        Palette.OFFICE_ROOF to Palette.GRIT_WALL,
        Palette.OFFICE_LEFT to Palette.GRIT_WALL,
        Palette.OFFICE_RIGHT to Palette.GRIT_WALL_DARK,
        Palette.GLASS to Palette.GRIT_WALL_DARK,
        Palette.GLASS_LIT to Palette.GRIT_WALL,
        Palette.WALL_ROOF to Palette.GRIT_WALL,
    )

    /** 大都市の色づかい。白い石と、青みの強いガラス。 */
    private val PRIME_MAP = mapOf(
        Palette.OFFICE_ROOF to Palette.PRIME_ROOF,
        Palette.OFFICE_LEFT to Palette.PRIME_LEFT,
        Palette.OFFICE_RIGHT to Palette.PRIME_RIGHT,
        Palette.GLASS to Palette.PRIME_GLASS,
        Palette.GLASS_LIT to Palette.GLASS_LIT,
        Palette.HOUSE_LEFT to Palette.PRIME_ROOF,
        Palette.HOUSE_RIGHT to Palette.PRIME_LEFT,
    )

    /** 古代都市。日干しレンガと石灰岩。屋根も壁も砂の色にそろえる。 */
    private val ANCIENT_MAP = mapOf(
        Palette.HOUSE_ROOF to Palette.ANCIENT_ROOF,
        Palette.HOUSE_ROOF_DARK to Palette.ANCIENT_ROOF_DARK,
        Palette.HOUSE_LEFT to Palette.ANCIENT_WALL,
        Palette.HOUSE_RIGHT to Palette.ANCIENT_WALL_DARK,
        Palette.RED_DARK to Palette.ANCIENT_ROOF_DARK,
        Palette.OFFICE_ROOF to Palette.ANCIENT_ROOF,
        Palette.OFFICE_LEFT to Palette.ANCIENT_WALL,
        Palette.OFFICE_RIGHT to Palette.ANCIENT_WALL_DARK,
        Palette.GLASS to Palette.ANCIENT_WALL_DARK,
        Palette.GLASS_LIT to Palette.ANCIENT_WALL,
        Palette.WALL_ROOF to Palette.ANCIENT_ROOF,
        Palette.WALL_LEFT to Palette.ANCIENT_WALL,
        Palette.WALL_RIGHT to Palette.ANCIENT_WALL_DARK,
        Palette.FACTORY_ROOF to Palette.ANCIENT_ROOF_DARK,
        Palette.FACTORY_LEFT to Palette.ANCIENT_WALL,
        Palette.FACTORY_RIGHT to Palette.ANCIENT_WALL_DARK,
        // 窓は小さな暗い穴に
        Palette.WINDOW_LIT to Palette.GOLD_DARK,
        Palette.WINDOW_DARK to Palette.STONE_EDGE,
    )

    /** 未来都市。白い躯体に、青く発光する帯。 */
    private val FUTURE_MAP = mapOf(
        Palette.HOUSE_ROOF to Palette.FUTURE_ROOF,
        Palette.HOUSE_ROOF_DARK to Palette.FUTURE_WALL_DARK,
        Palette.HOUSE_LEFT to Palette.FUTURE_WALL,
        Palette.HOUSE_RIGHT to Palette.FUTURE_WALL_DARK,
        Palette.RED_DARK to Palette.FUTURE_GLOW,
        Palette.OFFICE_ROOF to Palette.FUTURE_ROOF,
        Palette.OFFICE_LEFT to Palette.FUTURE_WALL,
        Palette.OFFICE_RIGHT to Palette.FUTURE_WALL_DARK,
        Palette.GLASS to Palette.FUTURE_WALL_DARK,
        Palette.GLASS_LIT to Palette.FUTURE_GLOW,
        Palette.WALL_ROOF to Palette.FUTURE_ROOF,
        Palette.WALL_LEFT to Palette.FUTURE_WALL,
        Palette.WALL_RIGHT to Palette.FUTURE_WALL_DARK,
        Palette.FACTORY_ROOF to Palette.FUTURE_WALL_DARK,
        Palette.FACTORY_LEFT to Palette.FUTURE_WALL,
        Palette.FACTORY_RIGHT to Palette.FUTURE_WALL_DARK,
        // 窓はすべて発光させる
        Palette.WINDOW_LIT to Palette.FUTURE_GLOW,
        Palette.WINDOW_DARK to Palette.SKY_DEEP,
    )

    /** ヨーロッパ。赤茶の瓦と、クリーム色の石壁。 */
    private val EUROPE_MAP = mapOf(
        Palette.HOUSE_ROOF to Palette.EURO_ROOF,
        Palette.HOUSE_ROOF_DARK to Palette.EURO_ROOF_DARK,
        Palette.HOUSE_LEFT to Palette.EURO_WALL,
        Palette.HOUSE_RIGHT to Palette.EURO_WALL_DARK,
        Palette.RED_DARK to Palette.EURO_ROOF_DARK,
        Palette.OFFICE_ROOF to Palette.EURO_ROOF,
        Palette.OFFICE_LEFT to Palette.EURO_WALL,
        Palette.OFFICE_RIGHT to Palette.EURO_WALL_DARK,
        // ガラスも石造りに置き換える。ここを残すと、どの様式でも
        // 同じ青いオフィスビルが並んでしまう。
        Palette.GLASS to Palette.EURO_WALL_DARK,
        Palette.GLASS_LIT to Palette.EURO_WALL,
        Palette.OFFICE_ROOF to Palette.EURO_ROOF,
        Palette.WALL_ROOF to Palette.EURO_ROOF,
        Palette.WALL_LEFT to Palette.EURO_WALL,
        Palette.WALL_RIGHT to Palette.EURO_WALL_DARK,
        Palette.FACTORY_ROOF to Palette.EURO_ROOF_DARK,
        Palette.FACTORY_LEFT to Palette.EURO_WALL,
        Palette.FACTORY_RIGHT to Palette.EURO_WALL_DARK,
        Palette.WALL_ROOF to Palette.EURO_WALL,
    )

    /** 日本の地方都市。いぶし銀の瓦と白い壁。 */
    private val JAPAN_MAP = mapOf(
        Palette.HOUSE_ROOF to Palette.JP_ROOF,
        Palette.HOUSE_ROOF_DARK to Palette.JP_ROOF_DARK,
        Palette.HOUSE_LEFT to Palette.JP_WALL,
        Palette.HOUSE_RIGHT to Palette.JP_WALL_DARK,
        Palette.RED_DARK to Palette.JP_ROOF_DARK,
        Palette.OFFICE_ROOF to Palette.JP_ROOF,
        Palette.OFFICE_LEFT to Palette.JP_WALL,
        Palette.OFFICE_RIGHT to Palette.JP_WALL_DARK,
        Palette.GLASS to Palette.JP_WALL_DARK,
        Palette.GLASS_LIT to Palette.JP_WALL,
        Palette.WALL_ROOF to Palette.JP_ROOF,
        Palette.WALL_LEFT to Palette.JP_WALL,
        Palette.WALL_RIGHT to Palette.JP_WALL_DARK,
        Palette.FACTORY_ROOF to Palette.JP_ROOF_DARK,
        Palette.FACTORY_LEFT to Palette.JP_WALL,
        Palette.FACTORY_RIGHT to Palette.JP_WALL_DARK,
        Palette.WALL_ROOF to Palette.JP_WALL,
    )

    private val recolourCache = object : LinkedHashMap<Any, Sprite>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, Sprite>?) = size > 64
    }

    /**
     * [style] に合わせて色を変えた絵を返す。作った結果は使い回す。
     *
     * 「じぶんで きめる」のときは [custom] の色を使う。色を変えるたびに
     * 別の絵になるので、使い回しの鍵には色の組み合わせも含める。
     */
    @Synchronized
    fun styled(sprite: Sprite, style: City.Style, custom: CustomStyle? = null): Sprite {
        if (style == City.Style.STANDARD) return sprite
        if (style == City.Style.CUSTOM) {
            val c = custom ?: return sprite
            if (c.isDefault) return sprite
            val key = Triple(sprite, style, c.save().toList())
            return recolourCache.getOrPut(key) { recolour(sprite, c.toMap()) }
        }
        return recolourCache.getOrPut(sprite to style) {
            recolour(
                sprite,
                when (style) {
                    City.Style.RURAL -> RURAL_MAP
                    City.Style.GRITTY -> GRITTY_MAP
                    City.Style.PRIME -> PRIME_MAP
                    City.Style.ANCIENT -> ANCIENT_MAP
                    City.Style.FUTURE -> FUTURE_MAP
                    City.Style.EUROPE -> EUROPE_MAP
                    City.Style.JAPAN -> JAPAN_MAP
                    else -> emptyMap()
                },
            )
        }
    }

    fun of(kind: io.github.hatake716.pixelcity.game.TileKind): Sprite? =
        when (kind) {
            io.github.hatake716.pixelcity.game.TileKind.POWER_COAL -> POWER_COAL
            io.github.hatake716.pixelcity.game.TileKind.POWER_SOLAR -> POWER_SOLAR
            io.github.hatake716.pixelcity.game.TileKind.POWER_WIND -> POWER_WIND
            io.github.hatake716.pixelcity.game.TileKind.POWER_LINE -> POWER_LINE
            io.github.hatake716.pixelcity.game.TileKind.PARK -> PARK
            io.github.hatake716.pixelcity.game.TileKind.POLICE -> POLICE
            io.github.hatake716.pixelcity.game.TileKind.FIRE -> FIRE
            io.github.hatake716.pixelcity.game.TileKind.SCHOOL -> SCHOOL
            io.github.hatake716.pixelcity.game.TileKind.HOSPITAL -> HOSPITAL
            io.github.hatake716.pixelcity.game.TileKind.CLINIC -> CLINIC
            io.github.hatake716.pixelcity.game.TileKind.WATER_TOWER -> WATER_TOWER
            io.github.hatake716.pixelcity.game.TileKind.WATER_PLANT -> WATER_PLANT
            io.github.hatake716.pixelcity.game.TileKind.SEWAGE_PLANT -> SEWAGE_PLANT
            io.github.hatake716.pixelcity.game.TileKind.LANDFILL -> LANDFILL
            io.github.hatake716.pixelcity.game.TileKind.INCINERATOR -> INCINERATOR
            io.github.hatake716.pixelcity.game.TileKind.RECYCLING -> RECYCLING
            io.github.hatake716.pixelcity.game.TileKind.BUS_STOP -> BUS_STOP
            io.github.hatake716.pixelcity.game.TileKind.SUBWAY_STATION -> SUBWAY_STATION
            io.github.hatake716.pixelcity.game.TileKind.AIRPORT -> AIRPORT
            io.github.hatake716.pixelcity.game.TileKind.SEAPORT -> SEAPORT
            else -> null
        }

    const val VARIANTS = 6
    private val variants = java.util.concurrent.ConcurrentHashMap<Int, Sprite>()
    fun isReady(kind: TileKind, stage: Int, variant: Int): Boolean =
        variants.containsKey(kind.saveId * 1000 + stage.coerceIn(1, 3) * 100 + Math.floorMod(variant, VARIANTS))

    /** Polygon generation is fast enough to produce the real building on its first lookup. */
    fun zoneBuilding(kind: TileKind, stage: Int, variant: Int): Sprite {
        val v = Math.floorMod(variant, VARIANTS)
        val s = stage.coerceIn(1, 3)
        return variants.getOrPut(kind.saveId * 1000 + s * 100 + v) { Architecture.zone(kind, s, v) }
    }
    val HOUSE_1: Sprite by lazy { zoneBuilding(TileKind.ZONE_R, 1, 0) }
    val HOUSE_2: Sprite by lazy { zoneBuilding(TileKind.ZONE_R, 2, 0) }
    val HOUSE_3: Sprite by lazy { zoneBuilding(TileKind.ZONE_R, 3, 0) }
    val SHOP_1: Sprite by lazy { zoneBuilding(TileKind.ZONE_C, 1, 0) }
    val SHOP_2: Sprite by lazy { zoneBuilding(TileKind.ZONE_C, 2, 0) }
    val SHOP_3: Sprite by lazy { zoneBuilding(TileKind.ZONE_C, 3, 0) }
    val FACTORY_1: Sprite by lazy { zoneBuilding(TileKind.ZONE_I, 1, 0) }
    val FACTORY_2: Sprite by lazy { zoneBuilding(TileKind.ZONE_I, 2, 0) }
    val FACTORY_3: Sprite by lazy { zoneBuilding(TileKind.ZONE_I, 3, 0) }
    val POWER_COAL: Sprite by lazy { Architecture.facility(TileKind.POWER_COAL) }
    val POWER_SOLAR: Sprite by lazy { Architecture.facility(TileKind.POWER_SOLAR) }
    val POWER_WIND: Sprite by lazy { Architecture.facility(TileKind.POWER_WIND) }
    val POWER_LINE: Sprite by lazy { Architecture.facility(TileKind.POWER_LINE) }
    val PARK: Sprite by lazy { Architecture.facility(TileKind.PARK) }
    val POLICE: Sprite by lazy { Architecture.facility(TileKind.POLICE) }
    val FIRE: Sprite by lazy { Architecture.facility(TileKind.FIRE) }
    val SCHOOL: Sprite by lazy { Architecture.facility(TileKind.SCHOOL) }
    val HOSPITAL: Sprite by lazy { Architecture.facility(TileKind.HOSPITAL) }
    val CLINIC: Sprite by lazy { Architecture.facility(TileKind.CLINIC) }
    val WATER_TOWER: Sprite by lazy { Architecture.facility(TileKind.WATER_TOWER) }
    val WATER_PLANT: Sprite by lazy { Architecture.facility(TileKind.WATER_PLANT) }
    val SEWAGE_PLANT: Sprite by lazy { Architecture.facility(TileKind.SEWAGE_PLANT) }
    val LANDFILL: Sprite by lazy { Architecture.facility(TileKind.LANDFILL) }
    val INCINERATOR: Sprite by lazy { Architecture.facility(TileKind.INCINERATOR) }
    val RECYCLING: Sprite by lazy { Architecture.facility(TileKind.RECYCLING) }
    val BUS_STOP: Sprite by lazy { Architecture.facility(TileKind.BUS_STOP) }
    val SUBWAY_STATION: Sprite by lazy { Architecture.facility(TileKind.SUBWAY_STATION) }
    val AIRPORT: Sprite by lazy { Architecture.facility(TileKind.AIRPORT) }
    val SEAPORT: Sprite by lazy { Architecture.facility(TileKind.SEAPORT) }
    val NO_POWER: Sprite by lazy {
        val data = ByteArray(48 * 64) { Pix.TRANSPARENT }
        for (y in 0 until 60) for (x in 0 until 48) {
            val on = if (y < 30) x in (25 - y / 2)..38 else x in 10..(38 - (y - 30) / 2)
            if (on) data[y * 48 + x] = Palette.GOLD.toByte()
        }
        Sprite(48, 64, data)
    }
}
