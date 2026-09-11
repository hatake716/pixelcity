package io.github.hatake716.pixelcity.game

/**
 * 条例。街全体に効く方針で、複数を同時に有効にできる。
 *
 * 費用は「人口 × [costPerCitizen]」で毎月かかる。小さな街では安く、
 * 大きな街では重くのしかかるので、いつ導入するかの判断が生まれる。
 *
 * 係数は、1つあたりが**税収の1割ほど**になるように決めてある。
 * これより高いと、入れた瞬間に赤字へ転げ落ちて選択肢にならない。
 * （最初は 0.5〜1.0 にしていたが、人口15,000の街で月15,000かかり、
 *   収入6,400をはるかに超えていた）
 */
enum class Ordinance(
    val label: String,
    val detail: String,
    val costPerCitizen: Float,
) {
    ENERGY_SAVING(
        label = "しょうエネ じょうれい",
        detail = "でんりょくの しょうひを 15% へらす",
        costPerCitizen = 0.05f,
    ),
    RECYCLING(
        label = "リサイクル じょうれい",
        detail = "ゴミを 25% へらす",
        costPerCitizen = 0.05f,
    ),
    PATROL(
        label = "ぼうはん パトロール",
        detail = "はんざいを 20% へらす",
        costPerCitizen = 0.08f,
    ),
    FREE_CLINIC(
        label = "むりょう しんりょう",
        detail = "けんこうを あげ、かんせんを 20% へらす",
        costPerCitizen = 0.10f,
    ),
    EDUCATION(
        label = "きょういく しんこう",
        detail = "きょういくを あげる",
        costPerCitizen = 0.08f,
    ),
    TRANSIT_SUBSIDY(
        label = "こうきょうこうつう ほじょ",
        detail = "こうつうりょうを 15% へらす",
        costPerCitizen = 0.06f,
    ),
}
