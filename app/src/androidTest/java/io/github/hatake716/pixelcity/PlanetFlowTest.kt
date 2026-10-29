package io.github.hatake716.pixelcity

import android.content.Context
import android.os.SystemClock
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Existing terrain contracts through the integrated city's tray; disposable emulator only. */
@RunWith(AndroidJUnit4::class)
class PlanetFlowTest {
    private val context: Context get()=ApplicationProvider.getApplicationContext()
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun content(a: MainActivity)=((a.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0) as ViewGroup).getChildAt(0)
    @Before fun prepare() { context.getSharedPreferences("pixelcity",0).edit().clear().commit();SaveGame.save(context,0,City(),Tutorial().apply { skip() },123L) }
    private fun open(s: ActivityScenario<MainActivity>) {
        s.onActivity { (content(it) as TitleView).onContinue!!.invoke() }
        s.onActivity { (content(it) as SlotView).onSlotChosen!!.invoke(0,true);(content(it) as GameView).pause() }
        awaitRendered(s,::content);awaitGlobe(s,::content)
    }
    private fun click(text: String) { device.wait(Until.findObject(By.text(text)),8000)?.click() ?: fail("Missing $text") }
    private fun editor(s: ActivityScenario<MainActivity>) { s.onActivity { (content(it) as GameView).openTerrainEditor() };assertTrue(device.wait(Until.hasObject(By.text("地形編集・無料")),5000)) }
    private fun coordinate(s: ActivityScenario<MainActivity>,x: Int,y: Int) {
        device.findObject(By.textStartsWith("X ")).click()
        assertTrue(device.wait(Until.hasObject(By.text("球面の地点へ移動")),5000))
        val fields=device.findObjects(By.clazz("android.widget.EditText"));assertEquals(2,fields.size)
        fields[0].text=x.toString();fields[1].text=y.toString();click("移動")
        s.onActivity { val p=(content(it) as GameView).planet!!;assertEquals(x,p.selectedX);assertEquals(y,p.selectedY) }
    }
    private fun terrain(s: ActivityScenario<MainActivity>,t: PlanetTerrain) {
        device.findObject(By.textStartsWith("地形：")).click()
        if(!device.wait(Until.hasObject(By.text(t.label)),1000)) UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView(t.label)
        click(t.label);click("地形を適用 $0")
        s.onActivity { val p=(content(it) as GameView).planet!!;assertEquals(t,p.terrainAt(p.selectedX,p.selectedY)) }
    }
    private fun brush(s: ActivityScenario<MainActivity>,size: Int) {
        var old=1;s.onActivity { old=(content(it) as GameView).planet!!.brushSize }
        click(if(old==1) "1マス" else "$old×$old");click(if(size==1) "1マス" else "$size×$size")
    }
    @Test fun tutorialStartsOnNaturalEarthAndEditingIsAvailableImmediately() {
        SaveGame.clear(context,0)
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            s.onActivity { (content(it) as TitleView).onStartTutorial!!.invoke() };awaitRendered(s,::content);awaitGlobe(s,::content)
            s.onActivity { val g=content(it) as GameView;assertTrue(g.tutorial.active);assertNotNull(g.globe);assertTrue(g.city.canBuildOn(g.city.originX,g.city.originY)) }
            editor(s);click("完了");s.onActivity { assertTrue((content(it) as GameView).tutorial.active) }
        }
    }
    @Test fun oneRemoteTileCanBecomePartOfTheSameCityAndPersist() {
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            open(s);editor(s);val before=SaveGame.load(context,0)!!;coordinate(s,420,64);terrain(s,PlanetTerrain.VOLCANO)
            val saved=SaveGame.load(context,0)!!;assertFalse(saved.city.build(420,64,TileKind.ROAD));assertEquals(before.city.tileAt(421,64).biome,saved.city.tileAt(421,64).biome)
            terrain(s,PlanetTerrain.PLAIN);click("完了")
            s.onActivity { val g=content(it) as GameView;assertEquals(420,g.planet!!.selectedX);assertTrue(g.city.build(420,64,TileKind.ROAD));g.globe!!.updateTown();g.onStateChanged!!.invoke() }
            assertEquals(before.city.funds-BuildCost.cost(TileKind.ROAD),SaveGame.load(context,0)!!.city.funds)
            s.recreate();open(s);s.onActivity { assertEquals(TileKind.ROAD,(content(it) as GameView).city.tileAt(420,64).kind) }
        }
    }
    @Test fun rotationAndAllElevenTerrainChoicesWorkInTheSameView() {
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            open(s);editor(s);val bounds=android.graphics.Rect();var before=0f
            s.onActivity { val g=content(it) as GameView;g.globe!!.getGlobalVisibleRect(bounds);before=g.planet!!.longitude }
            device.swipe(bounds.centerX()-80,bounds.centerY(),bounds.centerX()+130,bounds.centerY(),24)
            s.onActivity { val g=content(it) as GameView;g.globe!!.stopMotion();assertNotEquals(before,g.planet!!.longitude) }
            coordinate(s,450,360);for(t in PlanetTerrain.entries) terrain(s,t)
            click("惑星全体");s.onActivity { assertEquals(1f,(content(it) as GameView).planet!!.zoom,.001f) };click("完了")
        }
    }
    @Test fun storedBuildingsSurviveBackgroundAndReturnWithoutCharge() {
        val city=City().apply { funds=50000;build(64,64,TileKind.POWER_COAL) };SaveGame.save(context,0,city,Tutorial().apply { skip() },21L)
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            open(s);editor(s);coordinate(s,EarthTerrain.startX,EarthTerrain.startY);terrain(s,PlanetTerrain.SEA);s.moveToState(Lifecycle.State.CREATED)
            val saved=SaveGame.load(context,0)!!;assertEquals(TileKind.POWER_COAL,saved.city.tileAt(EarthTerrain.startX,EarthTerrain.startY).storedBuilding!!.kind)
            s.moveToState(Lifecycle.State.RESUMED);terrain(s,PlanetTerrain.PLAIN)
            val restored=SaveGame.load(context,0)!!;assertEquals(saved.city.funds,restored.city.funds);assertEquals(TileKind.POWER_COAL,restored.city.tileAt(EarthTerrain.startX,EarthTerrain.startY).kind);click("完了")
        }
    }
    @Test fun maximumBrushAndUndoKeepOneSharedTerrainAndBudget() {
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            open(s);editor(s);coordinate(s,420,64);val before=SaveGame.load(context,0)!!
            brush(s,32);terrain(s,PlanetTerrain.RAINFOREST);SystemClock.sleep(600)
            s.onActivity { val p=(content(it) as GameView).planet!!;assertEquals(1024,p.brushIndices(420,64,32).count { p.city.tiles[it].biome==PlanetTerrain.RAINFOREST });assertEquals(before.city.funds,p.city.funds);assertEquals(before.city.month,p.city.month) }
            click("変更を戻す")
            s.onActivity { val p=(content(it) as GameView).planet!!;for(i in p.brushIndices(420,64,32)) assertEquals(before.city.tiles[i].biome,p.city.tiles[i].biome) }
            brush(s,4);terrain(s,PlanetTerrain.RAINFOREST);brush(s,1);terrain(s,PlanetTerrain.PLAIN);click("完了")
            s.onActivity { val g=content(it) as GameView;assertTrue(g.city.build(420,64,TileKind.ROAD));assertFalse(g.city.build(421,64,TileKind.ROAD)) }
        }
    }
    @Test fun completedTutorialExplainsTheIntegratedEditorOnlyOnce() {
        SaveGame.save(context,0,City(),Tutorial().apply { restore(intArrayOf(0,Tutorial.STEPS.size,0,1)) },5)
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            open(s);click("市政を続ける")
            assertTrue(device.wait(Until.gone(By.text("ひよりから、惑星の地形編集の御案内")),5000))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertTrue(SaveGame.load(context,0)!!.planet!!.introductionSeen)
            s.recreate();open(s);assertFalse(device.hasObject(By.text("ひよりから、惑星の地形編集の御案内")))
        }
    }
}
