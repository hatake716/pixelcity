package io.github.hatake716.pixelcity.data

import android.content.Context
import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Monument
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind
import io.github.hatake716.pixelcity.game.Tutorial
import org.json.JSONArray
import org.json.JSONObject

/**
 * 都市の保存と復元。SharedPreferences に JSON で1スロットだけ持つ。
 *
 * タイルは1文字ずつの文字列にまとめて、32×32 でも小さく収まるようにしている。
 * 列挙の序数をそのまま保存しているので、[TileKind] と [Terrain] の
 * 既存の並びを変えてはいけない（追加は末尾に）。
 */
object SaveGame {
    private const val PREFS = "pixelcity"
    private const val KEY_CITY = "city"
    private const val VERSION = 1

    fun save(context: Context, city: City, tutorial: Tutorial, seed: Long) {
        val json = JSONObject().apply {
            put("version", VERSION)
            put("seed", seed)
            put("width", city.width)
            put("height", city.height)
            put("funds", city.funds)
            put("month", city.month)
            put("taxRate", city.taxRate)
            put("monthsInDebt", city.monthsInDebt)
            put("gameOver", city.gameOver)

            val terrain = StringBuilder(city.tiles.size)
            val kinds = StringBuilder(city.tiles.size)
            val stages = StringBuilder(city.tiles.size)
            for (t in city.tiles) {
                terrain.append(('0' + t.terrain.ordinal))
                kinds.append(('0' + t.kind.ordinal))
                stages.append(('0' + t.stage))
            }
            put("terrain", terrain.toString())
            put("kinds", kinds.toString())
            put("stages", stages.toString())

            // モニュメントは、本体タイルの索引と種類だけを持つ。
            val monuments = JSONArray()
            for ((i, t) in city.tiles.withIndex()) {
                val m = t.monument ?: continue
                monuments.put(JSONObject().apply {
                    put("i", i)
                    put("m", m.name)
                })
            }
            put("monuments", monuments)

            put("tutorial", JSONArray().apply { tutorial.saveState().forEach { put(it) } })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_CITY, json.toString()).apply()
    }

    fun hasSave(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_CITY)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_CITY).apply()
    }

    /** 読み込めなければ null。壊れた保存で落ちないようにする。 */
    fun load(context: Context): Loaded? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CITY, null) ?: return null
        return try {
            parse(raw)
        } catch (e: Exception) {
            // 壊れた保存は捨てる。遊べなくなるより良い。
            clear(context)
            null
        }
    }

    data class Loaded(val city: City, val tutorial: Tutorial, val seed: Long)

    /** テストから直接呼べるように、文字列の解釈を分けてある。 */
    fun parse(raw: String): Loaded {
        val json = JSONObject(raw)
        val width = json.getInt("width")
        val height = json.getInt("height")
        val city = City(width, height)
        city.funds = json.getInt("funds")
        city.month = json.getInt("month")
        city.taxRate = json.getInt("taxRate")
        city.monthsInDebt = json.optInt("monthsInDebt", 0)
        city.gameOver = json.optBoolean("gameOver", false)

        val terrain = json.getString("terrain")
        val kinds = json.getString("kinds")
        val stages = json.getString("stages")
        val terrainValues = Terrain.entries
        val kindValues = TileKind.entries
        for (i in city.tiles.indices) {
            val t = city.tiles[i]
            t.terrain = terrainValues.getOrElse(terrain[i] - '0') { Terrain.LAND }
            t.kind = kindValues.getOrElse(kinds[i] - '0') { TileKind.EMPTY }
            t.stage = (stages[i] - '0').coerceIn(0, 3)
        }

        val monuments = json.optJSONArray("monuments")
        if (monuments != null) {
            for (n in 0 until monuments.length()) {
                val entry = monuments.getJSONObject(n)
                val anchor = entry.getInt("i")
                val m = Monument.entries.firstOrNull { it.name == entry.getString("m") } ?: continue
                val ax = anchor % width
                val ay = anchor / width
                for (dy in 0..1) for (dx in 0..1) {
                    if (!city.inBounds(ax + dx, ay + dy)) continue
                    val tile = city.tileAt(ax + dx, ay + dy)
                    tile.kind = TileKind.MONUMENT
                    tile.monumentAnchor = anchor
                }
                city.tiles[anchor].monument = m
                city.builtMonuments.add(m)
            }
        }

        val tutorial = Tutorial()
        val ts = json.optJSONArray("tutorial")
        if (ts != null) {
            tutorial.restore(IntArray(ts.length()) { ts.getInt(it) })
        }

        return Loaded(city, tutorial, json.optLong("seed", 0L))
    }
}
