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
        const val DEFAULT_SIZE = 32
        const val STARTING_FUNDS = 20_000
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

        // 下の端を海にする。建設できる土地を十分に残すため、浅くとる。
        val seaBase = height - 3 - rnd.nextInt(2)
        for (x in 0 until width) {
            val edge = seaBase + ((x * 3 + rnd.nextInt(3)) % 3) - 1
            for (y in max(0, edge) until height) tileAt(x, y).terrain = Terrain.WATER
        }

        // 上から下へ蛇行する川を一本引く。幅1で、街を分断しすぎないようにする。
        var cx = width / 4 + rnd.nextInt(width / 2)
        for (y in 0 until height) {
            if (inBounds(cx, y)) tileAt(cx, y).terrain = Terrain.WATER
            cx += rnd.nextInt(3) - 1
            cx = cx.coerceIn(2, width - 3)
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
    fun clearStartingArea(halfWidth: Int = 7, halfHeight: Int = 5) {
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

    /** 4近傍に道路があるか。区分が育つための最低条件。 */
    fun touchesRoad(x: Int, y: Int): Boolean {
        var found = false
        forEachNeighbor4(x, y) { nx, ny ->
            if (tileAt(nx, ny).kind == TileKind.ROAD) found = true
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
    fun step() {
        if (gameOver) return
        month++
        updateConnectivity()
        updatePower()
        updateLocalValues()
        updateDemand()
        updateZoneStages()
        tallyPopulation()
        applyBudget()
        checkBankruptcy()
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
                if (n.kind == TileKind.ROAD && !visited[index(nx, ny)]) {
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
                if (tiles[ni].kind == TileKind.ROAD && !visited[ni]) {
                    visited[ni] = true
                    queue.add(ni)
                }
            }
        }

        // 繋がった道路に隣接する区分・建物を、繋がっているとみなす。
        for (y in 0 until height) for (x in 0 until width) {
            val t = tileAt(x, y)
            if (t.kind == TileKind.ROAD || t.kind == TileKind.EMPTY) continue
            if (t.connected) continue
            var touching = false
            forEachNeighbor4(x, y) { nx, ny ->
                val n = tileAt(nx, ny)
                if (n.kind == TileKind.ROAD && n.connected) touching = true
            }
            t.connected = touching
        }
    }

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
        powerDemand = demand

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

        for (t in tiles) {
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

        // 税率の影響。7%を基準に、高いほど需要が落ちる。
        val taxPenalty = (taxRate - DEFAULT_TAX_RATE) * 6

        // 電力が足りていなければ、どの需要も伸びない。
        val powerCut = if (powerRatio < 1f) ((1f - powerRatio) * 70).toInt() else 0

        demandR = approach(demandR, targetR - taxPenalty - powerCut)
        demandC = approach(demandC, targetC - taxPenalty - powerCut)
        demandI = approach(demandI, targetI - taxPenalty - powerCut)
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

            // 住宅は公害を嫌い、地価が要る。工業は気にしない。
            val quality = when (t.kind) {
                TileKind.ZONE_R -> t.landValue - t.pollution / 2 + t.safety / 3 + t.education / 3 + t.health / 3
                TileKind.ZONE_C -> t.landValue - t.pollution / 2 + t.safety / 4
                else -> t.landValue / 2 + 20
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

            when {
                t.stage < 3 && quality >= required && demand > demandToGrow -> t.stage++
                // 質が大きく欠けるか、需要が強く落ち込んだときだけ衰退する。
                t.stage > 0 && (quality < required / 2 || demand < -30 + jitter) -> t.stage--
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
        population = pop
        commercialJobs = cJobs
        industrialJobs = iJobs
        jobs = cJobs + iJobs
    }

    /** 税収と維持費を資金に反映する。 */
    private fun applyBudget() {
        var taxable = 0
        var upkeep = 0
        for (t in tiles) {
            if (t.kind.isZone && t.stage > 0) {
                // 評価額は段階と地価から。商業が最も稼ぐ。
                val base = t.stage * (t.landValue + 20)
                taxable += when (t.kind) {
                    TileKind.ZONE_C -> (base * 1.4f).toInt()
                    TileKind.ZONE_I -> (base * 1.1f).toInt()
                    else -> base
                }
            }
            upkeep += BuildCost.upkeep(t.kind)
        }
        val income = (taxable * taxRate / 100f).toInt() + tourismIncome
        lastIncome = income
        lastUpkeep = upkeep
        funds += income - upkeep
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
