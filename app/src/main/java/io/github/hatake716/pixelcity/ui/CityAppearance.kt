package io.github.hatake716.pixelcity.ui

import android.content.res.AssetManager
import androidx.core.graphics.toColorInt
import io.github.hatake716.pixelcity.game.City
import org.json.JSONObject

/** The same dyed-yarn palette is used by Blender and the live terrain renderer. */
class CityAppearance(assets: AssetManager) {
    private val palettes = JSONObject(assets.open("realistic/city-themes.json").bufferedReader().use { it.readText() })
    private val themedUtilities = setOf("PARK", "GROVE", "FARM", "WATER_TOWER", "SCHOOL")
    fun themed(style: City.Style) = style != City.Style.STANDARD && style != City.Style.CUSTOM
    fun model(style: City.Style, id: String): String {
        if (!themed(style)) return id
        return if (id.startsWith("ZONE_")) {
            val stem = id.substringBeforeLast('_')
            "${style.name}_${stem}_${id.substringAfterLast('_').toInt() % 2}"
        } else if (id in themedUtilities) "${style.name}_$id" else id
    }
    fun color(style: City.Style, slot: String): Int {
        val name = if (style == City.Style.CUSTOM) "STANDARD" else style.name
        return ("#" + palettes.getJSONObject(name).getString(slot)).toColorInt()
    }
}
