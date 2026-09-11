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
    const val STATUS_HEIGHT = 52
    const val TOOLBAR_HEIGHT = 60

    /** ツールバーに並べるもの。 */
    val TOOLS: List<Tool> = listOf(
        Tool(TileKind.ROAD, "どうろ"),
        Tool(TileKind.ZONE_R, "じゅうたく"),
        Tool(TileKind.ZONE_C, "しょうぎょう"),
        Tool(TileKind.ZONE_I, "こうぎょう"),
        Tool(TileKind.POWER_COAL, "かりょく"),
        Tool(TileKind.POWER_SOLAR, "たいようこう"),
        Tool(TileKind.PARK, "こうえん"),
        Tool(TileKind.POLICE, "けいさつ"),
        Tool(TileKind.FIRE, "しょうぼう"),
        Tool(TileKind.SCHOOL, "がっこう"),
        Tool(TileKind.HOSPITAL, "びょういん"),
        Tool(TileKind.EMPTY, "こわす"),
    )

    data class Tool(val kind: TileKind, val label: String)

    /** ツールの並ぶ1マスの大きさ。 */
    const val TOOL_SIZE = 28
    const val TOOL_GAP = 4

    fun toolCount(): Int = TOOLS.size

    /** [index] 番目のツールの、ツールバー内での x 座標。 */
    fun toolX(index: Int): Int = TOOL_GAP + index * (TOOL_SIZE + TOOL_GAP)

    /** ツールバー全体の幅。横にはみ出す分はスクロールする。 */
    fun toolStripWidth(): Int = toolX(TOOLS.size)

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
