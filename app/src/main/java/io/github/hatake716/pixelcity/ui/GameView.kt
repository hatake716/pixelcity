package io.github.hatake716.pixelcity.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import androidx.core.graphics.withTranslation
import io.github.hatake716.pixelcity.audio.Audio
import io.github.hatake716.pixelcity.audio.Bgm
import io.github.hatake716.pixelcity.audio.Sfx
import io.github.hatake716.pixelcity.data.Settings
import android.view.View
import android.widget.FrameLayout
import io.github.hatake716.pixelcity.game.Planet
import io.github.hatake716.pixelcity.game.BuildCost
import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Campaign
import io.github.hatake716.pixelcity.game.Monument
import io.github.hatake716.pixelcity.game.TileKind
import io.github.hatake716.pixelcity.game.Tutorial
import io.github.hatake716.pixelcity.game.TutorialDistrict
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * ゲーム画面。3D由来の都市と文字を端末の解像度で直接描画する。
 *
 * 入力・描画・毎月の進行をここでまとめて扱う。
 */
@SuppressLint("ViewConstructor")
class GameView(
    context: Context,
    var city: City,
    var tutorial: Tutorial,
    val planet: Planet? = null,
) : FrameLayout(context) {

    companion object {
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
        const val LOGICAL_W_MAX = 2400

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
            val lw = (w / scale).coerceIn(1, LOGICAL_W_MAX)
            val lh = (h / scale).coerceIn(1, LOGICAL_H_MAX)
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
        private const val MAX_STEPS_PER_FRAME = 1

        /**
         * 拡大率の段（分子, 分母）。広いマップを見渡せるよう、引いた画を厚くしてある。
         * 1/4 = 街の全体像、1/2 = 区画の配置、1/1 = 標準、2/1 = 建物の細部。
         */
        /**
         * 拡大率の分母。描画は整数で行うので、倍率はこの分母の分数で表す。
         * 256分率でピンチの連続倍率を表す。
         */
        private const val ZOOM_DEN = 256

        /**
         * 引ける限界と、寄れる限界。
         *
         * タイルが 512px になったので、全体を見るには 1/64 まで引ける必要がある。
         * 寄る側は 1/2（1タイル 256px）で十分に細部が見え、
         * 等倍だと画面に1枚しか入らず、街として読めなくなる。
         */
        private const val ZOOM_MIN = 1f / 64f
        private const val ZOOM_MAX = 1f / 2f

        /** 釦で切り替えるときの段。つまむ操作は段に縛られない。 */
        private val ZOOM_PRESETS =
            floatArrayOf(1f / 64f, 1f / 32f, 1f / 16f, 1f / 8f, 1f / 4f, 1f / 2f)

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
        private const val SELECTION_BAR_H = 76

        /** ツールバーの一番下の段（情報・予算・けんちく）の高さ。 */
        private const val BOTTOM_ROW_H = 64

        /**
         * 車を描き始める拡大率。
         * これより引くと1台が1ドットに満たず、描いても見えないうえ重い。
         */
        private const val CARS_MIN_ZOOM = 1f / 24f

        /** 車を配り直す間隔（ミリ秒）。短すぎると瞬間移動して見える。 */
        private const val REFILL_MILLIS = 2_500L

        /** 人口の節目。越えるたびに短い音が鳴る。 */
        private val MILESTONES = intArrayOf(1_000, 5_000, 10_000, 25_000, 50_000, 100_000)

        private const val SPEED_X = 14
        private const val ZOOM_X = 166
        private const val INFO_X = 316
        private const val BUDGET_X = 370
        private const val MONUMENT_X = 420
        private const val BANNER_H = 104
        private const val PANEL_MARGIN = 16
        private const val TAX_MINUS_X = 286
        private const val TAX_PLUS_X = 382
        /** 本文の文字の大きさ。折り返しの計算と描画で必ず同じ値を使う。 */
        private const val BODY_SIZE = 21

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
        private const val MONUMENT_ROW_H = 72
        private val CLOSE_X: Int get() = LOGICAL_W - 164
    }

    /** 画面の状態。 */
    enum class Screen { PLAYING, BUDGET, MONUMENTS, MESSAGE, GAME_OVER, INFO, STYLE_EDIT, CAMPAIGN }

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
    val landscape: Boolean get() = width > height
    var constructionMenuVisible = false
        private set
    var hiyoriMessagesVisible = true
        private set
    private val statusBottom: Int get() = insetTop + if(landscape) 80 else Hud.STATUS_HEIGHT
    private val mapTop: Int get() = statusBottom
    private val buildPanelWidth: Int get() = minOf(348,LOGICAL_W/3)
    private val mapWidth: Int get() = LOGICAL_W - if(landscape && constructionMenuVisible) buildPanelWidth else 0

    /**
     * ツールバーの上端。操作の欄のぶんだけ上げる。
     * 地図はここまで。
     */
    private val toolbarTopY: Int get() = logicalH - insetBottom - if(constructionMenuVisible && !landscape) Hud.TOOLBAR_HEIGHT else 0
    private var pixels = PixelCanvas(LOGICAL_W, LOGICAL_H)

    /** 道を走る車。見えている範囲ぶんだけ持つ。 */
    private val traffic = TrafficAnimation()
    /** 車を配り直した時刻。頻繁にやり直すと、車が瞬間移動して見える。 */
    private var lastRefill = 0L
    private val info = InfoPanel(GbText(context))

    /** 音。鳴らせないときも遊びは止めないよう、失敗は握りつぶす作り。 */
    val audio = Audio()
    private val styleEditor = StyleEditor(GbText(context))
    private val text = GbText(context)

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
    var zoom: Float = 1f / 8f
        private set(value) {
            field = value.coerceIn(ZOOM_MIN, ZOOM_MAX)
        }

    /** 段で切り替える釦のために、近い段を覚えておく。 */
    private val zoomStepIndex: Int
        get() = ZOOM_PRESETS.indices.minByOrNull { Math.abs(ZOOM_PRESETS[it] - zoom) } ?: 0

    /** 釦で拡大率を切り替える。決まった段を順に回る。 */
    fun cycleZoom() {
        if(globe!=null) {
            val levels=floatArrayOf(1f,6f,16f,32f,64f,96f)
            globe.setZoom(levels.firstOrNull { it>planet!!.zoom*1.15f } ?: 1f);invalidate();return
        }
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
        if (next == zoom) return false
        // 変える前に、その点が指しているタイルを覚えておく
        val before = mapCoordsFree(lx, ly)
        zoom = next
        val after = mapCoordsFree(lx, ly)
        run {
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
        setConstructionMenuVisible(true)
        selectedTool = kind
        invalidate()
    }

    fun setConstructionMenuVisible(visible: Boolean) {
        if(constructionMenuVisible==visible) return
        constructionMenuVisible=visible
        clearSelection();pendingMonument=null;inspecting=false
        strokeCancelled=true;pointerDown=false;panning=false;pinched=false;globeGesture=false
        draggingToolbar=false;draggingCategories=false
        globe?.endCameraGesture();globe?.stopMotion()
        toolScroll=0
        if(isLaidOut) layoutGlobe()
        requestLayout();invalidate()
        if(tutorial.active) post { if(isAttachedToWindow) focusTutorialSite() }
    }

    /** Hide only the conversation overlay, keeping the current story, camera and selection. */
    fun setHiyoriMessagesVisible(visible: Boolean) {
        if(hiyoriMessagesVisible==visible) return
        hiyoriMessagesVisible=visible
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
    private val hiyoriPortrait by lazy { HiyoriPortrait(context) }
    private val tutorialPanel by lazy { TutorialPanel(text, context, hiyoriPortrait) }
    private val campaignPanel by lazy { CampaignPanel(text, hiyoriPortrait) }
    private var shownTutorialStep = -1
    private var inspecting = false
    private var strokeAdding = true
    private var selectionBeforeGesture: Set<Int> = emptySet()

    /** 選んだマスを全部実行したときの費用。 */
    private val selectionCost: Int
        get() {
            return selection.sumOf { index ->
                val old = city.tiles[index].kind
                when {
                    old == selectedTool -> 0
                    selectedTool == TileKind.EMPTY -> BuildCost.BULLDOZE
                    else -> BuildCost.cost(selectedTool) + if (old != TileKind.EMPTY) BuildCost.BULLDOZE else 0
                }
            }
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
    private var category: Hud.Category = Hud.Category.TRANSPORT

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
    var onPlanetRequested: (() -> Unit)? = null
    private var planetButtonX = 0 to 0
    val globe: PlanetSurface? = planet?.let { PlanetSurface(context,it,true) }
    private var highlightsKey=Int.MIN_VALUE
    private var townRevision=0
    private var globeOverlay=CityRenderer.Overlay.NONE
    private var streetTick=0L
    private val navigating: Boolean get() = !constructionMenuVisible
    private var navigationButton=Rect()
    private var homeButton=Rect()
    private var globeButton=Rect()
    private var coordinateButton=Rect()
    private var hiyoriButton=Rect()
    private var constructionButton=Rect()
    private var inspectionButton=Rect()
    private var speedButton=Rect()
    private var zoomButton=Rect()
    private var categoryButton=Rect()
    private val landscapeTools=mutableListOf<Pair<Rect,Hud.Tool>>()
    private var toolGridTop=0
    private var toolGridBottom=0
    private var panelScroll=0
    private var panelMaxScroll=0
    private var panelBodyTop=0
    private var panelBodyBottom=0
    private var panelKey=""
    private var panelClipped=false
    private var globeGesture=false
    private var surfacePaused=false
    private val terrainEditorDelegate=lazy { GlobeTerrainEditor(context,planet!!,globe!!) { terrainChanged() } }
    private val terrainEditor by terrainEditorDelegate

    private fun notifyStateChanged() {
        townRevision++;globe?.updateTown(info.overlay);onStateChanged?.invoke()
    }
    fun openTerrainEditor() {
        if(planet==null) { onPlanetRequested?.invoke();return }
        setConstructionMenuVisible(false)
        pauseClock();resume();clearSelection();terrainEditor.show();invalidate()
    }
    private fun terrainChanged() {
        city.recomputeDerivedState();city.refreshBudgetForecast();notifyStateChanged();invalidate()
        if(!terrainEditor.isEditing && isAttachedToWindow && !surfacePaused) resume()
    }
    fun pauseRenderer() { if(!surfacePaused) { globe?.onPause();surfacePaused=true } }
    fun resumeRenderer() { if(surfacePaused) { globe?.onResume();surfacePaused=false } }

    init {
        setWillNotDraw(true)
        globe?.let { surface ->
            addView(surface,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT))
            surface.onCameraChanged={ surface.updateDetail();onStateChanged?.invoke();invalidate() }
        }
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
        showCampaignIfNeeded()
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
        if (speed > 0 && screen == Screen.PLAYING && !city.gameOver && tutorial.allowsSpeedChange()) {
            monthAccumulator += delta * speed
            // 早送りでも、1フレームで進めすぎない。
            // まとめて何十か月も計算すると、その間 画面が固まる。
            var steps = 0
            while (monthAccumulator >= MONTH_MILLIS && steps < MAX_STEPS_PER_FRAME && tutorial.allowsSpeedChange() && screen == Screen.PLAYING) {
                monthAccumulator -= MONTH_MILLIS
                advanceMonth()
                steps++
            }
            if (steps >= MAX_STEPS_PER_FRAME) monthAccumulator = 0
        }

        // 車を進める。止めているときは動かさない。
        if (screen == Screen.PLAYING && speed > 0) {
            // 早送りでは車も速く走る。時間が進んでいるのに
            // 車だけ同じ速さだと、ちぐはぐに見える。
            val carSpeed = (1f + speed * 0.35f).coerceAtMost(4f)
            traffic.advance(city, delta / 1000f * carSpeed)
        }
        when {
            screen == Screen.PLAYING && speed > 0 && tutorial.allowsSpeedChange() -> postInvalidateOnAnimation()
            tutorial.active && screen == Screen.PLAYING -> postInvalidateDelayed(500L)
            toast != null -> postInvalidateDelayed((toastUntil - now + 1).coerceAtLeast(1))
        }
    }

    /** 前の月の災害。同じ災害で何度も鳴らさないために覚えておく。 */
    private var lastDisasterSeen: String? = null
    /** 越えた人口の節目。 */
    private var milestoneSeen = 0

    private fun advanceMonth() {
        val before = city.population
        city.step()
        tutorial.onMonthPassed(city)
        showTutorialMessageIfNeeded()
        showCampaignIfNeeded()

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
        notifyStateChanged()
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
        audio.setBgm(Bgm.forCity(city.population, city.funds, city.monthsInDebt, city.campaign.route))
    }

    fun setSpeed(index: Int) {
        if (!tutorial.allowsSpeedChange()) {
            showToast("現在の目標に沿って進めましょう")
            return
        }
        speedIndex = index.coerceIn(0, SPEEDS.size - 1)
        lastFrameTime = 0L
        invalidate()
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
        val availableW=(w-SystemBars.rawLeft-SystemBars.rawRight).coerceAtLeast(1)
        val (s2, lw, lh) = layoutFor(availableW, h)
        scale = s2
        LOGICAL_W = lw
        logicalH = lh
        if (pixels.width != LOGICAL_W || pixels.height != logicalH) {
            pixels = PixelCanvas(LOGICAL_W, logicalH)
        }
        val drawW = LOGICAL_W * scale
        val drawH = logicalH * scale
        offsetX = SystemBars.rawLeft + (availableW - drawW) / 2
        offsetY = (h - drawH) / 2
        // 画面の大きさが決まってから、見える範囲にあわせて収め直す。
        if (!cameraInitialised) {
            centerCamera()
            cameraInitialised = true
        } else {
            clampCamera()
        }
    }

    private val realistic = RealisticCityRenderer(context)

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if(globe==null || screen!=Screen.PLAYING) canvas.drawColor(Palette.BEZEL)
        canvas.save()
        canvas.translate(offsetX.toFloat(), offsetY.toFloat())
        canvas.scale(scale.toFloat(), scale.toFloat())
        canvas.clipRect(0, 0, LOGICAL_W, logicalH)
        val mapTop = this.mapTop
        val mapHeight = toolbarTopY - mapTop
        if (screen == Screen.PLAYING && globe==null) realistic.draw(canvas, city, camX, camY, zoom, mapWidth,
            mapTop, mapHeight, visibleCars(mapTop,mapHeight).orEmpty(), info.overlay, selection,
            highlightForSelection(), placementHint(), blinkOn(), android.os.SystemClock.uptimeMillis()/1000f,
            streetTime = android.os.SystemClock.uptimeMillis()/1000.0)
        pixels.nativeCanvas = canvas
        try { renderFrame() } finally { pixels.nativeCanvas = null }
        if(globe!=null && screen==Screen.PLAYING) {
            pixels.nativeCanvas=canvas
            try { drawGlobeControls() } finally { pixels.nativeCanvas=null }
            syncGlobeScene()
        }
        if (screen == Screen.CAMPAIGN) campaignPanel.drawPortrait(canvas)
        else if (screen == Screen.PLAYING && tutorial.active && hiyoriMessagesVisible) tutorialPanel.drawPortrait(canvas)
        canvas.restore()
        tick()
    }
    override fun onLayout(changed: Boolean,l: Int,t: Int,r: Int,b: Int) {
        // Insets can change without the outer size changing (rotation/cutout changes).
        onSizeChanged(r-l,b-t,r-l,b-t)
        super.onLayout(changed,l,t,r,b)
        layoutGlobe()
        if(changed && globe!=null && tutorial.active) focusTutorialSite()
    }
    private fun layoutGlobe() {
        globe?.layout(offsetX,offsetY+mapTop*scale,offsetX+mapWidth*scale,offsetY+toolbarTopY*scale)
    }
    override fun onInterceptTouchEvent(event: MotionEvent)=true
    private fun drawGlobeControls() {
        val hasMessages=tutorial.active || city.campaign.visible
        val labels=listOf("市政",if(city.destinations.isEmpty()) "街の中心" else "都市移動","全景","座標指定") + if(hasMessages) listOf("ひより") else emptyList()
        val buttons=listOf(navigationButton,homeButton,globeButton,coordinateButton) + if(hasMessages) listOf(hiyoriButton) else emptyList()
        if(!hasMessages) hiyoriButton.setEmpty()
        val w=(mapWidth-6*(labels.size+1))/labels.size;val y=statusBottom+8
        for(i in labels.indices) {
            val x=6+i*(w+6);buttons[i].set(x,y,x+w,y+64)
            val hiyori=buttons[i]===hiyoriButton
            val on=hiyori && hiyoriMessagesVisible
            pixels.fillRect(x,y,w,64,if(on) Palette.UI_ACCENT else C_BG)
            text.textSize=19;text.drawCentered(pixels,labels[i],x+w/2,y+22,if(on) C_BG else C_TEXT)
            // Leave an unread marker without forcing a hidden conversation back open.
            if(hiyori && !on && (if(tutorial.active) !tutorial.briefingRead else city.campaign.needsBriefing))
                pixels.fillRect(x+w-12,y+6,6,6,Palette.UI_ACCENT)
        }
    }
    private fun syncGlobeScene() {
        val surface=globe ?: return
        val key=selection.hashCode()*31+tutorial.stepIndex*997+townRevision*131
        if(key!=highlightsKey) {
            val hints=if(tutorial.active && tutorial.briefingRead) TutorialDistrict.suggestions(city,tutorial).toIntArray() else IntArray(0)
            surface.setHighlights(selection.toIntArray(),hints);highlightsKey=key
        }
        if(globeOverlay!=info.overlay) { globeOverlay=info.overlay;surface.updateTown(info.overlay) }
        val now=android.os.SystemClock.uptimeMillis()
        if(now-streetTick<80 || speedIndex==0) return
        streetTick=now
        val c=PlanetGeometry.tileIndex(surface.camera.focus);val x=c%512;val y=c/512
        if(planet!!.zoom<12f) { surface.updateMovers(emptyList());return }
        if(now-lastRefill>REFILL_MILLIS) {
            traffic.refill(city,(x-16).coerceAtLeast(0),(y-16).coerceAtLeast(0),(x+16).coerceAtMost(511),(y+16).coerceAtMost(511));lastRefill=now
        }
        val movers=ArrayList<GlobeMover>()
        // Keep a share of the visible street-life budget for pedestrians, even in busy cities.
        for(car in traffic.all.sortedBy { (it.tx-x)*(it.tx-x)+(it.ty-y)*(it.ty-y) }.take(60)) {
            val p=car.progress-.5f
            val d=when(car.dir) { Vehicles.Dir.EAST->0;Vehicles.Dir.SOUTH->1;Vehicles.Dir.WEST->2;Vehicles.Dir.NORTH->3 }
            val dx=when(d) { 0->p;2->-p;1->-.16f;else->.16f }
            val dy=when(d) { 1->p;3->-p;0->.16f;else->-.16f }
            movers.add(GlobeMover("CAR_${car.kind.ordinal}_$d",car.tx+dx,car.ty+dy))
        }
        val walkers=ArrayList<Pedestrians.Pose>()
        for(yy in (y-10).coerceAtLeast(0)..(y+10).coerceAtMost(511)) for(xx in (x-10).coerceAtLeast(0)..(x+10).coerceAtMost(511)) {
            Pedestrians.onTile(city,xx,yy,now/1000.0,walkers)
        }
        for(p in walkers.sortedBy { (it.x-x)*(it.x-x)+(it.y-y)*(it.y-y) }.take(40)) movers.add(GlobeMover(p.spriteId,p.x,p.y))
        surface.updateMovers(movers)
    }

    private fun renderFrame() {
        // The council notebook covers the entire screen; avoid redrawing the hidden city.
        if(screen == Screen.CAMPAIGN) {
            campaignPanel.draw(pixels,city,insetTop,insetBottom)
            return
        }
        drawStatusBar()
        if(constructionMenuVisible) {
            if(landscape) drawLandscapeToolbar() else drawToolbar(toolbarTopY)
        }
        // 選んでいるあいだだけ、ツールバーの上に帯を重ねる。
        // 地図の大きさは変えない。選ぶたびに地図が動くと、狙いが定まらない。
        if (selection.isNotEmpty() && screen == Screen.PLAYING && constructionMenuVisible && !landscape) {
            drawSelectionBar(selectionBarBottom())
        } else if(!landscape || !constructionMenuVisible) {
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
            Screen.CAMPAIGN -> campaignPanel.draw(pixels, city, insetTop, insetBottom)
            Screen.PLAYING -> {
                if (info.overlay != CityRenderer.Overlay.NONE) drawOverlayBadge()
                if(hiyoriMessagesVisible) {
                    if (tutorial.active) drawTutorialBanner()
                    else if(city.campaign.visible) campaignPanel.drawBanner(pixels,city,toolbarTopY,mapWidth)
                }
                toast?.let { drawToast(it) }
            }
        }
    }

    /**
     * チュートリアル中、いま置ける場所を返す。
     * 「道路のとなり」と言われても、どこが該当するのかは初心者には分かりにくい。
     */
    private fun placementHint(): ((Int, Int) -> Boolean)? {
        if (!tutorial.active || !tutorial.briefingRead) return null
        if (tutorial.step?.goal is Tutorial.Goal.InspectHome) {
            return { x, y -> city.tileAt(x, y).let { it.kind == TileKind.ZONE_R && it.stage > 0 } }
        }
        val suggested = TutorialDistrict.suggestions(city, tutorial).toSet()
        if (suggested.isEmpty()) return null
        return { x, y -> city.index(x, y) in suggested }
    }

    private fun highlightForSelection(): IntArray? {
        val m = pendingMonument ?: return null
        return intArrayOf(camX.toInt() + 8, camY.toInt() + 6, 2)
    }

    private fun drawStatusBar() {
        pixels.fillRect(0,0,LOGICAL_W,statusBottom,C_BG)
        val buttonWidth=if(landscape) minOf(144,(LOGICAL_W-286)/4-6) else (LOGICAL_W-36)/4
        val controlsLeft=if(landscape) LOGICAL_W-12-(buttonWidth+6)*4 else 12
        val controlsTop=insetTop+if(landscape) 8 else 73
        text.textSize=23
        text.draw(pixels,"人口 ${"%,d".format(city.population)}",14,insetTop+8,C_TEXT)
        val funds="$${"%,d".format(city.funds)}"
        if(landscape) {
            text.textSize=21
            text.draw(pixels,funds,14,insetTop+39,Palette.UI_ACCENT)
            if(controlsLeft>510) { text.textSize=17;text.draw(pixels,"${1900+city.month/12}年 ${city.month%12+1}月  支持率 ${city.approval}%",235,insetTop+40,C_DIM) }
        } else {
            text.draw(pixels,funds,LOGICAL_W-14-text.measure(funds),insetTop+8,Palette.UI_ACCENT)
        }
        text.textSize=18
        if(!landscape) text.draw(pixels,"${1900+city.month/12}年 ${city.month%12+1}月  /  支持率 ${city.approval}%",14,insetTop+39,C_DIM)
        val labels=listOf("時間 ${if(speedIndex==0) "停止" else "×${SPEEDS[speedIndex]}"}",if(planet!=null) "拡大 ${planet.zoom.toInt()}倍" else "表示 ${Math.round(zoom*800)}%","調査","建設")
        for((i,b) in listOf(speedButton,zoomButton,inspectionButton,constructionButton).withIndex()) {
            val x=controlsLeft+i*(buttonWidth+6);b.set(x,controlsTop,x+buttonWidth,controlsTop+64)
            val on=(i==2 && inspecting) || (i==3 && constructionMenuVisible)
            pixels.fillRect(b.left,b.top,b.width(),b.height(),if(on) Palette.UI_ACCENT else Palette.UI_BG_LIGHT)
            pixels.drawRect(b.left,b.top,b.width(),b.height(),if(i==3 && tutorial.active && tutorial.neededTool!=null && !constructionMenuVisible && blinkOn()) C_TEXT else C_LINE)
            text.textSize=if(i<2) 18 else 21;text.drawCentered(pixels,labels[i],b.centerX(),b.top+20,if(on) C_BG else C_TEXT)
        }
        pixels.fillRect(0,statusBottom-1,LOGICAL_W,1,C_LINE)
    }

    private fun chooseCategory(value: Hud.Category) {
        category=value;toolScroll=0
        Hud.toolsIn(value).firstOrNull()?.let { if(tutorial.allowsBuild(it.kind) || it.kind==TileKind.EMPTY) selectedTool=it.kind }
        invalidate()
    }
    private fun drawLandscapeToolbar() {
        val left=mapWidth;val top=mapTop;val bottom=logicalH-insetBottom;val w=LOGICAL_W-left
        pixels.fillRect(left,top,w,bottom-top,C_BG);pixels.fillRect(left,top,2,bottom-top,C_LINE)
        categoryButton.set(left+8,top+6,LOGICAL_W-8,top+70)
        pixels.fillRect(categoryButton.left,categoryButton.top,categoryButton.width(),64,Palette.UI_BG_LIGHT)
        text.textSize=21;text.drawCentered(pixels,"分類：${category.label} ▾",categoryButton.centerX(),top+27,C_TEXT)
        toolGridTop=top+80;toolGridBottom=bottom-if(selection.isEmpty()) 54 else 110
        val tools=Hud.toolsIn(category);val cellW=(w-24)/3;val cellH=100
        val contentH=((tools.size+2)/3)*cellH
        toolScroll=toolScroll.coerceIn(0,max(0,contentH-(toolGridBottom-toolGridTop)))
        landscapeTools.clear()
        val c=pixels.nativeCanvas;c?.save();c?.clipRect(left,toolGridTop,LOGICAL_W,toolGridBottom)
        for((i,tool) in tools.withIndex()) {
            val x=left+8+(i%3)*(cellW+4);val y=toolGridTop+(i/3)*cellH-toolScroll
            val b=Rect(x,y,x+cellW,y+94);landscapeTools.add(b to tool)
            if(b.bottom<=toolGridTop || b.top>=toolGridBottom) continue
            pixels.fillRect(x,y,cellW,94,if(tool.kind==selectedTool) Palette.UI_BG_LIGHT else C_BG)
            pixels.drawRect(x,y,cellW,94,if(tool.kind==selectedTool) Palette.UI_ACCENT else C_LINE)
            c?.let {
                val bounds=android.graphics.RectF(x+8f,y+4f,x+cellW-8f,y+66f)
                when {
                    tool.kind==TileKind.EMPTY -> { text.textSize=34;text.drawCentered(pixels,"×",b.centerX(),y+12,Palette.RED) }
                    tool.kind.isTransport -> realistic.drawTransportIcon(it,tool.kind,bounds)
                    else -> realistic.drawIcon(it,if(tool.kind.isZone) "${tool.kind.name}_2_0" else tool.kind.name,bounds)
                }
            }
            text.textSize=17;text.drawCentered(pixels,tool.label,b.centerX(),y+71,C_TEXT)
            if(!BuildCost.isUnlocked(tool.kind,city.population)) pixels.ditherRect(x,y,cellW,94,C_BG)
        }
        c?.restore()
        if(contentH>toolGridBottom-toolGridTop) {
            val track=(toolGridBottom-toolGridTop).coerceAtLeast(1);val thumb=(track*track/contentH).coerceAtLeast(24)
            pixels.fillRect(LOGICAL_W-4,toolGridTop+(track-thumb)*toolScroll/(contentH-track).coerceAtLeast(1),3,thumb,Palette.UI_ACCENT)
        }
        pixels.fillRect(left,toolGridBottom,w,bottom-toolGridBottom,C_BG)
        text.textSize=18
        val price=if(selectedTool==TileKind.EMPTY) BuildCost.BULLDOZE else BuildCost.cost(selectedTool)
        text.draw(pixels,if(selection.isEmpty()) "${Hud.labelOf(selectedTool)}  $$price" else "${selection.size}マス  $$selectionCost",left+12,toolGridBottom+12,C_TEXT)
        if(selection.isNotEmpty()) {
            selectionButtonY=bottom-72
            val bw=(w-28)/2
            runButtonX=left+8 to left+8+bw;cancelButtonX=left+16+bw to LOGICAL_W-8
            for((pair,label) in listOf(runButtonX to "実行",cancelButtonX to "取消")) {
                val accent=label=="実行" && city.funds>=selectionCost
                pixels.fillRect(pair.first,selectionButtonY,pair.second-pair.first,64,if(accent) Palette.UI_ACCENT else Palette.UI_BG_LIGHT)
                text.textSize=21;text.drawCentered(pixels,label,(pair.first+pair.second)/2,selectionButtonY+20,if(accent) C_BG else C_TEXT)
            }
        } else { runButtonX=0 to 0;cancelButtonX=0 to 0 }
    }

    private fun drawToolbar(top: Int) {
        // 操作の欄の裏まで地の色を敷く
        pixels.fillRect(0, top, LOGICAL_W, Hud.TOOLBAR_HEIGHT + insetBottom, C_BG)
        pixels.fillRect(0, top, LOGICAL_W, 2, C_LINE)

        // --- 分類の帯 ---
        text.textSize = 21
        for ((i, cat) in Hud.Category.entries.withIndex()) {
            val x = Hud.categoryX(i) - categoryScroll
            if (x + Hud.CATEGORY_W < 0 || x > LOGICAL_W) continue
            val y = top + 5
            val on = cat == category
            pixels.fillRect(x, y, Hud.CATEGORY_W, Hud.CATEGORY_H, if (on) Palette.UI_ACCENT else C_BG)
            pixels.drawRect(x, y, Hud.CATEGORY_W, Hud.CATEGORY_H, C_LINE)
            // 文字は枠の上下の真ん中に置く
            text.drawCentered(
                pixels, cat.label, x + Hud.CATEGORY_W / 2, y + (Hud.CATEGORY_H - 21) / 2,
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

            pixels.nativeCanvas?.let { c ->
                val id=if(tool.kind.isZone) "${tool.kind.name}_2_0" else tool.kind.name
                if(tool.kind==TileKind.EMPTY) {
                    text.textSize=34;text.drawCentered(pixels,"×",x+Hud.TOOL_SIZE/2,toolTop+8,Palette.RED)
                } else if(tool.kind.isTransport) {
                    realistic.drawTransportIcon(c,tool.kind,android.graphics.RectF((x+6).toFloat(),(toolTop+6).toFloat(),(x+Hud.TOOL_SIZE-6).toFloat(),(toolTop+Hud.TOOL_SIZE-30).toFloat()))
                } else realistic.drawIcon(c,id,android.graphics.RectF((x+8).toFloat(),(toolTop+4).toFloat(),(x+Hud.TOOL_SIZE-8).toFloat(),(toolTop+Hud.TOOL_SIZE-26).toFloat()))
                text.textSize=17;text.drawCentered(pixels,tool.label,x+Hud.TOOL_SIZE/2,toolTop+Hud.TOOL_SIZE-24,C_TEXT)
            }

            // 解禁されていないものは暗くする
            if (!BuildCost.isUnlocked(tool.kind, city.population)) {
                pixels.ditherRect(x, toolTop, Hud.TOOL_SIZE, Hud.TOOL_SIZE, C_BG)
            }

            if (tutorial.active && tutorial.neededTool == tool.kind && blinkOn()) {
                pixels.drawRect(x - 3, toolTop - 3, Hud.TOOL_SIZE + 6, Hud.TOOL_SIZE + 6, C_TEXT)
            }
        }
        // --- 一番下の段: 道具の名前と、情報・予算・けんちくの入口 ---
        val rowH = BOTTOM_ROW_H
        val by = top + Hud.TOOLBAR_HEIGHT - rowH - 4
        bottomRowTop = by
        val labelY = by - 34

        val tool = Hud.TOOLS.firstOrNull { it.kind == selectedTool }
        if (tool != null) {
            val price =
                if (tool.kind == TileKind.EMPTY) BuildCost.BULLDOZE
                else BuildCost.cost(tool.kind)
            text.textSize = 18
            val locked = !BuildCost.isUnlocked(tool.kind, city.population)
            val label =
                if (locked) "${tool.label}（未解禁）"
                else "${tool.label} $${price}"
            text.draw(pixels, label, 6, labelY, if (locked) C_DIM else C_TEXT)
        }

        text.textSize=18
        for((i,entry) in listOf("R" to city.demandR,"C" to city.demandC,"I" to city.demandI).withIndex()) {
            text.draw(pixels,"${entry.first} ${entry.second}",LOGICAL_W-238+i*78,labelY,if(entry.second>=0) Palette.UI_ACCENT else Palette.GOLD)
        }

        // 右から詰める。文字の幅を測るので、重ならない。
        text.textSize = 22
        var bx = LOGICAL_W - 6
        for ((label, id) in listOf("記念建築" to 2, "予算" to 1, "都市情報" to 0, (if(globe!=null) "地形編集" else "惑星") to 3)) {
            val w = (LOGICAL_W - 36) / 4
            bx -= w + 6
            pixels.fillRect(bx, by, w, rowH, C_BG)
            pixels.drawRect(bx, by, w, rowH, C_LINE)
            text.draw(pixels, label, bx + 8, by + (rowH - 22) / 2, C_TEXT)
            when (id) {
                3 -> planetButtonX = bx to (bx + w)
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
            pixels.drawRect(SPEED_X - 2, insetTop + 71, 148, 68, C_TEXT)
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
        text.textSize = 21
        val what = if (selectedTool == TileKind.EMPTY) "撤去" else "建設"
        val head = "$what ${selection.size}マス"
        text.draw(pixels, head, 6, y + 6, C_TEXT)
        text.textSize = 21
        text.draw(
            pixels, "$${cost}", 6 + text.measure(head) + 10, y + 8,
            if (enough) C_DIM else Palette.RED,
        )

        // 右から「やめる」「じっこう」の順に置く
        val by = y + 4
        val bh = h - 8
        var bx = LOGICAL_W - 6
        run {
            val label = "取消"
            val w = text.measure(label) + 32
            bx -= w
            pixels.fillRect(bx, by, w, bh, C_BG)
            pixels.drawRect(bx, by, w, bh, C_LINE)
            text.draw(pixels, label, bx + 8, by + 18, C_TEXT)
            cancelButtonX = bx to (bx + w)
        }
        run {
            val label = "実行"
            val w = text.measure(label) + 32
            bx -= w + 8
            // 押せるときは目立たせる
            pixels.fillRect(bx, by, w, bh, if (enough) Palette.UI_ACCENT else C_BG)
            pixels.drawRect(bx, by, w, bh, C_LINE)
            text.draw(
                pixels, label, bx + 10, by + 18,
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
        val y = mapTop + 80
        pixels.fillRect(x, y, w, 26, Palette.UI_BG)
        pixels.drawRect(x, y, w, 26, Palette.UI_ACCENT)
        text.draw(pixels, label, x + 10, y + 5, Palette.UI_ACCENT)
        overlayBadge = x to (x + w)
        overlayBadgeY = y
    }

    /**
     * いま見えている範囲の車。
     *
     * 画面の外まで車を抱えると重いので、見える範囲だけを持つ。
     * 配り直しはときどきでよい。毎フレームやると、
     * 車が毎回別の場所に現れて、走っているように見えない。
     */
    private fun visibleCars(mapTop: Int, mapHeight: Int): List<TrafficAnimation.Car> {
        // 拡大率が低いと、1台が1ドットにも満たない。描いても見えない。
        if (zoom < CARS_MIN_ZOOM) return emptyList()

        val now = System.currentTimeMillis()
        if (now - lastRefill > REFILL_MILLIS) {
            lastRefill = now
            // 画面に映るタイルの範囲。余裕をもって広めにとる。
            val span = (LOGICAL_W / (Iso.TILE_W * zoom)).toInt() + 4
            val vspan = (mapHeight / (Iso.TILE_H * zoom)).toInt() + 4
            val r = maxOf(span, vspan)
            traffic.refill(
                city,
                (camX.toInt() - r), (camY.toInt() - r),
                (camX.toInt() + r), (camY.toInt() + r),
            )
        }
        return traffic.all
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
    private fun bannerHeight(): Int = if(!hiyoriMessagesVisible) 0
        else if(tutorial.active) tutorialPanel.height(city, tutorial, mapWidth,landscape)
        else if(city.campaign.visible) CampaignPanel.BANNER_HEIGHT else 0

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

    private fun drawTutorialBanner() = tutorialPanel.draw(pixels, city, tutorial, toolbarTopY,mapWidth,landscape)

    private fun drawToast(msg: String) {
        text.textSize=20
        val lines=text.wrap(msg,mapWidth-48)
        val w=(lines.maxOfOrNull { text.measure(it) } ?: 0)+24
        val h=lines.size*28+18;val x=(mapWidth-w)/2
        val y=selectionBarBottom()-(if(selection.isEmpty()) 10 else SELECTION_BAR_H+10)-h
        pixels.fillRect(x,y,w,h,C_BG);pixels.drawRect(x,y,w,h,C_LINE)
        for((i,line) in lines.withIndex()) text.draw(pixels,line,x+12,y+7+i*28,C_TEXT)
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
        if(panelKey!="$screen:$title") { panelKey="$screen:$title";panelScroll=0 }
        panelHeight = minOf(62 + contentHeight + 84,logicalH-insetTop-insetBottom-16)
        val top = panelTop()
        pixels.clear(C_BG)
        pixels.fillRect(m, top, LOGICAL_W - m * 2, panelHeight, C_BG)
        pixels.drawRect(m, top, LOGICAL_W - m * 2, panelHeight, C_LINE)
        pixels.drawRect(m + 1, top + 1, LOGICAL_W - m * 2 - 2, panelHeight - 2, C_LINE)
        text.textSize = 24
        text.drawCentered(pixels, title, LOGICAL_W / 2, top + 14, C_TEXT)
        pixels.fillRect(m + 8, top + 50, LOGICAL_W - m * 2 - 16, 2, C_LINE)
        panelBodyTop=top+62;panelBodyBottom=top+panelHeight-84
        panelMaxScroll=(contentHeight-(panelBodyBottom-panelBodyTop)).coerceAtLeast(0)
        panelScroll=panelScroll.coerceIn(0,panelMaxScroll)
        pixels.nativeCanvas?.let { it.save();it.clipRect(m,panelBodyTop,LOGICAL_W-m,panelBodyBottom);it.translate(0f,-panelScroll.toFloat());panelClipped=true }
        return top + 62
    }

    private var taxButtonY = 0

    private fun drawBudget() {
        val rows = 6 + if (city.tourismIncome > 0) 1 else 0
        var y = drawPanel("予算・現在の街での見込み", 80 + rows * 34 + 74)
        text.textSize = 22
        text.draw(pixels, "税率 ${city.taxRate}%", 30, y, C_TEXT)
        taxButtonY = y - 2
        // 税率の増減ボタン
        pixels.drawRect(TAX_MINUS_X, y - 2, 80, 64, C_LINE)
        text.drawCentered(pixels, "-", TAX_MINUS_X + 40, y + 16, C_TEXT)
        pixels.drawRect(TAX_PLUS_X, y - 2, 80, 64, C_LINE)
        text.drawCentered(pixels, "+", TAX_PLUS_X + 40, y + 16, C_TEXT)
        y += 80

        text.textSize = 21
        text.draw(pixels, "収入  $${city.lastIncome}", 30, y, C_TEXT); y += 34
        text.draw(pixels, "支出    $${city.lastUpkeep}", 30, y, C_TEXT); y += 34
        val balance = city.lastIncome - city.lastUpkeep
        text.draw(pixels, "収支    $${balance}", 30, y, C_TEXT); y += 38
        val powerLabel = if (city.powerSupply < city.powerDemand) {
            "電力  ${city.powerSupply}/${city.powerDemand} 不足"
        } else {
            "電力  ${city.powerSupply}/${city.powerDemand}"
        }
        text.draw(pixels, powerLabel, 30, y, C_TEXT); y += 34
        text.draw(pixels, "雇用     ${city.jobs}", 30, y, C_TEXT); y += 34
        if (city.tourismIncome > 0) {
            text.draw(pixels, "観光    $${city.tourismIncome}", 30, y, C_TEXT); y += 34
        }

        text.textSize = 20
        text.draw(pixels, "税率が高いと", 30, y + 4, C_DIM)
        text.draw(pixels, "住民が転出します", 30, y + 32, C_DIM)
        drawCloseButton()
    }

    /** モニュメント一覧の1行目の y。描画と判定で共有する。 */
    private fun monumentListTop(): Int = panelTop()+62

    private fun drawMonuments() {
        var y = drawPanel("世界の記念建築", Monument.entries.size * MONUMENT_ROW_H + 28)
        text.textSize = 21
        val all = Monument.entries
        for (m in all) {
            val built = m in city.builtMonuments
            val unlocked = city.population >= m.unlockPopulation
            val shade = if (built || unlocked) C_TEXT else C_DIM
            pixels.fillRect(24,y,LOGICAL_W-48,MONUMENT_ROW_H-4,Palette.UI_BG_LIGHT)
            pixels.nativeCanvas?.let { realistic.drawIcon(it,m.name,android.graphics.RectF(30f,y.toFloat(),90f,(y+64).toFloat())) }
            text.textSize=22;text.draw(pixels,m.label,100,y+6,shade)
            val detail=when { built -> "建設済み";unlocked -> "$${"%,d".format(m.cost)}";else -> "人口 ${"%,d".format(m.unlockPopulation)} 人で解禁" }
            text.textSize=18;text.draw(pixels,detail,100,y+37,C_DIM)
            y += MONUMENT_ROW_H
        }
        text.draw(pixels, "建築を選択し、地図上の敷地をタップ", 30, y + 4, C_TEXT)
        drawCloseButton()
    }

    private fun drawMessage() {
        text.textSize = 21
        val lines = text.wrap(message ?: "", LOGICAL_W - PANEL_MARGIN * 2 - 28)
        var y = drawPanel(messageTitle, lines.size * 32)
        text.textSize = 21
        for (line in lines) {
            text.draw(pixels, line, 30, y, C_TEXT)
            y += 32
        }
        drawCloseButton()
    }

    private fun drawGameOver() {
        pixels.clear(C_BG)
        // 画面の高さは端末で変わるので、中央から組み立てる。
        val mid = logicalH / 2
        text.textSize = 20
        text.drawCentered(pixels, "財政破綻", LOGICAL_W / 2, mid - 80, C_TEXT)
        text.textSize = 15
        text.drawCentered(pixels, "資金が尽きました", LOGICAL_W / 2, mid - 40, C_TEXT)
        text.drawCentered(pixels, "人口 ${city.population}", LOGICAL_W / 2, mid - 16, C_TEXT)
        text.textSize = 16
        pixels.drawRect(LOGICAL_W / 2 - 70, restartButtonY(), 140, 28, C_LINE)
        text.drawCentered(pixels, "再開", LOGICAL_W / 2, restartButtonY() + 4, C_TEXT)
    }

    /** もういちどボタンの y。描画と判定で共有する。 */
    private fun restartButtonY(): Int = logicalH / 2 + 30

    /** とじるボタンの y。パネルの下端に合わせ、描画と判定で共有する。 */
    private fun closeButtonY(): Int = panelTop() + panelHeight - 76

    private fun drawCloseButton() {
        if(panelClipped) { pixels.nativeCanvas?.restore();panelClipped=false }
        val x = CLOSE_X
        val y = closeButtonY()
        pixels.fillRect(x, y, 144, 64, Palette.UI_BG_LIGHT)
        pixels.drawRect(x, y, 144, 64, C_LINE)
        text.textSize = 15
        text.textSize=22
        text.drawCentered(pixels, "閉じる", x + 72, y + 18, C_TEXT)
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

        if(globe!=null && screen==Screen.PLAYING) {
            if(event.actionMasked==MotionEvent.ACTION_DOWN) {
                globeGesture=isOnMap(lx,ly) && !navigationButton.contains(lx,ly) && !homeButton.contains(lx,ly) &&
                    !globeButton.contains(lx,ly) && !coordinateButton.contains(lx,ly) && !hiyoriButton.contains(lx,ly)
            }
            if(globeGesture && (navigating || terrainEditor.isEditing)) {
                val local=MotionEvent.obtain(event);local.offsetLocation(-globe.left.toFloat(),-globe.top.toFloat())
                globe.onTileSelected={ index ->
                    if(terrainEditor.isEditing) {
                        planet!!.select(index%512,index/512);globe.select(index,false);terrainEditor.updateSelection();onStateChanged?.invoke()
                    } else if(inspecting) inspectPlot(index)
                    invalidate()
                }
                globe.onTouchEvent(local);local.recycle()
                if(event.actionMasked==MotionEvent.ACTION_UP || event.actionMasked==MotionEvent.ACTION_CANCEL) globeGesture=false
                return true
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                dragged = false
                pointerDown = true
                panning = false
                strokeCancelled = false
                pinched = false
                selectionBeforeGesture = selection.toSet()
                // パネルを開いている間は、下のツールバーに触れさせない。
                val tbTop = toolbarTopY
                draggingCategories = constructionMenuVisible && !landscape && screen == Screen.PLAYING &&
                    ly >= tbTop + 4 && ly < tbTop + 4 + Hud.CATEGORY_H
                draggingToolbar = constructionMenuVisible && screen == Screen.PLAYING &&
                    (if(landscape) lx>=mapWidth && ly in toolGridTop until toolGridBottom else ly>=tbTop && !draggingCategories)
                // マップ上なら、押した時点から選び始める（なぞってまとめて選べる）
                if (screen == Screen.PLAYING && constructionMenuVisible && isOnMap(lx,ly) && !inspecting && pendingMonument == null &&
                    !onOverlayBadge(lx, ly) && (globe==null || globeGesture)
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
                if (screen != Screen.PLAYING || (globe!=null && !globeGesture)) return true
                selection.clear()
                selection.addAll(selectionBeforeGesture)
                strokeCancelled = true
                draggingToolbar = false
                draggingCategories = false
                panning = true
                lastTouchX = (event.getX(0)+event.getX(1))/2f
                lastTouchY = (event.getY(0)+event.getY(1))/2f
                globe?.let { it.beginCameraGesture(lastTouchX-it.left,lastTouchY-it.top) }
                pinchBase = pinchSpan(event)
                pinched = false
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val remaining=(0 until event.pointerCount).filter { it != event.actionIndex }
                if (remaining.size >= 2) {
                    val a=remaining[0];val b=remaining[1]
                    lastTouchX=(event.getX(a)+event.getX(b))/2f
                    lastTouchY=(event.getY(a)+event.getY(b))/2f
                    globe?.let { it.beginCameraGesture(lastTouchX-it.left,lastTouchY-it.top) }
                    pinchBase=kotlin.math.hypot(event.getX(a)-event.getX(b),event.getY(a)-event.getY(b))
                } else {
                    globe?.endCameraGesture();panning=false;pinchBase=0f
                    lastTouchX=event.getX(remaining[0]);lastTouchY=event.getY(remaining[0])
                }
                pinched=true;strokeCancelled=true
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                if (abs(dx) > scale * 2 || abs(dy) > scale * 2) dragged = true

                if (screen == Screen.STYLE_EDIT) {
                    styleEditor.scrollBy((-dy/scale).toInt())
                    lastTouchX=event.x;lastTouchY=event.y;invalidate();return true
                }
                if (screen == Screen.INFO) {
                    info.scrollBy((-dy/scale).toInt())
                    lastTouchX=event.x;lastTouchY=event.y
                    invalidate();return true
                }
                if(screen in setOf(Screen.BUDGET,Screen.MONUMENTS,Screen.MESSAGE)) {
                    panelScroll=(panelScroll-(dy/scale).toInt()).coerceIn(0,panelMaxScroll)
                    lastTouchX=event.x;lastTouchY=event.y;invalidate();return true
                }
                if(globe==null && navigating && screen==Screen.PLAYING && isOnMap(lx,ly) && event.pointerCount==1) {
                    val before=mapCoordsFree((lastTouchX-offsetX)/scale,(lastTouchY-offsetY)/scale)
                    val after=mapCoordsFree((event.x-offsetX)/scale,(event.y-offsetY)/scale)
                    camX+=before.first-after.first;camY+=before.second-after.second;clampCamera()
                    lastTouchX=event.x;lastTouchY=event.y;invalidate();return true
                }
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
                    toolScroll = if(landscape) (toolScroll-(dy/scale).toInt()).coerceAtLeast(0) else (toolScroll - (dx / scale).toInt())
                        .coerceIn(0, max(0, Hud.toolStripWidth(category) - LOGICAL_W))
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                    return true
                }

                if (panning && event.pointerCount >= 2 && pinchBase > 0f) {
                    val mx=(event.getX(0)+event.getX(1))/2f
                    val my=(event.getY(0)+event.getY(1))/2f
                    val span=pinchSpan(event)
                    if(globe!=null) {
                        globe.moveCamera(mx-lastTouchX,my-lastTouchY,if(pinchBase>0) span/pinchBase else 1f,mx-globe.left,my-globe.top)
                        pinchBase=span;lastTouchX=mx;lastTouchY=my;pinched=true;dragged=true
                        invalidate();return true
                    }
                    if (span>0f) {
                        val before=mapCoordsFree((lastTouchX-offsetX)/scale,(lastTouchY-offsetY)/scale)
                        zoom=(zoom*span/pinchBase).coerceIn(ZOOM_MIN,ZOOM_MAX)
                        val after=mapCoordsFree((mx-offsetX)/scale,(my-offsetY)/scale)
                        camX+=before.first-after.first;camY+=before.second-after.second
                        clampCamera()
                        pinchBase=span
                    }
                    lastTouchX=mx;lastTouchY=my;pinched=true;dragged=true
                    postInvalidateOnAnimation()
                    return true
                }

                if (screen == Screen.PLAYING && constructionMenuVisible && isOnMap(lx,ly) &&
                    !inspecting && pendingMonument == null && !strokeCancelled && !onOverlayBadge(lx, ly) && (globe==null || globeGesture)
                ) {
                    // なぞって連続で選ぶ
                    selectAt(lx, ly)
                }
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                globe?.endCameraGesture()
                selection.clear()
                selection.addAll(selectionBeforeGesture)
                pointerDown = false
                panning = false
                pinched = false
                draggingToolbar = false
                draggingCategories = false
                strokeCancelled = true
                pinchBase = 0f
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                globe?.endCameraGesture()
                pointerDown = false
                if(globe!=null && (pinched || panning)) { globe.updateDetail();onStateChanged?.invoke() }
                if (!dragged && !panning && !pinched) {
                    // Time spent reading a panel must not be charged to the first frame back in town.
                    if (screen != Screen.PLAYING) lastFrameTime = System.currentTimeMillis()
                    handleTap(lx, ly)
                }
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
        tool == TileKind.ROAD || tool == TileKind.AVENUE -> Sfx.ROAD
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
        bottom -= bannerHeight()
        return bottom
    }

    private fun isOnMap(lx: Int,ly: Int): Boolean {
        if(lx !in 0 until mapWidth || ly < mapTop) return false
        var bottom = selectionBarBottom()
        // 帯が出ているあいだは、その上だけが地図。
        // でないと、釦を押したときに下のマスまで選んでしまう。
        if (selection.isNotEmpty() && !landscape) bottom = selectionBarBottom() - SELECTION_BAR_H
        return ly < bottom
    }

    private fun handleTap(lx: Int, ly: Int) {
        if(globe!=null && screen==Screen.PLAYING) {
            when {
                navigationButton.contains(lx,ly) -> { showCivicMenu();return }
                homeButton.contains(lx,ly) -> { showCityDestinations();return }
                globeButton.contains(lx,ly) -> { globe.setZoom(1f);clearSelection();invalidate();return }
                coordinateButton.contains(lx,ly) -> { pauseClock();resume();terrainEditor.chooseCoordinate();return }
                hiyoriButton.contains(lx,ly) -> { setHiyoriMessagesVisible(!hiyoriMessagesVisible);return }
            }
        }
        when (screen) {
            Screen.GAME_OVER -> {
                if (ly in restartButtonY()..(restartButtonY() + 28)) onRestartRequested?.invoke()
                return
            }
            Screen.BUDGET -> {
                // 税率の増減（drawBudget の配置と同じ値を使う）
                if (ly in panelBodyTop until panelBodyBottom && ly+panelScroll in taxButtonY until taxButtonY + 64) {
                    if (lx in TAX_MINUS_X..(TAX_MINUS_X + 80)) {
                        city.taxRate = (city.taxRate - 1).coerceAtLeast(0); city.refreshBudgetForecast(); notifyStateChanged()
                    }
                    if (lx in TAX_PLUS_X..(TAX_PLUS_X + 80)) {
                        city.taxRate = (city.taxRate + 1).coerceAtMost(20); city.refreshBudgetForecast(); notifyStateChanged()
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
                if (lx < 24 || lx >= LOGICAL_W-24 || ly !in panelBodyTop until panelBodyBottom || ly+panelScroll < monumentListTop()) return
                val idx = (ly+panelScroll - monumentListTop()) / MONUMENT_ROW_H
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
            Screen.CAMPAIGN -> { handleCampaignTap(lx,ly); return }
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

        // Only the rendered reply buttons advance dialogue. Reading text never places a tile.
        if (hiyoriMessagesVisible && tutorial.active && lx<mapWidth && ly in (toolbarTopY - bannerHeight()) until toolbarTopY) {
            handleTutorialAction(tutorialPanel.actionAt(lx,ly))
            notifyStateChanged()
            return
        }

        if (hiyoriMessagesVisible && !tutorial.active && city.campaign.visible && lx<mapWidth && ly in (toolbarTopY-bannerHeight()) until toolbarTopY) {
            if(campaignPanel.actionAt(lx,ly) == CampaignPanel.Action.OPEN) openCampaign()
            return
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

        if(constructionButton.contains(lx,ly)) { setConstructionMenuVisible(!constructionMenuVisible);audio.play(Sfx.TAP);return }
        if(inspectionButton.contains(lx,ly)) {
            inspecting=!inspecting;clearSelection()
            if(inspecting) showToast("建築をタップして調査します")
            return
        }
        if(speedButton.contains(lx,ly)) { cycleSpeed();return }
        if(zoomButton.contains(lx,ly)) { cycleZoom();return }
        if(constructionMenuVisible && landscape) {
            if(categoryButton.contains(lx,ly)) {
                android.app.AlertDialog.Builder(context).setTitle("建設する施設の分類")
                    .setItems(Hud.Category.entries.map { it.label }.toTypedArray()) { _,i -> chooseCategory(Hud.Category.entries[i]) }.show()
                return
            }
            if(lx>=mapWidth && ly in toolGridTop until toolGridBottom) {
                landscapeTools.firstOrNull { it.first.contains(lx,ly) }?.second?.let { tool ->
                    if(tutorial.allowsBuild(tool.kind) || tool.kind==TileKind.EMPTY) { selectedTool=tool.kind;pendingMonument=null }
                    else showToast("現在の目標で指定された道具を選択してください")
                };return
            }
        }

        // 情報・予算・けんちく
        if (constructionMenuVisible && !landscape && ly >= bottomRowTop && ly < bottomRowTop + BOTTOM_ROW_H) {
            if (lx in planetButtonX.first..planetButtonX.second) {
                clearSelection()
                openTerrainEditor()
                return
            }
            if (lx in infoButtonX.first..infoButtonX.second) {
                if (!tutorial.allowsInfo()) { showToast("現在の目標に沿って進めましょう"); return }
                screen = Screen.INFO
                audio.play(Sfx.OPEN)
                tutorial.onInfoOpened(city)
                showTutorialMessageIfNeeded()
                notifyStateChanged()
                return
            }
            if (lx in budgetButtonX.first..budgetButtonX.second) {
                if (!tutorial.allowsBudget()) { showToast("現在の目標に沿って進めましょう"); return }
                city.refreshBudgetForecast()
                screen = Screen.BUDGET
                audio.play(Sfx.OPEN)
                tutorial.onBudgetOpened(city)
                showTutorialMessageIfNeeded()
                notifyStateChanged()
                return
            }
            if (lx >= monumentButtonX.first) {
                if (tutorial.active) { showToast("チュートリアル終了後に利用できます"); return }
                screen = Screen.MONUMENTS
                audio.play(Sfx.OPEN)
                return
            }
        }

        // 分類の選択
        if (constructionMenuVisible && !landscape && ly >= toolbarTop + 4 && ly < toolbarTop + 4 + Hud.CATEGORY_H) {
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
        if (constructionMenuVisible && !landscape && ly >= toolTop && ly < toolTop + Hud.TOOL_SIZE) {
            val hit = (lx + toolScroll - Hud.TOOL_GAP) / (Hud.TOOL_SIZE + Hud.TOOL_GAP)
            val tool = Hud.toolsIn(category).getOrNull(hit)
            if (tool != null) {
                if (!tutorial.allowsBuild(tool.kind) && tool.kind != TileKind.EMPTY) {
                    showToast("現在の目標で指定された道具を選択してください")
                    return
                }
                selectedTool = tool.kind
                pendingMonument = null
            }
            return
        }

        // マップ（モニュメントの設置）
        if (isOnMap(lx,ly)) {
            if (inspecting) {
                val index = if(globe!=null) tileIndexAt(lx,ly) else realistic.buildingAtScreen(city, camX, camY, zoom,
                    LOGICAL_W, statusBottom, toolbarTopY - statusBottom, lx, ly) ?: tileIndexAt(lx, ly)
                index?.let { inspectPlot(it) }
                return
            }
            val m = pendingMonument
            if (m != null && constructionMenuVisible) {
                placeMonument(m, lx, ly)
            }
        }
    }

    private fun handleTutorialAction(action: TutorialPanel.Action?) {
            when (action) {
                TutorialPanel.Action.REPLY -> {
                    val before = tutorial.stepIndex
                    tutorial.acknowledge(city)
                    showTutorialMessageIfNeeded()
                    if(tutorial.active && tutorial.briefingRead) focusTutorialSite()
                    if (tutorial.allowsSpeedChange() && tutorial.active && tutorial.stepIndex == before) setSpeed(2)
                    if (tutorial.finished) {
                        inspecting = false
                        setSpeed(1)
                        audio.play(Sfx.MILESTONE)
                        onTutorialFinished?.invoke()
                        showCampaignIfNeeded()
                    }
                }
                TutorialPanel.Action.PARKS -> { tutorial.choose(Tutorial.Priority.PARKS, city); showTutorialMessageIfNeeded() }
                TutorialPanel.Action.CLINIC -> { tutorial.choose(Tutorial.Priority.CLINIC, city); showTutorialMessageIfNeeded() }
                TutorialPanel.Action.ADVICE -> { tutorial.reopenBriefing(); speedIndex = 0; monthAccumulator = 0; clearSelection() }
                TutorialPanel.Action.FOCUS -> focusTutorialSite()
                TutorialPanel.Action.DIALOG -> showTutorialDialog()
                null -> {}
            }
        notifyStateChanged();requestLayout();invalidate()
    }

    private fun showTutorialDialog() {
        if(!tutorial.active) return
        val text=if(tutorial.briefingRead) tutorial.objective(city)+"\n\n"+tutorial.advice(city) else tutorial.briefing(city)
        val d=android.app.AlertDialog.Builder(context)
            .setTitle("ひより：${tutorial.step!!.title}").setMessage(text)
        if(tutorial.awaitingChoice()) {
            d.setPositiveButton("公園を整備") { _,_ -> handleTutorialAction(TutorialPanel.Action.PARKS) }
                .setNegativeButton("診療所を整備") { _,_ -> handleTutorialAction(TutorialPanel.Action.CLINIC) }
        } else {
            val advance=!tutorial.briefingRead || tutorial.isRunningGoal()
            d.setPositiveButton(if(!tutorial.briefingRead) tutorial.step!!.reply else if(advance) "時間を進める" else "閉じる") { _,_ ->
                if(advance) handleTutorialAction(TutorialPanel.Action.REPLY)
            }
            if(tutorial.briefingRead && (tutorial.neededTool!=null || tutorial.step?.goal is Tutorial.Goal.InspectHome))
                d.setNeutralButton("候補地へ") { _,_ -> focusTutorialSite() }
        }
        d.show()
    }

    private fun inspectPlot(index: Int) {
        city.recomputeDerivedState()
        val tile = city.tiles[index]
        val x = index % city.width
        val y = index / city.width
        messageTitle = tile.monument?.label ?: Hud.labelOf(tile.kind).ifBlank { "空地" }
        fun state(value: Boolean) = if (value) "有" else "無"
        message = buildString {
            append("場所 ($x, $y)  成長 ${tile.stage}/3\n")
            append("道路 ${state(tile.connected)} / 電力 ${state(tile.powered)}\n")
            append("水道 ${state(tile.watered)}\n")
            append("地価 ${tile.landValue} / 公害 ${tile.pollution}\n")
            append("安全 ${tile.safety} / 教育 ${tile.education}\n")
            append("健康 ${tile.health} / 犯罪 ${tile.crime}\n")
            if (tile.kind.isTransport) append("交通 ${tile.traffic} / ${io.github.hatake716.pixelcity.game.Traffic.capacityPerHour(tile.kind)}\n")
            if (tile.kind == TileKind.LANDFILL) append("埋立量 ${tile.landfillFill} / ${BuildCost.LANDFILL_TOTAL}\n")
            if (tile.kind.isZone) append("\n" + city.growthStatus(x, y))
            else append("\n維持費 $${BuildCost.upkeep(tile.kind)} / 月")
        }
        screen = Screen.MESSAGE
        audio.play(Sfx.OPEN)
        tutorial.onHomeInspected(city, index)
        showTutorialMessageIfNeeded()
        notifyStateChanged()
    }

    /** 情報画面のタップ。 */
    private fun handleInfoTap(lx: Int, ly: Int) {
        // とじる
        val cy = info.closeButtonY(logicalH)
        if (ly >= cy && ly < cy + 64) {
            screen = Screen.PLAYING
            audio.play(Sfx.CLOSE)
            notifyStateChanged()
            return
        }
        // 見出し
        info.tabAt(lx, ly)?.let { info.tab = it; invalidate(); return }
        // データマップ
        info.overlayAt(lx, ly)?.let {
            info.overlay = it
            // 選んだら地図へ戻る。重ねて見るためのものなので、
            // いちいち閉じさせるより、そのまま見せたほうが早い。
            screen = Screen.PLAYING
            audio.play(if (it == CityRenderer.Overlay.NONE) Sfx.CLOSE else Sfx.OPEN)
            notifyStateChanged()
            invalidate()
            return
        }
        // 推移グラフの項目
        info.seriesAt(lx, ly)?.let { info.series = it; invalidate(); return }
        // 街並みの様式
        info.styleAt(lx, ly, logicalH)?.let { st ->
            city.style = st
            notifyStateChanged()
            invalidate()
            return
        }
        // 災害の多さ
        info.disasterAt(lx, ly)?.let { lv ->
            city.disasterLevel = lv
            notifyStateChanged()
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
            notifyStateChanged()
            invalidate()
            return
        }
        // 自分の様式の色を決める
        if (info.customEditTapped(lx, ly)) {
            city.style = City.Style.CUSTOM
            info.custom = city.customStyle
            screen = Screen.STYLE_EDIT
            audio.play(Sfx.OPEN)
            notifyStateChanged()
            invalidate()
            return
        }
        // 条例
        info.ordinanceAt(lx, ly, logicalH)?.let { o ->
            if (o in city.ordinances) {
                city.ordinances.remove(o)
            } else {
                city.ordinances.add(o)
                if (tutorial.finished) onTutorialFinished?.invoke()
            }
            notifyStateChanged()
            invalidate()
            return
        }
    }

    private fun handleStyleEditTap(lx: Int, ly: Int) {
        // もどる
        val by = styleEditor.backButtonY(logicalH)
        if (ly >= by && ly < by + 64) {
            screen = Screen.INFO
            audio.play(Sfx.CLOSE)
            notifyStateChanged()
            invalidate()
            return
        }
        // もとに もどす
        if (styleEditor.resetTapped(lx, ly, logicalH)) {
            city.customStyle.reset()
            notifyStateChanged()
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
            notifyStateChanged()
            invalidate()
            return
        }
    }

    private fun isCloseTapped(lx: Int, ly: Int): Boolean =
        lx >= CLOSE_X && lx <= CLOSE_X + 144 &&
            ly >= closeButtonY() && ly <= closeButtonY() + 64

    private fun selectMonument(m: Monument) {
        val blocker = when {
            m in city.builtMonuments -> "建設済みです"
            city.population < m.unlockPopulation -> "人口 ${m.unlockPopulation} 人で解禁"
            city.funds < m.cost -> "資金不足です"
            else -> null
        }
        if (blocker != null) { showToast(blocker); return }
        setConstructionMenuVisible(true)
        pendingMonument = m
        screen = Screen.PLAYING
        audio.play(Sfx.TAP)
        showToast("建設場所をタップ")
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
            notifyStateChanged()
        }
    }

    /**
     * 論理座標からタイル座標へ。マップ外なら null。
     *
     * 描画と同じ原点を使って、画面座標を菱形の格子へ戻す。
     */
    private fun mapCoords(lx: Int, ly: Int): Pair<Int, Int>? {
        if (!isOnMap(lx,ly)) return null
        if(globe!=null) {
            val index=globe.pick((lx*scale+offsetX-globe.left).toFloat(),(ly*scale+offsetY-globe.top).toFloat()) ?: return null
            return index%512 to index/512
        }
        val p=mapCoordsFree(lx.toFloat(),ly.toFloat())
        val tx=kotlin.math.floor(p.first+.5f).toInt()
        val ty=kotlin.math.floor(p.second+.5f).toInt()
        return if(city.inBounds(tx,ty)) tx to ty else null
    }

    private fun mapCoordsFree(lx: Int, ly: Int): Pair<Float,Float> = mapCoordsFree(lx.toFloat(),ly.toFloat())
    private fun mapCoordsFree(lx: Float, ly: Float): Pair<Float,Float> =
        RealisticCityRenderer.unproject(lx,ly,camX,camY,zoom,LOGICAL_W,statusBottom,toolbarTopY-statusBottom)

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
            if (!tutorial.allowsBuild(TileKind.EMPTY)) return "ひよりの話を聞いてから、手を入れましょう"
            if (city.tileAt(tx, ty).kind == TileKind.EMPTY) return "何もありません"
            return null
        }
        if (!tutorial.allowsBuild(selectedTool)) return "現在の目標で指定された道具を選択してください"
        // 求められた数を超えて選ばせない。余分な設置は資金と土地の無駄になり、
        // 「あと N」の意味も分からなくなる。
        if (tutorial.active && tutorial.placementGoal()?.kind == selectedTool &&
            tutorial.remaining() <= selection.size
        ) {
            return "次の目標へ進みます"
        }
        // すでに同じものが建っているところは選ばない（払い損になる）
        if (city.tileAt(tx, ty).kind == selectedTool) return "設置済みです"
        city.buildBlocker(tx, ty, selectedTool)?.let { return it }
        if (tutorial.active) TutorialDistrict.placementBlocker(city, tx, ty, selectedTool, selection)?.let { return it }

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
                showToast("資金不足です")
                audio.play(Sfx.DENIED)
                return
            }
            showToast("資金不足のため${affordable}区画のみ建設します")
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
            if (ok) done++
        }

        if (done > 0) {
            city.recomputeDerivedState()
            tutorial.onCityChanged(city)
            audio.play(if (selectedTool == TileKind.EMPTY) Sfx.BULLDOZE else sfxForTool(selectedTool))
            showTutorialMessageIfNeeded()
            if (tutorial.finished) onTutorialFinished?.invoke()
            notifyStateChanged()
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
        if (shownTutorialStep != tutorial.stepIndex) {
            val previous = shownTutorialStep
            shownTutorialStep = tutorial.stepIndex
            speedIndex = 0
            monthAccumulator = 0
            inspecting = false
            clearSelection()
            strokeCancelled = true
            if (previous >= 0) audio.play(Sfx.PROGRESS)
            focusTutorialSite()
            if (tutorial.step?.goal is Tutorial.Goal.OpenBudget) city.refreshBudgetForecast()
        }
        val needed = tutorial.neededTool ?: return
        if (selectedTool != needed) {
            selectedTool = needed
            pendingMonument = null
            // 道具が変わったら、いま触れている指での連続設置を打ち切る。
            // 続けると、指を離さないまま次のステップまで置いてしまう。
            strokeCancelled = true
        }
        // 選んだ道具が画面の外なら、見えるところまでツールバーを送る。
        val nextCategory = Hud.Category.entries.firstOrNull { c -> Hud.toolsIn(c).any { it.kind == needed } }
        if (nextCategory != null && category != nextCategory) {
            category = nextCategory
            toolScroll = 0
            categoryScroll = (Hud.categoryX(category.ordinal) - LOGICAL_W / 3).coerceAtLeast(0)
        }
        val index = Hud.toolsIn(category).indexOfFirst { it.kind == needed }
        if (index >= 0) {
            val x = Hud.toolX(index)
            val maxScroll = max(0, Hud.toolStripWidth(category) - LOGICAL_W)
            if (x - toolScroll < 0 || x - toolScroll + Hud.TOOL_SIZE > LOGICAL_W) {
                toolScroll = (x - LOGICAL_W / 2).coerceIn(0, maxScroll)
            }
        }
    }

    private fun showCampaignIfNeeded() {
        if(tutorial.active || city.gameOver) return
        if(tutorial.finished) city.campaign.offer()
        if(hiyoriMessagesVisible && city.campaign.needsBriefing) openCampaign()
    }

    private fun openCampaign() {
        clearSelection();inspecting=false;pendingMonument=null;monthAccumulator=0;lastFrameTime=0
        screen=Screen.CAMPAIGN;campaignPanel.open();invalidate()
    }

    private fun handleCampaignTap(x: Int,y: Int) {
        val action=campaignPanel.actionAt(x,y) ?: return
        val a=city.campaign
        when(action) {
            CampaignPanel.Action.METROPOLIS -> a.select(Campaign.Route.METROPOLIS,city)
            CampaignPanel.Action.GARDEN -> a.select(Campaign.Route.GARDEN,city)
            CampaignPanel.Action.INDUSTRIAL -> a.select(Campaign.Route.INDUSTRIAL,city)
            CampaignPanel.Action.OPTION_0,CampaignPanel.Action.OPTION_1 -> {
                if(landscape) {
                    val index=if(action==CampaignPanel.Action.OPTION_0) 0 else 1
                    val policy=a.current!!.options[index]
                    android.app.AlertDialog.Builder(context).setTitle(policy.label)
                        .setMessage("導入費 $${policy.cost} / 月額 $${policy.upkeep(city.population)}\n\n${policy.detail}")
                        .setPositiveButton("この施策を実施") { _,_ ->
                            if(!a.choose(index,city)) showToast("施策の導入資金が不足しています")
                            updateBgm();notifyStateChanged();invalidate()
                        }.setNegativeButton("戻る",null).show()
                    return
                }
                if(!a.choose(if(action==CampaignPanel.Action.OPTION_0)0 else 1,city)) audio.play(Sfx.DENIED)
                else audio.play(Sfx.PROGRESS)
            }
            CampaignPanel.Action.CLOSE -> {
                a.acknowledge();screen=Screen.PLAYING;monthAccumulator=0;lastFrameTime=0
                if(a.completed) frameCompletedCity()
            }
            else -> campaignPanel.navigate(action)
        }
        updateBgm();notifyStateChanged();invalidate()
    }

    /** Reveal the city the mayor actually built, including towns away from the map's centre. */
    private fun frameCompletedCity() {
        val built=city.tiles.indices.filter { city.tiles[it].kind != TileKind.EMPTY }
        if(built.isEmpty()) return
        val minX=built.minOf { it%city.width };val maxX=built.maxOf { it%city.width }
        val minY=built.minOf { it/city.width };val maxY=built.maxOf { it/city.width }
        val span=(maxX-minX+maxY-minY+4).toFloat()
        if(globe!=null) {
            focusOnTile((minX+maxX)/2,(minY+maxY)/2)
            globe.setZoom((600f/span).coerceIn(1f,48f));return
        }
        val visibleHeight=(toolbarTopY-statusBottom-bannerHeight()-40).coerceAtLeast(80)
        zoom=min((LOGICAL_W-32)/(span*Iso.TILE_W/2f),visibleHeight/((span+4)*Iso.TILE_H/2f))
        val shift=bannerHeight()/(2f*Iso.TILE_H*zoom)
        camX=(minX+maxX)/2f+shift;camY=(minY+maxY)/2f+shift
        clampCamera()
    }

    private fun focusTutorialSite() {
        val candidates = if (tutorial.step?.goal is Tutorial.Goal.InspectHome) {
            city.tiles.indices.filter { city.tiles[it].kind == TileKind.ZONE_R && city.tiles[it].stage > 0 }
        } else TutorialDistrict.suggestions(city, tutorial)
        val x = if (candidates.isEmpty()) city.originX.toFloat() else candidates.map { it % city.width }.average().toFloat()
        val y = if (candidates.isEmpty()) city.originY.toFloat() else candidates.map { it / city.width }.average().toFloat()
        if(globe!=null) {
            focusOnTile(x.toInt(),y.toInt())
            if(globe.width>0 && globe.height>0 && tutorial.briefingRead) {
                // Keep every suggested lot above both the notebook and the commit bar.
                val land=PlanetGeometry.Landscape(PlanetGeometry.Snapshot.of(planet!!))
                val sites=(if(candidates.isEmpty()) listOf(city.index(x.toInt(),y.toInt())) else candidates)
                    .map { land.ground(it%city.width+.5f,it/city.width+.5f) }
                val top=80f*scale
                val bottom=((selectionBarBottom()-(if(landscape) 0 else SELECTION_BAR_H)-mapTop-24)*scale).toFloat().coerceAtLeast(top+80)
                fun bounds(): FloatArray {
                    val cam=globe.camera;val aspect=globe.width.toFloat()/globe.height;val hh=cam.halfHeight(aspect)
                    val px=sites.map { globe.width*(.5f+(it-cam.focus*cam.focusRadius).dot(cam.right)/(2*hh*aspect)) }
                    val py=sites.map { globe.height*(.5f-(it-cam.focus*cam.focusRadius).dot(cam.up)/(2*hh)) }
                    return floatArrayOf(px.min(),py.min(),px.max(),py.max())
                }
                var b=bounds()
                val fit=min((globe.width-64f*scale)/(b[2]-b[0]+48f*scale),(bottom-top)/(b[3]-b[1]+48f*scale)).coerceIn(.2f,1f)
                globe.setZoom(planet.zoom*fit)
                repeat(2) {
                    b=bounds();val cx=(b[0]+b[2])/2;val cy=(b[1]+b[3])/2
                    val targetX=globe.width/2f;val targetY=(top+bottom)/2
                    globe.moveCamera(targetX-cx,targetY-cy,1f,targetX,targetY)
                }
                globe.updateDetail()
            }
            return
        }
        // Raise the point of interest into the visible area above the secretary's notebook.
        val shift = bannerHeight() / (2f * Iso.TILE_H * zoom)
        camX = x + shift; camY = y + shift
        clampCamera()
        invalidate()
    }

    var onTutorialFinished: (() -> Unit)? = null
    var onRestartRequested: (() -> Unit)? = null

    /** カメラを街の中央へ。 */
    fun centerCamera() {
        if(globe!=null) {
            if(tutorial.active) focusTutorialSite()
            return
        }
        camX = city.originX.toFloat()
        camY = city.originY.toFloat()
        if (tutorial.active) focusTutorialSite()
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

    fun focusOnTile(x: Int, y: Int) {
        // An explicit destination must survive the first layout of a newly created city view.
        cameraInitialised=true
        if(globe!=null) {
            planet!!.select(x,y);globe.select(planet.selectedIndex,true,true)
            invalidate();return
        }
        camX=x.toFloat(); camY=y.toFloat()
        clampCamera()
        invalidate()
    }

    fun showCityDestinations() {
        if(city.destinations.isEmpty()) { focusOnTile(city.originX,city.originY);return }
        android.app.AlertDialog.Builder(context).setTitle("都市移動")
            .setItems(city.destinations.map { "${it.name}（${it.region}）" }.toTypedArray()) { _,i ->
                val d=city.destinations[i]
                clearSelection()
                globe?.setZoom(24f);focusOnTile(d.x,d.y)
                notifyStateChanged()
            }.setNegativeButton("閉じる",null).show()
    }

    /** Civic tools stay reachable even with the construction tray closed. */
    fun showCivicMenu() {
        android.app.AlertDialog.Builder(context).setTitle("市政")
            .setItems(arrayOf("都市情報","予算","地形編集","記念建築","市政手帳")) { _,i ->
                when(i) {
                    0 -> {
                        if(!tutorial.allowsInfo()) { showToast("現在の目標に沿って進めましょう");return@setItems }
                        screen=Screen.INFO;tutorial.onInfoOpened(city);showTutorialMessageIfNeeded()
                    }
                    1 -> {
                        if(!tutorial.allowsBudget()) { showToast("現在の目標に沿って進めましょう");return@setItems }
                        city.refreshBudgetForecast();screen=Screen.BUDGET;tutorial.onBudgetOpened(city);showTutorialMessageIfNeeded()
                    }
                    2 -> openTerrainEditor()
                    3 -> {
                        if(tutorial.active) { showToast("チュートリアル終了後に利用できます");return@setItems }
                        screen=Screen.MONUMENTS
                    }
                    4 -> if(tutorial.active) showTutorialDialog() else openCampaign()
                }
                lastFrameTime=0;notifyStateChanged();invalidate()
            }.setNegativeButton("閉じる",null).show()
    }

    private fun pauseClock() {
        speedIndex = 0
        lastFrameTime = 0L
    }

    fun pause() {
        pauseClock()
        audio.pause()
        invalidate()
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
        pauseRenderer()
        if(terrainEditorDelegate.isInitialized()) terrainEditor.dispose()
        super.onDetachedFromWindow()
        realistic.releaseCaches()
        audio.pause()
    }

    /** 画面から離れる。音を止めて、機械の資源を返す。 */
    fun releaseAudio() {
        audio.release()
    }
}
