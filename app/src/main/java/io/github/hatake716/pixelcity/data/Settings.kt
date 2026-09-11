package io.github.hatake716.pixelcity.data

import android.content.Context

/**
 * 遊ぶ人ごとの設定。
 *
 * セーブデータ（[SaveGame]）とは分けてある。
 * 音を切ったかどうかは街ごとの話ではなく、遊ぶ人の好みなので、
 * どのスロットを開いても同じであってほしい。
 */
object Settings {

    private const val PREFS = "pixelcity"
    private const val KEY_SFX = "sfxEnabled"
    private const val KEY_BGM = "bgmEnabled"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 効果音を鳴らすか。初めて遊ぶときはオン。 */
    fun sfxEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SFX, true)

    /** 曲を流すか。初めて遊ぶときはオン。 */
    fun bgmEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BGM, true)

    fun setSfxEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SFX, value).apply()
    }

    fun setBgmEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_BGM, value).apply()
    }
}
