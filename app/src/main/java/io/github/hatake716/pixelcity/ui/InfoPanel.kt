package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.MonthlyStat
import io.github.hatake716.pixelcity.game.CustomStyle
import io.github.hatake716.pixelcity.game.Ordinance

/**
 * 情報の画面。地図に重ねるのではなく、全面に出して読ませる。
 *
 * 「いま街がどうなっているか」を伝えるのが役目なので、
 * 数字を並べるだけでなく、推移のグラフと助言も添える。
 */
class InfoPanel(private val text: GbText) {

    /** 情報画面の見出し。 */
    enum class Tab(val label: String) {
        SUMMARY("がいよう"),
        GRAPH("すいい"),
        BUDGET("しゅうし"),
        MAP("データマップ"),
        ORDINANCE("じょうれい"),
        SETTINGS("せってい"),
    }

    var tab: Tab = Tab.SUMMARY

    /** データマップの選択。地図へ戻したときに使う。 */
    var overlay: CityRenderer.Overlay = CityRenderer.Overlay.NONE

    /** 推移グラフで見る項目。 */
    enum class Series(val label: String, val pick: (MonthlyStat) -> Int) {
        POPULATION("じんこう", { it.population }),
        FUNDS("しきん", { it.funds }),
        BALANCE("しゅうし", { it.balance }),
        POLLUTION("こうがい", { it.pollution }),
        CRIME("はんざい", { it.crime }),
        UNEMPLOYMENT("しつぎょう", { it.unemployment }),
        TRAFFIC("じゅうたい", { it.traffic }),
    }

    var series: Series = Series.POPULATION

    private companion object {
        const val MARGIN = 12
        const val TAB_H = 24
        const val ROW = 19
        /** 様式の1行の高さ。 */
        const val STYLE_ROW_H = 32
    }

    /** 見出しの帯の位置。判定と描画で共有する。 */
    fun tabX(index: Int): Int = MARGIN + index * ((GameView.LOGICAL_W - MARGIN * 2) / Tab.entries.size)
    fun tabW(): Int = (GameView.LOGICAL_W - MARGIN * 2) / Tab.entries.size
    fun tabY(): Int = 34

    /** その座標にある見出し。なければ null。 */
    fun tabAt(lx: Int, ly: Int): Tab? {
        if (ly < tabY() || ly > tabY() + TAB_H) return null
        for ((i, t) in Tab.entries.withIndex()) {
            if (lx >= tabX(i) && lx < tabX(i) + tabW()) return t
        }
        return null
    }

    /** 一覧の行。中身によって当たり判定に使う。 */
    fun rowY(index: Int): Int = tabY() + TAB_H + 12 + index * ROW

    fun draw(pixels: PixelCanvas, city: City, logicalH: Int) {
        pixels.clear(Palette.UI_BG)

        text.textSize = 20
        text.drawCentered(pixels, "じょうほう", GameView.LOGICAL_W / 2, 8, Palette.UI_ACCENT)

        // 見出し
        text.textSize = 12
        for ((i, t) in Tab.entries.withIndex()) {
            val x = tabX(i)
            val w = tabW()
            val on = t == tab
            pixels.fillRect(x, tabY(), w, TAB_H, if (on) Palette.UI_ACCENT else Palette.UI_BG_LIGHT)
            pixels.drawRect(x, tabY(), w, TAB_H, Palette.UI_LINE)
            text.drawCentered(
                pixels, t.label, x + w / 2, tabY() + 6,
                if (on) Palette.UI_BG else Palette.UI_TEXT,
            )
        }

        when (tab) {
            Tab.SUMMARY -> drawSummary(pixels, city, logicalH)
            Tab.GRAPH -> drawGraph(pixels, city, logicalH)
            Tab.BUDGET -> drawBudget(pixels, city, logicalH)
            Tab.MAP -> drawMapList(pixels, logicalH)
            Tab.ORDINANCE -> drawOrdinances(pixels, city, logicalH)
            Tab.SETTINGS -> drawSettings(pixels, city, logicalH)
        }

        drawCloseButton(pixels, logicalH)
    }

    // ------------------------------------------------------------------

    private fun drawSummary(pixels: PixelCanvas, city: City, logicalH: Int) {
        var y = rowY(0)
        text.textSize = 14

        fun row(label: String, value: String, colour: Int = Palette.UI_TEXT) {
            text.draw(pixels, label, MARGIN + 6, y, Palette.UI_DIM)
            text.draw(pixels, value, GameView.LOGICAL_W - MARGIN - 8 - text.measure(value), y, colour)
            y += ROW
        }

        row("じんこう", "${city.population}")
        row("しごと", "${city.jobs}")
        row("しつぎょうりつ", "${city.unemployment}%", warn(city.unemployment, 20, 35))
        row("へいきん ちか", "${city.averageLandValue}")
        row("へいきん こうがい", "${city.averagePollution}", warn(city.averagePollution, 30, 50))
        row("へいきん はんざい", "${city.averageCrime}", warn(city.averageCrime, 35, 55))
        row("かんせんど", "${city.infection}", warn(city.infection, 30, 50))
        row("じゅうたい", "${city.congestionRate}%", warn(city.congestionRate, 25, 45))
        y += 4
        row("でんりょく", "${city.powerSupply}/${city.powerDemand}",
            if (city.powerSupply < city.powerDemand) Palette.RED else Palette.UI_TEXT)
        row("すいどう", "${city.waterSupply}/${city.waterDemand}",
            if (city.waterSupply < city.waterDemand) Palette.RED else Palette.UI_TEXT)
        row("ゴミ", "${city.garbageProduced}/${city.garbageCapacity}",
            if (city.garbageProduced > city.garbageCapacity) Palette.RED else Palette.UI_TEXT)
        if (city.garbageBacklog > 0) {
            row("たまった ゴミ", "${city.garbageBacklog}", warn(city.garbageBacklog / 100, 10, 40))
        }
        y += 6

        // 満足度を棒で
        text.draw(pixels, "まんぞくど", MARGIN + 6, y, Palette.UI_DIM)
        val barX = MARGIN + 110
        val barW = GameView.LOGICAL_W - barX - MARGIN - 40
        pixels.drawRect(barX, y + 2, barW, 12, Palette.UI_LINE)
        val fill = barW * city.approval / 100
        val colour = when {
            city.approval >= 60 -> Palette.TREE
            city.approval >= 35 -> Palette.GOLD
            else -> Palette.RED
        }
        pixels.fillRect(barX + 1, y + 3, (fill - 2).coerceAtLeast(0), 10, colour)
        text.draw(pixels, "${city.approval}", GameView.LOGICAL_W - MARGIN - 30, y, Palette.UI_TEXT)
        y += ROW + 8

        // 助言
        text.textSize = 13
        text.draw(pixels, "しちょうへの ほうこく", MARGIN + 6, y, Palette.UI_ACCENT)
        y += ROW
        text.textSize = 12
        for (a in city.advice().take(4)) {
            val colour = when {
                a.severity >= 80 -> Palette.RED
                a.severity >= 50 -> Palette.GOLD
                else -> Palette.UI_TEXT
            }
            for (line in text.wrap(a.text, GameView.LOGICAL_W - MARGIN * 2 - 16)) {
                if (y > logicalH - 70) return
                text.draw(pixels, line, MARGIN + 10, y, colour)
                y += 15
            }
            y += 3
        }
    }

    private fun warn(value: Int, caution: Int, danger: Int): Int = when {
        value >= danger -> Palette.RED
        value >= caution -> Palette.GOLD
        else -> Palette.UI_TEXT
    }

    // ------------------------------------------------------------------

    private fun drawGraph(pixels: PixelCanvas, city: City, logicalH: Int) {
        // 見る項目を選ぶ
        text.textSize = 12
        var x = MARGIN
        var y = rowY(0) - 6
        for (s in Series.entries) {
            val w = text.measure(s.label) + 10
            if (x + w > GameView.LOGICAL_W - MARGIN) {
                x = MARGIN
                y += 18
            }
            val on = s == series
            pixels.fillRect(x, y, w, 16, if (on) Palette.UI_ACCENT else Palette.UI_BG_LIGHT)
            pixels.drawRect(x, y, w, 16, Palette.UI_LINE)
            text.draw(pixels, s.label, x + 5, y + 2, if (on) Palette.UI_BG else Palette.UI_TEXT)
            x += w + 4
        }
        val graphTop = y + 26

        val data = city.history
        val gx = MARGIN + 30
        val gy = graphTop
        val gw = GameView.LOGICAL_W - gx - MARGIN
        val gh = (logicalH - graphTop - 80).coerceAtLeast(60)

        // 枠
        pixels.drawRect(gx, gy, gw, gh, Palette.UI_LINE)

        if (data.size < 2) {
            text.textSize = 13
            text.drawCentered(
                pixels, "まだ きろくが ありません",
                GameView.LOGICAL_W / 2, gy + gh / 2, Palette.UI_DIM,
            )
            return
        }

        val values = data.map(series.pick)
        val lo = minOf(values.min(), 0)
        val hi = maxOf(values.max(), 1)
        val span = (hi - lo).coerceAtLeast(1)

        // 目盛り
        text.textSize = 11
        text.draw(pixels, "$hi", MARGIN - 4, gy - 4, Palette.UI_DIM)
        text.draw(pixels, "$lo", MARGIN - 4, gy + gh - 10, Palette.UI_DIM)
        // ゼロの線
        if (lo < 0) {
            val zy = gy + gh - (0 - lo) * gh / span
            for (px in gx until gx + gw step 3) pixels.set(px, zy, Palette.UI_DIM)
        }

        // 折れ線
        var prevX = -1
        var prevY = -1
        for ((i, v) in values.withIndex()) {
            val px = gx + i * gw / (values.size - 1).coerceAtLeast(1)
            val py = gy + gh - (v - lo) * gh / span
            if (prevX >= 0) line(pixels, prevX, prevY, px, py, Palette.UI_ACCENT)
            prevX = px
            prevY = py
        }

        text.textSize = 12
        text.draw(
            pixels, "${data.first().month / 12 + 1900}ねん 〜 ${data.last().month / 12 + 1900}ねん",
            gx, gy + gh + 6, Palette.UI_DIM,
        )
    }

    /** 2点を結ぶ線。 */
    private fun line(pixels: PixelCanvas, x0: Int, y0: Int, x1: Int, y1: Int, colour: Int) {
        val dx = Math.abs(x1 - x0)
        val dy = Math.abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy
        var x = x0
        var y = y0
        var guard = 0
        while (guard++ < 4_000) {
            pixels.set(x, y, colour)
            pixels.set(x, y + 1, colour)
            if (x == x1 && y == y1) break
            val e2 = err * 2
            if (e2 > -dy) { err -= dy; x += sx }
            if (e2 < dx) { err += dx; y += sy }
        }
    }

    // ------------------------------------------------------------------

    private fun drawBudget(pixels: PixelCanvas, city: City, logicalH: Int) {
        var y = rowY(0)
        text.textSize = 14

        fun row(label: String, value: Int, colour: Int = Palette.UI_TEXT, indent: Int = 0) {
            text.draw(pixels, label, MARGIN + 6 + indent, y, Palette.UI_DIM)
            val v = "$$value"
            text.draw(pixels, v, GameView.LOGICAL_W - MARGIN - 8 - text.measure(v), y, colour)
            y += ROW
        }

        val inc = city.lastIncomeBreakdown
        val sp = city.lastSpending

        text.draw(pixels, "しゅうにゅう", MARGIN + 6, y, Palette.UI_ACCENT); y += ROW
        row("じゅうたく", inc.residential, indent = 10)
        row("しょうぎょう", inc.commercial, indent = 10)
        row("こうぎょう", inc.industrial, indent = 10)
        if (inc.tourism > 0) row("かんこう", inc.tourism, indent = 10)
        row("ごうけい", inc.total, Palette.TREE_LIT)
        y += 6

        text.draw(pixels, "ししゅつ", MARGIN + 6, y, Palette.UI_ACCENT); y += ROW
        if (sp.transport > 0) row("こうつう", sp.transport, indent = 10)
        if (sp.power > 0) row("でんりょく", sp.power, indent = 10)
        if (sp.water > 0) row("すいどう", sp.water, indent = 10)
        if (sp.garbage > 0) row("ゴミしょり", sp.garbage, indent = 10)
        if (sp.safety > 0) row("けいさつ・しょうぼう", sp.safety, indent = 10)
        if (sp.health > 0) row("いりょう", sp.health, indent = 10)
        if (sp.education > 0) row("きょういく", sp.education, indent = 10)
        if (sp.parks > 0) row("こうえん・のうち", sp.parks, indent = 10)
        if (sp.ordinances > 0) row("じょうれい", sp.ordinances, indent = 10)
        row("ごうけい", sp.total, Palette.RED)
        y += 6

        val balance = inc.total - sp.total
        row("さしひき", balance, if (balance >= 0) Palette.TREE_LIT else Palette.RED)
    }

    // ------------------------------------------------------------------

    private fun drawMapList(pixels: PixelCanvas, logicalH: Int) {
        var y = rowY(0)
        text.textSize = 14
        text.draw(pixels, "ちずに かさねて みる", MARGIN + 6, y, Palette.UI_DIM)
        y += ROW + 4

        for (o in CityRenderer.Overlay.entries) {
            val on = o == overlay
            val h = 20
            pixels.fillRect(MARGIN, y, GameView.LOGICAL_W - MARGIN * 2, h,
                if (on) Palette.UI_ACCENT else Palette.UI_BG_LIGHT)
            pixels.drawRect(MARGIN, y, GameView.LOGICAL_W - MARGIN * 2, h, Palette.UI_LINE)
            text.draw(pixels, o.label, MARGIN + 10, y + 3, if (on) Palette.UI_BG else Palette.UI_TEXT)
            y += h + 3
            if (y > logicalH - 70) break
        }
    }

    /** データマップの一覧で、その座標にある項目。 */
    fun overlayAt(lx: Int, ly: Int): CityRenderer.Overlay? {
        if (tab != Tab.MAP) return null
        var y = rowY(0) + ROW + 4
        for (o in CityRenderer.Overlay.entries) {
            if (ly >= y && ly < y + 20) return o
            y += 23
        }
        return null
    }

    // ------------------------------------------------------------------

    private fun drawOrdinances(pixels: PixelCanvas, city: City, logicalH: Int) {
        var y = rowY(0)
        text.textSize = 12
        text.draw(pixels, "ひようは じんこうに おうじて まいつき かかります", MARGIN + 6, y, Palette.UI_DIM)
        y += ROW

        for (o in Ordinance.entries) {
            val on = o in city.ordinances
            val h = 46
            if (y + h > logicalH - 60) break
            pixels.fillRect(MARGIN, y, GameView.LOGICAL_W - MARGIN * 2, h,
                if (on) Palette.UI_BG_LIGHT else Palette.UI_BG)
            pixels.drawRect(MARGIN, y, GameView.LOGICAL_W - MARGIN * 2, h,
                if (on) Palette.UI_ACCENT else Palette.UI_LINE)

            // 入／切の印
            val markX = GameView.LOGICAL_W - MARGIN - 34
            pixels.fillRect(markX, y + 13, 26, 18, if (on) Palette.TREE else Palette.UI_BG)
            pixels.drawRect(markX, y + 13, 26, 18, Palette.UI_LINE)
            text.textSize = 12
            text.drawCentered(pixels, if (on) "オン" else "オフ", markX + 13, y + 16,
                if (on) Palette.WHITE else Palette.UI_DIM)

            text.textSize = 13
            text.draw(pixels, o.label, MARGIN + 8, y + 4, Palette.UI_TEXT)
            text.textSize = 11
            text.draw(pixels, o.detail, MARGIN + 8, y + 21, Palette.UI_DIM)
            val cost = (city.population * o.costPerCitizen).toInt()
            text.draw(pixels, "$$cost/つき", MARGIN + 8, y + 33, Palette.GOLD)
            y += h + 4
        }
    }

    /** 条例の一覧で、その座標にある条例。 */
    fun ordinanceAt(lx: Int, ly: Int, logicalH: Int): Ordinance? {
        if (tab != Tab.ORDINANCE) return null
        var y = rowY(0) + ROW
        for (o in Ordinance.entries) {
            val h = 46
            if (y + h > logicalH - 60) break
            if (ly >= y && ly < y + h) return o
            y += h + 4
        }
        return null
    }

    // ------------------------------------------------------------------

    /**
     * せってい。街並みの様式と、災害の多さを選ぶ。
     *
     * 様式は見た目だけを変えるもので、シミュレーションには関わらない。
     * 同じ街でも、古代風にすれば砂漠の都に、未来風にすれば発光する都市に見える。
     */
    private fun drawSettings(pixels: PixelCanvas, city: City, logicalH: Int) {
        var y = rowY(0)

        // --- 音 ---
        text.textSize = 14
        text.draw(pixels, "おと", MARGIN + 6, y, Palette.UI_ACCENT)
        y += ROW
        soundRowY = y
        val half = (GameView.LOGICAL_W - MARGIN * 2) / 2
        for ((i, on) in booleanArrayOf(sfxOn, bgmOn).withIndex()) {
            val x = MARGIN + i * half
            pixels.fillRect(x, y, half - 3, 24, if (on) Palette.UI_ACCENT else Palette.UI_BG_LIGHT)
            pixels.drawRect(x, y, half - 3, 24, Palette.UI_LINE)
            text.textSize = 12
            val label = if (i == 0) "こうかおん" else "おんがく"
            text.drawCentered(
                pixels, "$label ${if (on) "オン" else "オフ"}",
                x + (half - 3) / 2, y + 6,
                if (on) Palette.UI_BG else Palette.UI_DIM,
            )
        }
        y += 32

        text.textSize = 14
        text.draw(pixels, "まちなみの ようしき", MARGIN + 6, y, Palette.UI_ACCENT)
        y += ROW

        text.textSize = 11
        text.draw(pixels, "みためだけが かわります。まちの なかみは そのままです。", MARGIN + 6, y, Palette.UI_DIM)
        y += 16

        for (st in City.Style.entries) {
            val on = st == city.style
            val h = STYLE_ROW_H
            pixels.fillRect(MARGIN, y, GameView.LOGICAL_W - MARGIN * 2, h,
                if (on) Palette.UI_ACCENT else Palette.UI_BG_LIGHT)
            pixels.drawRect(MARGIN, y, GameView.LOGICAL_W - MARGIN * 2, h, Palette.UI_LINE)
            text.textSize = 13
            text.draw(pixels, st.label, MARGIN + 8, y + 3,
                if (on) Palette.UI_BG else Palette.UI_TEXT)
            text.textSize = 10
            text.draw(pixels, st.detail, MARGIN + 8, y + 18,
                if (on) Palette.UI_BG else Palette.UI_DIM)
            // 色の見本を右に並べる
            val swatchX = GameView.LOGICAL_W - MARGIN - 52
            for ((i, c) in swatchesFor(st).withIndex()) {
                pixels.fillRect(swatchX + i * 12, y + 8, 10, 14, c)
                pixels.drawRect(swatchX + i * 12, y + 8, 10, 14, Palette.BLACK)
            }
            y += h + 3
            if (y > logicalH - 130) break
        }

        // 「じぶんで きめる」なら、色を選ぶ入口を出す
        if (city.style == City.Style.CUSTOM) {
            y += 4
            customEditY = y
            pixels.fillRect(MARGIN, y, GameView.LOGICAL_W - MARGIN * 2, 24, Palette.UI_BG_LIGHT)
            pixels.drawRect(MARGIN, y, GameView.LOGICAL_W - MARGIN * 2, 24, Palette.UI_ACCENT)
            text.textSize = 13
            text.draw(pixels, "いろを えらぶ ▶", MARGIN + 8, y + 5, Palette.UI_ACCENT)
            y += 28
        } else {
            customEditY = -1
        }

        y += 6
        text.textSize = 14
        text.draw(pixels, "さいがいの おおさ", MARGIN + 6, y, Palette.UI_ACCENT)
        y += ROW
        settingsDisasterY = y
        for (lv in City.DisasterLevel.entries) {
            val on = lv == city.disasterLevel
            val w = (GameView.LOGICAL_W - MARGIN * 2) / City.DisasterLevel.entries.size
            val x = MARGIN + lv.ordinal * w
            pixels.fillRect(x, y, w - 2, 22, if (on) Palette.UI_ACCENT else Palette.UI_BG_LIGHT)
            pixels.drawRect(x, y, w - 2, 22, Palette.UI_LINE)
            text.textSize = 12
            text.drawCentered(pixels, lv.label, x + w / 2, y + 5,
                if (on) Palette.UI_BG else Palette.UI_TEXT)
        }
    }

    /** その様式の代表的な3色。一覧で見分けるために出す。 */
    private fun swatchesFor(style: City.Style): IntArray = when (style) {
        City.Style.STANDARD -> intArrayOf(Palette.HOUSE_ROOF, Palette.HOUSE_LEFT, Palette.GLASS_LIT)
        City.Style.PRIME -> intArrayOf(Palette.PRIME_ROOF, Palette.PRIME_LEFT, Palette.PRIME_GLASS)
        City.Style.RURAL -> intArrayOf(Palette.RURAL_ROOF, Palette.RURAL_WALL, Palette.TREE)
        City.Style.GRITTY -> intArrayOf(Palette.GRIT_ROOF, Palette.GRIT_WALL, Palette.METAL_DARK)
        City.Style.ANCIENT -> intArrayOf(Palette.ANCIENT_ROOF, Palette.ANCIENT_WALL, Palette.SAND_LIT)
        City.Style.FUTURE -> intArrayOf(Palette.FUTURE_ROOF, Palette.FUTURE_WALL, Palette.FUTURE_GLOW)
        City.Style.EUROPE -> intArrayOf(Palette.EURO_ROOF, Palette.EURO_WALL, Palette.SAND_LIT)
        City.Style.JAPAN -> intArrayOf(Palette.JP_ROOF, Palette.JP_WALL, Palette.TREE_DARK)
        City.Style.CUSTOM -> intArrayOf(
            customSwatch(CustomStyle.Slot.HOUSE_ROOF),
            customSwatch(CustomStyle.Slot.HOUSE_WALL),
            customSwatch(CustomStyle.Slot.GLASS_LIT),
        )
    }

    /** いま編集中のカスタム様式。見本を出すために持つ。 */
    var custom: CustomStyle? = null

    /** 音の設定。表示のために持つだけで、実際に鳴らすのは GameView。 */
    var sfxOn: Boolean = true
    var bgmOn: Boolean = true
    private var soundRowY = -1

    /**
     * 音の釦のどちらを押したか。
     * 0 = 効果音、1 = 音楽、押していなければ null。
     */
    fun soundToggleAt(lx: Int, ly: Int): Int? {
        if (tab != Tab.SETTINGS || soundRowY < 0) return null
        if (ly < soundRowY || ly >= soundRowY + 24) return null
        val half = (GameView.LOGICAL_W - MARGIN * 2) / 2
        return if (lx < MARGIN + half) 0 else 1
    }

    private fun customSwatch(slot: CustomStyle.Slot): Int =
        custom?.get(slot) ?: slot.default

    /** 災害の段の y。描いたときに覚えて、判定で使う。 */
    private var settingsDisasterY = 0

    /** 「いろを えらぶ」の y。出していないときは -1。 */
    private var customEditY = -1

    /** 「いろを えらぶ」が押されたか。 */
    fun customEditTapped(lx: Int, ly: Int): Boolean =
        tab == Tab.SETTINGS && customEditY >= 0 && ly >= customEditY && ly < customEditY + 24

    /** せっていの一覧で、その座標にある様式。 */
    fun styleAt(lx: Int, ly: Int, logicalH: Int): City.Style? {
        if (tab != Tab.SETTINGS) return null
        var y = rowY(0) + ROW + 16
        for (st in City.Style.entries) {
            if (ly >= y && ly < y + STYLE_ROW_H) return st
            y += STYLE_ROW_H + 3
            if (y > logicalH - 130) break
        }
        return null
    }

    /** せっていの一覧で、その座標にある災害の段。 */
    fun disasterAt(lx: Int, ly: Int): City.DisasterLevel? {
        if (tab != Tab.SETTINGS) return null
        if (ly < settingsDisasterY || ly > settingsDisasterY + 22) return null
        val w = (GameView.LOGICAL_W - MARGIN * 2) / City.DisasterLevel.entries.size
        val i = (lx - MARGIN) / w
        return City.DisasterLevel.entries.getOrNull(i)
    }

    fun closeButtonY(logicalH: Int): Int = logicalH - 46

    private fun drawCloseButton(pixels: PixelCanvas, logicalH: Int) {
        val y = closeButtonY(logicalH)
        val w = 90
        val x = (GameView.LOGICAL_W - w) / 2
        pixels.fillRect(x, y, w, 28, Palette.UI_BG_LIGHT)
        pixels.drawRect(x, y, w, 28, Palette.UI_LINE)
        text.textSize = 15
        text.drawCentered(pixels, "とじる", GameView.LOGICAL_W / 2, y + 5, Palette.UI_TEXT)
    }

    /** 推移グラフで、その座標にある項目。 */
    fun seriesAt(lx: Int, ly: Int): Series? {
        if (tab != Tab.GRAPH) return null
        text.textSize = 12
        var x = MARGIN
        var y = rowY(0) - 6
        for (s in Series.entries) {
            val w = text.measure(s.label) + 10
            if (x + w > GameView.LOGICAL_W - MARGIN) {
                x = MARGIN
                y += 18
            }
            if (lx >= x && lx < x + w && ly >= y && ly < y + 16) return s
            x += w + 4
        }
        return null
    }
}
