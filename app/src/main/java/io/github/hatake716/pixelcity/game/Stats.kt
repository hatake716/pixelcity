package io.github.hatake716.pixelcity.game

/**
 * 街の統計。毎月の [City.step] の終わりに記録する。
 *
 * グラフと一覧に出すためのもので、シミュレーションはこれを読まない。
 */
data class MonthlyStat(
    val month: Int,
    val population: Int,
    val funds: Int,
    val balance: Int,
    val pollution: Int,
    val crime: Int,
    val unemployment: Int,
    val health: Int,
    val traffic: Int,
)

/** 収入の内訳。どこから税が入っているかを見せる。 */
data class Income(
    val residential: Int = 0,
    val commercial: Int = 0,
    val industrial: Int = 0,
    val tourism: Int = 0,
) {
    val total: Int get() = residential + commercial + industrial + tourism
}

/** 支出の内訳。何にお金がかかっているかを見せる。 */
data class Spending(
    val transport: Int = 0,
    val power: Int = 0,
    val water: Int = 0,
    val garbage: Int = 0,
    val safety: Int = 0,
    val health: Int = 0,
    val education: Int = 0,
    val parks: Int = 0,
    val ordinances: Int = 0,
) {
    val total: Int
        get() = transport + power + water + garbage + safety +
            health + education + parks + ordinances
}

/** 市長への助言。いま街が困っていること。 */
data class Advice(
    val text: String,
    /** 深刻なほど大きい。並べるときの順序に使う。 */
    val severity: Int,
)
