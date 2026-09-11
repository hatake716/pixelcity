package io.github.hatake716.pixelcity.game

import io.github.hatake716.pixelcity.ui.Palette

/**
 * 自分で決める街並みの色。
 *
 * 建物のどの部分にどの色を使うかを、ひとつずつ選べる。
 * 既定値は「ひょうじゅん」と同じで、そこから変えていく形にしてある。
 *
 * ここが持つのは [io.github.hatake716.pixelcity.ui.Palette] の索引なので、
 * 保存するときも数値をそのまま書けばよい。
 */
class CustomStyle {

    /**
     * 色を割り当てられる場所。
     *
     * [source] は、もとの絵で使われている色。ここを [Slot] の色へ置き換える。
     * 並びは保存の順序でもあるので、既存の項目の順を変えないこと（追加は末尾へ）。
     */
    enum class Slot(val label: String, val source: Int, val default: Int) {
        HOUSE_ROOF("じゅうたくの やね", Palette.HOUSE_ROOF, Palette.HOUSE_ROOF),
        HOUSE_ROOF_DARK("やねの かげ", Palette.HOUSE_ROOF_DARK, Palette.HOUSE_ROOF_DARK),
        HOUSE_WALL("じゅうたくの かべ", Palette.HOUSE_LEFT, Palette.HOUSE_LEFT),
        HOUSE_WALL_DARK("かべの かげ", Palette.HOUSE_RIGHT, Palette.HOUSE_RIGHT),
        OFFICE_ROOF("ビルの やね", Palette.OFFICE_ROOF, Palette.OFFICE_ROOF),
        OFFICE_WALL("ビルの かべ", Palette.OFFICE_LEFT, Palette.OFFICE_LEFT),
        OFFICE_WALL_DARK("ビルの かげ", Palette.OFFICE_RIGHT, Palette.OFFICE_RIGHT),
        GLASS("ガラス", Palette.GLASS, Palette.GLASS),
        GLASS_LIT("ひかる ガラス", Palette.GLASS_LIT, Palette.GLASS_LIT),
        WINDOW_LIT("まどの あかり", Palette.WINDOW_LIT, Palette.WINDOW_LIT),
        WINDOW_DARK("くらい まど", Palette.WINDOW_DARK, Palette.WINDOW_DARK),
        FACTORY_ROOF("こうじょうの やね", Palette.FACTORY_ROOF, Palette.FACTORY_ROOF),
        FACTORY_WALL("こうじょうの かべ", Palette.FACTORY_LEFT, Palette.FACTORY_LEFT),
        FACTORY_WALL_DARK("こうじょうの かげ", Palette.FACTORY_RIGHT, Palette.FACTORY_RIGHT),
        GENERIC_ROOF("しせつの やね", Palette.WALL_ROOF, Palette.WALL_ROOF),
        GENERIC_WALL("しせつの かべ", Palette.WALL_LEFT, Palette.WALL_LEFT),
        GENERIC_WALL_DARK("しせつの かげ", Palette.WALL_RIGHT, Palette.WALL_RIGHT),
    }

    /** 場所ごとに選んだ色（[Palette] の索引）。 */
    private val chosen = IntArray(Slot.entries.size) { Slot.entries[it].default }

    operator fun get(slot: Slot): Int = chosen[slot.ordinal]

    operator fun set(slot: Slot, colour: Int) {
        chosen[slot.ordinal] = colour
    }

    /** すべて既定へ戻す。 */
    fun reset() {
        for (s in Slot.entries) chosen[s.ordinal] = s.default
    }

    /** 既定のままか。まだ何も触っていないかを見分ける。 */
    val isDefault: Boolean
        get() = Slot.entries.all { chosen[it.ordinal] == it.default }

    /** 描画に渡す、置き換えの対応。 */
    fun toMap(): Map<Int, Int> =
        Slot.entries.associate { it.source to chosen[it.ordinal] }

    /** 保存用。索引を順に並べる。 */
    fun save(): IntArray = chosen.copyOf()

    fun restore(values: IntArray) {
        for (i in chosen.indices) {
            if (i < values.size) chosen[i] = values[i]
        }
    }

}
