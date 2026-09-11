package io.github.hatake716.pixelcity.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import io.github.hatake716.pixelcity.audio.Audio
import io.github.hatake716.pixelcity.audio.Bgm
import io.github.hatake716.pixelcity.audio.Sfx
import io.github.hatake716.pixelcity.data.Settings
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
         *
         * 端末の幅を倍率で割り切った値を入れるので、[LOGICAL_W_BASE] を
         * そのまま使うとはかぎらない。こうしないと、画面の左右に余白が出る。
         * 最初に画面の大きさが決まった時点（[onSizeChanged]）で1度だけ決まる。
         */
        var LOGICAL_W = LOGICAL_W_BASE
            private set

        /** 横幅の目安。実際の値は端末の幅にあわせて決まる。 */
        const val LOGICAL_W_BASE = 480
        /** 縦の既定値。実際の高さは端末の縦横比にあわせて決まる。 */
        const val LOGICAL_H = 420
        /** 横に取りうる上限。極端に横長の画面でも、間延びしすぎないようにする。 */
        const val LOGICAL_W_MAX = 900

        /**
         * 端末の大きさから、拡大率と論理解像度を決める。
         *
         * 画面いっぱいに、余白なく描くための計算。
         *  - 倍率は整数。半端な倍率にすると、1ドットの大きさが揃わずぼやける
         *  - 論理解像度は「端末の大きさ ÷ 倍率」。割り切れる値にすれば余白が出ない
         *
         * タイトル・スロット・おてほんの各画面でも同じ計算を使う。
         * ばらばらに書くと、画面を移ったときに大きさが変わってしまう。
         *
         * @return (倍率, 論理の横幅, 論理の高さ)
         */
        fun layoutFor(w: Int, h: Int): Triple<Int, Int, Int> {
            var scale = max(1, w / LOGICAL_W_BASE)
            while (scale > 1 && h / scale < LOGICAL_H) scale--
            val lw = (w / scale).coerceIn(LOGICAL_W_BASE, LOGICAL_W_MAX)
            val lh = (h / scale).coerceIn(LOGICAL_H, LOGICAL_H_MAX)
            // 横幅はここで決めてしまう。タイトル画面など、ゲーム画面より先に
            // 表示されるものからも参照されるので、最初に呼ばれた時点で入れておく。
            LOGICAL_W = lw
            return Triple(scale, lw, lh)
        }
        /**
         * 縦に取りうる上限。
         *
         * 端末の高さを倍率で割った値がこれを超えると、上下に余白が出る。
         * 1080×2400 の端末は 2倍で 1200 必要になるので、余裕をもたせてある。
         */
        const val LOGICAL_H_MAX = 1600
        /** 1か月の実時間（ミリ秒）。速度倍率で割る。 */
        const val MONTH_MILLIS = 8_000L

        /**
         * 時間の速さ。0 は停止。
         *
         * 街が育つのを待つ場面が多いので、30倍まで用意した。
         * 30倍なら1年が約3秒で進む。
         */
        private val SPEEDS = intArrayOf(0, 1, 2, 4, 10, 30)

        /** 1フレームで進める月数の上限。早送りでも描画が止まらないようにする。 */
        private const val MAX_STEPS_PER_FRAME = 4

        /**
         * 拡大率の段（分子, 分母）。広いマップを見渡せるよう、引いた画を厚くしてある。
         * 1/4 = 街の全体像、1/2 = 区画の配置、1/1 = 標準、2/1 = 建物の細部。
         */
        /**
         * 拡大率の分母。描画は整数で行うので、倍率はこの分母の分数で表す。
         * 64 なら、1/16 から 1/1 までを 1% 未満の誤差で表せる。
         */
        private const val ZOOM_DEN = 64

        /** 引ける限界と、寄れる限界。 */
        private const val ZOOM_MIN = 1f / 16f
        private const val ZOOM_MAX = 1f

        /** 釦で切り替えるときの段。つまむ操作は段に縛られない。 */
        private val ZOOM_PRESETS = floatArrayOf(1f / 16f, 1f / 8f, 1f / 4f, 1f / 2f, 1f)

        // 配置。描画と当たり判定で同じ値を使うため、ここに集める。
        /**
         * つまんで1段動かすのに必要な、指の開きの倍率。
         *
         * 拡大率の段は2倍ずつなので、本来は 2.0 が素直。
         * ただしそれだと指をいっぱいに広げないと変わらないので、
         * 少し手前で反応するようにしてある。
         */
        private const val PINCH_RATIO = 1.5f

        /** 「じっこう」「やめる」の帯の高さ。 */
        private const val SELECTION_BAR_H = 42

        /** ツールバーの一番下の段（情報・予算・けんちく）の高さ。 */
        private const val BOTTOM_ROW_H = 30

        /** 人口の節目。越えるたびに短い音が鳴る。 */
        private val MILESTONES = intArrayOf(1_000, 5_000, 10_000, 25_000, 50_000, 100_000)

        private const val SPEED_X = 150
        private const val ZOOM_X = 196
        private const val INFO_X = 316
        private const val BUDGET_X = 370
        private const val MONUMENT_X = 420
        private const val BANNER_H = 104
        private const val PANEL_MARGIN = 16
        private const val TAX_MINUS_X = 200
        private const val TAX_PLUS_X = 240
        /** 本文の文字の大きさ。折り返しの計算と描画で必ず同じ値を使う。 */
        private const val BODY_SIZE = 15

        /**
         * 吹き出しの本文を折り返す幅。
         *
         * 禁則処理で句読点が2文字ぶん はみ出せるので、そのぶん狭くとる。
         * ここを画面幅ぎりぎりにすると、文の右端が切れる。
         */
        private val BODY_WRAP_W: Int get() = LOGICAL_W - 52

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
        private val CLOSE_X: Int get() = LOGICAL_W - 84
    }

    /** 画面の状態。 */
    enum class Screen { PLAYING, BUDGET, MONUMENTS, MESSAGE, GAME_OVER, INFO, STYLE_EDIT }

    var screen: Screen = Screen.PLAYING
        private set

    /** 端末の縦横比にあわせた論理の高さ。onSizeChanged で決まる。 */
    private var logicalH = LOGICAL_H

    /**
     * 通知の欄・操作の欄の高さ（論理ピクセル）。
     *
     * 絵は画面の端まで描くが、状態表示とツールバーはこの内側に置く。
     * そうしないと、時計や戻る釦と重なって押せなくなる。
     */
    private val insetTop: Int get() = SystemBars.top(scale)
    private val insetBottom: Int get() = SystemBars.bottom(scale)

    /**
     * 状態表示の帯の下端。通知の欄のぶんだけ下げる。
     * 地図はこの下から始まる。
     */
    private val statusBottom: Int get() = insetTop + Hud.STATUS_HEIGHT

    /**
     * ツールバーの上端。操作の欄のぶんだけ上げる。
     * 地図はここまで。
     */
    private val toolbarTopY: Int get() = logicalH - insetBottom - Hud.TOOLBAR_HEIGHT
    private var pixels = PixelCanvas(LOGICAL_W, LOGICAL_H)
    private val renderer = CityRenderer()
    private val info = InfoPanel(GbText(context))

    /** 音。鳴らせないときも遊びは止めないよう、失敗は握りつぶす作り。 */
    val audio = Audio()
    private val styleEditor = StyleEditor(GbText(context))
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
     * 地図の拡大率。1.0 が等倍で、小さいほど引いた画になる。
     *
     * 段ではなく連続した値にしてある。つまむ操作に段でついていくと、
     * 一定以上ひらいた瞬間に絵が跳ぶ。指の動きにそのまま追いたい。
     */
    var zoom: Float = 0.25f
        private set(value) {
            field = value.coerceIn(ZOOM_MIN, ZOOM_MAX)
        }

    /**
     * いまの拡大率を分数で。描画は整数の掛け算で行うため。
     *
     * 分母を [ZOOM_DEN] に固定し、分子だけを変える。
     * こうすると、どの倍率でも同じ式で描ける。
     */
    private val zoomNum: Int get() = Math.round(zoom * ZOOM_DEN).coerceAtLeast(1)
    private val zoomDen: Int get() = ZOOM_DEN

    /** 段で切り替える釦のために、近い段を覚えておく。 */
    private val zoomStepIndex: Int
        get() = ZOOM_PRESETS.indices.minByOrNull { Math.abs(ZOOM_PRESETS[it] - zoom) } ?: 0

    /** 釦で拡大率を切り替える。決まった段を順に回る。 */
    fun cycleZoom() {
        val next = (zoomStepIndex + 1) % ZOOM_PRESETS.size
        zoomAround(ZOOM_PRESETS[next], LOGICAL_W / 2, (statusBottom + toolbarTopY) / 2)
        clampCamera()
        invalidate()
    }

    /**
     * 拡大率を [target] にする。[lx]/[ly] が指しているところを動かさない。
     *
     * ただ倍率を変えるだけだと、画面の中心を軸に伸び縮みする。
     * 指で広げたところが動かないほうが、拡げている感じになる。
     *
     * @return 実際に変わったか
     */
    private fun zoomAround(target: Float, lx: Int, ly: Int): Boolean {
        val next = target.coerceIn(ZOOM_MIN, ZOOM_MAX)
        // 描く側は分数に丸めるので、丸めた結果が同じなら何も変わらない
        if (Math.round(next * ZOOM_DEN) == Math.round(zoom * ZOOM_DEN)) {
            zoom = next
            return false
        }
        // 変える前に、その点が指しているタイルを覚えておく
        val before = mapCoordsFree(lx, ly)
        zoom = next
        val after = mapCoordsFree(lx, ly)
        if (before != null && after != null) {
            // 同じ点が同じタイルを指すように、地図をずらす
            camX += before.first - after.first
            camY += before.second - after.second
        }
        clampCamera()
        invalidate()
        return true
    }

    // --- 入力 ---
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var dragged = false
    private var pointerDown = false
    /** 2本指で地図を動かしている最中か。 */
    private var panning = false
    /**
     * つまむ操作の基準になる、2本指の間隔。
     * ここから何倍に開いた/縮めたかで、拡大率の段を決める。
     */
    private var pinchBase = 0f
    /** つまんで拡大率を変えたか。指を離したときに「置く」動作を起こさないために使う。 */
    private var pinched = false
    /** 道具が切り替わったので、指を離すまで置くのをやめる。 */
    private var strokeCancelled = false

    // --- 選んでいるマス ---
    /**
     * これから建てる（壊す）マス。タイル番号（y * width + x）で持つ。
     *
     * タップした時点では何も起きず、ここに溜まるだけ。
     * 「じっこう」を押して初めて実行する。
     * 押し間違いで街が壊れたり、資金が減ったりしないようにするため。
     */
    private val selection = LinkedHashSet<Int>()

    /** いま選んでいるマスの数。 */
    val selectedCount: Int get() = selection.size

    /** 道具を変える。選んであったマスは捨てられる。 */
    fun selectTool(kind: TileKind) {
        selectedTool = kind
        invalidate()
    }

    /** 選んだマスをまとめて実行する。ツールバーの「じっこう」と同じ。 */
    fun runSelectionForTest() = runSelection()

    /** 拡大率を直に決める。試験で、端に寄っていない状態を作るために使う。 */
    fun setZoomForTest(value: Float) {
        zoom = value
        clampCamera()
        invalidate()
    }

    /** なぞって選んでいる最中に、足しているのか外しているのか。 */
    private var strokeAdding = true

    /** 選んだマスを全部実行したときの費用。 */
    private val selectionCost: Int
        get() {
            if (selectedTool == TileKind.EMPTY) return selection.size * BuildCost.BULLDOZE
            return selection.size * BuildCost.cost(selectedTool)
        }
    private var toolScroll = 0
    private var categoryScroll = 0
    private var draggingToolbar = false
    /** 分類の帯をなぞっているか。 */
    private var draggingCategories = false

    // --- 進行 ---
    var speedIndex: Int = 1
        private set
    private var monthAccumulator = 0L
    private var lastFrameTime = 0L

    /**
     * いま持っている道具。
     *
     * 変えたら、選んであるマスは捨てる。
     * 道路のつもりで選んだマスに、そのまま発電所が建っては困る。
     */
    var selectedTool: TileKind = TileKind.ROAD
        private set(value) {
            if (field == value) return
            field = value
            selection.clear()
        }

    /** いま開いているツールの分類。 */
    private var category: Hud.Category = Hud.Category.ZONE

    // 右下のボタンの位置。描いたときに覚えて、判定で使う。
    /** 一番下の段の上端。描いた位置をそのまま当たり判定に使う。 */
    private var bottomRowTop = 0
    private var infoButtonX = 0 to 0
    private var budgetButtonX = 0 to 0
    private var monumentButtonX = 0 to 0
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
        info.custom = city.customStyle
        // 前に遊んだときの設定を引き継ぐ
        audio.sfxEnabled = Settings.sfxEnabled(context)
        audio.bgmEnabled = Settings.bgmEnabled(context)
        info.sfxOn = audio.sfxEnabled
        info.bgmOn = audio.bgmEnabled
        // 街の様子にあう曲から始める
        updateBgm()
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
            // 早送りでも、1フレームで進めすぎない。
            // まとめて何十か月も計算すると、その間 画面が固まる。
            var steps = 0
            while (monthAccumulator >= MONTH_MILLIS && steps < MAX_STEPS_PER_FRAME) {
                monthAccumulator -= MONTH_MILLIS
                advanceMonth()
                steps++
            }
            if (steps >= MAX_STEPS_PER_FRAME) monthAccumulator = 0
        }
        postInvalidateOnAnimation()
    }

    /** 前の月の災害。同じ災害で何度も鳴らさないために覚えておく。 */
    private var lastDisasterSeen: String? = null
    /** 越えた人口の節目。 */
    private var milestoneSeen = 0

    private fun advanceMonth() {
        val before = city.population
        city.step()
        tutorial.onMonthPassed()
        showTutorialMessageIfNeeded()

        // 災害。新しく起きたときだけ鳴らす。
        if (city.lastDisaster != null && city.lastDisaster != lastDisasterSeen) {
            audio.play(Sfx.DISASTER)
        }
        lastDisasterSeen = city.lastDisaster

        // 人口の節目。早送り中に何度も鳴らないよう、越えた段を覚えておく。
        val step = milestoneFor(city.population)
        if (step > milestoneSeen && city.population > before) {
            audio.play(Sfx.MILESTONE)
        }
        milestoneSeen = step

        if (city.gameOver) {
            screen = Screen.GAME_OVER
            audio.play(Sfx.BANKRUPT)
            speedIndex = 0
        }
        updateBgm()
        onStateChanged?.invoke()
    }

    /**
     * 人口が、どの節目まで来ているか。
     * 1,000 / 5,000 / 10,000 / 25,000 / 50,000 / 100,000。
     */
    private fun milestoneFor(population: Int): Int =
        MILESTONES.count { population >= it }

    /**
     * 街の様子にあわせて曲を選ぶ。
     *
     * 同じ曲を何時間も聞かせないための仕組みで、
     * 苦しいときは曲でも分かるようにしてある。
     */
    private fun updateBgm() {
        val bgm = when {
            // 資金が尽きかけ、または借金が続いている
            city.funds < 0 || city.monthsInDebt > 0 -> Bgm.TROUBLE
            // 育ってきた街
            city.population >= 2_000 -> Bgm.CITY
            else -> Bgm.DAWN
        }
        audio.setBgm(bgm)
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
        // 横も縦も、端末にあわせて論理解像度そのものを伸ばす。
        // 倍率で割り切った値にすると、拡大しても1ドットが正方形のまま、
        // かつ画面いっぱいに描けて余白が出ない。
        val (s2, lw, lh) = layoutFor(w, h)
        scale = s2
        LOGICAL_W = lw
        logicalH = lh
        if (pixels.width != LOGICAL_W || pixels.height != logicalH) {
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
        val mapTop = statusBottom
        val mapHeight = toolbarTopY - statusBottom

        renderer.draw(
            pixels, city, camX, camY, zoomNum, zoomDen, mapTop, mapHeight, info.overlay,
            highlightForSelection(),
            selected = selection,
            suggest = placementHint(),
            suggestOn = blinkOn(),
            animationPhase = growthPhase(),
        )

        drawStatusBar()
        drawToolbar(mapTop + mapHeight)
        // 選んでいるあいだだけ、ツールバーの上に帯を重ねる。
        // 地図の大きさは変えない。選ぶたびに地図が動くと、狙いが定まらない。
        if (selection.isNotEmpty() && screen == Screen.PLAYING) {
            drawSelectionBar(selectionBarBottom())
        } else {
            runButtonX = 0 to 0
            cancelButtonX = 0 to 0
        }

        when (screen) {
            Screen.BUDGET -> drawBudget()
            Screen.MONUMENTS -> drawMonuments()
            Screen.MESSAGE -> drawMessage()
            Screen.GAME_OVER -> drawGameOver()
            Screen.INFO -> {
                // 欄の高さは倍率が要るので、ここで渡す
                info.insetTop = insetTop
                info.insetBottom = insetBottom
                info.draw(pixels, city, logicalH)
            }
            Screen.STYLE_EDIT -> {
                styleEditor.insetTop = insetTop
                styleEditor.insetBottom = insetBottom
                styleEditor.draw(pixels, city.customStyle, logicalH)
            }
            Screen.PLAYING -> {
                if (info.overlay != CityRenderer.Overlay.NONE) drawOverlayBadge()
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
        // 通知の欄の裏まで地の色を敷き、その下に中身を置く
        pixels.fillRect(0, 0, LOGICAL_W, statusBottom, C_BG)
        pixels.fillRect(0, statusBottom - 2, LOGICAL_W, 2, C_LINE)

        text.textSize = 16
        text.draw(pixels, "$" + city.funds.toString(), 4, insetTop + 1, C_TEXT)

        val year = 1900 + city.month / 12
        val mon = city.month % 12 + 1
        text.draw(pixels, "${year}ねん${mon}がつ", LOGICAL_W - 150, insetTop + 1, C_TEXT)

        text.draw(pixels, "じんこう " + city.population, 4, insetTop + 20, C_TEXT)

        // 需要バー R/C/I
        Hud.drawDemandBars(pixels, city, 258, insetTop + 19, 28)

        // 速度
        val speedLabel = when (speedIndex) {
            0 -> "‖"
            1 -> "▶"
            2 -> "▶▶"
            3 -> "▶▶▶"
            4 -> "×10"
            else -> "×30"
        }
        text.draw(pixels, speedLabel, SPEED_X, insetTop + 20, C_TEXT)

        // 拡大率の切り替え
        val zoomLabel = when {
            zoom < 0.10f -> "ぜんたい"
            zoom < 0.19f -> "ひろい"
            zoom < 0.36f -> "ちゅう"
            zoom < 0.72f -> "ふつう"
            else -> "よせる"
        }
        pixels.drawRect(ZOOM_X, insetTop + 18, 64, 24, C_LINE)
        text.textSize = 14
        text.draw(pixels, zoomLabel, ZOOM_X + 5, insetTop + 21, C_TEXT)
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
        // 操作の欄の裏まで地の色を敷く
        pixels.fillRect(0, top, LOGICAL_W, Hud.TOOLBAR_HEIGHT + insetBottom, C_BG)
        pixels.fillRect(0, top, LOGICAL_W, 2, C_LINE)

        // --- 分類の帯 ---
        text.textSize = 17
        for ((i, cat) in Hud.Category.entries.withIndex()) {
            val x = Hud.categoryX(i) - categoryScroll
            if (x + Hud.CATEGORY_W < 0 || x > LOGICAL_W) continue
            val y = top + 5
            val on = cat == category
            pixels.fillRect(x, y, Hud.CATEGORY_W, Hud.CATEGORY_H, if (on) Palette.UI_ACCENT else C_BG)
            pixels.drawRect(x, y, Hud.CATEGORY_W, Hud.CATEGORY_H, C_LINE)
            // 文字は枠の上下の真ん中に置く
            text.drawCentered(
                pixels, cat.label, x + Hud.CATEGORY_W / 2, y + (Hud.CATEGORY_H - 17) / 2,
                if (on) Palette.UI_BG else C_TEXT,
            )
        }

        // --- 道具の並び ---
        val toolTop = top + 5 + Hud.CATEGORY_H + 6
        val tools = Hud.toolsIn(category)
        for ((i, tool) in tools.withIndex()) {
            val x = Hud.toolX(i) - toolScroll
            if (x + Hud.TOOL_SIZE < 0 || x > LOGICAL_W) continue
            val selected = tool.kind == selectedTool
            pixels.drawRect(x, toolTop, Hud.TOOL_SIZE, Hud.TOOL_SIZE, C_LINE)
            if (selected) {
                pixels.drawRect(x + 1, toolTop + 1, Hud.TOOL_SIZE - 2, Hud.TOOL_SIZE - 2, C_TEXT)
                pixels.drawRect(x + 2, toolTop + 2, Hud.TOOL_SIZE - 4, Hud.TOOL_SIZE - 4, C_TEXT)
            }

            val sprite = iconFor(tool.kind)
            if (sprite != null) {
                drawIcon(sprite, x + 1, toolTop + 1, Hud.TOOL_SIZE - 2)
            } else {
                // 取り壊しは×印。枠の大きさに合わせて引く。
                val pad = Hud.TOOL_SIZE / 5
                val span = Hud.TOOL_SIZE - pad * 2
                for (k in 0 until span) {
                    for (t in 0 until 3) {
                        pixels.set(x + pad + k + t, toolTop + pad + k, Palette.RED)
                        pixels.set(x + pad + span - k + t, toolTop + pad + k, Palette.RED)
                    }
                }
            }

            // 解禁されていないものは暗くする
            if (!BuildCost.isUnlocked(tool.kind, city.population)) {
                for (yy in toolTop until toolTop + Hud.TOOL_SIZE) {
                    for (xx in x until x + Hud.TOOL_SIZE) {
                        if ((xx + yy) % 2 == 0) pixels.set(xx, yy, C_BG)
                    }
                }
            }

            if (tutorial.active && tutorial.step?.highlightTool == tool.kind && blinkOn()) {
                pixels.drawRect(x - 3, toolTop - 3, Hud.TOOL_SIZE + 6, Hud.TOOL_SIZE + 6, C_TEXT)
            }
        }
        // --- 一番下の段: 道具の名前と、情報・予算・けんちくの入口 ---
        val rowH = BOTTOM_ROW_H
        val by = top + Hud.TOOLBAR_HEIGHT - rowH - 4
        bottomRowTop = by
        val labelY = by + (rowH - 18) / 2

        val tool = Hud.TOOLS.firstOrNull { it.kind == selectedTool }
        if (tool != null) {
            val price =
                if (tool.kind == TileKind.EMPTY) BuildCost.BULLDOZE
                else BuildCost.cost(tool.kind)
            text.textSize = 18
            val locked = !BuildCost.isUnlocked(tool.kind, city.population)
            val label =
                if (locked) "${tool.label}（まだ つかえません）"
                else "${tool.label} $${price}"
            text.draw(pixels, label, 6, labelY, if (locked) C_DIM else C_TEXT)
        }

        // 右から詰める。文字の幅を測るので、重ならない。
        text.textSize = 16
        var bx = LOGICAL_W - 6
        for ((label, id) in listOf("けんちく" to 2, "よさん" to 1, "じょうほう" to 0)) {
            val w = text.measure(label) + 16
            bx -= w + 6
            pixels.fillRect(bx, by, w, rowH, C_BG)
            pixels.drawRect(bx, by, w, rowH, C_LINE)
            text.draw(pixels, label, bx + 8, by + (rowH - 16) / 2, C_TEXT)
            when (id) {
                0 -> infoButtonX = bx to (bx + w)
                1 -> budgetButtonX = bx to (bx + w)
                else -> monumentButtonX = bx to (bx + w)
            }
        }
        // チュートリアルで、押してほしい釦を点滅させる
        if (tutorial.active && tutorial.step?.highlightBudget == true && blinkOn()) {
            pixels.drawRect(
                budgetButtonX.first - 3, by - 3,
                budgetButtonX.second - budgetButtonX.first + 6, rowH + 6, C_TEXT,
            )
        }
        if (tutorial.active && tutorial.step?.highlightInfo == true && blinkOn()) {
            pixels.drawRect(
                infoButtonX.first - 3, by - 3,
                infoButtonX.second - infoButtonX.first + 6, rowH + 6, C_TEXT,
            )
        }
        if (tutorial.active && tutorial.step?.highlightSpeed == true && blinkOn()) {
            pixels.drawRect(SPEED_X - 4, insetTop + 17, 40, 24, C_TEXT)
        }
    }

    /** 「じっこう」「やめる」の横の範囲。押したか調べるのに使う。 */
    private var runButtonX = 0 to 0
    private var cancelButtonX = 0 to 0
    /** 釦の縦の位置。 */
    private var selectionButtonY = 0

    /**
     * 選んだマスの数と値段、そして「じっこう」「やめる」。
     *
     * ツールバーの中には入れず、そのすぐ上に帯として出す。
     * 中に詰めると、情報・予算の釦と重なってしまう。
     *
     * 押す前に「何マスに、いくらかかるか」が見えるようにする。
     * 実行してから資金が減っていることに気づくのでは遅い。
     */
    private fun drawSelectionBar(bottom: Int) {
        val h = SELECTION_BAR_H
        val y = bottom - h
        selectionButtonY = y

        pixels.fillRect(0, y, LOGICAL_W, h, C_BG)
        pixels.fillRect(0, y, LOGICAL_W, 2, Palette.WATER_LIT)

        val cost = selectionCost
        val enough = city.funds >= cost

        // 何を、何マス、いくらで
        text.textSize = 15
        val what = if (selectedTool == TileKind.EMPTY) "こわす" else "たてる"
        val head = "$what ${selection.size}マス"
        text.draw(pixels, head, 6, y + 6, C_TEXT)
        text.textSize = 14
        text.draw(
            pixels, "$${cost}", 6 + text.measure(head) + 10, y + 8,
            if (enough) C_DIM else Palette.RED,
        )

        // 右から「やめる」「じっこう」の順に置く
        val by = y + 4
        val bh = h - 8
        var bx = LOGICAL_W - 6
        run {
            val label = "やめる"
            val w = text.measure(label) + 16
            bx -= w
            pixels.fillRect(bx, by, w, bh, C_BG)
            pixels.drawRect(bx, by, w, bh, C_LINE)
            text.draw(pixels, label, bx + 8, by + 4, C_TEXT)
            cancelButtonX = bx to (bx + w)
        }
        run {
            val label = "じっこう"
            val w = text.measure(label) + 20
            bx -= w + 8
            // 押せるときは目立たせる
            pixels.fillRect(bx, by, w, bh, if (enough) Palette.UI_ACCENT else C_BG)
            pixels.drawRect(bx, by, w, bh, C_LINE)
            text.draw(
                pixels, label, bx + 10, by + 4,
                if (enough) Palette.UI_BG else C_DIM,
            )
            runButtonX = bx to (bx + w)
        }
    }

    /** その座標が、重ね表示の名札の上か。名札の下のマスは選ばせない。 */
    private fun onOverlayBadge(lx: Int, ly: Int): Boolean =
        info.overlay != CityRenderer.Overlay.NONE &&
            ly >= overlayBadgeY && ly < overlayBadgeY + 26 &&
            lx >= overlayBadge.first && lx <= overlayBadge.second

    /** 重ね表示の名札の範囲。押したか調べるのに使う。 */
    private var overlayBadge = 0 to 0
    private var overlayBadgeY = 0

    /**
     * いま地図に重ねているものの名前。
     *
     * 重ねたまま画面を閉じると、色がついた理由が分からなくなる。
     * 名前を出し、押せばすぐ消せるようにしておく。
     */
    private fun drawOverlayBadge() {
        val label = "${info.overlay.label} ×"
        text.textSize = 16
        val w = text.measure(label) + 20
        val x = 8
        val y = statusBottom + 8
        pixels.fillRect(x, y, w, 26, Palette.UI_BG)
        pixels.drawRect(x, y, w, 26, Palette.UI_ACCENT)
        text.draw(pixels, label, x + 10, y + 5, Palette.UI_ACCENT)
        overlayBadge = x to (x + w)
        overlayBadgeY = y
    }

    /** ツールバーに出すアイコン。地図と同じドット絵を使う。 */
    private fun iconFor(kind: TileKind): Sprite? = when (kind) {
        TileKind.ROAD -> IsoTiles.ROAD_CROSS
        TileKind.AVENUE -> IsoTiles.AVENUE_X
        TileKind.HIGHWAY -> IsoTiles.HIGHWAY_X
        TileKind.RAIL -> IsoTiles.RAIL_X
        TileKind.SUBWAY -> IsoTiles.SUBWAY_X
        TileKind.ZONE_R -> IsoBuildings.HOUSE_1
        TileKind.ZONE_C -> IsoBuildings.SHOP_1
        TileKind.ZONE_I -> IsoBuildings.FACTORY_1
        TileKind.FARM -> IsoTiles.FARM
        TileKind.EMPTY -> null
        else -> IsoBuildings.of(kind)
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
        val lines = text.wrap(step.body, BODY_WRAP_W).size
        return 36 + lines * 18 + if (tutorial.awaitingContinue()) 30 else 6
    }

    /**
     * 建物が せり上がる進み具合 0f..1f。
     *
     * 1か月のあいだで 0→1 へ動かす。早送りのときは月が短いので、
     * 自然と速く建つように見える。
     */
    private fun growthPhase(): Float {
        val speed = SPEEDS[speedIndex]
        if (speed == 0) return 1f
        val monthLength = MONTH_MILLIS / speed
        if (monthLength <= 0) return 1f
        // 月の前半で建て終える。後半は静止して見せる。
        val t = monthAccumulator.toFloat() / (monthLength * 0.6f)
        return t.coerceIn(0f, 1f)
    }

    /** 点滅の位相。0.5秒ごとに切り替える。 */
    private fun blinkOn(): Boolean = (System.currentTimeMillis() / 500L) % 2 == 0L

    private fun drawTutorialBanner() {
        val step = tutorial.step ?: return
        text.textSize = BODY_SIZE
        val lines = text.wrap(step.body, BODY_WRAP_W)
        // 本文の行数と、進むボタンの有無で高さを決める。文字が欠けないようにする。
        val h = bannerHeight()
        val y = toolbarTopY - h
        pixels.fillRect(0, y, LOGICAL_W, h, C_BG)
        pixels.drawRect(0, y, LOGICAL_W, h, C_LINE)
        pixels.drawRect(1, y + 1, LOGICAL_W - 2, h - 2, C_LINE)

        // 見出しは「あと N」の手前で切る。重ねると両方読めなくなる。
        val remain = tutorial.remaining()
        val counter = if (remain > 0) "あと $remain" else ""
        // 章の名前を小さく添える。どこまで進んだかが分かるように。
        text.textSize = 11
        // 章の見出し。マスを選んでいるあいだは、その帯と重なるので出さない。
        if (selection.isEmpty()) {
            val chapterLabel =
                "${tutorial.chapterNumber()}/${tutorial.chapterCount()}　${step.chapter.summary}"
            text.draw(pixels, chapterLabel, 8, y - 13, Palette.UI_DIM)
        }

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
        val y = toolbarTopY - 36
        pixels.fillRect(x, y, w, 26, C_BG)
        pixels.drawRect(x, y, w, 26, C_LINE)
        text.draw(pixels, msg, x + 10, y + 4, C_TEXT)
    }

    /** いま開いているパネルの高さ。中身に合わせて決まる。 */
    private var panelHeight = 0

    /** パネルの上端。画面の中ほどに置く。 */
    private fun panelTop(): Int {
        // 欄の内側で中ほどに置く。高い枠でも、通知の欄には掛からない。
        val usableTop = insetTop
        val usable = logicalH - insetTop - insetBottom
        return (usableTop + (usable - panelHeight) / 2).coerceAtLeast(usableTop + 8)
    }

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
    /** 2本指の間隔。つまむ操作の判定に使う。 */
    private fun pinchSpan(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return kotlin.math.hypot(dx, dy)
    }

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
                val tbTop = toolbarTopY
                draggingCategories = screen == Screen.PLAYING &&
                    ly >= tbTop + 4 && ly < tbTop + 4 + Hud.CATEGORY_H
                draggingToolbar = screen == Screen.PLAYING && ly >= tbTop && !draggingCategories
                // マップ上なら、押した時点から選び始める（なぞってまとめて選べる）
                if (screen == Screen.PLAYING && isOnMap(ly) && pendingMonument == null &&
                    !onOverlayBadge(lx, ly)
                ) {
                    // すでに選んであるマスを押したら、なぞるあいだは「外す」側にする。
                    // 選びすぎたときに、同じ動きで取り消せる。
                    val at = tileIndexAt(lx, ly)
                    strokeAdding = at == null || at !in selection
                    selectAt(lx, ly)
                }
                return true
            }

            // 2本目の指が触れたら、置くのをやめて地図を動かす操作に切り替える。
            MotionEvent.ACTION_POINTER_DOWN -> {
                panning = true
                lastTouchX = event.x
                lastTouchY = event.y
                pinchBase = pinchSpan(event)
                pinched = false
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 2) {
                    panning = false
                    pinchBase = 0f
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                if (abs(dx) > scale * 2 || abs(dy) > scale * 2) dragged = true

                if (draggingCategories) {
                    val stripW = Hud.categoryX(Hud.Category.entries.size)
                    categoryScroll = (categoryScroll - (dx / scale).toInt())
                        .coerceIn(0, max(0, stripW - LOGICAL_W))
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                    return true
                }

                if (draggingToolbar) {
                    toolScroll = (toolScroll - (dx / scale).toInt())
                        .coerceIn(0, max(0, Hud.toolStripWidth(category) - LOGICAL_W))
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                    return true
                }

                // つまんで拡大・縮小。地図送りより先に見る。
                if (panning && event.pointerCount >= 2 && pinchBase > 0f) {
                    val span = pinchSpan(event)
                    // 指の開きの比を、そのまま倍率の比にする。
                    // 段で追うと、一定以上ひらいた瞬間に絵が跳ぶ。
                    val ratio = span / pinchBase
                    if (Math.abs(ratio - 1f) > 0.004f) {
                        // 2本指の中点を軸にする
                        val mx = toLogicalX((event.getX(0) + event.getX(1)) / 2f)
                        val my = toLogicalY((event.getY(0) + event.getY(1)) / 2f)
                        if (zoomAround(zoom * ratio, mx, my)) {
                            pinched = true
                            dragged = true
                        }
                        // いまの開きを次の基準にする
                        pinchBase = span
                    }
                    // つまんでいるあいだも、中点の移動ぶんだけ地図を送る
                    lastTouchX = event.x
                    lastTouchY = event.y
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
                    pendingMonument == null && !strokeCancelled && !onOverlayBadge(lx, ly)
                ) {
                    // なぞって連続で選ぶ
                    selectAt(lx, ly)
                }
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }

            MotionEvent.ACTION_UP -> {
                pointerDown = false
                if (!dragged && !panning && !pinched) handleTap(lx, ly)
                draggingToolbar = false
                draggingCategories = false
                panning = false
                pinchBase = 0f
                pinched = false
                strokeCancelled = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * 置いたものに合う音。
     *
     * 区分は用途で高さを変えてあるので、見ていなくても
     * 住宅・商業・工業のどれを敷いているか分かる。
     */
    private fun sfxForTool(tool: TileKind): Sfx = when {
        tool == TileKind.ROAD -> Sfx.ROAD
        tool == TileKind.ZONE_R -> Sfx.ZONE_R
        tool == TileKind.ZONE_C -> Sfx.ZONE_C
        tool == TileKind.ZONE_I -> Sfx.ZONE_I
        else -> Sfx.BUILD
    }

    /**
     * 「じっこう」の帯の下端。
     *
     * チュートリアルの説明が出ているときは、その上に置く。
     * 重ねると、説明に隠れて釦が押せなくなる。
     */
    private fun selectionBarBottom(): Int {
        var bottom = toolbarTopY
        if (tutorial.active && tutorial.step != null) bottom -= bannerHeight()
        return bottom
    }

    private fun isOnMap(ly: Int): Boolean {
        if (ly < statusBottom) return false
        var bottom = toolbarTopY
        // 帯が出ているあいだは、その上だけが地図。
        // でないと、釦を押したときに下のマスまで選んでしまう。
        if (selection.isNotEmpty()) bottom = selectionBarBottom() - SELECTION_BAR_H
        return ly < bottom
    }

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
                if (isCloseTapped(lx, ly)) {
                    screen = Screen.PLAYING
                    audio.play(Sfx.CLOSE)
                }
                return
            }
            Screen.MONUMENTS -> {
                if (isCloseTapped(lx, ly)) {
                    screen = Screen.PLAYING
                    audio.play(Sfx.CLOSE)
                    return
                }
                // 一覧から選ぶ。行の位置は描画と同じ式で求める。
                val idx = (ly - monumentListTop()) / MONUMENT_ROW_H
                val m = Monument.entries.getOrNull(idx)
                if (m != null) selectMonument(m)
                return
            }
            Screen.MESSAGE -> {
                if (isCloseTapped(lx, ly)) {
                    screen = Screen.PLAYING
                    audio.play(Sfx.CLOSE)
                    message = null
                }
                return
            }
            Screen.INFO -> {
                handleInfoTap(lx, ly)
                return
            }
            Screen.STYLE_EDIT -> {
                handleStyleEditTap(lx, ly)
                return
            }
            Screen.PLAYING -> {}
        }

        // 「じっこう」「やめる」。マスを選んでいるあいだだけ出る。
        // チュートリアルの帯より先に見る。でないと、
        // 説明の「つぎへ」に取られて押せない。
        if (selection.isNotEmpty() &&
            ly >= selectionButtonY && ly < selectionButtonY + SELECTION_BAR_H
        ) {
            if (lx in runButtonX.first..runButtonX.second) { runSelection(); return }
            if (lx in cancelButtonX.first..cancelButtonX.second) {
                clearSelection()
                audio.play(Sfx.CLOSE)
                return
            }
        }

        // チュートリアルの「つぎへ」
        if (tutorial.active && tutorial.awaitingContinue()) {
            val bannerTop = toolbarTopY - bannerHeight()
            if (ly >= bannerTop && ly < toolbarTopY) {
                tutorial.onContinuePressed()
                showTutorialMessageIfNeeded()
                if (tutorial.finished) onTutorialFinished?.invoke()
                onStateChanged?.invoke()
                return
            }
        }

        val toolbarTop = toolbarTopY

        // 速度
        // 重ね表示の名札。押すと消せる。
        if (info.overlay != CityRenderer.Overlay.NONE &&
            ly >= overlayBadgeY && ly < overlayBadgeY + 26 &&
            lx >= overlayBadge.first && lx <= overlayBadge.second
        ) {
            info.overlay = CityRenderer.Overlay.NONE
            audio.play(Sfx.CLOSE)
            invalidate()
            return
        }

        val statusRow = (insetTop + 17)..(insetTop + 44)
        if (ly in statusRow && lx in (SPEED_X - 4)..(SPEED_X + 36)) { cycleSpeed(); return }
        // 拡大率
        if (ly in statusRow && lx in ZOOM_X..(ZOOM_X + 64)) { cycleZoom(); return }

        // 情報・予算・けんちく
        if (ly >= bottomRowTop && ly < bottomRowTop + BOTTOM_ROW_H) {
            if (lx in infoButtonX.first..infoButtonX.second) {
                if (!tutorial.allowsInfo()) { showToast("いまは ステップの とおりに"); return }
                screen = Screen.INFO
                audio.play(Sfx.OPEN)
                tutorial.onInfoOpened()
                showTutorialMessageIfNeeded()
                return
            }
            if (lx in budgetButtonX.first..budgetButtonX.second) {
                if (!tutorial.allowsBudget()) { showToast("いまは ステップの とおりに"); return }
                screen = Screen.BUDGET
                audio.play(Sfx.OPEN)
                tutorial.onBudgetOpened()
                showTutorialMessageIfNeeded()
                return
            }
            if (lx >= monumentButtonX.first) {
                if (tutorial.active) { showToast("チュートリアルの あとで"); return }
                screen = Screen.MONUMENTS
                audio.play(Sfx.OPEN)
                return
            }
        }

        // 分類の選択
        if (ly >= toolbarTop + 4 && ly < toolbarTop + 4 + Hud.CATEGORY_H) {
            val hit = (lx + categoryScroll - Hud.TOOL_GAP) / (Hud.CATEGORY_W + Hud.TOOL_GAP)
            Hud.Category.entries.getOrNull(hit)?.let {
                category = it
                toolScroll = 0
                // 分類を変えたら、その先頭の道具を選ぶ
                Hud.toolsIn(it).firstOrNull()?.let { t ->
                    if (tutorial.allowsBuild(t.kind) || t.kind == TileKind.EMPTY) {
                        selectedTool = t.kind
                    }
                }
            }
            return
        }

        // ツールの選択
        val toolTop = toolbarTop + 4 + Hud.CATEGORY_H + 4
        if (ly >= toolTop && ly < toolTop + Hud.TOOL_SIZE) {
            val hit = (lx + toolScroll - Hud.TOOL_GAP) / (Hud.TOOL_SIZE + Hud.TOOL_GAP)
            val tool = Hud.toolsIn(category).getOrNull(hit)
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

    /** 情報画面のタップ。 */
    private fun handleInfoTap(lx: Int, ly: Int) {
        // とじる
        val cy = info.closeButtonY(logicalH)
        if (ly >= cy && ly < cy + 28) {
            screen = Screen.PLAYING
            audio.play(Sfx.CLOSE)
            onStateChanged?.invoke()
            return
        }
        // 見出し
        info.tabAt(lx, ly)?.let { info.tab = it; invalidate(); return }
        // データマップ
        info.overlayAt(lx, ly)?.let {
            info.overlay = it
            invalidate()
            return
        }
        // 推移グラフの項目
        info.seriesAt(lx, ly)?.let { info.series = it; invalidate(); return }
        // 街並みの様式
        info.styleAt(lx, ly, logicalH)?.let { st ->
            city.style = st
            onStateChanged?.invoke()
            invalidate()
            return
        }
        // 災害の多さ
        info.disasterAt(lx, ly)?.let { lv ->
            city.disasterLevel = lv
            onStateChanged?.invoke()
            invalidate()
            return
        }
        // 音のオン・オフ
        info.soundToggleAt(lx, ly)?.let { which ->
            if (which == 0) {
                audio.sfxEnabled = !audio.sfxEnabled
                info.sfxOn = audio.sfxEnabled
                Settings.setSfxEnabled(context, audio.sfxEnabled)
                // 切り替えた手ごたえを、その場で返す
                if (audio.sfxEnabled) audio.play(Sfx.TAP)
            } else {
                audio.bgmEnabled = !audio.bgmEnabled
                info.bgmOn = audio.bgmEnabled
                Settings.setBgmEnabled(context, audio.bgmEnabled)
                if (audio.bgmEnabled) updateBgm()
            }
            onStateChanged?.invoke()
            invalidate()
            return
        }
        // 自分の様式の色を決める
        if (info.customEditTapped(lx, ly)) {
            city.style = City.Style.CUSTOM
            info.custom = city.customStyle
            screen = Screen.STYLE_EDIT
            audio.play(Sfx.OPEN)
            onStateChanged?.invoke()
            invalidate()
            return
        }
        // 条例
        info.ordinanceAt(lx, ly, logicalH)?.let { o ->
            if (o in city.ordinances) {
                city.ordinances.remove(o)
            } else {
                city.ordinances.add(o)
                tutorial.onOrdinanceEnabled()
                if (tutorial.finished) onTutorialFinished?.invoke()
            }
            onStateChanged?.invoke()
            invalidate()
            return
        }
    }

    private fun handleStyleEditTap(lx: Int, ly: Int) {
        // もどる
        val by = styleEditor.backButtonY(logicalH)
        if (ly >= by && ly < by + 28) {
            screen = Screen.INFO
            audio.play(Sfx.CLOSE)
            onStateChanged?.invoke()
            invalidate()
            return
        }
        // もとに もどす
        if (styleEditor.resetTapped(lx, ly, logicalH)) {
            city.customStyle.reset()
            onStateChanged?.invoke()
            invalidate()
            return
        }
        // 編集する場所を変える
        styleEditor.slotAt(lx, ly)?.let { sl ->
            styleEditor.slot = sl
            invalidate()
            return
        }
        // 色を割り当てる
        styleEditor.colourAt(lx, ly, logicalH)?.let { c ->
            city.customStyle[styleEditor.slot] = c
            onStateChanged?.invoke()
            invalidate()
            return
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
        audio.play(Sfx.TAP)
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
            audio.play(Sfx.MONUMENT)
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
        val mapTop = statusBottom
        val mapHeight = toolbarTopY - statusBottom
        // 描く側（CityRenderer）と同じ式でなければ、押した場所と
        // 実際に選ばれるマスがずれる。
        val originX = LOGICAL_W / 2 -
            Math.round(Iso.screenX2(camX, camY) * zoomNum / zoomDen)
        val originY = mapTop + mapHeight / 2 -
            Math.round(Iso.screenY2(camX, camY) * zoomNum / zoomDen)
        // 菱形の中心を基準に戻す
        val sx = (lx - originX).toFloat() * zoomDen / zoomNum - Iso.TILE_W / 2f
        val sy = (ly - originY).toFloat() * zoomDen / zoomNum - Iso.TILE_H / 2f
        val (tx, ty) = Iso.tileAt(sx, sy)
        if (!city.inBounds(tx, ty)) return null
        return tx to ty
    }

    /**
     * 画面の点が指すタイル座標。[mapCoords] と違い、
     * 盤の外でも切り捨てずに実数で返す。拡大の軸を求めるのに使う。
     */
    private fun mapCoordsFree(lx: Int, ly: Int): Pair<Float, Float>? {
        val mapTop = statusBottom
        val mapHeight = toolbarTopY - statusBottom
        // 描く側（CityRenderer）と同じ式でなければ、押した場所と
        // 実際に選ばれるマスがずれる。
        val originX = LOGICAL_W / 2 -
            Math.round(Iso.screenX2(camX, camY) * zoomNum / zoomDen)
        val originY = mapTop + mapHeight / 2 -
            Math.round(Iso.screenY2(camX, camY) * zoomNum / zoomDen)
        val sx = (lx - originX).toFloat() * zoomDen / zoomNum - Iso.TILE_W / 2f
        val sy = (ly - originY).toFloat() * zoomDen / zoomNum - Iso.TILE_H / 2f
        // Iso.tileAt と同じ式。切り捨てずに実数のまま返す。
        val a = sx / (Iso.TILE_W / 2f)
        val b = sy / (Iso.TILE_H / 2f)
        return ((a + b) / 2f) to ((b - a) / 2f)
    }

    /** 画面の点が指すタイルの番号。マップ外なら null。 */
    private fun tileIndexAt(lx: Int, ly: Int): Int? {
        val (tx, ty) = mapCoords(lx, ly) ?: return null
        return ty * city.width + tx
    }

    /**
     * そのマスを、選んでいるものに足す（またはそこから外す）。
     *
     * ここでは建てない。溜めるだけ。
     * 置けない場所は選ばせない。選べてしまうと、
     * 「じっこう」を押したときに何も起きず、理由も分からなくなる。
     */
    private fun selectAt(lx: Int, ly: Int) {
        if (city.gameOver) return
        val (tx, ty) = mapCoords(lx, ly) ?: return
        val index = ty * city.width + tx

        if (!strokeAdding) {
            if (selection.remove(index)) invalidate()
            return
        }
        if (index in selection) return

        // 選べない場所は、その場で理由を出す
        val blocker = selectionBlocker(tx, ty)
        if (blocker != null) {
            // なぞっている最中に何度も言わない
            if (!dragged) { showToast(blocker); audio.play(Sfx.DENIED) }
            return
        }
        selection.add(index)
        audio.play(Sfx.TAP)
        invalidate()
    }

    /**
     * そのマスを選べない理由。選べるなら null。
     *
     * 実行したときに断られる条件は、選ぶ時点で弾いておく。
     */
    private fun selectionBlocker(tx: Int, ty: Int): String? {
        if (selectedTool == TileKind.EMPTY) {
            if (tutorial.active) return "いまは こわせません"
            return null
        }
        if (!tutorial.allowsBuild(selectedTool)) return "いまは ちがう どうぐです"
        // 求められた数を超えて選ばせない。余分な設置は資金と土地の無駄になり、
        // 「あと N」の意味も分からなくなる。
        if (tutorial.active && tutorial.step?.highlightTool != null &&
            tutorial.remaining() <= selection.size
        ) {
            return "つぎの ステップへ すすみます"
        }
        // すでに同じものが建っているところは選ばない（払い損になる）
        if (city.tileAt(tx, ty).kind == selectedTool) return null
        city.buildBlocker(tx, ty, selectedTool)?.let { return it }
        // チュートリアル中は、道路に接していない区分・発電所を断る
        if (tutorial.active && !city.touchesRoad(tx, ty) &&
            (selectedTool.isZone || selectedTool.isPowerPlant)
        ) {
            return "どうろの となりに おいてください"
        }
        return null
    }

    /** 選んだマスを取り消す。 */
    private fun clearSelection() {
        if (selection.isEmpty()) return
        selection.clear()
        invalidate()
    }

    /**
     * 選んだマスを、まとめて実行する。
     *
     * 資金が足りなくなったら、そこで止める。
     * 一部だけ建って残りが建たないのは分かりにくいので、
     * 何マスできたかを伝える。
     */
    private fun runSelection() {
        if (city.gameOver || selection.isEmpty()) return

        // 先に足りるか見る。足りなければ、建てられるところまでで止まる。
        val cost = selectionCost
        if (city.funds < cost) {
            val each = if (selectedTool == TileKind.EMPTY) BuildCost.BULLDOZE
            else BuildCost.cost(selectedTool)
            val affordable = if (each <= 0) selection.size else city.funds / each
            if (affordable <= 0) {
                showToast("しきんが たりません")
                audio.play(Sfx.DENIED)
                return
            }
            showToast("しきんが たりないので ${affordable}マスだけ")
        }

        var done = 0
        for (index in selection.toList()) {
            val tx = index % city.width
            val ty = index / city.width
            val ok = if (selectedTool == TileKind.EMPTY) {
                city.bulldoze(tx, ty)
            } else {
                if (city.tileAt(tx, ty).kind == selectedTool) false
                else city.build(tx, ty, selectedTool)
            }
            if (ok) {
                done++
                if (selectedTool != TileKind.EMPTY) {
                    tutorial.onBuilt(selectedTool)
                }
            }
        }

        if (done > 0) {
            audio.play(if (selectedTool == TileKind.EMPTY) Sfx.BULLDOZE else sfxForTool(selectedTool))
            showTutorialMessageIfNeeded()
            if (tutorial.finished) onTutorialFinished?.invoke()
            onStateChanged?.invoke()
        } else {
            audio.play(Sfx.DENIED)
        }
        selection.clear()
        invalidate()
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
            val maxScroll = max(0, Hud.toolStripWidth(category) - LOGICAL_W)
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

    fun pause() {
        speedIndex = 0
        audio.pause()
    }

    /** 画面に戻ってきた。曲を鳴らし直す。 */
    fun resume() {
        audio.resume()
        updateBgm()
    }

    /**
     * 画面に付いた。ここで曲を始める。
     *
     * [MainActivity] は画面を差し替えて組み立てるので、
     * onResume より後に view ができることがある。
     * その場合 onResume の resume() は届かないため、
     * 付いた時点でも鳴らし直す。
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        audio.resume()
        updateBgm()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        audio.pause()
    }

    /** 画面から離れる。音を止めて、機械の資源を返す。 */
    fun releaseAudio() {
        audio.release()
    }
}
