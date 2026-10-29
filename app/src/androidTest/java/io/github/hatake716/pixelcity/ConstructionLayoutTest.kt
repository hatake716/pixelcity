package io.github.hatake716.pixelcity

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.InputDevice
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
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real globe input, responsive viewports, and the controls drawn at their actual hit targets. */
@RunWith(AndroidJUnit4::class)
class ConstructionLayoutTest {
    private val context: Context get()=ApplicationProvider.getApplicationContext()
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun root(a: MainActivity)=((a.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0) as ViewGroup).getChildAt(0)
    private fun field(g: GameView,name: String): Any = try {
        GameView::class.java.getDeclaredField(name).apply { isAccessible=true }.get(g)!!
    } catch(_:NoSuchFieldException) {
        GameView::class.java.getDeclaredMethod("get"+name.replaceFirstChar { it.uppercaseChar() }).apply { isAccessible=true }.invoke(g)!!
    }
    private fun n(g: GameView,name: String)=(field(g,name) as Number).toInt()
    private fun draw(v: View) { val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b));b.recycle() }
    private fun send(g: GameView,action: Int,x: Float,y: Float,start: Long,time: Long) {
        val e=MotionEvent.obtain(start,time,action,x,y,0).apply { source=InputDevice.SOURCE_TOUCHSCREEN };g.dispatchTouchEvent(e);e.recycle()
    }
    private fun tap(g: GameView,x: Float,y: Float) { val t=SystemClock.uptimeMillis();send(g,0,x,y,t,t);send(g,1,x,y,t,t+30) }
    private fun logical(g: GameView,x: Int,y: Int)=n(g,"offsetX")+x*n(g,"scale").toFloat() to n(g,"offsetY")+y*n(g,"scale").toFloat()
    private fun button(g: GameView,name: String) {
        draw(g);val r=field(g,name) as Rect;assertTrue("Empty control: $name",r.width()>0)
        val p=logical(g,r.centerX(),r.centerY());tap(g,p.first,p.second)
    }
    private fun drag(g: GameView,x: Float,y: Float,dx: Float,dy: Float) {
        val t=SystemClock.uptimeMillis();send(g,0,x,y,t,t)
        for(i in 1..10) send(g,2,x+dx*i/10,y+dy*i/10,t,t+i*20)
        send(g,1,x+dx,y+dy,t,t+240);g.globe!!.stopMotion()
    }
    private fun two(g: GameView) {
        val surface=g.globe!!;val cx=surface.left+surface.width*.55f;val cy=surface.top+surface.height*.58f
        val t=SystemClock.uptimeMillis()
        fun pair(action: Int,span: Float,shift: Float,time: Long) {
            val props=Array(2) { i->MotionEvent.PointerProperties().apply { id=i;toolType=MotionEvent.TOOL_TYPE_FINGER } }
            val coords=Array(2) { i->MotionEvent.PointerCoords().apply { x=cx+shift+(if(i==0)-1 else 1)*span/2;y=cy+shift/2;pressure=1f;size=1f } }
            val e=MotionEvent.obtain(t,time,action,2,props,coords,0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);g.dispatchTouchEvent(e);e.recycle()
        }
        send(g,0,cx-60,cy,t,t)
        pair(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),120f,0f,t+20)
        for(i in 1..8) pair(2,120f+i*6f,i*5f,t+20+i*20)
        pair(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),168f,40f,t+200)
        // The remaining finger must not start selecting after a pinch.
        send(g,2,cx-34,cy+40,t,t+220);send(g,1,cx-34,cy+40,t,t+240);surface.stopMotion()
    }
    private fun point(g: GameView,x: Int,y: Int): Pair<Float,Float> {
        val s=g.globe!!;val cam=s.camera
        val p=PlanetGeometry.Landscape(PlanetGeometry.Snapshot.of(g.planet!!)).ground(x+.5f,y+.5f)-cam.focus*cam.focusRadius
        val aspect=s.width.toFloat()/s.height;val hh=cam.halfHeight(aspect)
        return s.left+s.width*(.5f+p.dot(cam.right)/(2*hh*aspect)) to s.top+s.height*(.5f-p.dot(cam.up)/(2*hh))
    }
    private fun open(story: Boolean=false,world: Boolean=false): ActivityScenario<MainActivity> {
        context.getSharedPreferences("pixelcity",Context.MODE_PRIVATE).edit().clear().commit()
        if(!story) {
            val loaded=if(world) RegionalShowcase.buildWorld() else SaveGame.expandToPlanet(SaveGame.Loaded(City(),Tutorial().apply { skip() },47L))
            if(!world) loaded.planet!!.apply { introductionSeen=true;zoom=24f;terraform(city.originX,city.originY,PlanetTerrain.PLAIN,16) }
            SaveGame.save(context,0,loaded.city,loaded.tutorial,loaded.seed,loaded.planet)
        }
        return ActivityScenario.launch(MainActivity::class.java).also { s ->
            s.onActivity { it.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            awaitRendered(s,::root)
            if(story) s.onActivity { (root(it) as TitleView).onStartTutorial!!.invoke() }
            else {
                s.onActivity { (root(it) as TitleView).onContinue!!.invoke() }
                s.onActivity { (root(it) as SlotView).onSlotChosen!!.invoke(0,true);(root(it) as GameView).setSpeed(0) }
            }
            awaitRendered(s,::root);awaitGlobe(s,::root)
        }
    }
    private fun rotate(s: ActivityScenario<MainActivity>,orientation: Int) {
        s.onActivity { it.requestedOrientation=orientation }
        val wide=orientation!=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val end=SystemClock.uptimeMillis()+10000;var ready=false
        while(!ready && SystemClock.uptimeMillis()<end) {
            s.onActivity { ready=(root(it).width>root(it).height)==wide };SystemClock.sleep(100)
        }
        assertTrue("Orientation did not change",ready);awaitRendered(s,::root);SystemClock.sleep(250)
    }
    private fun capture(s: ActivityScenario<MainActivity>,name: String) {
        awaitRendered(s,::root);awaitGlobe(s,::root);SystemClock.sleep(600)
        val b=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!
        File(context.getExternalFilesDir(null),"construction-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) };b.recycle()
    }
    private fun close(g: GameView) {
        draw(g);val y=GameView::class.java.getDeclaredMethod("closeButtonY").apply { isAccessible=true }.invoke(g) as Int
        val p=logical(g,GameView.LOGICAL_W-80,y+32);tap(g,p.first,p.second)
    }
    private fun bannerHeight(g: GameView)=GameView::class.java.getDeclaredMethod("bannerHeight").apply { isAccessible=true }.invoke(g) as Int

    @Test fun hiyoriCanHideAndReopenWithoutAdvancingStoryAcrossRotation() {
        open(story=true).use { s ->
            lateinit var game: GameView
            var step=0;var funds=0;var month=0;var briefingRead=false
            capture(s,"hiyori-portrait-visible")
            s.onActivity {
                game=root(it) as GameView
                step=game.tutorial.stepIndex;funds=game.city.funds;month=game.city.month;briefingRead=game.tutorial.briefingRead
                assertTrue(game.hiyoriMessagesVisible);assertTrue(bannerHeight(game)>0)
                button(game,"hiyoriButton")
                assertFalse(game.hiyoriMessagesVisible);assertEquals(0,bannerHeight(game));assertEquals(0,game.selectedCount)
                // The area previously covered by dialogue must become usable map, with no invisible reply targets.
                game.focusOnTile(game.city.originX,game.city.originY)
                val start=logical(game,n(game,"mapWidth")/2,n(game,"toolbarTopY")-70)
                val focus=game.globe!!.camera.focus
                drag(game,start.first,start.second,100f,-60f)
                assertTrue("Hidden dialogue still intercepts map drag",(game.globe!!.camera.focus-focus).length()>.0001f)
                assertEquals(step,game.tutorial.stepIndex);assertEquals(briefingRead,game.tutorial.briefingRead)
            }
            capture(s,"hiyori-portrait-hidden")
            rotate(s,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            capture(s,"hiyori-landscape-hidden")
            s.onActivity {
                assertSame(game,root(it));assertFalse(game.hiyoriMessagesVisible)
                button(game,"hiyoriButton");assertTrue(game.hiyoriMessagesVisible);assertTrue(bannerHeight(game)>0)
                val r=field(game,"hiyoriButton") as Rect;val a=logical(game,r.left,r.top);val b=logical(game,r.right,r.bottom)
                assertTrue(a.first>=SystemBars.rawLeft);assertTrue(a.second>=SystemBars.rawTop)
                assertTrue(b.first<=game.width-SystemBars.rawRight)
                assertEquals(step,game.tutorial.stepIndex);assertEquals(briefingRead,game.tutorial.briefingRead)
                assertEquals(month,game.city.month);assertEquals(funds,game.city.funds)
            }
            capture(s,"hiyori-landscape-reopened")
        }
    }

    @Test fun hiddenCampaignKeepsUnreadAdviceAndSelectionUntilMayorOpensIt() {
        open().use { s ->
            lateinit var game: GameView;var funds=0
            s.onActivity {
                game=root(it) as GameView;funds=game.city.funds
                game.city.campaign.offer();game.city.campaign.acknowledge()
                button(game,"hiyoriButton");assertFalse(game.hiyoriMessagesVisible)
                assertTrue(game.city.campaign.select(Campaign.Route.GARDEN,game.city))
                GameView::class.java.getDeclaredMethod("showCampaignIfNeeded").apply { isAccessible=true }.invoke(game)
                assertEquals(GameView.Screen.PLAYING,game.screen);assertTrue(game.city.campaign.needsBriefing)
                game.selectTool(TileKind.ROAD);game.focusOnTile(game.city.originX,game.city.originY)
                draw(game);val p=point(game,game.city.originX,game.city.originY);tap(game,p.first,p.second)
                assertEquals(1,game.selectedCount)
                // The toggle must not itself select a tile or clear the pending build.
                button(game,"hiyoriButton");assertTrue(game.hiyoriMessagesVisible);assertEquals(1,game.selectedCount)
                button(game,"hiyoriButton");assertFalse(game.hiyoriMessagesVisible);assertEquals(1,game.selectedCount)
                assertTrue(game.city.campaign.needsBriefing);assertEquals(funds,game.city.funds)
                assertEquals(TileKind.EMPTY,game.city.tileAt(game.city.originX,game.city.originY).kind)
            }
            capture(s,"navy-selection")
            // Explicitly opening the notebook remains possible while automatic messages are hidden.
            s.onActivity { button(game,"navigationButton") }
            device.wait(Until.findObject(By.text("市政手帳")),5000)!!.click();device.waitForIdle()
            s.onActivity { assertEquals(GameView.Screen.CAMPAIGN,game.screen);assertEquals(funds,game.city.funds) }
        }
    }

    @Test fun hiddenByDefaultAndUpperButtonRestoresTheWholeMap() {
        open().use { s ->
            capture(s,"portrait-map")
            s.onActivity {
                val g=root(it) as GameView;val surface=g.globe!!;val h=surface.height;val funds=g.city.funds
                assertFalse(g.constructionMenuVisible)
                assertTrue(surface.bottom>=g.height-SystemBars.rawBottom-n(g,"scale"))
                val inspect=field(g,"inspectionButton") as Rect;val build=field(g,"constructionButton") as Rect
                assertEquals(inspect.top,build.top);assertTrue(build.left>inspect.right)
                val focus=surface.camera.focus
                drag(g,surface.width*.45f,surface.top+surface.height*.72f,110f,40f)
                assertTrue("Hidden one-finger drag did not move",(surface.camera.focus-focus).length()>.0001f)
                assertEquals(0,g.selectedCount);assertEquals(funds,g.city.funds)
                button(g,"constructionButton");assertTrue(g.constructionMenuVisible)
                assertEquals(h-Hud.TOOLBAR_HEIGHT*n(g,"scale"),surface.height)
            }
            capture(s,"portrait-build")
            s.onActivity {
                val g=root(it) as GameView;button(g,"constructionButton");assertFalse(g.constructionMenuVisible)
                assertTrue(g.globe!!.bottom>=g.height-SystemBars.rawBottom-n(g,"scale"))
            }
        }
        // A new session always starts in map exploration mode, even after building last time.
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            s.onActivity { (root(it) as TitleView).onContinue!!.invoke() }
            s.onActivity { (root(it) as SlotView).onSlotChosen!!.invoke(0,true);assertFalse((root(it) as GameView).constructionMenuVisible) }
        }
    }
    @Test fun pinchAndPanWorkInBothModesWithoutChangingSelectionOrFunds() {
        open().use { s ->
            for(shown in listOf(false,true)) {
                s.onActivity {
                    val g=root(it) as GameView;g.setConstructionMenuVisible(shown);g.globe!!.setZoom(24f)
                    g.focusOnTile(g.city.originX,g.city.originY);draw(g)
                    if(shown) { g.selectTool(TileKind.ROAD);val p=point(g,g.city.originX,g.city.originY);tap(g,p.first,p.second);assertEquals(1,g.selectedCount) }
                    val selected=g.selectedCount;val funds=g.city.funds;val focus=g.globe!!.camera.focus
                    two(g)
                    assertEquals(33.6f,g.planet!!.zoom,.1f)
                    assertTrue((g.globe!!.camera.focus-focus).length()>.0001f)
                    assertEquals("Pinch must roll back its first-finger stroke",selected,g.selectedCount)
                    assertEquals(funds,g.city.funds);assertEquals(TileKind.EMPTY,g.city.tileAt(g.city.originX,g.city.originY).kind)
                }
            }
            capture(s,"pinch")
        }
    }
    @Test fun landscapeSidebarBuildsAndRotationKeepsTheSameCitySurface() {
        open().use { s ->
            lateinit var game: GameView;lateinit var surface: PlanetSurface
            s.onActivity { game=root(it) as GameView;surface=game.globe!! }
            rotate(s,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);capture(s,"landscape-map")
            s.onActivity {
                assertSame(game,root(it));assertSame(surface,game.globe);assertFalse(game.constructionMenuVisible)
                val w=surface.width;val h=surface.height;button(game,"constructionButton")
                assertEquals(h,surface.height);assertTrue(surface.width<w)
                game.selectTool(TileKind.ROAD)
                val p=point(game,game.city.originX,game.city.originY);tap(game,p.first,p.second)
                assertEquals(1,game.selectedCount)
            }
            capture(s,"landscape-build-selection")
            var funds=0
            s.onActivity {
                funds=game.city.funds;val b=field(game,"runButtonX") as Pair<*,*>
                val p=logical(game,((b.first as Int)+(b.second as Int))/2,n(game,"selectionButtonY")+32);tap(game,p.first,p.second)
                assertEquals(TileKind.ROAD,game.city.tileAt(game.city.originX,game.city.originY).kind)
                assertEquals(funds-BuildCost.cost(TileKind.ROAD),game.city.funds)
            }
            rotate(s,ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);capture(s,"reverse-landscape")
            s.onActivity {
                assertSame(game,root(it));assertTrue(game.constructionMenuVisible);assertSame(surface,game.globe)
                for(name in listOf("inspectionButton","constructionButton","categoryButton")) {
                    val r=field(game,name) as Rect;val a=logical(game,r.left,r.top);val b=logical(game,r.right,r.bottom)
                    assertTrue("$name overlaps left cutout",a.first>=SystemBars.rawLeft)
                    assertTrue("$name overlaps right cutout",b.first<=game.width-SystemBars.rawRight)
                    assertTrue("$name overlaps top cutout",a.second>=SystemBars.rawTop)
                }
                button(game,"categoryButton")
            }
            device.wait(Until.findObject(By.text("交通")),5000)!!.click();device.waitForIdle()
            s.onActivity { draw(game);assertEquals(Hud.Category.TRANSPORT,field(game,"category")) }
            rotate(s,ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            s.onActivity { assertSame(game,root(it));assertTrue(game.constructionMenuVisible);assertEquals(funds-BuildCost.cost(TileKind.ROAD),game.city.funds) }
        }
    }
    @Test fun cityManagementAndLongPanelsRemainReachableWithMenuClosedInLandscape() {
        open().use { s ->
            rotate(s,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            for((label,screen) in listOf("予算" to GameView.Screen.BUDGET,"記念建築" to GameView.Screen.MONUMENTS)) {
                s.onActivity { val g=root(it) as GameView;assertFalse(g.constructionMenuVisible);button(g,"navigationButton") }
                device.wait(Until.findObject(By.text(label)),5000)!!.click();device.waitForIdle()
                awaitRendered(s,::root)
                s.onActivity {
                    val g=root(it) as GameView;assertEquals(screen,g.screen)
                    val top=n(g,"panelBodyTop");val bottom=n(g,"panelBodyBottom");val p=logical(g,180,bottom-16)
                    drag(g,p.first,p.second,0f,-(bottom-top-24)*n(g,"scale").toFloat());draw(g)
                    assertTrue("Long landscape panel did not scroll",n(g,"panelScroll")>0)
                }
                capture(s,"landscape-$label")
                s.onActivity { val g=root(it) as GameView;close(g);assertEquals(GameView.Screen.PLAYING,g.screen);assertFalse(g.constructionMenuVisible) }
            }
        }
    }
    @Test fun landscapeTitleAndTutorialOfferReadableStoryAndBuildEntry() {
        context.getSharedPreferences("pixelcity",Context.MODE_PRIVATE).edit().clear().commit()
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            rotate(s,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);capture(s,"landscape-title")
            s.onActivity {
                val v=root(it) as TitleView;val start=v.findViewWithTag<View>("startTutorial")
                val pos=IntArray(2);start.getLocationOnScreen(pos)
                assertTrue(pos[0]>v.width/2-100);assertTrue(start.height>=48*v.resources.displayMetrics.density)
                assertTrue(start.performClick())
            }
            awaitRendered(s,::root);awaitGlobe(s,::root)
            var reply=""
            s.onActivity {
                val g=root(it) as GameView;assertFalse(g.constructionMenuVisible);draw(g)
                reply=g.tutorial.step!!.reply
                val panel=field(g,"tutorialPanel") as TutorialPanel
                val b=panel.buttons.single { it.action==TutorialPanel.Action.DIALOG }
                val p=logical(g,b.x+b.width/2,b.y+b.height/2);tap(g,p.first,p.second)
            }
            device.waitForIdle();assertNotNull(device.findObject(By.text(reply)))
            capture(s,"landscape-tutorial-story")
            device.findObject(By.text(reply)).click();device.waitForIdle()
            s.onActivity { val g=root(it) as GameView;assertFalse(g.constructionMenuVisible);button(g,"constructionButton");assertTrue(g.constructionMenuVisible) }
            capture(s,"landscape-tutorial-build")
        }
    }
    @Test fun developedWorldAndCampaignPoliciesUseTheLandscapeControls() {
        open(world=true).use { s ->
            rotate(s,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);capture(s,"developed-world")
            lateinit var game: GameView
            s.onActivity { game=root(it) as GameView;button(game,"homeButton") }
            device.wait(Until.findObject(By.textContains("イギリス")),5000)!!.click();device.waitForIdle()
            val end=SystemClock.uptimeMillis()+30000;var ready=false
            while(!ready && SystemClock.uptimeMillis()<end) {
                s.onActivity { ready=game.globe!!.cityModelsDrawn>5 && !game.globe!!.cityModelsPending };SystemClock.sleep(100)
            }
            assertTrue("Town models not visible after destination jump",ready)
            capture(s,"harbour-map")
            s.onActivity { button(game,"constructionButton");assertTrue(game.constructionMenuVisible) }
            capture(s,"harbour-build")
            s.onActivity { game.setConstructionMenuVisible(false);game.city.campaign.offer();game.city.campaign.acknowledge();button(game,"navigationButton") }
            device.wait(Until.findObject(By.text("市政手帳")),5000)!!.click();device.waitForIdle()
            capture(s,"landscape-campaign-routes")
            fun choose(action: CampaignPanel.Action) {
                s.onActivity {
                    draw(game);val panel=field(game,"campaignPanel") as CampaignPanel
                    val b=panel.buttons.single { it.action==action };val p=logical(game,b.x+b.w/2,b.y+b.h/2);tap(game,p.first,p.second)
                }
                device.waitForIdle()
            }
            choose(CampaignPanel.Action.INDUSTRIAL)
            s.onActivity { assertEquals(Campaign.Route.INDUSTRIAL,game.city.campaign.route) }
            capture(s,"landscape-campaign-policy")
            var funds=0;var cost=0
            s.onActivity { funds=game.city.funds;cost=game.city.campaign.current!!.options[0].cost }
            choose(CampaignPanel.Action.OPTION_0)
            capture(s,"landscape-campaign-confirm")
            s.onActivity { assertFalse(game.city.campaign.chosen);assertEquals(funds,game.city.funds) }
            device.wait(Until.findObject(By.text("戻る")),5000)!!.click();device.waitForIdle()
            s.onActivity { assertFalse(game.city.campaign.chosen);assertEquals(funds,game.city.funds) }
            choose(CampaignPanel.Action.OPTION_0)
            device.wait(Until.findObject(By.text("この施策を実施")),5000)!!.click();device.waitForIdle()
            s.onActivity { assertTrue(game.city.campaign.chosen);assertEquals(funds-cost,game.city.funds) }
            choose(CampaignPanel.Action.CLOSE)
            s.onActivity { assertEquals(GameView.Screen.PLAYING,game.screen);assertFalse(game.constructionMenuVisible) }
        }
    }
}
