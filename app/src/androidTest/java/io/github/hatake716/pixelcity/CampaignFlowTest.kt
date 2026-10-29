package io.github.hatake716.pixelcity

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.hatake716.pixelcity.data.SaveGame
import io.github.hatake716.pixelcity.audio.Audio
import io.github.hatake716.pixelcity.audio.Bgm
import io.github.hatake716.pixelcity.game.*
import io.github.hatake716.pixelcity.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.After
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CampaignFlowTest {
    private val context: Context get()=ApplicationProvider.getApplicationContext()
    private fun root(a: MainActivity)=((a.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0) as ViewGroup).getChildAt(0) as GameView
    private fun notebook(v: GameView)=GameView::class.java.getDeclaredMethod("getCampaignPanel").apply { isAccessible=true }.invoke(v) as CampaignPanel
    private fun load(s: ActivityScenario<MainActivity>) {
        s.onActivity {
            MainActivity::class.java.getDeclaredMethod("continueGame",Int::class.javaPrimitiveType).apply { isAccessible=true }.invoke(it,0)
            root(it).setSpeed(0)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
    private fun snapshot(v: GameView): Bitmap = Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }
    private fun tap(v: GameView,x: Int,y: Int) {
        val scale=GameView.layoutFor(v.width,v.height).first
        val now=SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
            val event=MotionEvent.obtain(now,now+10,action,x*scale.toFloat(),y*scale.toFloat(),0)
            v.dispatchTouchEvent(event);event.recycle()
        }
    }
    private fun action(v: GameView,a: CampaignPanel.Action) {
        snapshot(v).recycle()
        val b=notebook(v).buttons.firstOrNull { it.action==a }
        assertNotNull("Missing $a",b);tap(v,b!!.x+b.w/2,b.y+b.h/2)
    }
    private val captures=ArrayList<Pair<String,Bitmap>>()
    private fun screenshot(v: GameView,name: String) {
        captures.add(name to snapshot(v))
    }
    // PNG compression and MediaStore IPC must not block the UI/input thread during tests.
    @After fun writeScreenshots() {
        try {
            if(android.os.Build.VERSION.SDK_INT>=29) for((name,bmp) in captures) {
                val values=ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME,"$name.png")
                    put(MediaStore.Images.Media.MIME_TYPE,"image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/PixelCityScenarios")
                }
                val uri=context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
                context.contentResolver.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG,100,it) }
            }
        } finally { captures.forEach { it.second.recycle() };captures.clear() }
    }
    private fun graduated()=Tutorial().apply { restore(intArrayOf(0,18,0,1,2,-1,1,-1,0)) }
    private fun seed() {
        context.getSharedPreferences("pixelcity",Context.MODE_PRIVATE).edit().clear().commit()
        SaveGame.save(context,0,ShowcaseCity.build(ShowcaseCity.Kind.HARBOUR),graduated(),24)
    }
    @Test fun allThreeRoutesCanBeChosenFromGraduationAndSaveTheirPolicyAndCity() {
        for((i,route) in Campaign.Route.entries.withIndex()) {
            seed()
            ActivityScenario.launch(MainActivity::class.java).use { s ->
                load(s)
                var funds=0;var kinds=emptyList<TileKind>()
                s.onActivity { a ->
                    val v=root(a);assertEquals(GameView.Screen.CAMPAIGN,v.screen)
                    snapshot(v).recycle()
                    assertEquals(3,notebook(v).buttons.count { it.action in listOf(CampaignPanel.Action.METROPOLIS,CampaignPanel.Action.GARDEN,CampaignPanel.Action.INDUSTRIAL) })
                    if(i==0)screenshot(v,"branches")
                    funds=v.city.funds;kinds=v.city.tiles.map { it.kind }
                    action(v,listOf(CampaignPanel.Action.METROPOLIS,CampaignPanel.Action.GARDEN,CampaignPanel.Action.INDUSTRIAL)[i])
                    assertEquals(route,v.city.campaign.route)
                    val audio=GameView::class.java.getDeclaredField("audio").apply { isAccessible=true }.get(v) as Audio
                    val selected=Audio::class.java.getDeclaredField("currentBgm").apply { isAccessible=true }.get(audio)
                    assertEquals("Route music changes before the next simulation month",
                        Bgm.forCity(v.city.population,v.city.funds,v.city.monthsInDebt,route),selected)
                    screenshot(v,"${route.name.lowercase()}-opening")
                    action(v,CampaignPanel.Action.OPTION_1)
                    assertEquals(funds-v.city.campaign.current!!.options[1].cost,v.city.funds)
                    assertEquals(kinds,v.city.tiles.map { it.kind })
                    action(v,CampaignPanel.Action.POLICIES);screenshot(v,"${route.name.lowercase()}-policy")
                    action(v,CampaignPanel.Action.GOALS);assertEquals(1,notebook(v).tab)
                    action(v,CampaignPanel.Action.CLOSE);assertEquals(GameView.Screen.PLAYING,v.screen)
                    action(v,CampaignPanel.Action.OPEN);assertEquals(GameView.Screen.CAMPAIGN,v.screen)
                }
                s.recreate();load(s)
                s.onActivity {
                    val v=root(it);assertEquals(route,v.city.campaign.route);assertEquals(1,v.city.campaign.choiceAt(0))
                    assertEquals(kinds,v.city.tiles.map { tile -> tile.kind })
                    v.pause()
                }
                writeScreenshots()
                assertEquals(route,SaveGame.load(context,0)!!.city.campaign.route)
            }
        }
    }
    @Test fun unaffordablePolicyAllowsReturningToTheCityWithoutSpendingOrAdvancing() {
        seed();val loaded=SaveGame.load(context,0)!!;loaded.city.funds=0
        SaveGame.save(context,0,loaded.city,loaded.tutorial,loaded.seed)
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            load(s)
            s.onActivity {
                val v=root(it);action(v,CampaignPanel.Action.METROPOLIS)
                action(v,CampaignPanel.Action.OPTION_0);assertFalse(v.city.campaign.chosen);assertEquals(0,v.city.funds)
                action(v,CampaignPanel.Action.CLOSE);assertEquals(GameView.Screen.PLAYING,v.screen)
                assertEquals(0,v.city.campaign.chapter)
                v.pause()
            }
        }
    }
    @Test fun notebookReadingAndTextTapsDoNotAdvanceTheClockOrChooseAPolicy() {
        seed()
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            load(s);var month=0
            s.onActivity {
                val v=root(it);action(v,CampaignPanel.Action.GARDEN);month=v.city.month
                tap(v,30,240);assertFalse(v.city.campaign.chosen)
                v.setSpeed(5)
            }
            SystemClock.sleep(1500)
            s.onActivity {
                val v=root(it);assertEquals(month,v.city.month)
                action(v,CampaignPanel.Action.CLOSE);snapshot(v).recycle();assertEquals(month,v.city.month)
                v.pause()
            }
        }
    }
    @Test fun completedRealCitiesShowTheirOwnEndingAndContinueInTheSameSave() {
        for(route in Campaign.Route.entries) {
            context.getSharedPreferences("pixelcity",Context.MODE_PRIVATE).edit().clear().commit()
            val file="scenario/${route.name.lowercase()}-completed.json"
            val raw=InstrumentationRegistry.getInstrumentation().context.assets.open(file).bufferedReader().use { it.readText() }
            val loaded=SaveGame.parse(raw)
            assertTrue(loaded.city.campaign.completed)
            assertTrue(loaded.city.campaign.objectives(loaded.city).all { it.met })
            SaveGame.save(context,0,loaded.city,loaded.tutorial,loaded.seed)
            ActivityScenario.launch(MainActivity::class.java).use { s ->
                load(s)
                s.onActivity {
                    val v=root(it);assertEquals(GameView.Screen.CAMPAIGN,v.screen)
                    screenshot(v,"${route.name.lowercase()}-ending")
                }
                s.onActivity {
                    val v=root(it)
                    action(v,CampaignPanel.Action.GOALS);screenshot(v,"${route.name.lowercase()}-final-goals")
                }
                s.onActivity {
                    val v=root(it)
                    val before=v.city.tiles.map { tile -> tile.kind }
                    action(v,CampaignPanel.Action.CLOSE);v.setSpeed(0)
                    assertEquals(GameView.Screen.PLAYING,v.screen)
                    assertEquals(before,v.city.tiles.map { tile -> tile.kind })
                    screenshot(v,"${route.name.lowercase()}-completed-city")
                    v.pause()
                }
            }
            writeScreenshots()
            val save=SaveGame.load(context,0)!!
            assertTrue(save.city.campaign.completed);assertEquals(route,save.city.campaign.route)
        }
    }
}
