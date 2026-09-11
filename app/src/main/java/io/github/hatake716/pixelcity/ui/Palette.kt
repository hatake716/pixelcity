package io.github.hatake716.pixelcity.ui

/**
 * 16ビット機ふうの色。
 *
 * 当時の実機は 5bit/ch（各色32段、全32768色）から、1枚の絵に使える色を
 * 少数に絞って描いていた。ここでも同じ考え方をとり、
 *  - すべての色を 5bit/ch の格子に丸める（`quantize`）
 *  - 用途ごとに「屋根」「明るい面」「暗い面」の3〜4色を組にして持つ
 * ことで、時代の質感を出している。
 *
 * 色は用途ごとに名前をつけて [COLORS] に並べ、スプライトはその索引を持つ。
 * こうすると、スプライトの側は色を知らずに「屋根の色」とだけ書けばよく、
 * 配色をあとから変えても絵が壊れない。
 *
 * 任天堂の素材・ロゴ・書体・ROM は一切使用していない。色の数値だけを扱う。
 */
object Palette {

    /**
     * 5bit/ch へ丸める。16ビット機の色の刻みに合わせる。
     *
     * `android.graphics.Color` は単体テストでは中身のない stub なので、
     * ここでは使わず、ビット演算だけで組み立てている。
     * そうしないと、テストではすべての色が黒になってしまう。
     */
    private fun q(argb: Int): Int {
        val r = ((argb shr 16) and 0xFF) and 0xF8
        val g = ((argb shr 8) and 0xFF) and 0xF8
        val b = (argb and 0xFF) and 0xF8
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    // ------------------------------------------------------------------
    // 索引。スプライトはこの名前で色を指す。
    // 並びは保存形式には関わらないが、まとまりを保つため用途ごとに並べる。
    // ------------------------------------------------------------------

    const val TRANSPARENT = -1

    // 地面
    const val GRASS_LIT = 0
    const val GRASS = 1
    const val GRASS_DARK = 2
    const val GRASS_EDGE = 3

    // 水
    const val WATER_LIT = 4
    const val WATER = 5
    const val WATER_DARK = 6
    const val WATER_FOAM = 7

    // 砂浜
    const val SAND_LIT = 8
    const val SAND = 9
    const val SAND_DARK = 10

    // 舗装
    const val ROAD_LIT = 11
    const val ROAD = 12
    const val ROAD_DARK = 13
    const val ROAD_LINE = 14
    const val KERB = 15

    // 建物の面（光は左上から）
    const val WALL_ROOF = 16
    const val WALL_LEFT = 17
    const val WALL_RIGHT = 18
    const val WALL_EDGE = 19

    // 住宅（暖かいベージュ）
    const val HOUSE_ROOF = 20
    const val HOUSE_ROOF_DARK = 21
    const val HOUSE_LEFT = 22
    const val HOUSE_RIGHT = 23

    // 商業（青灰のガラス）
    const val OFFICE_ROOF = 24
    const val OFFICE_LEFT = 25
    const val OFFICE_RIGHT = 26
    const val GLASS_LIT = 27
    const val GLASS = 28

    // 工業（くすんだ赤茶）
    const val FACTORY_ROOF = 29
    const val FACTORY_LEFT = 30
    const val FACTORY_RIGHT = 31

    // 窓の明かり
    const val WINDOW_LIT = 32
    const val WINDOW_DARK = 33

    // 樹木
    const val TREE_LIT = 34
    const val TREE = 35
    const val TREE_DARK = 36
    const val TRUNK = 37

    // 石・金属（モニュメント用）
    const val STONE_LIT = 38
    const val STONE = 39
    const val STONE_DARK = 40
    const val STONE_EDGE = 41
    const val METAL_LIT = 42
    const val METAL = 43
    const val METAL_DARK = 44

    // 差し色
    const val RED = 45
    const val RED_DARK = 46
    const val GOLD = 47
    const val GOLD_DARK = 48
    const val WHITE = 49
    const val BLACK = 50

    // 画面まわり
    const val UI_BG = 51
    const val UI_BG_LIGHT = 52
    const val UI_LINE = 53
    const val UI_TEXT = 54
    const val UI_DIM = 55
    const val UI_ACCENT = 56
    const val SKY = 57
    const val SKY_DEEP = 58

    /** 索引から実際の色へ。 */
    val COLORS: IntArray = intArrayOf(
        // 地面
        q(0xFF8CBF4D.toInt()), q(0xFF6FA23C.toInt()),
        q(0xFF54812C.toInt()), q(0xFF3E6320.toInt()),
        // 水
        q(0xFF5FB4E5.toInt()), q(0xFF3D8FD1.toInt()),
        q(0xFF2A6CAD.toInt()), q(0xFFB8E4F5.toInt()),
        // 砂浜
        q(0xFFEDD9A3.toInt()), q(0xFFD6BC7E.toInt()), q(0xFFB39A60.toInt()),
        // 舗装
        q(0xFF9A9A94.toInt()), q(0xFF7C7C78.toInt()),
        q(0xFF5C5C5A.toInt()), q(0xFFE8E4C8.toInt()), q(0xFFAFAFA8.toInt()),
        // 建物の面（汎用）
        q(0xFFBFBFB6.toInt()), q(0xFF9E9E95.toInt()),
        q(0xFF6E6E68.toInt()), q(0xFF3C3C3A.toInt()),
        // 住宅
        q(0xFFC2543E.toInt()), q(0xFF8E3A2B.toInt()),
        q(0xFFE0C9A6.toInt()), q(0xFFB39C7C.toInt()),
        // 商業
        q(0xFF8FA3B8.toInt()), q(0xFF7C93AD.toInt()),
        q(0xFF556A82.toInt()), q(0xFFAEE0F2.toInt()), q(0xFF6C93AD.toInt()),
        // 工業
        q(0xFF8A6A55.toInt()), q(0xFF9E7C63.toInt()), q(0xFF6B513F.toInt()),
        // 窓
        q(0xFFFFE9A0.toInt()), q(0xFF3E4A5A.toInt()),
        // 樹木
        q(0xFF7ABF4F.toInt()), q(0xFF4E9438.toInt()),
        q(0xFF356B26.toInt()), q(0xFF6B4A2F.toInt()),
        // 石・金属
        q(0xFFE8E0CE.toInt()), q(0xFFC9BFA8.toInt()),
        q(0xFF9E937C.toInt()), q(0xFF6E6555.toInt()),
        q(0xFFD8DCE0.toInt()), q(0xFFA8AEB6.toInt()), q(0xFF70767E.toInt()),
        // 差し色
        q(0xFFD24B3E.toInt()), q(0xFF8E2E25.toInt()),
        q(0xFFEFC050.toInt()), q(0xFFB08A2E.toInt()),
        q(0xFFF4F4F0.toInt()), q(0xFF1A1A20.toInt()),
        // 画面まわり
        q(0xFF2A3550.toInt()), q(0xFF3C4A68.toInt()),
        q(0xFF8FA8D8.toInt()), q(0xFFF4F4F0.toInt()),
        q(0xFF9AA6BE.toInt()), q(0xFFEFC050.toInt()),
        q(0xFF7EB8E8.toInt()), q(0xFF3A6EA8.toInt()),
    )

    fun of(index: Int): Int =
        if (index < 0 || index >= COLORS.size) COLORS[0] else COLORS[index]

    /** 画面の外側（ベゼル）。 */
    val BEZEL: Int = q(0xFF141820.toInt())

    val SIZE: Int get() = COLORS.size
}
