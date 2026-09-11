package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.ui.GameView
import io.github.hatake716.pixelcity.ui.Hud
import io.github.hatake716.pixelcity.ui.InfoPanel
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 画面の配置。
 *
 * 通知の欄・操作の欄と重なると、読めない・押せないので、
 * 内側に収まっていることを数で確かめる。
 * 指で押すものの大きさも、ここで見張る。
 *
 * 描く側（[InfoPanel]）は Context が要るので作れない。
 * 位置を決める式だけを、同じ値でなぞって確かめる。
 */
class LayoutTest {

    /** 端末でよくある欄の高さ（論理ピクセル）。 */
    private val insetTop = 38
    private val insetBottom = 32

    /**
     * 指で押すものが、十分に大きいこと。
     *
     * Android の目安は 48dp。420dpi の端末では約126画素で、
     * 論理では約63にあたる（実機では論理の2倍で描かれる）。
     * ここでは、その半分の 32 を下限として見張る。
     */
    @Test
    fun `everything you tap is big enough`() {
        val minimum = 32
        assertTrue("category buttons are ${Hud.CATEGORY_H}", Hud.CATEGORY_H >= minimum)
        assertTrue("tool buttons are ${Hud.TOOL_SIZE}", Hud.TOOL_SIZE >= minimum)
        assertTrue("the tab row is ${InfoPanel.TAB_H}", InfoPanel.TAB_H >= minimum)
        // 帯そのものは、中身が収まる高さがあること
        assertTrue(
            "the toolbar cannot hold its rows",
            Hud.TOOLBAR_HEIGHT >= Hud.CATEGORY_H + Hud.TOOL_SIZE + 30,
        )
    }

    /**
     * じょうほう画面の中身が、通知の欄の下から始まること。
     *
     * `InfoPanel.tabY()` と同じ式。
     */
    @Test
    fun `the info panel starts below the notification bar`() {
        val tabY = insetTop + 44
        assertTrue("tabs are under the notification bar", tabY >= insetTop)
        val firstRow = tabY + InfoPanel.TAB_H + 12
        assertTrue("the first row is under the notification bar", firstRow >= insetTop)

        // 閉じる釦は、操作の欄の上（InfoPanel.closeButtonY と同じ式）
        val logicalH = 1200
        val closeY = logicalH - insetBottom - 52
        assertTrue(
            "the close button is under the navigation bar",
            closeY + 28 <= logicalH - insetBottom,
        )
    }

    /** 地図の見える高さが、十分に残っていること。 */
    @Test
    fun `the map still gets most of the screen`() {
        val logicalH = 1200
        val mapHeight = logicalH - insetTop - insetBottom -
            Hud.STATUS_HEIGHT - Hud.TOOLBAR_HEIGHT
        assertTrue("the map only gets $mapHeight of $logicalH", mapHeight > logicalH / 2)
    }

    /** 論理解像度が端末の大きさを割り切ること（余白が出ない）。 */
    @Test
    fun `the logical size divides the screen exactly`() {
        for ((w, h) in listOf(1080 to 2400, 1440 to 3120, 720 to 1600)) {
            val (scale, lw, lh) = GameView.layoutFor(w, h)
            assertTrue("$w: scale must be at least 1", scale >= 1)
            assertTrue("$w: ${lw * scale} leaves a margin", lw * scale == w)
            assertTrue("$h: ${lh * scale} leaves a margin", lh * scale == h)
        }
    }
}
