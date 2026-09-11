package io.github.hatake716.pixelcity.ui

/**
 * ドット絵の記法。
 *
 * 高精細なスプライトを文字列で書くために、16階調を1文字ずつに割り当てている。
 * 明るい側から暗い側へ:
 *
 * ```
 *   ' ' 透明（描かない）
 *   '.' 0   最も明るい
 *   ',' 1
 *   '-' 2
 *   '~' 3
 *   ':' 4
 *   ';' 5
 *   '+' 6
 *   '=' 7
 *   '*' 8
 *   'o' 9
 *   'O' 10
 *   '&' 11
 *   '%' 12
 *   '#' 13
 *   '@' 14
 *   '█' 15  最も暗い
 * ```
 *
 * 透明は -1 として保持する。
 */
object Pix {
    const val TRANSPARENT: Byte = -1

    private val CHARS = ".,-~:;+=*oO&%#@█"

    fun level(ch: Char): Byte = when (ch) {
        ' ' -> TRANSPARENT
        else -> {
            val i = CHARS.indexOf(ch)
            require(i >= 0) { "unknown pixel char '$ch'" }
            i.toByte()
        }
    }

    /**
     * 文字列の行からスプライトを作る。行の長さは揃っていること。
     * 返り値は幅×高さの配列で、-1 は透明。
     */
    fun sprite(vararg rows: String): Sprite {
        require(rows.isNotEmpty()) { "sprite needs at least one row" }
        val h = rows.size
        val w = rows[0].length
        val data = ByteArray(w * h)
        rows.forEachIndexed { y, row ->
            require(row.length == w) { "row $y is ${row.length} wide, expected $w" }
            row.forEachIndexed { x, ch -> data[y * w + x] = level(ch) }
        }
        return Sprite(w, h, data)
    }
}

/** 幅・高さつきのドット絵。 */
class Sprite(val width: Int, val height: Int, val data: ByteArray) {
    fun at(x: Int, y: Int): Byte =
        if (x < 0 || y < 0 || x >= width || y >= height) Pix.TRANSPARENT else data[y * width + x]

    /** 描いた画素の数。空のスプライトを見つけるために使う。 */
    val inkCount: Int get() = data.count { it != Pix.TRANSPARENT }
}
