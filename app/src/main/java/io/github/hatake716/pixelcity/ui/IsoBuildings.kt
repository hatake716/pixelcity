package io.github.hatake716.pixelcity.ui

import kotlin.math.abs

/**
 * 建物のスプライト。
 *
 * クォータービューでは、建物は「屋根の菱形 + 左右2つの側面」でできた箱になる。
 * 光は左上から当たるものとして、
 *   屋根 = 明るい / 左の面 = 中間 / 右の面 = 暗い
 * と塗り分けると立体に見える。
 *
 * 箱を組み立てる [box] を土台にして、そこへ窓・煙突・屋根の飾りを重ねていく。
 * 手で1行ずつ書くと幅がずれて壊れるので、すべて生成する。
 */
object IsoBuildings {

    private const val W = Iso.TILE_W          // 32
    private const val TH = Iso.TILE_H         // 16 屋根の菱形の高さ

    /** 面の明るさ。左上からの光を想定。 */
    private const val ROOF: Byte = 4
    private const val ROOF_EDGE: Byte = 6
    private const val LEFT: Byte = 8
    private const val RIGHT: Byte = 11
    private const val OUTLINE: Byte = 14
    private const val WINDOW_LIT: Byte = 1
    private const val WINDOW_DARK: Byte = 13

    /**
     * 高さ [h] 論理ピクセルの箱を描く。
     *
     * 返るスプライトの大きさは 32 ×（h + 16）。
     * 下端の菱形が地面のタイルにぴったり重なるように作る。
     *
     * [detail] で、屋根と側面へ自由に描き込める。
     *  - roof(x, y): 屋根の上（菱形の内側）
     *  - wall(u, v, side): 側面。u は水平方向 0..1、v は下からの高さ 0..1、
     *    side は -1 が左面、+1 が右面。
     */
    private fun box(
        h: Int,
        roof: ((x: Int, y: Int, edge: Float) -> Byte?)? = null,
        wall: ((u: Float, v: Float, side: Int) -> Byte?)? = null,
    ): Sprite {
        // 屋根の菱形を h だけ持ち上げた箱。
        // 全体の高さは「屋根の菱形 + 持ち上げた分」。
        val height = TH + h
        val data = ByteArray(W * height) { Pix.TRANSPARENT }

        // 屋根の菱形の中心は、上から TH/2 の位置。
        // 地面の菱形の中心は、その h 下。
        val roofCy = TH / 2f

        // --- 側面 ---
        // 屋根の菱形の「下半分の輪郭」と、それを h 下ろした輪郭に挟まれた帯。
        for (x in 0 until W) {
            val dx = (x + 0.5f) - W / 2f
            val halfSpan = (1f - abs(dx) / (W / 2f)) * (TH / 2f)
            if (halfSpan <= 0f) continue
            val top = roofCy + halfSpan          // 屋根の下側の縁
            val bottom = top + h                 // 地面に接する縁
            val side = if (dx < 0f) -1 else 1
            val u = abs(dx) / (W / 2f)

            var y = Math.floor(top.toDouble()).toInt()
            while (y < bottom && y < height) {
                if (y >= 0) {
                    val v = 1f - (y + 0.5f - top) / h.coerceAtLeast(1).toFloat()
                    var c = if (side < 0) LEFT else RIGHT
                    if (u > 0.96f) c = OUTLINE                    // 左右の角
                    if (y + 1 >= bottom) c = OUTLINE              // 接地の線
                    if (abs(dx) < 1f) c = OUTLINE                 // 手前の角
                    wall?.invoke(u, v.coerceIn(0f, 1f), side)?.let { c = it }
                    data[y * W + x] = c
                }
                y++
            }
        }

        // --- 屋根 ---
        for (y in 0 until TH) for (x in 0 until W) {
            val dx = (x + 0.5f) - W / 2f
            val dy = (y + 0.5f) - roofCy
            val d = abs(dx) / (W / 2f) + abs(dy) / (TH / 2f)
            if (d > 1f) continue
            var c = if (d > 0.9f) ROOF_EDGE else ROOF
            roof?.invoke(x, y, d)?.let { c = it }
            data[y * W + x] = c
        }

        return Sprite(W, height, data)
    }

    /** 窓を並べる側面。[rows] 段、[cols] 列。 */
    private fun windows(rows: Int, cols: Int, lit: (Int, Int) -> Boolean): (Float, Float, Int) -> Byte? =
        { u, v, side ->
            // 面の中ほどだけに窓を置く
            if (u > 0.9f || v > 0.94f || v < 0.06f) null
            else {
                val col = (u * cols).toInt().coerceIn(0, cols - 1)
                val row = ((1f - v) * rows).toInt().coerceIn(0, rows - 1)
                val cu = (u * cols) - col
                val cv = ((1f - v) * rows) - row
                if (cu in 0.22f..0.78f && cv in 0.25f..0.75f) {
                    if (lit(col + if (side < 0) 0 else cols, row)) WINDOW_LIT else WINDOW_DARK
                } else null
            }
        }

    private fun hash(a: Int, b: Int, salt: Int): Int {
        var h = a * 374761393 + b * 668265263 + salt * 362437
        h = (h xor (h shr 13)) * 1274126177
        return (h xor (h shr 16)) and 0x7fffffff
    }

    // ------------------------------------------------------------------
    // 住宅 1〜3段階
    // ------------------------------------------------------------------

    /** 小さな一戸建て。切妻屋根。 */
    val HOUSE_1 = box(
        h = 10,
        roof = { x, y, d ->
            // 屋根を棟で二分して、片側を明るくする
            val dx = (x + 0.5f) - W / 2f
            when {
                d > 0.92f -> ROOF_EDGE
                dx < 0 -> 3
                else -> 5
            }
        },
        wall = windows(1, 2) { c, _ -> c % 3 != 1 },
    )

    /** 集合住宅。 */
    val HOUSE_2 = box(
        h = 22,
        wall = windows(3, 3) { c, r -> hash(c, r, 11) % 5 != 0 },
    )

    /** 高層の集合住宅。 */
    val HOUSE_3 = box(
        h = 38,
        roof = { _, _, d -> if (d > 0.86f) ROOF_EDGE else 3 },
        wall = windows(6, 3) { c, r -> hash(c, r, 23) % 4 != 0 },
    )

    // ------------------------------------------------------------------
    // 商業 1〜3段階
    // ------------------------------------------------------------------

    /** 商店。日よけのある低い建物。 */
    val SHOP_1 = box(
        h = 12,
        wall = { u, v, _ ->
            when {
                v < 0.36f && u < 0.86f -> 2           // 大きなショーウィンドウ
                v in 0.36f..0.46f && u < 0.9f -> 12   // 日よけ
                else -> null
            }
        },
    )

    /** 雑居ビル。 */
    val SHOP_2 = box(
        h = 26,
        wall = { u, v, side ->
            when {
                v < 0.22f && u < 0.88f -> 2
                else -> windows(4, 3) { c, r -> hash(c, r, 31) % 6 != 0 }(u, v, side)
            }
        },
    )

    /** オフィスビル。窓が縦に連なる。 */
    val SHOP_3 = box(
        h = 46,
        roof = { _, _, d -> if (d > 0.86f) ROOF_EDGE else 2 },
        wall = { u, v, side ->
            when {
                v < 0.14f && u < 0.88f -> 2
                else -> {
                    // 縦のガラス帯
                    val col = (u * 4).toInt()
                    val cu = (u * 4) - col
                    if (u < 0.92f && cu in 0.15f..0.85f) {
                        if (((v * 14).toInt() and 1) == 0) 1 else 3
                    } else null
                }
            }
        },
    )

    // ------------------------------------------------------------------
    // 工業 1〜3段階
    // ------------------------------------------------------------------

    /** 小さな作業場。 */
    val FACTORY_1 = box(
        h = 12,
        roof = { x, _, d ->
            // のこぎり屋根
            if (d > 0.9f) ROOF_EDGE else if (((x / 4) and 1) == 0) 5 else 3
        },
        wall = windows(1, 3) { c, _ -> c % 2 == 0 },
    )

    /** 工場。 */
    val FACTORY_2 = box(
        h = 20,
        roof = { x, _, d -> if (d > 0.9f) ROOF_EDGE else if (((x / 3) and 1) == 0) 6 else 4 },
        wall = { u, v, _ ->
            when {
                v < 0.3f && u in 0.1f..0.5f -> 12   // シャッター
                else -> null
            }
        },
    )

    /** 大きな工場。 */
    val FACTORY_3 = box(
        h = 30,
        roof = { x, _, d -> if (d > 0.9f) ROOF_EDGE else if (((x / 3) and 1) == 0) 6 else 4 },
        wall = windows(3, 4) { c, r -> hash(c, r, 41) % 3 != 0 },
    )

    // ------------------------------------------------------------------
    // 施設
    // ------------------------------------------------------------------

    /** 火力発電所。煙突は別に重ねる。 */
    val POWER_COAL = box(
        h = 24,
        roof = { _, _, d -> if (d > 0.9f) ROOF_EDGE else 5 },
        wall = { u, v, _ -> if (v < 0.28f && u < 0.7f) 12 else null },
    )

    /** 太陽光発電。低く、屋根がパネル。 */
    val POWER_SOLAR = box(
        h = 8,
        roof = { x, y, d ->
            if (d > 0.9f) ROOF_EDGE
            else if (((x / 3) + (y / 2)) % 2 == 0) 11 else 9
        },
    )

    /** 公園。建物ではなく、地面に木を植える。 */
    val PARK = run {
        val h = 14
        val height = h + TH
        val data = ByteArray(W * height) { Pix.TRANSPARENT }
        // 芝生の菱形
        for (y in 0 until TH) for (x in 0 until W) {
            val dx = (x + 0.5f) - W / 2f
            val dy = (y + 0.5f) - TH / 2f
            val d = abs(dx) / (W / 2f) + abs(dy) / (TH / 2f)
            if (d <= 1f) data[(y + h) * W + x] = if (d > 0.9f) 4.toByte() else 1.toByte()
        }
        fun tree(cx: Int, cy: Int, r: Int) {
            for (y in -r..r) for (x in -r - 1..r + 1) {
                val px = cx + x
                val py = cy + y
                if (px !in 0 until W || py !in 0 until height) continue
                val dd = (x * x).toFloat() / ((r + 1) * (r + 1)) + (y * y).toFloat() / (r * r)
                if (dd <= 1f) {
                    data[py * W + px] = if (x > r / 2 || y > r / 2) 9 else 6
                }
            }
            // 幹
            for (y in cy + r until cy + r + 3) {
                if (y in 0 until height) data[y * W + cx] = 12
            }
        }
        tree(10, 8, 5)
        tree(21, 12, 4)
        Sprite(W, height, data)
    }

    /** 警察署。 */
    val POLICE = box(
        h = 18,
        roof = { _, _, d -> if (d > 0.9f) ROOF_EDGE else 3 },
        wall = { u, v, _ ->
            when {
                v < 0.3f && u in 0.15f..0.5f -> 2     // 入口
                v in 0.5f..0.8f && u < 0.85f -> if (((u * 6).toInt() and 1) == 0) 1 else null
                else -> null
            }
        },
    )

    /** 消防署。大きなシャッター。 */
    val FIRE = box(
        h = 18,
        roof = { _, _, d -> if (d > 0.9f) ROOF_EDGE else 6 },
        wall = { u, v, _ -> if (v < 0.42f && u < 0.62f) 12 else null },
    )

    /** 学校。横に長い窓。 */
    val SCHOOL = box(
        h = 20,
        wall = { u, v, _ ->
            if (u < 0.88f && (v in 0.30f..0.44f || v in 0.58f..0.72f)) 1 else null
        },
    )

    /** 病院。十字の印。 */
    val HOSPITAL = box(
        h = 24,
        roof = { x, y, d ->
            val dx = abs((x + 0.5f) - W / 2f)
            val dy = abs((y + 0.5f) - TH / 2f)
            when {
                d > 0.9f -> ROOF_EDGE
                dx < 2.5f && dy < 5f -> 13     // 十字
                dy < 1.5f && dx < 9f -> 13
                else -> 1
            }
        },
        wall = windows(3, 3) { c, r -> hash(c, r, 53) % 4 != 0 },
    )

    /** 電気が来ていない印。建物の上に重ねる。 */
    val NO_POWER = Pix.sprite(
        "  @@@@  ",
        " @....@ ",
        "@..@@..@",
        "@.@..@.@",
        "@.@..@.@",
        "@..@@..@",
        " @....@ ",
        "  @@@@  ",
    )
}
