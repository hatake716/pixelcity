package io.github.hatake716.pixelcity.ui

import kotlin.math.abs

/**
 * 建物のスプライト。64×32 の菱形の上に立つ箱。
 *
 * クォータービューでは、建物は「屋根の菱形 + 左右2つの側面」でできた箱になる。
 * 光は左上から当たるものとして、屋根を明るく、左の面を中間、右の面を暗く塗る。
 *
 * 面積が従来の4倍あるので、窓・戸口・屋根の設備・看板まで描き込める。
 * 手で1行ずつ書くと幅がずれて壊れるため、すべて式から生成している。
 */
object IsoBuildings {

    private const val W = Iso.TILE_W          // 64
    private const val TH = Iso.TILE_H         // 32 屋根の菱形の高さ

    /** 建物ごとの配色。屋根・左面・右面・輪郭。 */
    class Skin(
        val roof: Int,
        val left: Int,
        val right: Int,
        val edge: Int = Palette.WALL_EDGE,
    )

    private val GENERIC = Skin(Palette.WALL_ROOF, Palette.WALL_LEFT, Palette.WALL_RIGHT)
    private val HOUSE = Skin(Palette.HOUSE_ROOF, Palette.HOUSE_LEFT, Palette.HOUSE_RIGHT)
    private val OFFICE = Skin(Palette.OFFICE_ROOF, Palette.OFFICE_LEFT, Palette.OFFICE_RIGHT)
    private val FACTORY = Skin(Palette.FACTORY_ROOF, Palette.FACTORY_LEFT, Palette.FACTORY_RIGHT)

    /**
     * 高さ [h] 論理ピクセルの箱を描く。
     *
     * 返るスプライトの大きさは 64 ×（h + 32）。
     * 下端の菱形が地面のタイルにぴったり重なるように作る。
     *
     * [roof] は屋根の上、[wall] は側面へ自由に描き込むための差し替え。
     *  - roof(x, y, edge): 屋根。edge は菱形の中心からの距離 0..1
     *  - wall(u, v, side): 側面。u は水平 0..1、v は下からの高さ 0..1、
     *    side は -1 が左面、+1 が右面
     */
    private fun box(
        h: Int,
        skin: Skin = GENERIC,
        roof: ((x: Int, y: Int, edge: Float) -> Int?)? = null,
        wall: ((u: Float, v: Float, side: Int) -> Int?)? = null,
    ): Sprite {
        val height = TH + h
        val data = ByteArray(W * height) { Pix.TRANSPARENT }
        val roofCy = TH / 2f

        // --- 側面 ---
        for (x in 0 until W) {
            val dx = (x + 0.5f) - W / 2f
            val halfSpan = (1f - abs(dx) / (W / 2f)) * (TH / 2f)
            if (halfSpan <= 0f) continue
            val top = roofCy + halfSpan
            val bottom = top + h
            val side = if (dx < 0f) -1 else 1
            val u = abs(dx) / (W / 2f)

            var y = Math.floor(top.toDouble()).toInt()
            while (y < bottom && y < height) {
                if (y >= 0) {
                    val v = 1f - (y + 0.5f - top) / h.coerceAtLeast(1).toFloat()
                    var c = if (side < 0) skin.left else skin.right
                    if (u > 0.985f) c = skin.edge                 // 左右の角
                    if (y + 1 >= bottom) c = skin.edge            // 接地の線
                    if (abs(dx) < 1.5f) c = skin.edge             // 手前の角
                    wall?.invoke(u, v.coerceIn(0f, 1f), side)?.let { c = it }
                    data[y * W + x] = c.toByte()
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
            var c = if (d > 0.94f) skin.edge else skin.roof
            roof?.invoke(x, y, d)?.let { c = it }
            data[y * W + x] = c.toByte()
        }

        return Sprite(W, height, data)
    }

    private fun hash(a: Int, b: Int, salt: Int): Int {
        var h = a * 374761393 + b * 668265263 + salt * 362437
        h = (h xor (h shr 13)) * 1274126177
        return (h xor (h shr 16)) and 0x7fffffff
    }

    /**
     * 窓を並べる側面。[rows] 段、[cols] 列。
     * 窓枠まで描けるだけの面積があるので、枠と明かりを塗り分ける。
     */
    private fun windows(
        rows: Int,
        cols: Int,
        frame: Int = Palette.WALL_EDGE,
        lit: (Int, Int) -> Boolean,
    ): (Float, Float, Int) -> Int? = { u, v, side ->
        if (u > 0.93f || v > 0.95f || v < 0.05f) null
        else {
            val col = (u * cols).toInt().coerceIn(0, cols - 1)
            val row = ((1f - v) * rows).toInt().coerceIn(0, rows - 1)
            val cu = (u * cols) - col
            val cv = ((1f - v) * rows) - row
            when {
                cu in 0.20f..0.80f && cv in 0.22f..0.78f ->
                    if (lit(col + if (side < 0) 0 else cols, row)) Palette.WINDOW_LIT
                    else Palette.WINDOW_DARK
                // 窓枠
                cu in 0.14f..0.86f && cv in 0.16f..0.84f -> frame
                else -> null
            }
        }
    }

    // ------------------------------------------------------------------
    // 住宅 1〜3段階
    // ------------------------------------------------------------------

    /** 一戸建て。切妻の瓦屋根、玄関と小窓。 */
    val HOUSE_1 = box(
        h = 16,
        skin = HOUSE,
        roof = { x, y, d ->
            val dx = (x + 0.5f) - W / 2f
            val dy = (y + 0.5f) - TH / 2f
            when {
                d > 0.94f -> Palette.HOUSE_ROOF_DARK
                // 棟（頂上の線）
                abs(dy) < 1.5f -> Palette.HOUSE_ROOF_DARK
                // 瓦の筋
                ((x + y) / 3) % 2 == 0 && dx < 0 -> Palette.HOUSE_ROOF
                dx < 0 -> Palette.HOUSE_ROOF
                else -> Palette.HOUSE_ROOF_DARK
            }
        },
        wall = { u, v, side ->
            when {
                // 玄関
                side < 0 && u in 0.30f..0.55f && v < 0.55f -> Palette.TRUNK
                // 小窓
                u in 0.62f..0.82f && v in 0.35f..0.70f -> Palette.WINDOW_LIT
                u in 0.58f..0.86f && v in 0.30f..0.75f -> Palette.WALL_EDGE
                else -> null
            }
        },
    )

    /** 低層の集合住宅。 */
    val HOUSE_2 = box(
        h = 34,
        skin = HOUSE,
        roof = { _, _, d -> if (d > 0.94f) Palette.HOUSE_ROOF_DARK else Palette.HOUSE_ROOF },
        wall = windows(3, 3) { c, r -> hash(c, r, 11) % 5 != 0 },
    )

    /** 高層の集合住宅。屋上に給水塔。 */
    val HOUSE_3 = box(
        h = 56,
        skin = HOUSE,
        roof = { x, y, d ->
            val dx = abs((x + 0.5f) - W / 2f)
            val dy = abs((y + 0.5f) - TH / 2f)
            when {
                d > 0.94f -> Palette.HOUSE_ROOF_DARK
                // 屋上の塔屋
                dx < 9f && dy < 5f -> Palette.WALL_LEFT
                else -> Palette.HOUSE_ROOF_DARK
            }
        },
        wall = windows(6, 4) { c, r -> hash(c, r, 23) % 4 != 0 },
    )

    // ------------------------------------------------------------------
    // 商業 1〜3段階
    // ------------------------------------------------------------------

    /** 商店。大きなショーウィンドウと日よけ。 */
    val SHOP_1 = box(
        h = 20,
        skin = OFFICE,
        roof = { _, _, d -> if (d > 0.94f) Palette.WALL_EDGE else Palette.OFFICE_ROOF },
        wall = { u, v, _ ->
            when {
                v < 0.42f && u < 0.88f -> Palette.GLASS_LIT       // ショーウィンドウ
                v in 0.42f..0.52f && u < 0.92f ->
                    if (((u * 12).toInt() and 1) == 0) Palette.RED else Palette.WHITE  // 縞の日よけ
                else -> null
            }
        },
    )

    /** 雑居ビル。1階が店、上が事務所。 */
    val SHOP_2 = box(
        h = 42,
        skin = OFFICE,
        wall = { u, v, side ->
            when {
                v < 0.24f && u < 0.90f -> Palette.GLASS_LIT
                v in 0.24f..0.30f && u < 0.92f -> Palette.WALL_EDGE
                else -> windows(4, 4) { c, r -> hash(c, r, 31) % 6 != 0 }(u, v, side)
            }
        },
    )

    /** オフィスビル。全面ガラスの縦帯。 */
    val SHOP_3 = box(
        h = 74,
        skin = OFFICE,
        roof = { x, y, d ->
            val dx = abs((x + 0.5f) - W / 2f)
            val dy = abs((y + 0.5f) - TH / 2f)
            when {
                d > 0.94f -> Palette.WALL_EDGE
                dx < 7f && dy < 4f -> Palette.METAL       // 屋上設備
                else -> Palette.OFFICE_ROOF
            }
        },
        wall = { u, v, _ ->
            when {
                v < 0.10f && u < 0.90f -> Palette.GLASS_LIT
                u > 0.94f -> null
                else -> {
                    val col = (u * 6).toInt()
                    val cu = (u * 6) - col
                    when {
                        cu < 0.18f -> Palette.OFFICE_RIGHT       // 柱
                        ((v * 22).toInt() and 1) == 0 -> Palette.GLASS_LIT
                        else -> Palette.GLASS
                    }
                }
            }
        },
    )

    // ------------------------------------------------------------------
    // 工業 1〜3段階
    // ------------------------------------------------------------------

    /** 作業場。のこぎり屋根。 */
    val FACTORY_1 = box(
        h = 20,
        skin = FACTORY,
        roof = { x, _, d ->
            when {
                d > 0.94f -> Palette.WALL_EDGE
                ((x / 6) and 1) == 0 -> Palette.METAL
                else -> Palette.FACTORY_ROOF
            }
        },
        wall = { u, v, _ ->
            if (u in 0.15f..0.60f && v < 0.5f) Palette.METAL_DARK else null   // シャッター
        },
    )

    /** 工場。煙突つき。 */
    val FACTORY_2 = box(
        h = 34,
        skin = FACTORY,
        roof = { x, y, d ->
            val dx = (x + 0.5f) - W / 2f
            val dy = (y + 0.5f) - TH / 2f
            when {
                d > 0.94f -> Palette.WALL_EDGE
                // 煙突
                abs(dx + 14f) < 4f && abs(dy) < 5f -> Palette.RED_DARK
                ((x / 5) and 1) == 0 -> Palette.METAL
                else -> Palette.FACTORY_ROOF
            }
        },
        wall = windows(2, 4, frame = Palette.FACTORY_RIGHT) { c, _ -> c % 2 == 0 },
    )

    /** 大規模な工場。 */
    val FACTORY_3 = box(
        h = 48,
        skin = FACTORY,
        roof = { x, y, d ->
            val dx = (x + 0.5f) - W / 2f
            val dy = (y + 0.5f) - TH / 2f
            when {
                d > 0.94f -> Palette.WALL_EDGE
                abs(dx + 16f) < 5f && abs(dy) < 6f -> Palette.RED_DARK
                abs(dx - 12f) < 4f && abs(dy) < 5f -> Palette.RED_DARK
                ((x / 5) and 1) == 0 -> Palette.METAL
                else -> Palette.FACTORY_ROOF
            }
        },
        wall = windows(4, 5, frame = Palette.FACTORY_RIGHT) { c, r -> hash(c, r, 41) % 3 != 0 },
    )

    // ------------------------------------------------------------------
    // 施設
    // ------------------------------------------------------------------

    /** 火力発電所。太い煙突と、赤白の帯。 */
    val POWER_COAL = box(
        h = 40,
        skin = Skin(Palette.WALL_ROOF, Palette.WALL_LEFT, Palette.WALL_RIGHT),
        roof = { x, y, d ->
            val dx = (x + 0.5f) - W / 2f
            val dy = (y + 0.5f) - TH / 2f
            val stack = Math.hypot(dx.toDouble() + 12, dy.toDouble() * 2) < 9
            when {
                d > 0.94f -> Palette.WALL_EDGE
                stack -> Palette.METAL_DARK
                else -> Palette.WALL_ROOF
            }
        },
        wall = { u, v, _ ->
            when {
                v < 0.3f && u in 0.1f..0.5f -> Palette.METAL_DARK
                // 赤白の帯
                v in 0.55f..0.65f -> if (((u * 10).toInt() and 1) == 0) Palette.RED else Palette.WHITE
                else -> null
            }
        },
    )

    /** 太陽光発電。青いパネルが並ぶ。 */
    val POWER_SOLAR = box(
        h = 12,
        skin = Skin(Palette.METAL, Palette.METAL, Palette.METAL_DARK),
        roof = { x, y, d ->
            when {
                d > 0.94f -> Palette.METAL_DARK
                // パネルの格子
                ((x / 5) + (y / 3)) % 2 == 0 -> Palette.SKY_DEEP
                else -> Palette.WINDOW_DARK
            }
        },
    )

    /** 公園。芝生と木立、小径。 */
    val PARK: Sprite = run {
        val h = 22
        val height = h + TH
        val data = ByteArray(W * height) { Pix.TRANSPARENT }

        // 芝生の菱形
        for (y in 0 until TH) for (x in 0 until W) {
            val dx = (x + 0.5f) - W / 2f
            val dy = (y + 0.5f) - TH / 2f
            val d = abs(dx) / (W / 2f) + abs(dy) / (TH / 2f)
            if (d > 1f) continue
            // 小径を斜めに通す
            val path = abs(dx / (W / 2f) - dy / (TH / 2f)) < 0.18f
            val c = when {
                d > 0.94f -> Palette.GRASS_EDGE
                path -> Palette.SAND
                ((x + y) / 4) % 3 == 0 -> Palette.GRASS_LIT
                else -> Palette.GRASS
            }
            data[(y + h) * W + x] = c.toByte()
        }

        fun tree(cx: Int, cy: Int, r: Int) {
            // 幹
            for (y in cy + r - 1 until cy + r + 5) {
                if (y in 0 until height) {
                    data[y * W + cx] = Palette.TRUNK.toByte()
                    if (cx + 1 < W) data[y * W + cx + 1] = Palette.TRUNK.toByte()
                }
            }
            // 葉。左上を明るく、右下を暗くして立体に。
            for (y in -r..r) for (x in -r - 1..r + 1) {
                val px = cx + x
                val py = cy + y
                if (px !in 0 until W || py !in 0 until height) continue
                val dd = (x * x).toFloat() / ((r + 1) * (r + 1)) + (y * y).toFloat() / (r * r)
                if (dd > 1f) continue
                val c = when {
                    dd > 0.82f -> Palette.TREE_DARK
                    x < -r / 3 && y < 0 -> Palette.TREE_LIT
                    x > r / 3 || y > r / 3 -> Palette.TREE_DARK
                    else -> Palette.TREE
                }
                data[py * W + px] = c.toByte()
            }
        }
        tree(18, 14, 9)
        tree(42, 20, 7)
        tree(30, 8, 6)
        Sprite(W, height, data)
    }

    /** 警察署。青い看板と車寄せ。 */
    val POLICE = box(
        h = 30,
        roof = { _, _, d -> if (d > 0.94f) Palette.WALL_EDGE else Palette.OFFICE_ROOF },
        wall = { u, v, side ->
            when {
                // 入口
                side < 0 && u in 0.18f..0.48f && v < 0.42f -> Palette.GLASS_LIT
                // 看板
                v in 0.52f..0.62f && u in 0.12f..0.60f -> Palette.SKY_DEEP
                else -> windows(2, 4) { c, r -> hash(c, r, 61) % 3 != 0 }(u, v, side)
            }
        },
    )

    /** 消防署。赤い大きなシャッター。 */
    val FIRE = box(
        h = 30,
        skin = Skin(Palette.WALL_ROOF, Palette.WALL_LEFT, Palette.WALL_RIGHT),
        roof = { _, _, d -> if (d > 0.94f) Palette.WALL_EDGE else Palette.RED_DARK },
        wall = { u, v, _ ->
            when {
                v < 0.5f && u < 0.66f -> Palette.RED            // シャッター
                v in 0.5f..0.56f && u < 0.70f -> Palette.WALL_EDGE
                v in 0.60f..0.78f && u in 0.14f..0.56f -> Palette.WINDOW_LIT
                else -> null
            }
        },
    )

    /** 学校。横に長い窓の並び。 */
    val SCHOOL = box(
        h = 32,
        roof = { x, y, d ->
            val dx = abs((x + 0.5f) - W / 2f)
            val dy = abs((y + 0.5f) - TH / 2f)
            when {
                d > 0.94f -> Palette.WALL_EDGE
                // 時計塔
                dx < 5f && dy < 4f -> Palette.GOLD
                else -> Palette.HOUSE_ROOF_DARK
            }
        },
        wall = { u, v, _ ->
            when {
                u > 0.92f -> null
                v in 0.26f..0.42f || v in 0.54f..0.70f ->
                    if (((u * 14).toInt() and 1) == 0) Palette.WINDOW_LIT else Palette.WALL_EDGE
                else -> null
            }
        },
    )

    /** 病院。屋根に赤十字。 */
    val HOSPITAL = box(
        h = 40,
        skin = Skin(Palette.WHITE, Palette.WALL_ROOF, Palette.WALL_LEFT),
        roof = { x, y, d ->
            val dx = abs((x + 0.5f) - W / 2f)
            val dy = abs((y + 0.5f) - TH / 2f)
            when {
                d > 0.94f -> Palette.WALL_EDGE
                dx < 4f && dy < 10f -> Palette.RED       // 十字（縦）
                dy < 3.5f && dx < 18f -> Palette.RED     // 十字（横）
                else -> Palette.WHITE
            }
        },
        wall = windows(4, 4) { c, r -> hash(c, r, 53) % 4 != 0 },
    )

    /**
     * 電気が来ていない印。建物の上に小さく浮かべる。
     * 黄色い稲妻にして、街並みの中でも見つけやすくする。
     */
    val NO_POWER: Sprite = run {
        val w = 10
        val h = 12
        val shape = listOf(
            "   ##   ",
            "  ####  ",
            " ###    ",
            "######  ",
            " #####  ",
            "   ###  ",
            "  ###   ",
            " ###    ",
            "  #     ",
        )
        val data = ByteArray(w * h) { Pix.TRANSPARENT }
        for ((yy, row) in shape.withIndex()) {
            for ((xx, ch) in row.withIndex()) {
                if (ch != '#') continue
                val px = xx + 1
                val py = yy + 1
                if (px in 0 until w && py in 0 until h) {
                    data[py * w + px] = Palette.GOLD.toByte()
                }
            }
        }
        // 縁取り
        val outlined = data.copyOf()
        for (y in 0 until h) for (x in 0 until w) {
            if (data[y * w + x] != Pix.TRANSPARENT) continue
            val near = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).any { (dx, dy) ->
                val nx = x + dx
                val ny = y + dy
                nx in 0 until w && ny in 0 until h && data[ny * w + nx] != Pix.TRANSPARENT
            }
            if (near) outlined[y * w + x] = Palette.BLACK.toByte()
        }
        Sprite(w, h, outlined)
    }
}
