package io.github.hatake716.pixelcity.game

/**
 * チュートリアル。初心者が手順どおりに進めば、都市経営のすべてを一通り触れる。
 *
 * 方針（docs/SPEC.md §7）:
 *  - 1ステップにつき1つの操作だけを求める
 *  - そのステップに関係のない操作は受け付けない（[allowsBuild] などで門番をする）
 *  - 「なぜそうするのか」を必ず添える
 *  - **完走した時点で、財政が健全な中規模都市が残る**
 *
 * 要素が増えたので4つの章に分けてある。章の切れ目で区切りを見せるので、
 * 途中でやめても、どこまで進んだかが分かる。
 */
class Tutorial {

    /** 章。区切りを見せるために使う。 */
    enum class Chapter(val label: String, val summary: String) {
        FOUNDATION("１しょう　まちの はじまり", "どうろ・でんき・みず・くかく"),
        INFRASTRUCTURE("２しょう　インフラを ととのえる", "ゴミ・こうつう・でんりょくの えらびかた"),
        QUALITY("３しょう　くらしを よくする", "サービス・みどり・あんぜん"),
        MAYOR("４しょう　しちょうの しごと", "ぜいきん・じょうほう・じょうれい"),
    }

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
        /** 情報の画面を開く。 */
        data object OpenInfo : Goal
        /** 条例をひとつ有効にする。 */
        data object EnableOrdinance : Goal
    }

    data class Step(
        val chapter: Chapter,
        val title: String,
        val body: String,
        val goal: Goal,
        /** 強調するツールボタン。null なら強調しない。 */
        val highlightTool: TileKind? = null,
        /** 進行ボタンを強調するか。 */
        val highlightSpeed: Boolean = false,
        /** 予算ボタンを強調するか。 */
        val highlightBudget: Boolean = false,
        /** 情報ボタンを強調するか。 */
        val highlightInfo: Boolean = false,
    )

    companion object {
        /** チュートリアル中に補填する資金。手順どおりに進めれば足りなくならない。 */
        const val GRANT = 20_000

        /**
         * 手順。
         *
         * 数は「その章で伝えたいこと」から決めている。たとえば住宅12マスは、
         * 完走したときに人口2,000〜4,000（中規模）へ届く量から逆算した値。
         * 変えるときは TutorialTest の「完走した街」の検査も合わせて直すこと。
         */
        val STEPS: List<Step> = listOf(
            // ============================================================
            // 1章 まちの はじまり
            // ============================================================
            Step(
                chapter = Chapter.FOUNDATION,
                title = "ようこそ、しちょう！",
                body = "きょうから あなたが このまちの しちょうです。もくひょうは じんこうを ふやすこと。" +
                    "ただし おかねが なくなると はさんします。",
                goal = Goal.Continue,
            ),
            Step(
                chapter = Chapter.FOUNDATION,
                title = "がめんの みかた",
                body = "うえに「しきん」「じんこう」「ねんげつ」。みぎの R/C/I の バーは、" +
                    "じゅうたく・しょうぎょう・こうぎょうの じゅようです。バーが うえむきなら たりていません。",
                goal = Goal.Continue,
            ),
            Step(
                chapter = Chapter.FOUNDATION,
                title = "まずは どうろ",
                body = "たてものは どうろが ないと そだちません。したの「こうつう」から「どうろ」を えらび、" +
                    "よこ・たてを なんぼんか なぞって、ごばんの めを つくりましょう。" +
                    "ながく のばすより、まとまった しかくに するほうが、" +
                    "あとで たてる がっこうや びょういんが よく ききます。",
                goal = Goal.Place(TileKind.ROAD, 230),
                highlightTool = TileKind.ROAD,
            ),
            Step(
                chapter = Chapter.FOUNDATION,
                title = "でんきを つくる",
                body = "まちには でんきが いります。「でんりょく」から「かりょく」を えらび、" +
                    "どうろの となりに 3つ たてましょう。",
                goal = Goal.Place(TileKind.POWER_COAL, 3),
                highlightTool = TileKind.POWER_COAL,
            ),
            Step(
                chapter = Chapter.FOUNDATION,
                title = "みずを ひく",
                body = "みずが ないと、たてものは 1だんかいまでしか そだちません。" +
                    "「すいどう」から「きゅうすいとう」を 7つ、まちの あちこちに ちらして たてましょう。" +
                    "とどく はんいが きまっているので、かためて たてても むだに なります。",
                goal = Goal.Place(TileKind.WATER_TOWER, 7),
                highlightTool = TileKind.WATER_TOWER,
            ),
            Step(
                chapter = Chapter.FOUNDATION,
                title = "じゅうたくを おく",
                body = "ひとが すむ ばしょです。「くかく」から「じゅうたく」を えらび、" +
                    "どうろの となりに なぞって 70マス おきましょう。ゆびを はなさずに なぞれば、" +
                    "まとめて おけます。くかくは かってに はってんします。",
                goal = Goal.Place(TileKind.ZONE_R, 70),
                highlightTool = TileKind.ZONE_R,
            ),
            Step(
                chapter = Chapter.FOUNDATION,
                title = "しごとばを つくる",
                body = "しごとが ないと ひとは すみつきません。「しょうぎょう」を 45マス おきましょう。" +
                    "しょうぎょうは ぜいしゅうの ちゅうしんです。",
                goal = Goal.Place(TileKind.ZONE_C, 45),
                highlightTool = TileKind.ZONE_C,
            ),
            Step(
                chapter = Chapter.FOUNDATION,
                title = "こうぎょうも おく",
                body = "こうぎょうも しごとを うみます。ただし こうがいを だすので、" +
                    "じゅうたくから はなして 20マス おきましょう。",
                goal = Goal.Place(TileKind.ZONE_I, 20),
                highlightTool = TileKind.ZONE_I,
            ),
            Step(
                chapter = Chapter.FOUNDATION,
                title = "じかんを すすめる",
                body = "うえの ▶ で じかんが すすみます。はやさも かえられます。" +
                    "24かげつ すすめて、まちが そだつのを みましょう。" +
                    "じんこうが ふえると、あたらしい しせつが つかえるように なります。",
                goal = Goal.Advance(24),
                highlightSpeed = true,
            ),

            // ============================================================
            // 2章 インフラを ととのえる
            // ============================================================
            Step(
                chapter = Chapter.INFRASTRUCTURE,
                title = "ゴミの しまつ",
                body = "ひとが すむと ゴミが でます。しまつしないと たまって、" +
                    "こうがいが ふえ、びょうきの もとに なります。「ゴミ」から「うめたてち」を 6つ。",
                goal = Goal.Place(TileKind.LANDFILL, 6),
                highlightTool = TileKind.LANDFILL,
            ),
            Step(
                chapter = Chapter.INFRASTRUCTURE,
                title = "でんりょくの えらびかた",
                body = "かりょくは やすいけれど こうがいを だします。たいようこうと ふうりょくは" +
                    "こうがいを ださず、でんきも つくります。「ふうりょく」を 2つ たてましょう。",
                goal = Goal.Place(TileKind.POWER_WIND, 2),
                highlightTool = TileKind.POWER_WIND,
            ),
            Step(
                chapter = Chapter.INFRASTRUCTURE,
                title = "おおどおりで じゅうたいを ふせぐ",
                body = "どうろは くるまが おおいと じゅうたいします。じゅうたいは ちかを さげ、" +
                    "こうがいを ふやします。「おおどおり」は どうろの 3ばい はこべます。6マス ひきましょう。",
                goal = Goal.Place(TileKind.AVENUE, 6),
                highlightTool = TileKind.AVENUE,
            ),
            Step(
                chapter = Chapter.INFRASTRUCTURE,
                title = "バスていで くるまを へらす",
                body = "バスていは まわりの こうつうりょうを へらします。" +
                    "じゅうたくの ちかくに 2つ おきましょう。",
                goal = Goal.Place(TileKind.BUS_STOP, 2),
                highlightTool = TileKind.BUS_STOP,
            ),
            Step(
                chapter = Chapter.INFRASTRUCTURE,
                title = "ようすを みる",
                body = "24かげつ すすめて、まちが そだつのを みましょう。" +
                    "しせつを ふやすと いじひも ふえるので、ぜいしゅうが おいつくまで まちます。",
                goal = Goal.Advance(24),
                highlightSpeed = true,
            ),

            // ============================================================
            // 3章 くらしを よくする
            // ============================================================
            Step(
                chapter = Chapter.QUALITY,
                title = "こうえんで ちかを あげる",
                body = "こうえんは まわりの ちかを あげ、こうがいを へらします。" +
                    "ちかが あがると たてものは おおきく そだちます。" +
                    "じゅうたくの あいだに ちらして 8つ おきましょう。ちらすほど ひろく ききます。",
                goal = Goal.Place(TileKind.PARK, 8),
                highlightTool = TileKind.PARK,
            ),
            Step(
                chapter = Chapter.QUALITY,
                title = "のうちで こうがいを すう",
                body = "のうちは こうがいを すいとります。しごとは うみませんが、" +
                    "こうぎょうの ちかくに おくと まちが きれいに なります。8マス おきましょう。",
                goal = Goal.Place(TileKind.FARM, 8),
                highlightTool = TileKind.FARM,
            ),
            Step(
                chapter = Chapter.QUALITY,
                title = "けいさつで はんざいを へらす",
                body = "じんこうが ふえると はんざいも ふえます。はんざいが おおいと" +
                    "ひとが でていきます。「サービス」から「けいさつ」を 2つ。",
                goal = Goal.Place(TileKind.POLICE, 2),
                highlightTool = TileKind.POLICE,
            ),
            Step(
                chapter = Chapter.QUALITY,
                title = "しょうぼうで かじに そなえる",
                body = "かじが おきたとき、しょうぼうしょが ちかいと ちいさく すみます。" +
                    "2つ たてましょう。",
                goal = Goal.Place(TileKind.FIRE, 2),
                highlightTool = TileKind.FIRE,
            ),
            Step(
                chapter = Chapter.QUALITY,
                title = "びょういんで けんこうを まもる",
                body = "ゴミや こうがいが おおいと かんせんしょうが ひろがり、じんこうが へります。" +
                    "「びょういん」を じゅうたくの ちかくに 2つ たてましょう。",
                goal = Goal.Place(TileKind.HOSPITAL, 2),
                highlightTool = TileKind.HOSPITAL,
            ),
            Step(
                chapter = Chapter.QUALITY,
                title = "がっこうで きょういくを",
                body = "きょういくが たかいと、はんざいが へり、たてものが おおきく そだちます。" +
                    "「がっこう」を じゅうたくの ちかくに 2つ たてましょう。",
                goal = Goal.Place(TileKind.SCHOOL, 2),
                highlightTool = TileKind.SCHOOL,
            ),
            Step(
                chapter = Chapter.QUALITY,
                title = "そだつのを まつ",
                body = "48かげつ すすめましょう。サービスが いきわたると、" +
                    "たてものが 2だんかい・3だんかいへと そだち、ぜいしゅうも ふえます。",
                goal = Goal.Advance(48),
                highlightSpeed = true,
            ),

            // ============================================================
            // 4章 しちょうの しごと
            // ============================================================
            Step(
                chapter = Chapter.MAYOR,
                title = "ぜいりつを ととのえる",
                body = "しせつを ふやすと いじひが かかります。「よさん」を ひらいて、" +
                    "しゅうしが プラスに なるよう ぜいりつを ととのえましょう。" +
                    "たかすぎると ひとが でていくので、11〜13% あたりが めやすです。",
                goal = Goal.OpenBudget,
                highlightBudget = true,
            ),
            Step(
                chapter = Chapter.MAYOR,
                title = "まちの ようすを みる",
                body = "「じょうほう」で、じんこう・こうがい・はんざい・じゅうたいが みられます。" +
                    "「データマップ」で ちずに かさねたり、「すいい」で グラフも みられます。",
                goal = Goal.OpenInfo,
                highlightInfo = true,
            ),
            Step(
                chapter = Chapter.MAYOR,
                title = "じょうれいを ためす",
                body = "「じょうほう」の「じょうれい」で、まち ぜんたいの ほうしんを きめられます。" +
                    "ひようは じんこうに おうじて かかります。ひとつ いれてみましょう。",
                goal = Goal.EnableOrdinance,
                highlightInfo = true,
            ),
            Step(
                chapter = Chapter.MAYOR,
                title = "まちが そだつのを まつ",
                body = "さいごに 60かげつ すすめましょう。がっこうや びょういんの ききめが ひろがり、" +
                    "たてものが 3だんかいまで そだつと、ぜいしゅうが いっきに ふえます。" +
                    "しゅうしが マイナスなら、「よさん」で ぜいりつを あげてください。",
                goal = Goal.Advance(60),
                highlightSpeed = true,
            ),
            Step(
                chapter = Chapter.MAYOR,
                title = "そつぎょう！",
                body = "これで きほんは かんぺきです。じんこうが 2,000を こえると" +
                    "とうきょうタワー、さらに ふえると せかいの けんちくが たてられます。" +
                    "さいがいの おおさは せっていで かえられます。めざせ 大とし！",
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

    /** 情報の画面を開いてよいか。 */
    fun allowsInfo(): Boolean {
        if (!active) return true
        val g = step?.goal
        return g is Goal.OpenInfo || g is Goal.EnableOrdinance
    }

    /** いまの章。 */
    val chapter: Chapter? get() = step?.chapter

    /**
     * このステップで章が変わるか。
     * 変わるときは、区切りを見せてから次へ進む。
     */
    fun startsNewChapter(): Boolean {
        if (!active) return false
        if (stepIndex == 0) return true
        val prev = STEPS.getOrNull(stepIndex - 1)?.chapter
        return prev != null && prev != step?.chapter
    }

    /** その章が、全体の何番目か（1から）。 */
    fun chapterNumber(): Int = (chapter?.ordinal ?: 0) + 1

    /** 章の数。 */
    fun chapterCount(): Int = Chapter.entries.size

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

    fun onInfoOpened() {
        if (step?.goal is Goal.OpenInfo) advance()
    }

    fun onOrdinanceEnabled() {
        if (step?.goal is Goal.EnableOrdinance) advance()
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
