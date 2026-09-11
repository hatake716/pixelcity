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
    MONUMENT;

    val isZone: Boolean get() = this == ZONE_R || this == ZONE_C || this == ZONE_I
    val isPowerPlant: Boolean get() = this == POWER_COAL || this == POWER_SOLAR
    /** 道路網に繋がる必要があり、維持費を払う建造物か。 */
    val isBuilding: Boolean
        get() = this != EMPTY && this != ROAD
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
        else -> 0
    }

    fun powerOutput(kind: TileKind): Int = when (kind) {
        TileKind.POWER_COAL -> 2_000
        TileKind.POWER_SOLAR -> 1_600
        else -> 0
    }

    /** 太陽光発電は人口3000から解禁。 */
    fun isUnlocked(kind: TileKind, population: Int): Boolean = when (kind) {
        TileKind.POWER_SOLAR -> population >= 3_000
        else -> true
    }
}
