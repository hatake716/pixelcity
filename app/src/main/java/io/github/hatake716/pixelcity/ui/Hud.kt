package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.TileKind

/**
 * 画面上部の状態表示と、下部のツールバーの配置。
 *
 * 座標はすべて論理ピクセル。実際の当たり判定は [GameView] が
 * 同じ値を使って行うので、ここが配置の唯一の出どころになる。
 */
object Hud {
    /**
     * 上下の帯の高さ。
     *
     * 指で押すものは、48dp（この端末で約126px、論理で約63）を下回らないようにする。
     * それより小さいと、狙って押すのが難しくなる。
     */
    const val STATUS_HEIGHT = 76
    const val TOOLBAR_HEIGHT = 164

    /**
     * ツールの分類。
     *
     * 施設が28種になり、一列では収まらなくなった。
     * まず分類を選び、その中の道具を選ぶ二段構えにする。
     */
    enum class Category(val label: String) {
        ZONE("くかく"),
        TRANSPORT("こうつう"),
        POWER("でんりょく"),
        WATER("すいどう"),
        GARBAGE("ゴミ"),
        SERVICE("サービス"),
        GREEN("みどり"),
        DEMOLISH("こわす"),
    }

    data class Tool(val kind: TileKind, val label: String)

    /** 分類ごとの道具。 */
    val TOOLS_BY_CATEGORY: Map<Category, List<Tool>> = mapOf(
        Category.ZONE to listOf(
            Tool(TileKind.ZONE_R, "じゅうたく"),
            Tool(TileKind.ZONE_C, "しょうぎょう"),
            Tool(TileKind.ZONE_I, "こうぎょう"),
        ),
        Category.TRANSPORT to listOf(
            Tool(TileKind.ROAD, "どうろ"),
            Tool(TileKind.AVENUE, "おおどおり"),
            Tool(TileKind.HIGHWAY, "こうそく"),
            Tool(TileKind.RAIL, "せんろ"),
            Tool(TileKind.SUBWAY, "ちかてつ"),
            Tool(TileKind.BUS_STOP, "バスてい"),
            Tool(TileKind.SUBWAY_STATION, "えき"),
            Tool(TileKind.AIRPORT, "くうこう"),
            Tool(TileKind.SEAPORT, "みなと"),
        ),
        Category.POWER to listOf(
            Tool(TileKind.POWER_COAL, "かりょく"),
            Tool(TileKind.POWER_SOLAR, "たいようこう"),
            Tool(TileKind.POWER_WIND, "ふうりょく"),
            Tool(TileKind.POWER_LINE, "そうでんせん"),
        ),
        Category.WATER to listOf(
            Tool(TileKind.WATER_TOWER, "きゅうすいとう"),
            Tool(TileKind.WATER_PLANT, "じょうすいじょう"),
            Tool(TileKind.SEWAGE_PLANT, "げすいしょり"),
        ),
        Category.GARBAGE to listOf(
            Tool(TileKind.LANDFILL, "うめたてち"),
            Tool(TileKind.INCINERATOR, "しょうきゃくじょう"),
            Tool(TileKind.RECYCLING, "リサイクル"),
        ),
        Category.SERVICE to listOf(
            Tool(TileKind.POLICE, "けいさつ"),
            Tool(TileKind.FIRE, "しょうぼう"),
            Tool(TileKind.HOSPITAL, "びょういん"),
            Tool(TileKind.CLINIC, "しんりょうじょ"),
            Tool(TileKind.SCHOOL, "がっこう"),
        ),
        Category.GREEN to listOf(
            Tool(TileKind.PARK, "こうえん"),
            Tool(TileKind.FARM, "のうち"),
        ),
        Category.DEMOLISH to listOf(
            Tool(TileKind.EMPTY, "こわす"),
        ),
    )

    /** すべての道具。名前を引くのに使う。 */
    val TOOLS: List<Tool> = TOOLS_BY_CATEGORY.values.flatten()

    fun toolsIn(category: Category): List<Tool> = TOOLS_BY_CATEGORY[category].orEmpty()

    fun labelOf(kind: TileKind): String =
        TOOLS.firstOrNull { it.kind == kind }?.label ?: ""

    /** 分類の並ぶ1マスの大きさ。 */
    const val CATEGORY_W = 92
    const val CATEGORY_H = 44

    fun categoryX(index: Int): Int = TOOL_GAP + index * (CATEGORY_W + TOOL_GAP)

    /** ツールの並ぶ1マスの大きさ。 */
    const val TOOL_SIZE = 58
    const val TOOL_GAP = 8

    fun toolCount(): Int = TOOLS.size

    /** [index] 番目のツールの、ツールバー内での x 座標。 */
    fun toolX(index: Int): Int = TOOL_GAP + index * (TOOL_SIZE + TOOL_GAP)

    /** いま開いている分類の道具が並ぶ幅。 */
    fun toolStripWidth(category: Category): Int = toolX(toolsIn(category).size)

    /**
     * 需要バー。R/C/I を縦棒で表す。中央より上が「もっとほしい」、下が「余っている」。
     * 文字の下に置くので、記号は棒の上に小さく出す。
     */
    fun drawDemandBars(canvas: PixelCanvas, city: City, x: Int, y: Int, height: Int) {
        val values = intArrayOf(city.demandR, city.demandC, city.demandI)
        for (i in 0..2) {
            val bx = x + i * 12
            val mid = y + height / 2
            // 目盛りの中心線
            canvas.fillRect(bx, mid, 10, 1, Palette.UI_DIM)
            val v = values[i].coerceIn(-100, 100)
            val span = v * (height / 2 - 1) / 100
            when {
                // 需要が高い（もっとほしい）ほうを目立つ色に
                span > 0 -> canvas.fillRect(bx + 1, mid - span, 8, span, Palette.UI_ACCENT)
                span < 0 -> canvas.ditherRect(bx + 1, mid + 1, 8, -span, Palette.RED)
            }
            // 枠は上下だけ引いて、棒を見やすくする
            canvas.fillRect(bx, y, 10, 1, Palette.UI_DIM)
            canvas.fillRect(bx, y + height - 1, 10, 1, Palette.UI_DIM)
        }
    }
}
