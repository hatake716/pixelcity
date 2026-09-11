package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Monument
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind
import kotlin.random.Random

/**
 * 完成した街の見本。
 *
 * ゲーム本編とまったく同じ [City] を組み立てるので、
 * タイトルの背景に使えば「行き着く先」がそのまま見え、
 * 「おてほんからはじめる」で選べば、その街の続きから遊べる。
 *
 * 3種類とも、そのまま黒字で回るように作ってある。
 */
object ShowcaseCity {

    /** 見本の種類。 */
    enum class Kind(
        val label: String,
        val summary: String,
        val seed: Long,
    ) {
        /** 大都市。人口と密度が最大級で、財政と人口のつりあいが取れている。 */
        METROPOLIS(
            label = "だいとし",
            summary = "じんこうと みつどが さいだいきゅう。ざいせいとの つりあいが とれた まち。",
            seed = 20260911L,
        ),

        /** 田園都市。農業と環境への配慮が行き届いている。 */
        GARDEN(
            label = "でんえんとし",
            summary = "のうちと みどりが ゆたか。たいようこう・ふうりょく・てつどうで かんきょうに やさしい まち。",
            seed = 31415926L,
        ),

        /** 工業都市。工業が中心で、財政が最も健全。 */
        INDUSTRIAL(
            label = "こうぎょうとし",
            summary = "こうぎょうが ちゅうしん。ざいせいが もっとも けんぜんな まち。",
            seed = 27182818L,
        ),
    }

    /** 見本を組み立てる。同じ [kind] からは必ず同じ街ができる。 */
    fun build(kind: Kind = Kind.METROPOLIS): City = when (kind) {
        Kind.METROPOLIS -> metropolis()
        Kind.GARDEN -> garden()
        Kind.INDUSTRIAL -> industrial()
    }

    // ------------------------------------------------------------------
    // 共通の下ごしらえ
    // ------------------------------------------------------------------

    /** 更地の街と、水辺のある地形を作る。 */
    private fun blank(seed: Long, bayX: Int, bayY: Int, bayR: Int): Pair<City, Random> {
        val city = City(60, 60)
        val rnd = Random(seed)
        for (t in city.tiles) {
            t.terrain = Terrain.LAND
            t.kind = TileKind.EMPTY
            t.stage = 0
        }
        // 湾
        for (y in 0 until city.height) for (x in 0 until city.width) {
            val d = (x - bayX) * (x - bayX) + (y - bayY) * (y - bayY)
            if (d < bayR) city.tileAt(x, y).terrain = Terrain.WATER
        }
        // 川
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
        return city to rnd
    }

    /** 碁盤の目の道路を敷く。 */
    private fun grid(city: City, step: Int, cross: Int, from: Int = 3, to: Int = 57) {
        for (y in from until to step step) {
            for (x in (from - 1) until (to + 1)) place(city, x, y, TileKind.ROAD)
        }
        for (x in (from - 1) until (to + 1) step cross) {
            for (y in from until to) place(city, x, y, TileKind.ROAD)
        }
    }

    /** 施設を散らす。置けた数を返す。 */
    private fun scatter(
        city: City,
        rnd: Random,
        kind: TileKind,
        count: Int,
        area: IntRange = 3..56,
    ): Int {
        var placed = 0
        var guard = 0
        while (placed < count && guard++ < 3_000) {
            val x = area.first + rnd.nextInt(area.last - area.first)
            val y = area.first + rnd.nextInt(area.last - area.first)
            val t = city.tileOrNull(x, y) ?: continue
            if (t.terrain == Terrain.WATER || t.kind == TileKind.ROAD) continue
            if (t.kind == TileKind.RAIL || t.monumentAnchor >= 0) continue
            t.kind = kind
            t.stage = 1
            placed++
        }
        return placed
    }

    /** モニュメントを置く。置けなければ飛ばす。 */
    private fun monuments(city: City, spots: List<Pair<Monument, Pair<Int, Int>>>) {
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
    }

    /** 仕上げ。電力と地価を計算し、遊べる状態にする。 */
    private fun finish(city: City, funds: Int, month: Int, taxRate: Int): City {
        city.funds = funds
        city.month = month
        city.taxRate = taxRate
        city.recomputeDerivedState()
        for (t in city.tiles) t.powered = true
        return city
    }

    // ------------------------------------------------------------------
    // 大都市
    // ------------------------------------------------------------------

    /**
     * 人口と密度が最大級。中心に高層の商業街、その周りに住宅、外周に工業。
     * 公共施設をひととおり備え、税率は標準のままで黒字になる。
     */
    private fun metropolis(): City {
        val (city, rnd) = blank(Kind.METROPOLIS.seed, 50, 57, 70)
        grid(city, step = 3, cross = 4)

        val cx = 30
        val cy = 28
        for (y in 3 until 57) for (x in 2 until 58) {
            val t = city.tileOrNull(x, y) ?: continue
            if (t.terrain == Terrain.WATER || t.kind != TileKind.EMPTY) continue
            val d = Math.hypot((x - cx).toDouble(), (y - cy).toDouble())
            val n = rnd.nextInt(100)
            when {
                d < 9 -> {
                    t.kind = if (n < 82) TileKind.ZONE_C else TileKind.ZONE_R
                    t.stage = when { n < 58 -> 3; n < 86 -> 2; else -> 1 }
                }
                d < 15 -> {
                    t.kind = if (n < 50) TileKind.ZONE_C else TileKind.ZONE_R
                    t.stage = when { n < 45 -> 3; n < 82 -> 2; else -> 1 }
                }
                d < 23 -> {
                    t.kind = TileKind.ZONE_R
                    t.stage = when { n < 45 -> 3; n < 80 -> 2; else -> 1 }
                }
                else -> {
                    t.kind = if (n < 45) TileKind.ZONE_I else TileKind.ZONE_R
                    t.stage = when { n < 30 -> 3; n < 70 -> 2; else -> 1 }
                }
            }
        }

        scatter(city, rnd, TileKind.PARK, 22)
        scatter(city, rnd, TileKind.SCHOOL, 8)
        scatter(city, rnd, TileKind.HOSPITAL, 8)
        scatter(city, rnd, TileKind.POLICE, 8)
        scatter(city, rnd, TileKind.FIRE, 8)
        scatter(city, rnd, TileKind.POWER_SOLAR, 6)
        scatter(city, rnd, TileKind.POWER_COAL, 16)

        monuments(city, listOf(
            Monument.TOKYO_TOWER to (26 to 22),
            Monument.ARC_DE_TRIOMPHE to (34 to 26),
            Monument.COLOSSEUM to (22 to 33),
            Monument.BIG_BEN to (38 to 33),
            Monument.TAJ_MAHAL to (30 to 40),
            Monument.LEANING_TOWER to (18 to 25),
            Monument.PYRAMID to (44 to 42),
            Monument.STATUE_OF_LIBERTY to (40 to 47),
        ))

        return finish(city, funds = 250_000, month = 1_200, taxRate = 7)
    }

    // ------------------------------------------------------------------
    // 田園都市
    // ------------------------------------------------------------------

    /**
     * 農地と緑が豊か。火力発電を使わず、太陽光と風力だけでまかなう。
     * 鉄道が街を貫き、公園と農地が公害を抑える。
     * 人口は大都市に及ばないが、公害がほとんどない。
     */
    private fun garden(): City {
        val (city, rnd) = blank(Kind.GARDEN.seed, 48, 55, 90)
        // 道路は広めの間隔。街区をゆったりとる。
        grid(city, step = 4, cross = 6)

        // 鉄道を2本通す。駅前に街ができるよう、道路網と交差させる。
        for (x in 4 until 56) {
            place(city, x, 20, TileKind.RAIL)
            place(city, x, 38, TileKind.RAIL)
        }
        for (y in 12 until 46) place(city, 30, y, TileKind.RAIL)

        val cx = 30
        val cy = 29
        for (y in 3 until 57) for (x in 2 until 58) {
            val t = city.tileOrNull(x, y) ?: continue
            if (t.terrain == Terrain.WATER || t.kind != TileKind.EMPTY) continue
            val d = Math.hypot((x - cx).toDouble(), (y - cy).toDouble())
            val n = rnd.nextInt(100)
            when {
                // 駅のまわりに、職のある低中層の街。
                // 農地は職を生まないので、商業を厚くしないと住民が出ていく。
                d < 13 -> {
                    t.kind = if (n < 66) TileKind.ZONE_C else TileKind.ZONE_R
                    t.stage = when { n < 34 -> 3; n < 80 -> 2; else -> 1 }
                }
                d < 21 -> {
                    t.kind = when {
                        n < 34 -> TileKind.ZONE_C
                        n < 44 -> TileKind.ZONE_I     // 農産物を扱う軽工業
                        else -> TileKind.ZONE_R
                    }
                    t.stage = when { n < 28 -> 3; n < 74 -> 2; else -> 1 }
                }
                // 外は農地と、ところどころの住宅
                else -> {
                    when {
                        n < 52 -> { t.kind = TileKind.FARM; t.stage = 1 }
                        n < 64 -> { t.kind = TileKind.ZONE_C; t.stage = 2 }
                        else -> {
                            t.kind = TileKind.ZONE_R
                            t.stage = if (n < 84) 2 else 1
                        }
                    }
                }
            }
        }

        // 緑と公共サービスを厚く
        scatter(city, rnd, TileKind.PARK, 60)
        scatter(city, rnd, TileKind.SCHOOL, 10)
        scatter(city, rnd, TileKind.HOSPITAL, 10)
        scatter(city, rnd, TileKind.POLICE, 8)
        scatter(city, rnd, TileKind.FIRE, 8)
        // 電力は太陽光と風力だけ。火力は1つも置かない。
        scatter(city, rnd, TileKind.POWER_SOLAR, 22)
        scatter(city, rnd, TileKind.POWER_WIND, 26)

        monuments(city, listOf(
            Monument.TAJ_MAHAL to (24 to 26),
            Monument.ARC_DE_TRIOMPHE to (36 to 32),
            Monument.LEANING_TOWER to (20 to 44),
        ))

        return finish(city, funds = 180_000, month = 1_100, taxRate = 8)
    }

    // ------------------------------------------------------------------
    // 工業都市
    // ------------------------------------------------------------------

    /**
     * 工業が中心の大都市。工業と商業で税収を稼ぎ、財政が最も厚い。
     * そのかわり公害が多く、公園と病院で押さえこんでいる。
     */
    private fun industrial(): City {
        val (city, rnd) = blank(Kind.INDUSTRIAL.seed, 50, 56, 80)
        grid(city, step = 3, cross = 4)

        // 貨物の線路を港（湾）へ向けて通す
        for (x in 6 until 50) place(city, x, 44, TileKind.RAIL)
        for (y in 20 until 44) place(city, 46, y, TileKind.RAIL)

        val cx = 30
        val cy = 30
        for (y in 3 until 57) for (x in 2 until 58) {
            val t = city.tileOrNull(x, y) ?: continue
            if (t.terrain == Terrain.WATER || t.kind != TileKind.EMPTY) continue
            val d = Math.hypot((x - cx).toDouble(), (y - cy).toDouble())
            val n = rnd.nextInt(100)
            when {
                // 中心は商業（税収の要）。最高段階で固める。
                d < 10 -> {
                    t.kind = TileKind.ZONE_C
                    t.stage = if (n < 86) 3 else 2
                }
                // その周りに労働者の住宅と、商業がまざる
                d < 18 -> {
                    t.kind = if (n < 42) TileKind.ZONE_C else TileKind.ZONE_R
                    t.stage = when { n < 56 -> 3; n < 88 -> 2; else -> 1 }
                }
                // 外周は一面の工業地帯
                else -> {
                    t.kind = if (n < 76) TileKind.ZONE_I else TileKind.ZONE_R
                    t.stage = when { n < 70 -> 3; n < 92 -> 2; else -> 1 }
                }
            }
        }

        // 公害対策を厚めに
        scatter(city, rnd, TileKind.PARK, 34)
        scatter(city, rnd, TileKind.HOSPITAL, 14)
        scatter(city, rnd, TileKind.FIRE, 12)
        scatter(city, rnd, TileKind.POLICE, 10)
        scatter(city, rnd, TileKind.SCHOOL, 8)
        // 工業は電気を食うので、火力を多めに
        scatter(city, rnd, TileKind.POWER_COAL, 20)
        scatter(city, rnd, TileKind.POWER_SOLAR, 6)

        monuments(city, listOf(
            Monument.COLOSSEUM to (26 to 24),
            Monument.BIG_BEN to (34 to 30),
            Monument.PYRAMID to (42 to 40),
            Monument.TOKYO_TOWER to (22 to 34),
        ))

        // 財政が最も健全なので、資金を厚く持たせる
        return finish(city, funds = 600_000, month = 1_300, taxRate = 9)
    }

    // ------------------------------------------------------------------

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
