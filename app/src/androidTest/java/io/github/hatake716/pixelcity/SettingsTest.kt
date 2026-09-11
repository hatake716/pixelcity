package io.github.hatake716.pixelcity

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hatake716.pixelcity.data.SaveGame
import io.github.hatake716.pixelcity.data.Settings
import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind
import io.github.hatake716.pixelcity.game.Tutorial
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 音の設定。SharedPreferences が要るので実機で確かめる。
 *
 * 設定は「遊ぶ人のもの」なので、街を切り替えても、
 * 街を消しても残っていてほしい。
 */
@RunWith(AndroidJUnit4::class)
class SettingsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("pixelcity", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    /** 初めて遊ぶときは、どちらも鳴る。 */
    @Test
    fun sound_is_on_for_a_new_player() {
        assertTrue(Settings.sfxEnabled(context))
        assertTrue(Settings.bgmEnabled(context))
    }

    /** 切った設定が残ること。 */
    @Test
    fun turning_sound_off_is_remembered() {
        Settings.setSfxEnabled(context, false)
        Settings.setBgmEnabled(context, false)
        assertFalse(Settings.sfxEnabled(context))
        assertFalse(Settings.bgmEnabled(context))
    }

    /** 効果音と音楽を、別々に切れること。 */
    @Test
    fun the_two_switches_are_independent() {
        Settings.setSfxEnabled(context, false)
        assertFalse(Settings.sfxEnabled(context))
        assertTrue("music should still be on", Settings.bgmEnabled(context))

        Settings.setSfxEnabled(context, true)
        Settings.setBgmEnabled(context, false)
        assertTrue(Settings.sfxEnabled(context))
        assertFalse(Settings.bgmEnabled(context))
    }

    /**
     * 街を保存しても、音の設定が消えないこと。
     *
     * 同じ SharedPreferences を使っているので、
     * 鍵がぶつかっていると、街を保存したときに設定が飛ぶ。
     */
    @Test
    fun saving_a_city_does_not_clear_the_sound_settings() {
        Settings.setSfxEnabled(context, false)
        Settings.setBgmEnabled(context, false)

        val city = City(32, 32).apply {
            for (t in tiles) t.terrain = Terrain.LAND
            for (x in 2..20) build(x, 10, TileKind.ROAD)
        }
        SaveGame.save(context, 0, city, Tutorial(), 1L)
        SaveGame.save(context, 1, city, Tutorial(), 2L)

        assertFalse("sfx setting was lost", Settings.sfxEnabled(context))
        assertFalse("bgm setting was lost", Settings.bgmEnabled(context))
    }

    /** 街を消しても、音の設定は残ること。 */
    @Test
    fun clearing_a_city_keeps_the_sound_settings() {
        Settings.setBgmEnabled(context, false)
        val city = City(32, 32).apply { for (t in tiles) t.terrain = Terrain.LAND }
        SaveGame.save(context, 0, city, Tutorial(), 1L)
        SaveGame.clear(context, 0)
        assertFalse("bgm setting was lost", Settings.bgmEnabled(context))
    }
}
