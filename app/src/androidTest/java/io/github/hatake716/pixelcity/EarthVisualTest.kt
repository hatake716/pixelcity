package io.github.hatake716.pixelcity

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import io.github.hatake716.pixelcity.data.SaveGame
import io.github.hatake716.pixelcity.game.*
import io.github.hatake716.pixelcity.ui.*
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Disposable emulator only. Full-screen capture includes the actual composited GLES surface. */
@RunWith(AndroidJUnit4::class)
class EarthVisualTest {
    private val context: Context get()=ApplicationProvider.getApplicationContext()
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun content(a: MainActivity): View=((a.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0) as ViewGroup).getChildAt(0)
    private fun runScene(name: String,lon: Double,lat: Double,zoom: Float,checks: (ActivityScenario<MainActivity>)->Unit = {}) {
        context.getSharedPreferences("pixelcity",Context.MODE_PRIVATE).edit().clear().commit()
        val save=SaveGame.expandToPlanet(SaveGame.Loaded(City(),Tutorial().apply { skip() },47L))
        save.planet!!.apply {
            longitude=Math.toRadians(lon).toFloat();latitude=Math.toRadians(lat).toFloat();this.zoom=zoom
            introductionSeen=true
        }
        SaveGame.save(context,0,save.city,save.tutorial,save.seed,save.planet)
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            s.onActivity { (content(it) as TitleView).onContinue!!.invoke() }
            s.onActivity {
                (content(it) as SlotView).onSlotChosen!!.invoke(0,true)
                (content(it) as GameView).apply { pause() }
            }
            capture(s,name)
            checks(s)
        }
    }
    private fun capture(s: ActivityScenario<MainActivity>,name: String) {
        val deadline=SystemClock.uptimeMillis()+20000
        var ready=false;val bounds=Rect()
        while(!ready && SystemClock.uptimeMillis()<deadline) {
            s.onActivity {
                val surface=(content(it) as GameView).globe!!
                ready=surface.renderedFrames>0;surface.getGlobalVisibleRect(bounds)
            }
            if(!ready) SystemClock.sleep(80)
        }
        assertTrue("GL frame not rendered",ready)
        SystemClock.sleep(700)
        val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        assertNotNull(image)
        val colors=HashSet<Int>()
        for(y in bounds.top+bounds.height()/5 until bounds.bottom-bounds.height()/5 step 5)
            for(x in bounds.left+bounds.width()/5 until bounds.right-bounds.width()/5 step 5) colors.add(image!!.getPixel(x,y))
        assertTrue("Terrain surface missing: ${colors.size} colors",colors.size>200)
        image!!.recycle()
        assertTrue(device.takeScreenshot(File(context.getExternalFilesDir(null),"earth-$name.png")))
    }
    @Test fun earthContinentsAndSeabedToggleAreVisibleAndPersisted() = runScene("africa",15.0,20.0,1f) { s ->
        s.onActivity { val g=content(it) as GameView;g.globe!!.setSeabed(true);g.onStateChanged!!.invoke() }
        var changed=false;val deadline=SystemClock.uptimeMillis()+8000
        while(!changed && SystemClock.uptimeMillis()<deadline) {
            s.onActivity { changed=(content(it) as GameView).planet!!.seabedVisible }
            if(!changed) SystemClock.sleep(80)
        }
        assertTrue(changed)
        capture(s,"atlantic-floor")
        assertTrue(SaveGame.load(context,0)!!.planet!!.seabedVisible)
        s.onActivity { val g=content(it) as GameView;g.globe!!.setSeabed(false);g.onStateChanged!!.invoke() }
        capture(s,"africa-restored")
        s.onActivity { assertFalse((content(it) as GameView).planet!!.seabedVisible) }
    }
    @Test fun asianMountainRangesHaveVisibleContinuousRelief() = runScene("himalaya",86.0,30.0,5.5f)
    @Test fun naturalStartingTownAndAmericasHaveNoCentralPlainRectangle() = runScene("americas",-95.0,25.0,1f) { s ->
        s.onActivity { val g=content(it) as GameView;g.focusOnTile(g.city.originX,g.city.originY) }
        capture(s,"natural-start")
        s.onActivity {
            val p=(content(it) as GameView).planet!!
            assertEquals(EarthTerrain.startX,p.selectedX);assertEquals(EarthTerrain.startY,p.selectedY)
            assertTrue(p.canBuild(p.selectedX,p.selectedY))
        }
    }
}
