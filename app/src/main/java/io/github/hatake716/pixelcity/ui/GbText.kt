package io.github.hatake716.pixelcity.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import io.github.hatake716.pixelcity.R

/**
 * 同梱の DotGothic16 で文字を描き、4階調へ落としてピクセルバッファへ転送する。
 *
 * 日本語のドットフォントを自前で持つのは現実的ではないので、
 * SIL OFL 1.1 の DotGothic16（Fontworks Inc.）をそのまま同梱して使う。
 * いったんビットマップへ描いてから2値化することで、
 * アンチエイリアスのにじみを消し、ドット絵と同じ見た目に揃える。
 */
class GbText(context: Context) {

    private companion object {
        /** 行頭に来てほしくない文字（禁則）。 */
        const val NO_LINE_START = "、。！？」）ー・"
    }


    private val typeface: Typeface =
        ResourcesCompat.getFont(context, R.font.dot_gothic) ?: Typeface.MONOSPACE

    private val paint = Paint().apply {
        typeface = this@GbText.typeface
        isAntiAlias = false
        color = Color.BLACK
    }

    private var scratch: Bitmap? = null
    private var scratchCanvas: Canvas? = null

    /** 論理ピクセルでの1文字の高さ。 */
    var textSize: Int = 8
        set(value) {
            field = value
            paint.textSize = value.toFloat()
        }

    init {
        paint.textSize = textSize.toFloat()
    }

    fun measure(text: String): Int = paint.measureText(text).toInt()

    /** 描画に要する高さ。上下にはみ出す分を含む。 */
    private fun lineHeight(): Int = textSize + textSize / 2 + 2

    /** ベースラインの位置。文字の上端から下ろした距離。 */
    private fun baseline(): Int = textSize

    /**
     * [text] を (x, y) を左上として [canvas] に描く。[value] はパレット索引。
     * 文字の形をした画素だけを塗り、背景には触れない。
     */
    fun draw(canvas: PixelCanvas, text: String, x: Int, y: Int, value: Int = 3) {
        if (text.isEmpty()) return
        val w = measure(text).coerceAtLeast(1) + 2
        val h = lineHeight()
        val bmp = ensureScratch(w, h) ?: return
        val c = scratchCanvas ?: return

        bmp.eraseColor(Color.WHITE)
        c.drawText(text, 0f, baseline().toFloat(), paint)

        for (py in 0 until h) {
            val ty = y + py
            if (ty < 0 || ty >= canvas.height) continue
            for (px in 0 until w) {
                val tx = x + px
                if (tx < 0 || tx >= canvas.width) continue
                // 白でない画素を文字とみなす（2値化）。
                // ドットフォントの細い線を落とさないよう、しきい値は高めにとる。
                if (Color.red(bmp.getPixel(px, py)) < 200) canvas.set(tx, ty, value)
            }
        }
    }

    /**
     * [maxWidth] に収まるよう [text] を折り返す。
     *
     * 日本語には単語の切れ目がないので、空白があればそこで、
     * なければ文字単位で折る。句読点が行頭に来ないよう、直前の文字と一緒に送る。
     */
    fun wrap(text: String, maxWidth: Int): List<String> {
        if (text.isEmpty()) return listOf("")
        val out = mutableListOf<String>()
        for (paragraph in text.split("\n")) {
            if (measure(paragraph) <= maxWidth) {
                out.add(paragraph)
                continue
            }
            val line = StringBuilder()
            // 禁則で1行に押し込める幅の上限。ここを超えたら、
            // 句読点であっても折る。無制限に許すと画面からはみ出す。
            val hardLimit = maxWidth + measure("。。")
            for (ch in paragraph) {
                val next = measure(line.toString() + ch)
                val overflow = next > maxWidth
                // 行頭に置きたくない文字は、少しだけなら前の行に残す。
                val keepWithPrevious = ch in NO_LINE_START && next <= hardLimit
                if (overflow && line.isNotEmpty() && !keepWithPrevious) {
                    out.add(line.toString().trimEnd())
                    line.setLength(0)
                }
                line.append(ch)
            }
            if (line.isNotEmpty()) out.add(line.toString().trimEnd())
        }
        return out
    }

    /** 中央寄せで描く。 */
    fun drawCentered(canvas: PixelCanvas, text: String, centerX: Int, y: Int, value: Int = 3) {
        draw(canvas, text, centerX - measure(text) / 2, y, value)
    }

    /**
     * 描画用のビットマップを確保する。足りなければ作り直す。
     * 幅と高さを別々に判定しないと、横に長い文字列が切れる。
     */
    private fun ensureScratch(w: Int, h: Int): Bitmap? {
        val current = scratch
        if (current != null && current.width >= w && current.height >= h) return current
        val bmp = Bitmap.createBitmap(
            maxOf(w, current?.width ?: 0),
            maxOf(h, current?.height ?: 0),
            Bitmap.Config.ARGB_8888,
        )
        scratch = bmp
        scratchCanvas = Canvas(bmp)
        return bmp
    }
}
