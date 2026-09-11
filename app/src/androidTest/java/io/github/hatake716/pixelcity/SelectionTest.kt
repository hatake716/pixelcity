package io.github.hatake716.pixelcity

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.TileKind
import io.github.hatake716.pixelcity.game.Tutorial
import io.github.hatake716.pixelcity.ui.GameView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * マスを選んでから実行する流れ。
 *
 * 押し間違いで街が壊れたり、資金が減ったりしないよう、
 * タップした時点では何も起きないことを確かめる。
 */
@RunWith(AndroidJUnit4::class)
class SelectionTest {

    private lateinit var view: GameView
    private lateinit var city: City

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        city = City(32, 32).apply {
            for (t in tiles) t.terrain = Terrain.LAND
            funds = 10_000
        }
        view = GameView(context, city, Tutorial())
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 1080, 2400)
    }

    private fun tap(x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
        view.onTouchEvent(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, x, y, 0))
    }

    /** 地図の真ん中あたり。ここなら必ずマップの上。 */
    private fun mapPoint(dx: Float = 0f, dy: Float = 0f): Pair<Float, Float> =
        (540f + dx) to (900f + dy)

    /** タップしても、その場では何も建たないこと。 */
    @Test
    fun tapping_a_tile_does_not_build_anything() {
        val before = city.funds
        val (x, y) = mapPoint()
        tap(x, y)

        assertEquals("something was built", before, city.funds)
        assertTrue("nothing was selected", view.selectedCount > 0)
    }

    /** 複数のマスをまとめて選べること。 */
    @Test
    fun several_tiles_can_be_selected() {
        tap(mapPoint().first, mapPoint().second)
        tap(mapPoint(120f).first, mapPoint(120f).second)
        tap(mapPoint(-120f).first, mapPoint(-120f).second)
        assertTrue("expected 3 tiles, got ${view.selectedCount}", view.selectedCount >= 2)
    }

    /** 同じマスを2回押すと、選択から外れること。 */
    @Test
    fun tapping_a_selected_tile_again_removes_it() {
        val (x, y) = mapPoint()
        tap(x, y)
        val after1 = view.selectedCount
        tap(x, y)
        assertEquals("the tile was not removed", after1 - 1, view.selectedCount)
    }

    /** なぞると、通った道のマスがまとめて選ばれること。 */
    @Test
    fun dragging_selects_the_tiles_along_the_way() {
        val t = SystemClock.uptimeMillis()
        view.onTouchEvent(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 400f, 800f, 0))
        for (i in 1..10) {
            view.onTouchEvent(
                MotionEvent.obtain(
                    t, t + i * 10L, MotionEvent.ACTION_MOVE,
                    400f + i * 30f, 800f + i * 15f, 0,
                ),
            )
        }
        view.onTouchEvent(MotionEvent.obtain(t, t + 200, MotionEvent.ACTION_UP, 700f, 950f, 0))
        assertTrue("dragging selected only ${view.selectedCount}", view.selectedCount >= 3)
    }

    /** 道具を変えたら、選んであったマスは捨てること。 */
    @Test
    fun changing_the_tool_clears_the_selection() {
        tap(mapPoint().first, mapPoint().second)
        assertTrue(view.selectedCount > 0)
        view.selectTool(TileKind.ZONE_R)
        assertEquals("the selection survived a tool change", 0, view.selectedCount)
    }

    /** 選んでから実行すると、まとめて建つこと。 */
    @Test
    fun running_the_selection_builds_every_chosen_tile() {
        tap(mapPoint().first, mapPoint().second)
        tap(mapPoint(120f).first, mapPoint(120f).second)
        val chosen = view.selectedCount
        assertTrue("nothing was selected", chosen > 0)

        val before = city.funds
        view.runSelectionForTest()

        val roads = city.tiles.count { it.kind == TileKind.ROAD }
        assertEquals("not every tile was built", chosen, roads)
        assertTrue("nothing was paid", city.funds < before)
        assertEquals("the selection was not cleared", 0, view.selectedCount)
    }
}
