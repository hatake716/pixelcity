package io.github.hatake716.pixelcity.ui

import android.graphics.Color

/**
 * 緑の階調パレット。
 *
 * 初代ゲームボーイ（DMG）の緑4色を両端と中間に据えたまま、
 * 階調を16段に増やしてある。高精細なドット絵で立体感と陰影を出すには
 * 4階調では足りないため。色相はDMGの緑を保っているので、
 * 見た目の印象は変わらない。
 *
 * 任天堂の素材・ロゴ・書体は一切使用していない。色の数値だけを扱う。
 */
object GbPalette {
    /** DMG の4色。スプライトの基準として残す。 */
    const val LIGHTEST = 0xFF9BBC0FL.toInt()
    const val LIGHT = 0xFF8BAC0FL.toInt()
    const val DARK = 0xFF306230L.toInt()
    const val DARKEST = 0xFF0F380FL.toInt()

    /** 階調の数。スプライトの値はこの範囲に収まる。 */
    const val LEVELS = 16

    /**
     * 索引 0（最も明るい）から 15（最も暗い）までの階調。
     * DMGの4色を 0 / 4 / 10 / 15 に置き、その間を補間してある。
     */
    val byIndex: IntArray = buildPalette()

    private fun buildPalette(): IntArray {
        // DMG の4色を、16段のどこに置くか
        val anchors = listOf(
            0 to LIGHTEST,
            4 to LIGHT,
            10 to DARK,
            15 to DARKEST,
        )
        val out = IntArray(LEVELS)
        for (i in 0 until LEVELS) {
            // i を挟む2つの基準色を探して補間する
            val upper = anchors.first { it.first >= i }
            val lower = anchors.last { it.first <= i }
            out[i] = if (upper.first == lower.first) {
                lower.second
            } else {
                val t = (i - lower.first).toFloat() / (upper.first - lower.first)
                lerp(lower.second, upper.second, t)
            }
        }
        return out
    }

    private fun lerp(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    fun of(index: Int): Int = byIndex[index.coerceIn(0, LEVELS - 1)]

    /** 画面の外側（ベゼル）に使う、やや暗い縁。 */
    val BEZEL: Int = Color.rgb(28, 40, 22)

    // --- スプライトの文字表記で使う代表値 ---
    // '.'=0 '-'=3 '+'=7 '#'=11 '@'=15 のように、
    // 従来の4階調とも対応が取れるようにしてある。
    const val L0 = 0
    const val L1 = 2
    const val L2 = 4
    const val L3 = 6
    const val L4 = 8
    const val L5 = 10
    const val L6 = 12
    const val L7 = 15
}
