package io.github.hatake716.pixelcity.ui

/**
 * 論理解像度のピクセルバッファ。0..3 のパレット索引を持つ。
 *
 * ここへ描いてから整数倍で画面に引き伸ばすことで、
 * どの端末でもドットの大きさが揃い、にじまない。
 */
class PixelCanvas(val width: Int, val height: Int) {
    val pixels = ByteArray(width * height)

    fun clear(value: Int = 0) = pixels.fill(value.toByte())

    fun set(x: Int, y: Int, value: Int) {
        if (x < 0 || y < 0 || x >= width || y >= height) return
        pixels[y * width + x] = value.toByte()
    }

    fun get(x: Int, y: Int): Int =
        if (x < 0 || y < 0 || x >= width || y >= height) 0 else pixels[y * width + x].toInt()

    /** 透明（-1）を含めて塗りつぶす。デバッグ用。 */
    fun clearTo(value: Int) = pixels.fill(value.toByte())

    fun fillRect(x: Int, y: Int, w: Int, h: Int, value: Int) {
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
        for (xx in x until x + w) { set(xx, y, value); set(xx, y + h - 1, value) }
        for (yy in y until y + h) { set(x, yy, value); set(x + w - 1, yy, value) }
    }

    /** 市松模様で塗る。4階調しかないので、中間の濃さはこれで作る。 */
    fun ditherRect(x: Int, y: Int, w: Int, h: Int, value: Int) {
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
