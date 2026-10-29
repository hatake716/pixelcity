package io.github.hatake716.pixelcity.game

/** A small mayoral story. Milestones use the real city, never simulated button counts. */
class Tutorial {
    enum class Chapter(val label: String, val summary: String) {
        ARRIVAL("着任の日", "一本の道から"),
        NEIGHBOURS("初めての住民", "暮らしに耳を澄ます"),
        MAIN_STREET("小さな商店街", "住む街から、働く街へ"),
        PROMISE("市長の約束", "この街らしい公共投資"),
        ACCOUNTS("最初の決算", "自分の力で歩く街"),
    }

    enum class Priority(val label: String, val kind: TileKind, val count: Int, val result: String) {
        PARKS("緑の広場", TileKind.PARK, 2, "広場には、夕方になると人が集まるようになりました。"),
        CLINIC("身近な診療所", TileKind.CLINIC, 1, "診療所の灯りが、帰り道の安心になりました。"),
    }

    sealed interface Goal {
        data object Continue : Goal
        data class Place(val kind: TileKind, val count: Int) : Goal
        data class Population(val count: Int) : Goal
        data object InspectHome : Goal
        data object GrowingStreet : Goal
        data object ChoosePriority : Goal
        data object KeepPromise : Goal
        data object OpenInfo : Goal
        data object OpenBudget : Goal
        data class StableBudget(val months: Int) : Goal
    }

    data class Step(
        /** Stable save identifier; never reuse when rearranging the story. */
        val id: Int,
        val chapter: Chapter,
        val title: String,
        val body: String,
        val goal: Goal,
        val reply: String = "やってみます",
    ) {
        val highlightTool: TileKind? get() = (goal as? Goal.Place)?.kind
        val highlightSpeed: Boolean get() = goal is Goal.Population || goal is Goal.GrowingStreet || goal is Goal.StableBudget
        val highlightBudget: Boolean get() = goal is Goal.OpenBudget || goal is Goal.StableBudget
        val highlightInfo: Boolean get() = goal is Goal.OpenInfo
    }

    companion object {
        private const val STORY_VERSION = 2
        val STEPS = listOf(
            Step(0, Chapter.ARRIVAL, "空っぽの市長室",
                "おはようございます、市長。秘書のひよりです。机と地図しかない市役所ですが、転入を待つ家族がいます。まずは、その人たちの帰る場所を一緒につくりませんか。",
                Goal.Continue, "最初の通りをつくろう"),
            Step(1, Chapter.ARRIVAL, "地図に引く、最初の一本",
                "引っ越しの車が入れる道から始めましょう。上部の「建設」で道具を開き、光るマスをなぞって選び、費用を見て「実行」。選び直すときは「取消」です。「建設」を閉じると一本指で地図を動かせます。二本指なら、どちらでも移動と拡大縮小ができます。",
                Goal.Place(TileKind.ROAD, 12)),
            Step(2, Chapter.ARRIVAL, "帰り道に灯りを",
                "道ができましたね。次は発電所です。火力は手頃ですが煙が出るので、住宅をつくる場所から離して、通りの端へ。道路を通じて街へ電気が届きます。",
                Goal.Place(TileKind.POWER_COAL, 1)),
            Step(3, Chapter.ARRIVAL, "まだ見ぬ隣人のために",
                "「庭の見える家に住みたい」と、転入希望の手紙にありました。煙から離れた道沿いを住宅にしましょう。区画を用意すると、家は街の状況に合わせて育ちます。",
                Goal.Place(TileKind.ZONE_R, 8)),
            Step(4, Chapter.ARRIVAL, "引っ越しの朝",
                "準備は整いました。時間を進めて、最初の住民を迎えましょう。屋根が立ち上がるところ、私も楽しみです。入居しなければ、道路と電気を一緒に確認しましょう。",
                Goal.Population(32), "住民を迎えよう"),
            Step(5, Chapter.NEIGHBOURS, "一通目の転入届",
                "転入届が届きました！ 地図の数字が、暮らす人の数になりましたね。でも『もっと大きな家にしたいのに』という相談も。答えは、建物を調べると見つかりそうです。",
                Goal.Continue, "住民の声を聞こう"),
            Step(6, Chapter.NEIGHBOURS, "屋根の向こうの暮らし",
                "右上の「調査」を選んで、人が住んでいる家をタップしてください。電気、水道、地価、そして次の成長に必要なことが分かります。困ったときの、市長の虫眼鏡です。",
                Goal.InspectHome),
            Step(7, Chapter.NEIGHBOURS, "蛇口から始まる未来",
                "電気があっても、水が届かなければ家は小さいままです。住宅の近くへ給水塔を置きましょう。水は一定の範囲に届きます。高い建物を育てるための、大切な一歩です。",
                Goal.Place(TileKind.WATER_TOWER, 1)),
            Step(8, Chapter.MAIN_STREET, "パン屋さんの出店相談",
                "『この通りでお店を開きたい』とパン屋さんが来ました。商業区画を用意しましょう。お店は仕事と税収を生みます。赤や青の日よけが並ぶ日が待ち遠しいですね。",
                Goal.Place(TileKind.ZONE_C, 4)),
            Step(9, Chapter.MAIN_STREET, "街を支える工房",
                "店だけでなく、ものをつくる仕事も必要です。工業区画は煙の出る側へまとめて、家とは距離を取りましょう。小さく始めれば、公害も出費も抑えられます。",
                Goal.Place(TileKind.ZONE_I, 2)),
            Step(10, Chapter.MAIN_STREET, "通りが商店街になる日",
                "家と仕事と水がそろいました。時間を進めて、人口120人、仕事40人分、二段階以上の住宅4軒を目指しましょう。月数を待つだけでなく、実際に街が育ったら次へ進みます。",
                Goal.GrowingStreet, "街の成長を見届けよう"),
            Step(11, Chapter.PROMISE, "市長の最初の約束",
                "住民の集まりで、二つの希望が出ました。『夕方に集まれる緑の広場』と『近くで診てもらえる診療所』。まず、どちらを約束しますか。建設費だけでなく毎月の費用も見てください。",
                Goal.ChoosePriority),
            Step(12, Chapter.PROMISE, "約束を、街の形に",
                "選んだ方針を、住宅の近くで形にしましょう。建てる場所で、恩恵が届く範囲も変わります。市長が選んだものが、この街らしさになっていきます。",
                Goal.KeepPromise),
            Step(13, Chapter.PROMISE, "楽しい通りの、舞台裏",
                "にぎわいの後には、ゴミも残ります。回収先がなければ街にたまり、暮らしを悪くします。埋立地を住宅から離して用意しましょう。満杯になったら、次の受け入れ先も必要です。",
                Goal.Place(TileKind.LANDFILL, 2)),
            Step(14, Chapter.ACCOUNTS, "ひよりの市政ノート",
                "日々の変化を、市政ノートで確認しましょう。上部の「市政」→「都市情報」には街の統計、重ね地図、収支などの推移があります。条例もここから。費用が続くので、必要なものを選ぶのが市長の仕事です。",
                Goal.OpenInfo, "市政ノートを開こう"),
            Step(15, Chapter.ACCOUNTS, "はじめての予算会議",
                "税金が入り始めました。上部の「市政」→「予算」で収入と支出を見比べてください。黒字なら増税は不要。赤字なら税率と施設の維持費を見直しましょう。税率を上げすぎると、街の需要は下がります。",
                Goal.OpenBudget, "帳簿を見てみよう"),
            Step(16, Chapter.ACCOUNTS, "三か月の約束",
                "最後は、市政が自分の足で歩けるかの確認です。住民120人以上と電気・水・ゴミ処理を保ち、三か月続けて黒字にしましょう。建設も税率も、ここからは市長にお任せします。困ったら私を頼ってください。",
                Goal.StableBudget(3), "街の時間を進めよう"),
            Step(17, Chapter.ACCOUNTS, "市長室に、街の音が届く",
                "三か月続けて黒字です。道を引いた日には静かだった窓の外から、今は暮らしの音がします。次に何をつくるかは、市長が決めてください。私はこれからも、隣の机にいます。",
                Goal.Continue, "この街の続きをつくろう"),
        )
    }

    var stepIndex = 0; private set
    var active = false; private set
    var finished = false; private set
    var progress = 0; private set
    var briefingRead = false; private set
    var priority: Priority? = null; private set
    var inheritedCity = false; private set
    private var lastCheckedMonth = -1
    val step: Step? get() = if (active) STEPS.getOrNull(stepIndex) else null
    val chapter get() = step?.chapter
    val neededTool: TileKind? get() = if (step?.goal is Goal.KeepPromise) priority?.kind else step?.highlightTool

    fun start() {
        active = true; finished = false; stepIndex = 0; progress = 0
        briefingRead = false; priority = null; inheritedCity = false; lastCheckedMonth = -1
    }
    fun skip() { start(); active = false }
    fun chapterNumber() = (chapter?.ordinal ?: 0) + 1
    fun chapterCount() = Chapter.entries.size
    fun awaitingContinue() = step?.goal is Goal.Continue
    fun awaitingChoice() = step?.goal is Goal.ChoosePriority
    fun placementGoal(): Goal.Place? = when (val goal = step?.goal) {
        is Goal.Place -> goal
        Goal.KeepPromise -> priority?.let { Goal.Place(it.kind, it.count) }
        else -> null
    }
    fun remaining(): Int = (placementGoal()?.count?.minus(progress) ?: 0).coerceAtLeast(0)
    fun isRunningGoal() = step?.highlightSpeed == true
    fun allowsSpeedChange() = !active || briefingRead && isRunningGoal()
    // Observation and the budget remain available, including when a resident needs help.
    fun allowsBudget() = true
    fun allowsInfo() = true
    fun allowsBuild(kind: TileKind): Boolean {
        if (!active) return true
        if (!briefingRead) return false
        if (isRunningGoal()) return true
        if (kind == TileKind.EMPTY || kind == TileKind.ROAD || kind == TileKind.POWER_LINE) return placementGoal() != null
        return placementGoal()?.kind == kind
    }

    fun briefing(city: City): String = when {
        inheritedCity && stepIndex == 0 -> "お帰りなさい、市長。秘書のひよりです。街と資金はそのまま、新しい就任日誌でお手伝いします。すでに整っている設備は、確認だけで次へ進めます。"
        step?.id == 5 -> "現在の住民は${city.population}人。最初の転入届が届きました！ 地図の数字が、暮らす人の数になりましたね。でも『もっと大きな家にしたいのに』という相談も。建物を調べてみましょう。"
        step?.goal is Goal.KeepPromise -> when (priority) {
            Priority.PARKS -> "『緑の広場をつくる』とお伝えしました。住民も楽しみにしています。公園を住宅の近くに二つ。地価を上げ、公害を和らげます。ベンチのある帰り道をつくりましょう。"
            else -> "『身近な診療所をつくる』とお伝えしました。住宅のそばに一つ。近所の健康を支える、小さな拠点です。必要な医療から整えていく、市長らしい約束ですね。"
        }
        step?.id == 17 -> "${priority?.result.orEmpty()} 今は${city.population}人が暮らし、直近の月は$${city.lastIncome - city.lastUpkeep}の黒字です。道を引いた日には静かだった窓の外から、暮らしの音がします。次の一歩も、市長と一緒に。"
        else -> step?.body.orEmpty()
    }

    fun objective(city: City): String = when (val goal = step?.goal) {
        is Goal.Place -> "${toolName(goal.kind)} ${progress}/${goal.count}"
        is Goal.KeepPromise -> placementGoal()?.let { "${toolName(it.kind)} ${progress}/${it.count}" }.orEmpty()
        is Goal.Population -> "最初の住民 ${city.population}/${goal.count}人"
        is Goal.GrowingStreet -> "住民 ${city.population}/120人・仕事 ${city.jobs}/40人分・育った家 ${grownHomes(city)}/4軒"
        is Goal.StableBudget -> "連続黒字 $progress/${goal.months}か月・住民 ${city.population}/120人"
        Goal.InspectHome -> "「調査」で入居済みの住宅を見る"
        Goal.OpenBudget -> "「予算」で収入と支出を確認する"
        Goal.OpenInfo -> "「都市情報」で街の統計を見る"
        else -> step?.title.orEmpty()
    }

    fun advice(city: City): String {
        if (isRunningGoal()) {
            val homes = city.tiles.filter { it.kind == TileKind.ZONE_R }
            return when {
                homes.isEmpty() -> "住宅がありません。道沿いに区画を用意しましょう。"
                homes.any { !it.connected } -> "道がつながっていない住宅があります。発電所につながる通りへ結びましょう。"
                homes.any { !it.powered } -> "電気が届いていません。発電所・道路・送電線を確認しましょう。"
                step?.goal !is Goal.Population && homes.any { !it.watered } -> "給水塔の範囲から外れた住宅があります。家の近くに水を届けましょう。"
                step?.goal is Goal.StableBudget && city.garbageProduced > city.garbageCapacity -> "ゴミの処理量が足りません。埋立地の数と残り容量を確認しましょう。"
                city.taxRate > 9 -> "税率が需要を抑えています。まず7〜9%で収支と成長を見比べましょう。"
                homes.any { it.pollution > 20 } -> "住宅に公害が届いています。工場・発電所から離すか、公園や農地で和らげましょう。"
                step?.goal is Goal.StableBudget && city.lastIncome <= city.lastUpkeep -> "先月は赤字でした。予算で維持費と税率を確認。必要なら住宅や仕事を少し増やしましょう。"
                step?.goal is Goal.StableBudget && !promiseKept(city) -> "住民への約束の施設が足りません。選んだ公共投資をもう一度整えましょう。"
                city.population < 120 && step?.goal !is Goal.Population && city.demandR <= 20 -> "住宅需要が低めです。仕事・税率を確認して、時間を進めましょう。"
                else -> "時間を進めて見守りましょう。途中でも調査・予算・建設で手を入れられます。"
            }
        }
        return when (step?.goal) {
            Goal.InspectHome -> "右上の「調査」→家の屋根をタップ。空き地や道路では進みません。"
            Goal.OpenBudget -> "黒字なら、税率を変えずに進んで構いません。"
            Goal.OpenInfo -> "統計・データマップ・推移を、これからの判断に使えます。"
            else -> "光る候補地へ置くか、道沿いを選びましょう。「実行」で確定します。"
        }
    }

    fun acknowledge(city: City) {
        if (!active || awaitingChoice()) return
        if (awaitingContinue()) advance(city) else {
            briefingRead = true
            onCityChanged(city)
        }
    }
    fun reopenBriefing() { if (active) briefingRead = false }
    fun choose(value: Priority, city: City) {
        if (!awaitingChoice()) return
        priority = value
        advance(city)
    }
    fun onHomeInspected(city: City, index: Int) {
        if (briefingRead && step?.goal is Goal.InspectHome && city.tiles.getOrNull(index)?.let { it.kind == TileKind.ZONE_R && it.stage > 0 } == true) advance(city)
    }
    fun onBudgetOpened(city: City) { if (briefingRead && step?.goal is Goal.OpenBudget) advance(city) }
    fun onInfoOpened(city: City) { if (briefingRead && step?.goal is Goal.OpenInfo) advance(city) }

    fun onCityChanged(city: City) {
        if (!active || !briefingRead) return
        placementGoal()?.let { goal ->
            progress = placed(city, goal.kind)
            if (progress >= goal.count) advance(city)
            return
        }
        when (val goal = step?.goal) {
            is Goal.Population -> if (city.population >= goal.count) advance(city)
            Goal.GrowingStreet -> if (city.population >= 120 && city.jobs >= 40 && grownHomes(city) >= 4) advance(city)
            // A broken service or a demolition interrupts the streak even between months.
            is Goal.StableBudget -> if (!stableServices(city)) progress = 0
            else -> {}
        }
    }
    fun onMonthPassed(city: City) {
        if (!active || !briefingRead || city.month == lastCheckedMonth) return
        lastCheckedMonth = city.month
        if (step?.goal is Goal.StableBudget) {
            progress = if (stableServices(city) && city.lastIncome > city.lastUpkeep) progress + 1 else 0
            if (progress >= (step!!.goal as Goal.StableBudget).months) advance(city)
        } else onCityChanged(city)
    }
    private fun stableServices(city: City): Boolean {
        val homes = city.tiles.filter { it.kind == TileKind.ZONE_R && it.stage > 0 }
        return city.population >= 120 && city.funds >= 0 && !city.gameOver && homes.isNotEmpty() &&
            homes.all { it.connected && it.powered && it.watered } &&
            city.garbageProduced <= city.garbageCapacity && promiseKept(city)
    }
    private fun promiseKept(city: City) = priority?.let { placed(city, it.kind) >= it.count } == true
    private fun grownHomes(city: City) = city.tiles.count { it.kind == TileKind.ZONE_R && it.stage >= 2 }
    private fun placed(city: City, kind: TileKind): Int {
        if (kind != TileKind.ROAD) return city.tiles.indices.count { i ->
            val tile = city.tiles[i]
            tile.kind == kind && if (kind.isPowerPlant) city.touchesRoad(i % city.width, i / city.width)
                else tile.connected && tile.powered
        }
        val visited = BooleanArray(city.tiles.size)
        var largest = 0
        for (start in city.tiles.indices) {
            if (visited[start] || city.tiles[start].kind != TileKind.ROAD) continue
            val queue = java.util.ArrayDeque<Int>()
            queue.add(start); visited[start] = true
            var count = 0
            while (queue.isNotEmpty()) {
                val i = queue.removeFirst(); count++
                val x = i % city.width; val y = i / city.width
                for ((nx, ny) in listOf(x-1 to y, x+1 to y, x to y-1, x to y+1)) {
                    if (!city.inBounds(nx, ny)) continue
                    val n = city.index(nx, ny)
                    if (!visited[n] && city.tiles[n].kind == TileKind.ROAD) { visited[n] = true; queue.add(n) }
                }
            }
            largest = maxOf(largest, count)
        }
        return largest
    }

    private fun advance(city: City) {
        progress = 0; briefingRead = false; lastCheckedMonth = city.month
        stepIndex++
        if (stepIndex >= STEPS.size) { active = false; finished = true }
    }

    fun saveState() = intArrayOf(if (active) 1 else 0, step?.id ?: STEPS.size, progress,
        if (finished) 1 else 0, STORY_VERSION, priority?.ordinal ?: -1, if (briefingRead) 1 else 0,
        lastCheckedMonth, if (inheritedCity) 1 else 0)

    fun restore(state: IntArray) {
        if (state.size < 4) return
        active = state[0] == 1
        finished = state[3] == 1
        if (state.size < 9 || state[4] != STORY_VERSION) {
            // The old 26-step course used unrelated indexes. Keep the city and restart only its journal.
            stepIndex = if (finished) STEPS.size else 0
            progress = 0; briefingRead = false; inheritedCity = active
            priority = null; lastCheckedMonth = -1
        } else {
            stepIndex = STEPS.indexOfFirst { it.id == state[1] }.let { if (it < 0) STEPS.size else it }
            progress = state[2].coerceAtLeast(0)
            priority = Priority.entries.getOrNull(state[5])
            briefingRead = state[6] == 1
            lastCheckedMonth = state[7]
            inheritedCity = state[8] == 1
            if (active && stepIndex >= 12 && priority == null) { stepIndex = 11; briefingRead = false; progress = 0 }
        }
        if (finished || stepIndex >= STEPS.size) active = false
    }

    private fun toolName(kind: TileKind) = when (kind) {
        TileKind.ROAD -> "つながる道路"
        TileKind.POWER_COAL -> "道沿いの火力発電所"
        TileKind.ZONE_R -> "電気の届く住宅区画"
        TileKind.ZONE_C -> "電気の届く商業区画"
        TileKind.ZONE_I -> "電気の届く工業区画"
        TileKind.WATER_TOWER -> "給水塔"
        TileKind.LANDFILL -> "埋立地"
        TileKind.PARK -> "公園"
        else -> "診療所"
    }
}
