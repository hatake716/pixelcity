package io.github.hatake716.pixelcity.ui

/**
 * 論理解像度のピクセルバッファ。0..126 のパレット索引を持つ。
 *
 * ここへ描いてから整数倍で画面に引き伸ばすことで、
 * どの端末でもドットの大きさが揃い、にじまない。
 */
class PixelCanvas(val width: Int, val height: Int) {
    val pixels = ByteArray(width * height)
    // Production UI is drawn at native resolution. The palette buffer remains for legacy previews.
    var nativeCanvas: android.graphics.Canvas? = null
    private val nativePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private fun nativeRect(x: Int, y: Int, w: Int, h: Int, value: Int): Boolean {
        val c = nativeCanvas ?: return false
        if (value >= 0) {
            nativePaint.color = Palette.of(value)
            c.drawRect(x.toFloat(), y.toFloat(), (x+w).toFloat(), (y+h).toFloat(), nativePaint)
        }
        return true
    }

    fun clear(value: Int = 0) { if (!nativeRect(0,0,width,height,value)) pixels.fill(value.toByte()) }

    fun set(x: Int, y: Int, value: Int) {
        if (x < 0 || y < 0 || x >= width || y >= height) return
        if (!nativeRect(x,y,1,1,value)) pixels[y * width + x] = value.toByte()
    }

    fun get(x: Int, y: Int): Int =
        if (x < 0 || y < 0 || x >= width || y >= height) 0 else pixels[y * width + x].toInt()

    /** 透明（-1）を含めて塗りつぶす。デバッグ用。 */
    fun clearTo(value: Int) = clear(value)

    fun fillRect(x: Int, y: Int, w: Int, h: Int, value: Int) {
        if (nativeRect(x,y,w,h,value)) return
        val v = value.toByte()
        val x0 = maxOf(0, x)
        val y0 = maxOf(0, y)
        val x1 = minOf(width, x + w)
        val y1 = minOf(height, y + h)
        for (yy in y0 until y1) {
            val row = yy * width
            for (xx in x0 until x1) pixels[row + xx] = v
        }
    }

    fun drawRect(x: Int, y: Int, w: Int, h: Int, value: Int) {
        if (w <= 0 || h <= 0) return
        if (nativeCanvas != null) {
            nativeRect(x,y,w,1,value); nativeRect(x,y+h-1,w,1,value)
            nativeRect(x,y,1,h,value); nativeRect(x+w-1,y,1,h,value)
            return
        }
        for (xx in x until x + w) { set(xx, y, value); set(xx, y + h - 1, value) }
        for (yy in y until y + h) { set(x, yy, value); set(x + w - 1, yy, value) }
    }

    private val ditherShaders = HashMap<Int, android.graphics.BitmapShader>()

    /** 市松模様で塗る。ネイティブ描画では一枚の繰り返し模様として送る。 */
    fun ditherRect(x: Int, y: Int, w: Int, h: Int, value: Int) {
        val c = nativeCanvas
        if (c != null) {
            if (value < 0 || w <= 0 || h <= 0) return
            val shader = ditherShaders.getOrPut(value) {
                val color = Palette.of(value)
                val b = android.graphics.Bitmap.createBitmap(intArrayOf(color, 0, 0, color), 2, 2, android.graphics.Bitmap.Config.ARGB_8888)
                android.graphics.BitmapShader(b, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
            }
            nativePaint.shader = shader
            nativePaint.isAntiAlias = false
            c.drawRect(maxOf(0,x).toFloat(), maxOf(0,y).toFloat(), minOf(width,x+w).toFloat(), minOf(height,y+h).toFloat(), nativePaint)
            nativePaint.shader = null
            nativePaint.isAntiAlias = true
            return
        }
        for (yy in y until y + h) for (xx in x until x + w) {
            if ((xx + yy) and 1 == 0) set(xx, yy, value)
        }
    }

    /**
     * スプライトを描く。[transparent] と同じ値の画素は描かない（-1 で全て描く）。
     */
    fun blit(sprite: ByteArray, spriteSize: Int, x: Int, y: Int, transparent: Int = -1) {
        for (sy in 0 until spriteSize) {
            val ty = y + sy
            if (ty < 0 || ty >= height) continue
            val srow = sy * spriteSize
            val trow = ty * width
            for (sx in 0 until spriteSize) {
                val v = sprite[srow + sx].toInt()
                if (v == transparent) continue
                val tx = x + sx
                if (tx < 0 || tx >= width) continue
                pixels[trow + tx] = v.toByte()
            }
        }
    }
}
