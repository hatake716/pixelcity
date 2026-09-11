package io.github.hatake716.pixelcity.game

/** 地形。生成時に決まり、以後変化しない。 */
enum class Terrain { LAND, WATER, SHORE }

/**
 * タイルに置かれているもの。順序は保存フォーマットの一部なので、
 * 既存の値の並びを変えず、追加は末尾に行うこと。
 */
enum class TileKind {
    EMPTY,
    ROAD,
    ZONE_R,
    ZONE_C,
    ZONE_I,
    POWER_COAL,
    POWER_SOLAR,
    PARK,
    POLICE,
    FIRE,
    SCHOOL,
    HOSPITAL,
    MONUMENT,
    // ここから下は v0.4 で追加。保存は序数なので、必ず末尾に足すこと。
    /** 農地。食料を供給し、公害を吸う。 */
    FARM,
    /** 風力発電。公害なしだが出力は小さい。 */
    POWER_WIND,
    /** 鉄道。道路より輸送力が高く、公害を減らす。 */
    RAIL,

    // --- v2: 交通 ---
    /** 大通り。道路より輸送力が高い。 */
    AVENUE,
    /** 高速道路。輸送力は最大だが、区分は直接つながらない。 */
    HIGHWAY,
    /** 地下鉄。用地を取らず、地価を下げない。 */
    SUBWAY,
    /** バス停。まわりの交通量を減らす。 */
    BUS_STOP,
    /** 地下鉄の駅。 */
    SUBWAY_STATION,
    /** 空港。商業需要を押し上げるが、公害と騒音を出す。 */
    AIRPORT,
    /** 港。工業需要を押し上げる。水辺に要接続。 */
    SEAPORT,

    // --- v2: 環境 ---
    /** 給水塔。 */
    WATER_TOWER,
    /** 浄水場。 */
    WATER_PLANT,
    /** 下水処理場。 */
    SEWAGE_PLANT,
    /** 埋立地。容量があり、満杯になると機能しない。 */
    LANDFILL,
    /** 焼却場。 */
    INCINERATOR,
    /** リサイクル施設。 */
    RECYCLING,
    /** 送電線。 */
    POWER_LINE,

    // --- v2: 保健 ---
    /** 診療所。安価で広く配れる。 */
    CLINIC;

    val isZone: Boolean get() = this == ZONE_R || this == ZONE_C || this == ZONE_I
    val isPowerPlant: Boolean
        get() = this == POWER_COAL || this == POWER_SOLAR || this == POWER_WIND

    /** 道路と同じように、線路として繋がるもの。 */
    val isTrack: Boolean get() = this == RAIL || this == SUBWAY

    /** 交通網としてつながるもの。 */
    val isTransport: Boolean
        get() = this == ROAD || this == AVENUE || this == HIGHWAY ||
            this == RAIL || this == SUBWAY

    /** 区分が直接つながれる道か。高速道路と地下鉄は出入口が要る。 */
    val isLocalRoad: Boolean
        get() = this == ROAD || this == AVENUE || this == RAIL

    /** その道の輸送力。 */
    val capacity: Int
        get() = when (this) {
            ROAD -> 100
            AVENUE -> 300
            HIGHWAY -> 800
            RAIL -> 600
            SUBWAY -> 900
            else -> 0
        }

    /** 電気を通すもの。 */
    val carriesPower: Boolean
        get() = this == POWER_LINE || isPowerPlant || isBuilding
    /** 道路網に繋がる必要があり、維持費を払う建造物か。 */
    val isBuilding: Boolean
        get() = this != EMPTY && this != ROAD && this != RAIL
}

/** 建てられる世界の有名建築。1都市につき各1つ。 */
enum class Monument(
    val label: String,
    val unlockPopulation: Int,
    val cost: Int,
    val radius: Int,
    val landValueBonus: Int,
    val tourismIncome: Int,
    val needsWater: Boolean,
    val blurb: String,
) {
    TOKYO_TOWER("とうきょうタワー", 2_000, 2_500, 12, 15, 80, false,
        "1958ねん、とうきょうにたてられたでんぱとう。\nまちのシンボルとして ちかが あがる。"),
    ARC_DE_TRIOMPHE("がいせんもん", 4_000, 3_000, 10, 20, 100, false,
        "パリのちゅうしんに たつ かちどきのもん。\nひろばに ひとが あつまる。"),
    COLOSSEUM("コロッセオ", 6_000, 3_500, 12, 18, 130, false,
        "ローマの えんけいとうぎじょう。\nふるいいしの ぶたいに かんこうきゃくが くる。"),
    STATUE_OF_LIBERTY("じゆうのめがみ", 8_000, 4_000, 14, 20, 160, true,
        "ニューヨークのみなとに たつぞう。\nみずべに たてると みなとまちが さかえる。"),
    BIG_BEN("ビッグ・ベン", 10_000, 4_500, 12, 22, 180, false,
        "ロンドンの とけいとう。\nかねのおとが まちに ひびく。"),
    LEANING_TOWER("ピサのしゃとう", 12_000, 5_000, 10, 25, 200, false,
        "かたむいたまま たちつづける しろいとう。\nめずらしさが ひとを よぶ。"),
    TAJ_MAHAL("タージ・マハル", 15_000, 6_000, 14, 28, 240, false,
        "インドの しろいだいりせきの びょう。\nうつくしさで しられる。"),
    PYRAMID("ギザのピラミッド", 20_000, 7_000, 16, 30, 300, false,
        "4500ねんまえに つくられた きょだいなはか。\nさばくに そびえる せかいのふしぎ。"),
}

/** 1タイルの状態。マップは Tile の配列で保持する。 */
class Tile {
    var terrain: Terrain = Terrain.LAND
    var kind: TileKind = TileKind.EMPTY

    /** 区分の発展段階 0..3。区分以外では常に 0。 */
    var stage: Int = 0

    /** モニュメントの本体タイルのみ非 null。占有タイルは [monumentAnchor] を持つ。 */
    var monument: Monument? = null
    /** 複数タイルを占めるモニュメントの、左上タイルの索引。本体自身も自分を指す。 */
    var monumentAnchor: Int = -1

    // --- 毎月のシミュレーションで再計算される局所値（保存しない） ---
    var landValue: Int = 0
    var pollution: Int = 0
    var safety: Int = 0
    var education: Int = 0
    var health: Int = 0
    /** 道路網で発電所まで繋がっているか。 */
    var connected: Boolean = false
    var powered: Boolean = false

    // --- v2 ---
    /** そのタイルを通る交通量。容量を超えると渋滞する。 */
    var traffic: Int = 0
    /** 犯罪の起きやすさ 0..100。 */
    var crime: Int = 0
    /** 水が来ているか。 */
    var watered: Boolean = false
    /** 埋立地に溜まったゴミ。満杯になると受け入れられない。 */
    var landfillFill: Int = 0
    /** 公共交通による交通量の軽減 0..100(%)。 */
    var transitRelief: Int = 0

    /**
     * 段階が変わった月。建ったばかりの建物をアニメーションさせるために使う。
     * -1 は「まだ一度も変わっていない」。保存しない（見た目だけのため）。
     */
    var stageChangedMonth: Int = -1

    /** 直前の段階。上がったのか下がったのかを見分ける。 */
    var previousStage: Int = 0

    /** 渋滞しているか。容量に対して交通量が多い。 */
    val congested: Boolean
        get() = kind.capacity > 0 && traffic > kind.capacity

    fun clearForBulldoze() {
        kind = TileKind.EMPTY
        stage = 0
        monument = null
        monumentAnchor = -1
    }
}

/** 建てられるものの費用・維持費・電力の一覧。 */
object BuildCost {
    fun cost(kind: TileKind): Int = when (kind) {
        TileKind.ROAD -> 8
        TileKind.ZONE_R -> 40
        TileKind.ZONE_C -> 60
        TileKind.ZONE_I -> 60
        TileKind.POWER_COAL -> 900
        TileKind.POWER_SOLAR -> 1_600
        TileKind.PARK -> 60
        TileKind.POLICE -> 300
        TileKind.FIRE -> 300
        TileKind.SCHOOL -> 400
        TileKind.HOSPITAL -> 500
        TileKind.FARM -> 30
        TileKind.POWER_WIND -> 700
        TileKind.RAIL -> 40
        // v2: 交通
        TileKind.AVENUE -> 24
        TileKind.HIGHWAY -> 60
        TileKind.SUBWAY -> 90
        TileKind.BUS_STOP -> 120
        TileKind.SUBWAY_STATION -> 400
        TileKind.AIRPORT -> 3_000
        TileKind.SEAPORT -> 2_000
        // v2: 環境
        TileKind.WATER_TOWER -> 300
        TileKind.WATER_PLANT -> 800
        TileKind.SEWAGE_PLANT -> 900
        TileKind.LANDFILL -> 200
        TileKind.INCINERATOR -> 1_200
        TileKind.RECYCLING -> 1_500
        TileKind.POWER_LINE -> 20
        // v2: 保健
        TileKind.CLINIC -> 250
        TileKind.EMPTY, TileKind.MONUMENT -> 0
    }

    /** 取り壊しの費用。 */
    const val BULLDOZE = 4

    fun upkeep(kind: TileKind): Int = when (kind) {
        TileKind.ROAD -> 1
        TileKind.POWER_COAL -> 40
        TileKind.POWER_SOLAR -> 25
        TileKind.PARK -> 2
        TileKind.POLICE -> 20
        TileKind.FIRE -> 20
        TileKind.SCHOOL -> 30
        TileKind.HOSPITAL -> 35
        TileKind.FARM -> 2
        TileKind.POWER_WIND -> 18
        TileKind.RAIL -> 3
        TileKind.AVENUE -> 3
        TileKind.HIGHWAY -> 6
        TileKind.SUBWAY -> 8
        TileKind.BUS_STOP -> 8
        TileKind.SUBWAY_STATION -> 25
        TileKind.AIRPORT -> 120
        TileKind.SEAPORT -> 80
        TileKind.WATER_TOWER -> 20
        TileKind.WATER_PLANT -> 45
        TileKind.SEWAGE_PLANT -> 50
        TileKind.LANDFILL -> 15
        TileKind.INCINERATOR -> 60
        TileKind.RECYCLING -> 70
        TileKind.POWER_LINE -> 1
        TileKind.CLINIC -> 18
        else -> 0
    }

    fun powerOutput(kind: TileKind): Int = when (kind) {
        TileKind.POWER_COAL -> 2_000
        TileKind.POWER_SOLAR -> 1_600
        // 風力は出力が小さいかわりに安く、公害も出さない。
        TileKind.POWER_WIND -> 900
        else -> 0
    }

    /** 給水できる量。人口に対して足りないと区分が育たない。 */
    fun waterOutput(kind: TileKind): Int = when (kind) {
        TileKind.WATER_TOWER -> 1_200
        TileKind.WATER_PLANT -> 4_000
        else -> 0
    }

    /** 1か月に処理できるゴミの量。 */
    fun garbageCapacity(kind: TileKind): Int = when (kind) {
        TileKind.LANDFILL -> 60
        TileKind.INCINERATOR -> 400
        TileKind.RECYCLING -> 250
        else -> 0
    }

    /** 埋立地が受け入れられる総量。満杯になると機能しない。 */
    const val LANDFILL_TOTAL = 12_000

    /** 一部の施設は人口で解禁する。 */
    fun isUnlocked(kind: TileKind, population: Int): Boolean = when (kind) {
        TileKind.POWER_SOLAR -> population >= 3_000
        TileKind.POWER_WIND -> population >= 1_000
        TileKind.RAIL -> population >= 2_000
        TileKind.AVENUE -> population >= 500
        TileKind.HIGHWAY -> population >= 5_000
        TileKind.SUBWAY, TileKind.SUBWAY_STATION -> population >= 8_000
        TileKind.BUS_STOP -> population >= 800
        TileKind.AIRPORT -> population >= 10_000
        TileKind.SEAPORT -> population >= 4_000
        TileKind.WATER_PLANT -> population >= 2_000
        TileKind.SEWAGE_PLANT -> population >= 3_000
        TileKind.INCINERATOR -> population >= 2_500
        TileKind.RECYCLING -> population >= 5_000
        else -> true
    }
}
