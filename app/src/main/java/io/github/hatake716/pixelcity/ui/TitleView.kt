package io.github.hatake716.pixelcity.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * タイトル画面。チュートリアルの開始・スキップ・つづきからを選ぶ。
 */
@SuppressLint("ViewConstructor")
class TitleView(
    context: Context,
    private val hasSave: Boolean,
) : View(context) {

    private var logicalH = GameView.LOGICAL_H
    private var pixels = PixelCanvas(GameView.LOGICAL_W, GameView.LOGICAL_H)
    private val text = GbText(context)
    private var frame = Bitmap.createBitmap(
        GameView.LOGICAL_W, GameView.LOGICAL_H, Bitmap.Config.ARGB_8888,
    )
    private var buffer = IntArray(GameView.LOGICAL_W * GameView.LOGICAL_H)
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val dst = Rect()
    private var scale = 1
    private var offsetX = 0
    private var offsetY = 0

    var onStartTutorial: (() -> Unit)? = null
    var onSkipTutorial: (() -> Unit)? = null
    var onContinue: (() -> Unit)? = null
    var onShowcase: (() -> Unit)? = null
    var onLicenses: (() -> Unit)? = null

    /** ボタンの位置（論理座標）。描画と当たり判定で同じ値を使う。 */
    private data class Button(val y: Int, val label: String, val action: () -> Unit)

    private fun buttons(): List<Button> = buildList {
        // ロゴとビル群のあいだに、中央寄りで積む。
        val bottom = logicalH - skylineHeight() - 40
        val count = if (hasSave) 5 else 4
        var y = bottom - count * (BUTTON_H + 12)
        // 画面が高いときは、上に寄りすぎないよう中ほどへ寄せる
        val minY = logicalH / 2 - 20
        if (y > minY) y = minY
        if (hasSave) {
            add(Button(y, "つづきから") { onContinue?.invoke() })
            y += BUTTON_H + 10
        }
        add(Button(y, "はじめから") { onStartTutorial?.invoke() })
        y += BUTTON_H + 10
        add(Button(y, "チュートリアルを とばす") { onSkipTutorial?.invoke() })
        y += BUTTON_H + 10
        add(Button(y, "おてほんから はじめる") { onShowcase?.invoke() })
        y += BUTTON_H + 10
        add(Button(y, "ライセンス") { onLicenses?.invoke() })
    }

    /** 下に並べるビル群の高さ。 */
    private fun skylineHeight(): Int = 96

    private companion object {
        const val BUTTON_H = 30
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // ゲーム画面と同じ考え方。横幅に合わせ、縦は端末いっぱいに伸ばす。
        // ゲーム画面と同じ計算を使う。ばらばらに書くと、
        // 画面を移ったときに大きさが変わってしまう。
        val (s2, _, lh) = GameView.layoutFor(w, h)
        scale = s2
        logicalH = lh
        if (pixels.width != GameView.LOGICAL_W || pixels.height != logicalH) {
            pixels = PixelCanvas(GameView.LOGICAL_W, logicalH)
            frame = Bitmap.createBitmap(GameView.LOGICAL_W, logicalH, Bitmap.Config.ARGB_8888)
            buffer = IntArray(GameView.LOGICAL_W * logicalH)
        }
        val dw = GameView.LOGICAL_W * scale
        val dh = logicalH * scale
        offsetX = (w - dw) / 2
        offsetY = (h - dh) / 2
        dst.set(offsetX, offsetY, offsetX + dw, offsetY + dh)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        pixels.clear(0)

        drawShowcase()

        // 街の上に文字を置くため、ロゴの帯を敷いて読みやすくする。
        val logoY = logicalH / 8
        val bandTop = logoY - 14
        val bandH = 120
        for (y in bandTop until bandTop + bandH) {
            // 上下の端をぼかすように、市松で薄く重ねる
            val edge = minOf(y - bandTop, bandTop + bandH - 1 - y)
            for (x in 0 until GameView.LOGICAL_W) {
                if (edge < 6 && ((x + y) and 1) == 0) continue
                pixels.set(x, y, Palette.UI_BG)
            }
        }
        pixels.fillRect(0, bandTop, GameView.LOGICAL_W, 2, Palette.UI_LINE)
        pixels.fillRect(0, bandTop + bandH - 2, GameView.LOGICAL_W, 2, Palette.UI_LINE)
        text.textSize = 58
        text.drawCentered(pixels, "PIXELCITY", GameView.LOGICAL_W / 2 + 3, logoY + 3, Palette.BLACK)
        text.drawCentered(pixels, "PIXELCITY", GameView.LOGICAL_W / 2, logoY, Palette.UI_ACCENT)

        text.textSize = 18
        text.drawCentered(
            pixels, "としを そだてる しちょうの しごと",
            GameView.LOGICAL_W / 2, logoY + 74, Palette.UI_TEXT,
        )

        text.textSize = 18
        for (b in buttons()) {
            val w = text.measure(b.label) + 36
            val x = (GameView.LOGICAL_W - w) / 2
            // 街が透けないよう、しっかり塗ってから枠と文字を置く
            pixels.fillRect(x, b.y, w, BUTTON_H, Palette.UI_BG)
            pixels.drawRect(x, b.y, w, BUTTON_H, Palette.UI_LINE)
            pixels.drawRect(x + 1, b.y + 1, w - 2, BUTTON_H - 2, Palette.UI_BG_LIGHT)
            // 影で浮かせる
            pixels.fillRect(x + 2, b.y + BUTTON_H, w, 2, Palette.BLACK)
            pixels.fillRect(x + w, b.y + 2, 2, BUTTON_H, Palette.BLACK)
            text.drawCentered(pixels, b.label, GameView.LOGICAL_W / 2, b.y + 6, Palette.UI_TEXT)
        }

        for (i in pixels.pixels.indices) buffer[i] = Palette.of(pixels.pixels[i].toInt())
        frame.setPixels(buffer, 0, GameView.LOGICAL_W, 0, 0, GameView.LOGICAL_W, logicalH)
        canvas.drawColor(Palette.BEZEL)
        canvas.drawBitmap(frame, null, dst, paint)
    }

    /**
     * 空。上を濃く、下を淡くして奥行きを出し、雲をいくつか浮かべる。
     * 街だけだと画面の上下が寂しくなるため。
     */
    private fun drawSky() {
        for (y in 0 until logicalH) {
            val t = y.toFloat() / logicalH
            val c = if (t < 0.55f) Palette.SKY_DEEP else Palette.SKY
            pixels.fillRect(0, y, GameView.LOGICAL_W, 1, c)
        }
        // 雲。横に長い塊をいくつか。
        fun cloud(cx: Int, cy: Int, w: Int, h: Int) {
            for (y in -h..h) for (x in -w..w) {
                val d = (x * x).toFloat() / (w * w) + (y * y).toFloat() / (h * h)
                if (d > 1f) continue
                val c = if (y < 0 && d < 0.6f) Palette.WHITE else Palette.GLASS_LIT
                pixels.set(cx + x, cy + y, c)
            }
        }
        cloud(90, 70, 46, 11)
        cloud(150, 88, 30, 7)
        cloud(360, 108, 52, 12)
        cloud(300, 126, 26, 6)
        cloud(120, logicalH - 120, 40, 9)
        cloud(390, logicalH - 92, 34, 8)
    }

    /** 背景の大都市。作るのは一度きりで、以後は使い回す。 */
    private val showcase by lazy { ShowcaseCity.build() }
    private val cityRenderer = CityRenderer()

    /**
     * 背景の大都市を描く。
     *
     * ゲーム本編とまったく同じ [CityRenderer] を使うので、
     * 「この街づくりの行き着く先」をそのまま見せることになる。
     */
    private fun drawShowcase() {
        drawSky()
        cityRenderer.draw(
            pixels, showcase,
            camX = 31f, camY = 31f,
            zoomNum = 1, zoomDen = 3,
            viewTop = 0, viewHeight = logicalH,
            clearBackground = false,
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true
        val ly = ((event.y - offsetY) / scale).toInt()
        for (b in buttons()) {
            if (ly >= b.y && ly < b.y + BUTTON_H) { b.action(); return true }
        }
        return true
    }
}
