package io.github.hatake716.pixelcity

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import io.github.hatake716.pixelcity.data.*
import io.github.hatake716.pixelcity.game.*
import io.github.hatake716.pixelcity.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Creates test saves on the disposable emulator, never on the daily-use device. */
@RunWith(AndroidJUnit4::class)
class DevelopedWorldFlowTest {
    private val context: Context get()=ApplicationProvider.getApplicationContext()
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun content(a: MainActivity)=((a.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0) as ViewGroup).getChildAt(0)
    private fun screenshot(name: String): Bitmap {
        val b=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!
        File(context.getExternalFilesDir(null),"world-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }
        return b
    }
    private fun click(text: String) { device.wait(Until.findObject(By.text(text)),8000)!!.click();device.waitForIdle() }
    private fun openWorld(): ActivityScenario<MainActivity> {
        context.getSharedPreferences("pixelcity",Context.MODE_PRIVATE).edit().clear().commit()
        return ActivityScenario.launch(MainActivity::class.java).also { s ->
            s.onActivity { (content(it) as TitleView).onShowcase!!.invoke() }
            awaitRendered(s,::content);device.waitForIdle();screenshot("picker").recycle()
            click("発展した世界");click("開始")
            s.onActivity { (content(it) as GameView).setSpeed(0) }
            awaitRendered(s,::content);awaitGlobe(s,::content)
            SystemClock.sleep(800)
        }
    }
    private fun captureCity(s: ActivityScenario<MainActivity>,name: String) {
        SystemClock.sleep(600)
        val end=SystemClock.uptimeMillis()+30000
        while(SystemClock.uptimeMillis()<end) {
            var ready=false;s.onActivity { a -> (content(a) as GameView).let { g ->
                ready=g.globe!!.let { it.renderedZoom==24f && it.cityModelsDrawn>5 && !it.cityModelsPending }
            } }
            if(ready) { SystemClock.sleep(200);screenshot(name).recycle();return }
            SystemClock.sleep(100)
        };fail("City never rendered: $name")
    }
    private fun tapHome(s: ActivityScenario<MainActivity>) {
        s.onActivity { a ->
            val g=content(a) as GameView
            val rect=GameView::class.java.getDeclaredField("homeButton").apply { isAccessible=true }.get(g) as Rect
            val (scale,lw,lh)=GameView.layoutFor(g.width,g.height)
            val ox=(g.width-lw*scale)/2;val oy=(g.height-lh*scale)/2
            val now=SystemClock.uptimeMillis()
            for(action in intArrayOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                val e=MotionEvent.obtain(now,now+30*action,action,ox+rect.centerX().toFloat()*scale,oy+rect.centerY().toFloat()*scale,0)
                g.dispatchTouchEvent(e);e.recycle()
            }
        }
    }
    @Test fun listEntryStartsOneWorldAndEveryDestinationRendersAndSurvivesRestart() {
        lateinit var destinations: List<City.Destination>
        openWorld().use { s ->
            screenshot("planet").recycle()
            s.onActivity { a -> val g=content(a) as GameView
                assertEquals(4,g.city.destinations.size);assertEquals(3140,g.city.population)
                assertSame(g.city,g.planet!!.city);destinations=g.city.destinations.toList()
            }
            for((i,d) in destinations.withIndex()) {
                tapHome(s);click("${d.name}（${d.region}）")
                s.onActivity { a -> val p=(content(a) as GameView).planet!!;assertEquals(d.x,p.selectedX);assertEquals(d.y,p.selectedY) }
                captureCity(s,"city-$i")
            }
        }
        val saved=SaveGame.load(context,0)!!
        assertEquals(destinations,saved.city.destinations);assertTrue(saved.city.localStyles.isNotEmpty())
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            s.onActivity { (content(it) as TitleView).onContinue!!.invoke() }
            s.onActivity { (content(it) as SlotView).onSlotChosen!!.invoke(0,true);(content(it) as GameView).setSpeed(0) }
            awaitRendered(s,::content)
            tapHome(s);assertTrue(device.wait(Until.hasObject(By.text("都市移動")),5000))
            click("${destinations.first().name}（${destinations.first().region}）")
            captureCity(s,"restored-harbour")
        }
    }
    private fun buttons(v: View): List<Button> = if(v is Button) listOf(v) else if(v is ViewGroup)
        (0 until v.childCount).flatMap { buttons(v.getChildAt(it)) } else emptyList()
    @Test fun terrainTrayHasReadableRenderedContrastAndLightPickers() {
        openWorld().use { s ->
            s.onActivity { (content(it) as GameView).openTerrainEditor() }
            assertTrue(device.wait(Until.hasObject(By.text("地形を適用 $0")),5000));device.waitForIdle()
            lateinit var controls: List<Button>
            s.onActivity { a ->
                val editor=(GameView::class.java.getDeclaredField("terrainEditorDelegate").apply { isAccessible=true }.get(content(a)) as Lazy<*>).value
                val dialog=GlobeTerrainEditor::class.java.getDeclaredField("dialog").apply { isAccessible=true }.get(editor) as Dialog
                controls=buttons(dialog.window!!.decorView)
                assertEquals(9,controls.size)
            }
            fun contrast(name: String) {
                val b=screenshot(name)
                s.onActivity {
                    for(button in controls) {
                        val visible=Rect();assertTrue(button.getGlobalVisibleRect(visible))
                        val location=IntArray(2);button.getLocationOnScreen(location)
                        val r=Rect(location[0],location[1],location[0]+button.width,location[1]+button.height)
                        val bg=b.getPixel(r.centerX(),r.bottom-(button.resources.displayMetrics.density*6).toInt())
                        val ratio=ColorUtils.calculateContrast(button.currentTextColor,bg)
                        println("TERRAIN_CONTRAST $name ${button.text}: $ratio")
                        assertTrue("${button.text}: contrast=$ratio",ratio>=4.5)
                        assertTrue("Button overlapped camera",r.top>=SystemBars.rawTop)
                    }
                };b.recycle()
            }
            contrast("terrain-readable")
            s.onActivity { controls.forEach { it.isPressed=true } };SystemClock.sleep(300)
            contrast("terrain-pressed")
            s.onActivity { controls.forEach { it.isPressed=false } }
            click("地形：平野")
            assertTrue(device.wait(Until.hasObject(By.text("土地属性")),5000));SystemClock.sleep(250)
            screenshot("terrain-attributes").recycle();click("森林")
            click("1マス")
            assertTrue(device.wait(Until.hasObject(By.text("筆の大きさ")),5000));SystemClock.sleep(250)
            screenshot("terrain-brush").recycle();click("4×4")
            s.onActivity { controls[2].performClick() }
            assertTrue(device.wait(Until.hasObject(By.text("球面の地点へ移動")),5000));device.waitForIdle();screenshot("terrain-coordinate").recycle()
            click("取消");click("完了")
        }
    }
}
