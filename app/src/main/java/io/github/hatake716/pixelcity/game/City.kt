package io.github.hatake716.pixelcity.game

import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * 都市の状態と、毎月のシミュレーション。
 *
 * シミュレーションの順序は [step] のとおりに固定されている。単体テストが
 * この順序に依存しているので、変更するときは docs/SPEC.md と test を合わせて直すこと。
 */
class City(
    val width: Int = DEFAULT_SIZE,
    val height: Int = DEFAULT_SIZE,
) {
    companion object {
        /**
         * マップの一辺。128×128 = 16,384 タイル。
         *
         * 毎月の計算はタイル数に比例するが、施設の影響範囲（[spread]）は
         * 半径で頭打ちになるので、1タイルあたりの費用は広げても増えない。
         */
        const val DEFAULT_SIZE = 128
        const val STARTING_FUNDS = 20_000

        /** 推移グラフに残す月数。 */
        const val HISTORY_MONTHS = 120

        /** 溜められるゴミの上限。これ以上は増えない。 */
        const val GARBAGE_BACKLOG_MAX = 12_000
        const val DEFAULT_TAX_RATE = 7

        /** 需要が1か月に動ける幅。大きいほど街が振動しやすい。 */
        private const val DEMAND_STEP = 12

        /** 資金がマイナスのまま この月数 が続くとゲームオーバー。 */
        const val BANKRUPT_MONTHS = 12
        /** 破綻の警告を出し始める月数。 */
        const val BANKRUPT_WARN_MONTHS = 6

        /** 住宅の段階ごとの人口。索引は stage。 */
        private val RESIDENTIAL_POP = intArrayOf(0, 8, 20, 40)
        /** 商業・工業の段階ごとの雇用。 */
        private val COMMERCIAL_JOBS = intArrayOf(0, 10, 24, 48)
        private val INDUSTRIAL_JOBS = intArrayOf(0, 12, 28, 56)
    }

    val tiles: Array<Tile> = Array(width * height) { Tile() }

    var funds: Int = STARTING_FUNDS
    var month: Int = 0
    var taxRate: Int = DEFAULT_TAX_RATE
    var population: Int = 0
    var jobs: Int = 0

    var powerSupply: Int = 0
    var powerDemand: Int = 0

    var demandR: Int = 0
    var demandC: Int = 0
    var demandI: Int = 0

    var lastIncome: Int = 0
    var lastUpkeep: Int = 0
    var monthsInDebt: Int = 0
    var gameOver: Boolean = false

    val builtMonuments: MutableSet<Monument> = mutableSetOf()

    // ------------------------------------------------------------------
    // v2: 水・ゴミ・犯罪・疫病・交通・条例・統計
    // ------------------------------------------------------------------

    var waterSupply: Int = 0
    var waterDemand: Int = 0

    /** 毎月出るゴミと、処理できる量。 */
    var garbageProduced: Int = 0
    var garbageCapacity: Int = 0
    /** 処理しきれずに溜まったゴミ。街の地価と健康を下げる。 */
    var garbageBacklog: Int = 0

    /** 街全体の感染度 0..100。高いと人口が減る。 */
    var infection: Int = 0

    /** 渋滞しているタイルの割合 0..100。 */
    var congestionRate: Int = 0

    /** 失業率 0..100。 */
    var unemployment: Int = 0

    /** 有効にしている条例。 */
    val ordinances: MutableSet<Ordinance> = mutableSetOf()

    /** 災害の起きやすさ。 */
    var disasterLevel: DisasterLevel = DisasterLevel.NORMAL

    /** 直近に起きた災害の知らせ。UIが読んで消す。 */
    var lastDisaster: String? = null

    /**
     * 街並みの様式。建物の色づかいを変えるだけで、計算には関わらない。
     * お手本の街を見分けやすくするために使う。
     */
    enum class Style { STANDARD, RURAL, GRITTY, PRIME }

    var style: Style = Style.STANDARD

    var lastIncomeBreakdown: Income = Income()
    var lastSpending: Spending = Spending()

    /** 過去の推移。古いものから捨てる。 */
    val history: MutableList<MonthlyStat> = mutableListOf()

    /** 乱数。災害などに使う。同じ街から同じ結果が出るよう、月から作る。 */
    private fun monthlyRandom(salt: Int): Random = Random(month * 7919L + salt)

    val waterRatio: Float
        get() = if (waterDemand <= 0) 1f else min(1f, waterSupply.toFloat() / waterDemand)

    /**
     * 平均の値。統計と助言に使う。
     *
     * 空き地まで含めて平均すると、広いマップではどの指標もほぼ 0 になり、
     * 街の様子が見えなくなる。**建物のある場所だけ**で平均を取る。
     */
    private inline fun averageOver(pick: (Tile) -> Int): Int {
        var sum = 0
        var n = 0
        for (t in tiles) {
            if (t.kind == TileKind.EMPTY) continue
            sum += pick(t)
            n++
        }
        return if (n == 0) 0 else sum / n
    }

    val averagePollution: Int get() = averageOver { it.pollution }
    val averageCrime: Int get() = averageOver { it.crime }
    val averageLandValue: Int get() = averageOver { it.landValue }
    val averageHealth: Int get() = averageOver { it.health }

    /**
     * 市民の満足度 0..100。街の良し悪しをひとつの数にまとめたもの。
     * 助言や、遊ぶ人が自分の街を測る目安に使う。
     */
    val approval: Int
        get() {
            var v = 50
            v += (averageLandValue - 30) / 2
            v -= averagePollution / 2
            v -= averageCrime / 2
            v -= unemployment / 2
            v -= infection / 3
            v -= congestionRate / 4
            if (powerRatio < 1f) v -= 20
            if (waterRatio < 1f) v -= 20
            if (garbageBacklog > 2_000) v -= 15
            if (funds < 0) v -= 15
            return v.coerceIn(0, 100)
        }

    fun index(x: Int, y: Int): Int = y * width + x
    fun inBounds(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height
    fun tileAt(x: Int, y: Int): Tile = tiles[index(x, y)]
    fun tileOrNull(x: Int, y: Int): Tile? = if (inBounds(x, y)) tiles[index(x, y)] else null

    /** 電力の充足率。1.0 で全需要をまかなえている。 */
    val powerRatio: Float
        get() = if (powerDemand <= 0) 1f else min(1f, powerSupply.toFloat() / powerDemand)

    /** 観光収入の合計。 */
    val tourismIncome: Int
        get() = builtMonuments.sumOf { it.tourismIncome }

    // ------------------------------------------------------------------
    // 地形生成
    // ------------------------------------------------------------------

    /**
     * 海と川のある地形を作る。同じ [seed] からは必ず同じ地形ができる。
     * 建設可能な陸地が十分に残るよう、水は左下の海と、一本の川に限る。
     */
    fun generateTerrain(seed: Long) {
        val rnd = Random(seed)
        for (t in tiles) { t.terrain = Terrain.LAND; t.clearForBulldoze() }

        // 下の端を海にする。マップの大きさに比例させ、
        // 広いマップでも「海辺の街」と分かる厚みを持たせる。
        val seaDepth = (height / 10).coerceAtLeast(3)
        val seaBase = height - seaDepth - rnd.nextInt(2)
        // 海岸線の揺らぎも、マップの広さに応じて大きくとる。
        val coastWave = (height / 24).coerceAtLeast(1)
        for (x in 0 until width) {
            val edge = seaBase + ((x * 3 + rnd.nextInt(3)) % (coastWave * 2 + 1)) - coastWave
            for (y in max(0, edge) until height) tileAt(x, y).terrain = Terrain.WATER
        }

        // 上から下へ蛇行する川を引く。広いマップでは本数と幅を増やす。
        val riverCount = (width / 48).coerceAtLeast(1)
        val riverWidth = (width / 64).coerceAtLeast(1)
        for (n in 0 until riverCount) {
            // 川どうしが重ならないよう、幅を等分した帯の中から始める。
            val band = width / (riverCount + 1)
            var cx = band * (n + 1) + rnd.nextInt(band / 2) - band / 4
            for (y in 0 until height) {
                for (d in 0 until riverWidth) {
                    val x = cx + d
                    if (inBounds(x, y)) tileAt(x, y).terrain = Terrain.WATER
                }
                cx += rnd.nextInt(3) - 1
                cx = cx.coerceIn(2, width - riverWidth - 2)
            }
        }

        markShores()
    }

    /**
     * 街の中心に、必ず平らな土地を用意する。
     *
     * チュートリアルは「道路を引いて、その両側に区分を置く」という手順を踏むので、
     * 中心が川や海だと最初の一歩が踏み出せない。生成のたびに詰まないよう、
     * 中央の一画だけは必ず陸地にする。
     */
    fun clearStartingArea(
        halfWidth: Int = (width / 5).coerceAtLeast(7),
        halfHeight: Int = (height / 7).coerceAtLeast(5),
    ) {
        val cx = width / 2
        val cy = height / 2
        for (y in (cy - halfHeight)..(cy + halfHeight)) {
            for (x in (cx - halfWidth)..(cx + halfWidth)) {
                if (inBounds(x, y)) tileAt(x, y).terrain = Terrain.LAND
            }
        }
        // 平らにした縁で、水辺の判定をやり直す。
        for (t in tiles) if (t.terrain == Terrain.SHORE) t.terrain = Terrain.LAND
        markShores()
    }

    /** 水に接する陸地を SHORE にする。地価の計算で使う。 */
    private fun markShores() {
        for (y in 0 until height) for (x in 0 until width) {
            val t = tileAt(x, y)
            if (t.terrain != Terrain.LAND) continue
            if (neighbors4(x, y).any { it.terrain == Terrain.WATER }) t.terrain = Terrain.SHORE
        }
    }

    private fun neighbors4(x: Int, y: Int): List<Tile> = buildList {
        tileOrNull(x - 1, y)?.let { add(it) }
        tileOrNull(x + 1, y)?.let { add(it) }
        tileOrNull(x, y - 1)?.let { add(it) }
        tileOrNull(x, y + 1)?.let { add(it) }
    }

    /**
     * 4近傍に道路または線路があるか。区分が育つための最低条件。
     * 鉄道も交通として数えるので、駅前に街をつくれる。
     */
    fun touchesRoad(x: Int, y: Int): Boolean {
        var found = false
        forEachNeighbor4(x, y) { nx, ny ->
            val k = tileAt(nx, ny).kind
            if (k == TileKind.ROAD || k == TileKind.RAIL) found = true
        }
        return found
    }

    fun canBuildOn(x: Int, y: Int): Boolean {
        val t = tileOrNull(x, y) ?: return false
        return t.terrain != Terrain.WATER
    }

    // ------------------------------------------------------------------
    // 建設
    // ------------------------------------------------------------------

    /** 建設できない理由。null なら建設できる。 */
    fun buildBlocker(x: Int, y: Int, kind: TileKind): String? {
        if (!inBounds(x, y)) return "はんいの そとです"
        val t = tileAt(x, y)
        if (t.terrain == Terrain.WATER) return "みずのうえには たてられません"
        if (t.monument != null || t.monumentAnchor >= 0) return "モニュメントが あります"
        if (t.kind == kind && !kind.isZone) return "すでに あります"
        // 施設の上に、なぞって区分や道路を敷いてしまう事故を防ぐ。
        // 建て替えたいときは「こわす」で明示的に壊してもらう。
        if (t.kind.isBuilding && !t.kind.isZone && kind != t.kind) {
            return "さきに こわしてください"
        }
        if (!BuildCost.isUnlocked(kind, population)) return "まだ かいきんされていません"
        val cost = BuildCost.cost(kind) + if (t.kind != TileKind.EMPTY) BuildCost.BULLDOZE else 0
        if (funds < cost) return "しきんが たりません"
        return null
    }

    /** 1タイルに建設する。成功したら true。 */
    fun build(x: Int, y: Int, kind: TileKind): Boolean {
        if (buildBlocker(x, y, kind) != null) return false
        val t = tileAt(x, y)
        var cost = BuildCost.cost(kind)
        if (t.kind != TileKind.EMPTY) cost += BuildCost.BULLDOZE
        funds -= cost
        t.clearForBulldoze()
        t.kind = kind
        t.stage = if (kind.isZone) 0 else 1
        return true
    }

    fun bulldoze(x: Int, y: Int): Boolean {
        val t = tileOrNull(x, y) ?: return false
        if (t.kind == TileKind.EMPTY) return false
        if (funds < BuildCost.BULLDOZE) return false
        // モニュメントは占有タイルのどこを押しても、全体を取り壊す。
        val anchor = t.monumentAnchor
        funds -= BuildCost.BULLDOZE
        if (anchor >= 0) {
            val m = tiles[anchor].monument
            if (m != null) builtMonuments.remove(m)
            for (tt in tiles) if (tt.monumentAnchor == anchor) tt.clearForBulldoze()
        } else {
            t.clearForBulldoze()
        }
        return true
    }

    // --- モニュメント（2x2 を占める） ---

    fun monumentBlocker(x: Int, y: Int, m: Monument): String? {
        if (m in builtMonuments) return "すでに たてられています"
        if (population < m.unlockPopulation) return "じんこう ${m.unlockPopulation} で かいきん"
        if (funds < m.cost) return "しきんが たりません"
        for (dy in 0..1) for (dx in 0..1) {
            val tx = x + dx
            val ty = y + dy
            if (!inBounds(tx, ty)) return "はんいの そとです"
            val t = tileAt(tx, ty)
            if (t.terrain == Terrain.WATER) return "みずのうえには たてられません"
            if (t.monumentAnchor >= 0) return "モニュメントが あります"
        }
        if (m.needsWater) {
            val touchesWater = (-1..2).any { d ->
                listOf(
                    tileOrNull(x + d, y - 1), tileOrNull(x + d, y + 2),
                    tileOrNull(x - 1, y + d), tileOrNull(x + 2, y + d),
                ).any { it?.terrain == Terrain.WATER }
            }
            if (!touchesWater) return "みずべに たててください"
        }
        return null
    }

    fun buildMonument(x: Int, y: Int, m: Monument): Boolean {
        if (monumentBlocker(x, y, m) != null) return false
        funds -= m.cost
        val anchor = index(x, y)
        for (dy in 0..1) for (dx in 0..1) {
            val t = tileAt(x + dx, y + dy)
            t.clearForBulldoze()
            t.kind = TileKind.MONUMENT
            t.monumentAnchor = anchor
            t.stage = 1
        }
        tiles[anchor].monument = m
        builtMonuments.add(m)
        return true
    }

    /** その人口で解禁済みのモニュメント。 */
    fun availableMonuments(): List<Monument> =
        Monument.entries.filter { it !in builtMonuments && population >= it.unlockPopulation }

    /** まだ解禁されていない次のモニュメント。目標として画面に出す。 */
    fun nextLockedMonument(): Monument? =
        Monument.entries.filter { it !in builtMonuments }.minByOrNull { it.unlockPopulation }

    // ------------------------------------------------------------------
    // シミュレーション
    // ------------------------------------------------------------------

    /**
     * 保存から復元した直後に、タイルから導かれる値を組み直す。
     *
     * 人口や雇用は毎月の [step] でしか計算していないため、読み込み直後は 0 のままで、
     * モニュメントの解禁判定などが誤る。月を進めずに整合させるために使う。
     */
    fun recomputeDerivedState() {
        updateConnectivity()
        updatePower()
        updateLocalValues()
        tallyPopulation()
    }

    /** 1か月進める。順序は docs/SPEC.md §5 のとおり。 */
    /**
     * 1か月進める。
     *
     * 順序には意味がある。交通は人口と雇用から決まり、犯罪と疫病は
     * 地価や公害の結果に依る。ここを入れ替えると街の挙動が変わるので、
     * 変更するときは docs/SPEC_V2.md とテストを合わせて直すこと。
     */
    fun step() {
        if (gameOver) return
        month++
        updateConnectivity()
        updatePower()
        updateWater()
        updateTraffic()
        updateLocalValues()
        updateCrime()
        updateGarbage()
        updateInfection()
        updateDemand()
        updateZoneStages()
        tallyPopulation()
        applyBudget()
        runDisasters()
        recordHistory()
        checkBankruptcy()
    }


    // ------------------------------------------------------------------
    // v2: 水
    // ------------------------------------------------------------------

    /**
     * 水の需給。電力と同じく、足りないぶんのタイルには水が来ない。
     * 給水は施設の半径で表す（水道管を引かせると操作が増えすぎるため）。
     */
    private fun updateWater() {
        var supply = 0
        var demand = 0
        for (t in tiles) {
            supply += BuildCost.waterOutput(t.kind)
            demand += waterNeedOf(t)
        }
        waterSupply = supply
        waterDemand = demand

        // まず全部を「水なし」にしてから、給水施設の届く範囲を塗る
        for (t in tiles) t.watered = false
        var remaining = supply
        for (y in 0 until height) for (x in 0 until width) {
            val t = tileAt(x, y)
            val r = when (t.kind) {
                TileKind.WATER_TOWER -> 10
                TileKind.WATER_PLANT -> 14
                else -> continue
            }
            spread(x, y, r) { n, _ ->
                val need = waterNeedOf(n)
                if (!n.watered && need > 0 && remaining >= need) {
                    n.watered = true
                    remaining -= need
                }
            }
        }
        // 施設そのものは自前でまかなう
        for (t in tiles) {
            if (t.kind == TileKind.WATER_TOWER || t.kind == TileKind.WATER_PLANT) t.watered = true
        }
    }

    private fun waterNeedOf(t: Tile): Int = when {
        t.kind.isZone -> 6 + t.stage * 10
        t.kind == TileKind.FARM -> 8
        t.kind.isBuilding -> 8
        else -> 0
    }

    // ------------------------------------------------------------------
    // v2: 交通
    // ------------------------------------------------------------------

    /**
     * 交通量を求める。
     *
     * 一人ひとりの経路を追うのは重すぎるので、
     *  1. 住宅から「通勤の量」を出す
     *  2. 交通網を幅優先でたどり、距離で減衰させながら配る
     * という近似をとる。渋滞の起きる場所（幹線と交差点）は、これで十分に再現できる。
     */
    private fun updateTraffic() {
        for (t in tiles) {
            t.traffic = 0
            t.transitRelief = 0
        }

        // 公共交通の効き目を先に塗る
        for (y in 0 until height) for (x in 0 until width) {
            val t = tileAt(x, y)
            val (r, relief) = when (t.kind) {
                TileKind.BUS_STOP -> 6 to 25
                TileKind.SUBWAY_STATION -> 8 to 40
                else -> continue
            }
            spread(x, y, r) { n, f ->
                n.transitRelief = max(n.transitRelief, (relief * f).toInt())
            }
        }

        // 住宅から通勤の量を流す
        val queue = ArrayDeque<Int>()
        val load = IntArray(tiles.size)
        for (y in 0 until height) for (x in 0 until width) {
            val t = tileAt(x, y)
            if (t.kind != TileKind.ZONE_R || t.stage == 0) continue
            val commuters = t.stage * 14
            forEachNeighbor4(x, y) { nx, ny ->
                if (tileAt(nx, ny).kind.isTransport) {
                    val i = index(nx, ny)
                    if (load[i] == 0) queue.add(i)
                    load[i] += commuters
                }
            }
        }

        // 網をたどって広げる。距離で減っていく。
        var guard = 0
        val maxSteps = tiles.size * 4
        while (queue.isNotEmpty() && guard++ < maxSteps) {
            val i = queue.poll()
            val amount = load[i]
            if (amount <= 0) continue
            load[i] = 0
            val t = tiles[i]
            t.traffic += amount

            // 減衰。半分ずつ隣へ渡す。
            val pass = amount / 2
            if (pass < 8) continue
            val x = i % width
            val y = i / width
            val next = mutableListOf<Int>()
            forEachNeighbor4(x, y) { nx, ny ->
                if (tileAt(nx, ny).kind.isTransport) next.add(index(nx, ny))
            }
            if (next.isEmpty()) continue
            val each = pass / next.size
            if (each < 4) continue
            for (ni in next) {
                if (tiles[ni].traffic > tiles[ni].kind.capacity * 3) continue
                if (load[ni] == 0) queue.add(ni)
                load[ni] += each
            }
        }

        // 公共交通と条例で交通量を減らす
        val subsidy = if (Ordinance.TRANSIT_SUBSIDY in ordinances) 15 else 0
        var congested = 0
        var roads = 0
        for (t in tiles) {
            if (t.kind.capacity <= 0) continue
            val cut = (t.transitRelief + subsidy).coerceAtMost(70)
            t.traffic = t.traffic * (100 - cut) / 100
            roads++
            if (t.congested) congested++
        }
        congestionRate = if (roads == 0) 0 else congested * 100 / roads
    }

    // ------------------------------------------------------------------
    // v2: 犯罪
    // ------------------------------------------------------------------

    /**
     * 犯罪の起きやすさ。人口密度・失業・低い地価が上げ、
     * 警察と教育と地価が下げる。
     */
    private fun updateCrime() {
        for (t in tiles) {
            if (!t.kind.isZone || t.stage == 0) {
                t.crime = 0
                continue
            }
            var c = 22 + t.stage * 8
            c += unemployment / 2
            c -= t.landValue / 3
            c -= t.education / 4
            c -= t.safety / 2
            t.crime = c.coerceIn(0, 100)
        }
        if (Ordinance.PATROL in ordinances) {
            for (t in tiles) t.crime = t.crime * 80 / 100
        }
    }

    // ------------------------------------------------------------------
    // v2: ゴミ
    // ------------------------------------------------------------------

    /**
     * ゴミの需給。処理しきれないと溜まり、街の地価と健康を下げる。
     * 埋立地は容量があり、満杯になると受け入れられなくなる。
     */
    private fun updateGarbage() {
        var produced = 0
        for (t in tiles) {
            produced += when {
                t.kind == TileKind.ZONE_R -> t.stage * 3
                t.kind == TileKind.ZONE_C -> t.stage * 2
                t.kind == TileKind.ZONE_I -> t.stage * 5
                else -> 0
            }
        }
        if (Ordinance.RECYCLING in ordinances) produced = produced * 75 / 100
        garbageProduced = produced

        var capacity = 0
        for (t in tiles) {
            when (t.kind) {
                TileKind.LANDFILL -> {
                    // 満杯になったら受け入れない
                    if (t.landfillFill < BuildCost.LANDFILL_TOTAL) {
                        capacity += BuildCost.garbageCapacity(t.kind)
                    }
                }
                TileKind.INCINERATOR, TileKind.RECYCLING ->
                    capacity += BuildCost.garbageCapacity(t.kind)
                else -> {}
            }
        }
        garbageCapacity = capacity

        val overflow = produced - capacity
        // 溜まる量には上限を置く。青天井にすると、ゴミ処理を知らないうちに
        // 街が壊れてしまう（処理しないと不便、という程度に留める）。
        garbageBacklog = (garbageBacklog + overflow).coerceIn(0, GARBAGE_BACKLOG_MAX)

        // 埋立地に溜める
        var toBury = min(produced, capacity)
        for (t in tiles) {
            if (t.kind != TileKind.LANDFILL) continue
            if (t.landfillFill >= BuildCost.LANDFILL_TOTAL) continue
            val take = min(toBury, BuildCost.garbageCapacity(TileKind.LANDFILL))
            t.landfillFill += take
            toBury -= take
            if (toBury <= 0) break
        }
    }

    // ------------------------------------------------------------------
    // v2: 疫病
    // ------------------------------------------------------------------

    /**
     * 感染の広がり。人口密度・ゴミ・公害・水不足が上げ、
     * 病院と診療所と公園が下げる。
     */
    private fun updateInfection() {
        var risk = 0
        risk += (population / 1_200).coerceAtMost(30)
        risk += (garbageBacklog / 1_500).coerceAtMost(18)
        risk += averagePollution / 3
        if (waterRatio < 1f) risk += ((1f - waterRatio) * 40).toInt()

        var care = averageHealth / 3
        if (Ordinance.FREE_CLINIC in ordinances) care += 15
        care += tiles.count { it.kind == TileKind.PARK } / 4

        val target = (risk - care).coerceIn(0, 100)
        // 急に変わらないよう、じわじわ近づける
        infection += ((target - infection) / 3).coerceIn(-8, 8)
        infection = infection.coerceIn(0, 100)
    }

    // ------------------------------------------------------------------
    // v2: 災害
    // ------------------------------------------------------------------

    enum class DisasterLevel(val label: String, val chancePerMonth: Int) {
        NONE("なし", 0),
        LOW("ひかえめ", 1),
        NORMAL("ふつう", 3),
        HIGH("おおい", 7),
    }

    /** 災害を起こすか判定し、起きたら被害を出す。 */
    private fun runDisasters() {
        lastDisaster = null
        if (disasterLevel == DisasterLevel.NONE) return
        if (month < 24) return   // 始めたばかりの街は見逃す
        val rnd = monthlyRandom(13)
        if (rnd.nextInt(100) >= disasterLevel.chancePerMonth) return

        when (rnd.nextInt(4)) {
            0 -> fire(rnd)
            1 -> earthquake(rnd)
            2 -> flood(rnd)
            else -> tornado(rnd)
        }
    }

    /** 火事。消防署から遠いところほど燃え広がる。 */
    private fun fire(rnd: Random) {
        val candidates = tiles.indices.filter {
            val t = tiles[it]
            t.kind.isZone && t.stage > 0
        }
        if (candidates.isEmpty()) return
        val at = candidates[rnd.nextInt(candidates.size)]
        val x = at % width
        val y = at / width
        // 消防が近いほど小さく収まる
        val protection = tileAt(x, y).safety
        val radius = (4 - protection / 30).coerceIn(1, 4)
        var burned = 0
        spread(x, y, radius) { t, f ->
            if (t.kind.isZone && t.stage > 0 && rnd.nextInt(100) < (70 * f).toInt()) {
                t.stage = 0
                burned++
            }
        }
        if (burned > 0) lastDisaster = "かじが おきました（${burned}けん しょうしつ）"
    }

    private fun earthquake(rnd: Random) {
        val cx = rnd.nextInt(width)
        val cy = rnd.nextInt(height)
        var broken = 0
        spread(cx, cy, 8) { t, f ->
            if (rnd.nextInt(100) < (55 * f).toInt()) {
                when {
                    t.kind.isZone && t.stage > 0 -> { t.stage = 0; broken++ }
                    t.kind.isTransport -> { t.clearForBulldoze(); broken++ }
                }
            }
        }
        if (broken > 0) lastDisaster = "じしんが おきました（${broken}かしょ ひがい）"
    }

    private fun flood(rnd: Random) {
        // 水辺のタイルを探す
        val shore = tiles.indices.filter { tiles[it].terrain == Terrain.SHORE }
        if (shore.isEmpty()) return
        val at = shore[rnd.nextInt(shore.size)]
        var washed = 0
        spread(at % width, at / width, 5) { t, f ->
            if (t.kind.isZone && t.stage > 0 && rnd.nextInt(100) < (60 * f).toInt()) {
                t.stage = 0
                washed++
            }
        }
        if (washed > 0) lastDisaster = "こうずいが おきました（${washed}けん ひがい）"
    }

    private fun tornado(rnd: Random) {
        var x = rnd.nextInt(width)
        var y = rnd.nextInt(height)
        var destroyed = 0
        repeat(24) {
            spread(x, y, 2) { t, _ ->
                if (t.kind.isZone && t.stage > 0 && rnd.nextInt(100) < 60) {
                    t.stage = 0
                    destroyed++
                }
            }
            x = (x + rnd.nextInt(3) - 1).coerceIn(0, width - 1)
            y = (y + 1).coerceIn(0, height - 1)
        }
        if (destroyed > 0) lastDisaster = "たつまきが はっせいしました（${destroyed}けん ひがい）"
    }

    // ------------------------------------------------------------------
    // v2: 統計
    // ------------------------------------------------------------------

    private fun recordHistory() {
        history.add(
            MonthlyStat(
                month = month,
                population = population,
                funds = funds,
                balance = lastIncome - lastUpkeep,
                pollution = averagePollution,
                crime = averageCrime,
                unemployment = unemployment,
                health = averageHealth,
                traffic = congestionRate,
            ),
        )
        while (history.size > HISTORY_MONTHS) history.removeAt(0)
    }

    /** いま街が困っていること。深刻な順に返す。 */
    fun advice(): List<Advice> {
        val out = mutableListOf<Advice>()
        if (powerRatio < 1f) {
            val short = powerDemand - powerSupply
            val plants = (short + 1_999) / 2_000
            out.add(Advice("でんりょくが たりません。はつでんしょが あと${plants}き ひつようです。", 100))
        }
        if (waterRatio < 1f) {
            out.add(Advice("みずが たりません。きゅうすいとうを ふやしてください。", 95))
        }
        if (funds < 0) {
            out.add(Advice("ざいせいが あかじです。ぜいりつを みなおしてください。", 90))
        }
        if (garbageBacklog > 2_000) {
            out.add(Advice("ゴミが あふれています。しょりしせつを ふやしてください。", 80))
        }
        if (infection > 40) {
            out.add(Advice("かんせんしょうが ひろがっています。びょういんを ふやしてください。", 78))
        }
        if (averageCrime > 45) {
            out.add(Advice("はんざいが ふえています。けいさつしょを ふやしてください。", 70))
        }
        if (congestionRate > 35) {
            out.add(Advice("じゅうたいが ひどいです。おおどおりや こうきょうこうつうを。", 65))
        }
        if (unemployment > 25) {
            out.add(Advice("しつぎょうが おおいです。しょうぎょう・こうぎょうを ふやしてください。", 60))
        }
        if (averagePollution > 40) {
            out.add(Advice("こうがいが ひどいです。こうえんや のうちを ふやしてください。", 55))
        }
        if (demandR > 60 && population > 100) {
            out.add(Advice("じゅうたくの じゅようが たかいです。", 30))
        }
        if (demandC > 60 && population > 100) {
            out.add(Advice("しょうぎょうの じゅようが たかいです。", 28))
        }
        if (out.isEmpty()) {
            out.add(Advice("まちは おちついています。このちょうしで。", 0))
        }
        return out.sortedByDescending { it.severity }
    }

    /**
     * 道路網の連結を求める。発電所を起点に道路を辿り、道路に接する建物・区分を
     * 「繋がっている」とする。発電所が道路に繋がっていなければ、その発電所は孤立する。
     */
    private fun updateConnectivity() {
        for (t in tiles) t.connected = false

        val queue = ArrayDeque<Int>()
        val visited = BooleanArray(tiles.size)

        // 発電所に隣接する道路を起点にする。
        for (y in 0 until height) for (x in 0 until width) {
            val t = tileAt(x, y)
            if (!t.kind.isPowerPlant) continue
            t.connected = true
            forEachNeighbor4(x, y) { nx, ny ->
                val n = tileAt(nx, ny)
                if (isTransport(n.kind) && !visited[index(nx, ny)]) {
                    visited[index(nx, ny)] = true
                    queue.add(index(nx, ny))
                }
            }
        }

        // 道路網を幅優先で辿る。
        while (queue.isNotEmpty()) {
            val i = queue.poll()
            val t = tiles[i]
            t.connected = true
            val x = i % width
            val y = i / width
            forEachNeighbor4(x, y) { nx, ny ->
                val ni = index(nx, ny)
                if (isTransport(tiles[ni].kind) && !visited[ni]) {
                    visited[ni] = true
                    queue.add(ni)
                }
            }
        }

        // 繋がった道路に隣接する区分・建物を、繋がっているとみなす。
        for (y in 0 until height) for (x in 0 until width) {
            val t = tileAt(x, y)
            if (isTransport(t.kind) || t.kind == TileKind.EMPTY) continue
            if (t.connected) continue
            var touching = false
            forEachNeighbor4(x, y) { nx, ny ->
                val n = tileAt(nx, ny)
                if (isTransport(n.kind) && n.connected) touching = true
            }
            t.connected = touching
        }
    }

    /** 4近傍に渋滞した道があるか。区分の成長を鈍らせる。 */
    private fun nearCongestion(x: Int, y: Int): Boolean {
        var found = false
        forEachNeighbor4(x, y) { nx, ny -> if (tileAt(nx, ny).congested) found = true }
        return found
    }

    /** 道路か線路か。どちらも網としてつながる。 */
    private fun isTransport(kind: TileKind): Boolean =
        kind == TileKind.ROAD || kind == TileKind.RAIL

    private inline fun forEachNeighbor4(x: Int, y: Int, body: (Int, Int) -> Unit) {
        if (x > 0) body(x - 1, y)
        if (x < width - 1) body(x + 1, y)
        if (y > 0) body(x, y - 1)
        if (y < height - 1) body(x, y + 1)
    }

    /**
     * 電力の需給を求める。発電所は道路網に繋がっていなくても発電するが、
     * 電気を受け取れるのは繋がっている区分だけ。
     */
    /**
     * 電力の需給を求める。
     *
     * 供給が需要に一歩でも届かないだけで街全体が停電すると、
     * 需要が上下するたびに街が丸ごと明滅してしまう。そこで、供給できる分は
     * 順に配り、足りなかったタイルだけを停電させる（部分停電）。
     * 配る順序は索引順で固定なので、同じ状態からは必ず同じ結果になる。
     */
    private fun updatePower() {
        var supply = 0
        var demand = 0
        for (t in tiles) {
            if (t.kind.isPowerPlant) supply += BuildCost.powerOutput(t.kind)
            demand += powerNeedOf(t)
        }
        powerSupply = supply
        powerDemand = if (Ordinance.ENERGY_SAVING in ordinances) demand * 85 / 100 else demand

        var remaining = supply
        for (t in tiles) {
            val need = powerNeedOf(t)
            if (need == 0) {
                // 発電所自身は自前でまかなう。
                t.powered = t.kind.isPowerPlant
                continue
            }
            if (t.connected && remaining >= need) {
                t.powered = true
                remaining -= need
            } else {
                t.powered = false
            }
        }
    }

    /** そのタイルが必要とする電力。発電所と空き地は 0。 */
    private fun powerNeedOf(t: Tile): Int = when {
        t.kind.isZone -> 8 + t.stage * 12
        t.kind.isPowerPlant -> 0
        t.kind.isBuilding -> 10
        else -> 0
    }

    /**
     * 地価・公害・治安・教育・健康を求める。
     * 施設は半径内に効果を配り、距離で減衰する。
     */
    private fun updateLocalValues() {
        for (t in tiles) {
            t.landValue = when (t.terrain) {
                Terrain.SHORE -> 34  // 水辺は地価が高い
                else -> 26
            }
            t.pollution = 0
            t.safety = 0
            t.education = 0
            t.health = 0
        }

        for (y in 0 until height) for (x in 0 until width) {
            val t = tileAt(x, y)
            when (t.kind) {
                TileKind.PARK -> {
                    spread(x, y, 4) { n, f -> n.landValue += (10 * f).toInt(); n.pollution -= (6 * f).toInt() }
                }
                TileKind.POLICE -> spread(x, y, 8) { n, f -> n.safety += (24 * f).toInt() }
                TileKind.FIRE -> spread(x, y, 8) { n, f -> n.safety += (16 * f).toInt() }
                TileKind.SCHOOL -> spread(x, y, 10) { n, f -> n.education += (26 * f).toInt() }
                TileKind.HOSPITAL -> spread(x, y, 10) { n, f -> n.health += (26 * f).toInt() }
                TileKind.POWER_COAL -> spread(x, y, 5) { n, f -> n.pollution += (26 * f).toInt(); n.landValue -= (8 * f).toInt() }
                // 農地は公害を吸い、まわりを少しだけ気持ちよくする。
                TileKind.FARM -> spread(x, y, 3) { n, f -> n.pollution -= (8 * f).toInt(); n.landValue += (2 * f).toInt() }
                // 風力は静かで、景色をわずかに良くする。
                TileKind.POWER_WIND -> spread(x, y, 3) { n, f -> n.landValue += (3 * f).toInt() }
                // 駅前は地価が上がる。
                TileKind.RAIL -> spread(x, y, 4) { n, f -> n.landValue += (6 * f).toInt() }
                // v2 の施設
                TileKind.SUBWAY_STATION -> spread(x, y, 8) { n, f -> n.landValue += (10 * f).toInt() }
                TileKind.BUS_STOP -> spread(x, y, 5) { n, f -> n.landValue += (3 * f).toInt() }
                TileKind.CLINIC -> spread(x, y, 8) { n, f -> n.health += (18 * f).toInt() }
                TileKind.LANDFILL -> spread(x, y, 6) { n, f ->
                    n.landValue -= (20 * f).toInt(); n.pollution += (14 * f).toInt()
                }
                TileKind.INCINERATOR -> spread(x, y, 6) { n, f ->
                    n.pollution += (22 * f).toInt(); n.landValue -= (10 * f).toInt()
                }
                TileKind.RECYCLING -> spread(x, y, 4) { n, f -> n.landValue -= (4 * f).toInt() }
                TileKind.SEWAGE_PLANT -> spread(x, y, 6) { n, f ->
                    n.pollution -= (10 * f).toInt(); n.landValue -= (6 * f).toInt()
                }
                TileKind.AIRPORT -> spread(x, y, 10) { n, f ->
                    n.pollution += (16 * f).toInt(); n.landValue -= (8 * f).toInt()
                }
                TileKind.SEAPORT -> spread(x, y, 8) { n, f -> n.pollution += (10 * f).toInt() }
                TileKind.HIGHWAY -> spread(x, y, 3) { n, f ->
                    n.pollution += (8 * f).toInt(); n.landValue -= (6 * f).toInt()
                }
                TileKind.ZONE_I -> if (t.stage > 0) {
                    spread(x, y, 3) { n, f -> n.pollution += (7 * t.stage * f).toInt(); n.landValue -= (3 * t.stage * f).toInt() }
                }
                TileKind.ZONE_C -> if (t.stage > 0) {
                    spread(x, y, 3) { n, f -> n.landValue += (3 * t.stage * f).toInt() }
                }
                TileKind.MONUMENT -> {
                    val m = t.monument
                    if (m != null) spread(x, y, m.radius) { n, f -> n.landValue += (m.landValueBonus * f).toInt() }
                }
                else -> {}
            }
        }

        // 溜まったゴミは街全体の公害を増やす。
        //
        // 地価まで下げると、成長の敷居（8）を割って街がすべて消えてしまう。
        // ゴミを放っておくと「育たない・不健康になる」が、
        // 街が消えるほどではない、という重さに留める。
        val garbagePenalty = (garbageBacklog / 3_000).coerceAtMost(5)
        // 教育の条例
        val eduBonus = if (Ordinance.EDUCATION in ordinances) 15 else 0
        val healthBonus = if (Ordinance.FREE_CLINIC in ordinances) 15 else 0
        for (t in tiles) {
            t.pollution += garbagePenalty
            t.education += eduBonus
            t.health += healthBonus
            // 渋滞している道のまわりは公害が増える
            if (t.congested) t.pollution += 6
            t.pollution = t.pollution.coerceIn(0, 100)
            t.landValue = (t.landValue - t.pollution / 5).coerceIn(0, 255)
            t.safety = t.safety.coerceIn(0, 100)
            t.education = t.education.coerceIn(0, 100)
            t.health = t.health.coerceIn(0, 100)
        }
    }

    /** (cx,cy) を中心に半径 r へ、距離で減衰する係数 f を配る。 */
    private inline fun spread(cx: Int, cy: Int, r: Int, body: (Tile, Float) -> Unit) {
        val minX = max(0, cx - r)
        val maxX = min(width - 1, cx + r)
        val minY = max(0, cy - r)
        val maxY = min(height - 1, cy + r)
        for (y in minY..maxY) for (x in minX..maxX) {
            val d = abs(x - cx) + abs(y - cy)
            if (d > r) continue
            val f = 1f - d.toFloat() / (r + 1)
            body(tileAt(x, y), f)
        }
    }

    /**
     * 需要を求める。住宅は職の空き、商業・工業は人口に対して不足していると需要が上がる。
     * 税率が高いと全体の需要が下がる。
     */
    /**
     * 需要を求める。住宅は職の空き、商業・工業は人口に対して不足していると需要が上がる。
     *
     * 目標値へ一気に飛ばすと、全区分が同時に成長・衰退して2か月周期で振動する。
     * 目標へ少しずつ近づける（平滑化する）ことで、街が落ち着いて成長するようにしている。
     */
    private fun updateDemand() {
        val pop = population
        val employable = (pop * 0.55f).toInt()

        // 住宅需要: 職が余っていれば人が来る。
        val targetR = (40 + (jobs - employable) / 4).coerceIn(-100, 100)

        // 商業需要: 人口に対して商業の職が足りないほど高い。
        val wantC = (pop * 0.30f).toInt()
        val targetC = (34 + (wantC - commercialJobs) / 3).coerceIn(-100, 100)

        // 工業需要: 同様。序盤は工業から始まるよう下駄を履かせる。
        val wantI = (pop * 0.34f).toInt()
        val targetI = (30 + (wantI - industrialJobs) / 3).coerceIn(-100, 100)

        // 空港は商業を、港は工業を押し上げる
        val airports = tiles.count { it.kind == TileKind.AIRPORT }
        val ports = tiles.count { it.kind == TileKind.SEAPORT }

        // 税率の影響。7%を基準に、高いほど需要が落ちる。
        val taxPenalty = (taxRate - DEFAULT_TAX_RATE) * 6

        // 電力が足りていなければ、どの需要も伸びない。
        val powerCut = if (powerRatio < 1f) ((1f - powerRatio) * 70).toInt() else 0

        // 水の不足は「段階の上限」で表しているので、需要までは潰さない。
        // ただし、水道を敷いたのに足りていない街には、軽い重しをかける。
        val waterCut = if (waterSupply > 0 && waterRatio < 1f) {
            ((1f - waterRatio) * 25).toInt()
        } else {
            0
        }
        val cut = powerCut + waterCut

        demandR = approach(demandR, targetR - taxPenalty - cut)
        demandC = approach(demandC, targetC - taxPenalty - cut + airports * 20)
        demandI = approach(demandI, targetI - taxPenalty - cut + ports * 20)
    }

    /** 現在値を目標へ [DEMAND_STEP] だけ近づける。振動を抑えるための平滑化。 */
    private fun approach(current: Int, target: Int): Int {
        val clamped = target.coerceIn(-100, 100)
        val delta = clamped - current
        return when {
            delta > DEMAND_STEP -> current + DEMAND_STEP
            delta < -DEMAND_STEP -> current - DEMAND_STEP
            else -> clamped
        }
    }

    private var commercialJobs: Int = 0
    private var industrialJobs: Int = 0

    /**
     * 各区分タイルの段階を上下させる。
     * 上がる条件: 道路に繋がり、電気があり、需要が正で、地価・公害が許すこと。
     */
    /**
     * 各区分タイルの段階を上下させる。
     *
     * 全タイルが同じ条件で同時に成長・衰退すると、雇用と需要が一斉に振れて
     * 街全体が2か月周期で明滅する。それを避けるため、
     *  - 弱い需要不足では すぐに取り壊さず、強い不足のときだけ衰退させる
     *  - タイルごとに少しだけ異なる敷居を与え、成長の足並みをずらす
     * という2点で ばらつきを持たせている。
     */
    private fun updateZoneStages() {
        for (y in 0 until height) for (x in 0 until width) {
            val t = tileAt(x, y)
            if (!t.kind.isZone) continue

            val demand = when (t.kind) {
                TileKind.ZONE_R -> demandR
                TileKind.ZONE_C -> demandC
                else -> demandI
            }

            // 道路と電気は必須。欠けたら衰退する。
            if (!t.connected || !t.powered) {
                if (t.stage > 0) t.stage--
                continue
            }

            // 水は「大きく育つための条件」。いきなり必須にすると、
            // 水道を知らないうちから街が壊れてしまう。
            // 水がなければ段階1までしか育たない。
            val waterCap = if (t.watered) 3 else 1

            // 住宅は公害を嫌い、地価が要る。工業は気にしない。
            // 犯罪と渋滞は、住みたさ・商売のしやすさを下げる。
            //
            // ただし、平常時の犯罪率（20〜40）でも引いてしまうと、
            // どの街も成長の敷居（8/20/34）に届かなくなって衰退する。
            // 「ひどいときだけ効く」ようにして、普通の街は今までどおり育つようにする。
            val crimePenalty = ((t.crime - 45) / 3).coerceAtLeast(0)
            val trafficPenalty = if (nearCongestion(x, y)) 6 else 0
            val quality = when (t.kind) {
                TileKind.ZONE_R ->
                    t.landValue - t.pollution / 2 + t.safety / 3 + t.education / 3 +
                        t.health / 3 - crimePenalty - trafficPenalty
                TileKind.ZONE_C ->
                    t.landValue - t.pollution / 2 + t.safety / 4 - crimePenalty - trafficPenalty
                else -> t.landValue / 2 + 20 - trafficPenalty / 2
            }

            // タイルごとの癖。位置から決まるので、同じ街なら毎月同じ値になる。
            val jitter = ((x * 7 + y * 13) % 9) - 4

            // 段階ごとに求められる質。上に行くほど厳しい。
            val required = when (t.stage) {
                0 -> 8
                1 -> 20
                else -> 34
            } + jitter

            // 成長に必要な需要。上の段階ほど強い需要が要る。
            val demandToGrow = t.stage * 20 + jitter

            val before = t.stage
            when {
                t.stage < waterCap && quality >= required && demand > demandToGrow -> t.stage++
                // 質が大きく欠けるか、需要が強く落ち込んだときだけ衰退する。
                t.stage > 0 && (quality < required / 2 || demand < -30 + jitter) -> t.stage--
            }
            // 変わった月を覚えておく。建つ様子を見せるために使う。
            if (t.stage != before) {
                t.previousStage = before
                t.stageChangedMonth = month
            }
        }
    }

    private fun tallyPopulation() {
        var pop = 0
        var cJobs = 0
        var iJobs = 0
        for (t in tiles) {
            when (t.kind) {
                TileKind.ZONE_R -> pop += RESIDENTIAL_POP[t.stage]
                TileKind.ZONE_C -> cJobs += COMMERCIAL_JOBS[t.stage]
                TileKind.ZONE_I -> iJobs += INDUSTRIAL_JOBS[t.stage]
                else -> {}
            }
        }
        // 感染が広がると人が減る。ただし、じわじわ効く程度に留める。
        // ここを強くすると、病院を建てる前に街が消えてしまう。
        if (infection > 50) {
            pop = pop * (100 - (infection - 50) / 5) / 100
        }
        population = pop
        commercialJobs = cJobs
        industrialJobs = iJobs
        jobs = cJobs + iJobs

        // 失業率。働ける人に対して職がどれだけ足りないか。
        val employable = (pop * 0.55f).toInt()
        unemployment = if (employable <= 0) 0
        else ((employable - jobs).coerceAtLeast(0) * 100 / employable).coerceIn(0, 100)
    }

    /** 税収と維持費を資金に反映する。 */
    private fun applyBudget() {
        var taxable = 0
        var taxableR = 0
        var taxableC = 0
        var taxableI = 0
        var upkeep = 0
        for (t in tiles) {
            if (t.kind.isZone && t.stage > 0) {
                // 評価額は段階と地価から。商業が最も稼ぐ。
                val base = t.stage * (t.landValue + 20)
                val v = when (t.kind) {
                    TileKind.ZONE_C -> (base * 1.4f).toInt()
                    TileKind.ZONE_I -> (base * 1.1f).toInt()
                    else -> base
                }
                taxable += v
                when (t.kind) {
                    TileKind.ZONE_R -> taxableR += v
                    TileKind.ZONE_C -> taxableC += v
                    else -> taxableI += v
                }
            }
            upkeep += BuildCost.upkeep(t.kind)
        }
        val income = (taxable * taxRate / 100f).toInt() + tourismIncome

        // 条例の費用は人口に比例する
        val ordinanceCost = ordinances.sumOf { (population * it.costPerCitizen).toInt() }
        val totalUpkeep = upkeep + ordinanceCost

        lastIncome = income
        lastUpkeep = totalUpkeep
        lastIncomeBreakdown = Income(
            residential = (taxableR * taxRate / 100f).toInt(),
            commercial = (taxableC * taxRate / 100f).toInt(),
            industrial = (taxableI * taxRate / 100f).toInt(),
            tourism = tourismIncome,
        )
        lastSpending = spendingBreakdown(ordinanceCost)
        funds += income - totalUpkeep
    }

    /** 支出の内訳。何にお金がかかっているかを見せるため。 */
    private fun spendingBreakdown(ordinanceCost: Int): Spending {
        var transport = 0
        var power = 0
        var water = 0
        var garbage = 0
        var safety = 0
        var health = 0
        var education = 0
        var parks = 0
        for (t in tiles) {
            val u = BuildCost.upkeep(t.kind)
            if (u == 0) continue
            when (t.kind) {
                TileKind.ROAD, TileKind.AVENUE, TileKind.HIGHWAY, TileKind.RAIL,
                TileKind.SUBWAY, TileKind.BUS_STOP, TileKind.SUBWAY_STATION,
                TileKind.AIRPORT, TileKind.SEAPORT -> transport += u
                TileKind.POWER_COAL, TileKind.POWER_SOLAR, TileKind.POWER_WIND,
                TileKind.POWER_LINE -> power += u
                TileKind.WATER_TOWER, TileKind.WATER_PLANT, TileKind.SEWAGE_PLANT -> water += u
                TileKind.LANDFILL, TileKind.INCINERATOR, TileKind.RECYCLING -> garbage += u
                TileKind.POLICE, TileKind.FIRE -> safety += u
                TileKind.HOSPITAL, TileKind.CLINIC -> health += u
                TileKind.SCHOOL -> education += u
                TileKind.PARK, TileKind.FARM -> parks += u
                else -> {}
            }
        }
        return Spending(
            transport, power, water, garbage, safety, health, education, parks, ordinanceCost,
        )
    }

    private fun checkBankruptcy() {
        if (funds < 0) {
            monthsInDebt++
            if (monthsInDebt >= BANKRUPT_MONTHS) gameOver = true
        } else {
            monthsInDebt = 0
        }
    }

    /** 破綻までの残り月数。負債がなければ null。 */
    fun bankruptcyWarning(): Int? =
        if (funds < 0 && monthsInDebt >= BANKRUPT_WARN_MONTHS) BANKRUPT_MONTHS - monthsInDebt else null
}
