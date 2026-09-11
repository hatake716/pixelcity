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

/**
 * お手本の街を選ぶ画面。
 *
 * それぞれの街を小さく描いて見せ、性格の説明を添える。
 * 絵は本編と同じ [CityRenderer] で描くので、選んだものがそのまま手に入る。
 */
@SuppressLint("ViewConstructor")
class ShowcasePickerView(context: Context) : View(context) {

    var onChosen: ((ShowcaseCity.Kind) -> Unit)? = null
    var onBack: (() -> Unit)? = null

    private var logicalH = GameView.LOGICAL_H
    private var pixels = PixelCanvas(GameView.LOGICAL_W, GameView.LOGICAL_H)
    private var frame = Bitmap.createBitmap(
        GameView.LOGICAL_W, GameView.LOGICAL_H, Bitmap.Config.ARGB_8888,
    )
    private var buffer = IntArray(GameView.LOGICAL_W * GameView.LOGICAL_H)
    private val text = GbText(context)
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    private val dst = Rect()
    private var scale = 1
    private var offsetX = 0
    private var offsetY = 0
    private val renderer = CityRenderer()

    /** 見本の街。描くときに一度だけ組み立てる。 */
    private val cities by lazy {
        ShowcaseCity.Kind.entries.associateWith { ShowcaseCity.build(it) }
    }

    private var pressed = -1

    private companion object {
        const val CARD_H = 150
        const val CARD_GAP = 12
        const val MARGIN = 14
        /** 絵の高さ。街を覗く窓。 */
        const val VIEW_H = 84
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
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

    private fun cardY(index: Int): Int {
        val total = ShowcaseCity.Kind.entries.size * (CARD_H + CARD_GAP)
        val top = max(76, (logicalH - total) / 2)
        return top + index * (CARD_H + CARD_GAP)
    }

    private fun backButtonY(): Int = logicalH - 52

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        pixels.clear(Palette.UI_BG)

        text.textSize = 22
        text.drawCentered(pixels, "おてほんから はじめる", GameView.LOGICAL_W / 2, 26, Palette.UI_ACCENT)
        pixels.fillRect(MARGIN, 60, GameView.LOGICAL_W - MARGIN * 2, 2, Palette.UI_LINE)

        for ((i, kind) in ShowcaseCity.Kind.entries.withIndex()) {
            drawCard(i, kind)
        }

        val by = backButtonY()
        val bw = 96
        val bx = (GameView.LOGICAL_W - bw) / 2
        pixels.fillRect(bx, by, bw, 30, Palette.UI_BG_LIGHT)
        pixels.drawRect(bx, by, bw, 30, Palette.UI_LINE)
        text.textSize = 17
        text.drawCentered(pixels, "もどる", GameView.LOGICAL_W / 2, by + 6, Palette.UI_TEXT)

        for (i in pixels.pixels.indices) buffer[i] = Palette.of(pixels.pixels[i].toInt())
        frame.setPixels(buffer, 0, GameView.LOGICAL_W, 0, 0, GameView.LOGICAL_W, logicalH)
        canvas.drawColor(Palette.BEZEL)
        canvas.drawBitmap(frame, null, dst, paint)
    }

    private fun drawCard(index: Int, kind: ShowcaseCity.Kind) {
        val y = cardY(index)
        val x = MARGIN
        val w = GameView.LOGICAL_W - MARGIN * 2
        val on = pressed == index

        pixels.fillRect(x, y, w, CARD_H, if (on) Palette.UI_LINE else Palette.UI_BG_LIGHT)
        pixels.drawRect(x, y, w, CARD_H, Palette.UI_LINE)

        // 街の絵。カードの中だけに描く。
        val city = cities[kind] ?: return
        renderer.draw(
            pixels, city,
            camX = 30f, camY = 30f,
            zoomNum = 1, zoomDen = 8,
            viewTop = y + 2, viewHeight = VIEW_H,
        )
        pixels.drawRect(x, y + 2, w, VIEW_H, Palette.UI_LINE)

        // 名前と説明
        val textTop = y + VIEW_H + 8
        text.textSize = 19
        text.draw(pixels, kind.label, x + 10, textTop, Palette.UI_ACCENT)

        text.textSize = 13
        var ly = textTop + 24
        for (line in text.wrap(kind.summary, w - 20)) {
            text.draw(pixels, line, x + 10, ly, Palette.UI_TEXT)
            ly += 15
            if (ly > y + CARD_H - 14) break
        }

        // 人口の目安
        text.textSize = 13
        val pop = "じんこう ${city.population}"
        text.draw(pixels, pop, x + w - text.measure(pop) - 10, textTop + 2, Palette.UI_DIM)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val lx = ((event.x - offsetX) / scale).toInt()
        val ly = ((event.y - offsetY) / scale).toInt()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = cardAt(lx, ly)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val hit = pressed
                pressed = -1
                invalidate()
                if (ly >= backButtonY() && ly < backButtonY() + 30) {
                    onBack?.invoke()
                    return true
                }
                ShowcaseCity.Kind.entries.getOrNull(hit)?.let { onChosen?.invoke(it) }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun cardAt(lx: Int, ly: Int): Int {
        if (lx < MARGIN || lx > GameView.LOGICAL_W - MARGIN) return -1
        for (i in ShowcaseCity.Kind.entries.indices) {
            val y = cardY(i)
            if (ly >= y && ly < y + CARD_H) return i
        }
        return -1
    }
}
