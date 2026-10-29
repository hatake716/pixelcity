package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Monument
import io.github.hatake716.pixelcity.game.Ordinance
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
 * 4種類とも、そのまま黒字で回るように作ってある。
 */
object ShowcaseCity {

    /** 見本の種類。 */
    enum class Kind(
        val label: String,
        val summary: String,
        val seed: Long,
    ) {
        HARBOUR(
            label = "木漏れ日の港町",
            summary = "色とりどりの家、水辺のカフェ、緑の広場。市長として暮らしを育てる街。",
            seed = 20260912L,
        ),
        /** 大都市。人口と密度が最大級で、財政と人口のつりあいが取れている。 */
        METROPOLIS(
            label = "大都市",
            summary = "人口と密度が最大級。財政との均衡が取れた街。",
            seed = 20260911L,
        ),

        /** 田園都市。農業と環境への配慮が行き届いている。 */
        GARDEN(
            label = "田園都市",
            summary = "農地と緑が豊か。太陽光・風力・鉄道で環境に優しい街。",
            seed = 31415926L,
        ),

        /** 工業都市。工業が中心で、財政が最も健全。 */
        INDUSTRIAL(
            label = "工業都市",
            summary = "工業が中心。財政が最も健全な街。",
            seed = 27182818L,
        ),
    }

    /** 見本を組み立てる。同じ [kind] からは必ず同じ街ができる。 */
    fun build(kind: Kind = Kind.METROPOLIS): City = when (kind) {
        Kind.HARBOUR -> harbour()
        Kind.METROPOLIS -> metropolis()
        Kind.GARDEN -> garden()
        Kind.INDUSTRIAL -> industrial()
    }

    /** A playable coastal neighbourhood, built from the same zoning and service systems. */
    private fun harbour(): City {
        val city = City(60, 60)
        for(y in 0 until 60) for(x in 0 until 60) {
            val t = city.tileAt(x,y)
            t.terrain = if (y > 43 || (x < 7 && y > 27)) Terrain.WATER else Terrain.LAND
        }
        markShores(city)
        for(y in 6..42) for(x in 8..52) {
            val k = when {
                x % 3 == 2 || y % 4 == 2 -> if(y == 30 || x == 26) TileKind.AVENUE else TileKind.ROAD
                y < 12 -> TileKind.ZONE_I
                (x + y) % 13 == 0 -> TileKind.PARK
                y > 34 || (x in 24..30 && y in 20..29) -> TileKind.ZONE_C
                else -> TileKind.ZONE_R
            }
            place(city,x,y,k)
            if(k.isZone) city.tileAt(x,y).stage = if(y > 34) 2 else if((x*3+y)%7==0)3 else 2
        }
        for(x in 8..52) { place(city,x,4,TileKind.ROAD); place(city,x,5,TileKind.ROAD) }
        for(x in 9..51 step 3) place(city,x,3,TileKind.POWER_SOLAR)
        for(x in 12..39 step 9) place(city,x,2,TileKind.POWER_SOLAR)
        for(y in 9..41 step 8) for(x in 12..48 step 12) {
            place(city,x,y,TileKind.WATER_TOWER)
            place(city,x+1,y,TileKind.PARK)
        }
        for(y in listOf(17,29,37))for(x in listOf(15,39)) {
            place(city,x,y,TileKind.SCHOOL)
            place(city,x+1,y,TileKind.CLINIC)
            place(city,x,y+4,TileKind.POLICE)
        }
        for(x in 12..48 step 6)place(city,x,7,TileKind.RECYCLING)
        for(x in 15..45 step 6)place(city,x,30,TileKind.BUS_STOP)
        for(x in 9..51)place(city,x,43,TileKind.PARK)
        place(city,42,43,TileKind.SEAPORT)
        place(city,24,37,TileKind.FIRE)
        place(city,33,33,TileKind.HOSPITAL)
        monuments(city,listOf(Monument.BIG_BEN to (29 to 34)))
        city.disasterLevel = City.DisasterLevel.LOW
        return finish(city, funds=100_000, month=120, taxRate=7, style=City.Style.EUROPE)
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
        // 川。幅は1で、街を分断しすぎないようにする。
        var rx = 10
        for (y in 0 until 46) {
            if (city.inBounds(rx, y)) city.tileAt(rx, y).terrain = Terrain.WATER
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
    private fun finish(
        city: City,
        funds: Int,
        month: Int,
        taxRate: Int,
        style: City.Style = City.Style.STANDARD,
    ): City {
        city.style = style
        city.funds = funds
        city.month = month
        city.taxRate = taxRate
        city.recomputeDerivedState()
        city.refreshBudgetForecast()
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
        // 湾に面した都市。水際まで街が迫る。
        val (city, rnd) = blank(Kind.METROPOLIS.seed, 55, 60, 60)
        // 細かい碁盤の目。街区を小さく取ると、びっしり建て込んで見える。
        grid(city, step = 3, cross = 3)
        // 大通りを十字に通す。都心の骨格になる。
        for (x in 2 until 58) place(city, x, 28, TileKind.AVENUE)
        for (y in 3 until 57) place(city, 30, y, TileKind.AVENUE)

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

        scatter(city, rnd, TileKind.PARK, 30)
        scatter(city, rnd, TileKind.SCHOOL, 8)
        scatter(city, rnd, TileKind.HOSPITAL, 14)
        scatter(city, rnd, TileKind.POLICE, 8)
        scatter(city, rnd, TileKind.FIRE, 8)
        scatter(city, rnd, TileKind.POWER_SOLAR, 6)
        scatter(city, rnd, TileKind.POWER_COAL, 26)
        // 上下水道とゴミ処理。v2 の仕組みを備えた模範的な街にする。
        scatter(city, rnd, TileKind.WATER_PLANT, 24)
        scatter(city, rnd, TileKind.WATER_TOWER, 6)
        scatter(city, rnd, TileKind.SEWAGE_PLANT, 6)
        // ゴミは毎月7,000ほど出る。焼却場400・リサイクル250なので、
        // 足りるだけ置く（測って決めた数）。
        scatter(city, rnd, TileKind.INCINERATOR, 14)
        scatter(city, rnd, TileKind.RECYCLING, 12)
        scatter(city, rnd, TileKind.CLINIC, 12)
        scatter(city, rnd, TileKind.BUS_STOP, 24)

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

        // 条例で、ゴミと犯罪と電力を抑える。手本として、使い方も見せる。
        city.ordinances.add(Ordinance.RECYCLING)
        city.ordinances.add(Ordinance.PATROL)
        return finish(city, funds = 250_000, month = 1_200, taxRate = 9, style = City.Style.PRIME)
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
        // 小さな湖と、いくつもの川。水と緑の街。
        val (city, rnd) = blank(Kind.GARDEN.seed, 14, 48, 40)
        // 区画はゆったり。ただし、どの区分も道に接するようにする。
        grid(city, step = 4, cross = 5)

        // 川をもう1本。水と緑が この街の見た目を決める。
        run {
            var rx = 44
            for (y in 0 until city.height) {
                if (city.inBounds(rx, y)) city.tileAt(rx, y).terrain = Terrain.WATER
                rx += rnd.nextInt(3) - 1
                rx = rx.coerceIn(40, 50)
            }
        }
        markShores(city)

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
                // 駅前だけが、ささやかな中心部。それでも3階建てまで。
                // 高層ビルを建てないことが、この街の見た目の特徴になる。
                d < 10 -> {
                    t.kind = if (n < 60) TileKind.ZONE_C else TileKind.ZONE_R
                    t.stage = if (n < 70) 2 else 1
                }
                // そのまわりは、ひくい住宅と商店が、緑にまぎれて建つ
                d < 20 -> {
                    when {
                        n < 26 -> { t.kind = TileKind.ZONE_C; t.stage = 2 }
                        n < 34 -> { t.kind = TileKind.ZONE_I; t.stage = 1 }
                        n < 52 -> { t.kind = TileKind.FARM; t.stage = 1 }
                        else -> { t.kind = TileKind.ZONE_R; t.stage = if (n < 76) 2 else 1 }
                    }
                }
                // 外はほとんど農地。ぽつぽつと家が建つ。
                else -> {
                    when {
                        n < 74 -> { t.kind = TileKind.FARM; t.stage = 1 }
                        n < 80 -> { t.kind = TileKind.ZONE_C; t.stage = 1 }
                        else -> { t.kind = TileKind.ZONE_R; t.stage = 1 }
                    }
                }
            }
        }

        // 緑と公共サービスを厚く
        scatter(city, rnd, TileKind.PARK, 30)
        scatter(city, rnd, TileKind.SCHOOL, 6)
        scatter(city, rnd, TileKind.HOSPITAL, 5)
        scatter(city, rnd, TileKind.POLICE, 5)
        scatter(city, rnd, TileKind.FIRE, 5)
        // 電力は太陽光と風力だけ。火力は1つも置かない。
        // 電力は太陽光と風力だけ。人口に見合う数に留める。
        scatter(city, rnd, TileKind.POWER_SOLAR, 12)
        scatter(city, rnd, TileKind.POWER_WIND, 12)
        // 田園都市は環境が売り。水とゴミの処理も行き届かせる。
        // 浄水場は半径14と広いので、少ない数で街全体に水を届けられる。
        scatter(city, rnd, TileKind.WATER_PLANT, 12)
        scatter(city, rnd, TileKind.WATER_TOWER, 4)
        scatter(city, rnd, TileKind.SEWAGE_PLANT, 3)
        // 田園都市は焼却場を使わず、リサイクルだけでまかなう
        scatter(city, rnd, TileKind.RECYCLING, 14)
        scatter(city, rnd, TileKind.CLINIC, 8)
        scatter(city, rnd, TileKind.BUS_STOP, 10)

        monuments(city, listOf(
            Monument.TAJ_MAHAL to (24 to 26),
            Monument.ARC_DE_TRIOMPHE to (36 to 32),
            Monument.LEANING_TOWER to (20 to 44),
        ))

        // 環境の街らしく、リサイクルと公共交通の条例を入れる
        // 環境の街らしく、リサイクルの条例だけ入れる
        city.ordinances.add(Ordinance.RECYCLING)
        return finish(city, funds = 180_000, month = 1_100, taxRate = 9, style = City.Style.RURAL)
    }

    // ------------------------------------------------------------------
    // 工業都市
    // ------------------------------------------------------------------

    /**
     * 工業が中心の大都市。工業と商業で税収を稼ぎ、財政が最も厚い。
     * そのかわり公害が多く、公園と病院で押さえこんでいる。
     */
    private fun industrial(): City {
        // 港に面した平地。工業用地を広く取る。
        val (city, rnd) = blank(Kind.INDUSTRIAL.seed, 54, 54, 110)
        // 工業用地らしく、たてよこに太い直線。区画は横長にとる。
        grid(city, step = 4, cross = 3)
        // 貨物を運ぶ大通りを2本
        for (x in 2 until 58) { place(city, x, 18, TileKind.AVENUE); place(city, x, 40, TileKind.AVENUE) }

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
                // その周りは労働者の住宅が主。
                // 工業を厚くしすぎると、働く人が足りずに工業需要が底を打つ。
                d < 20 -> {
                    t.kind = when {
                        n < 26 -> TileKind.ZONE_C
                        n < 40 -> TileKind.ZONE_I      // 中間にも軽工業を混ぜる
                        else -> TileKind.ZONE_R
                    }
                    t.stage = when { n < 56 -> 3; n < 88 -> 2; else -> 1 }
                }
                // 外周は工業地帯。住宅も混ぜて、働き手を近くに置く。
                else -> {
                    t.kind = when {
                        n < 62 -> TileKind.ZONE_I
                        n < 72 -> TileKind.ZONE_C
                        else -> TileKind.ZONE_R
                    }
                    t.stage = when { n < 70 -> 3; n < 92 -> 2; else -> 1 }
                }
            }
        }

        // 公害対策を厚めに
        // 公害を抑えるため、緑を厚く取る
        scatter(city, rnd, TileKind.PARK, 70)
        scatter(city, rnd, TileKind.FARM, 40)
        scatter(city, rnd, TileKind.HOSPITAL, 14)
        scatter(city, rnd, TileKind.FIRE, 12)
        scatter(city, rnd, TileKind.POLICE, 10)
        scatter(city, rnd, TileKind.SCHOOL, 8)
        // 工業は電気を食うので、火力を多めに
        scatter(city, rnd, TileKind.POWER_COAL, 28)
        scatter(city, rnd, TileKind.POWER_SOLAR, 6)
        // 工業は水を食い、ゴミも多い。処理を厚くする。
        scatter(city, rnd, TileKind.WATER_PLANT, 20)
        scatter(city, rnd, TileKind.WATER_TOWER, 6)
        scatter(city, rnd, TileKind.SEWAGE_PLANT, 8)
        scatter(city, rnd, TileKind.INCINERATOR, 6)
        scatter(city, rnd, TileKind.RECYCLING, 6)
        scatter(city, rnd, TileKind.CLINIC, 12)
        scatter(city, rnd, TileKind.SEAPORT, 2)

        monuments(city, listOf(
            Monument.COLOSSEUM to (26 to 24),
            Monument.BIG_BEN to (34 to 30),
            Monument.PYRAMID to (42 to 40),
            Monument.TOKYO_TOWER to (22 to 34),
        ))

        // 財政が最も健全なので、資金を厚く持たせる。
        // 工業は電気を食うので、省エネ条例で支出を抑える。
        city.ordinances.add(Ordinance.ENERGY_SAVING)
        city.ordinances.add(Ordinance.RECYCLING)
        return finish(city, funds = 600_000, month = 1_300, taxRate = 10, style = City.Style.GRITTY)
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
