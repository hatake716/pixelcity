package io.github.hatake716.pixelcity

import android.content.Context
import android.graphics.*
import android.os.Debug
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hatake716.pixelcity.data.SaveGame
import io.github.hatake716.pixelcity.game.*
import io.github.hatake716.pixelcity.ui.*
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run only on an emulator. Same workload and measurements before and after optimization. */
@RunWith(AndroidJUnit4::class)
class PerformanceProbeTest {
    private val context: Context get()=ApplicationProvider.getApplicationContext()
    private val label get()=InstrumentationRegistry.getArguments().getString("profileLabel") ?: "optimized"
    private fun content(a: MainActivity): View=((a.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0) as ViewGroup).getChildAt(0)
    private class CountCanvas(bitmap: Bitmap): Canvas(bitmap) {
        var rectangles=0
        override fun drawRect(left: Float,top: Float,right: Float,bottom: Float,paint: Paint) {
            rectangles++;super.drawRect(left,top,right,bottom,paint)
        }
    }
    @Test fun lockedToolbarAndSceneSwitchesRemainBounded() {
        val dense=InstrumentationRegistry.getArguments().getString("showcase")=="true"
        val save=SaveGame.expandToPlanet(SaveGame.Loaded(if(dense) ShowcaseCity.build(ShowcaseCity.Kind.HARBOUR) else City(),Tutorial().apply { skip() },41L))
        save.planet!!.introductionSeen=true
        val initialMonth=save.city.month;val initialFunds=save.city.funds
        context.getSharedPreferences("pixelcity",Context.MODE_PRIVATE).edit().clear().commit()
        SaveGame.save(context,0,save.city,save.tutorial,save.seed,save.planet)
        if(!dense) assertTrue(Hud.toolsIn(Hud.Category.TRANSPORT).count { !BuildCost.isUnlocked(it.kind,0) }>=5)
        val result=JSONObject();val pss=ArrayList<Long>()
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            s.onActivity { (content(it) as TitleView).onContinue!!.invoke() }
            s.onActivity {
                (content(it) as SlotView).onSlotChosen!!.invoke(0,true)
                val v=content(it) as GameView;v.pause()
                GameView::class.java.getDeclaredField("category").apply { isAccessible=true }.set(v,Hud.Category.TRANSPORT)
            }
            awaitRendered(s,::content)
            s.onActivity {
                val v=content(it) as GameView
                val bmp=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);val c=CountCanvas(bmp)
                val times=ArrayList<Double>()
                repeat(16) { i ->
                    c.rectangles=0;val start=System.nanoTime();v.draw(c)
                    if(i>=4) times.add((System.nanoTime()-start)/1e6)
                }
                result.put("toolbarRectangles",c.rectangles)
                if(label=="baseline") assertTrue("Baseline must exercise locked-tool drawing",c.rectangles>10000)
                times.sort();result.put("frameMedianMs",times[times.size/2]);result.put("frameMaxMs",times.max())
                File(context.getExternalFilesDir(null),"performance-$label-toolbar.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG,100,it) }
                bmp.recycle()
                if(label!="baseline") assertTrue("Locked tools created ${c.rectangles} draw commands",c.rectangles<500)
            }
            fun zoomAndSettle(zoom: Float) {
                var frame=0L
                s.onActivity { val g=(content(it) as GameView).globe!!;frame=g.renderedFrames;g.setZoom(zoom) }
                val deadline=SystemClock.uptimeMillis()+30000;var ready=false
                while(!ready && SystemClock.uptimeMillis()<deadline) {
                    SystemClock.sleep(100)
                    s.onActivity {
                        val g=(content(it) as GameView).globe!!
                        ready=g.renderedFrames>frame && kotlin.math.abs(g.renderedZoom-zoom)<.01f && !g.cityModelsPending && (!dense || zoom<3f || g.cityModelsDrawn>5)
                    }
                }
                assertTrue("Globe did not finish rendering zoom $zoom",ready)
            }
            zoomAndSettle(if(dense)48f else 24f)
            pss.add(Debug.getPss())
            val switches=InstrumentationRegistry.getArguments().getString("switches")?.toInt() ?: 6
            result.put("sceneRoundTrips",switches)
            repeat(switches) {
                zoomAndSettle(1f)
                pss.add(Debug.getPss())
                zoomAndSettle(if(dense)48f else 24f)
                pss.add(Debug.getPss())
            }
            if(label!="baseline" && switches>=6) {
                assertTrue("Scene resources grew by ${pss.last()-pss[2]} KiB",pss.last()-pss[2]<64L*1024)
            }
            s.onActivity {
                val v=content(it) as GameView
                assertEquals(initialMonth,v.city.month);assertEquals(initialFunds,v.city.funds)
                val before=System.nanoTime();v.city.step();result.put("monthStepMs",(System.nanoTime()-before)/1e6)
            }
        }
        result.put("label",label);result.put("pssSamplesKiB",org.json.JSONArray(pss));result.put("maxPssKiB",pss.max())
        File(context.getExternalFilesDir(null),"performance-$label.json").writeText(result.toString(2))
        println("PERFORMANCE $result")
    }
}
