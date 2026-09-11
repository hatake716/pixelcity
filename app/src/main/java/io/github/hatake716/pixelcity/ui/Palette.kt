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

    // --- 街の性格づけ用（v3）。同じ建物でも街ごとに色を変える ---
    /** 田園都市: 木造と漆喰の、あたたかい低層。 */
    const val RURAL_ROOF = 59
    const val RURAL_ROOF_DARK = 60
    const val RURAL_WALL = 61
    const val RURAL_WALL_DARK = 62
    /** 工業都市: すすけたレンガとトタン。 */
    const val GRIT_ROOF = 63
    const val GRIT_ROOF_DARK = 64
    const val GRIT_WALL = 65
    const val GRIT_WALL_DARK = 66
    /** 大都市: 磨かれたガラスと白い石。 */
    const val PRIME_ROOF = 67
    const val PRIME_LEFT = 68
    const val PRIME_RIGHT = 69
    const val PRIME_GLASS = 70

    // --- 様式を増やす（v4） ---
    /** 古代都市: 日干しレンガと石灰岩。砂漠の色。 */
    const val ANCIENT_ROOF = 71
    const val ANCIENT_ROOF_DARK = 72
    const val ANCIENT_WALL = 73
    const val ANCIENT_WALL_DARK = 74
    /** 未来都市: 発光する青と、白い合成素材。 */
    const val FUTURE_ROOF = 75
    const val FUTURE_WALL = 76
    const val FUTURE_WALL_DARK = 77
    const val FUTURE_GLOW = 78
    /** ヨーロッパ: 石畳の街。赤茶の瓦とクリーム色の石。 */
    const val EURO_ROOF = 79
    const val EURO_ROOF_DARK = 80
    const val EURO_WALL = 81
    const val EURO_WALL_DARK = 82
    /** 日本の地方都市: 灰色の瓦と、白い壁。 */
    const val JP_ROOF = 83
    const val JP_ROOF_DARK = 84
    const val JP_WALL = 85
    const val JP_WALL_DARK = 86

    // --- 世界の有名建築（v5） ---
    //
    // モニュメントはドームや像など曲面が多い。既存の石・砂の4段では
    // 段差が目立つので、素材ごとに5〜6段の階調を用意する。
    /** 白大理石。タージ・マハル、ピサの斜塔。 */
    const val MARBLE_HI = 87
    const val MARBLE_LIT = 88
    const val MARBLE = 89
    const val MARBLE_MID = 90
    const val MARBLE_DARK = 91
    const val MARBLE_EDGE = 92
    /** 銅の緑青。自由の女神。 */
    const val PATINA_HI = 93
    const val PATINA_LIT = 94
    const val PATINA = 95
    const val PATINA_DARK = 96
    const val PATINA_EDGE = 97
    /** 石灰岩（トラバーチン）。コロッセオ、凱旋門。 */
    const val TRAVERTINE_HI = 98
    const val TRAVERTINE_LIT = 99
    const val TRAVERTINE = 100
    const val TRAVERTINE_DARK = 101
    const val TRAVERTINE_EDGE = 102
    /** ピラミッドの石灰岩。日に焼けた砂の色。 */
    const val LIMESTONE_HI = 103
    const val LIMESTONE_LIT = 104
    const val LIMESTONE = 105
    const val LIMESTONE_DARK = 106
    const val LIMESTONE_EDGE = 107
    /** 東京タワーの鉄骨。朱色（インターナショナルオレンジ）と白。 */
    const val TOWER_ORANGE_LIT = 108
    const val TOWER_ORANGE = 109
    const val TOWER_ORANGE_DARK = 110
    const val TOWER_WHITE = 111
    /** ロンドンの砂岩。ビッグ・ベンの塔。 */
    const val LONDON_LIT = 112
    const val LONDON = 113
    const val LONDON_DARK = 114
    const val LONDON_EDGE = 115
    /** 金。時計の文字盤や尖塔の飾り。 */
    const val GILT_LIT = 116
    const val GILT = 117
    const val GILT_DARK = 118
    /** 影。地面に落ちる影と、窪んだ開口部。 */
    const val SHADOW = 119
    const val OPENING = 120

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
        // 田園: こげ茶の瓦、生成りの漆喰
        q(0xFF8A6A4A.toInt()), q(0xFF5E4630.toInt()),
        q(0xFFF0E4C8.toInt()), q(0xFFC8B894.toInt()),
        // 工業: すすけた赤レンガ、灰色のトタン
        q(0xFF7A4838.toInt()), q(0xFF522E24.toInt()),
        q(0xFF8E8478.toInt()), q(0xFF5E564E.toInt()),
        // 大都市: 白い石と、青みの強いガラス
        q(0xFFE8ECF0.toInt()), q(0xFFC0CAD6.toInt()),
        q(0xFF8E9AAA.toInt()), q(0xFF4A8EC8.toInt()),
        // 古代: 日干しレンガと石灰岩
        q(0xFFC89A5E.toInt()), q(0xFF96703E.toInt()),
        q(0xFFE0C48E.toInt()), q(0xFFB4966A.toInt()),
        // 未来: 白い合成素材と、光る青
        q(0xFFF4F8FC.toInt()), q(0xFFD0DCE8.toInt()),
        q(0xFF8CA0B8.toInt()), q(0xFF40E0E0.toInt()),
        // ヨーロッパ: 赤茶の瓦とクリーム色の石
        q(0xFFB05038.toInt()), q(0xFF7C3624.toInt()),
        q(0xFFEEE0C0.toInt()), q(0xFFC4B294.toInt()),
        // 日本の地方都市: いぶし銀の瓦と白い壁
        q(0xFF6E7480.toInt()), q(0xFF484E58.toInt()),
        q(0xFFF0EEE8.toInt()), q(0xFFC8C4BC.toInt()),
        // 白大理石（6段）
        q(0xFFFFFDF6.toInt()), q(0xFFF2ECDE.toInt()), q(0xFFDED6C2.toInt()),
        q(0xFFC0B69E.toInt()), q(0xFF988E78.toInt()), q(0xFF6C6354.toInt()),
        // 銅の緑青（5段）
        q(0xFFA6DEC6.toInt()), q(0xFF7AC2A4.toInt()), q(0xFF4E9C7E.toInt()),
        q(0xFF34785E.toInt()), q(0xFF1E4C3C.toInt()),
        // 石灰岩・トラバーチン（5段）
        q(0xFFF0DFB4.toInt()), q(0xFFD8C294.toInt()), q(0xFFBCA273.toInt()),
        q(0xFF917A52.toInt()), q(0xFF5E4E34.toInt()),
        // ピラミッドの石灰岩（5段）
        q(0xFFF4DFA8.toInt()), q(0xFFE0C486.toInt()), q(0xFFC4A462.toInt()),
        q(0xFF9C7E46.toInt()), q(0xFF6B5430.toInt()),
        // 東京タワーの鉄骨（朱と白）
        q(0xFFF07038.toInt()), q(0xFFD2451C.toInt()), q(0xFF8E2A10.toInt()),
        q(0xFFF4F2EC.toInt()),
        // ロンドンの砂岩（4段）
        q(0xFFEAD8A8.toInt()), q(0xFFCEB684.toInt()),
        q(0xFFA48C5E.toInt()), q(0xFF6E5C3C.toInt()),
        // 金（3段）
        q(0xFFFFE080.toInt()), q(0xFFE0AE30.toInt()), q(0xFF9C7418.toInt()),
        // 影と開口部
        q(0xFF2A2620.toInt()), q(0xFF1C1A18.toInt()),
    )

    fun of(index: Int): Int =
        if (index < 0 || index >= COLORS.size) COLORS[0] else COLORS[index]

    /** 画面の外側（ベゼル）。 */
    val BEZEL: Int = q(0xFF141820.toInt())

    val SIZE: Int get() = COLORS.size
}
