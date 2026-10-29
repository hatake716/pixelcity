package io.github.hatake716.pixelcity

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.max

/** Inspect the actual composited GLES image, including odd/even tiles inside coarse mesh cells. */
@RunWith(AndroidJUnit4::class)
class PlanetTileVisualTest {
    private val context: Context get()=ApplicationProvider.getApplicationContext()
    private fun content(a: MainActivity): View=((a.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0) as ViewGroup).getChildAt(0)
    private fun setup(zoom: Float,prepare: (Planet)->Unit): ActivityScenario<MainActivity> {
        val loaded=SaveGame.expandToPlanet(SaveGame.Loaded(City(),Tutorial().apply { skip() },97L))
        val p=loaded.planet!!
        // Uniform local plain isolates the rendered tile material from nearby tall terrain.
        p.terraform(256,256,PlanetTerrain.PLAIN,32)
        prepare(p);p.select(256,256)
        val center=PlanetGeometry.center(p.selectedIndex)
        p.longitude=center.first;p.latitude=center.second;p.zoom=zoom;p.introductionSeen=true
        context.getSharedPreferences("pixelcity",Context.MODE_PRIVATE).edit().clear().commit()
        SaveGame.save(context,0,loaded.city,loaded.tutorial,loaded.seed,p)
        return ActivityScenario.launch(MainActivity::class.java).also { s ->
            s.onActivity { (content(it) as TitleView).onContinue!!.invoke() }
            s.onActivity { (content(it) as SlotView).onSlotChosen!!.invoke(0,true);(content(it) as GameView).apply { pause() } }
        }
    }
    private fun capture(s: ActivityScenario<MainActivity>,name: String,centerForest: Boolean?=null): Bitmap {
        val deadline=SystemClock.uptimeMillis()+20000
        var mismatch="Current tile revision was not drawn"
        while(SystemClock.uptimeMillis()<deadline) {
            var ready=false;val bounds=Rect()
            s.onActivity {
                val v=content(it) as GameView
                ready=v.globe!!.renderedFrames>0 && v.globe!!.renderedRevision==v.planet!!.revision
                v.globe!!.getGlobalVisibleRect(bounds)
            }
            if(ready) {
                val image=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
                // A submitted GL frame can precede SurfaceFlinger's first visible buffer.
                // Wait for the composited scene and the actual requested tile colors, not a timer.
                val colors=HashSet<Int>()
                for(y in bounds.top+bounds.height()/4 until bounds.bottom-bounds.height()/4 step 12)
                    for(x in bounds.left+bounds.width()/4 until bounds.right-bounds.width()/4 step 12) colors.add(image.getPixel(x,y))
                val error=if(colors.size<24) "GL surface is not visible yet" else centerForest?.let { gridMismatch(s,image,it) }
                if(error==null) {
                    File(context.getExternalFilesDir(null),"tiles-$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
                    return image
                }
                mismatch=error;image.recycle()
            }
            SystemClock.sleep(100)
        }
        throw AssertionError(mismatch)
    }
    private fun gridMismatch(s: ActivityScenario<MainActivity>,image: Bitmap,centerForest: Boolean): String? {
        var mismatch: String?=null
        s.onActivity {
            val v=content(it) as GameView;val p=v.planet!!;val surface=v.globe!!;val bounds=Rect();surface.getGlobalVisibleRect(bounds)
            val cam=surface.camera
            val landscape=PlanetGeometry.Landscape(PlanetGeometry.Snapshot.of(p))
            val aspect=surface.width.toFloat()/surface.height;val hh=cam.halfHeight(aspect)
            for(dy in -1..1) for(dx in -1..1) {
                val point=landscape.point(256.5f+dx,256.5f+dy)-cam.focus*cam.focusRadius
                val x=(bounds.left+surface.width*(0.5f+point.dot(cam.right)/(2*hh*aspect))).toInt()
                val y=(bounds.top+surface.height*(0.5f-point.dot(cam.up)/(2*hh))).toInt()
                val pixel=image.getPixel(x,y);val r=Color.red(pixel);val g=Color.green(pixel)
                val forest=if(dx==0 && dy==0) centerForest else (dx+dy)%2!=0
                val matches=if(forest) g>r*1.2f else r>g*.82f && r>100
                if(!matches) mismatch="Tile ${256+dx},${256+dy} at $x,$y expected ${if(forest) "forest" else "snow"}, RGB=${Integer.toHexString(pixel)}"
            }
        }
        return mismatch
    }
    @Test fun adjacentSavedTilesAndSingleCellUndoAppearOnTheActualGlobe() {
        setup(6.5f) { p -> for(dy in -3..3) for(dx in -3..3)
            p.terraform(256+dx,256+dy,if((dx+dy)%2==0) PlanetTerrain.SNOW else PlanetTerrain.FOREST,1)
        }.use { s ->
            capture(s,"checkerboard",false).recycle()
            s.onActivity { val v=content(it) as GameView;v.planet!!.terraform(256,256,PlanetTerrain.FOREST,1);v.globe!!.refresh(v.planet!!.selectedIndex) }
            capture(s,"one-cell-edited",true).recycle()
            s.onActivity { val v=content(it) as GameView;assertTrue(v.planet!!.undo());v.globe!!.refresh(v.planet!!.selectedIndex) }
            capture(s,"one-cell-restored",false).recycle()
        }
    }
    @Test fun allElevenTypesHaveVisibleModelsInTheMagnifiedPlanet() {
        setup(24f) { p -> for((i,t) in PlanetTerrain.entries.withIndex()) {
            p.terraform(252+i%4*2,252+i/4*2,t,1)
        } }.use { s -> capture(s,"terrain-catalog").recycle() }
    }
}
