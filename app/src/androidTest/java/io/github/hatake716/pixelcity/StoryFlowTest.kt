package io.github.hatake716.pixelcity

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercise the actual Activity, rendered buttons, selection/commit, and real timed simulation. */
@RunWith(AndroidJUnit4::class)
class StoryFlowTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    @Before fun clearDisposableEmulator() {
        context.getSharedPreferences("pixelcity", Context.MODE_PRIVATE).edit().clear().commit()
    }
    private fun root(activity: MainActivity) =
        ((activity.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0) as ViewGroup).getChildAt(0)
    private fun number(view: GameView, name: String): Number = try {
        GameView::class.java.getDeclaredField(name).apply { isAccessible = true }.get(view) as Number
    } catch (_: NoSuchFieldException) {
        GameView::class.java.getDeclaredMethod("get" + name.replaceFirstChar { it.uppercaseChar() }).apply { isAccessible = true }.invoke(view) as Number
    }
    private fun panel(view: GameView) = GameView::class.java.getDeclaredMethod("getTutorialPanel").apply { isAccessible = true }.invoke(view) as TutorialPanel
    private fun draw(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }
    private fun tap(view: View, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        for ((action, time) in listOf(MotionEvent.ACTION_DOWN to now, MotionEvent.ACTION_UP to now + 15)) {
            val event = MotionEvent.obtain(now, time, action, x, y, 0)
            view.dispatchTouchEvent(event); event.recycle()
        }
    }
    private fun logicalTap(view: GameView, x: Int, y: Int) {
        val scale = number(view,"scale").toInt()
        tap(view, (x * scale + number(view,"offsetX").toInt()).toFloat(), (y * scale + number(view,"offsetY").toInt()).toFloat())
    }
    private fun action(view: GameView, action: TutorialPanel.Action) {
        draw(view).recycle()
        val button = panel(view).buttons.firstOrNull { it.action == action }
        assertNotNull("Missing rendered story action $action at ${view.tutorial.stepIndex}",button)
        logicalTap(view, button!!.x + button.width / 2, button.y + button.height / 2)
    }
    private fun control(view: GameView, name: String) {
        if(!view.constructionMenuVisible) {
            topControl(view,"constructionButton")
        }
        draw(view).recycle()
        val bounds = GameView::class.java.getDeclaredField(name).apply { isAccessible = true }.get(view) as Pair<*,*>
        logicalTap(view, ((bounds.first as Int)+(bounds.second as Int))/2,number(view,"bottomRowTop").toInt()+15)
    }
    private fun topControl(view: GameView,name: String) {
        draw(view).recycle()
        val b=GameView::class.java.getDeclaredField(name).apply { isAccessible=true }.get(view) as android.graphics.Rect
        logicalTap(view,b.centerX(),b.centerY())
    }
    private fun close(view: GameView) {
        draw(view).recycle()
        val y = GameView::class.java.getDeclaredMethod("closeButtonY").apply { isAccessible=true }.invoke(view) as Int
        logicalTap(view,GameView.LOGICAL_W-50,y+12)
    }
    private fun plot(view: GameView, index: Int) {
        if(view.globe!=null) {
            val surface=view.globe!!;val cam=surface.camera
            val p=PlanetGeometry.Landscape(PlanetGeometry.Snapshot.of(view.planet!!)).ground(index%512+.5f,index/512+.5f)-cam.focus*cam.focusRadius
            val aspect=surface.width.toFloat()/surface.height;val hh=cam.halfHeight(aspect)
            val x=surface.left+surface.width*(.5f+p.dot(cam.right)/(2*hh*aspect))
            val y=surface.top+surface.height*(.5f-p.dot(cam.up)/(2*hh))
            tap(view,x,y);return
        }
        val mapTop = number(view,"statusBottom").toInt()
        val mapHeight = number(view,"toolbarTopY").toInt() - mapTop
        val cx = number(view,"camX").toFloat(); val cy = number(view,"camY").toFloat()
        val x = index % view.city.width; val y = index / view.city.width
        val ox = GameView.LOGICAL_W/2 - Math.round(Iso.screenX2(cx,cy) * view.zoom)
        val oy = mapTop+mapHeight/2 - Math.round(Iso.screenY2(cx,cy) * view.zoom)
        logicalTap(view,ox + ((Iso.screenX(x,y)+Iso.TILE_W/2)*view.zoom).toInt(),oy + ((Iso.screenY(x,y)+Iso.TILE_H/2)*view.zoom).toInt())
    }
    private fun execute(view: GameView) {
        draw(view).recycle()
        val bounds = GameView::class.java.getDeclaredField("runButtonX").apply { isAccessible=true }.get(view) as Pair<*,*>
        logicalTap(view,((bounds.first as Int)+(bounds.second as Int))/2,number(view,"selectionButtonY").toInt()+20)
    }
    private fun start(scenario: ActivityScenario<MainActivity>) {
        awaitRendered(scenario,::root)
        scenario.onActivity { activity ->
            val title = root(activity) as TitleView
            draw(title).recycle()
            val button=title.findViewWithTag<View>("startTutorial")
            assertTrue(button.height >= 48*title.resources.displayMetrics.density)
            assertTrue(button.performClick())
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        scenario.onActivity { assertTrue(root(it) is GameView) }
        awaitGlobe(scenario,::root)
    }
    private fun screenshot(view: GameView, name: String) {
        val bitmap = draw(view)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"$name.png")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/PixelCityStory")
            }
            val uri = requireNotNull(context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values))
            context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        } else {
            File(context.getExternalFilesDir(null),"story-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        bitmap.recycle()
    }

    @Test fun fromTheStartButtonToTheFinalLetterUsingRenderedControls() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            start(scenario)
            var done=false;var guard=0
            while(!done && guard++ < 65) {
                var waiting=-1
                scenario.onActivity { activity ->
                    val view=root(activity) as GameView
                    val t=view.tutorial
                    if(!t.active) { done=true;return@onActivity }
                    val id=t.step!!.id
                    if(id==0 || id==11 || id==17) screenshot(view,when(id){0->"opening";11->"choice";else->"ending"})
                    if(t.awaitingChoice()) { action(view,TutorialPanel.Action.CLINIC);return@onActivity }
                    if(!t.briefingRead) action(view,TutorialPanel.Action.REPLY)
                    if(!t.active || t.step?.id != id)return@onActivity
                    when(val goal=t.step!!.goal) {
                        is Tutorial.Goal.Place,Tutorial.Goal.KeepPromise -> {
                            if(!view.constructionMenuVisible) { topControl(view,"constructionButton");return@onActivity }
                            val suggestions=TutorialDistrict.suggestions(view.city,t)
                            val count=t.remaining()
                            for(index in suggestions.take(count)) { draw(view).recycle();plot(view,index) }
                            assertEquals("candidate selection at $id",count,view.selectedCount)
                            execute(view)
                            assertNotEquals("commit did not finish assignment $id",id,t.step?.id)
                            assertEquals(0,view.selectedCount)
                        }
                        Tutorial.Goal.InspectHome -> {
                            topControl(view,"inspectionButton")
                            val home=view.city.tiles.indexOfLast { it.kind==TileKind.ZONE_R && it.stage>0 }
                            plot(view,home)
                            assertEquals(GameView.Screen.MESSAGE,view.screen)
                            close(view);assertEquals(7,t.step?.id)
                        }
                        Tutorial.Goal.OpenInfo -> {
                            control(view,"infoButtonX")
                            assertEquals(GameView.Screen.INFO,view.screen)
                            draw(view).recycle()
                            // The information screen has its own close button at the bottom.
                            val info=GameView::class.java.getDeclaredField("info").apply { isAccessible=true }.get(view) as InfoPanel
                            val logicalHeight=number(view,"logicalH").toInt()
                            logicalTap(view,GameView.LOGICAL_W-50,info.closeButtonY(logicalHeight)+12)
                        }
                        Tutorial.Goal.OpenBudget -> {
                            control(view,"budgetButtonX");assertEquals(GameView.Screen.BUDGET,view.screen)
                            draw(view).recycle()
                            val before=view.city.funds;val month=view.city.month;val income=view.city.lastIncome
                            val taxY=number(view,"taxButtonY").toInt()+10
                            logicalTap(view,422,taxY);logicalTap(view,422,taxY)
                            assertEquals(9,view.city.taxRate)
                            assertTrue("forecast stayed stale",view.city.lastIncome>income)
                            assertEquals(before,view.city.funds);assertEquals(month,view.city.month)
                            screenshot(view,"budget");close(view)
                        }
                        is Tutorial.Goal.Population,Tutorial.Goal.GrowingStreet,is Tutorial.Goal.StableBudget -> {
                            view.setSpeed(5);waiting=id
                        }
                        else -> fail("Unhandled $goal")
                    }
                }
                if(waiting>=0) {
                    var changed=false
                    val deadline=SystemClock.uptimeMillis()+12_000
                    while(!changed && SystemClock.uptimeMillis()<deadline) {
                        SystemClock.sleep(100)
                        scenario.onActivity { changed=(root(it) as GameView).tutorial.step?.id!=waiting }
                    }
                    assertTrue("timed milestone $waiting never completed",changed)
                }
            }
            assertTrue("story never ended",done)
            scenario.onActivity {
                val view=root(it) as GameView
                assertTrue(view.tutorial.finished);assertTrue(view.city.population>=120)
                assertTrue(view.city.lastIncome>view.city.lastUpkeep)
                assertEquals(Tutorial.Priority.CLINIC,view.tutorial.priority)
                assertEquals(GameView.Screen.CAMPAIGN,view.screen)
                assertTrue(view.city.campaign.offered)
                assertNull(view.city.campaign.route)
                view.pause()
            }
            val save=SaveGame.load(context,0)!!
            assertTrue(save.tutorial.finished);assertEquals(9,save.city.taxRate)
        }
    }

    @Test fun readingTheNotebookDoesNotBuildOrAdvanceAndSurvivesActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            start(scenario)
            scenario.onActivity {
                val view=root(it) as GameView;draw(view).recycle()
                val button=panel(view).buttons.single()
                logicalTap(view,25,button.y-30)
                assertEquals(0,view.tutorial.stepIndex);assertEquals(0,view.selectedCount)
                assertEquals(City.STARTING_FUNDS,view.city.funds)
                action(view,TutorialPanel.Action.REPLY)
                action(view,TutorialPanel.Action.REPLY)
                assertFalse(view.constructionMenuVisible)
                topControl(view,"constructionButton")
            }
            awaitRendered(scenario,::root)
            scenario.onActivity {
                val view=root(it) as GameView
                val first=TutorialDistrict.suggestions(view.city,view.tutorial).first()
                plot(view,first);execute(view)
                assertEquals(1,view.tutorial.progress)
            }
            scenario.recreate()
            scenario.onActivity { (root(it) as TitleView).onContinue!!.invoke() }
            scenario.onActivity { (root(it) as SlotView).onSlotChosen!!.invoke(0,true) }
            scenario.onActivity {
                val view=root(it) as GameView
                assertEquals(1,view.tutorial.stepIndex);assertEquals(1,view.tutorial.progress)
                assertTrue(view.tutorial.briefingRead)
                assertEquals(City.STARTING_FUNDS-BuildCost.cost(TileKind.ROAD),view.city.funds)
            }
        }
    }
    @Test fun readingABudgetNeverFastForwardsTheCityWhenItCloses() {
        SaveGame.save(context,0,ShowcaseCity.build(ShowcaseCity.Kind.HARBOUR),Tutorial().apply { skip() },23)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { (root(it) as TitleView).onContinue!!.invoke() }
            scenario.onActivity { (root(it) as SlotView).onSlotChosen!!.invoke(0,true) }
            var month=0;var closeY=0
            scenario.onActivity {
                val view=root(it) as GameView
                view.setSpeed(5)
                control(view,"budgetButtonX")
                draw(view).recycle()
                month=view.city.month
                closeY=GameView::class.java.getDeclaredMethod("closeButtonY").apply { isAccessible=true }.invoke(view) as Int
            }
            SystemClock.sleep(1800)
            scenario.onActivity {
                val view=root(it) as GameView
                assertEquals(month,view.city.month)
                logicalTap(view,GameView.LOGICAL_W-50,closeY+12)
                draw(view).recycle()
                assertTrue("reading advanced several months",view.city.month<=month+1)
                view.pause()
            }
        }
    }

}
