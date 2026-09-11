package io.github.hatake716.pixelcity.ui

/**
 * クォータービュー（斜め見下ろし）の座標変換。
 *
 * タイルは幅 [TILE_W]、高さ [TILE_H] の菱形。真上からの正方形ではなく、
 * 2:1 の菱形を敷き詰めることで、斜め上から街を見下ろしている画になる。
 *
 * ```
 *        (0,0)
 *       ◇     ◇
 *   ◇     ◇     ◇
 *       ◇     ◇
 * ```
 *
 * タイル (tx, ty) の菱形の**中心**は
 *   sx = (tx - ty) * TILE_W / 2
 *   sy = (tx + ty) * TILE_H / 2
 * に来る。x が右下へ、y が左下へ伸びる並びになる。
 */
object Iso {
    /**
     * 菱形の幅（論理ピクセル）。
     *
     * 128×64 は、従来の 64×32 の縦横2倍＝面積4倍。
     * 窓枠の桟、ベランダ、屋上の設備、店の看板、道路の白線まで描き分けられる。
     * 普段は縮小して表示し、「よせる」縮尺にすると等倍で細部が見える。
     */
    const val TILE_W = 128
    /** 菱形の高さ。幅の半分にすると、いわゆる2:1のクォータービューになる。 */
    const val TILE_H = 64

    /** 建物の1段ぶんの高さ。段数を掛けて持ち上げる。 */
    const val LEVEL_H = 32

    /**
     * 斜めに置いた街全体の、画面上の大きさ。
     * 幅は (w + h) * TILE_W / 2、高さは (w + h) * TILE_H / 2 になる。
     */
    fun mapPixelWidth(w: Int, h: Int): Int = (w + h) * TILE_W / 2
    fun mapPixelHeight(w: Int, h: Int): Int = (w + h) * TILE_H / 2

    /** 小数のタイル座標 → 画面座標。カメラの位置に使う。 */
    fun screenX2(tx: Float, ty: Float): Float = (tx - ty) * TILE_W / 2f
    fun screenY2(tx: Float, ty: Float): Float = (tx + ty) * TILE_H / 2f

    /** タイル座標 → 画面座標（菱形の上の頂点）。 */
    fun screenX(tx: Int, ty: Int): Int = (tx - ty) * TILE_W / 2
    fun screenY(tx: Int, ty: Int): Int = (tx + ty) * TILE_H / 2

    /**
     * 画面座標 → タイル座標。[screenX]/[screenY] の逆変換。
     * 菱形の内側かどうかまでは見ないので、境界では隣のタイルを返すことがある。
     */
    fun tileAt(sx: Float, sy: Float): Pair<Int, Int> {
        val a = sx / (TILE_W / 2f)
        val b = sy / (TILE_H / 2f)
        val tx = ((a + b) / 2f)
        val ty = ((b - a) / 2f)
        return Math.floor(tx.toDouble()).toInt() to Math.floor(ty.toDouble()).toInt()
    }

    /**
     * 菱形の内側かどうか。中心からの距離を菱形の式で測る。
     * [dx]/[dy] は菱形の中心からの差。
     */
    fun insideDiamond(dx: Float, dy: Float): Boolean =
        Math.abs(dx) / (TILE_W / 2f) + Math.abs(dy) / (TILE_H / 2f) <= 1f

    /**
     * 描画の順序。奥（画面の上）から手前（下）へ描くことで、
     * 手前の建物が奥の建物を正しく隠す。
     * (tx + ty) が小さいほど奥にある。
     */
    fun depth(tx: Int, ty: Int): Int = tx + ty
}
