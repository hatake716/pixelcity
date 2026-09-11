package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.Terrain
import kotlin.math.abs

/**
 * 地面のタイル。64×32 の菱形。
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
    val GRASS = diamond { x, y, edge ->
        val n = noise(x, y, 1) % 100
        // 草の房を、少し大きめの塊で散らす
        val clump = noise(x / 3, y / 2, 5) % 100
        when {
            edge > 0.96f -> Palette.GRASS_EDGE
            clump < 12 && n < 60 -> Palette.GRASS_DARK
            n < 14 -> Palette.GRASS_LIT
            n < 30 -> Palette.GRASS_DARK
            else -> Palette.GRASS
        }
    }

    /** 水面。横長の波と、ちらつく反射。 */
    val WATER = diamond { x, y, edge ->
        val wave = ((x / 3) + (y * 2)) % 11
        val sparkle = noise(x, y, 9) % 100
        when {
            edge > 0.96f -> Palette.WATER_DARK
            sparkle < 4 && wave < 4 -> Palette.WATER_FOAM
            wave < 3 -> Palette.WATER_LIT
            wave < 7 -> Palette.WATER
            else -> Palette.WATER_DARK
        }
    }

    /** 砂浜。粒の粗い砂。 */
    val SHORE = diamond { x, y, edge ->
        val n = noise(x, y, 7) % 100
        when {
            edge > 0.94f -> Palette.SAND_DARK
            n < 16 -> Palette.SAND_LIT
            n < 34 -> Palette.SAND_DARK
            else -> Palette.SAND
        }
    }

    // --- 道路 ---

    /**
     * 道路。[alongX] と [alongY] で、どちらの向きに車線を引くかを決める。
     * 交差点は両方 true。
     */
    private fun road(alongX: Boolean, alongY: Boolean): Sprite = diamond { x, y, edge ->
        if (edge > 0.93f) return@diamond Palette.KERB

        val cx = (x + 0.5f) - W / 2f
        val cy = (y + 0.5f) - H / 2f
        // 菱形の2つの軸方向の座標（-1..1）
        val u = (cx / (W / 2f) + cy / (H / 2f)) / 2f   // x方向（右下）
        val v = (cy / (H / 2f) - cx / (W / 2f)) / 2f   // y方向（左下）

        // 中央線。交差点では中心を空ける。
        val onXLine = alongX && abs(v) < 0.07f && (!alongY || abs(u) > 0.32f)
        val onYLine = alongY && abs(u) < 0.07f && (!alongX || abs(v) > 0.32f)
        val dashX = ((u * 7f).toInt() and 1) == 0
        val dashY = ((v * 7f).toInt() and 1) == 0

        // 舗装のざらつき
        val grain = noise(x, y, 3) % 100

        when {
            onXLine && dashX -> Palette.ROAD_LINE
            onYLine && dashY -> Palette.ROAD_LINE
            edge > 0.78f -> Palette.ROAD_DARK
            grain < 10 -> Palette.ROAD_LIT
            grain < 22 -> Palette.ROAD_DARK
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
