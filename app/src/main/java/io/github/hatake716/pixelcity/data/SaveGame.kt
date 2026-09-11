package io.github.hatake716.pixelcity.data

import android.content.Context
import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Monument
import io.github.hatake716.pixelcity.game.MonthlyStat
import io.github.hatake716.pixelcity.game.Ordinance
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind
import io.github.hatake716.pixelcity.game.Tutorial
import org.json.JSONArray
import org.json.JSONObject

/**
 * 都市の保存と復元。SharedPreferences に JSON で持つ。
 *
 * 保存先は [SLOT_COUNT] 個の「スロット」。遊んでいるスロットへ自動で保存し続け、
 * タイトルの「つづきから」で選んで再開する。別のスロットを使えば、
 * 前の街を消さずに新しい街を始められる。
 *
 * タイルは1文字ずつの文字列にまとめて、大きなマップでも小さく収まるようにしている。
 * 列挙の序数をそのまま保存しているので、[TileKind] と [Terrain] の
 * 既存の並びを変えてはいけない（追加は末尾に）。
 */
object SaveGame {
    private const val PREFS = "pixelcity"
    /** 保存できる街の数。 */
    const val SLOT_COUNT = 10
    private const val VERSION = 3

    /** 旧版（1スロットだけだったころ）の保存先。読み込んで引き継ぐために残す。 */
    private const val LEGACY_KEY = "city"

    private fun key(slot: Int) = "city_$slot"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, slot: Int, city: City, tutorial: Tutorial, seed: Long) {
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

            // v2: 条例・災害の設定・溜まったゴミ・感染
            put("ordinances", JSONArray().apply { city.ordinances.forEach { put(it.name) } })
            put("disasterLevel", city.disasterLevel.name)
            put("style", city.style.name)
            put("customStyle", JSONArray().apply { city.customStyle.save().forEach { put(it) } })
            put("garbageBacklog", city.garbageBacklog)
            put("infection", city.infection)
            // 埋立地の埋まり具合は、タイルごとに持つ
            val fills = JSONArray()
            for ((i, t) in city.tiles.withIndex()) {
                if (t.landfillFill > 0) {
                    fills.put(JSONObject().apply { put("i", i); put("f", t.landfillFill) })
                }
            }
            put("landfills", fills)
            // 推移の記録
            put("history", JSONArray().apply {
                for (h in city.history) {
                    put(JSONObject().apply {
                        put("m", h.month); put("p", h.population); put("f", h.funds)
                        put("b", h.balance); put("o", h.pollution); put("c", h.crime)
                        put("u", h.unemployment); put("h", h.health); put("t", h.traffic)
                    })
                }
            })

            // 一覧に出すための要約。街全体を読まずに済ませる。
            put("population", city.population)
            put("savedAt", System.currentTimeMillis())
        }
        prefs(context).edit().putString(key(slot), json.toString()).apply()
    }

    /** そのスロットに街があるか。 */
    fun hasSave(context: Context, slot: Int): Boolean = prefs(context).contains(key(slot))

    /** どれか1つでも街があるか。「つづきから」を出すかの判定に使う。 */
    fun hasAnySave(context: Context): Boolean =
        (0 until SLOT_COUNT).any { hasSave(context, it) }

    fun clear(context: Context, slot: Int) {
        prefs(context).edit().remove(key(slot)).apply()
    }

    /** 空いている最初のスロット。すべて埋まっていれば null。 */
    fun firstEmptySlot(context: Context): Int? =
        (0 until SLOT_COUNT).firstOrNull { !hasSave(context, it) }

    /**
     * 一覧に出すための、スロットの要約。
     * 街の中身は読まないので、10個ぶん集めても軽い。
     */
    data class SlotInfo(
        val slot: Int,
        val population: Int,
        val month: Int,
        val funds: Int,
        val savedAt: Long,
        val tutorialActive: Boolean,
    ) {
        /** 「1905ねん7がつ」の形。 */
        val dateLabel: String get() = "${1900 + month / 12}ねん${month % 12 + 1}がつ"
    }

    /** 全スロットの要約。街のないスロットは null。 */
    fun listSlots(context: Context): List<SlotInfo?> =
        (0 until SLOT_COUNT).map { slotInfo(context, it) }

    fun slotInfo(context: Context, slot: Int): SlotInfo? {
        val raw = prefs(context).getString(key(slot), null) ?: return null
        return try {
            val json = JSONObject(raw)
            val ts = json.optJSONArray("tutorial")
            SlotInfo(
                slot = slot,
                population = json.optInt("population", 0),
                month = json.optInt("month", 0),
                funds = json.optInt("funds", 0),
                savedAt = json.optLong("savedAt", 0L),
                tutorialActive = ts != null && ts.length() > 0 && ts.optInt(0) == 1,
            )
        } catch (e: Exception) {
            null
        }
    }

    /** 読み込めなければ null。壊れた保存で落ちないようにする。 */
    fun load(context: Context, slot: Int): Loaded? {
        val raw = prefs(context).getString(key(slot), null) ?: return null
        return try {
            parse(raw)
        } catch (e: Exception) {
            // 壊れた保存は捨てる。遊べなくなるより良い。
            clear(context, slot)
            null
        }
    }

    /**
     * 旧版（1スロットだけ）の保存を、スロット0へ移す。
     * すでに移してあるか、旧版の保存がなければ何もしない。
     * これがないと、更新した人の街が消えてしまう。
     */
    fun migrateLegacySave(context: Context) {
        val p = prefs(context)
        val legacy = p.getString(LEGACY_KEY, null) ?: return
        if (!p.contains(key(0))) {
            p.edit().putString(key(0), legacy).apply()
        }
        p.edit().remove(LEGACY_KEY).apply()
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

        // v2 の状態。古いセーブには入っていないので、なければ既定値のまま。
        json.optJSONArray("ordinances")?.let { arr ->
            for (n in 0 until arr.length()) {
                Ordinance.entries.firstOrNull { it.name == arr.getString(n) }
                    ?.let { city.ordinances.add(it) }
            }
        }
        json.optString("disasterLevel", "").takeIf { it.isNotEmpty() }?.let { name ->
            City.DisasterLevel.entries.firstOrNull { it.name == name }
                ?.let { city.disasterLevel = it }
        }
        json.optJSONArray("customStyle")?.let { arr ->
            city.customStyle.restore(IntArray(arr.length()) { arr.getInt(it) })
        }
        json.optString("style", "").takeIf { it.isNotEmpty() }?.let { name ->
            City.Style.entries.firstOrNull { it.name == name }?.let { city.style = it }
        }
        city.garbageBacklog = json.optInt("garbageBacklog", 0)
        city.infection = json.optInt("infection", 0)
        json.optJSONArray("landfills")?.let { arr ->
            for (n in 0 until arr.length()) {
                val e = arr.getJSONObject(n)
                val i = e.getInt("i")
                if (i in city.tiles.indices) city.tiles[i].landfillFill = e.getInt("f")
            }
        }
        json.optJSONArray("history")?.let { arr ->
            for (n in 0 until arr.length()) {
                val e = arr.getJSONObject(n)
                city.history.add(
                    MonthlyStat(
                        month = e.optInt("m"), population = e.optInt("p"),
                        funds = e.optInt("f"), balance = e.optInt("b"),
                        pollution = e.optInt("o"), crime = e.optInt("c"),
                        unemployment = e.optInt("u"), health = e.optInt("h"),
                        traffic = e.optInt("t"),
                    ),
                )
            }
        }

        // 人口などはタイルから導かれる値なので、保存せずに組み直す。
        city.recomputeDerivedState()

        return Loaded(city, tutorial, json.optLong("seed", 0L))
    }
}
