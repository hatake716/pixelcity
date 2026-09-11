package io.github.hatake716.pixelcity

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hatake716.pixelcity.data.SaveGame
import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind
import io.github.hatake716.pixelcity.game.Tutorial
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 10個のスロットの振る舞い。
 *
 * SharedPreferences が要るので、実機（エミュレータ）で動かす。
 * ここが壊れると、遊んでいた街が消えるので必ず確かめる。
 */
@RunWith(AndroidJUnit4::class)
class SaveSlotTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 前のテストの残りを消す
        context.getSharedPreferences("pixelcity", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun cityWith(population: Int): City = City(32, 32).apply {
        for (t in tiles) t.terrain = Terrain.LAND
        funds = 12_345
        month = 42
        // 人口を持つ街にする
        for (x in 2..20) build(x, 10, TileKind.ROAD)
        build(2, 9, TileKind.POWER_COAL)
        for (x in 4..12) {
            build(x, 9, TileKind.ZONE_R)
            build(x, 11, TileKind.ZONE_C)
        }
        repeat(24) { step() }
    }

    @Test
    fun there_are_ten_slots() {
        assertEquals(10, SaveGame.SLOT_COUNT)
        assertEquals(10, SaveGame.listSlots(context).size)
    }

    @Test
    fun a_fresh_install_has_no_saves() {
        assertFalse(SaveGame.hasAnySave(context))
        assertEquals(0, SaveGame.firstEmptySlot(context))
        assertTrue(SaveGame.listSlots(context).all { it == null })
    }

    @Test
    fun saving_to_one_slot_leaves_the_others_empty() {
        SaveGame.save(context, 3, cityWith(100), Tutorial(), 1L)
        assertTrue(SaveGame.hasSave(context, 3))
        for (i in 0 until SaveGame.SLOT_COUNT) {
            if (i != 3) assertFalse("slot $i", SaveGame.hasSave(context, i))
        }
        assertEquals(0, SaveGame.firstEmptySlot(context))
    }

    /** 別々の街が、混ざらずにそれぞれ保存されること。 */
    @Test
    fun slots_hold_separate_cities() {
        val a = cityWith(100).apply { funds = 1_111 }
        val b = cityWith(200).apply { funds = 2_222; month = 99 }
        SaveGame.save(context, 0, a, Tutorial(), 10L)
        SaveGame.save(context, 1, b, Tutorial(), 20L)

        val loadedA = SaveGame.load(context, 0)
        val loadedB = SaveGame.load(context, 1)
        assertNotNull(loadedA)
        assertNotNull(loadedB)
        assertEquals(1_111, loadedA!!.city.funds)
        assertEquals(2_222, loadedB!!.city.funds)
        assertEquals(99, loadedB.city.month)
        assertEquals(10L, loadedA.seed)
        assertEquals(20L, loadedB.seed)
    }

    @Test
    fun clearing_one_slot_keeps_the_rest() {
        SaveGame.save(context, 0, cityWith(100), Tutorial(), 1L)
        SaveGame.save(context, 1, cityWith(100), Tutorial(), 2L)
        SaveGame.clear(context, 0)
        assertFalse(SaveGame.hasSave(context, 0))
        assertTrue(SaveGame.hasSave(context, 1))
        assertTrue(SaveGame.hasAnySave(context))
    }

    /** 一覧が、街を読まずに要約を出せること。 */
    @Test
    fun the_list_summarises_each_city() {
        val city = cityWith(100)
        SaveGame.save(context, 5, city, Tutorial(), 1L)
        val info = SaveGame.slotInfo(context, 5)
        assertNotNull(info)
        assertEquals(5, info!!.slot)
        assertEquals(city.population, info.population)
        assertEquals(city.month, info.month)
        assertEquals(city.funds, info.funds)
        assertTrue("savedAt was not recorded", info.savedAt > 0)
        // 年月の表示
        assertTrue(info.dateLabel.contains("ねん"))
        assertTrue(info.dateLabel.contains("がつ"))
    }

    /** チュートリアルの途中かどうかが、一覧に出せること。 */
    @Test
    fun the_list_shows_whether_the_tutorial_is_running() {
        val running = Tutorial().apply { start() }
        SaveGame.save(context, 0, cityWith(0), running, 1L)
        assertTrue(SaveGame.slotInfo(context, 0)!!.tutorialActive)

        val done = Tutorial().apply { skip() }
        SaveGame.save(context, 1, cityWith(0), done, 1L)
        assertFalse(SaveGame.slotInfo(context, 1)!!.tutorialActive)
    }

    @Test
    fun first_empty_slot_skips_the_used_ones() {
        SaveGame.save(context, 0, cityWith(0), Tutorial(), 1L)
        SaveGame.save(context, 1, cityWith(0), Tutorial(), 1L)
        assertEquals(2, SaveGame.firstEmptySlot(context))
    }

    @Test
    fun every_slot_can_be_filled() {
        for (i in 0 until SaveGame.SLOT_COUNT) {
            SaveGame.save(context, i, cityWith(0).apply { funds = i * 100 }, Tutorial(), i.toLong())
        }
        assertNull("there should be no empty slot left", SaveGame.firstEmptySlot(context))
        for (i in 0 until SaveGame.SLOT_COUNT) {
            assertEquals("slot $i", i * 100, SaveGame.load(context, i)!!.city.funds)
        }
    }

    /**
     * 更新前（1スロットだけ）に遊んでいた街が、最初の枠へ引き継がれること。
     * これが壊れると、更新した人の街が消えてしまう。
     */
    @Test
    fun a_city_from_the_old_single_slot_version_is_carried_over() {
        // 旧版と同じ形で書き込む
        val city = cityWith(100).apply { funds = 7_777 }
        SaveGame.save(context, 0, city, Tutorial(), 55L)
        val raw = context.getSharedPreferences("pixelcity", Context.MODE_PRIVATE)
            .getString("city_0", null)
        assertNotNull(raw)
        context.getSharedPreferences("pixelcity", Context.MODE_PRIVATE).edit()
            .remove("city_0")
            .putString("city", raw)   // 旧版の置き場所
            .commit()

        assertFalse(SaveGame.hasSave(context, 0))
        SaveGame.migrateLegacySave(context)

        assertTrue("the old city was lost", SaveGame.hasSave(context, 0))
        assertEquals(7_777, SaveGame.load(context, 0)!!.city.funds)
        // 移したあと、旧版の置き場所は空になる
        assertNull(
            context.getSharedPreferences("pixelcity", Context.MODE_PRIVATE)
                .getString("city", null),
        )
    }

    /** 引き継ぎを二度呼んでも、すでにある街を壊さないこと。 */
    @Test
    fun migrating_twice_does_not_overwrite_a_newer_city() {
        val old = cityWith(0).apply { funds = 1 }
        SaveGame.save(context, 0, old, Tutorial(), 1L)
        val raw = context.getSharedPreferences("pixelcity", Context.MODE_PRIVATE)
            .getString("city_0", null)!!
        // 旧版の置き場所に古い街、スロット0には新しい街
        val fresh = cityWith(0).apply { funds = 9_999 }
        SaveGame.save(context, 0, fresh, Tutorial(), 2L)
        context.getSharedPreferences("pixelcity", Context.MODE_PRIVATE)
            .edit().putString("city", raw).commit()

        SaveGame.migrateLegacySave(context)
        assertEquals("the newer city was overwritten", 9_999, SaveGame.load(context, 0)!!.city.funds)
    }

    /** 壊れた保存を読んでも落ちず、その枠が空になること。 */
    @Test
    fun a_corrupted_slot_is_discarded_instead_of_crashing() {
        context.getSharedPreferences("pixelcity", Context.MODE_PRIVATE)
            .edit().putString("city_4", "{ this is not valid json").commit()
        assertNull(SaveGame.load(context, 4))
        assertFalse(SaveGame.hasSave(context, 4))
        assertNull(SaveGame.slotInfo(context, 4))
    }
}
