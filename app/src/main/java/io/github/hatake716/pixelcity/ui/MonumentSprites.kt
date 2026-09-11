package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.Monument
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 世界の有名建築。クォータービューの 2×2 タイルを占める。
 *
 * 実在の建造物の「形の特徴」をドット絵に起こしたもので、
 * 写真・図面・第三者の素材は使用していない。すべてこのファイルのコードで描いている。
 *
 * ## 原寸で描く（v5）
 *
 * 以前は 64 ドットで形を決めてから4倍に引き伸ばしていた。そのため最小の粒が
 * 4×4 の塊になり、柱・窓・装飾といった細部がそもそも描けなかった。
 * いまは最終の大きさ（[W] = 256）のまま描いている。
 *
 * 細部を出すために、次の道具を用意している。
 *  - [column]    円柱。丸みを階調で出す
 *  - [colonnade] 柱の列（コロッセオ・ピサの斜塔）
 *  - [dome]      たまねぎ形にもできるドーム
 *  - [archway]   縁と奥行きのあるアーチ（ただ穴を開けるのではない）
 *  - [lattice]   鉄骨の格子（東京タワー）
 *  - [entablature] 軒。水平の帯で建築らしい区切りを作る
 */
object MonumentSprites {

    /** 2×2 タイルぶんの幅。形もこの大きさのまま決める。 */
    const val W = Iso.TILE_W * 2

    /** 底面の菱形の高さ。2タイルぶんの奥行き。 */
    private const val BASE_H = Iso.TILE_H * 2

    // ------------------------------------------------------------------
    // 素材
    // ------------------------------------------------------------------

    /**
     * 素材の階調。明るい順に並べる。
     * 曲面は [at] に「光の当たり具合 0..1」を渡して段を選ぶ。
     */
    private class Ramp(vararg val steps: Int) {
        val size get() = steps.size

        /** [t] は 0 が最も暗く、1 が最も明るい。 */
        fun at(t: Float): Int {
            val i = ((1f - t.coerceIn(0f, 1f)) * (size - 1)).roundToInt()
            return steps[i.coerceIn(0, size - 1)]
        }

        val hi get() = steps[0]
        val lit get() = steps[if (size > 1) 1 else 0]
        val mid get() = steps[size / 2]
        val dark get() = steps[(size - 2).coerceAtLeast(0)]
        val edge get() = steps[size - 1]
    }

    private val MARBLE = Ramp(
        Palette.MARBLE_HI, Palette.MARBLE_LIT, Palette.MARBLE,
        Palette.MARBLE_MID, Palette.MARBLE_DARK, Palette.MARBLE_EDGE,
    )
    private val PATINA = Ramp(
        Palette.PATINA_HI, Palette.PATINA_LIT, Palette.PATINA,
        Palette.PATINA_DARK, Palette.PATINA_EDGE,
    )
    private val TRAVERTINE = Ramp(
        Palette.TRAVERTINE_HI, Palette.TRAVERTINE_LIT, Palette.TRAVERTINE,
        Palette.TRAVERTINE_DARK, Palette.TRAVERTINE_EDGE,
    )
    /**
     * ピラミッドの石灰岩。
     *
     * 斜面が広いので、5段では面が板に見えてしまう。
     * 砂と石の色を混ぜて8段にし、なだらかにつなぐ。
     */
    private val LIMESTONE = Ramp(
        Palette.SAND_LIT, Palette.LIMESTONE_HI, Palette.LIMESTONE_LIT,
        Palette.SAND, Palette.LIMESTONE, Palette.LIMESTONE_DARK,
        Palette.SAND_DARK, Palette.LIMESTONE_EDGE,
    )
    private val LONDON = Ramp(
        Palette.LONDON_LIT, Palette.LONDON, Palette.LONDON_DARK, Palette.LONDON_EDGE,
    )
    private val IRON = Ramp(
        Palette.TOWER_ORANGE_LIT, Palette.TOWER_ORANGE, Palette.TOWER_ORANGE_DARK,
    )
    private val GILT = Ramp(Palette.GILT_LIT, Palette.GILT, Palette.GILT_DARK)
    private val GROUND = Ramp(
        Palette.STONE_LIT, Palette.STONE, Palette.STONE_DARK, Palette.STONE_EDGE,
    )

    // ------------------------------------------------------------------
    // 描く先
    // ------------------------------------------------------------------

    private class Buf(val w: Int, val h: Int) {
        val data = ByteArray(w * h) { Pix.TRANSPARENT }

        fun set(x: Int, y: Int, v: Int) {
            if (x in 0 until w && y in 0 until h) data[y * w + x] = v.toByte()
        }

        fun get(x: Int, y: Int): Int =
            if (x in 0 until w && y in 0 until h) data[y * w + x].toInt() else Pix.TRANSPARENT.toInt()

        fun isInk(x: Int, y: Int): Boolean = get(x, y) != Pix.TRANSPARENT.toInt()

        /** すでに何か描いてあるところだけ塗り替える。装飾を building の上に乗せるとき用。 */
        fun over(x: Int, y: Int, v: Int) {
            if (isInk(x, y)) set(x, y, v)
        }

        fun clear(x: Int, y: Int) {
            if (x in 0 until w && y in 0 until h) data[y * w + x] = Pix.TRANSPARENT
        }

        fun toSprite() = Sprite(w, h, data)
    }

    // ------------------------------------------------------------------
    // 部品
    // ------------------------------------------------------------------

    /**
     * 菱形の台。クォータービューの地面に接する部分。
     * [step] を増やすと、段のある基壇になる。
     */
    private fun Buf.platform(
        cx: Int, baseY: Int, width: Int, thickness: Int, r: Ramp, steps: Int = 1,
    ) {
        // 下の段から順に積む。段ごとに一回り小さくして、階段状の基壇にする。
        for (s in 0 until steps) {
            val ww = width - s * (width / 9)
            val hh = ww / 2
            // 段の上面の中心。上の段ほど高い位置に来る。
            val top = baseY - s * thickness
            // 側面を先に、上面をあとに描く（上面が手前に来るため）
            for (t in thickness downTo 1) {
                for (y in 0 until hh) for (x in 0 until ww) {
                    val dx = (x + 0.5f) - ww / 2f
                    val dy = (y + 0.5f) - hh / 2f
                    if (abs(dx) / (ww / 2f) + abs(dy) / (hh / 2f) > 1f) continue
                    // 側面が見えるのは菱形の下半分だけ
                    if (dy < 0) continue
                    set(cx - ww / 2 + x, top - hh / 2 + y + t, if (dx < 0) r.mid else r.dark)
                }
            }
            for (y in 0 until hh) for (x in 0 until ww) {
                val dx = (x + 0.5f) - ww / 2f
                val dy = (y + 0.5f) - hh / 2f
                val d = abs(dx) / (ww / 2f) + abs(dy) / (hh / 2f)
                if (d > 1f) continue
                set(cx - ww / 2 + x, top - hh / 2 + y, if (d > 0.94f) r.lit else r.hi)
            }
        }
    }

    /**
     * 四角い建物の胴。左右の面と、正面の中央に稜線を持つ。
     * [decor] に (u, v, side) を渡して、窓や帯を描き込める。
     * u は面の左右 0..1、v は下 0 から上 1。
     */
    private fun Buf.block(
        cx: Int, baseY: Int, h: Int, w: Int, topW: Int = w, r: Ramp,
        decor: (x: Int, y: Int, u: Float, v: Float) -> Int? = { _, _, _, _ -> null },
    ) {
        for (i in 0 until h) {
            val v = if (h <= 1) 0f else i.toFloat() / (h - 1)
            val ww = (w + (topW - w) * v).roundToInt().coerceAtLeast(2)
            val y = baseY - i
            val half = ww / 2
            for (x in -half..half) {
                val u = if (ww <= 1) 0.5f else (x + half).toFloat() / ww
                // 左の面を明るく、右の面を暗く。角は最も暗い。
                var c = when {
                    abs(x) >= half -> r.edge
                    x < -half / 6 -> r.lit
                    x < half / 6 -> r.mid          // 稜線のあたり
                    else -> r.dark
                }
                decor(cx + x, y, u, v)?.let { c = it }
                set(cx + x, y, c)
            }
        }
    }

    /**
     * クォータービューの箱。
     *
     * 平面を「奥行き [dw] × 幅 [dd] タイル」の菱形として捉え、
     * 左の面・右の面・上の面の3つを描く。手前の角が [cx] に来る。
     *
     * 屋根が菱形に見えるのは、この3面で組み立てたときだけ。
     * 正面だけを四角く塗って上に菱形を乗せても、切妻屋根にしか見えない。
     */
    private fun Buf.isoBox(
        cx: Int, baseY: Int, halfW: Int, height: Int, r: Ramp,
        /** 手前の角から左右へ下がる深さ。菱形の高さの半分。 */
        depth: Int = halfW / 2,
        left: (x: Int, y: Int, u: Float, v: Float) -> Int? = { _, _, _, _ -> null },
        right: (x: Int, y: Int, u: Float, v: Float) -> Int? = { _, _, _, _ -> null },
        top: Boolean = true,
    ) {
        /** その x での、手前の輪郭線の y。菱形の下半分をなぞる。 */
        fun frontY(x: Int): Int =
            baseY - (depth - depth * abs(x).toFloat() / halfW).roundToInt()

        // 左右の壁
        for (x in -halfW..halfW) {
            val fy = frontY(x)
            val u = abs(x).toFloat() / halfW
            for (i in 0 until height) {
                val y = fy - i
                val v = i.toFloat() / height
                val isLeft = x < 0
                var c = if (isLeft) r.lit else r.mid
                if (abs(x) >= halfW - 1) c = r.dark       // 左右の端
                if (abs(x) <= 1) c = r.dark               // 手前の角
                val hook = if (isLeft) left else right
                hook(cx + x, y, u, v)?.let { c = it }
                set(cx + x, y, c)
            }
        }
        if (!top) return
        // 上面。壁の上端がそのまま菱形の手前2辺になる。
        // 手前の角は (0, baseY - height)、そこから奥へ depth ぶん開く。
        val topCy = baseY - height - depth
        for (x in -halfW..halfW) {
            // この x での手前の縁（壁の上端）
            val nearY = frontY(x) - height
            // 菱形の奥の縁
            val farY = topCy - (depth - depth * abs(x) / halfW)
            for (y in farY..nearY) {
                set(cx + x, y, if (y < topCy) r.hi else r.lit)
            }
        }
    }

    /**
     * 円柱。中心が明るく、縁に向かって暗くなる。
     * [flute] を true にすると、縦の溝（フルーティング）を刻む。
     */
    private fun Buf.column(
        cx: Int, baseY: Int, h: Int, radius: Int, r: Ramp, flute: Boolean = false,
    ) {
        for (y in baseY - h + 1..baseY) {
            for (x in -radius..radius) {
                val u = x.toFloat() / radius            // -1..1
                // 円筒の法線。光は左上から。
                var t = sqrt((1f - u * u).coerceAtLeast(0f)) * (0.62f - u * 0.42f)
                if (flute && radius >= 3) {
                    // 溝を数本。明暗を交互にして丸みを強調する。
                    val f = ((x + radius) * 3f / radius).toInt() % 2
                    if (f == 1) t -= 0.16f
                }
                set(cx + x, y, r.at(t))
            }
        }
    }

    /**
     * 柱の列。[count] 本の円柱を [from]..[to] の幅に並べ、
     * 柱と柱のあいだは奥の闇にする。
     */
    private fun Buf.colonnade(
        cx: Int, baseY: Int, h: Int, halfWidth: Int, count: Int, r: Ramp,
        radius: Int = 2,
    ) {
        // まず奥の闇で埋める
        for (y in baseY - h + 1..baseY) for (x in -halfWidth..halfWidth) {
            set(cx + x, y, Palette.OPENING)
        }
        for (i in 0 until count) {
            val t = if (count <= 1) 0.5f else i.toFloat() / (count - 1)
            val x = (-halfWidth + t * halfWidth * 2).roundToInt()
            // 端の柱ほど、こちらを向いていないので細く見える
            val rr = (radius * (0.55f + 0.45f * sin(Math.PI * t).toFloat())).roundToInt()
                .coerceAtLeast(1)
            column(cx + x, baseY, h, rr, r)
        }
    }

    /**
     * ドーム。[onion] を上げるとたまねぎ形（タージ・マハル）になる。
     * 光は左上から当て、下ほど暗くする。
     */
    private fun Buf.dome(
        cx: Int, baseY: Int, radius: Int, height: Int, r: Ramp, onion: Float = 0f,
    ) {
        for (i in 0..height) {
            val v = i.toFloat() / height              // 0=下 1=頂上
            // 半円を基本に、たまねぎ形は下で一度ふくらむ
            // 半球を基本に、たまねぎ形は下でいったんふくらみ、
            // 上で細く絞ってから閉じる。
            var rr = sqrt((1f - v * v).coerceAtLeast(0f))
            if (onion > 0f) {
                val bulge = sin(Math.PI * (0.22f + v * 0.78f)).toFloat() * (1f - v * v).pow(0.42f)
                rr = rr * (1f - onion) + onion * bulge * 1.34f
            }
            val half = (rr * radius).roundToInt()
            val y = baseY - i
            for (x in -half..half) {
                val u = if (half == 0) 0f else x.toFloat() / half
                // 球の陰影。左上が明るい。
                val nz = sqrt((1f - u * u).coerceAtLeast(0f))
                var t = (-u * 0.52f + v * 0.30f + nz * 0.46f).coerceIn(0f, 1f)
                if (abs(x) >= half) t -= 0.30f
                set(cx + x, y, r.at(t))
            }
        }
    }

    /**
     * アーチの開口。穴を開けるだけでなく、奥に引っ込んだ面と縁を描く。
     *
     * 下端は [baseYAt] で x ごとに決める。クォータービューの壁は
     * 下端が斜めなので、それに沿わせないと開口が壁から浮いてしまう。
     * [depth] は見込みの厚み。左から見込む形に描く。
     */
    private fun Buf.archway(
        cx: Int, w: Int, h: Int, r: Ramp, depth: Int = 2,
        baseYAt: (Int) -> Int,
    ) {
        val half = w / 2
        val springing = h - half        // 半円が始まる高さ

        /** 開口の上端。柱の部分は垂直、そこから上は半円。 */
        fun topOffset(x: Int): Int {
            val a = abs(x)
            if (a > half) return -1
            val t = a.toFloat() / half
            return springing + (half * sqrt((1f - t * t).coerceAtLeast(0f))).roundToInt()
        }

        for (x in -half..half) {
            val up = topOffset(x)
            if (up < 0) continue
            val by = baseYAt(cx + x)
            for (i in 0..up) {
                // 奥ほど暗い。上に向かって少しだけ明るさを落とす。
                set(cx + x, by - i, Palette.OPENING)
            }
        }
        // 見込み（開口の厚み）。左の内側の面が見える。
        for (x in -half..(-half + depth)) {
            val up = topOffset(x)
            if (up < 0) continue
            val by = baseYAt(cx + x)
            for (i in 0..up) set(cx + x, by - i, r.edge)
        }
        // 迫石（アーチの縁取り）
        for (x in -half - 1..half + 1) {
            val up = topOffset(x.coerceIn(-half, half))
            if (up < 0) continue
            val by = baseYAt(cx + x)
            if (abs(x) > half) {
                for (i in 0..up) set(cx + x, by - i, r.dark)
            } else {
                set(cx + x, by - up - 1, r.hi)
                set(cx + x, by - up - 2, r.lit)
            }
        }
    }

    /**
     * 軒（エンタブレチュア）。建物を水平に区切る帯。
     * 上端を明るく、下に影を落とすと、石が積まれた感じになる。
     */
    private fun Buf.entablature(
        cx: Int, y: Int, halfWidth: Int, thickness: Int, r: Ramp, overhang: Int = 2,
    ) {
        for (t in 0 until thickness) {
            val hw = halfWidth + if (t < thickness - 1) overhang else 0
            for (x in -hw..hw) {
                val c = when {
                    t == 0 -> r.hi
                    t == thickness - 1 -> r.edge
                    x < 0 -> r.lit
                    else -> r.mid
                }
                set(cx + x, y + t, c)
            }
        }
    }

    /**
     * 鉄骨の格子。東京タワーの脚のあいだに入る筋交い。
     * 一定の間隔で×の形を描く。
     */
    private fun Buf.lattice(
        cx: Int, top: Int, bottom: Int, halfAt: (Int) -> Int, r: Ramp, pitch: Int,
    ) {
        var y = bottom
        while (y > top) {
            val next = (y - pitch).coerceAtLeast(top)
            val h0 = halfAt(y)
            val h1 = halfAt(next)
            val steps = (y - next).coerceAtLeast(1)
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val yy = y - i
                val hw0 = (h0 + (h1 - h0) * t)
                // 右下がりと右上がりの2本
                val a = (-hw0 + 2 * hw0 * t).roundToInt()
                val b = (hw0 - 2 * hw0 * t).roundToInt()
                set(cx + a, yy, r.mid)
                set(cx + b, yy, r.dark)
            }
            // 水平の梁
            val hw = halfAt(next)
            for (x in -hw..hw) set(cx + x, next, r.lit)
            y = next
        }
    }

    /**
     * 地面に落ちる影。足元に菱形を薄く敷いて、建物を地面に固定して見せる。
     *
     * 菱形の高さは幅の半分ではなく [flat] 倍にして、地面に貼りついた
     * 平たい影にする。半分にすると、建物の中ほどまで影が伸びてしまう。
     */
    private fun Buf.groundShadow(cx: Int, baseY: Int, width: Int, flat: Float = 0.5f) {
        val hh = (width * flat / 2f).roundToInt().coerceAtLeast(2)
        for (y in -hh..hh) for (x in -width / 2..width / 2) {
            val dx = x.toFloat()
            val dy = y.toFloat()
            if (abs(dx) / (width / 2f) + abs(dy) / hh > 1f) continue
            val px = cx + x
            val py = baseY + y
            if (!isInk(px, py)) set(px, py, Palette.SHADOW)
        }
    }

    // ------------------------------------------------------------------
    // 東京タワー
    // ------------------------------------------------------------------

    /**
     * 1958年、港区芝公園。高さ333m の鉄塔。
     *
     * 形の決め手は、下へ向かってふくらむ脚の曲線（双曲線に近い）と、
     * 中ほどの大展望台、その上の特別展望台、そして先端のアンテナ。
     * 塗りは朱色と白の帯（航空障害標識）。
     */
    private fun tokyoTower(): Sprite {
        val h = 300
        val b = Buf(W, h)
        val cx = W / 2
        val baseY = h - 1 - BASE_H / 4

        b.groundShadow(cx + 6, baseY + 4, 150)
        b.platform(cx, baseY, 132, 5, GROUND, steps = 2)

        val footY = baseY - 10          // 脚の接地
        val topY = 58                   // 鉄塔の頂
        val spread = 52f                // 接地したときの広がり

        // 脚の広がり。下ほど急に開く（双曲線に近い形）
        fun halfAt(y: Int): Int {
            val t = ((footY - y).toFloat() / (footY - topY)).coerceIn(0f, 1f)
            return (spread * (1f - t).pow(1.7f) + 5f).roundToInt()
        }

        // 筋交い
        b.lattice(cx, topY, footY, ::halfAt, IRON, pitch = 13)

        // 4本の脚。手前の2本を太く、奥の2本を細く描いて奥行きを出す。
        for (y in topY..footY) {
            val hw = halfAt(y)
            // 手前（左右）の脚
            for (d in intArrayOf(-hw, hw)) {
                val w = if (y > footY - 60) 4 else 3
                for (i in 0 until w) {
                    val x = if (d < 0) d + i else d - i
                    b.set(cx + x, y, if (d < 0) IRON.lit else IRON.mid)
                }
                b.set(cx + d, y, IRON.dark)
            }
            // 奥の2本（中央寄りに見える）
            val inner = (hw * 0.34f).roundToInt()
            for (d in intArrayOf(-inner, inner)) {
                b.set(cx + d, y - 6, IRON.dark)
                b.set(cx + d + 1, y - 6, IRON.mid)
            }
        }

        // 航空障害標識の白い帯。脚の部分だけを白く塗り替える。
        for (band in 0 until 7) {
            val y0 = footY - 22 - band * 34
            if (y0 < topY) break
            if (band % 2 == 0) continue
            for (y in y0 until y0 + 9) {
                val hw = halfAt(y)
                for (d in intArrayOf(-hw, hw)) {
                    for (i in 0..4) {
                        val x = if (d < 0) d + i else d - i
                        b.over(cx + x, y, if (d < 0) Palette.TOWER_WHITE else Palette.STONE_LIT)
                    }
                }
            }
        }

        // 大展望台（2層）。四角い箱と、その下の支え。
        val deckY = footY - 150
        b.entablature(cx, deckY, 40, 4, IRON, overhang = 4)
        for (y in deckY + 4 until deckY + 26) {
            val v = (y - deckY - 4).toFloat() / 22f
            val hw = (40 - v * 6).roundToInt()
            for (x in -hw..hw) {
                var c = if (x < 0) Palette.TOWER_WHITE else Palette.STONE_LIT
                if (abs(x) >= hw - 1) c = IRON.dark
                // 展望台の窓
                if ((y - deckY) % 8 in 3..6 && (x + hw) % 7 in 1..4) c = Palette.WINDOW_LIT
                b.set(cx + x, y, c)
            }
        }
        b.entablature(cx, deckY + 26, 42, 3, IRON, overhang = 3)

        // 特別展望台（小さい方）
        val topDeckY = footY - 236
        b.entablature(cx, topDeckY, 17, 3, IRON, overhang = 3)
        for (y in topDeckY + 3 until topDeckY + 15) {
            for (x in -16..16) {
                var c = if (x < 0) Palette.TOWER_WHITE else Palette.STONE_LIT
                if (abs(x) >= 15) c = IRON.dark
                if ((y - topDeckY) % 6 in 2..4 && (x + 16) % 6 in 1..3) c = Palette.WINDOW_LIT
                b.set(cx + x, y, c)
            }
        }
        b.entablature(cx, topDeckY + 15, 18, 3, IRON, overhang = 2)

        // アンテナ。先端は細く、赤い灯をともす。
        for (y in 10 until topY) {
            val t = (y - 10).toFloat() / (topY - 10)
            val w = (t * 3).roundToInt()
            for (x in -w..w) b.set(cx + x, y, if (x < 0) IRON.mid else IRON.dark)
        }
        for (y in 4 until 10) b.set(cx, y, Palette.METAL)
        b.set(cx, 3, Palette.RED)
        b.set(cx, 2, Palette.RED)
        return b.toSprite()
    }


    // ------------------------------------------------------------------
    // 凱旋門
    // ------------------------------------------------------------------

    /**
     * パリ、シャルル・ド・ゴール広場。1836年完成。
     *
     * 形の決め手は、ほぼ正方形の輪郭（幅50m・高さ50m）に穿たれた
     * 大きな半円アーチ、四隅の浮き彫り、そして上部の帯（アティック）。
     * 側面にも小さなアーチが抜けている。
     */
    private fun arcDeTriomphe(): Sprite {
        val h = 250
        val b = Buf(W, h)
        val cx = W / 2
        val baseY = h - 1 - BASE_H / 4

        b.groundShadow(cx + 5, baseY + 3, 156)
        b.platform(cx, baseY, 156, 4, GROUND, steps = 2)

        val footY = baseY - 12
        val halfW = 64
        val depth = 30
        val bodyH = 132

        // 本体。左右の壁と屋上の菱形を、等角の箱として組む。
        b.isoBox(cx, footY, halfW, bodyH, TRAVERTINE, depth)

        /** その x での、手前の輪郭線の y。isoBox と同じ形。 */
        fun frontY(x: Int): Int =
            footY - (depth - depth * abs(x).toFloat() / halfW).roundToInt()

        val roofY = footY - bodyH - depth

        // アティック（上部の帯）。屋上のすぐ下を水平に巻く。
        for (x in -halfW..halfW) {
            val top = frontY(x) - bodyH
            for (i in 0 until 30) {
                val y = top + i
                val c = when {
                    i < 3 -> TRAVERTINE.hi          // 上の繰形
                    i > 26 -> TRAVERTINE.edge       // 下の影
                    x < 0 -> TRAVERTINE.lit
                    else -> TRAVERTINE.mid
                }
                b.over(cx + x, y, c)
            }
            // 区画の縦筋
            if ((x + 200) % 13 == 0) {
                for (i in 5..25) b.over(cx + x, top + i, TRAVERTINE.dark)
                for (i in 5..25) b.over(cx + x + 1, top + i, TRAVERTINE.hi)
            }
        }

        // 左の面（正面）に大アーチ。実物は縦に長い（高さが幅の2倍以上）。
        // 壁の下端が斜めなので、開口もそれに沿わせる。
        b.archway(cx - 36, 40, 100, TRAVERTINE, depth = 5) { x -> frontY(x - cx) }
        // 右の面（側面）にも抜ける小アーチ
        b.archway(cx + 40, 26, 54, TRAVERTINE, depth = 4) { x -> frontY(x - cx) }

        // 正面の彫刻群。大アーチの左右に残った壁面へ置く。
        for (ox in intArrayOf(-60, -18)) {
            val y0 = frontY(ox) - 86
            val ow = 14
            for (y in y0..y0 + 40) for (x in ox until ox + ow) {
                val edge = y == y0 || y == y0 + 40 || x == ox || x == ox + ow - 1
                b.over(cx + x, y, if (edge) TRAVERTINE.edge else TRAVERTINE.mid)
            }
            // 人の立ち姿を思わせる陰影を2体
            for (i in 0 until 2) {
                val fx = ox + 3 + i * 6
                for (yy in 0 until 4) for (xx in 0 until 3) {
                    b.over(cx + fx + xx, y0 + 6 + yy, TRAVERTINE.hi)
                }
                for (y in y0 + 10..y0 + 35) {
                    b.over(cx + fx, y, TRAVERTINE.lit)
                    b.over(cx + fx + 1, y, TRAVERTINE.hi)
                    b.over(cx + fx + 2, y, TRAVERTINE.dark)
                }
            }
        }

        // 大アーチ上のスパンドレル（円い浮き彫り）
        for (d in intArrayOf(-56, -16)) {
            for (yy in -7..7) for (xx in -7..7) {
                if (xx * xx + yy * yy > 49) continue
                val t = (-xx * 0.32f - yy * 0.32f + 4.8f) / 9.6f
                b.over(cx + d + xx, frontY(d) - 108 + yy, TRAVERTINE.at(t))
            }
        }
        return b.toSprite()
    }

    // ------------------------------------------------------------------
    // コロッセオ
    // ------------------------------------------------------------------

    /**
     * ローマ、80年完成の円形闘技場。
     *
     * 形の決め手は、楕円の外壁に3段重なったアーチの列と、
     * その上の壁（アッティコ）。そして北側だけが残り、
     * 南側は崩れているという、あの非対称な姿。
     */
    private fun colosseum(): Sprite {
        val h = 190
        val b = Buf(W, h)
        val cx = W / 2
        val baseY = h - 1 - BASE_H / 4


        val footY = baseY - 8
        val rx = 104f            // 楕円の横半径
        val ry = 54f             // 奥行きの半径（クォータービューで潰れる）
        val wallH = 96           // 外壁の高さ

        /**
         * 崩れ具合。実物は半周だけが4層のまま高く残り、
         * 残りは2層まで崩れている。0 = そのまま、1 = 最も崩れている。
         */
        fun ruin(deg: Int): Float {
            // 手前の左（120度あたり）を中心に崩す。
            // ここを低くすると、欠けたところから内部の観客席がのぞく。
            val d = ((deg - 122 + 540) % 360) - 180
            return (1f - abs(d) / 46f).coerceIn(0f, 1f).pow(1.5f)
        }

        /** 外壁の高さ。 */
        fun wallAt(deg: Int): Int = (wallH * (1f - 0.44f * ruin(deg))).roundToInt()

        /** 角度 [deg] の外壁の、足元の y。 */
        fun footAt(deg: Int): Int =
            footY + (sin(Math.toRadians(deg.toDouble())).toFloat() * ry).roundToInt()

        /**
         * その x で、手前の外壁の上端。内部はこれより上には見えない。
         * 手前側（sin>0）で同じ x を通る角度を逆算して求める。
         */
        fun visibleTopAt(x: Int): Int {
            val u = (x / rx).toDouble().coerceIn(-1.0, 1.0)
            val deg = Math.toDegrees(Math.acos(u)).roundToInt().coerceIn(0, 180)
            return footAt(deg) - wallAt(deg)
        }


        // --- 1. 奥の外壁の上端 ---
        //
        // 輪の向こう側の壁。全部描くと画面を覆ってしまうので、
        // 内側の面の「上のほう」だけを帯として描く。
        // これが輪の向こうの縁になり、輪が閉じて見える。
        for (x in -rx.toInt()..rx.toInt()) {
            val u = (x / rx).toDouble().coerceIn(-1.0, 1.0)
            val backDeg = (360 - Math.toDegrees(Math.acos(u))).roundToInt().coerceIn(180, 359)
            val yb = footAt(backDeg)
            val top = yb - wallAt(backDeg)
            for (i in 0 until 10) {
                val y = top + i
                if (y > yb) break
                // 上端は明るく、下るほど日陰
                val t = if (i < 2) 0.46f else 0.28f - i * 0.010f
                b.set(cx + x, y, TRAVERTINE.at(t))
            }
        }

        // --- 2. 内側。観客席と闘技場 ---
        //
        // 輪の内側に、外から内へ下る観客席をつける。
        // 同心の楕円を描くのではなく、x ごとに縦へ塗るので隙間が開かない。
        val innerRx = rx * 0.52f
        val innerRy = ry * 0.52f
        /** 闘技場の床の高さ。観客席のぶんだけ、輪の足元より下げる。 */
        val arenaCy = footY + 10

        fun ellipseK(x: Int, radius: Float): Float {
            val u = x / radius
            return sqrt((1f - u * u).coerceAtLeast(0f))
        }

        for (x in -rx.toInt()..rx.toInt()) {
            val u = (x / rx).toDouble().coerceIn(-1.0, 1.0)
            val backDeg = (360 - Math.toDegrees(Math.acos(u))).roundToInt().coerceIn(180, 359)
            // 奥の壁の足元（観客席の上端）
            val seatTop = footAt(backDeg)
            val inArena = abs(x) <= innerRx
            val ik = if (inArena) ellipseK(x, innerRx) else 0f
            val arenaBack = arenaCy - (ik * innerRy).roundToInt()
            val arenaFront = arenaCy + (ik * innerRy).roundToInt()

            // 奥の観客席。壁の足元から闘技場のふちまで。
            for (y in seatTop..arenaBack) {
                // 段。数ドットごとに明暗を変えて、すり鉢に見せる。
                val step = ((y - seatTop) / 5) % 2
                b.set(cx + x, y, TRAVERTINE.at(if (step == 0) 0.34f else 0.26f))
            }
            // 闘技場の床
            if (inArena) {
                for (y in arenaBack..arenaFront) b.set(cx + x, y, Palette.SAND)
                b.set(cx + x, arenaBack, Palette.SAND_DARK)
            }
        }

        // --- 3. 手前の外壁。3段のアーケードと、最上段の壁 ---
        //
        // こちらも x で回す。角度で回すと、楕円の左右の端で
        // 同じ x に何度も描かれ、あいだに縦の隙間が残る。
        for (x in -rx.toInt()..rx.toInt()) {
            val u = (x / rx).toDouble().coerceIn(-1.0, 1.0)
            val deg = Math.toDegrees(Math.acos(u)).roundToInt().coerceIn(0, 180)
            val yb = footAt(deg)
            val hh = wallAt(deg)
            if (hh <= 3) continue
            // 面の向き。左を明るく、右を暗く。
            val facing = -u.toFloat() * 0.40f + 0.48f

            for (i in 0 until hh) {
                val y = yb - i
                val tier = when {
                    i < 26 -> 0
                    i < 50 -> 1
                    i < 72 -> 2
                    else -> 3
                }
                val inTier = i - intArrayOf(0, 26, 50, 72)[tier]
                var t = facing
                if (inTier < 4) t += 0.22f          // 各段の下の繰形
                var col = TRAVERTINE.at(t)

                if (tier < 3) {
                    // アーチの列。角度で数えると、円周に沿って等間隔に並ぶ。
                    val phase = deg % 9
                    if (phase in 2..7 && inTier in 5..22) {
                        val au = (phase - 4.5f) / 3.6f
                        val top = 5 + (16 * sqrt((1f - au * au).coerceAtLeast(0f))).roundToInt()
                        if (inTier <= top) {
                            col = if (inTier >= top - 1) TRAVERTINE.at(t - 0.40f)
                            else Palette.OPENING
                        }
                    }
                } else {
                    // 最上段は壁。付け柱と、そのあいだの四角い窓。
                    val phase = deg % 9
                    if (phase == 0) t += 0.18f
                    if (phase in 3..6 && inTier in 4..13) t -= 0.34f
                    col = TRAVERTINE.at(t)
                }
                b.set(cx + x, y, col)
            }
            // 上端。崩れているところは欠けさせる。
            val jag = if (ruin(deg) > 0.04f) (deg * 7 % 4) else 0
            b.set(cx + x, yb - hh + jag, TRAVERTINE.hi)
            b.set(cx + x, yb - hh + jag + 1, TRAVERTINE.lit)
        }

        // --- 4. 足元の石畳と影 ---
        // 輪の外側にだけ敷く。内側に敷くと、闘技場の砂を塗りつぶしてしまう。
        for (deg in 0 until 180) {
            val a = Math.toRadians(deg.toDouble()).toFloat()
            val yb = footAt(deg)
            val x = (cos(a) * rx).roundToInt()
            // 石畳
            for (dy in 1..6) {
                val xx = cx + x + (dy * 0.5f).roundToInt()
                if (!b.isInk(xx, yb + dy)) b.set(xx, yb + dy, GROUND.mid)
            }
            // その下に影
            for (dy in 7..10) {
                val xx = cx + x + (dy * 0.5f).roundToInt()
                if (!b.isInk(xx, yb + dy)) b.set(xx, yb + dy, Palette.SHADOW)
            }
        }
        return b.toSprite()
    }

    // ------------------------------------------------------------------
    // 自由の女神
    // ------------------------------------------------------------------

    /**
     * ニューヨーク、リバティ島。1886年。
     *
     * 形の決め手は、星形の台座、そこに立つ銅像、
     * 右手に高く掲げた松明、左手に抱えた銘板、そして7本の光条を持つ冠。
     * 銅が酸化した緑青の色。
     */
    private fun statueOfLiberty(): Sprite {
        val h = 368
        val b = Buf(W, h)
        val cx = W / 2
        val baseY = h - 1 - BASE_H / 4

        b.groundShadow(cx + 6, baseY + 4, 150, flat = 0.46f)

        // --- 星形の稜堡（台座の土台） ---
        b.platform(cx, baseY, 148, 6, GROUND, steps = 2)

        // --- 台座。上に向かって細くなる石の柱 ---
        val pedFoot = baseY - 16
        val pedH = 84
        // 縦の溝と、上部の帯を、左右どちらの面にも入れる
        val pedDecor: (Int, Int, Float, Float) -> Int? = { x, _, _, v ->
            when {
                v > 0.86f -> MARBLE.hi
                v > 0.80f -> MARBLE.dark
                (x - cx + 44) % 11 == 0 && v in 0.12f..0.76f -> MARBLE.dark
                else -> null
            }
        }
        b.isoBox(cx, pedFoot, 44, pedH, MARBLE, depth = 20, left = pedDecor, right = pedDecor)
        // 台座の上の見晴らし台
        val pedTop = pedFoot - pedH
        b.entablature(cx, pedTop - 22, 40, 5, MARBLE, overhang = 5)
        // 台座上部のアーケード（小さな窓の列）
        for (x in -36..36 step 9) {
            for (y in pedTop - 16..pedTop - 4) {
                b.over(cx + x, y, Palette.OPENING)
                b.over(cx + x + 1, y, Palette.OPENING)
            }
        }

        // --- 像の足元の基壇 ---
        val statueFoot = pedTop - 26
        b.entablature(cx, statueFoot - 4, 22, 5, PATINA, overhang = 3)

        // --- 体。ローブをまとった立ち姿 ---
        // 腰から下は、裾が広がる。上へ行くほど細い。
        val bodyH = 104
        for (i2 in 0 until bodyH) {
            val v = i2.toFloat() / bodyH            // 0=足元 1=肩
            val y = statueFoot - 4 - i2
            // 裾は広く、腰でくびれ、胸でまた少し広がる
            val hw = when {
                // 裾は広く、腰でくびれ、肩へ向かってわずかに広がる
                v < 0.26f -> (19 - v * 30).roundToInt()
                v < 0.66f -> (11 - (v - 0.26f) * 5).roundToInt()
                else -> (9 + (v - 0.66f) * 10).roundToInt()
            }.coerceAtLeast(5)
            for (x in -hw..hw) {
                val u = x.toFloat() / hw
                // 円筒の陰影。左から光。
                var t = sqrt((1f - u * u).coerceAtLeast(0f)) * (0.62f - u * 0.34f)
                // ローブのひだ。縦の筋を何本か入れる。
                val fold = ((x + hw) * 7f / (hw * 2)).toInt()
                if (fold % 2 == 1) t -= 0.13f
                b.set(cx + x, y, PATINA.at(t))
            }
            // 輪郭
            b.set(cx - hw, y, PATINA.edge)
            b.set(cx + hw, y, PATINA.edge)
        }

        val shoulderY = statueFoot - 4 - bodyH

        // --- 左手に抱える銘板（独立宣言の日付が刻まれた板） ---
        // 体の左前に、斜めに抱えている。
        for (i2 in 0 until 34) {
            val y = shoulderY + 16 + i2
            val slant = (i2 * 0.34f).roundToInt()
            for (x in -26 + slant..-12 + slant) {
                val edge = i2 == 0 || i2 == 33 || x == -26 + slant || x == -12 + slant
                b.set(cx + x, y, if (edge) PATINA.edge else PATINA.lit)
            }
        }

        // --- 首 ---
        val headY = shoulderY - 9
        for (y in headY + 6..headY + 12) for (x in -4..4) {
            b.set(cx + x, y, PATINA.at(0.32f))
        }

        // --- 冠。7本の光条。頭より先に描いて、頭を手前に出す ---
        for (k in 0 until 7) {
            val ang = Math.PI * (0.08 + 0.84 * k / 6.0)
            val len = if (k == 3) 20 else 16
            for (r in 7..len) {
                val sx = cx - (cos(ang) * r).roundToInt()
                val sy = headY - 3 - (sin(ang) * r * 0.94).roundToInt()
                b.set(sx, sy, if (r > len - 4) PATINA.hi else PATINA.lit)
                // 光条に厚みを持たせる
                if (r <= len - 2) b.set(sx, sy + 1, PATINA.dark)
            }
        }

        // --- 頭 ---
        for (yy in -10..7) for (xx in -8..8) {
            val d = (xx * xx).toFloat() / 64f + (yy * yy).toFloat() / 100f
            if (d > 1f) continue
            val t = (-xx * 0.30f - yy * 0.20f + 4.8f) / 9.6f
            b.set(cx + xx, headY + yy, PATINA.at(t))
        }
        // 冠の帯（額のところ）
        for (x in -8..8) {
            val yy = headY - 4 + abs(x) / 4
            b.set(cx + x, yy, PATINA.dark)
            b.set(cx + x, yy + 1, PATINA.hi)
        }

        // --- 右腕と松明。斜め上に高く掲げる ---
        val armLen = 52
        for (i2 in 0 until armLen) {
            val t = i2.toFloat() / armLen
            val x = 8 + (t * 22).roundToInt()
            val y = shoulderY + 8 - (t * armLen).roundToInt()
            val thick = (5 - t * 2).roundToInt().coerceAtLeast(2)
            for (d in -thick..thick) {
                val u = d.toFloat() / thick
                val sh = sqrt((1f - u * u).coerceAtLeast(0f)) * (0.60f - u * 0.28f)
                b.set(cx + x + d, y, PATINA.at(sh))
            }
        }
        // 松明の握り
        val torchX = cx + 30
        val torchY = shoulderY + 8 - armLen
        for (y in torchY - 6..torchY + 4) for (x in -4..4) {
            b.set(torchX + x, y, PATINA.at(0.40f - abs(x) * 0.05f))
        }
        // 松明の受け皿
        for (x in -8..8) {
            b.set(torchX + x, torchY - 7, GILT.mid)
            b.set(torchX + x, torchY - 8, GILT.lit)
        }
        // 炎
        for (yy in 0 until 16) {
            val hw = ((1f - yy / 16f).pow(0.6f) * 6).roundToInt()
            for (x in -hw..hw) {
                val t = 1f - yy / 18f - abs(x) * 0.06f
                b.set(torchX + x, torchY - 9 - yy, GILT.at(t))
            }
        }
        return b.toSprite()
    }

    // ------------------------------------------------------------------
    // ビッグ・ベン（エリザベス・タワー）
    // ------------------------------------------------------------------

    /**
     * ロンドン、ウェストミンスター。1859年。
     *
     * 形の決め手は、細長いゴシック様式の塔、上部の4面の大時計、
     * その上の鐘楼（ベルフリー）と、槍のように尖った屋根。
     * 壁は砂岩、屋根と細部は緑青の銅。
     */
    private fun bigBen(): Sprite {
        val h = 392
        val b = Buf(W, h)
        val cx = W / 2
        val baseY = h - 1 - BASE_H / 4

        b.groundShadow(cx + 6, baseY + 4, 118, flat = 0.46f)
        b.platform(cx, baseY, 116, 5, GROUND, steps = 2)

        val footY = baseY - 12
        val halfW = 34
        val depth = 16
        val shaftH = 178

        fun frontY(x: Int): Int =
            footY - (depth - depth * abs(x).toFloat() / halfW).roundToInt()

        // --- 塔の胴。細長い縦の窓を並べる ---
        val shaftDecor: (Int, Int, Float, Float) -> Int? = { x, y, _, v ->
            val lx = x - cx
            // 縦に細長い窓を、左右の面に3本ずつ
            val slot = ((lx + halfW) % 13)
            when {
                v > 0.97f -> LONDON.hi
                // 水平の帯（階を分ける繰形）
                ((v * 7).toInt() % 1 == 0 && (v * 700).toInt() % 100 in 0..5) -> LONDON.lit
                slot in 4..7 && v in 0.10f..0.88f -> {
                    // 窓。上端は尖ったアーチ。
                    val inWin = ((v * 100).toInt() % 26)
                    if (inWin in 2..20) Palette.OPENING else LONDON.dark
                }
                else -> null
            }
        }
        b.isoBox(cx, footY, halfW, shaftH, LONDON, depth,
            left = shaftDecor, right = shaftDecor, top = false)

        // --- 時計の段。胴より少し張り出す ---
        val clockBase = footY - shaftH
        for (x in -halfW - 3..halfW + 3) {
            val fy = frontY(x.coerceIn(-halfW, halfW)) - shaftH
            for (i2 in 0 until 62) {
                val y = fy - i2
                val c = when {
                    i2 < 4 -> LONDON.hi                    // 下の繰形
                    i2 > 57 -> LONDON.hi                   // 上の繰形
                    abs(x) > halfW -> LONDON.dark
                    x < 0 -> LONDON.lit
                    else -> LONDON.mid
                }
                b.set(cx + x, y, c)
            }
        }

        // --- 時計の文字盤。左右の面に1つずつ ---
        for ((ox, lit) in listOf(-18 to true, 20 to false)) {
            val ccy = frontY(ox) - shaftH - 30
            val rr = 15
            for (yy in -rr..rr) for (xx in -rr..rr) {
                val d2 = xx * xx + yy * yy
                if (d2 > rr * rr) continue
                b.set(
                    cx + ox + xx, ccy + yy,
                    when {
                        d2 > (rr - 2) * (rr - 2) -> GILT.mid          // 金の縁
                        d2 > (rr - 4) * (rr - 4) -> GILT.lit
                        else -> if (lit) Palette.WINDOW_LIT else Palette.MARBLE_LIT
                    },
                )
            }
            // 文字盤の目盛り
            for (k in 0 until 12) {
                val ang = Math.PI * 2 * k / 12
                val mx = cx + ox + (sin(ang) * (rr - 5)).roundToInt()
                val my = ccy - (cos(ang) * (rr - 5)).roundToInt()
                b.set(mx, my, GILT.dark)
            }
            // 針（10時10分）
            for (r in 0..9) {
                b.set(cx + ox - (r * 0.50).roundToInt(), ccy - (r * 0.86).roundToInt(), Palette.BLACK)
            }
            for (r in 0..7) {
                b.set(cx + ox + (r * 0.86).roundToInt(), ccy - (r * 0.50).roundToInt(), Palette.BLACK)
            }
        }

        // --- 鐘楼。時計の上。開口があり、鐘が見える ---
        val belfryBase = clockBase - 62
        for (x in -halfW + 2..halfW - 2) {
            val fy = frontY(x) - shaftH - 62
            for (i2 in 0 until 44) {
                val y = fy - i2
                val lx = x
                // 尖ったアーチの開口
                val slot = (lx + halfW) % 15
                val c = when {
                    i2 < 3 -> LONDON.hi
                    slot in 4..10 && i2 in 6..38 -> {
                        // アーチの頭を尖らせる
                        val u = abs(slot - 7) / 3.5f
                        val top = 38 - (u * 14).roundToInt()
                        if (i2 <= top) Palette.OPENING else LONDON.dark
                    }
                    x < 0 -> LONDON.lit
                    else -> LONDON.mid
                }
                b.set(cx + x, y, c)
            }
        }

        // --- 屋根。ゴシックの尖塔 ---
        val spireBase = belfryBase - 44
        // 屋根の裾の繰形
        b.entablature(cx, spireBase - 6, halfW - 1, 6, PATINA, overhang = 4)
        val spireH = 62
        for (i2 in 0 until spireH) {
            val t = i2.toFloat() / spireH
            val hw = ((1f - t).pow(0.82f) * (halfW - 4)).roundToInt().coerceAtLeast(1)
            val y = spireBase - 7 - i2
            for (x in -hw..hw) {
                val u = x.toFloat() / hw
                // 四角錐なので、中央に稜線が立つ
                val c = when {
                    abs(x) >= hw -> PATINA.edge
                    x < -hw / 5 -> PATINA.lit
                    x < hw / 5 -> PATINA.hi
                    else -> PATINA.dark
                }
                b.set(cx + x, y, c)
            }
        }
        // 頂点の飾りと十字
        val tipY = spireBase - 7 - spireH
        for (y in tipY - 12..tipY) b.set(cx, y, GILT.mid)
        for (x in -3..3) b.set(cx + x, tipY - 9, GILT.lit)
        b.set(cx, tipY - 14, GILT.hi)

        // 四隅の小尖塔
        for (d in intArrayOf(-halfW + 3, halfW - 3)) {
            for (i2 in 0 until 26) {
                val t = i2.toFloat() / 26
                val hw = ((1f - t) * 4).roundToInt().coerceAtLeast(1)
                for (x in -hw..hw) {
                    b.set(cx + d + x, spireBase - 7 - i2, if (x < 0) PATINA.lit else PATINA.dark)
                }
            }
        }
        return b.toSprite()
    }

    // ------------------------------------------------------------------
    // ピサの斜塔
    // ------------------------------------------------------------------

    /**
     * イタリア、ピサの大聖堂の鐘楼。1372年完成。
     *
     * 形の決め手は、白大理石の円筒に、6層の柱廊がぐるりと巻いていること。
     * 一番下は壁面に半円柱（ブラインドアーケード）、
     * 一番上は一回り細い鐘楼。そして南へ傾いている。
     */
    private fun leaningTower(): Sprite {
        val h = 360
        val b = Buf(W, h)
        val cx = W / 2 - 14
        val baseY = h - 1 - BASE_H / 4

        b.groundShadow(cx + 16, baseY + 4, 128, flat = 0.46f)
        b.platform(cx + 8, baseY, 122, 5, GROUND, steps = 2)

        val footY = baseY - 12
        /** 各層の高さ。 */
        val floorH = 34
        /** 円筒の半径。 */
        val radius = 40
        /** 傾き。1層あたり右へ何ドットずれるか。 */
        val lean = 3.4f

        /**
         * [f] 層目の中心 x。塔は根元から曲がっているので、
         * 上へ行くほど、ずれが加速するようにする。
         */
        fun leanAt(f: Float): Int = (f * lean).roundToInt()

        // --- 1層目。壁面に半円柱を並べた、閉じた基部 ---
        run {
            val ox = leanAt(0f)
            for (i2 in 0 until floorH + 8) {
                val y = footY - i2
                for (x in -radius..radius) {
                    val u = x.toFloat() / radius
                    // 円筒の陰影
                    var t = sqrt((1f - u * u).coerceAtLeast(0f)) * (0.66f - u * 0.36f)
                    // 半円柱（ブラインドアーケード）の縦の筋
                    val col = ((x + radius) * 15f / (radius * 2)).toInt()
                    val inCol = ((x + radius) * 15f / (radius * 2)) - col
                    if (inCol < 0.18f) t -= 0.24f
                    // 上下の繰形
                    if (i2 < 3 || i2 > floorH + 3) t += 0.18f
                    b.set(cx + ox + x, y, MARBLE.at(t))
                }
            }
        }

        // --- 2〜7層目。柱廊がぐるりと巻く ---
        for (f in 1..6) {
            val fy = footY - (floorH + 8) - (f - 1) * floorH
            // 層の中でも高さに応じてずらすと、折れ線ではなく一本の傾いた塔に見える
            val ox = leanAt(f.toFloat())
            // 各層の床（張り出した繰形）
            for (x in -radius - 2..radius + 2) {
                val u = (x.toFloat() / (radius + 2)).coerceIn(-1f, 1f)
                val t = sqrt((1f - u * u).coerceAtLeast(0f)) * 0.5f + 0.42f
                b.set(cx + ox + x, fy, MARBLE.at(t + 0.12f))
                b.set(cx + ox + x, fy - 1, MARBLE.at(t))
                b.set(cx + ox + x, fy + 1, MARBLE.at(t - 0.34f))     // 影
            }
            // 柱廊。奥の闇を敷いてから、手前に柱を立てる。
            for (i2 in 2 until floorH - 4) {
                val y = fy - i2
                for (x in -radius + 2..radius - 2) {
                    b.set(cx + ox + x, y, Palette.OPENING)
                }
            }
            // 柱。円筒に沿って並ぶので、端ほど詰まって見える。
            val pillars = 15
            for (k in 0..pillars) {
                val ang = Math.PI * k / pillars
                val px = -(cos(ang) * (radius - 2)).roundToInt()
                // 端の柱ほど細い
                val pr = (1.6f + 1.4f * sin(ang)).roundToInt().coerceAtLeast(1)
                for (x in -pr..pr) {
                    for (i2 in 2 until floorH - 4) {
                        val u = if (pr == 0) 0f else x.toFloat() / pr
                        val t = sqrt((1f - u * u).coerceAtLeast(0f)) * 0.52f + 0.28f
                        b.set(cx + ox + px + x, fy - i2, MARBLE.at(t))
                    }
                }
            }
            // 柱の上のアーチ
            for (k in 0 until pillars) {
                val a0 = Math.PI * k / pillars
                val a1 = Math.PI * (k + 1) / pillars
                val x0 = -(cos(a0) * (radius - 2)).roundToInt()
                val x1 = -(cos(a1) * (radius - 2)).roundToInt()
                val mid = (x0 + x1) / 2
                val span = (x1 - x0).coerceAtLeast(1)
                for (x in x0..x1) {
                    val u = (x - mid).toFloat() / (span / 2f).coerceAtLeast(1f)
                    val rise = (4 * sqrt((1f - u * u).coerceAtLeast(0f))).roundToInt()
                    b.set(cx + ox + x, fy - (floorH - 5) - rise, MARBLE.lit)
                }
            }
        }

        // --- 8層目。一回り細い鐘楼 ---
        run {
            val f = 7f
            val ox = leanAt(f)
            val fy = footY - (floorH + 8) - 6 * floorH
            val r2 = radius - 11
            // 床
            for (x in -radius - 2..radius + 2) {
                val u = (x.toFloat() / (radius + 2)).coerceIn(-1f, 1f)
                val t = sqrt((1f - u * u).coerceAtLeast(0f)) * 0.5f + 0.44f
                b.set(cx + ox + x, fy, MARBLE.at(t + 0.12f))
                b.set(cx + ox + x, fy + 1, MARBLE.at(t - 0.32f))
            }
            // 鐘楼の壁
            for (i2 in 1 until 30) {
                val y = fy - i2
                for (x in -r2..r2) {
                    val u = x.toFloat() / r2
                    var t = sqrt((1f - u * u).coerceAtLeast(0f)) * (0.64f - u * 0.32f)
                    // 鐘の見える開口。柱を何本か残す。
                    val slot = ((x + r2) * 7f / (r2 * 2)).toInt()
                    val inSlot = ((x + r2) * 7f / (r2 * 2)) - slot
                    if (i2 in 6..22 && abs(x) < r2 - 4 && inSlot > 0.28f) {
                        b.set(cx + ox + x, y, Palette.OPENING)
                        continue
                    }
                    if (i2 < 3 || i2 > 26) t += 0.16f
                    b.set(cx + ox + x, y, MARBLE.at(t))
                }
            }
            // 鐘
            for (yy in 0 until 11) {
                val hw = (3 + yy * 0.42f).roundToInt()
                for (x in -hw..hw) {
                    b.set(cx + ox + x, fy - 20 + yy, GILT.at(0.6f - abs(x) * 0.06f))
                }
            }
            // 屋上の縁
            for (x in -r2 - 2..r2 + 2) {
                b.set(cx + ox + x, fy - 30, MARBLE.hi)
                b.set(cx + ox + x, fy - 31, MARBLE.lit)
            }
        }
        return b.toSprite()
    }

    // ------------------------------------------------------------------
    // タージ・マハル
    // ------------------------------------------------------------------

    /**
     * インド、アーグラ。1653年完成の白大理石の霊廟。
     *
     * 形の決め手は、たまねぎ形の大ドーム、その周りの4つの小ドーム（チャトリ）、
     * 正面中央の大きな尖頭アーチ（イーワーン）、
     * そして基壇の四隅に立つ4本のミナレット。
     */
    private fun tajMahal(): Sprite {
        val h = 300
        val b = Buf(W, h)
        val cx = W / 2
        val baseY = h - 1 - BASE_H / 4

        b.groundShadow(cx + 6, baseY + 4, 236, flat = 0.42f)

        // --- 大基壇 ---
        b.platform(cx, baseY, 232, 7, MARBLE, steps = 1)
        val plinthY = baseY - 7

        // --- 4本のミナレット。基壇の四隅に立つ ---
        // 奥の2本を先に描く（手前の本体に隠れる）
        val minaretH = 150
        fun minaret(mx: Int, my: Int, scale: Float) {
            val r = (6 * scale).roundToInt().coerceAtLeast(3)
            val hh = (minaretH * scale).roundToInt()
            // 円筒の軸
            for (i2 in 0 until hh) {
                val v = i2.toFloat() / hh
                val rr = (r * (1f - v * 0.14f)).roundToInt().coerceAtLeast(2)
                for (x in -rr..rr) {
                    val u = x.toFloat() / rr
                    var t = sqrt((1f - u * u).coerceAtLeast(0f)) * (0.66f - u * 0.34f)
                    // 3か所のバルコニー
                    val band = (v * 3.4f) % 1f
                    if (band < 0.06f) t += 0.24f
                    b.set(mx + x, my - i2, MARBLE.at(t))
                }
                // バルコニーの張り出し
                val band = (v * 3.4f) % 1f
                if (band < 0.05f) {
                    for (x in -rr - 2..rr + 2) {
                        b.set(mx + x, my - i2, MARBLE.at(0.82f))
                    }
                }
            }
            // 頂部のチャトリ（小さなドームの東屋）
            val topY = my - hh
            for (x in -r - 2..r + 2) b.set(mx + x, topY, MARBLE.hi)
            b.dome(mx, topY - 2, r + 1, r + 3, MARBLE, onion = 0.30f)
            for (y in topY - r - 8..topY - r - 4) b.set(mx, y, GILT.mid)
        }

        // 奥の2本
        minaret(cx - 86, plinthY - 16, 0.86f)
        minaret(cx + 86, plinthY - 16, 0.86f)

        // --- 本体。四隅を落とした正方形（八角形に近い） ---
        val bodyHalf = 62
        val bodyH = 86
        val bodyDepth = 28
        val bodyFoot = plinthY - 4

        fun frontY(x: Int): Int =
            bodyFoot - (bodyDepth - bodyDepth * abs(x).toFloat() / bodyHalf).roundToInt()

        b.isoBox(cx, bodyFoot, bodyHalf, bodyH, MARBLE, bodyDepth)

        // --- 正面の大きな尖頭アーチ（イーワーン） ---
        // 縁を一段へこませた枠のなかに、尖ったアーチを彫る。
        run {
            val ax = cx - 26
            val aw = 44
            val ah = 66
            // 枠
            for (x in -aw / 2 - 5..aw / 2 + 5) {
                val by = frontY(ax - cx + x)
                for (i2 in 0..ah + 8) {
                    if (abs(x) > aw / 2 + 5) continue
                    b.over(ax + x, by - i2, MARBLE.at(0.92f))
                }
            }
            // 尖頭アーチの開口
            for (x in -aw / 2..aw / 2) {
                val by = frontY(ax - cx + x)
                val u = abs(x).toFloat() / (aw / 2f)
                // 尖頭。頂点で尖るよう、上半分を直線的に落とす。
                val top = if (u < 0.55f) {
                    ah - (u / 0.55f).pow(2.2f) * 10
                } else {
                    val k = (u - 0.55f) / 0.45f
                    (ah - 10) * sqrt((1f - k * k).coerceAtLeast(0f))
                }
                for (i2 in 0..top.roundToInt()) {
                    b.set(ax + x, by - i2, Palette.OPENING)
                }
                // 縁取り
                b.set(ax + x, by - top.roundToInt() - 1, MARBLE.hi)
            }
        }
        // 左右の小さな尖頭アーチ（2段重ね）
        for (ox in intArrayOf(16, 40)) {
            for (row in 0 until 2) {
                val ax = cx + ox
                val by = frontY(ox) - row * 40
                for (x in -7..7) {
                    val u = abs(x) / 7f
                    val top = (26 * sqrt((1f - u * u).coerceAtLeast(0f))).roundToInt()
                    for (i2 in 4..top + 4) b.set(ax + x, by - i2, Palette.OPENING)
                }
            }
        }

        // --- 屋上の欄干 ---
        val roofY = bodyFoot - bodyH - bodyDepth
        for (x in -bodyHalf..bodyHalf) {
            val topEdge = frontY(x) - bodyH
            for (i2 in 0 until 5) {
                b.over(cx + x, topEdge + i2, if (i2 < 2) MARBLE.hi else MARBLE.dark)
            }
        }

        // --- 4つのチャトリ（小ドーム）。大ドームの四隅 ---
        for ((dx, dy, sc) in listOf(
            Triple(-46, -6, 0.9f), Triple(46, -6, 0.9f),
            Triple(-30, -20, 0.8f), Triple(30, -20, 0.8f),
        )) {
            val ccx = cx + dx
            val ccy = roofY + dy
            val cr = (11 * sc).roundToInt()
            // 柱（4本の脚）
            for (px in intArrayOf(-cr + 1, cr - 1)) {
                for (i2 in 0 until 14) b.set(ccx + px, ccy - i2, MARBLE.at(0.44f))
            }
            for (x in -cr..cr) b.set(ccx + x, ccy - 14, MARBLE.hi)
            b.dome(ccx, ccy - 15, cr, (cr * 1.5f).roundToInt(), MARBLE, onion = 0.42f)
            for (y in ccy - 15 - (cr * 1.5f).roundToInt() - 7..ccy - 15 - (cr * 1.5f).roundToInt() - 2) {
                b.set(ccx, y, GILT.mid)
            }
        }

        // --- 大ドーム。たまねぎ形 ---
        // 円筒の首（ドラム）
        val drumY = roofY - 6
        for (i2 in 0 until 22) {
            for (x in -30..30) {
                val u = x / 30f
                val t = sqrt((1f - u * u).coerceAtLeast(0f)) * (0.70f - u * 0.30f)
                b.set(cx + x, drumY - i2, MARBLE.at(t))
            }
        }
        // 首の繰形
        for (x in -33..33) {
            b.set(cx + x, drumY - 22, MARBLE.hi)
            b.set(cx + x, drumY - 23, MARBLE.lit)
        }
        b.dome(cx, drumY - 24, 34, 56, MARBLE, onion = 0.50f)
        // 頂部の飾りと尖塔
        val domeTop = drumY - 24 - 56
        for (x in -4..4) b.set(cx + x, domeTop - 1, GILT.dark)
        for (y in domeTop - 20..domeTop - 2) b.set(cx, y, GILT.mid)
        for (y in domeTop - 14..domeTop - 10) {
            for (x in -3..3) b.set(cx + x, y, GILT.lit)
        }
        b.set(cx, domeTop - 22, GILT.hi)

        // --- 手前の2本のミナレット ---
        minaret(cx - 96, plinthY - 2, 1f)
        minaret(cx + 96, plinthY - 2, 1f)
        return b.toSprite()
    }

    // ------------------------------------------------------------------
    // ギザのピラミッド
    // ------------------------------------------------------------------

    /**
     * エジプト、ギザ。紀元前2560年ごろ。
     *
     * 形の決め手は、2つの面が見える四角錐と、そのあいだに立つ稜線。
     * 表面は石を積んだ段でできている。頂部にだけ化粧石が残る。
     * 奥に小さめの第二・第三ピラミッド、手前にスフィンクス。
     */
    private fun pyramid(): Sprite {
        val h = 236
        val b = Buf(W, h)
        val cx = W / 2
        val baseY = h - 1 - BASE_H / 4

        /**
         * 四角錐を1つ描く。
         *
         * クォータービューでは、手前の稜線を境に左右2面が見える。
         * 底面は菱形なので、左右の裾はそれぞれ斜めに下がる。
         *
         * @param half 底面の横半径
         * @param height 頂点までの高さ
         * @param cap 頂部に残る化粧石の割合
         */
        fun pyramidAt(pcx: Int, pbase: Int, half: Int, height: Int, cap: Float = 0f) {
            val depth = half / 2
            // 頂点
            val apexY = pbase - height
            for (x in -half..half) {
                val u = abs(x).toFloat() / half
                // その x での、底辺の y（菱形の手前側の輪郭）
                val footY = pbase + depth - (depth * u).roundToInt()
                // その x での、上の輪郭（頂点から左右の角へ下る稜線）
                val top = apexY + ((height - depth) * u).roundToInt()
                for (y in top..footY) {
                    if (y < 0) continue
                    // 左の面は光を受け、右の面は陰。差をつけすぎない。
                    var t = if (x < 0) 0.72f else 0.44f
                    // 上へいくほどわずかに明るく（空気遠近）。
                    // これがないと面が2色の板になってしまう。
                    t += (footY - y).toFloat() / height * 0.16f
                    // 稜線から離れるほど、わずかに暗く（面の丸み）
                    t -= u * 0.10f
                    // 石を積んだ段。画面の高さで数えると水平にそろう。
                    // 実物も水平に積まれているので、これが正しい。
                    if ((pbase - y) % 7 == 0) t += 0.09f      // 石の上端
                    else if ((pbase - y) % 7 == 1) t -= 0.07f // その下の目地
                    // 頂部に残る化粧石
                    if (cap > 0f && y < apexY + (height * cap).roundToInt()) t += 0.14f
                    b.set(pcx + x, y, LIMESTONE.at(t))
                }
                // 上の輪郭を明るく（陽の当たる稜）
                b.set(pcx + x, top, LIMESTONE.at(if (x < 0) 0.94f else 0.52f))
                // 裾の影
                b.set(pcx + x, footY + 1, LIMESTONE.edge)
            }
            // 手前に立つ稜線（頂点から手前の角へ）。ここが面の折れ目。
            for (i2 in 0..(height + depth)) {
                val t2 = i2.toFloat() / (height + depth)
                val y = apexY + i2
                if (y > pbase + depth) break
                b.set(pcx, y, LIMESTONE.at(0.86f - t2 * 0.06f))
            }
        }

        // --- 奥の2基（第二・第三ピラミッド）。左右に振って、3基とも見せる ---
        pyramidAt(cx - 74, baseY - 26, 40, 66)
        pyramidAt(cx + 78, baseY - 20, 28, 42)

        // --- 大ピラミッド（クフ王） ---
        pyramidAt(cx + 2, baseY - 2, 78, 132, cap = 0.18f)

        // --- 砂の地面。ピラミッドの裾に沿って敷く ---
        for (x in -W / 2 until W / 2) {
            val yy = baseY + 2 + (abs(x) / 20)
            for (y in yy..yy + 7) {
                if (!b.isInk(cx + x, y)) {
                    b.set(cx + x, y, if (y < yy + 2) Palette.SAND else Palette.SAND_DARK)
                }
            }
        }

        // --- スフィンクス。手前の左に、小さく ---
        run {
            val sx = cx - 60
            val sy = baseY + 16
            // 胴（横たわった獅子）
            for (i2 in 0 until 13) {
                val hw = (17 - i2 * 0.5f).roundToInt()
                for (x in -hw..hw) {
                    val t = 0.56f - i2 * 0.014f - if (x > hw / 3) 0.16f else 0f
                    b.set(sx + x, sy - i2, LIMESTONE.at(t))
                }
            }
            // 前脚
            for (x in -20..-13) for (y in sy - 5..sy) {
                b.set(sx + x, y, LIMESTONE.at(0.62f))
            }
            // 頭とネメス頭巾
            for (yy in 0 until 13) {
                val hw = (7 - abs(yy - 5) * 0.4f).roundToInt().coerceAtLeast(3)
                for (x in -hw..hw) {
                    val t = 0.70f - abs(x) * 0.022f - yy * 0.01f
                    b.set(sx - 10 + x, sy - 13 - yy, LIMESTONE.at(t))
                }
            }
            // 頭巾の垂れ
            for (x in intArrayOf(-18, -3)) for (y in sy - 16..sy - 9) {
                b.set(sx + x, y, LIMESTONE.at(0.44f))
            }
            // 顔（陰で目鼻を示す）
            b.set(sx - 12, sy - 20, LIMESTONE.edge)
            b.set(sx - 8, sy - 20, LIMESTONE.edge)
            for (x in -12..-8) b.set(sx + x, sy - 16, LIMESTONE.dark)
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
