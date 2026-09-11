package io.github.hatake716.pixelcity.ui

import kotlin.math.abs

/**
 * 地面のタイル。32×16 の菱形。
 *
 * 菱形は手で書くと1行ずつ幅がずれて壊れやすいので、
 * **菱形の式から生成する**。どの行も必ず正しい幅になり、
 * 外側は透明、内側だけが塗られることが保証される。
 *
 * 模様は「そのタイルの何色を置くか」を決める関数として渡す。
 */
object IsoTiles {

    private const val W = Iso.TILE_W
    private const val H = Iso.TILE_H

    /**
     * 菱形を作る。[shade] にはタイル内の座標と、
     * 菱形の中心からの正規化距離（0=中心, 1=縁）が渡る。
     */
    private fun diamond(shade: (x: Int, y: Int, edge: Float) -> Byte): Sprite {
        val data = ByteArray(W * H) { Pix.TRANSPARENT }
        for (y in 0 until H) for (x in 0 until W) {
            // 画素の中心で判定する
            val dx = (x + 0.5f) - W / 2f
            val dy = (y + 0.5f) - H / 2f
            val d = abs(dx) / (W / 2f) + abs(dy) / (H / 2f)
            if (d <= 1f) data[y * W + x] = shade(x, y, d)
        }
        return Sprite(W, H, data)
    }

    /** 疑似乱数。同じ座標からは必ず同じ値。地面の粒に使う。 */
    private fun noise(x: Int, y: Int, salt: Int): Int {
        var h = x * 374761393 + y * 668265263 + salt * 362437
        h = (h xor (h shr 13)) * 1274126177
        return (h xor (h shr 16)) and 0x7fffffff
    }

    /** 草地。細かな粒で土と草の質感を出す。 */
    val GRASS = diamond { x, y, edge ->
        val n = noise(x, y, 1) % 100
        when {
            edge > 0.94f -> 4            // 縁をわずかに締める
            n < 6 -> 3                   // ところどころ濃い草
            n < 18 -> 2
            n < 34 -> 1
            else -> 0
        }.toByte()
    }

    /** 水面。横縞で波を出し、奥ほどわずかに暗くする。 */
    val WATER = diamond { x, y, _ ->
        val wave = ((x / 2) + (y * 3)) % 7
        val base = when {
            wave < 2 -> 11
            wave < 4 -> 10
            else -> 9
        }
        // 上（奥）をすこし暗くして水面の奥行きを出す
        (base + if (y < H / 3) 1 else 0).coerceAtMost(13).toByte()
    }

    /** 砂浜。水際の明るい帯。 */
    val SHORE = diamond { x, y, edge ->
        val n = noise(x, y, 7) % 100
        when {
            edge > 0.9f -> 3
            n < 12 -> 2
            n < 30 -> 1
            else -> 0
        }.toByte()
    }

    // --- 道路 ---

    /** 舗装の基本色と縁。 */
    private const val PAVE: Byte = 7
    private const val PAVE_DARK: Byte = 8
    private const val KERB: Byte = 11
    private const val LINE: Byte = 3

    /**
     * 道路。[alongX] と [alongY] で、どちらの向きに車線を引くかを決める。
     * 交差点は両方 true。
     */
    private fun road(alongX: Boolean, alongY: Boolean): Sprite = diamond { x, y, edge ->
        // 縁石
        if (edge > 0.88f) return@diamond KERB

        val cx = (x + 0.5f) - W / 2f
        val cy = (y + 0.5f) - H / 2f
        // 菱形の2つの軸方向の座標（-1..1）
        val u = (cx / (W / 2f) + cy / (H / 2f)) / 2f   // x方向（右下）
        val v = (cy / (H / 2f) - cx / (W / 2f)) / 2f   // y方向（左下）

        // 中央線。交差点では中心を空ける。
        val onXLine = alongX && abs(v) < 0.12f && (!alongY || abs(u) > 0.30f)
        val onYLine = alongY && abs(u) < 0.12f && (!alongX || abs(v) > 0.30f)
        // 破線にする
        val dashX = ((u * 6f).toInt() and 1) == 0
        val dashY = ((v * 6f).toInt() and 1) == 0

        when {
            onXLine && dashX -> LINE
            onYLine && dashY -> LINE
            edge > 0.7f -> PAVE_DARK
            else -> PAVE
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
     * 区分を指定しただけで、まだ建っていない土地。
     * 縁に色をつけて用途が分かるようにする。
     */
    private fun zoneMarker(edgeLevel: Byte): Sprite = diamond { x, y, edge ->
        val n = noise(x, y, 3) % 100
        when {
            // 縁だけで用途を示す。面を明るくしすぎると、
            // 建っていない土地が街の中で浮いて見える。
            edge > 0.90f -> edgeLevel
            n < 10 -> 4
            n < 26 -> 3
            else -> 2
        }.toByte()
    }

    val ZONE_R_EMPTY = zoneMarker(6)
    val ZONE_C_EMPTY = zoneMarker(9)
    val ZONE_I_EMPTY = zoneMarker(12)

    fun terrainTile(terrain: io.github.hatake716.pixelcity.game.Terrain): Sprite =
        when (terrain) {
            io.github.hatake716.pixelcity.game.Terrain.WATER -> WATER
            io.github.hatake716.pixelcity.game.Terrain.SHORE -> SHORE
            io.github.hatake716.pixelcity.game.Terrain.LAND -> GRASS
        }
}
