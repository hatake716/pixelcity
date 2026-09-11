package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.CustomStyle

/**
 * 自分の様式の色を選ぶ画面。
 *
 * 左に「どこの色か」を並べ、選んだ場所に対して、
 * 右下のパレットから色を割り当てる。
 * いま選んでいる場所と色が、つねに見えるようにしてある。
 */
class StyleEditor(private val text: GbText) {

    /** いま編集している場所。 */
    var slot: CustomStyle.Slot = CustomStyle.Slot.HOUSE_ROOF

    private companion object {
        const val MARGIN = 12
        /** 場所の一覧の1行の高さ。 */
        const val ROW_H = 20
        /** パレットの1マス。 */
        const val SWATCH = 22
        const val SWATCH_GAP = 3
        /** パレットの横に並ぶ数。 */
        const val COLS = 8
    }

    /**
     * 選べる色。パレット全部を出すと多すぎるので、
     * 建物に使って様になるものだけを並べる。
     */
    private val choices: IntArray = intArrayOf(
        // 白・灰・黒
        Palette.WHITE, Palette.STONE_LIT, Palette.STONE, Palette.STONE_DARK,
        Palette.STONE_EDGE, Palette.METAL_LIT, Palette.METAL, Palette.METAL_DARK,
        // 赤・茶
        Palette.RED, Palette.RED_DARK, Palette.HOUSE_ROOF, Palette.HOUSE_ROOF_DARK,
        Palette.EURO_ROOF, Palette.EURO_ROOF_DARK, Palette.TRUNK, Palette.GRIT_ROOF,
        // 砂・黄
        Palette.SAND_LIT, Palette.SAND, Palette.SAND_DARK, Palette.GOLD,
        Palette.GOLD_DARK, Palette.ANCIENT_ROOF, Palette.ANCIENT_WALL, Palette.WINDOW_LIT,
        // 緑
        Palette.TREE_LIT, Palette.TREE, Palette.TREE_DARK, Palette.GRASS_LIT,
        Palette.GRASS, Palette.GRASS_DARK, Palette.RURAL_ROOF, Palette.RURAL_WALL,
        // 青・水
        Palette.SKY, Palette.SKY_DEEP, Palette.WATER_LIT, Palette.WATER,
        Palette.WATER_DARK, Palette.GLASS_LIT, Palette.GLASS, Palette.FUTURE_GLOW,
        // 建物の既定色
        Palette.WALL_ROOF, Palette.WALL_LEFT, Palette.WALL_RIGHT, Palette.WALL_EDGE,
        Palette.OFFICE_ROOF, Palette.OFFICE_LEFT, Palette.OFFICE_RIGHT, Palette.WINDOW_DARK,
        // 様式の色
        Palette.PRIME_ROOF, Palette.PRIME_LEFT, Palette.PRIME_GLASS, Palette.FUTURE_ROOF,
        Palette.FUTURE_WALL, Palette.JP_ROOF, Palette.JP_WALL, Palette.BLACK,
    )

    private fun listTop(): Int = 56
    private fun rowY(i: Int): Int = listTop() + i * ROW_H

    /** パレットの上端。場所の一覧の下に置く。 */
    private fun paletteTop(): Int = rowY(CustomStyle.Slot.entries.size) + 12

    private fun swatchX(i: Int): Int = MARGIN + (i % COLS) * (SWATCH + SWATCH_GAP)
    private fun swatchY(i: Int): Int = paletteTop() + 22 + (i / COLS) * (SWATCH + SWATCH_GAP)

    fun backButtonY(logicalH: Int): Int = logicalH - 46
    private fun resetButtonY(logicalH: Int): Int = logicalH - 82

    fun draw(pixels: PixelCanvas, custom: CustomStyle, logicalH: Int) {
        pixels.clear(Palette.UI_BG)

        text.textSize = 18
        text.drawCentered(pixels, "いろを えらぶ", GameView.LOGICAL_W / 2, 10, Palette.UI_ACCENT)
        text.textSize = 11
        text.drawCentered(
            pixels, "ばしょを えらんでから、したの いろを おします",
            GameView.LOGICAL_W / 2, 34, Palette.UI_DIM,
        )

        // --- どこの色か ---
        for ((i, sl) in CustomStyle.Slot.entries.withIndex()) {
            val y = rowY(i)
            val on = sl == slot
            pixels.fillRect(MARGIN, y, GameView.LOGICAL_W - MARGIN * 2, ROW_H - 2,
                if (on) Palette.UI_ACCENT else Palette.UI_BG_LIGHT)
            pixels.drawRect(MARGIN, y, GameView.LOGICAL_W - MARGIN * 2, ROW_H - 2, Palette.UI_LINE)
            text.textSize = 12
            text.draw(pixels, sl.label, MARGIN + 8, y + 3,
                if (on) Palette.UI_BG else Palette.UI_TEXT)
            // いま割り当てている色
            val c = custom[sl]
            val cx = GameView.LOGICAL_W - MARGIN - 30
            pixels.fillRect(cx, y + 2, 26, ROW_H - 6, c)
            pixels.drawRect(cx, y + 2, 26, ROW_H - 6, Palette.BLACK)
        }

        // --- 色の見本 ---
        val pt = paletteTop()
        text.textSize = 12
        text.draw(pixels, "「${slot.label}」の いろ", MARGIN, pt, Palette.UI_ACCENT)

        val current = custom[slot]
        for (i in choices.indices) {
            val x = swatchX(i)
            val y = swatchY(i)
            if (y + SWATCH > logicalH - 90) break
            pixels.fillRect(x, y, SWATCH, SWATCH, choices[i])
            pixels.drawRect(x, y, SWATCH, SWATCH, Palette.BLACK)
            if (choices[i] == current) {
                // 選んでいる色を二重の枠で示す
                pixels.drawRect(x - 2, y - 2, SWATCH + 4, SWATCH + 4, Palette.UI_ACCENT)
                pixels.drawRect(x - 3, y - 3, SWATCH + 6, SWATCH + 6, Palette.UI_ACCENT)
            }
        }

        // --- もとにもどす ---
        val ry = resetButtonY(logicalH)
        val rw = 120
        val rx = (GameView.LOGICAL_W - rw) / 2
        pixels.fillRect(rx, ry, rw, 26, Palette.UI_BG_LIGHT)
        pixels.drawRect(rx, ry, rw, 26, Palette.UI_LINE)
        text.textSize = 13
        text.drawCentered(pixels, "もとに もどす", GameView.LOGICAL_W / 2, ry + 5, Palette.UI_TEXT)

        // --- もどる ---
        val by = backButtonY(logicalH)
        val bw = 90
        val bx = (GameView.LOGICAL_W - bw) / 2
        pixels.fillRect(bx, by, bw, 28, Palette.UI_BG_LIGHT)
        pixels.drawRect(bx, by, bw, 28, Palette.UI_LINE)
        text.textSize = 15
        text.drawCentered(pixels, "もどる", GameView.LOGICAL_W / 2, by + 5, Palette.UI_TEXT)
    }

    /** その座標にある場所。なければ null。 */
    fun slotAt(lx: Int, ly: Int): CustomStyle.Slot? {
        for ((i, sl) in CustomStyle.Slot.entries.withIndex()) {
            val y = rowY(i)
            if (ly >= y && ly < y + ROW_H - 2) return sl
        }
        return null
    }

    /** その座標にある色。なければ null。 */
    fun colourAt(lx: Int, ly: Int, logicalH: Int): Int? {
        for (i in choices.indices) {
            val x = swatchX(i)
            val y = swatchY(i)
            if (y + SWATCH > logicalH - 90) break
            if (lx >= x && lx < x + SWATCH && ly >= y && ly < y + SWATCH) return choices[i]
        }
        return null
    }

    fun resetTapped(lx: Int, ly: Int, logicalH: Int): Boolean {
        val ry = resetButtonY(logicalH)
        return ly >= ry && ly < ry + 26
    }
}
