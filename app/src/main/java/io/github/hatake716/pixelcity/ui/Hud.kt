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
    const val STATUS_HEIGHT = 150
    const val TOOLBAR_HEIGHT = 274

    /**
     * ツールの分類。
     *
     * 施設が28種になり、一列では収まらなくなった。
     * まず分類を選び、その中の道具を選ぶ二段構えにする。
     */
    enum class Category(val label: String) {
        ZONE("区画"),
        TRANSPORT("交通"),
        POWER("電力"),
        WATER("水道"),
        GARBAGE("ゴミ"),
        SERVICE("公共施設"),
        GREEN("緑地"),
        DEMOLISH("撤去"),
    }

    data class Tool(val kind: TileKind, val label: String)

    /** 分類ごとの道具。 */
    val TOOLS_BY_CATEGORY: Map<Category, List<Tool>> = mapOf(
        Category.ZONE to listOf(
            Tool(TileKind.ZONE_R, "住宅"),
            Tool(TileKind.ZONE_C, "商業"),
            Tool(TileKind.ZONE_I, "工業"),
        ),
        Category.TRANSPORT to listOf(
            Tool(TileKind.ROAD, "道路"),
            Tool(TileKind.AVENUE, "大通り"),
            Tool(TileKind.HIGHWAY, "高速道路"),
            Tool(TileKind.RAIL, "鉄道"),
            Tool(TileKind.SUBWAY, "地下鉄"),
            Tool(TileKind.BUS_STOP, "バス停"),
            Tool(TileKind.SUBWAY_STATION, "地下鉄駅"),
            Tool(TileKind.AIRPORT, "空港"),
            Tool(TileKind.SEAPORT, "港"),
        ),
        Category.POWER to listOf(
            Tool(TileKind.POWER_COAL, "火力"),
            Tool(TileKind.POWER_SOLAR, "太陽光"),
            Tool(TileKind.POWER_WIND, "風力"),
            Tool(TileKind.POWER_LINE, "送電線"),
        ),
        Category.WATER to listOf(
            Tool(TileKind.WATER_TOWER, "給水塔"),
            Tool(TileKind.WATER_PLANT, "浄水場"),
            Tool(TileKind.SEWAGE_PLANT, "下水処理"),
        ),
        Category.GARBAGE to listOf(
            Tool(TileKind.LANDFILL, "埋立地"),
            Tool(TileKind.INCINERATOR, "焼却場"),
            Tool(TileKind.RECYCLING, "リサイクル"),
        ),
        Category.SERVICE to listOf(
            Tool(TileKind.POLICE, "警察"),
            Tool(TileKind.FIRE, "消防"),
            Tool(TileKind.HOSPITAL, "病院"),
            Tool(TileKind.CLINIC, "診療所"),
            Tool(TileKind.SCHOOL, "学校"),
        ),
        Category.GREEN to listOf(
            Tool(TileKind.PARK, "公園"),
            Tool(TileKind.FARM, "農地"),
        ),
        Category.DEMOLISH to listOf(
            Tool(TileKind.EMPTY, "撤去"),
        ),
    )

    /** すべての道具。名前を引くのに使う。 */
    val TOOLS: List<Tool> = TOOLS_BY_CATEGORY.values.flatten()

    fun toolsIn(category: Category): List<Tool> = TOOLS_BY_CATEGORY[category].orEmpty()

    fun labelOf(kind: TileKind): String =
        TOOLS.firstOrNull { it.kind == kind }?.label ?: ""

    /** 分類の並ぶ1マスの大きさ。 */
    const val CATEGORY_W = 104
    const val CATEGORY_H = 64

    fun categoryX(index: Int): Int = TOOL_GAP + index * (CATEGORY_W + TOOL_GAP)

    /** ツールの並ぶ1マスの大きさ。 */
    const val TOOL_SIZE = 88
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
