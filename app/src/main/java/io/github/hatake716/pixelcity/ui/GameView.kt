package io.github.hatake716.pixelcity.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import io.github.hatake716.pixelcity.game.BuildCost
import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Monument
import io.github.hatake716.pixelcity.game.TileKind
import io.github.hatake716.pixelcity.game.Tutorial
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * ゲーム画面。論理解像度 160×144 へ描いてから整数倍で引き伸ばす。
 *
 * 入力・描画・毎月の進行をここでまとめて扱う。
 */
@SuppressLint("ViewConstructor")
class GameView(
    context: Context,
    var city: City,
    var tutorial: Tutorial,
) : View(context) {

    companion object {
        /**
         * 論理解像度。DMG の 160×144 の2倍。
         *
         * 日本語のドットフォント DotGothic16 は 16px 四方の格子で設計されていて、
         * 8px で描くと1ドットが半ピクセルに落ちて字が崩れる。実解像度にこだわるより、
         * 2倍にして文字を 16px で描くほうが読みやすく、情報量も足りる。
         * タイルは 8×8 のドット絵を2倍に拡大して使うので、見た目の粒は変わらない。
         */
        /**
         * 論理解像度の横幅。
         *
         * ドット絵を細かくしたぶん、1画面に入る情報量を確保するために広くとる。
         * 1080px の端末なら 2倍、1440px なら 2〜3倍で表示される。
         */
        const val LOGICAL_W = 480
        /** 縦の既定値。実際の高さは端末の縦横比にあわせて決まる。 */
        const val LOGICAL_H = 420
        /** 縦に取りうる範囲。 */
        const val LOGICAL_H_MAX = 1000
        /** 1か月の実時間（ミリ秒）。速度倍率で割る。 */
        const val MONTH_MILLIS = 8_000L
        private val SPEEDS = intArrayOf(0, 1, 2, 4)

        /**
         * 拡大率の段（分子, 分母）。広いマップを見渡せるよう、引いた画を厚くしてある。
         * 1/4 = 街の全体像、1/2 = 区画の配置、1/1 = 標準、2/1 = 建物の細部。
         */
        private val ZOOM_STEPS = arrayOf(1 to 8, 1 to 4, 1 to 2, 1 to 1)

        // 配置。描画と当たり判定で同じ値を使うため、ここに集める。
        private const val SPEED_X = 150
        private const val ZOOM_X = 196
        private const val BUDGET_X = 168
        private const val MONUMENT_X = 236
        private const val BANNER_H = 104
        private const val PANEL_MARGIN = 16
        private const val TAX_MINUS_X = 200
        private const val TAX_PLUS_X = 240
        /** 本文の文字の大きさ。折り返しの計算と描画で必ず同じ値を使う。 */
        private const val BODY_SIZE = 15

        // 画面まわりの色。どのパレット索引を使うかをここにまとめる。
        /** パネルや帯の下地。 */
        private const val C_BG = Palette.UI_BG
        /** 罫線・枠。 */
        private const val C_LINE = Palette.UI_LINE
        /** 本文の文字。 */
        private const val C_TEXT = Palette.UI_TEXT
        /** 補助的な文字。 */
        private const val C_DIM = Palette.UI_DIM
        /** モニュメント一覧の行の高さ。 */
        private const val MONUMENT_ROW_H = 20
        private const val CLOSE_X = LOGICAL_W - 84
    }

    /** 画面の状態。 */
    enum class Screen { PLAYING, BUDGET, MONUMENTS, MESSAGE, GAME_OVER }

    var screen: Screen = Screen.PLAYING
        private set

    /** 端末の縦横比にあわせた論理の高さ。onSizeChanged で決まる。 */
    private var logicalH = LOGICAL_H
    private var pixels = PixelCanvas(LOGICAL_W, LOGICAL_H)
    private val renderer = CityRenderer()
    private val text = GbText(context)

    private var frame = Bitmap.createBitmap(LOGICAL_W, LOGICAL_H, Bitmap.Config.ARGB_8888)
    private var frameRow = IntArray(LOGICAL_W * LOGICAL_H)
    private val blitPaint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false  // ドットをドットのまま拡大する
        isDither = false
    }
    private val dst = Rect()

    /** 論理ピクセル1つが画面上で何ピクセルになるか。 */
    private var scale = 1
    private var offsetX = 0
    private var offsetY = 0

    // --- カメラ ---
    // カメラは「画面の中央に来るタイル座標」。斜め見下ろしなので、
    // 縦横のタイル数ではなく中心を持つほうが素直になる。
    private var camX = 16f
    private var camY = 16f
    private var cameraInitialised = false
    /**
     * 地図の拡大率の段。[ZOOM_STEPS] の索引。
     * 引いた画（街全体）から、寄った画（建物の細部）まで選べる。
     */
    private var zoomStep = 2

    /** いまの拡大率。分数を使わずに済むよう、分子と分母で持つ。 */
    private val zoomNum: Int get() = ZOOM_STEPS[zoomStep].first
    private val zoomDen: Int get() = ZOOM_STEPS[zoomStep].second

    /** 拡大率を切り替える。 */
    fun cycleZoom() {
        zoomStep = (zoomStep + 1) % ZOOM_STEPS.size
        clampCamera()
        invalidate()
    }

    // --- 入力 ---
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var dragged = false
    private var pointerDown = false
    /** 2本指で地図を動かしている最中か。 */
    private var panning = false
    /** 道具が切り替わったので、指を離すまで置くのをやめる。 */
    private var strokeCancelled = false
    private var toolScroll = 0
    private var draggingToolbar = false

    // --- 進行 ---
    var speedIndex: Int = 1
        private set
    private var monthAccumulator = 0L
    private var lastFrameTime = 0L

    var selectedTool: TileKind = TileKind.ROAD
        private set
    private var pendingMonument: Monument? = null

    private var overlay: CityRenderer.Overlay = CityRenderer.Overlay.NONE

    /** 画面に出す一時的な知らせ。 */
    private var message: String? = null
    private var messageTitle: String = ""
    private var toast: String? = null
    private var toastUntil = 0L

    /** 保存を促すための通知。Activity が受け取る。 */
    var onStateChanged: (() -> Unit)? = null

    init {
        isFocusable = true
        keepScreenOn = true
        showTutorialMessageIfNeeded()
    }

    // ------------------------------------------------------------------
    // 進行
    // ------------------------------------------------------------------

    private fun tick() {
        val now = System.currentTimeMillis()
        if (lastFrameTime == 0L) lastFrameTime = now
        val delta = now - lastFrameTime
        lastFrameTime = now

        if (toast != null && now > toastUntil) toast = null

        val speed = SPEEDS[speedIndex]
        if (speed > 0 && screen == Screen.PLAYING && !city.gameOver) {
            monthAccumulator += delta * speed
            while (monthAccumulator >= MONTH_MILLIS) {
                monthAccumulator -= MONTH_MILLIS
                advanceMonth()
            }
        }
        postInvalidateOnAnimation()
    }

    private fun advanceMonth() {
        city.step()
        tutorial.onMonthPassed()
        showTutorialMessageIfNeeded()
        if (city.gameOver) {
            screen = Screen.GAME_OVER
            speedIndex = 0
        }
        onStateChanged?.invoke()
    }

    fun setSpeed(index: Int) {
        if (!tutorial.allowsSpeedChange()) {
            showToast("いまは ステップの とおりに")
            return
        }
        speedIndex = index.coerceIn(0, SPEEDS.size - 1)
    }

    fun cycleSpeed() = setSpeed((speedIndex + 1) % SPEEDS.size)

    // ------------------------------------------------------------------
    // 描画
    // ------------------------------------------------------------------

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 横幅にあわせた整数倍で拡大する。ただし、その倍率で縦に最低限の
        // 高さ（LOGICAL_H）が入らない画面（横向きなど）では、縦に合わせて縮める。
        // 横だけで決めると、横向きで文字が巨大になり画面からあふれる。
        scale = max(1, w / LOGICAL_W)
        while (scale > 1 && h / scale < LOGICAL_H) scale--
        // 縦は端末にあわせて論理解像度そのものを伸ばす。
        // こうしないと、細長い端末で上下に大きな余白ができ、マップが潰れる。
        logicalH = (h / scale).coerceIn(LOGICAL_H, LOGICAL_H_MAX)
        if (pixels.height != logicalH) {
            pixels = PixelCanvas(LOGICAL_W, logicalH)
            frame = Bitmap.createBitmap(LOGICAL_W, logicalH, Bitmap.Config.ARGB_8888)
            frameRow = IntArray(LOGICAL_W * logicalH)
        }
        val drawW = LOGICAL_W * scale
        val drawH = logicalH * scale
        offsetX = (w - drawW) / 2
        offsetY = (h - drawH) / 2
        dst.set(offsetX, offsetY, offsetX + drawW, offsetY + drawH)
        // 画面の大きさが決まってから、見える範囲にあわせて収め直す。
        if (!cameraInitialised) {
            centerCamera()
            cameraInitialised = true
        } else {
            clampCamera()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderFrame()

        // パレット索引を色へ変換して転送する
        for (i in pixels.pixels.indices) {
            frameRow[i] = Palette.of(pixels.pixels[i].toInt())
        }
        frame.setPixels(frameRow, 0, LOGICAL_W, 0, 0, LOGICAL_W, logicalH)

        canvas.drawColor(Palette.BEZEL)
        canvas.drawBitmap(frame, null, dst, blitPaint)
        tick()
    }

    private fun renderFrame() {
        val mapTop = Hud.STATUS_HEIGHT
        val mapHeight = logicalH - Hud.STATUS_HEIGHT - Hud.TOOLBAR_HEIGHT

        renderer.draw(
            pixels, city, camX, camY, zoomNum, zoomDen, mapTop, mapHeight, overlay,
            highlightForSelection(),
            suggest = placementHint(),
            suggestOn = blinkOn(),
        )

        drawStatusBar()
        drawToolbar(mapTop + mapHeight)

        when (screen) {
            Screen.BUDGET -> drawBudget()
            Screen.MONUMENTS -> drawMonuments()
            Screen.MESSAGE -> drawMessage()
            Screen.GAME_OVER -> drawGameOver()
            Screen.PLAYING -> {
                if (tutorial.active) drawTutorialBanner()
                toast?.let { drawToast(it) }
            }
        }
    }

    /**
     * チュートリアル中、いま置ける場所を返す。
     * 「道路のとなり」と言われても、どこが該当するのかは初心者には分かりにくい。
     */
    private fun placementHint(): ((Int, Int) -> Boolean)? {
        if (!tutorial.active) return null
        val kind = tutorial.step?.highlightTool ?: return null
        if (!kind.isZone && !kind.isPowerPlant) return null
        return { x, y ->
            city.tileAt(x, y).kind == TileKind.EMPTY &&
                city.canBuildOn(x, y) &&
                city.touchesRoad(x, y)
        }
    }

    private fun highlightForSelection(): IntArray? {
        val m = pendingMonument ?: return null
        return intArrayOf(camX.toInt() + 8, camY.toInt() + 6, 2)
    }

    private fun drawStatusBar() {
        pixels.fillRect(0, 0, LOGICAL_W, Hud.STATUS_HEIGHT, C_BG)
        pixels.fillRect(0, Hud.STATUS_HEIGHT - 2, LOGICAL_W, 2, C_LINE)

        text.textSize = 16
        text.draw(pixels, "$" + city.funds.toString(), 4, 1, C_TEXT)

        val year = 1900 + city.month / 12
        val mon = city.month % 12 + 1
        text.draw(pixels, "${year}ねん${mon}がつ", LOGICAL_W - 150, 1, C_TEXT)

        text.draw(pixels, "じんこう " + city.population, 4, 20, C_TEXT)

        // 需要バー R/C/I
        Hud.drawDemandBars(pixels, city, 258, 19, 28)

        // 速度
        val speedLabel = when (speedIndex) { 0 -> "‖"; 1 -> "▶"; 2 -> "▶▶"; else -> "▶▶▶" }
        text.draw(pixels, speedLabel, SPEED_X, 20, C_TEXT)

        // 拡大率の切り替え
        val zoomLabel = when (zoomStep) {
            0 -> "ぜんたい"
            1 -> "ひろい"
            2 -> "ふつう"
            else -> "よせる"
        }
        pixels.drawRect(ZOOM_X, 18, 64, 24, C_LINE)
        text.textSize = 14
        text.draw(pixels, zoomLabel, ZOOM_X + 5, 21, C_TEXT)
        text.textSize = 16

        // 警告は1行にまとめる
        val warning = when {
            city.powerSupply < city.powerDemand -> {
                // どれだけ足りないのかを出す。「たりません」だけでは
                // 発電所を何基 建てればよいのか分からない。
                val short = city.powerDemand - city.powerSupply
                val plants = (short + 1_999) / 2_000
                "はつでんしょ あと${plants}"
            }
            else -> city.bankruptcyWarning()?.let { "はさんまで ${it}かげつ" }
        }
        if (warning != null) {
            // 需要バーに かぶらない幅で切る。
            text.textSize = 14
            var w: String = warning
            while (w.isNotEmpty() && text.measure(w) > 248) w = w.dropLast(1)
            text.draw(pixels, w, 4, 37, C_TEXT)
        }
    }

    private fun drawToolbar(top: Int) {
        pixels.fillRect(0, top, LOGICAL_W, Hud.TOOLBAR_HEIGHT, C_BG)
        pixels.fillRect(0, top, LOGICAL_W, 2, C_LINE)

        text.textSize = 14
        for ((i, tool) in Hud.TOOLS.withIndex()) {
            val x = Hud.toolX(i) - toolScroll
            if (x + Hud.TOOL_SIZE < 0 || x > LOGICAL_W) continue
            val y = top + 4
            val selected = tool.kind == selectedTool
            // 枠を二重にして選択中を示す。塗りつぶすと、同じ濃さのアイコンが消えてしまう。
            pixels.drawRect(x, y, Hud.TOOL_SIZE, Hud.TOOL_SIZE, C_LINE)
            if (selected) {
                pixels.drawRect(x + 1, y + 1, Hud.TOOL_SIZE - 2, Hud.TOOL_SIZE - 2, C_TEXT)
                pixels.drawRect(x + 2, y + 2, Hud.TOOL_SIZE - 4, Hud.TOOL_SIZE - 4, C_TEXT)
            }

            // アイコン。地図と同じドット絵を縮めて見せる。
            val sprite = when (tool.kind) {
                TileKind.ROAD -> IsoTiles.ROAD_CROSS
                TileKind.ZONE_R -> IsoBuildings.HOUSE_1
                TileKind.ZONE_C -> IsoBuildings.SHOP_1
                TileKind.ZONE_I -> IsoBuildings.FACTORY_1
                TileKind.POWER_COAL -> IsoBuildings.POWER_COAL
                TileKind.POWER_SOLAR -> IsoBuildings.POWER_SOLAR
                TileKind.PARK -> IsoBuildings.PARK
                TileKind.POLICE -> IsoBuildings.POLICE
                TileKind.FIRE -> IsoBuildings.FIRE
                TileKind.SCHOOL -> IsoBuildings.SCHOOL
                TileKind.HOSPITAL -> IsoBuildings.HOSPITAL
                else -> null
            }
            if (sprite != null) {
                drawIcon(sprite, x + 1, y + 1, Hud.TOOL_SIZE - 2)
            } else {
                // 取り壊しは×印
                for (k in 0 until 16) {
                    pixels.set(x + 6 + k, y + 6 + k, 3)
                    pixels.set(x + 21 - k, y + 6 + k, 3)
                }
            }

            // チュートリアルで指す先を点滅させる
            if (tutorial.active && tutorial.step?.highlightTool == tool.kind && blinkOn()) {
                pixels.drawRect(x - 3, y - 3, Hud.TOOL_SIZE + 6, Hud.TOOL_SIZE + 6, C_TEXT)
                pixels.drawRect(x - 4, y - 4, Hud.TOOL_SIZE + 8, Hud.TOOL_SIZE + 8, C_TEXT)
            }
        }

        // 選んでいるものの名前と値段
        val tool = Hud.TOOLS.firstOrNull { it.kind == selectedTool }
        if (tool != null) {
            val price = if (tool.kind == TileKind.EMPTY) BuildCost.BULLDOZE else BuildCost.cost(tool.kind)
            text.textSize = 15
            text.draw(pixels, "${tool.label} $${price}", 4, top + 38, C_TEXT)
        }

        // 右下に予算・けんちくの入口
        text.textSize = 15
        pixels.drawRect(BUDGET_X, top + 36, 62, 20, C_LINE)
        text.draw(pixels, "よさん", BUDGET_X + 5, top + 38, C_TEXT)
        pixels.drawRect(MONUMENT_X, top + 36, 78, 20, C_LINE)
        text.draw(pixels, "けんちく", MONUMENT_X + 5, top + 38, C_TEXT)
        if (tutorial.active && tutorial.step?.highlightBudget == true && blinkOn()) {
            pixels.drawRect(BUDGET_X - 2, top + 34, 66, 24, C_TEXT)
        }
        if (tutorial.active && tutorial.step?.highlightSpeed == true && blinkOn()) {
            pixels.drawRect(SPEED_X - 4, 17, 40, 24, C_TEXT)
        }
    }

    /**
     * ツールバーのアイコン。[size] の枠に収まるよう縮めて描く。
     * 縦長の建物も、全体が入るように縦横で同じ率を使う。
     */
    private fun drawIcon(sprite: Sprite, x: Int, y: Int, size: Int) {
        val scaleNum = size
        val scaleDen = maxOf(sprite.width, sprite.height)
        // 中央寄せ
        val ox = x + (size - sprite.width * scaleNum / scaleDen) / 2
        val oy = y + (size - sprite.height * scaleNum / scaleDen) / 2
        for (dy in 0 until sprite.height * scaleNum / scaleDen) {
            val sy = dy * scaleDen / scaleNum
            for (dx in 0 until sprite.width * scaleNum / scaleDen) {
                val v = sprite.at(dx * scaleDen / scaleNum, sy)
                if (v == Pix.TRANSPARENT) continue
                pixels.set(ox + dx, oy + dy, v.toInt())
            }
        }
    }

    /** チュートリアルの吹き出しの高さ。描画と判定で同じ値を使う。 */
    private fun bannerHeight(): Int {
        val step = tutorial.step ?: return 0
        text.textSize = BODY_SIZE
        val lines = text.wrap(step.body, LOGICAL_W - 20).size
        return 36 + lines * 18 + if (tutorial.awaitingContinue()) 30 else 6
    }

    /** 点滅の位相。0.5秒ごとに切り替える。 */
    private fun blinkOn(): Boolean = (System.currentTimeMillis() / 500L) % 2 == 0L

    private fun drawTutorialBanner() {
        val step = tutorial.step ?: return
        text.textSize = BODY_SIZE
        val lines = text.wrap(step.body, LOGICAL_W - 20)
        // 本文の行数と、進むボタンの有無で高さを決める。文字が欠けないようにする。
        val h = bannerHeight()
        val y = logicalH - Hud.TOOLBAR_HEIGHT - h
        pixels.fillRect(0, y, LOGICAL_W, h, C_BG)
        pixels.drawRect(0, y, LOGICAL_W, h, C_LINE)
        pixels.drawRect(1, y + 1, LOGICAL_W - 2, h - 2, C_LINE)

        // 見出しは「あと N」の手前で切る。重ねると両方読めなくなる。
        val remain = tutorial.remaining()
        val counter = if (remain > 0) "あと $remain" else ""
        text.textSize = 16
        val counterW = if (counter.isEmpty()) 0 else text.measure(counter) + 12
        var title = step.title
        while (title.isNotEmpty() && text.measure(title) > LOGICAL_W - 16 - counterW) {
            title = title.dropLast(1)
        }
        text.draw(pixels, title, 8, y + 5, C_TEXT)
        if (counter.isNotEmpty()) {
            text.draw(pixels, counter, LOGICAL_W - text.measure(counter) - 8, y + 5, C_TEXT)
        }
        pixels.fillRect(8, y + 26, LOGICAL_W - 16, 2, C_LINE)

        var ly = y + 32
        for (line in lines) {
            text.draw(pixels, line, 8, ly, C_TEXT)
            ly += 18
        }

        if (tutorial.awaitingContinue()) {
            text.textSize = 16
            val label = if (tutorial.stepIndex == Tutorial.STEPS.size - 1) "はじめる" else "つぎへ ▶"
            val w = text.measure(label) + 16
            val bx = LOGICAL_W - w - 8
            val by = ly + 2
            pixels.fillRect(bx, by, w, 24, C_BG)
            pixels.drawRect(bx, by, w, 24, C_LINE)
            if (blinkOn()) pixels.drawRect(bx - 2, by - 2, w + 4, 28, C_TEXT)
            text.draw(pixels, label, bx + 8, by + 3, C_TEXT)
        }
    }

    private fun drawToast(msg: String) {
        text.textSize = 16
        val w = text.measure(msg) + 20
        val x = (LOGICAL_W - w) / 2
        val y = logicalH - Hud.TOOLBAR_HEIGHT - 36
        pixels.fillRect(x, y, w, 26, C_BG)
        pixels.drawRect(x, y, w, 26, C_LINE)
        text.draw(pixels, msg, x + 10, y + 4, C_TEXT)
    }

    /** いま開いているパネルの高さ。中身に合わせて決まる。 */
    private var panelHeight = 0

    /** パネルの上端。画面の中ほどに置く。 */
    private fun panelTop(): Int = ((logicalH - panelHeight) / 2).coerceAtLeast(24)

    /**
     * パネルの枠を描き、本文を書き始める y を返す。
     * [contentHeight] は見出しと閉じるボタンを除いた中身の高さ。
     */
    private fun drawPanel(title: String, contentHeight: Int): Int {
        val m = PANEL_MARGIN
        panelHeight = 44 + contentHeight + 40
        val top = panelTop()
        pixels.fillRect(m, top, LOGICAL_W - m * 2, panelHeight, C_BG)
        pixels.drawRect(m, top, LOGICAL_W - m * 2, panelHeight, C_LINE)
        pixels.drawRect(m + 1, top + 1, LOGICAL_W - m * 2 - 2, panelHeight - 2, C_LINE)
        text.textSize = 16
        text.drawCentered(pixels, title, LOGICAL_W / 2, top + 6, C_TEXT)
        pixels.fillRect(m + 8, top + 28, LOGICAL_W - m * 2 - 16, 2, C_LINE)
        return top + 36
    }

    private fun drawBudget() {
        val rows = 6 + if (city.tourismIncome > 0) 1 else 0
        var y = drawPanel("よさん", 28 + rows * 20 + 40)
        text.textSize = 16
        text.draw(pixels, "ぜいりつ ${city.taxRate}%", 30, y, C_TEXT)
        // 税率の増減ボタン
        pixels.drawRect(TAX_MINUS_X, y - 2, 26, 22, C_LINE)
        text.drawCentered(pixels, "-", TAX_MINUS_X + 13, y - 1, C_TEXT)
        pixels.drawRect(TAX_PLUS_X, y - 2, 26, 22, C_LINE)
        text.drawCentered(pixels, "+", TAX_PLUS_X + 13, y - 1, C_TEXT)
        y += 28

        text.textSize = 15
        text.draw(pixels, "しゅうにゅう  $${city.lastIncome}", 30, y, C_TEXT); y += 20
        text.draw(pixels, "ししゅつ    $${city.lastUpkeep}", 30, y, C_TEXT); y += 20
        val balance = city.lastIncome - city.lastUpkeep
        text.draw(pixels, "さしひき    $${balance}", 30, y, C_TEXT); y += 24
        val powerLabel = if (city.powerSupply < city.powerDemand) {
            "でんりょく  ${city.powerSupply}/${city.powerDemand} ふそく"
        } else {
            "でんりょく  ${city.powerSupply}/${city.powerDemand}"
        }
        text.draw(pixels, powerLabel, 30, y, C_TEXT); y += 20
        text.draw(pixels, "しごと     ${city.jobs}", 30, y, C_TEXT); y += 20
        if (city.tourismIncome > 0) {
            text.draw(pixels, "かんこう    $${city.tourismIncome}", 30, y, C_TEXT); y += 20
        }

        text.textSize = 14
        text.draw(pixels, "ぜいりつが たかいと", 30, y + 4, C_DIM)
        text.draw(pixels, "ひとが でていきます", 30, y + 22, C_DIM)
        drawCloseButton()
    }

    /** モニュメント一覧の1行目の y。描画と判定で共有する。 */
    private fun monumentListTop(): Int {
        // drawPanel と同じ高さの計算をして、本文の開始位置を求める。
        val content = Monument.entries.size * MONUMENT_ROW_H + 28
        val height = 44 + content + 40
        val top = ((logicalH - height) / 2).coerceAtLeast(24)
        return top + 36
    }

    private fun drawMonuments() {
        var y = drawPanel("せかいの けんちく", Monument.entries.size * MONUMENT_ROW_H + 28)
        text.textSize = 14
        val all = Monument.entries
        for (m in all) {
            val built = m in city.builtMonuments
            val unlocked = city.population >= m.unlockPopulation
            val label = when {
                built -> "✓ ${m.label}"
                unlocked -> "${m.label} $${m.cost}"
                else -> "${m.label}（じんこう${m.unlockPopulation}）"
            }
            val shade = if (built || unlocked) C_TEXT else C_DIM
            text.draw(pixels, label, 30, y, shade)
            y += MONUMENT_ROW_H
        }
        text.draw(pixels, "えらんで マップを タップ", 30, y + 4, C_TEXT)
        drawCloseButton()
    }

    private fun drawMessage() {
        text.textSize = 15
        val lines = text.wrap(message ?: "", LOGICAL_W - PANEL_MARGIN * 2 - 28)
        var y = drawPanel(messageTitle, lines.size * 20)
        text.textSize = 15
        for (line in lines) {
            text.draw(pixels, line, 30, y, C_TEXT)
            y += 20
        }
        drawCloseButton()
    }

    private fun drawGameOver() {
        pixels.clear(C_BG)
        // 画面の高さは端末で変わるので、中央から組み立てる。
        val mid = logicalH / 2
        text.textSize = 20
        text.drawCentered(pixels, "ざいせい はさん", LOGICAL_W / 2, mid - 80, C_TEXT)
        text.textSize = 15
        text.drawCentered(pixels, "しきんが つきました", LOGICAL_W / 2, mid - 40, C_TEXT)
        text.drawCentered(pixels, "さいだい じんこう ${city.population}", LOGICAL_W / 2, mid - 16, C_TEXT)
        text.textSize = 16
        pixels.drawRect(LOGICAL_W / 2 - 70, restartButtonY(), 140, 28, C_LINE)
        text.drawCentered(pixels, "もういちど", LOGICAL_W / 2, restartButtonY() + 4, C_TEXT)
    }

    /** もういちどボタンの y。描画と判定で共有する。 */
    private fun restartButtonY(): Int = logicalH / 2 + 30

    /** とじるボタンの y。パネルの下端に合わせ、描画と判定で共有する。 */
    private fun closeButtonY(): Int = panelTop() + panelHeight - 34

    private fun drawCloseButton() {
        val x = CLOSE_X
        val y = closeButtonY()
        pixels.fillRect(x, y, 64, 26, C_BG)
        pixels.drawRect(x, y, 64, 26, C_LINE)
        text.textSize = 15
        text.draw(pixels, "とじる", x + 8, y + 4, C_TEXT)
    }

    // ------------------------------------------------------------------
    // 入力
    // ------------------------------------------------------------------

    /** 画面座標を論理座標へ。 */
    private fun toLogicalX(x: Float): Int = ((x - offsetX) / scale).toInt()
    private fun toLogicalY(y: Float): Int = ((y - offsetY) / scale).toInt()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val lx = toLogicalX(event.x)
        val ly = toLogicalY(event.y)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                dragged = false
                pointerDown = true
                panning = false
                strokeCancelled = false
                // パネルを開いている間は、下のツールバーに触れさせない。
                draggingToolbar = screen == Screen.PLAYING && ly >= logicalH - Hud.TOOLBAR_HEIGHT
                // マップ上なら、押した時点から置き始める（なぞって敷けるように）
                if (screen == Screen.PLAYING && isOnMap(ly) && pendingMonument == null) {
                    applyToolAt(lx, ly)
                }
                return true
            }

            // 2本目の指が触れたら、置くのをやめて地図を動かす操作に切り替える。
            MotionEvent.ACTION_POINTER_DOWN -> {
                panning = true
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) panning = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                if (abs(dx) > scale * 2 || abs(dy) > scale * 2) dragged = true

                if (draggingToolbar) {
                    toolScroll = (toolScroll - (dx / scale).toInt())
                        .coerceIn(0, max(0, Hud.toolStripWidth() - LOGICAL_W))
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                    return true
                }

                if (panning) {
                    // 指の動きにあわせて地図を送る
                    // 画面の移動量を、斜めの軸にほどいてタイル座標へ変換する
                    val lx2 = dx / scale * zoomDen / zoomNum
                    val ly2 = dy / scale * zoomDen / zoomNum
                    camX -= (lx2 / (Iso.TILE_W / 2f) + ly2 / (Iso.TILE_H / 2f)) / 2f
                    camY -= (ly2 / (Iso.TILE_H / 2f) - lx2 / (Iso.TILE_W / 2f)) / 2f
                    clampCamera()
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                    return true
                }

                if (screen == Screen.PLAYING && isOnMap(ly) &&
                    pendingMonument == null && !strokeCancelled
                ) {
                    // なぞって連続で置く
                    applyToolAt(lx, ly)
                }
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }

            MotionEvent.ACTION_UP -> {
                pointerDown = false
                if (!dragged && !panning) handleTap(lx, ly)
                draggingToolbar = false
                panning = false
                strokeCancelled = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun isOnMap(ly: Int): Boolean =
        ly >= Hud.STATUS_HEIGHT && ly < logicalH - Hud.TOOLBAR_HEIGHT

    private fun handleTap(lx: Int, ly: Int) {
        when (screen) {
            Screen.GAME_OVER -> {
                if (ly in restartButtonY()..(restartButtonY() + 28)) onRestartRequested?.invoke()
                return
            }
            Screen.BUDGET -> {
                // 税率の増減（drawBudget の配置と同じ値を使う）
                if (ly in 58..80) {
                    if (lx in TAX_MINUS_X..(TAX_MINUS_X + 26)) {
                        city.taxRate = (city.taxRate - 1).coerceAtLeast(0); onStateChanged?.invoke()
                    }
                    if (lx in TAX_PLUS_X..(TAX_PLUS_X + 26)) {
                        city.taxRate = (city.taxRate + 1).coerceAtMost(20); onStateChanged?.invoke()
                    }
                }
                if (isCloseTapped(lx, ly)) { screen = Screen.PLAYING }
                return
            }
            Screen.MONUMENTS -> {
                if (isCloseTapped(lx, ly)) { screen = Screen.PLAYING; return }
                // 一覧から選ぶ。行の位置は描画と同じ式で求める。
                val idx = (ly - monumentListTop()) / MONUMENT_ROW_H
                val m = Monument.entries.getOrNull(idx)
                if (m != null) selectMonument(m)
                return
            }
            Screen.MESSAGE -> {
                if (isCloseTapped(lx, ly)) {
                    screen = Screen.PLAYING
                    message = null
                }
                return
            }
            Screen.PLAYING -> {}
        }

        // チュートリアルの「つぎへ」
        if (tutorial.active && tutorial.awaitingContinue()) {
            val bannerTop = logicalH - Hud.TOOLBAR_HEIGHT - bannerHeight()
            if (ly >= bannerTop && ly < logicalH - Hud.TOOLBAR_HEIGHT) {
                tutorial.onContinuePressed()
                showTutorialMessageIfNeeded()
                if (tutorial.finished) onTutorialFinished?.invoke()
                onStateChanged?.invoke()
                return
            }
        }

        val toolbarTop = logicalH - Hud.TOOLBAR_HEIGHT

        // 速度
        if (ly in 17..44 && lx in (SPEED_X - 4)..(SPEED_X + 36)) { cycleSpeed(); return }
        // 拡大率
        if (ly in 17..44 && lx in ZOOM_X..(ZOOM_X + 64)) { cycleZoom(); return }

        // 予算・けんちく
        if (ly >= toolbarTop + 34) {
            if (lx in BUDGET_X..(BUDGET_X + 62)) {
                if (!tutorial.allowsBudget()) { showToast("いまは ステップの とおりに"); return }
                screen = Screen.BUDGET
                tutorial.onBudgetOpened()
                showTutorialMessageIfNeeded()
                return
            }
            if (lx >= MONUMENT_X) {
                if (tutorial.active) { showToast("チュートリアルの あとで"); return }
                screen = Screen.MONUMENTS
                return
            }
        }

        // ツールの選択
        if (ly >= toolbarTop && ly < toolbarTop + 4 + Hud.TOOL_SIZE + 4) {
            val hit = (lx + toolScroll - Hud.TOOL_GAP) / (Hud.TOOL_SIZE + Hud.TOOL_GAP)
            val tool = Hud.TOOLS.getOrNull(hit)
            if (tool != null) {
                if (!tutorial.allowsBuild(tool.kind) && tool.kind != TileKind.EMPTY) {
                    showToast("いまは ちがう どうぐです")
                    return
                }
                selectedTool = tool.kind
                pendingMonument = null
            }
            return
        }

        // マップ（モニュメントの設置）
        if (isOnMap(ly)) {
            val m = pendingMonument
            if (m != null) {
                placeMonument(m, lx, ly)
            }
        }
    }

    private fun isCloseTapped(lx: Int, ly: Int): Boolean =
        lx >= CLOSE_X && lx <= CLOSE_X + 64 &&
            ly >= closeButtonY() && ly <= closeButtonY() + 26

    private fun selectMonument(m: Monument) {
        val blocker = when {
            m in city.builtMonuments -> "すでに たてられています"
            city.population < m.unlockPopulation -> "じんこう ${m.unlockPopulation} で かいきん"
            city.funds < m.cost -> "しきんが たりません"
            else -> null
        }
        if (blocker != null) { showToast(blocker); return }
        pendingMonument = m
        screen = Screen.PLAYING
        showToast("ばしょを タップ")
    }

    private fun placeMonument(m: Monument, lx: Int, ly: Int) {
        val (tx, ty) = mapCoords(lx, ly) ?: return
        val blocker = city.monumentBlocker(tx, ty, m)
        if (blocker != null) { showToast(blocker); return }
        if (city.buildMonument(tx, ty, m)) {
            pendingMonument = null
            messageTitle = m.label
            message = m.blurb
            screen = Screen.MESSAGE
            onStateChanged?.invoke()
        }
    }

    /**
     * 論理座標からタイル座標へ。マップ外なら null。
     *
     * 描画と同じ原点を使って、画面座標を菱形の格子へ戻す。
     */
    private fun mapCoords(lx: Int, ly: Int): Pair<Int, Int>? {
        if (!isOnMap(ly)) return null
        val mapTop = Hud.STATUS_HEIGHT
        val mapHeight = logicalH - Hud.STATUS_HEIGHT - Hud.TOOLBAR_HEIGHT
        val originX = LOGICAL_W / 2 - Iso.screenX2(camX, camY).toInt() * zoomNum / zoomDen
        val originY = mapTop + mapHeight / 2 - Iso.screenY2(camX, camY).toInt() * zoomNum / zoomDen
        // 菱形の中心を基準に戻す
        val sx = (lx - originX).toFloat() * zoomDen / zoomNum - Iso.TILE_W / 2f
        val sy = (ly - originY).toFloat() * zoomDen / zoomNum - Iso.TILE_H / 2f
        val (tx, ty) = Iso.tileAt(sx, sy)
        if (!city.inBounds(tx, ty)) return null
        return tx to ty
    }

    private fun applyToolAt(lx: Int, ly: Int) {
        if (city.gameOver) return
        val (tx, ty) = mapCoords(lx, ly) ?: return

        if (selectedTool == TileKind.EMPTY) {
            if (tutorial.active) { showToast("いまは こわせません"); return }
            if (city.bulldoze(tx, ty)) { onStateChanged?.invoke(); invalidate() }
            return
        }

        if (!tutorial.allowsBuild(selectedTool)) {
            showToast("いまは ちがう どうぐです")
            return
        }

        // 同じところに同じものを置き直さない（なぞったときに無駄に払わない）
        val existing = city.tileAt(tx, ty)
        if (existing.kind == selectedTool) return

        val blocker = city.buildBlocker(tx, ty, selectedTool)
        if (blocker != null) { showToast(blocker); return }

        // 求められた数を超えて置かせない。余分な設置は資金と土地の無駄になり、
        // 「あと N」の意味も分からなくなる。
        if (tutorial.active && tutorial.remaining() <= 0 && tutorial.step?.highlightTool != null) {
            showToast("つぎの ステップへ すすみます")
            return
        }

        // チュートリアル中は、道路に接していない区分・発電所を断る。
        // 置けてしまうと「電気の来ない街」ができて、
        // 何が悪いのか分からないまま詰んでしまう。
        if (tutorial.active && !city.touchesRoad(tx, ty) &&
            (selectedTool.isZone || selectedTool.isPowerPlant)
        ) {
            showToast("どうろの となりに おいてください")
            return
        }

        if (city.build(tx, ty, selectedTool)) {
            tutorial.onBuilt(selectedTool)
            showTutorialMessageIfNeeded()
            if (tutorial.finished) onTutorialFinished?.invoke()
            onStateChanged?.invoke()
            invalidate()
        }
    }

    private fun showToast(msg: String) {
        toast = msg
        toastUntil = System.currentTimeMillis() + 1_600
        invalidate()
    }

    /**
     * チュートリアルの状態にあわせて画面を整える。
     *
     * そのステップで使う道具を自動で選び、ツールバーを見える位置まで送る。
     * 道具を自分で探させると、見つけられないまま他の操作も塞がれて詰む。
     */
    private fun showTutorialMessageIfNeeded() {
        if (!tutorial.active) return
        // 手順どおりに進めれば資金が尽きないように補う。
        if (city.funds < 2_000) city.funds += Tutorial.GRANT

        val needed = tutorial.step?.highlightTool ?: return
        if (selectedTool != needed) {
            selectedTool = needed
            pendingMonument = null
            // 道具が変わったら、いま触れている指での連続設置を打ち切る。
            // 続けると、指を離さないまま次のステップまで置いてしまう。
            strokeCancelled = true
        }
        // 選んだ道具が画面の外なら、見えるところまでツールバーを送る。
        val index = Hud.TOOLS.indexOfFirst { it.kind == needed }
        if (index >= 0) {
            val x = Hud.toolX(index)
            val maxScroll = max(0, Hud.toolStripWidth() - LOGICAL_W)
            if (x - toolScroll < 0 || x - toolScroll + Hud.TOOL_SIZE > LOGICAL_W) {
                toolScroll = (x - LOGICAL_W / 2).coerceIn(0, maxScroll)
            }
        }
    }

    var onTutorialFinished: (() -> Unit)? = null
    var onRestartRequested: (() -> Unit)? = null

    /** カメラを街の中央へ。 */
    fun centerCamera() {
        camX = city.width / 2f
        camY = city.height / 2f
        clampCamera()
    }

    /**
     * カメラが街から離れすぎないようにする。
     * 斜め見下ろしでは端が菱形にはみ出すので、少し余裕を持たせる。
     */
    private fun clampCamera() {
        camX = camX.coerceIn(-2f, city.width + 2f)
        camY = camY.coerceIn(-2f, city.height + 2f)
    }

    fun pause() { speedIndex = 0 }
}
