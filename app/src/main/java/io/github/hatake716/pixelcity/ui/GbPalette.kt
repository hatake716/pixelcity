package io.github.hatake716.pixelcity.ui

import android.graphics.Color

/**
 * 初代ゲームボーイ（DMG）の緑4階調。
 *
 * 色の数値のみを使い、任天堂の素材・ロゴ・書体は一切使用しない。
 * 中間色は作らず、濃淡はディザ（市松模様）で表現する。
 */
object GbPalette {
    const val LIGHTEST = 0xFF9BBC0FL.toInt()
    const val LIGHT = 0xFF8BAC0FL.toInt()
    const val DARK = 0xFF306230L.toInt()
    const val DARKEST = 0xFF0F380FL.toInt()

    /** スプライトの値 0..3 を色へ。 */
    val byIndex = intArrayOf(LIGHTEST, LIGHT, DARK, DARKEST)

    fun of(index: Int): Int = byIndex[index.coerceIn(0, 3)]

    /** 画面の外側（ベゼル）に使う、やや暗い縁。 */
    val BEZEL: Int = Color.rgb(28, 40, 22)
}
