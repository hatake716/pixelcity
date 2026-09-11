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

    private val pixels = PixelCanvas(GameView.LOGICAL_W, GameView.LOGICAL_H)
    private val text = GbText(context)
    private val frame = Bitmap.createBitmap(
        GameView.LOGICAL_W, GameView.LOGICAL_H, Bitmap.Config.ARGB_8888,
    )
    private val buffer = IntArray(GameView.LOGICAL_W * GameView.LOGICAL_H)
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
        // ビル群（下から26px）に重ならないよう、上から順に積む。
        var y = if (hasSave) 104 else 120
        if (hasSave) {
            add(Button(y, "つづきから") { onContinue?.invoke() })
            y += BUTTON_H + 8
        }
        add(Button(y, "はじめから") { onStartTutorial?.invoke() })
        y += BUTTON_H + 8
        add(Button(y, "チュートリアルを とばす") { onSkipTutorial?.invoke() })
        y += BUTTON_H + 8
        add(Button(y, "ライセンス") { onLicenses?.invoke() })
    }

    private companion object {
        const val BUTTON_H = 28
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        scale = max(1, w / GameView.LOGICAL_W)
        val dw = GameView.LOGICAL_W * scale
        val dh = GameView.LOGICAL_H * scale
        offsetX = (w - dw) / 2
        offsetY = (h - dh) / 2
        dst.set(offsetX, offsetY, offsetX + dw, offsetY + dh)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        pixels.clear(0)

        // 背景に街のシルエット
        drawSkyline()

        text.textSize = 40
        text.drawCentered(pixels, "PIXELCITY", GameView.LOGICAL_W / 2, 34, 3)
        text.textSize = 15
        text.drawCentered(pixels, "としを そだてる しちょうの しごと", GameView.LOGICAL_W / 2, 84, 3)

        text.textSize = 16
        for (b in buttons()) {
            val w = text.measure(b.label) + 28
            val x = (GameView.LOGICAL_W - w) / 2
            pixels.fillRect(x, b.y, w, BUTTON_H, 0)
            pixels.drawRect(x, b.y, w, BUTTON_H, 3)
            text.drawCentered(pixels, b.label, GameView.LOGICAL_W / 2, b.y + 6, 3)
        }

        for (i in pixels.pixels.indices) buffer[i] = GbPalette.of(pixels.pixels[i].toInt())
        frame.setPixels(buffer, 0, GameView.LOGICAL_W, 0, 0, GameView.LOGICAL_W, GameView.LOGICAL_H)
        canvas.drawColor(GbPalette.BEZEL)
        canvas.drawBitmap(frame, null, dst, paint)
    }

    /** タイトルの下に並ぶ、飾りのビル。 */
    private fun drawSkyline() {
        val heights = intArrayOf(18, 30, 14, 36, 22, 40, 16, 32, 24, 38, 20, 28, 21, 34, 15, 26)
        var x = 0
        for ((i, h) in heights.withIndex()) {
            val w = 20
            val top = GameView.LOGICAL_H - 34 - h
            pixels.fillRect(x, top, w - 2, h, if (i % 2 == 0) 2 else 1)
            pixels.drawRect(x, top, w - 2, h, 3)
            // 窓
            for (wy in top + 6 until GameView.LOGICAL_H - 40 step 9) {
                for (wx in x + 4 until x + w - 6 step 7) {
                    pixels.fillRect(wx, wy, 3, 3, 3)
                }
            }
            x += w
        }
        pixels.fillRect(0, GameView.LOGICAL_H - 34, GameView.LOGICAL_W, 34, 0)
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
