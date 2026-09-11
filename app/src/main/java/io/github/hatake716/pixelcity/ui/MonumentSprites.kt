package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.Monument
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 世界の有名建築。クォータービューの 2×2 タイルを占める。
 *
 * 幅は 64（タイル2枚ぶん）。高さは建物ごとに変える。
 * 実在の建造物の「形の特徴」をドット絵に起こしたもので、
 * 写真・図面・第三者の素材は使用していない。すべてこのファイルのコードで描いている。
 *
 * 形は「積み上げる部品」の組み合わせで作る。手で1行ずつ書くと
 * 幅がずれて壊れるため、すべて生成している。
 */
object MonumentSprites {

    /** 2×2 タイルぶんの幅。 */
    const val W = Iso.TILE_W * 2      // 64
    /** 底面の菱形の高さ。 */
    private const val BASE_H = Iso.TILE_H * 2  // 32

    /** 面の明るさ。左上からの光。 */
    private const val LIT: Byte = 2
    private const val MID: Byte = 6
    private const val SHADE: Byte = 10
    private const val EDGE: Byte = 14

    /** 描く先。生成のあいだだけ使う可変のキャンバス。 */
    private class Buf(val w: Int, val h: Int) {
        val data = ByteArray(w * h) { Pix.TRANSPARENT }
        fun set(x: Int, y: Int, v: Byte) {
            if (x in 0 until w && y in 0 until h) data[y * w + x] = v
        }
        fun get(x: Int, y: Int): Byte =
            if (x in 0 until w && y in 0 until h) data[y * w + x] else Pix.TRANSPARENT
        fun toSprite() = Sprite(w, h, data)
    }

    /**
     * 菱形の土台。[cx]/[cy] を中心に、幅 [w] の菱形を [thickness] の厚みで置く。
     */
    private fun Buf.platform(cx: Int, cy: Int, w: Int, thickness: Int, top: Byte) {
        val h = w / 2
        for (t in 0 until thickness) {
            for (y in 0 until h) for (x in 0 until w) {
                val dx = (x + 0.5f) - w / 2f
                val dy = (y + 0.5f) - h / 2f
                if (abs(dx) / (w / 2f) + abs(dy) / (h / 2f) > 1f) continue
                val px = cx - w / 2 + x
                val py = cy - h / 2 + y + t
                set(px, py, if (t == 0) top else if (dx < 0) SHADE else EDGE)
            }
        }
    }

    /**
     * 四角い塔。[cx] を中心に、下端 [baseY] から高さ [h]、幅 [w] の箱を積む。
     * 上に行くほど細くしたいときは [topW] を変える。
     */
    private fun Buf.tower(
        cx: Int, baseY: Int, h: Int, w: Int, topW: Int = w,
        face: (u: Float, v: Float, side: Int) -> Byte? = { _, _, _ -> null },
    ) {
        for (i in 0 until h) {
            val v = i.toFloat() / h            // 0=下, 1=上
            val ww = (w + (topW - w) * v).roundToInt().coerceAtLeast(2)
            val y = baseY - i
            for (x in -ww / 2..ww / 2) {
                val u = if (ww <= 1) 0f else (x + ww / 2f) / ww
                val side = if (x < 0) -1 else 1
                var c = if (x < 0) MID else SHADE
                if (abs(x) >= ww / 2) c = EDGE
                face(u, v, side)?.let { c = it }
                set(cx + x, y, c)
            }
        }
    }

    /** 屋根の菱形（塔の頂点など）。 */
    private fun Buf.cap(cx: Int, y: Int, w: Int, v: Byte) {
        val h = (w / 2).coerceAtLeast(1)
        for (yy in 0 until h) for (xx in 0 until w) {
            val dx = (xx + 0.5f) - w / 2f
            val dy = (yy + 0.5f) - h / 2f
            if (abs(dx) / (w / 2f) + abs(dy) / (h / 2f) > 1f) continue
            set(cx - w / 2 + xx, y - h / 2 + yy, v)
        }
    }

    /** アーチ（凱旋門・コロッセオ用）。 */
    private fun Buf.arch(cx: Int, baseY: Int, w: Int, h: Int) {
        for (y in 0 until h) for (x in -w / 2..w / 2) {
            val v = y.toFloat() / h
            val halfW = (w / 2f) * Math.sqrt((1f - v * v).toDouble()).toFloat()
            if (abs(x) <= halfW) set(cx + x, baseY - y, Pix.TRANSPARENT)
        }
    }

    // ------------------------------------------------------------------

    /** 東京タワー。四脚の鉄塔と展望台。 */
    private fun tokyoTower(): Sprite {
        val h = 92
        val b = Buf(W, h)
        val cx = W / 2
        val baseY = h - 1
        b.platform(cx, baseY - BASE_H / 2 + 4, W - 8, 3, LIT)

        // 4本の脚が上へ向かって細くなる鉄塔
        val topY = 10
        val legBottom = baseY - 10
        for (y in topY..legBottom) {
            val v = (legBottom - y).toFloat() / (legBottom - topY)   // 0=下 1=上
            val half = ((1f - v) * 22f + 2f).toInt()
            // 左右の脚
            for (d in intArrayOf(-half, half)) {
                b.set(cx + d, y, EDGE)
                b.set(cx + d + if (d < 0) 1 else -1, y, MID)
            }
            // 格子。斜めの筋を入れて鉄骨に見せる
            if ((y + (half / 2)) % 6 == 0) {
                var x = -half
                while (x <= half) {
                    b.set(cx + x, y, if (x < 0) MID else SHADE)
                    x += 3
                }
            }
        }
        // 展望台（2段）
        b.cap(cx, topY + 40, 30, MID)
        for (y in topY + 40 until topY + 48) {
            for (x in -15..15) b.set(cx + x, y, if (x < 0) MID else SHADE)
            b.set(cx - 15, y, EDGE); b.set(cx + 15, y, EDGE)
        }
        b.cap(cx, topY + 20, 18, LIT)
        for (y in topY + 20 until topY + 26) {
            for (x in -9..9) b.set(cx + x, y, if (x < 0) MID else SHADE)
        }
        // アンテナ
        for (y in 0 until topY + 2) b.set(cx, y, EDGE)
        return b.toSprite()
    }

    /** 凱旋門。太い脚とアーチ。 */
    private fun arcDeTriomphe(): Sprite {
        val h = 64
        val b = Buf(W, h)
        val cx = W / 2
        val baseY = h - 1
        b.platform(cx, baseY - BASE_H / 2 + 2, W - 6, 3, LIT)

        val top = 8
        val bottom = baseY - 12
        val halfW = 24
        for (y in top..bottom) {
            for (x in -halfW..halfW) {
                val u = (x + halfW).toFloat() / (halfW * 2)
                var c = if (x < 0) MID else SHADE
                if (abs(x) >= halfW - 1) c = EDGE
                // 上部の帯（装飾）
                if (y in top + 6..top + 10) c = if (x < 0) LIT else MID
                b.set(cx + x, y, c)
            }
        }
        b.cap(cx, top, halfW * 2 + 2, LIT)
        // 大きなアーチを抜く
        b.arch(cx, bottom, 26, 30)
        // 左右の小さなアーチ
        b.arch(cx - 17, bottom, 10, 14)
        b.arch(cx + 17, bottom, 10, 14)
        return b.toSprite()
    }

    /** コロッセオ。楕円の外壁とアーチの列。 */
    private fun colosseum(): Sprite {
        val h = 56
        val b = Buf(W, h)
        val cx = W / 2
        val baseY = h - 1
        val rx = 28f
        val ry = 14f
        val wallH = 26

        // 外周の壁を、楕円に沿って立てる
        for (deg in 0 until 360) {
            val a = Math.toRadians(deg.toDouble())
            val x = (Math.cos(a) * rx).toInt()
            val yBase = baseY - 12 + (Math.sin(a) * ry).toInt()
            // 奥（上）の壁は暗く、手前は明るく
            val front = Math.sin(a) > 0
            for (i in 0 until wallH) {
                val y = yBase - i
                var c = if (front) (if (x < 0) MID else SHADE) else EDGE
                // アーチの列を3段
                val band = i / 9
                val arcPhase = (deg / 9) % 2
                if (band < 3 && arcPhase == 0 && i % 9 in 2..6) c = EDGE
                b.set(cx + x, y, c)
            }
        }
        // 上端の縁
        for (deg in 0 until 360) {
            val a = Math.toRadians(deg.toDouble())
            val x = (Math.cos(a) * rx).toInt()
            val y = baseY - 12 + (Math.sin(a) * ry).toInt() - wallH
            b.set(cx + x, y, LIT)
        }
        b.platform(cx, baseY - BASE_H / 2 + 2, W - 4, 2, MID)
        return b.toSprite()
    }

    /** 自由の女神。台座と、松明を掲げた像。 */
    private fun statueOfLiberty(): Sprite {
        val h = 86
        val b = Buf(W, h)
        val cx = W / 2
        val baseY = h - 1
        b.platform(cx, baseY - BASE_H / 2 + 2, W - 10, 3, LIT)
        // 台座（下が広い）
        b.tower(cx, baseY - 12, 24, 30, 22)
        b.cap(cx, baseY - 36, 22, LIT)
        // 体
        b.tower(cx, baseY - 36, 26, 14, 10)
        // 頭
        b.cap(cx, baseY - 64, 10, MID)
        for (y in baseY - 66..baseY - 62) for (x in -4..4) {
            b.set(cx + x, y, if (x < 0) MID else SHADE)
        }
        // 冠
        for (x in -6..6 step 3) b.set(cx + x, baseY - 68, EDGE)
        // 掲げた腕と松明
        for (i in 0 until 16) b.set(cx + 6 + i / 3, baseY - 60 - i, MID)
        b.cap(cx + 11, baseY - 78, 8, LIT)
        for (y in baseY - 82..baseY - 78) b.set(cx + 11, y, EDGE)
        return b.toSprite()
    }

    /** ビッグ・ベン。細長い時計塔。 */
    private fun bigBen(): Sprite {
        val h = 96
        val b = Buf(W, h)
        val cx = W / 2
        val baseY = h - 1
        b.platform(cx, baseY - BASE_H / 2 + 2, W - 16, 3, LIT)
        // 塔本体。窓の縦筋を入れる。
        b.tower(cx, baseY - 10, 58, 22, 20) { u, v, _ ->
            if (v < 0.8f && u in 0.25f..0.75f && ((v * 26).toInt() % 3) == 0) LIT else null
        }
        // 時計の面
        val clockY = baseY - 70
        b.cap(cx, clockY + 6, 20, MID)
        for (y in clockY..clockY + 12) for (x in -8..8) {
            val d = (x * x) / 64f + ((y - clockY - 6) * (y - clockY - 6)) / 36f
            if (d <= 1f) b.set(cx + x, y, if (d > 0.75f) EDGE else LIT)
        }
        // 針
        b.set(cx, clockY + 6, EDGE)
        for (i in 1..4) b.set(cx, clockY + 6 - i, EDGE)
        for (i in 1..3) b.set(cx + i, clockY + 6, EDGE)
        // 尖塔
        b.tower(cx, clockY - 2, 18, 16, 2)
        for (y in clockY - 24..clockY - 18) b.set(cx, y, EDGE)
        return b.toSprite()
    }

    /** ピサの斜塔。円柱が傾いている。 */
    private fun leaningTower(): Sprite {
        val h = 84
        val b = Buf(W, h)
        val cx = W / 2 - 6
        val baseY = h - 1
        b.platform(W / 2, baseY - BASE_H / 2 + 2, W - 18, 3, LIT)
        // 8段の円柱。段ごとに少しずつ右へずらして傾ける。
        val floors = 8
        for (f in 0 until floors) {
            val fy = baseY - 8 - f * 8
            val lean = (f * 1.6f).toInt()
            val r = 11 - f / 4
            for (y in fy - 7..fy) {
                for (x in -r..r) {
                    var c = if (x < 0) MID else SHADE
                    if (abs(x) >= r) c = EDGE
                    // 柱の列
                    if (y < fy - 1 && (x + r) % 3 == 0) c = EDGE
                    b.set(cx + lean + x, y, c)
                }
            }
            // 各段の張り出し
            for (x in -r - 1..r + 1) b.set(cx + lean + x, fy - 7, LIT)
        }
        return b.toSprite()
    }

    /** タージ・マハル。中央のドームと4本の尖塔。 */
    private fun tajMahal(): Sprite {
        val h = 72
        val b = Buf(W, h)
        val cx = W / 2
        val baseY = h - 1
        b.platform(cx, baseY - BASE_H / 2 + 2, W - 2, 3, LIT)
        // 本体
        b.tower(cx, baseY - 14, 22, 38, 36)
        b.cap(cx, baseY - 36, 36, LIT)
        // 中央ドーム
        val domeY = baseY - 40
        for (y in 0..16) {
            val r = (Math.sqrt((1.0 - (y / 16.0) * (y / 16.0))) * 13).toInt()
            for (x in -r..r) {
                b.set(cx + x, domeY - y, if (x < 0) LIT else MID)
            }
            b.set(cx - r, domeY - y, EDGE); b.set(cx + r, domeY - y, EDGE)
        }
        for (y in domeY - 22..domeY - 17) b.set(cx, y, EDGE)
        // 4本のミナレット
        for (d in intArrayOf(-26, -20, 20, 26)) {
            if (abs(d) == 20) continue
            b.tower(cx + d, baseY - 14, 34, 6, 5)
            b.cap(cx + d, baseY - 48, 8, LIT)
            for (y in baseY - 54..baseY - 50) b.set(cx + d, y, EDGE)
        }
        // アーチの入口
        b.arch(cx, baseY - 16, 14, 16)
        return b.toSprite()
    }

    /** ギザのピラミッド。三角と砂。 */
    private fun pyramid(): Sprite {
        val h = 60
        val b = Buf(W, h)
        val cx = W / 2
        val baseY = h - 1
        b.platform(cx, baseY - BASE_H / 2 + 2, W - 2, 2, 1)
        // 大きな四角錐。左の面を明るく、右の面を暗く。
        val apexY = baseY - 48
        for (y in apexY..baseY - 8) {
            val v = (y - apexY).toFloat() / (baseY - 8 - apexY)
            val half = (v * 28).toInt()
            for (x in -half..half) {
                // 稜線から左右で塗り分ける
                val c = when {
                    abs(x) >= half - 1 -> EDGE
                    x < 0 -> LIT
                    else -> SHADE
                }
                b.set(cx + x, y, c)
            }
            // 中央の稜線
            b.set(cx, y, MID)
        }
        // 奥に小さいピラミッドを2つ
        for ((ox, sc) in listOf(-24 to 14, 22 to 11)) {
            val ay = baseY - 14 - sc
            for (y in ay..baseY - 12) {
                val v = (y - ay).toFloat() / (baseY - 12 - ay).coerceAtLeast(1)
                val half = (v * sc).toInt()
                for (x in -half..half) {
                    b.set(cx + ox + x, y, if (x < 0) MID else SHADE)
                }
            }
        }
        return b.toSprite()
    }

    private val cache = HashMap<Monument, Sprite>()

    fun of(monument: Monument): Sprite = cache.getOrPut(monument) {
        when (monument) {
            Monument.TOKYO_TOWER -> tokyoTower()
            Monument.ARC_DE_TRIOMPHE -> arcDeTriomphe()
            Monument.COLOSSEUM -> colosseum()
            Monument.STATUE_OF_LIBERTY -> statueOfLiberty()
            Monument.BIG_BEN -> bigBen()
            Monument.LEANING_TOWER -> leaningTower()
            Monument.TAJ_MAHAL -> tajMahal()
            Monument.PYRAMID -> pyramid()
        }
    }
}
