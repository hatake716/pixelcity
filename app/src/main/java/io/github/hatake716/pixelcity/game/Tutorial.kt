package io.github.hatake716.pixelcity.game

/**
 * チュートリアル。初心者が手順どおりに進めば、都市経営の基盤ができあがる。
 *
 * 方針（docs/SPEC.md §7）:
 *  - 1ステップにつき1つの操作だけを求める
 *  - そのステップに関係のない操作は受け付けない（[allows] で門番をする）
 *  - 「なぜそうするのか」を必ず添える
 *  - 完走した時点で黒字に回り始める街が残る
 */
class Tutorial {

    /** 各ステップで player に何をさせるか。 */
    sealed interface Goal {
        /** ボタンを押して次へ進むだけ。 */
        data object Continue : Goal
        /** [kind] を [count] タイル置く。 */
        data class Place(val kind: TileKind, val count: Int) : Goal
        /** [months] か月ぶん時間を進める。 */
        data class Advance(val months: Int) : Goal
        /** 税率の画面を開く。 */
        data object OpenBudget : Goal
    }

    data class Step(
        val title: String,
        val body: String,
        val goal: Goal,
        /** 強調するツールボタン。null なら強調しない。 */
        val highlightTool: TileKind? = null,
        /** 進行ボタンを強調するか。 */
        val highlightSpeed: Boolean = false,
        /** 予算ボタンを強調するか。 */
        val highlightBudget: Boolean = false,
    )

    companion object {
        /** チュートリアル中に補填する資金。手順どおりに進めれば足りなくならない。 */
        const val GRANT = 20_000

        val STEPS: List<Step> = listOf(
            Step(
                title = "ようこそ、しちょう！",
                body = "きょうから あなたが このまちの しちょうです。もくひょうは まちの じんこうを ふやすこと。でも おかねが なくなると はさんします。",
                goal = Goal.Continue,
            ),
            Step(
                title = "がめんの みかた",
                body = "うえに「しきん」「じんこう」「ねんげつ」が でています。みぎの R/C/I の バーは、じゅうたく・しょうぎょう・こうぎょうの「もっとほしい」ぐあい（じゅよう）です。",
                goal = Goal.Continue,
            ),
            Step(
                title = "まずは どうろ",
                body = "たてものは どうろが ないと そだちません。したの「どうろ」を えらんで、マップを よこに なぞり、10マス ひいてみましょう。",
                goal = Goal.Place(TileKind.ROAD, 10),
                highlightTool = TileKind.ROAD,
            ),
            Step(
                title = "でんきを つくる",
                body = "まちには でんきが いります。「かりょく」を えらんで、どうろの ちかくに はつでんしょを 1つ たてましょう。",
                goal = Goal.Place(TileKind.POWER_COAL, 1),
                highlightTool = TileKind.POWER_COAL,
            ),
            Step(
                title = "じゅうたくを おく",
                body = "ひとが すむ ばしょです。「じゅうたく」を えらんで、どうろの となりに 6マス おきましょう。かってに はってんします。",
                goal = Goal.Place(TileKind.ZONE_R, 6),
                highlightTool = TileKind.ZONE_R,
            ),
            Step(
                title = "しょうぎょうを おく",
                body = "しごとが ないと ひとは すみつきません。「しょうぎょう」を どうろの となりに 3マス。しょうぎょうは ぜいしゅうの ちゅうしんです。",
                goal = Goal.Place(TileKind.ZONE_C, 3),
                highlightTool = TileKind.ZONE_C,
            ),
            Step(
                title = "こうぎょうを おく",
                body = "こうぎょうも しごとを うみます。ただし こうがいを だすので、じゅうたくから はなして 3マス おきましょう。",
                goal = Goal.Place(TileKind.ZONE_I, 3),
                highlightTool = TileKind.ZONE_I,
            ),
            Step(
                title = "じかんを すすめる",
                body = "みぎしたの ▶ で じかんが すすみます。はやさも かえられます。3かげつ すすめて、まちの ようすを みましょう。",
                goal = Goal.Advance(3),
                highlightSpeed = true,
            ),
            Step(
                title = "こうえんを おく",
                body = "じんこうが ふえてきましたね。こうえんは まわりの ちかを あげ、こうがいを へらします。じゅうたくの ちかくに 2つ おきましょう。",
                goal = Goal.Place(TileKind.PARK, 2),
                highlightTool = TileKind.PARK,
            ),
            Step(
                title = "ぜいりつを しる",
                body = "「よさん」で ぜいりつを かえられます。たかすぎると ひとが でていきます。ひらいて たしかめてみましょう。",
                goal = Goal.OpenBudget,
                highlightBudget = true,
            ),
            Step(
                title = "そつぎょう！",
                body = "これで きほんは かんぺきです。じんこうが ふえると、とうきょうタワーなどの せかいの けんちくが たてられます。めざせ 大とし！",
                goal = Goal.Continue,
            ),
        )
    }

    var stepIndex: Int = 0
        private set
    var active: Boolean = false
        private set
    var finished: Boolean = false
        private set

    /** そのステップで数えた進み具合（置いた数・進めた月数）。 */
    var progress: Int = 0
        private set

    val step: Step? get() = if (active && stepIndex in STEPS.indices) STEPS[stepIndex] else null

    fun start() {
        active = true
        finished = false
        stepIndex = 0
        progress = 0
    }

    fun skip() {
        active = false
        finished = false
        stepIndex = 0
        progress = 0
    }

    /** 目標に対する残り。表示に使う。 */
    fun remaining(): Int {
        val g = step?.goal ?: return 0
        return when (g) {
            is Goal.Place -> (g.count - progress).coerceAtLeast(0)
            is Goal.Advance -> (g.months - progress).coerceAtLeast(0)
            else -> 0
        }
    }

    /**
     * その操作を いま 許すか。チュートリアル中は、
     * ステップに関係のない建設をさせない（初心者が迷子にならないように）。
     */
    fun allowsBuild(kind: TileKind): Boolean {
        if (!active) return true
        val g = step?.goal ?: return false
        return g is Goal.Place && g.kind == kind
    }

    /** 時間を進めてよいか。 */
    fun allowsSpeedChange(): Boolean {
        if (!active) return true
        return step?.goal is Goal.Advance
    }

    fun allowsBudget(): Boolean {
        if (!active) return true
        return step?.goal is Goal.OpenBudget
    }

    /** 「つぎへ」で進むステップか。 */
    fun awaitingContinue(): Boolean = active && step?.goal is Goal.Continue

    // --- 進捗の通知 ---

    fun onBuilt(kind: TileKind) {
        val g = step?.goal ?: return
        if (g is Goal.Place && g.kind == kind) {
            progress++
            if (progress >= g.count) advance()
        }
    }

    fun onMonthPassed() {
        val g = step?.goal ?: return
        if (g is Goal.Advance) {
            progress++
            if (progress >= g.months) advance()
        }
    }

    fun onBudgetOpened() {
        if (step?.goal is Goal.OpenBudget) advance()
    }

    fun onContinuePressed() {
        if (step?.goal is Goal.Continue) advance()
    }

    private fun advance() {
        progress = 0
        stepIndex++
        if (stepIndex >= STEPS.size) {
            active = false
            finished = true
        }
    }

    // --- 保存・復元 ---

    fun saveState(): IntArray = intArrayOf(
        if (active) 1 else 0,
        stepIndex,
        progress,
        if (finished) 1 else 0,
    )

    fun restore(state: IntArray) {
        if (state.size < 4) return
        active = state[0] == 1
        stepIndex = state[1].coerceIn(0, STEPS.size)
        progress = state[2].coerceAtLeast(0)
        finished = state[3] == 1
    }
}
