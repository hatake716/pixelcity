package io.github.hatake716.pixelcity

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hatake716.pixelcity.game.City
import io.github.hatake716.pixelcity.game.Terrain
import io.github.hatake716.pixelcity.game.Tutorial
import io.github.hatake716.pixelcity.ui.GameView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2本指でつまんで拡大・縮小。
 *
 * 指の動きは [MotionEvent] を組み立てて流し込む。
 * `adb shell input` では2本目の指を送れないので、ここで確かめる。
 */
@RunWith(AndroidJUnit4::class)
class PinchZoomTest {

    private lateinit var view: GameView

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val city = City(32, 32).apply { for (t in tiles) t.terrain = Terrain.LAND }
        view = GameView(context, city, Tutorial())
        // 画面の大きさを与える。これがないと論理座標に直せない。
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 1080, 2400)
    }

    /** 2本指の位置から [MotionEvent] を作る。 */
    private fun twoFingerEvent(
        action: Int, x0: Float, y0: Float, x1: Float, y1: Float,
    ): MotionEvent {
        val t = SystemClock.uptimeMillis()
        val props = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER },
            MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER },
        )
        val coords = arrayOf(
            MotionEvent.PointerCoords().apply { x = x0; y = y0; pressure = 1f; size = 1f },
            MotionEvent.PointerCoords().apply { x = x1; y = y1; pressure = 1f; size = 1f },
        )
        return MotionEvent.obtain(
            t, t, action, 2, props, coords, 0, 0, 1f, 1f, 0, 0, 0, 0,
        )
    }

    private fun oneFingerEvent(action: Int, x: Float, y: Float): MotionEvent {
        val t = SystemClock.uptimeMillis()
        return MotionEvent.obtain(t, t, action, x, y, 0)
    }

    /**
     * 指を広げると拡大し、縮めると縮小すること。
     *
     * 画面の中ほど（地図の上）でつまむ。
     */
    private fun pinch(from: Float, to: Float): Float {
        val cx = 540f
        val cy = 1100f
        // 1本目
        view.onTouchEvent(oneFingerEvent(MotionEvent.ACTION_DOWN, cx, cy))
        // 2本目。ここが基準の間隔になる。
        view.onTouchEvent(
            twoFingerEvent(
                MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                cx - from / 2, cy, cx + from / 2, cy,
            ),
        )
        // 広げる/縮める。指は少しずつ動くので、何回かに分けて送る。
        val steps = 8
        for (i in 1..steps) {
            val span = from + (to - from) * i / steps
            view.onTouchEvent(
                twoFingerEvent(MotionEvent.ACTION_MOVE, cx - span / 2, cy, cx + span / 2, cy),
            )
        }
        val result = view.zoom
        view.onTouchEvent(
            twoFingerEvent(
                MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                cx - to / 2, cy, cx + to / 2, cy,
            ),
        )
        view.onTouchEvent(oneFingerEvent(MotionEvent.ACTION_UP, cx, cy))
        return result
    }

    @Test
    fun spreading_two_fingers_zooms_in() {
        val before = view.zoom
        val after = pinch(200f, 400f)     // 2倍に広げる
        assertTrue("zoom did not change ($before -> $after)", after > before)
    }

    @Test
    fun pinching_two_fingers_zooms_out() {
        val before = view.zoom
        val after = pinch(400f, 200f)     // 半分に縮める
        assertTrue("zoom did not change ($before -> $after)", after < before)
    }

    /**
     * 指の開きに、そのままついてくること。
     *
     * 2倍にひらいたら、拡大率もおよそ2倍になる。
     * 段で追っていたころは、一定以上ひらくまで何も起きず、
     * そのあと急に跳んでいた。
     */
    @Test
    fun the_zoom_follows_the_fingers() {
        // 端で頭打ちにならないよう、真ん中あたりから始める
        view.setZoomForTest(0.25f)
        val before = view.zoom
        val after = pinch(200f, 400f)
        val ratio = after / before
        assertTrue("expected about 2x, got ${ratio}x", ratio > 1.6f && ratio < 2.4f)
    }

    /** 少し動かしたら、少しだけ変わること（何も起きない区間がない）。 */
    @Test
    fun a_small_movement_changes_the_zoom_a_little() {
        view.setZoomForTest(0.25f)
        val before = view.zoom
        val after = pinch(300f, 345f)     // 15% ほど
        val ratio = after / before
        assertTrue("nothing happened (ratio $ratio)", ratio > 1.02f)
        assertTrue("it jumped too far (ratio $ratio)", ratio < 1.4f)
    }

    /** 端まで行ったら、それ以上は変わらないこと。 */
    @Test
    fun the_zoom_stops_at_both_ends() {
        repeat(10) { pinch(100f, 400f) }
        val maxZoom = view.zoom
        pinch(100f, 400f)
        assertEquals("zoom went past the closest step", maxZoom, view.zoom, 0.0001f)

        repeat(14) { pinch(400f, 100f) }
        val minZoom = view.zoom
        pinch(400f, 100f)
        assertEquals("zoom went past the widest step", minZoom, view.zoom, 0.0001f)
        assertTrue("the two ends are the same", minZoom < maxZoom)
    }
}
