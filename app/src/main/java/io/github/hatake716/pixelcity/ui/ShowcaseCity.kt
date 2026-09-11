package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Monument
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind
import kotlin.random.Random

/**
 * タイトル画面の背景に置く、完成しきった大都市。
 *
 * ゲーム本編とまったく同じ [City] と描画を使って作るので、
 * 「このゲームで行き着く先」をそのまま見せることになる。
 * 遊びには関わらず、描くためだけの街。
 */
object ShowcaseCity {

    /**
     * 最高段階まで発展させた都市を組み立てる。
     *
     * 中央に高層の商業街、それを囲む住宅街、外周に工業地帯、
     * 水辺と公園、そして建てられるモニュメントをすべて並べる。
     */
    fun build(seed: Long = 20260911L): City {
        val city = City(60, 60)
        val rnd = Random(seed)

        for (t in city.tiles) {
            t.terrain = Terrain.LAND
            t.kind = TileKind.EMPTY
            t.stage = 0
        }

        // 街の南側に湾を作る。水辺の景色を入れるため。
        for (y in 0 until city.height) for (x in 0 until city.width) {
            val d = (x - 50) * (x - 50) + (y - 57) * (y - 57)
            if (d < 70) city.tileAt(x, y).terrain = Terrain.WATER
        }
        // 北から流れる川
        var rx = 10
        for (y in 0 until 46) {
            for (dx in 0..1) {
                val x = rx + dx
                if (city.inBounds(x, y)) city.tileAt(x, y).terrain = Terrain.WATER
            }
            rx += rnd.nextInt(3) - 1
            rx = rx.coerceIn(4, 14)
        }
        markShores(city)

        // 碁盤の目の道路
        for (y in 3 until 57 step 3) {
            for (x in 2 until 58) place(city, x, y, TileKind.ROAD)
        }
        for (x in 2 until 58 step 4) {
            for (y in 3 until 57) place(city, x, y, TileKind.ROAD)
        }

        // 中心ほど高く、外へ行くほど低い街並みにする。
        val cx = 30
        val cy = 28
        for (y in 3 until 57) for (x in 2 until 58) {
            val t = city.tileOrNull(x, y) ?: continue
            if (t.terrain == Terrain.WATER || t.kind != TileKind.EMPTY) continue
            val d = Math.hypot((x - cx).toDouble(), (y - cy).toDouble())
            val n = rnd.nextInt(100)

            when {
                // 中心部: 高層の商業
                d < 9 -> { t.kind = TileKind.ZONE_C; t.stage = 3 }
                // その周り: 商業と住宅が混ざる
                d < 15 -> {
                    t.kind = if (n < 55) TileKind.ZONE_C else TileKind.ZONE_R
                    t.stage = if (n < 80) 3 else 2
                }
                // 住宅街
                d < 23 -> {
                    t.kind = TileKind.ZONE_R
                    t.stage = when { n < 45 -> 3; n < 80 -> 2; else -> 1 }
                }
                // 外周: 工業と低層住宅
                else -> {
                    t.kind = if (n < 45) TileKind.ZONE_I else TileKind.ZONE_R
                    t.stage = when { n < 30 -> 3; n < 70 -> 2; else -> 1 }
                }
            }
        }

        // 公共施設を散らす
        val services = listOf(
            TileKind.PARK to 22,
            TileKind.SCHOOL to 6,
            TileKind.HOSPITAL to 6,
            TileKind.POLICE to 6,
            TileKind.FIRE to 6,
            TileKind.POWER_SOLAR to 5,
            TileKind.POWER_COAL to 3,
        )
        for ((kind, count) in services) {
            var placed = 0
            var guard = 0
            while (placed < count && guard++ < 400) {
                val x = 3 + rnd.nextInt(54)
                val y = 4 + rnd.nextInt(52)
                val t = city.tileOrNull(x, y) ?: continue
                if (t.terrain == Terrain.WATER || t.kind == TileKind.ROAD) continue
                if (t.monumentAnchor >= 0) continue
                t.kind = kind
                t.stage = 1
                placed++
            }
        }

        // モニュメントを見栄えのする位置へ並べる
        val spots = listOf(
            Monument.TOKYO_TOWER to (26 to 22),
            Monument.ARC_DE_TRIOMPHE to (34 to 26),
            Monument.COLOSSEUM to (22 to 33),
            Monument.BIG_BEN to (38 to 33),
            Monument.TAJ_MAHAL to (30 to 40),
            Monument.LEANING_TOWER to (18 to 25),
            Monument.PYRAMID to (44 to 42),
            Monument.STATUE_OF_LIBERTY to (40 to 47),
        )
        for ((m, pos) in spots) {
            val (x, y) = pos
            var ok = true
            for (dy in 0..1) for (dx in 0..1) {
                val t = city.tileOrNull(x + dx, y + dy)
                if (t == null || t.terrain == Terrain.WATER) ok = false
            }
            if (!ok) continue
            val anchor = city.index(x, y)
            for (dy in 0..1) for (dx in 0..1) {
                val t = city.tileAt(x + dx, y + dy)
                t.kind = TileKind.MONUMENT
                t.monument = null
                t.monumentAnchor = anchor
                t.stage = 1
            }
            city.tiles[anchor].monument = m
            city.builtMonuments.add(m)
        }

        // 表示のために、電力と地価などを一度計算しておく
        city.funds = 999_999
        city.recomputeDerivedState()
        // 電気は全部通っているものとして見せる
        for (t in city.tiles) t.powered = true
        return city
    }

    private fun markShores(city: City) {
        for (y in 0 until city.height) for (x in 0 until city.width) {
            val t = city.tileAt(x, y)
            if (t.terrain != Terrain.LAND) continue
            val touchesWater = listOf(
                city.tileOrNull(x - 1, y), city.tileOrNull(x + 1, y),
                city.tileOrNull(x, y - 1), city.tileOrNull(x, y + 1),
            ).any { it?.terrain == Terrain.WATER }
            if (touchesWater) t.terrain = Terrain.SHORE
        }
    }

    private fun place(city: City, x: Int, y: Int, kind: TileKind) {
        val t = city.tileOrNull(x, y) ?: return
        if (t.terrain == Terrain.WATER) return
        t.kind = kind
        t.stage = 1
    }
}
