package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.Terrain
import kotlin.math.abs

/**
 * 地面のタイル。512×256 の菱形。
 *
 * 菱形は手で書くと1行ずつ幅がずれて壊れやすいので、**菱形の式から生成する**。
 * どの行も必ず正しい幅になり、外側は透明、内側だけが塗られることが保証される。
 *
 * 色は [Palette] の索引で持つ。スプライトは「草の明るい色」とだけ書けばよく、
 * 実際の色をあとから変えても絵は壊れない。
 */
object IsoTiles {

    private const val W = Iso.TILE_W
    private const val H = Iso.TILE_H

    /**
     * 菱形を作る。[shade] にはタイル内の座標と、
     * 菱形の中心からの正規化距離（0=中心, 1=縁）が渡る。
     */
    private fun diamond(shade: (x: Int, y: Int, edge: Float) -> Int): Sprite {
        val data = ByteArray(W * H) { Pix.TRANSPARENT }
        for (y in 0 until H) for (x in 0 until W) {
            val dx = (x + 0.5f) - W / 2f
            val dy = (y + 0.5f) - H / 2f
            val d = abs(dx) / (W / 2f) + abs(dy) / (H / 2f)
            if (d <= 1f) data[y * W + x] = shade(x, y, d).toByte()
        }
        return Sprite(W, H, data)
    }

    /** 疑似乱数。同じ座標からは必ず同じ値。地面の粒に使う。 */
    private fun noise(x: Int, y: Int, salt: Int): Int {
        var h = x * 374761393 + y * 668265263 + salt * 362437
        h = (h xor (h shr 13)) * 1274126177
        return (h xor (h shr 16)) and 0x7fffffff
    }

    /** 草地。草むらの粒と、ところどころの濃い茂み。 */
    val GRASS = diamond { x, y, _ ->
        val clump = noise(x / 16, y / 10, 5) % 100
        when {
            clump < 4 && (x % 16 in 5..8) && (y % 10 in 3..5) -> Palette.GRASS_LIT
            else -> Palette.GRASS
        }
    }

    val WATER = diamond { x, y, _ ->
        when {
            y % 58 in 0..2 && (x + y * 3) % 153 < 35 -> Palette.WATER_LIT
            else -> Palette.WATER
        }
    }

    val SHORE = diamond { x, y, _ ->
        if (noise(x / 5, y / 3, 7) % 100 < 5) Palette.SAND else Palette.SAND_LIT
    }

    // --- 道路 ---

    /**
     * 道路。[alongX] と [alongY] で、どちらの向きに車線を引くかを決める。
     * 交差点は両方 true。
     */
    private fun road(alongX: Boolean, alongY: Boolean, broad: Boolean = false): Sprite = diamond { x, y, _ ->
        val cx = (x + 0.5f) - W / 2f
        val cy = (y + 0.5f) - H / 2f
        val u = (cx / (W / 2f) + cy / (H / 2f)) / 2f
        val v = (cy / (H / 2f) - cx / (W / 2f)) / 2f
        val width = if (broad) .36f else .29f
        val across = minOf(if(alongX) abs(v) else 1f, if(alongY) abs(u) else 1f)
        val crossing = alongX && alongY
        when {
            across > width + .025f -> Palette.SAND_LIT
            across > width -> Palette.STONE_DARK
            crossing && ((abs(u) in .34f.. .43f && abs(v) < .26f && ((v + .5f) * 44).toInt()%2==0) ||
                (abs(v) in .34f.. .43f && abs(u) < .26f && ((u + .5f) * 44).toInt()%2==0)) -> Palette.WHITE
            broad && !crossing && across < .025f -> Palette.TREE
            !crossing && across < .009f && (((if(alongX)u else v)+.5f)*18).toInt()%2==0 -> Palette.ROAD_LINE
            else -> Palette.ROAD
        }
    }

    val ROAD_X = road(alongX = true, alongY = false)
    val ROAD_Y = road(alongX = false, alongY = true)
    val ROAD_CROSS = road(alongX = true, alongY = true)

    /** 接続に応じた道路を返す。 */
    fun roadFor(alongX: Boolean, alongY: Boolean): Sprite = when {
        alongX && alongY -> ROAD_CROSS
        alongY -> ROAD_Y
        else -> ROAD_X
    }

    /**
     * 大通り。道路より広く、中央分離帯がある。
     */
    val AVENUE_X = road(alongX = true, alongY = false, broad = true)
    val AVENUE_Y = road(alongX = false, alongY = true, broad = true)
    val AVENUE_CROSS = road(alongX = true, alongY = true, broad = true)
    fun avenueFor(alongX: Boolean, alongY: Boolean): Sprite = when {
        alongX && alongY -> AVENUE_CROSS
        alongY -> AVENUE_Y
        else -> AVENUE_X
    }

    /**
     * 高速道路。高架にはせず、中央に柵のある広い舗装で表す。
     */
    private fun highway(alongX: Boolean, alongY: Boolean): Sprite = diamond { x, y, edge ->
        if (edge > 0.96f) return@diamond Palette.WALL_EDGE
        val cx = (x + 0.5f) - W / 2f
        val cy = (y + 0.5f) - H / 2f
        val u = (cx / (W / 2f) + cy / (H / 2f)) / 2f
        val v = (cy / (H / 2f) - cx / (W / 2f)) / 2f
        val along = if (alongX) u else v
        val across = if (alongX) v else u
        when {
            // 中央の柵
            abs(across) < 0.05f -> Palette.METAL
            // 外側のガードレール
            abs(abs(across) - 0.42f) < 0.035f -> Palette.METAL_LIT
            abs(abs(across) - 0.20f) < 0.02f &&
                ((along * 12f).toInt() and 1) == 0 -> Palette.ROAD_LINE
            edge > 0.86f -> Palette.ROAD_DARK
            else -> Palette.ROAD_DARK
        }
    }

    val HIGHWAY_X = highway(alongX = true, alongY = false)
    val HIGHWAY_Y = highway(alongX = false, alongY = true)

    fun highwayFor(alongX: Boolean, alongY: Boolean): Sprite =
        if (alongY && !alongX) HIGHWAY_Y else HIGHWAY_X

    /**
     * 地下鉄。地上には点線の目印だけを出す（地下を走るため）。
     */
    private fun subway(alongX: Boolean, alongY: Boolean): Sprite = diamond { x, y, edge ->
        val cx = (x + 0.5f) - W / 2f
        val cy = (y + 0.5f) - H / 2f
        val u = (cx / (W / 2f) + cy / (H / 2f)) / 2f
        val v = (cy / (H / 2f) - cx / (W / 2f)) / 2f
        val along = if (alongX) u else v
        val across = if (alongX) v else u
        val n = noise(x, y, 21) % 100
        when {
            edge > 0.94f -> Palette.GRASS_EDGE
            // 地下を示す点線
            abs(across) < 0.08f && ((along * 10f).toInt() and 1) == 0 -> Palette.METAL_DARK
            n < 14 -> Palette.GRASS_LIT
            n < 30 -> Palette.GRASS_DARK
            else -> Palette.GRASS
        }
    }

    val SUBWAY_X = subway(alongX = true, alongY = false)
    val SUBWAY_Y = subway(alongX = false, alongY = true)

    fun subwayFor(alongX: Boolean, alongY: Boolean): Sprite =
        if (alongY && !alongX) SUBWAY_Y else SUBWAY_X

    /**
     * 線路。枕木とレールを2本。道路と同じく向きで描き分ける。
     */
    private fun rail(alongX: Boolean, alongY: Boolean): Sprite = diamond { x, y, edge ->
        val cx = (x + 0.5f) - W / 2f
        val cy = (y + 0.5f) - H / 2f
        val u = (cx / (W / 2f) + cy / (H / 2f)) / 2f
        val v = (cy / (H / 2f) - cx / (W / 2f)) / 2f

        // その向きに沿った「線路方向の座標」と「横断方向の座標」
        val along = if (alongX) u else v
        val across = if (alongX) v else u

        val n = noise(x, y, 13) % 100
        when {
            edge > 0.93f -> Palette.SAND_DARK
            // レール2本
            abs(abs(across) - 0.16f) < 0.045f -> Palette.METAL_LIT
            // 枕木
            ((along * 16f).toInt() and 1) == 0 && abs(across) < 0.30f -> Palette.TRUNK
            // 砂利
            n < 24 -> Palette.STONE_DARK
            n < 50 -> Palette.SAND_DARK
            else -> Palette.STONE
        }
    }

    val RAIL_X = rail(alongX = true, alongY = false)
    val RAIL_Y = rail(alongX = false, alongY = true)

    fun railFor(alongX: Boolean, alongY: Boolean): Sprite =
        if (alongY && !alongX) RAIL_Y else RAIL_X

    /**
     * 農地。畝を引いた畑。季節で色が変わるように見せるため、
     * 位置によって作物の色を少し変える。
     */
    val FARM = diamond { x, y, edge ->
        val cx = (x + 0.5f) - W / 2f
        val cy = (y + 0.5f) - H / 2f
        val u = (cx / (W / 2f) + cy / (H / 2f)) / 2f
        val v = (cy / (H / 2f) - cx / (W / 2f)) / 2f
        val row = ((v * 22f).toInt())
        val n = noise(x, y, 17) % 100
        when {
            edge > 0.93f -> Palette.SAND_DARK
            // 畝のあいだの土
            (row and 1) == 0 -> if (n < 30) Palette.SAND_DARK else Palette.SAND
            // 作物
            n < 18 -> Palette.TREE_LIT
            n < 40 -> Palette.TREE_DARK
            else -> Palette.TREE
        }
    }

    /**
     * 区分を指定しただけで、まだ建っていない土地。
     * ならした地面に、用途の色で杭を打った縁をつける。
     */
    private fun zoneMarker(edgeColor: Int): Sprite = diamond { x, y, edge ->
        val n = noise(x, y, 11) % 100
        when {
            // 縁に等間隔の杭を打つ
            edge > 0.90f -> if (((x / 4) + (y / 2)) % 3 == 0) edgeColor else Palette.SAND_DARK
            n < 12 -> Palette.SAND_LIT
            n < 28 -> Palette.SAND_DARK
            else -> Palette.SAND
        }
    }

    val ZONE_R_EMPTY = zoneMarker(Palette.HOUSE_ROOF)
    val ZONE_C_EMPTY = zoneMarker(Palette.OFFICE_LEFT)
    val ZONE_I_EMPTY = zoneMarker(Palette.FACTORY_LEFT)

    fun terrainTile(terrain: Terrain): Sprite = when (terrain) {
        Terrain.WATER -> WATER
        Terrain.SHORE -> SHORE
        Terrain.LAND -> GRASS
    }
}
