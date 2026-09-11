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

        drawSkyline()

        // 大きく構えたロゴ。影を先に落としてから本体を重ねる。
        val logoY = logicalH / 8
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
            val w = text.measure(b.label) + 32
            val x = (GameView.LOGICAL_W - w) / 2
            pixels.fillRect(x, b.y, w, BUTTON_H, 1)
            pixels.drawRect(x, b.y, w, BUTTON_H, 14)
            pixels.drawRect(x + 1, b.y + 1, w - 2, BUTTON_H - 2, 11)
            text.drawCentered(pixels, b.label, GameView.LOGICAL_W / 2, b.y + 6, 14)
        }

        for (i in pixels.pixels.indices) buffer[i] = GbPalette.of(pixels.pixels[i].toInt())
        frame.setPixels(buffer, 0, GameView.LOGICAL_W, 0, 0, GameView.LOGICAL_W, logicalH)
        canvas.drawColor(GbPalette.BEZEL)
        canvas.drawBitmap(frame, null, dst, paint)
    }

    /**
     * 画面の下に並ぶビル群。ゲーム本編と同じ、斜め見下ろしの箱で描く。
     */
    private fun drawSkyline() {
        val groundY = logicalH - 28
        // 空はうっすら明るく、地面は暗く
        pixels.fillRect(0, 0, GameView.LOGICAL_W, logicalH, 0)
        pixels.fillRect(0, groundY, GameView.LOGICAL_W, logicalH - groundY, 6)

        val heights = intArrayOf(34, 58, 26, 72, 44, 84, 30, 62, 48, 76, 38, 54, 42, 66, 28, 50)
        var x = -8
        for ((i, h) in heights.withIndex()) {
            val w = 26 + (i % 3) * 4
            val top = groundY - h
            // 正面の壁
            pixels.fillRect(x, top, w, h, if (i % 2 == 0) 7 else 9)
            // 右側面（暗い）
            pixels.fillRect(x + w, top + 4, 5, h - 4, 12)
            // 屋根
            pixels.fillRect(x, top, w, 3, 4)
            for (k in 0 until 5) pixels.set(x + w + k, top + 3 + k, 12)
            // 輪郭
            pixels.drawRect(x, top, w, h, 14)
            // 窓
            for (wy in top + 8 until groundY - 6 step 9) {
                for (wx in x + 4 until x + w - 5 step 8) {
                    val lit = ((wx * 7 + wy * 13 + i) % 5) != 0
                    pixels.fillRect(wx, wy, 4, 5, if (lit) 1 else 13)
                }
            }
            x += w + 5
            if (x > GameView.LOGICAL_W) break
        }
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
