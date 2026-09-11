package io.github.hatake716.pixelcity.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import io.github.hatake716.pixelcity.data.SaveGame
import kotlin.math.max

/**
 * 街を選ぶ画面。
 *
 * 保存した街を一覧で見せ、選んで再開する。空いている枠を選べば新しい街を始める。
 * 長押しで消せる。
 *
 * 一覧には要約（人口・年月・資金）だけを出す。街の中身は選ばれてから読むので、
 * 10個あっても待たされない。
 */
@SuppressLint("ViewConstructor")
class SlotView(
    context: Context,
    /** 新しい街を始める枠も選べるようにするか。 */
    private val allowEmpty: Boolean,
    private val title: String,
) : View(context) {

    /** 選ばれたとき。空き枠なら [SlotInfoOrNull] は null。 */
    var onSlotChosen: ((slot: Int, hasCity: Boolean) -> Unit)? = null
    var onBack: (() -> Unit)? = null
    /** 消していいか確かめてから消す。 */
    var onDeleteRequested: ((slot: Int) -> Unit)? = null

    private var slots: List<SaveGame.SlotInfo?> = SaveGame.listSlots(context)

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

    /** 押している最中の枠。長押しの判定に使う。 */
    private var pressedSlot = -1
    private var pressedAt = 0L
    private var longPressFired = false

    private companion object {
        const val ROW_H = 42
        const val ROW_GAP = 6
        const val LIST_TOP = 78
        const val MARGIN = 14
        /** 長押しとみなす時間。 */
        const val LONG_PRESS_MS = 600L
    }

    /** 一覧を読み直す。消したあとなどに呼ぶ。 */
    fun refresh() {
        slots = SaveGame.listSlots(context)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
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

    /** [index] 番目の枠の y（論理座標）。一覧を画面の中ほどに寄せる。 */
    private fun rowY(index: Int): Int {
        val total = SaveGame.SLOT_COUNT * (ROW_H + ROW_GAP)
        val top = max(LIST_TOP, (logicalH - total) / 2)
        return top + index * (ROW_H + ROW_GAP)
    }

    private fun backButtonY(): Int = logicalH - 52

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        pixels.clear(Palette.UI_BG)

        // 見出し
        text.textSize = 24
        text.drawCentered(pixels, title, GameView.LOGICAL_W / 2, 28, Palette.UI_ACCENT)
        pixels.fillRect(MARGIN, 62, GameView.LOGICAL_W - MARGIN * 2, 2, Palette.UI_LINE)

        for (i in 0 until SaveGame.SLOT_COUNT) {
            drawRow(i, slots.getOrNull(i))
        }

        // もどる
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

    private fun drawRow(index: Int, info: SaveGame.SlotInfo?) {
        val y = rowY(index)
        val x = MARGIN
        val w = GameView.LOGICAL_W - MARGIN * 2
        val pressed = pressedSlot == index

        pixels.fillRect(x, y, w, ROW_H, if (pressed) Palette.UI_LINE else Palette.UI_BG_LIGHT)
        pixels.drawRect(x, y, w, ROW_H, Palette.UI_LINE)

        // 番号
        text.textSize = 17
        text.draw(pixels, "${index + 1}", x + 8, y + 11, Palette.UI_ACCENT)

        if (info == null) {
            text.textSize = 16
            val label = if (allowEmpty) "あたらしい まち" else "（からっぽ）"
            val shade = if (allowEmpty) Palette.UI_TEXT else Palette.UI_DIM
            text.draw(pixels, label, x + 34, y + 13, shade)
            return
        }

        // 人口と年月
        text.textSize = 16
        text.draw(pixels, "じんこう ${info.population}", x + 34, y + 5, Palette.UI_TEXT)
        text.textSize = 14
        text.draw(pixels, info.dateLabel, x + 34, y + 24, Palette.UI_DIM)
        text.draw(pixels, "$${info.funds}", x + 168, y + 24, Palette.UI_DIM)

        // チュートリアルの途中なら印をつける
        if (info.tutorialActive) {
            text.textSize = 13
            text.draw(pixels, "チュートリアル", x + w - 108, y + 6, Palette.GOLD)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val lx = ((event.x - offsetX) / scale).toInt()
        val ly = ((event.y - offsetY) / scale).toInt()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedSlot = slotAt(lx, ly)
                pressedAt = System.currentTimeMillis()
                longPressFired = false
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                // 押したまま時間が経ったら、消すかどうかを尋ねる
                if (pressedSlot >= 0 && !longPressFired &&
                    System.currentTimeMillis() - pressedAt > LONG_PRESS_MS
                ) {
                    longPressFired = true
                    if (slots.getOrNull(pressedSlot) != null) {
                        onDeleteRequested?.invoke(pressedSlot)
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val slot = pressedSlot
                val wasLong = longPressFired
                pressedSlot = -1
                invalidate()

                if (wasLong) return true

                // 長押しの時間に達していたら、離した時点でも消す判定にする
                if (slot >= 0 && System.currentTimeMillis() - pressedAt > LONG_PRESS_MS) {
                    if (slots.getOrNull(slot) != null) {
                        onDeleteRequested?.invoke(slot)
                        return true
                    }
                }

                if (ly >= backButtonY() && ly < backButtonY() + 30) {
                    onBack?.invoke()
                    return true
                }
                if (slot >= 0) {
                    val info = slots.getOrNull(slot)
                    if (info == null && !allowEmpty) return true
                    onSlotChosen?.invoke(slot, info != null)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    /** その論理座標にある枠の番号。どれでもなければ -1。 */
    private fun slotAt(lx: Int, ly: Int): Int {
        if (lx < MARGIN || lx > GameView.LOGICAL_W - MARGIN) return -1
        for (i in 0 until SaveGame.SLOT_COUNT) {
            val y = rowY(i)
            if (ly >= y && ly < y + ROW_H) return i
        }
        return -1
    }
}
