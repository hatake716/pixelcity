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
    var onLicenses: (() -> Unit)? = null

    /** ボタンの位置（論理座標）。描画と当たり判定で同じ値を使う。 */
    private data class Button(val y: Int, val label: String, val action: () -> Unit)

    private fun buttons(): List<Button> = buildList {
        // ロゴとビル群のあいだに、中央寄りで積む。
        val bottom = logicalH - skylineHeight() - 40
        val count = if (hasSave) 4 else 3
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
        scale = max(1, w / GameView.LOGICAL_W)
        while (scale > 1 && h / scale < GameView.LOGICAL_H) scale--
        logicalH = (h / scale).coerceIn(GameView.LOGICAL_H, GameView.LOGICAL_H_MAX)
        if (pixels.height != logicalH) {
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
                pixels.set(x, y, 1)
            }
        }
        pixels.fillRect(0, bandTop, GameView.LOGICAL_W, 2, 11)
        pixels.fillRect(0, bandTop + bandH - 2, GameView.LOGICAL_W, 2, 11)
        text.textSize = 58
        text.drawCentered(pixels, "PIXELCITY", GameView.LOGICAL_W / 2 + 3, logoY + 3, 12)
        text.drawCentered(pixels, "PIXELCITY", GameView.LOGICAL_W / 2, logoY, 15)

        text.textSize = 18
        text.drawCentered(
            pixels, "としを そだてる しちょうの しごと",
            GameView.LOGICAL_W / 2, logoY + 74, 12,
        )

        text.textSize = 18
        for (b in buttons()) {
            val w = text.measure(b.label) + 36
            val x = (GameView.LOGICAL_W - w) / 2
            // 街が透けないよう、しっかり塗ってから枠と文字を置く
            pixels.fillRect(x, b.y, w, BUTTON_H, 1)
            pixels.drawRect(x, b.y, w, BUTTON_H, 14)
            pixels.drawRect(x + 1, b.y + 1, w - 2, BUTTON_H - 2, 11)
            // 影で浮かせる
            pixels.fillRect(x + 2, b.y + BUTTON_H, w, 2, 13)
            pixels.fillRect(x + w, b.y + 2, 2, BUTTON_H, 13)
            text.drawCentered(pixels, b.label, GameView.LOGICAL_W / 2, b.y + 6, 14)
        }

        for (i in pixels.pixels.indices) buffer[i] = GbPalette.of(pixels.pixels[i].toInt())
        frame.setPixels(buffer, 0, GameView.LOGICAL_W, 0, 0, GameView.LOGICAL_W, logicalH)
        canvas.drawColor(GbPalette.BEZEL)
        canvas.drawBitmap(frame, null, dst, paint)
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
        cityRenderer.draw(
            pixels, showcase,
            camX = 31f, camY = 31f,
            zoomNum = 1, zoomDen = 1,
            viewTop = 0, viewHeight = logicalH,
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
