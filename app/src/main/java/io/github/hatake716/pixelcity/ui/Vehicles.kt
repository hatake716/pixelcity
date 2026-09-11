package io.github.hatake716.pixelcity.ui

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 道路を走る車。
 *
 * タイルが 512×256 になったので、車1台にも屋根・窓・前照灯まで描ける。
 * 絵はすべて式から作る。外部の素材は使わない。
 *
 * 向きは4つ。クォータービューでは、
 *  - 東（右下へ）／西（左上へ）＝ X 軸に沿う道
 *  - 南（左下へ）／北（右上へ）＝ Y 軸に沿う道
 * で、見える面が変わる。
 */
object Vehicles {

    /** 車1台の絵の大きさ。1タイル（512）の1/6ほど。 */
    const val W = 84
    const val H = 60

    /** 進む向き。 */
    enum class Dir { EAST, WEST, SOUTH, NORTH }

    /**
     * 車の種類。
     *
     * 大きさと色を変えて、道が単調にならないようにする。
     * バスとトラックは長く、乗用車は短い。
     */
    enum class Kind(
        /** 車体の長さ（絵の座標）。 */
        val length: Int,
        /** 車体の高さ。 */
        val tall: Int,
        val body: Int,
        val bodyDark: Int,
        /** 1時間あたりに進むタイル数の目安。大きいものほど遅い。 */
        val speedScale: Float,
    ) {
        CAR_RED(46, 16, Palette.RED, Palette.RED_DARK, 1.0f),
        CAR_BLUE(46, 16, Palette.SKY_DEEP, Palette.WATER_DARK, 1.0f),
        CAR_WHITE(46, 16, Palette.WHITE, Palette.STONE, 1.0f),
        CAR_YELLOW(44, 16, Palette.GOLD, Palette.GOLD_DARK, 1.05f),
        VAN(54, 22, Palette.STONE_LIT, Palette.STONE, 0.9f),
        TRUCK(64, 26, Palette.TREE, Palette.TREE_DARK, 0.8f),
        BUS(72, 26, Palette.TOWER_ORANGE, Palette.TOWER_ORANGE_DARK, 0.75f),
        ;

        /** 乗用車か。信号待ちの並びかたを変えるのに使う。 */
        val isCar: Boolean get() = ordinal <= CAR_YELLOW.ordinal
    }

    private class Buf {
        val data = ByteArray(W * H) { Pix.TRANSPARENT }
        fun set(x: Int, y: Int, v: Int) {
            if (x in 0 until W && y in 0 until H) data[y * W + x] = v.toByte()
        }
        fun toSprite() = Sprite(W, H, data)
    }

    /**
     * 1台ぶんの絵。
     *
     * クォータービューの車は、上から見た菱形の車体に、
     * 高さのぶんの側面が付いた形になる。
     * 進む向きによって、どちらの側面が見えるかが変わる。
     */
    private fun draw(kind: Kind, dir: Dir): Sprite {
        val b = Buf()
        val cx = W / 2
        val cy = H / 2 + kind.tall / 2

        // 車体。長さ方向と幅方向を、道の軸に合わせる。
        //
        // 上から見た車は、長さが幅の2倍以上ある。
        // 菱形にすると角が尖りすぎるので、角を丸めた長方形にする。
        val len = kind.length
        val wid = (len * 0.40f).roundToInt()
        val alongX = dir == Dir.EAST || dir == Dir.WEST

        /**
         * 車体の上面のなかか。
         * [a] は進む向き、[c] は横向きの位置（どちらも -1..1）。
         *
         * 角を丸めた長方形（superellipse）にする。
         * 菱形（|a|+|c|<=1）だと鼻先が尖りすぎ、
         * 四角（max<=1）だと箱に見える。その中間をとる。
         */
        fun inBody(a: Float, c: Float): Boolean {
            val p = 3.0                      // 3 くらいが車らしい丸み
            return Math.pow(abs(a).toDouble(), p) + Math.pow(abs(c).toDouble(), p) <= 1.0
        }

        // --- 側面（車体の厚み）---
        for (py in 0 until H) for (px in 0 until W) {
            // 画面の座標を、道の軸（進行方向 a、横方向 c）へほどく
            val dx = (px - cx).toFloat()
            val dy = (py - cy).toFloat()
            // クォータービューでは、菱形の軸は画面上で 2:1 に潰れる。
            // 長さ len の車体は、その軸に沿って len だけ伸びる。
            val a1 = dx / (len / 2f)          // 右下へ向かう軸
            val a2 = dy / (len / 4f)
            val c1 = dx / (wid / 2f)
            val c2 = dy / (wid / 4f)
            val along = if (alongX) (a1 + a2) / 2f else (a2 - a1) / 2f
            val across = if (alongX) (c2 - c1) / 2f else (c1 + c2) / 2f
            if (!inBody(along, across)) continue

            // 上面
            val roofShade = when {
                abs(along) > 0.86f || abs(across) > 0.86f -> kind.bodyDark
                else -> kind.body
            }
            b.set(px, py, roofShade)

            // 窓。乗用車は中ほど、バスとトラックは前だけ。
            val winA = if (kind.isCar) abs(along) < 0.42f else along in -0.10f..0.52f
            if (winA && abs(across) < 0.60f && abs(along) < 0.86f) {
                b.set(px, py, Palette.GLASS)
            }
        }

        // --- 厚み（側面）を下に伸ばす ---
        for (px in 0 until W) {
            // その列で、いちばん下の車体の画素を探す
            var lowest = -1
            for (py in H - 1 downTo 0) {
                if (b.data[py * W + px] != Pix.TRANSPARENT) { lowest = py; break }
            }
            if (lowest < 0) continue
            for (k in 1..kind.tall) {
                val py = lowest + k
                if (py >= H) break
                // 進む向きで、明るい側面と暗い側面が入れ替わる
                val lit = when (dir) {
                    Dir.EAST, Dir.SOUTH -> px < cx
                    Dir.WEST, Dir.NORTH -> px >= cx
                }
                b.set(px, py, if (lit) kind.body else kind.bodyDark)
            }
            // 接地の影
            val shadowY = lowest + kind.tall + 1
            if (shadowY < H) b.set(px, shadowY, Palette.SHADOW)
        }

        // --- 前照灯と尾灯 ---
        // 進む先の端を明るく、後ろを赤く。走る向きが分かる。
        val noseX = when (dir) {
            Dir.EAST -> cx + len / 3
            Dir.WEST -> cx - len / 3
            Dir.SOUTH -> cx - len / 3
            Dir.NORTH -> cx + len / 3
        }
        val noseY = when (dir) {
            Dir.EAST, Dir.SOUTH -> cy + len / 7
            else -> cy - len / 7
        }
        for (dy2 in -2..2) for (dx2 in -3..3) {
            if (abs(dx2) + abs(dy2) > 3) continue
            b.set(noseX + dx2, noseY + dy2, Palette.WINDOW_LIT)
        }
        val tailX = 2 * cx - noseX
        val tailY = 2 * cy - noseY
        for (dy2 in -1..1) for (dx2 in -2..2) {
            if (abs(dx2) + abs(dy2) > 2) continue
            b.set(tailX + dx2, tailY + dy2, Palette.RED)
        }
        return b.toSprite()
    }

    /** 作った絵は使い回す。向き4つ × 種類7つ = 28枚。 */
    private val cache = HashMap<Int, Sprite>()

    fun of(kind: Kind, dir: Dir): Sprite =
        cache.getOrPut(kind.ordinal * 4 + dir.ordinal) { draw(kind, dir) }
}
