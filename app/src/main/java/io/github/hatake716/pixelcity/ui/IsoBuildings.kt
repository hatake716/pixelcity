package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.CustomStyle
import io.github.hatake716.pixelcity.game.TileKind
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

    private val recolourCache = HashMap<Any, Sprite>()

    /**
     * [style] に合わせて色を変えた絵を返す。作った結果は使い回す。
     *
     * 「じぶんで きめる」のときは [custom] の色を使う。色を変えるたびに
     * 別の絵になるので、使い回しの鍵には色の組み合わせも含める。
     */
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
        /** ベランダを付けるか。集合住宅らしくなる。 */
        balcony: Boolean = false,
        lit: (Int, Int) -> Boolean,
    ): (Float, Float, Int) -> Int? = { u, v, side ->
        if (u > 0.93f || v > 0.95f || v < 0.05f) null
        else {
            val col = (u * cols).toInt().coerceIn(0, cols - 1)
            val row = ((1f - v) * rows).toInt().coerceIn(0, rows - 1)
            val cu = (u * cols) - col
            val cv = ((1f - v) * rows) - row
            val isLit = lit(col + if (side < 0) 0 else cols, row)
            when {
                // ベランダの手すり
                balcony && cv in 0.80f..0.92f && cu in 0.10f..0.90f ->
                    if (((cu * 14).toInt() and 1) == 0) frame else Palette.WALL_ROOF
                // 窓の桟（縦と横）。4倍の面積があるので描き分けられる。
                cu in 0.46f..0.54f && cv in 0.22f..0.78f -> frame
                cv in 0.46f..0.54f && cu in 0.20f..0.80f -> frame
                // ガラス。上半分をわずかに明るくして、映り込みを出す。
                cu in 0.20f..0.80f && cv in 0.22f..0.78f ->
                    when {
                        !isLit -> Palette.WINDOW_DARK
                        cv < 0.4f -> Palette.WHITE
                        else -> Palette.WINDOW_LIT
                    }
                // 窓枠
                cu in 0.14f..0.86f && cv in 0.16f..0.84f -> frame
                else -> null
            }
        }
    }

    // ------------------------------------------------------------------
    // 街並みに変化をつける部品
    //
    // 同じ形の箱が並ぶと、どれだけ数があっても「街」に見えない。
    // 実際の街並みは、屋根の形・窓の割りつけ・1階の店・屋上の設備が
    // 棟ごとに違うから、にぎやかに見える。
    // ここでは、その違いを作るための部品を用意する。
    // ------------------------------------------------------------------

    /**
     * 切妻屋根。菱形の上に、三角の山をのせる。
     *
     * [ridge] は棟の向き。0 なら左右に、1 なら奥手前に傾く。
     * 平らな屋根の建物ばかりだと、住宅地が倉庫街に見えてしまう。
     */
    private fun gableRoof(
        skin: Skin, ridge: Int = 0, tile: Boolean = true,
    ): (Int, Int, Float) -> Int? = { x, y, _ ->
        val dx = (x + 0.5f) - W / 2f
        val dy = (y + 0.5f) - TH / 2f
        // 棟からの距離。棟に近いほど高い（＝明るい）
        val t = if (ridge == 0) abs(dy) / (TH / 2f) else abs(dx) / (W / 2f)
        when {
            t < 0.08f -> skin.roof                       // 棟木
            t < 0.55f -> if (ridge == 0) skin.roof else skin.left
            else -> skin.right
        }?.let { base ->
            // 瓦の筋。横に並べると、屋根らしく見える。
            if (tile && ((if (ridge == 0) x else y) / 6) % 2 == 0) darken(base) else base
        }
    }

    /**
     * マンサード屋根（腰折れ屋根）。
     * パリの街並みでよく見る、上が平らで下が急に落ちる形。
     */
    private fun mansardRoof(skin: Skin): (Int, Int, Float) -> Int? = { x, y, d ->
        when {
            d < 0.45f -> skin.roof                       // 上の平らなところ
            d < 0.52f -> skin.edge                       // 折れ目
            else -> {
                // 急な斜面。屋根窓（ドーマー）を点々と入れる。
                val around = ((x + y) / 22) % 3
                if (around == 0 && d in 0.62f..0.86f) Palette.WINDOW_LIT else skin.left
            }
        }
    }

    /**
     * 屋上の設備。給水塔・室外機・煙突・階段室。
     *
     * 平らな屋根がそのままだと、のっぺりして見える。
     * 参考にした街の絵は、どの屋根にも何かが載っている。
     */
    private fun roofClutter(
        skin: Skin, seed: Int,
    ): (Int, Int, Float) -> Int? = { x, y, d ->
        if (d > 0.88f) null
        else {
            val cx = W / 2
            val cy = TH / 2
            var c: Int? = null
            // 給水塔（丸い樽と脚）
            if (seed % 3 == 0) {
                val tx = x - (cx - W / 5)
                val ty = y - (cy - TH / 8)
                if (tx * tx + ty * ty * 4 < (W / 12) * (W / 12)) {
                    c = if (tx < 0) Palette.TRUNK else Palette.GRIT_ROOF
                }
            }
            // 室外機の列
            if (seed % 3 == 1) {
                val gx = (x - cx + W / 4) / (W / 22)
                if (gx in 0..2 && abs(y - cy) < TH / 10 && (x - cx + W / 4) % (W / 22) > 2) {
                    c = Palette.METAL
                }
            }
            // 階段室（小さな箱）
            if (seed % 3 == 2) {
                if (x in (cx + W / 8)..(cx + W / 8 + W / 12) &&
                    y in (cy - TH / 10)..(cy + TH / 12)
                ) {
                    c = if (x < cx + W / 8 + W / 24) skin.left else skin.right
                }
            }
            c
        }
    }

    /** その色を一段暗くする。瓦や板の筋を入れるのに使う。 */
    private fun darken(c: Int): Int = when (c) {
        Palette.HOUSE_ROOF -> Palette.HOUSE_ROOF_DARK
        Palette.EURO_ROOF -> Palette.EURO_ROOF_DARK
        Palette.RURAL_ROOF -> Palette.RURAL_ROOF_DARK
        Palette.GRIT_ROOF -> Palette.GRIT_ROOF_DARK
        Palette.JP_ROOF -> Palette.JP_ROOF_DARK
        Palette.WALL_ROOF -> Palette.WALL_LEFT
        Palette.OFFICE_ROOF -> Palette.OFFICE_LEFT
        Palette.FACTORY_ROOF -> Palette.FACTORY_LEFT
        else -> c
    }

    /**
     * 1階を店にする壁。
     *
     * 参考にした街の絵は、どの通りも1階が店になっている。
     * 大きなショーウィンドウと、その上のひさし（日よけ）を描く。
     * [awning] を変えると、ひさしの色が棟ごとに変わる。
     */
    private fun shopFront(
        base: (Float, Float, Int) -> Int?,
        awning: Int,
        /** 建物の高さに対する、1階ぶんの割合。 */
        floorFrac: Float = 0.30f,
    ): (Float, Float, Int) -> Int? = { u, v, side ->
        if (v > floorFrac) base(u, (v - floorFrac) / (1f - floorFrac), side)
        else {
            val f = v / floorFrac          // 1階のなかでの高さ 0..1
            when {
                // ひさし。縞模様にする。
                f in 0.72f..0.88f && u < 0.92f ->
                    if (((u * 22).toInt() and 1) == 0) awning else Palette.WHITE
                // ひさしの下の影
                f in 0.66f..0.72f -> Palette.WALL_EDGE
                // ショーウィンドウ
                f in 0.12f..0.64f && u in 0.06f..0.90f -> {
                    val pane = (u * 5).toInt()
                    val pu = (u * 5) - pane
                    when {
                        pu < 0.10f -> Palette.WALL_EDGE          // 窓の桟
                        f < 0.20f -> Palette.WALL_EDGE           // 下端の枠
                        // 中に並ぶ品。点々と色を変えて、賑わいを出す。
                        f in 0.24f..0.40f && pu in 0.25f..0.75f ->
                            when ((pane + (if (side < 0) 0 else 5)) % 4) {
                                0 -> Palette.RED
                                1 -> Palette.GOLD
                                2 -> Palette.TREE
                                else -> Palette.SKY
                            }
                        else -> Palette.WINDOW_LIT
                    }
                }
                f < 0.10f -> Palette.WALL_EDGE                   // 歩道との境
                else -> null
            }
        }
    }

    /**
     * 外階段（非常階段）。壁の手前に、踏み板と手すりを重ねる。
     *
     * 参考にした街の絵の、赤レンガの建物によく付いている。
     */
    private fun fireEscape(
        base: (Float, Float, Int) -> Int?, rows: Int,
    ): (Float, Float, Int) -> Int? = { u, v, side ->
        // 右の面にだけ付ける。両面だと、うるさくなる。
        if (side > 0 && u in 0.14f..0.52f && v < 0.92f) {
            val r = ((1f - v) * rows).toInt()
            val rv = ((1f - v) * rows) - r
            when {
                rv < 0.10f -> Palette.METAL_DARK                     // 踏み板
                rv in 0.10f..0.26f && ((u * 30).toInt() and 1) == 0 -> Palette.METAL
                // 縦の柱
                u in 0.14f..0.17f || u in 0.49f..0.52f -> Palette.METAL_DARK
                else -> base(u, v, side)
            }
        } else base(u, v, side)
    }

    // ------------------------------------------------------------------
    // 住宅 1〜3段階
    // ------------------------------------------------------------------

    /** 一戸建て。切妻の瓦屋根、玄関と小窓。 */
    val HOUSE_1: Sprite by lazy { box(
        h = 128,
        skin = HOUSE,
        roof = { x, y, d ->
            val dx = (x + 0.5f) - W / 2f
            val dy = (y + 0.5f) - TH / 2f
            when {
                d > 0.96f -> Palette.HOUSE_ROOF_DARK
                // 棟（頂上の線）
                abs(dy) < 8f -> Palette.STONE_DARK
                // 瓦の筋。4倍の面積があるので、1枚ずつ描ける。
                ((x / 12) + (y / 8)) % 2 == 0 ->
                    if (dx < 0) Palette.HOUSE_ROOF else Palette.HOUSE_ROOF_DARK
                dx < 0 -> Palette.HOUSE_ROOF_DARK
                else -> Palette.RED_DARK
            }
        },
        wall = { u, v, side ->
            when {
                // 玄関（枠つき）
                side < 0 && u in 0.32f..0.52f && v < 0.50f -> Palette.TRUNK
                side < 0 && u in 0.28f..0.56f && v < 0.56f -> Palette.WALL_EDGE
                // 窓（桟つき）
                u in 0.66f..0.70f && v in 0.34f..0.72f -> Palette.WALL_EDGE
                u in 0.62f..0.82f && v in 0.36f..0.70f -> Palette.WINDOW_LIT
                u in 0.58f..0.86f && v in 0.32f..0.74f -> Palette.WALL_EDGE
                // 雨どい
                u > 0.90f -> Palette.STONE_DARK
                // 土台
                v < 0.08f -> Palette.STONE_DARK
                else -> null
            }
        },
    ) }

    /** 低層の集合住宅。 */
    val HOUSE_2: Sprite by lazy { box(
        h = 272,
        skin = HOUSE,
        roof = { _, _, d -> if (d > 0.94f) Palette.HOUSE_ROOF_DARK else Palette.HOUSE_ROOF },
        wall = windows(5, 4, balcony = true) { c, r -> hash(c, r, 11) % 5 != 0 },
    ) }

    /** 高層の集合住宅。屋上に給水塔。 */
    val HOUSE_3: Sprite by lazy { box(
        h = 448,
        skin = HOUSE,
        roof = { x, y, d ->
            val dx = abs((x + 0.5f) - W / 2f)
            val dy = abs((y + 0.5f) - TH / 2f)
            when {
                d > 0.94f -> Palette.HOUSE_ROOF_DARK
                // 屋上の塔屋
                dx < 36f && dy < 20f -> Palette.WALL_LEFT
                else -> Palette.HOUSE_ROOF_DARK
            }
        },
        wall = windows(8, 5, balcony = true) { c, r -> hash(c, r, 23) % 4 != 0 },
    ) }

    // ------------------------------------------------------------------
    // 商業 1〜3段階
    // ------------------------------------------------------------------

    /** 商店。大きなショーウィンドウと日よけ。 */
    val SHOP_1: Sprite by lazy { box(
        h = 160,
        skin = OFFICE,
        roof = { _, _, d -> if (d > 0.94f) Palette.WALL_EDGE else Palette.OFFICE_ROOF },
        wall = { u, v, _ ->
            when {
                // ショーウィンドウ（枠つき）
                v < 0.38f && u in 0.06f..0.88f -> Palette.GLASS_LIT
                v < 0.42f && u < 0.92f -> Palette.WALL_EDGE
                // 縞の日よけ
                v in 0.42f..0.54f && u < 0.92f ->
                    if (((u * 16).toInt() and 1) == 0) Palette.RED else Palette.WHITE
                // 看板
                v in 0.60f..0.78f && u in 0.12f..0.80f ->
                    if (((u * 20).toInt() + (v * 30).toInt()) % 5 == 0) Palette.GOLD
                    else Palette.SKY_DEEP
                v in 0.56f..0.82f && u in 0.08f..0.84f -> Palette.WALL_EDGE
                else -> null
            }
        },
    ) }

    /** 雑居ビル。1階が店、上が事務所。 */
    val SHOP_2: Sprite by lazy { box(
        h = 336,
        skin = OFFICE,
        wall = { u, v, side ->
            when {
                v < 0.24f && u < 0.90f -> Palette.GLASS_LIT
                v in 0.24f..0.30f && u < 0.92f -> Palette.WALL_EDGE
                else -> windows(4, 4) { c, r -> hash(c, r, 31) % 6 != 0 }(u, v, side)
            }
        },
    ) }

    /** オフィスビル。全面ガラスの縦帯。 */
    val SHOP_3: Sprite by lazy { box(
        h = 592,
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
                    val col = (u * 8).toInt()
                    val cu = (u * 8) - col
                    val floor = (v * 30).toInt()
                    val fv = (v * 30) - floor
                    when {
                        cu < 0.16f -> Palette.OFFICE_RIGHT        // 縦の柱
                        fv < 0.18f -> Palette.OFFICE_ROOF         // 階の帯
                        // ガラス。上のほうを明るくして、空の映り込みを出す。
                        fv < 0.45f -> Palette.GLASS_LIT
                        else -> Palette.GLASS
                    }
                }
            }
        },
    ) }

    // ------------------------------------------------------------------
    // 工業 1〜3段階
    // ------------------------------------------------------------------

    /** 作業場。のこぎり屋根。 */
    val FACTORY_1: Sprite by lazy { box(
        h = 160,
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
    ) }

    /** 工場。煙突つき。 */
    val FACTORY_2: Sprite by lazy { box(
        h = 272,
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
    ) }

    /** 大規模な工場。 */
    val FACTORY_3: Sprite by lazy { box(
        h = 384,
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
    ) }

    // ------------------------------------------------------------------
    // 施設
    // ------------------------------------------------------------------

    /** 火力発電所。太い煙突と、赤白の帯。 */
    val POWER_COAL: Sprite by lazy { box(
        h = 320,
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
    ) }

    /** 太陽光発電。青いパネルが並ぶ。 */
    val POWER_SOLAR: Sprite by lazy { box(
        h = 96,
        skin = Skin(Palette.METAL, Palette.METAL, Palette.METAL_DARK),
        roof = { x, y, d ->
            when {
                d > 0.94f -> Palette.METAL_DARK
                // パネルの格子
                ((x / 5) + (y / 3)) % 2 == 0 -> Palette.SKY_DEEP
                else -> Palette.WINDOW_DARK
            }
        },
    ) }

    /**
     * 風力発電。細い塔と3枚の羽根。
     * 箱ではないので、地面の菱形の上へ直接組み立てる。
     */
    val POWER_WIND: Sprite = run {
        val h = 156
        val height = h + TH
        val data = ByteArray(W * height) { Pix.TRANSPARENT }
        fun set(x: Int, y: Int, c: Int) {
            if (x in 0 until W && y in 0 until height) data[y * W + x] = c.toByte()
        }

        // 基礎の菱形
        for (y in 0 until TH) for (x in 0 until W) {
            val dx = (x + 0.5f) - W / 2f
            val dy = (y + 0.5f) - TH / 2f
            val d = abs(dx) / (W / 2f) + abs(dy) / (TH / 2f)
            if (d > 0.55f) continue
            set(x, y + h, if (d > 0.45f) Palette.STONE_DARK else Palette.STONE)
        }

        val cx = W / 2
        val baseY = h + TH / 2
        val topY = 16
        // 塔。下が太く上が細い。
        for (y in topY..baseY) {
            val t = (baseY - y).toFloat() / (baseY - topY)
            val half = ((1f - t) * 2.4f + 1.2f).toInt()
            for (x in -half..half) {
                set(cx + x, y, if (x < 0) Palette.WHITE else Palette.METAL)
            }
            set(cx - half, y, Palette.METAL_DARK)
            set(cx + half, y, Palette.METAL_DARK)
        }
        // 軸
        for (y in topY - 3..topY + 2) for (x in -3..3) set(cx + x, y, Palette.METAL)

        // 羽根3枚。120度ずつ。
        val hub = topY
        for (k in 0 until 3) {
            val a = Math.toRadians(90.0 + k * 120.0)
            for (r in 4..30) {
                val bx = cx + (Math.cos(a) * r).toInt()
                val by = hub - (Math.sin(a) * r * 0.62).toInt()
                // 根元を太く、先を細く
                val w = if (r < 14) 2 else 1
                for (o in -w..w) {
                    set(bx, by + o, Palette.WHITE)
                }
                set(bx, by - w - 1, Palette.METAL_DARK)
            }
        }
        Sprite(W, height, data)
    }

    /** 公園。芝生と木立、小径。 */
    val PARK: Sprite = run {
        val h = 44
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
    val POLICE: Sprite by lazy { box(
        h = 240,
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
    ) }

    /** 消防署。赤い大きなシャッター。 */
    val FIRE: Sprite by lazy { box(
        h = 240,
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
    ) }

    /** 学校。横に長い窓の並び。 */
    val SCHOOL: Sprite by lazy { box(
        h = 256,
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
    ) }

    /** 病院。屋根に赤十字。 */
    val HOSPITAL: Sprite by lazy { box(
        h = 320,
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
    ) }

    // ------------------------------------------------------------------
    // v2 の施設
    // ------------------------------------------------------------------

    /** 診療所。白い小さな建物に緑の十字。 */
    val CLINIC: Sprite by lazy { box(
        h = 176,
        skin = Skin(Palette.WHITE, Palette.WALL_ROOF, Palette.WALL_LEFT),
        roof = { x, y, d ->
            val dx = abs((x + 0.5f) - W / 2f)
            val dy = abs((y + 0.5f) - TH / 2f)
            when {
                d > 0.94f -> Palette.WALL_EDGE
                dx < 3f && dy < 7f -> Palette.TREE
                dy < 2.5f && dx < 12f -> Palette.TREE
                else -> Palette.WHITE
            }
        },
        wall = { u, v, _ ->
            if (v < 0.4f && u in 0.2f..0.5f) Palette.GLASS_LIT else null
        },
    ) }

    /** 給水塔。細い脚の上に丸いタンク。 */
    val WATER_TOWER: Sprite = run {
        val h = 88
        val height = h + TH
        val data = ByteArray(W * height) { Pix.TRANSPARENT }
        fun set(x: Int, y: Int, c: Int) {
            if (x in 0 until W && y in 0 until height) data[y * W + x] = c.toByte()
        }
        // 基礎
        for (y in 0 until TH) for (x in 0 until W) {
            val dx = (x + 0.5f) - W / 2f
            val dy = (y + 0.5f) - TH / 2f
            val d = abs(dx) / (W / 2f) + abs(dy) / (TH / 2f)
            if (d <= 0.6f) set(x, y + h, if (d > 0.5f) Palette.STONE_DARK else Palette.STONE)
        }
        val cx = W / 2
        // 4本の脚
        for (dx in intArrayOf(-10, -4, 4, 10)) {
            for (y in 18..h + TH / 2) set(cx + dx, y, Palette.METAL_DARK)
        }
        // タンク
        for (y in 0..20) {
            val t = y / 20f
            val r = (Math.sin(Math.PI * (0.25 + t * 0.5)) * 15).toInt()
            for (x in -r..r) {
                val c = when {
                    abs(x) >= r - 1 -> Palette.METAL_DARK
                    x < -r / 3 -> Palette.METAL_LIT
                    else -> Palette.METAL
                }
                set(cx + x, y + 4, c)
            }
        }
        // 帯
        for (x in -12..12) set(cx + x, 13, Palette.SKY_DEEP)
        Sprite(W, height, data)
    }

    /** 浄水場。四角い沈殿池が並ぶ。 */
    val WATER_PLANT: Sprite by lazy { box(
        h = 128,
        skin = Skin(Palette.WALL_ROOF, Palette.WALL_LEFT, Palette.WALL_RIGHT),
        roof = { x, y, d ->
            when {
                d > 0.94f -> Palette.WALL_EDGE
                // 水をたたえた池
                ((x / 9) + (y / 5)) % 2 == 0 -> Palette.WATER
                else -> Palette.WATER_DARK
            }
        },
    ) }

    /** 下水処理場。円形の池。 */
    val SEWAGE_PLANT: Sprite by lazy { box(
        h = 112,
        skin = Skin(Palette.STONE, Palette.STONE_DARK, Palette.STONE_EDGE),
        roof = { x, y, d ->
            val dx = ((x + 0.5f) - W / 2f) / (W / 2f)
            val dy = ((y + 0.5f) - TH / 2f) / (TH / 2f)
            val r = Math.sqrt((dx * dx + dy * dy).toDouble())
            when {
                d > 0.94f -> Palette.STONE_EDGE
                r < 0.55 -> Palette.TREE_DARK      // 濁った水
                r < 0.68 -> Palette.STONE_DARK
                else -> Palette.STONE
            }
        },
    ) }

    /** 埋立地。土を盛った山。 */
    val LANDFILL: Sprite = run {
        val h = 40
        val height = h + TH
        val data = ByteArray(W * height) { Pix.TRANSPARENT }
        for (y in 0 until height) for (x in 0 until W) {
            val dx = (x + 0.5f) - W / 2f
            // 下へ行くほど広がる山
            val t = (y.toFloat() / height).coerceIn(0f, 1f)
            val halfW = (W / 2f) * (0.25f + t * 0.75f)
            if (abs(dx) > halfW) continue
            val dyBase = TH / 2f + h * t
            if (y < height - TH || abs(dx) / halfW + abs((y + 0.5f) - (height - TH / 2f)) / (TH / 2f) <= 1f) {
                val c = when {
                    abs(dx) > halfW - 2 -> Palette.STONE_EDGE
                    dx < 0 -> Palette.SAND_DARK
                    else -> Palette.STONE_DARK
                }
                data[y * W + x] = c.toByte()
            }
        }
        // ゴミの色をまだらに
        for (y in 2 until height - 4) for (x in 4 until W - 4) {
            if (data[y * W + x] == Pix.TRANSPARENT) continue
            if ((x * 7 + y * 13) % 11 == 0) data[y * W + x] = Palette.TREE_DARK.toByte()
            if ((x * 5 + y * 3) % 17 == 0) data[y * W + x] = Palette.RED_DARK.toByte()
        }
        Sprite(W, height, data)
    }

    /** 焼却場。高い煙突。 */
    val INCINERATOR: Sprite by lazy { box(
        h = 288,
        skin = Skin(Palette.WALL_ROOF, Palette.WALL_LEFT, Palette.WALL_RIGHT),
        roof = { x, y, d ->
            val dx = (x + 0.5f) - W / 2f
            val dy = (y + 0.5f) - TH / 2f
            val stack = Math.hypot(dx.toDouble() - 10, dy.toDouble() * 2) < 8
            when {
                d > 0.94f -> Palette.WALL_EDGE
                stack -> Palette.RED_DARK
                else -> Palette.METAL_DARK
            }
        },
        wall = { u, v, _ ->
            when {
                v < 0.3f && u in 0.1f..0.5f -> Palette.METAL_DARK
                v in 0.5f..0.58f -> Palette.GOLD_DARK
                else -> null
            }
        },
    ) }

    /** リサイクル施設。緑の屋根に矢印の輪。 */
    val RECYCLING: Sprite by lazy { box(
        h = 160,
        skin = Skin(Palette.TREE, Palette.WALL_LEFT, Palette.WALL_RIGHT),
        roof = { x, y, d ->
            val dx = ((x + 0.5f) - W / 2f) / (W / 2f)
            val dy = ((y + 0.5f) - TH / 2f) / (TH / 2f)
            val r = Math.sqrt((dx * dx + dy * dy).toDouble())
            when {
                d > 0.94f -> Palette.WALL_EDGE
                r in 0.45..0.7 -> Palette.WHITE     // 輪
                else -> Palette.TREE
            }
        },
    ) }

    /** バス停。小さな屋根とベンチ。 */
    val BUS_STOP: Sprite = run {
        val h = 32
        val height = h + TH
        val data = ByteArray(W * height) { Pix.TRANSPARENT }
        fun set(x: Int, y: Int, c: Int) {
            if (x in 0 until W && y in 0 until height) data[y * W + x] = c.toByte()
        }
        // 舗装
        for (y in 0 until TH) for (x in 0 until W) {
            val dx = (x + 0.5f) - W / 2f
            val dy = (y + 0.5f) - TH / 2f
            val d = abs(dx) / (W / 2f) + abs(dy) / (TH / 2f)
            if (d <= 0.8f) set(x, y + h, if (d > 0.7f) Palette.ROAD_DARK else Palette.ROAD)
        }
        val cx = W / 2
        // 支柱
        for (y in 6..h + TH / 2) { set(cx - 12, y, Palette.METAL_DARK); set(cx + 12, y, Palette.METAL_DARK) }
        // 屋根
        for (x in -16..16) for (y in 2..5) set(cx + x, y, if (y < 4) Palette.SKY_DEEP else Palette.METAL_DARK)
        // ベンチ
        for (x in -9..9) for (y in h + 2..h + 5) set(cx + x, y, Palette.TRUNK)
        Sprite(W, height, data)
    }

    /** 地下鉄の駅。地上の入口。 */
    val SUBWAY_STATION: Sprite by lazy { box(
        h = 144,
        skin = Skin(Palette.STONE_LIT, Palette.STONE, Palette.STONE_DARK),
        roof = { _, _, d -> if (d > 0.94f) Palette.STONE_EDGE else Palette.STONE_LIT },
        wall = { u, v, side ->
            when {
                // 階段の入口
                side < 0 && u in 0.2f..0.6f && v < 0.55f -> Palette.BLACK
                v in 0.6f..0.72f && u < 0.8f -> Palette.SKY_DEEP   // 看板
                else -> null
            }
        },
    ) }

    /** 空港。滑走路と管制塔。 */
    val AIRPORT: Sprite = run {
        val h = 60
        val height = h + TH
        val data = ByteArray(W * height) { Pix.TRANSPARENT }
        fun set(x: Int, y: Int, c: Int) {
            if (x in 0 until W && y in 0 until height) data[y * W + x] = c.toByte()
        }
        // 滑走路（地面の菱形）
        for (y in 0 until TH) for (x in 0 until W) {
            val dx = (x + 0.5f) - W / 2f
            val dy = (y + 0.5f) - TH / 2f
            val d = abs(dx) / (W / 2f) + abs(dy) / (TH / 2f)
            if (d > 1f) continue
            val u = (dx / (W / 2f) + dy / (TH / 2f)) / 2f
            val c = when {
                d > 0.94f -> Palette.GRASS_DARK
                abs(dy) < 2.5f -> if (((u * 10).toInt() and 1) == 0) Palette.WHITE else Palette.ROAD_DARK
                else -> Palette.ROAD
            }
            set(x, y + h, c)
        }
        val cx = W / 2 + 16
        // 管制塔
        for (y in 8..h + TH / 2) {
            for (x in -4..4) set(cx + x, y, if (x < 0) Palette.WALL_ROOF else Palette.WALL_LEFT)
        }
        for (y in 2..9) for (x in -7..7) {
            set(cx + x, y, if (y < 5) Palette.GLASS_LIT else Palette.WALL_EDGE)
        }
        Sprite(W, height, data)
    }

    /** 港。クレーンとコンテナ。 */
    val SEAPORT: Sprite = run {
        val h = 52
        val height = h + TH
        val data = ByteArray(W * height) { Pix.TRANSPARENT }
        fun set(x: Int, y: Int, c: Int) {
            if (x in 0 until W && y in 0 until height) data[y * W + x] = c.toByte()
        }
        // 岸壁
        for (y in 0 until TH) for (x in 0 until W) {
            val dx = (x + 0.5f) - W / 2f
            val dy = (y + 0.5f) - TH / 2f
            val d = abs(dx) / (W / 2f) + abs(dy) / (TH / 2f)
            if (d <= 1f) set(x, y + h, if (d > 0.9f) Palette.STONE_EDGE else Palette.STONE_DARK)
        }
        // コンテナを積む
        val colors = intArrayOf(Palette.RED, Palette.SKY_DEEP, Palette.GOLD, Palette.TREE)
        var n = 0
        for (row in 0 until 3) for (col in 0 until 3) {
            val bx = 10 + col * 15 - row * 4
            val by = h - 4 - row * 7
            val c = colors[n++ % colors.size]
            for (y in by until by + 6) for (x in bx until bx + 13) {
                val edge = y == by || y == by + 5 || x == bx || x == bx + 12
                set(x, y, if (edge) Palette.BLACK else c)
            }
        }
        // クレーン
        val cx = W - 14
        for (y in 2..h + TH / 2) set(cx, y, Palette.METAL_DARK)
        for (x in cx - 18..cx + 4) set(x, 4, Palette.METAL)
        Sprite(W, height, data)
    }

    /** 送電線。鉄塔と電線。 */
    val POWER_LINE: Sprite = run {
        val h = 68
        val height = h + TH
        val data = ByteArray(W * height) { Pix.TRANSPARENT }
        fun set(x: Int, y: Int, c: Int) {
            if (x in 0 until W && y in 0 until height) data[y * W + x] = c.toByte()
        }
        val cx = W / 2
        // 4本脚がすぼまる鉄塔
        for (y in 4..h + TH / 2) {
            val t = (y - 4).toFloat() / (h + TH / 2 - 4)
            val half = (2 + t * 9).toInt()
            set(cx - half, y, Palette.METAL_DARK)
            set(cx + half, y, Palette.METAL_DARK)
            if ((y % 7) == 0) {
                for (x in -half..half) set(cx + x, y, Palette.METAL)
            }
        }
        // 腕木
        for (arm in intArrayOf(8, 16)) {
            for (x in -20..20) set(cx + x, arm, Palette.METAL)
            set(cx - 20, arm - 1, Palette.METAL_DARK)
            set(cx + 20, arm - 1, Palette.METAL_DARK)
        }
        // 電線を左右へ
        for (x in 0 until W) {
            val sag = ((x - cx) * (x - cx)) / 260
            set(x, 8 + sag, Palette.BLACK)
            set(x, 16 + sag, Palette.BLACK)
        }
        Sprite(W, height, data)
    }

    /** 施設の種類からドット絵を引く。地図にもツールバーにも使う。 */
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

    /**
     * 電気が来ていない印。建物の上に小さく浮かべる。
     * 黄色い稲妻にして、街並みの中でも見つけやすくする。
     */
    val NO_POWER: Sprite = run {
        val w = 10
        val h = 24
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

    // ------------------------------------------------------------------
    // 街並みのバリエーション
    //
    // 同じ段階でも、棟ごとに違う絵を使う。
    // 1種類しかないと、どれだけ数が増えても「同じ建物の反復」に見える。
    // 参考にした街の絵は、隣り合う建物がことごとく違う。
    // ------------------------------------------------------------------

    /** 棟ごとに変える壁の色。同じ形でも、色が違えば別の建物に見える。 */
    private val HOUSE_SKINS = arrayOf(
        Skin(Palette.HOUSE_ROOF, Palette.HOUSE_LEFT, Palette.HOUSE_RIGHT),
        Skin(Palette.EURO_ROOF, Palette.EURO_WALL, Palette.EURO_WALL_DARK),
        Skin(Palette.RURAL_ROOF, Palette.RURAL_WALL, Palette.RURAL_WALL_DARK),
        Skin(Palette.JP_ROOF, Palette.JP_WALL, Palette.JP_WALL_DARK),
        Skin(Palette.GRIT_ROOF, Palette.GRIT_WALL, Palette.GRIT_WALL_DARK),
    )

    private val SHOP_SKINS = arrayOf(
        Skin(Palette.OFFICE_ROOF, Palette.OFFICE_LEFT, Palette.OFFICE_RIGHT),
        Skin(Palette.PRIME_ROOF, Palette.PRIME_LEFT, Palette.PRIME_RIGHT),
        Skin(Palette.WALL_ROOF, Palette.WALL_LEFT, Palette.WALL_RIGHT),
        Skin(Palette.EURO_ROOF, Palette.EURO_WALL, Palette.EURO_WALL_DARK),
        Skin(Palette.STONE, Palette.STONE_LIT, Palette.STONE_DARK),
    )

    private val FACTORY_SKINS = arrayOf(
        Skin(Palette.FACTORY_ROOF, Palette.FACTORY_LEFT, Palette.FACTORY_RIGHT),
        Skin(Palette.GRIT_ROOF, Palette.GRIT_WALL, Palette.GRIT_WALL_DARK),
        Skin(Palette.METAL, Palette.METAL_LIT, Palette.METAL_DARK),
        Skin(Palette.TRUNK, Palette.SAND_DARK, Palette.STONE_EDGE),
    )

    /** ひさしの色。店ごとに変える。 */
    private val AWNINGS = intArrayOf(
        Palette.RED, Palette.TREE, Palette.SKY_DEEP, Palette.GOLD,
        Palette.EURO_ROOF, Palette.PATINA,
    )

    /**
     * 住宅の棟。[variant] で屋根の形と色が変わる。
     *
     * 低層は切妻とマンサードを混ぜる。平らな屋根だけだと、
     * 住宅地が倉庫街に見えてしまう。
     */
    private fun houseVariant(stage: Int, variant: Int): Sprite {
        val skin = HOUSE_SKINS[variant % HOUSE_SKINS.size]
        return when (stage) {
            1 -> {
                // 一戸建て。屋根の形を3通りに振る。
                val h = 96 + (variant % 3) * 32
                box(
                    h = h,
                    skin = skin,
                    roof = when (variant % 3) {
                        0 -> gableRoof(skin, ridge = 0)
                        1 -> gableRoof(skin, ridge = 1)
                        else -> mansardRoof(skin)
                    },
                    wall = { u, v, side ->
                        when {
                            // 玄関
                            side < 0 && u in 0.30f..0.50f && v < 0.42f -> Palette.TRUNK
                            side < 0 && u in 0.26f..0.54f && v < 0.48f -> skin.edge
                            // 窓。棟ごとに数を変える。
                            else -> windows(
                                1 + variant % 2, 2,
                                frame = skin.edge,
                            ) { c, r -> hash(c, r, variant * 7) % 4 != 0 }(u, v, side)
                        }
                    },
                )
            }
            2 -> {
                val h = 224 + (variant % 3) * 48
                box(
                    h = h,
                    skin = skin,
                    roof = if (variant % 2 == 0) mansardRoof(skin) else roofClutter(skin, variant),
                    wall = if (variant % 3 == 0) {
                        // 外階段つき。赤レンガの集合住宅らしくなる。
                        fireEscape(
                            windows(4 + variant % 2, 4, frame = skin.edge, balcony = true) {
                                c, r -> hash(c, r, 11 + variant) % 5 != 0
                            },
                            rows = 4,
                        )
                    } else {
                        windows(4 + variant % 3, 4, frame = skin.edge, balcony = true) {
                            c, r -> hash(c, r, 11 + variant) % 5 != 0
                        }
                    },
                )
            }
            else -> {
                val h = 400 + (variant % 4) * 64
                box(
                    h = h,
                    skin = skin,
                    roof = roofClutter(skin, variant),
                    wall = windows(7 + variant % 3, 5, frame = skin.edge, balcony = variant % 2 == 0) {
                        c, r -> hash(c, r, 23 + variant) % 4 != 0
                    },
                )
            }
        }
    }

    /**
     * 商業の棟。1階はどれも店。
     *
     * 参考にした街の絵は、どの通りも1階が店になっている。
     * ひさしの色を棟ごとに変えると、通りがにぎやかに見える。
     */
    private fun shopVariant(stage: Int, variant: Int): Sprite {
        val skin = SHOP_SKINS[variant % SHOP_SKINS.size]
        val awning = AWNINGS[variant % AWNINGS.size]
        return when (stage) {
            1 -> box(
                h = 128 + (variant % 3) * 32,
                skin = skin,
                roof = if (variant % 3 == 0) gableRoof(skin, ridge = variant % 2)
                else roofClutter(skin, variant),
                wall = shopFront(
                    windows(1, 3, frame = skin.edge) { c, r -> hash(c, r, variant) % 3 != 0 },
                    awning, floorFrac = 0.55f,
                ),
            )
            2 -> box(
                h = 288 + (variant % 3) * 64,
                skin = skin,
                roof = roofClutter(skin, variant),
                wall = shopFront(
                    windows(3 + variant % 2, 4, frame = skin.edge) {
                        c, r -> hash(c, r, 5 + variant) % 4 != 0
                    },
                    awning, floorFrac = 0.28f,
                ),
            )
            else -> box(
                h = 512 + (variant % 4) * 96,
                skin = skin,
                roof = roofClutter(skin, variant),
                wall = shopFront(
                    // 高層はガラス張り。窓を細かく割る。
                    windows(9 + variant % 4, 6, frame = skin.edge) {
                        c, r -> hash(c, r, 17 + variant) % 5 != 0
                    },
                    awning, floorFrac = 0.16f,
                ),
            )
        }
    }

    /** 工業の棟。屋根は平らで、煙突や設備が載る。 */
    private fun factoryVariant(stage: Int, variant: Int): Sprite {
        val skin = FACTORY_SKINS[variant % FACTORY_SKINS.size]
        return when (stage) {
            1 -> box(
                // 高さを6通りに散らす。棟ごとの違いが、いちばん目につく。
                h = 112 + (variant % 6) * 26,
                skin = skin,
                roof = when (variant % 3) {
                    // のこぎり屋根。工場らしい形。
                    0 -> { x, _, d ->
                        if (d > 0.94f) skin.edge
                        else if ((x / 40) % 2 == 0) skin.roof else skin.left
                    }
                    // 筋の向きを変えたのこぎり屋根
                    1 -> { _, y, d ->
                        if (d > 0.94f) skin.edge
                        else if ((y / 20) % 2 == 0) skin.roof else skin.left
                    }
                    // 平らな屋根に設備を載せる
                    else -> roofClutter(skin, variant)
                },
                wall = { u, v, side ->
                    when {
                        // 大きなシャッター。位置と幅を棟ごとに変える。
                        side > 0 && u in (0.14f + (variant % 3) * 0.08f)..
                            (0.62f + (variant % 3) * 0.08f) && v < 0.52f -> {
                            if (((v * 40).toInt() and 1) == 0) Palette.METAL else Palette.METAL_DARK
                        }
                        v < 0.06f -> skin.edge
                        else -> null
                    }
                },
            )
            2 -> box(
                h = 224 + (variant % 3) * 48,
                skin = skin,
                roof = roofClutter(skin, variant + 1),
                wall = windows(3, 5, frame = skin.edge) { c, r -> hash(c, r, 31 + variant) % 3 != 0 },
            )
            else -> box(
                h = 320 + (variant % 3) * 64,
                skin = skin,
                roof = roofClutter(skin, variant + 2),
                wall = windows(5 + variant % 2, 6, frame = skin.edge) {
                    c, r -> hash(c, r, 41 + variant) % 3 != 0
                },
            )
        }
    }

    /**
     * 棟ごとの絵は重いので、作ったものを使い回す。
     *
     * 別の糸から書き込むので、[java.util.concurrent.ConcurrentHashMap] を使う。
     */
    private val variantCache = java.util.concurrent.ConcurrentHashMap<Int, Sprite>()

    /**
     * まだ絵ができていないときに出す、間に合わせの箱。
     *
     * 1棟あたり 0.1〜0.2 秒かかるので、54種を画面を止めて作ると
     * 10秒近く固まる。作っているあいだは、この簡単な箱で場所を示し、
     * できあがった順に差し替える。
     */
    private val placeholders = java.util.concurrent.ConcurrentHashMap<Int, Sprite>()

    /** 絵を作る糸。1本にして、機械を占有しないようにする。 */
    private val builder by lazy {
        java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "pixelcity-sprites").apply {
                isDaemon = true
                // 画面より低い優先度にする。作っているあいだも操作は滑らかに。
                priority = Thread.MIN_PRIORITY
            }
        }
    }

    /** いま作っている最中のもの。二重に頼まないようにする。 */
    private val inFlight = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<Int, Boolean>(),
    )

    /** 絵ができあがったときに呼ぶ。画面を描き直させる。 */
    var onSpriteReady: (() -> Unit)? = null

    /**
     * その絵が、もう本物になっているか。
     * 試験で「間に合わせ」と比べてしまわないために使う。
     */
    fun isReady(kind: TileKind, stage: Int, variant: Int): Boolean {
        val v = ((variant % VARIANTS) + VARIANTS) % VARIANTS
        return variantCache.containsKey(kind.ordinal * 1000 + stage * 100 + v)
    }

    /**
     * 間に合わせの箱。色と高さだけ合わせた、装飾のない箱。
     * 作るのが軽いので、画面を止めずに出せる。
     */
    private fun placeholderFor(kind: TileKind, stage: Int, variant: Int): Sprite {
        val key = kind.ordinal * 1000 + stage * 100 + variant
        return placeholders.getOrPut(key) {
            val skin = when (kind) {
                TileKind.ZONE_R -> HOUSE_SKINS[variant % HOUSE_SKINS.size]
                TileKind.ZONE_C -> SHOP_SKINS[variant % SHOP_SKINS.size]
                else -> FACTORY_SKINS[variant % FACTORY_SKINS.size]
            }
            val h = when (stage) {
                1 -> 112
                2 -> 240
                else -> 432
            }
            box(h = h, skin = skin)
        }
    }

    /** 1つの段階あたり、何通りの見た目を用意するか。 */
    const val VARIANTS = 6

    /**
     * その区分・段階・番号の建物。
     *
     * [variant] はタイルの位置から決める。同じ場所なら毎回同じ絵になるので、
     * 街を眺め直しても建物が入れ替わらない。
     */
    fun zoneBuilding(kind: TileKind, stage: Int, variant: Int): Sprite {
        val v = ((variant % VARIANTS) + VARIANTS) % VARIANTS
        val key = kind.ordinal * 1000 + stage * 100 + v
        variantCache[key]?.let { return it }

        // まだできていない。別の糸で作り始め、いまは間に合わせを返す。
        if (inFlight.add(key)) {
            builder.execute {
                val sp = when (kind) {
                    TileKind.ZONE_R -> houseVariant(stage, v)
                    TileKind.ZONE_C -> shopVariant(stage, v)
                    else -> factoryVariant(stage, v)
                }
                variantCache[key] = sp
                inFlight.remove(key)
                onSpriteReady?.invoke()
            }
        }
        return placeholderFor(kind, stage, v)
    }

}
