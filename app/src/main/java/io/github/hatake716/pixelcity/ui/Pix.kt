package io.github.hatake716.pixelcity.ui

/**
 * ドット絵の画素。
 *
 * 画素は [Palette] の索引を1バイトで持つ。-1 は透明。
 * 色そのものではなく索引を持つことで、
 *  - スプライトは「屋根の色」とだけ書けばよく、配色を後から変えられる
 *  - 1画素1バイトで済み、大きな絵でも軽い
 * という利点がある。当時の実機がパレット方式だったのと同じ考え方。
 */
object Pix {
    const val TRANSPARENT: Byte = -1

    /**
     * 色の索引からスプライトを組み立てる補助。
     * [w]×[h] の大きさで、[fill] が各画素の [Palette] 索引を返す。
     * 透明にしたい画素では [TRANSPARENT] を返す。
     */
    inline fun build(w: Int, h: Int, fill: (x: Int, y: Int) -> Int): Sprite {
        val data = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            data[y * w + x] = fill(x, y).toByte()
        }
        return Sprite(w, h, data)
    }
}

/** 幅・高さつきのドット絵。画素は [Palette] の索引。 */
class Sprite(val width: Int, val height: Int, val data: ByteArray) {
    fun at(x: Int, y: Int): Byte =
        if (x < 0 || y < 0 || x >= width || y >= height) Pix.TRANSPARENT else data[y * width + x]

    /** 描いた画素の数。空のスプライトを見つけるために使う。 */
    val inkCount: Int get() = data.count { it != Pix.TRANSPARENT }

    /** 使われている色の種類。のっぺりした絵を見つけるために使う。 */
    val colourCount: Int get() = data.filter { it != Pix.TRANSPARENT }.toSet().size
}
