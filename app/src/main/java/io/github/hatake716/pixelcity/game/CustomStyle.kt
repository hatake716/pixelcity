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
        HOUSE_ROOF("住宅の屋根", Palette.HOUSE_ROOF, Palette.HOUSE_ROOF),
        HOUSE_ROOF_DARK("屋根の影", Palette.HOUSE_ROOF_DARK, Palette.HOUSE_ROOF_DARK),
        HOUSE_WALL("住宅の壁", Palette.HOUSE_LEFT, Palette.HOUSE_LEFT),
        HOUSE_WALL_DARK("壁の影", Palette.HOUSE_RIGHT, Palette.HOUSE_RIGHT),
        OFFICE_ROOF("商業建築の屋根", Palette.OFFICE_ROOF, Palette.OFFICE_ROOF),
        OFFICE_WALL("商業建築の壁", Palette.OFFICE_LEFT, Palette.OFFICE_LEFT),
        OFFICE_WALL_DARK("商業建築の影", Palette.OFFICE_RIGHT, Palette.OFFICE_RIGHT),
        GLASS("ガラス", Palette.GLASS, Palette.GLASS),
        GLASS_LIT("明るい窓", Palette.GLASS_LIT, Palette.GLASS_LIT),
        WINDOW_LIT("窓の明かり", Palette.WINDOW_LIT, Palette.WINDOW_LIT),
        WINDOW_DARK("暗い窓", Palette.WINDOW_DARK, Palette.WINDOW_DARK),
        FACTORY_ROOF("工場の屋根", Palette.FACTORY_ROOF, Palette.FACTORY_ROOF),
        FACTORY_WALL("工場の壁", Palette.FACTORY_LEFT, Palette.FACTORY_LEFT),
        FACTORY_WALL_DARK("工場の影", Palette.FACTORY_RIGHT, Palette.FACTORY_RIGHT),
        GENERIC_ROOF("施設の屋根", Palette.WALL_ROOF, Palette.WALL_ROOF),
        GENERIC_WALL("施設の壁", Palette.WALL_LEFT, Palette.WALL_LEFT),
        GENERIC_WALL_DARK("施設の影", Palette.WALL_RIGHT, Palette.WALL_RIGHT),
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
