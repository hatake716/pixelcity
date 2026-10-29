package io.github.hatake716.pixelcity

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.InputDevice
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import io.github.hatake716.pixelcity.data.SaveGame
import io.github.hatake716.pixelcity.game.*
import io.github.hatake716.pixelcity.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UnifiedCityTest {
    private val context: Context get()=ApplicationProvider.getApplicationContext()
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun content(a: MainActivity)=((a.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0) as ViewGroup).getChildAt(0)
    private fun open(showcase: Boolean=false): ActivityScenario<MainActivity> {
        context.getSharedPreferences("pixelcity",Context.MODE_PRIVATE).edit().clear().commit()
        val city=if(showcase) ShowcaseCity.build(ShowcaseCity.Kind.HARBOUR) else City()
        val loaded=SaveGame.expandToPlanet(SaveGame.Loaded(city,Tutorial().apply { skip() },47L))
        loaded.planet!!.apply {
            introductionSeen=true;zoom=32f
            if(!showcase) terraform(city.originX,city.originY,PlanetTerrain.PLAIN,16)
        }
        SaveGame.save(context,0,loaded.city,loaded.tutorial,loaded.seed,loaded.planet)
        return ActivityScenario.launch(MainActivity::class.java).also { s ->
            s.onActivity { (content(it) as TitleView).onContinue!!.invoke() }
            s.onActivity { (content(it) as SlotView).onSlotChosen!!.invoke(0,true);(content(it) as GameView).pause() }
        }
    }
    private fun capture(s: ActivityScenario<MainActivity>,name: String,models: Boolean=false) {
        awaitRendered(s,::content)
        val deadline=SystemClock.uptimeMillis()+30000
        while(SystemClock.uptimeMillis()<deadline) {
            val bounds=Rect();var ready=false
            s.onActivity {
                val g=content(it) as GameView;val globe=g.globe!!;globe.getGlobalVisibleRect(bounds)
                ready=globe.renderedFrames>0 && kotlin.math.abs(globe.renderedZoom-g.planet!!.zoom)<.01f &&
                    (!models || (globe.cityModelsDrawn>5 && !globe.cityModelsPending))
            }
            if(ready && bounds.width()>0) {
                val image=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!
                val colors=HashSet<Int>()
                for(y in bounds.top+bounds.height()/4 until bounds.bottom-bounds.height()/4 step 9)
                    for(x in bounds.left+bounds.width()/4 until bounds.right-bounds.width()/4 step 9) colors.add(image.getPixel(x,y))
                if(colors.size>80) {
                    File(context.getExternalFilesDir(null),"unified-$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                    image.recycle();return
                };image.recycle()
            }
            SystemClock.sleep(150)
        };fail("Unified globe did not become visible: $name")
    }
    private fun tilePoint(g: GameView,x: Int,y: Int): Pair<Float,Float> {
        val s=g.globe!!;val cam=s.camera
        val p=PlanetGeometry.Landscape(PlanetGeometry.Snapshot.of(g.planet!!)).ground(x+.5f,y+.5f)-cam.focus*cam.focusRadius
        val aspect=s.width.toFloat()/s.height;val hh=cam.halfHeight(aspect)
        return s.left+s.width*(.5f+p.dot(cam.right)/(2*hh*aspect)) to s.top+s.height*(.5f-p.dot(cam.up)/(2*hh))
    }
    private fun tap(g: GameView,x: Float,y: Float) {
        val now=SystemClock.uptimeMillis()
        for(a in intArrayOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
            val e=MotionEvent.obtain(now,now+30*a,a,x,y,0);g.dispatchTouchEvent(e);e.recycle()
        }
    }
    @Test fun feltTownAndPlanetAreOneSurfaceAtEveryZoom() {
        open(true).use { s ->
            capture(s,"town",true)
            lateinit var first: GameView
            s.onActivity { first=content(it) as GameView;first.globe!!.setZoom(6f) }
            SystemClock.sleep(800);capture(s,"curved-town",true)
            s.onActivity { first=content(it) as GameView;first.globe!!.setZoom(1f) }
            capture(s,"planet")
            s.onActivity { assertSame(first,content(it));first.globe!!.setZoom(48f) }
            SystemClock.sleep(800);capture(s,"buildings",true)
            s.onActivity { assertSame(first,content(it));assertSame(first.city,first.planet!!.city) }
        }
    }
    @Test fun projectedTileSelectionBuildsAndBulldozesWithMunicipalFunds() {
        open().use { s ->
            capture(s,"empty")
            var funds=0;var index=0
            s.onActivity {
                val g=content(it) as GameView;val x=g.city.originX;val y=g.city.originY
                index=g.city.index(x,y);funds=g.city.funds;g.selectTool(TileKind.ROAD)
                val p=tilePoint(g,x,y);tap(g,p.first,p.second)
                assertEquals(1,g.selectedCount);assertEquals(funds,g.city.funds)
                g.runSelectionForTest();assertEquals(TileKind.ROAD,g.city.tiles[index].kind)
                assertEquals(funds-BuildCost.cost(TileKind.ROAD),g.city.funds)
            }
            SystemClock.sleep(500);capture(s,"road")
            s.onActivity {
                val g=content(it) as GameView;g.selectTool(TileKind.EMPTY)
                val p=tilePoint(g,index%512,index/512);tap(g,p.first,p.second);g.runSelectionForTest()
                assertEquals(TileKind.EMPTY,g.city.tiles[index].kind)
                assertEquals(funds-BuildCost.cost(TileKind.ROAD)-BuildCost.BULLDOZE,g.city.funds)
            }
            assertEquals(TileKind.EMPTY,SaveGame.load(context,0)!!.city.tiles[index].kind)
        }
    }
    @Test fun terrainTrayEditsTheSameCityAndUndoPreservesFunds() {
        open().use { s ->
            capture(s,"before-edit")
            lateinit var original: GameView;var funds=0
            s.onActivity { original=content(it) as GameView;funds=original.city.funds;original.openTerrainEditor() }
            assertTrue(device.wait(Until.hasObject(By.text("地形：平野")),5000));device.findObject(By.text("地形：平野")).click()
            device.wait(Until.findObject(By.text("海")),5000)!!.click()
            device.wait(Until.findObject(By.text("地形を適用 $0")),5000)!!.click()
            s.onActivity { assertSame(original,content(it));assertEquals(PlanetTerrain.SEA,original.planet!!.let { p -> p.terrainAt(p.selectedX,p.selectedY) });assertEquals(funds,original.city.funds) }
            device.findObject(By.text("変更を戻す")).click()
            s.onActivity { assertEquals(PlanetTerrain.PLAIN,original.planet!!.let { p -> p.terrainAt(p.selectedX,p.selectedY) }) }
            device.findObject(By.text("完了")).click()
            capture(s,"after-edit")
            s.onActivity { assertSame(original,content(it));assertEquals(funds,original.city.funds) }
        }
    }
    @Test fun backgroundResumeRecreatesGlResourcesAndKeepsTheCity() {
        open(true).use { s ->
            capture(s,"before-background",true)
            var funds=0;var population=0
            s.onActivity { val g=content(it) as GameView;funds=g.city.funds;population=g.city.population }
            repeat(3) { s.moveToState(Lifecycle.State.CREATED);s.moveToState(Lifecycle.State.RESUMED);capture(s,"resumed-$it",true) }
            s.onActivity { val g=content(it) as GameView;assertEquals(funds,g.city.funds);assertEquals(population,g.city.population) }
        }
    }
    @Test fun carsAndWalkersShareTheLiveSphericalStreets() {
        open(true).use { s ->
            capture(s,"before-street-life",true)
            s.onActivity { val g=content(it) as GameView;g.globe!!.setZoom(48f);g.setSpeed(1) }
            val deadline=SystemClock.uptimeMillis()+30000;var visible=false
            while(!visible && SystemClock.uptimeMillis()<deadline) {
                SystemClock.sleep(120)
                s.onActivity { val surface=(content(it) as GameView).globe!!;visible=surface.cityVehiclesDrawn>0 && surface.cityPedestriansDrawn>0 }
            }
            assertTrue("Cars and walkers were not both rendered on the city globe",visible)
            capture(s,"street-life",true)
            SystemClock.sleep(1200);capture(s,"street-life-later",true)
            s.onActivity { (content(it) as GameView).pause() }
        }
    }
    @Test fun pinchKeepsTheHeldGroundAndNeverCommitsConstruction() {
        open().use { s ->
            capture(s,"before-pinch")
            s.onActivity {
                val g=content(it) as GameView;val surface=g.globe!!
                val cx=surface.width*.57f;val cy=surface.top+surface.height*.42f
                val held=surface.pick(cx,cy-surface.top)!!;val funds=g.city.funds;val zoom=g.planet!!.zoom
                val now=SystemClock.uptimeMillis()
                fun one(action: Int) { val e=MotionEvent.obtain(now,now,action,cx-70,cy,0).apply { source=InputDevice.SOURCE_TOUCHSCREEN };g.dispatchTouchEvent(e);e.recycle() }
                fun two(action: Int,span: Float,shift: Float) {
                    val props=Array(2) { i -> MotionEvent.PointerProperties().apply { id=i;toolType=MotionEvent.TOOL_TYPE_FINGER } }
                    val coords=Array(2) { i -> MotionEvent.PointerCoords().apply { x=cx+shift+(if(i==0) -1 else 1)*span/2;y=cy;pressure=1f;size=1f } }
                    // SOURCE_UNKNOWN bypasses Android's location transform; use actual touchscreen semantics.
                    val e=MotionEvent.obtain(now,now,action,2,props,coords,0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0)
                    g.dispatchTouchEvent(e);e.recycle()
                }
                one(MotionEvent.ACTION_DOWN)
                two(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),140f,0f)
                for(i in 1..8) two(MotionEvent.ACTION_MOVE,140f+i*8.75f,i*3f)
                two(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),210f,24f)
                one(MotionEvent.ACTION_UP)
                assertEquals(zoom*1.5f,g.planet!!.zoom,.08f);assertEquals(0,g.selectedCount);assertEquals(funds,g.city.funds)
                val after=surface.pick(cx+24f,cy-surface.top)!!
                assertTrue("Pinch anchor changed tiles: $held -> $after",kotlin.math.abs(held%512-after%512)+kotlin.math.abs(held/512-after/512)<=1)
            }
            capture(s,"after-pinch")
        }
    }
}
