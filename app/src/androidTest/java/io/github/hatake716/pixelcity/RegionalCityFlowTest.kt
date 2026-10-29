package io.github.hatake716.pixelcity

import android.content.Context
import android.media.AudioTrack
import android.os.SystemClock
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import io.github.hatake716.pixelcity.audio.Audio
import io.github.hatake716.pixelcity.data.*
import io.github.hatake716.pixelcity.game.*
import io.github.hatake716.pixelcity.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Disposable emulator only: these tests create and replace test save slots. */
@RunWith(AndroidJUnit4::class)
class RegionalCityFlowTest {
    private val context: Context get()=ApplicationProvider.getApplicationContext()
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun content(a: MainActivity)=((a.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0) as ViewGroup).getChildAt(0)
    private fun clean() { context.getSharedPreferences("pixelcity",Context.MODE_PRIVATE).edit().clear().commit() }
    private fun track(g: GameView)=Audio::class.java.getDeclaredField("bgmTrack").apply { isAccessible=true }.get(g.audio) as AudioTrack?
    private fun await(s: ActivityScenario<MainActivity>,message: String,condition: (GameView)->Boolean) {
        val end=SystemClock.uptimeMillis()+30000
        while(SystemClock.uptimeMillis()<end) {
            var ok=false;s.onActivity { ok=condition(content(it) as GameView) };if(ok) return
            SystemClock.sleep(80)
        };fail(message)
    }
    private fun capture(s: ActivityScenario<MainActivity>,name: String,models: Boolean=false) {
        awaitRendered(s,::content)
        await(s,"Globe was not ready: $name") { g -> g.globe!!.let { v ->
            v.renderedFrames>0 && kotlin.math.abs(v.renderedZoom-g.planet!!.zoom)<.01f &&
                (!models || (v.cityModelsDrawn>5 && !v.cityModelsPending))
        } }
        SystemClock.sleep(200)
        screenshot(name)
    }
    private fun screenshot(name: String) {
        val b=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!
        File(context.getExternalFilesDir(null),"regional-$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };b.recycle()
    }
    private fun continueSlot(): ActivityScenario<MainActivity> = ActivityScenario.launch(MainActivity::class.java).also { s ->
        s.onActivity { (content(it) as TitleView).onContinue!!.invoke() }
        s.onActivity { (content(it) as SlotView).onSlotChosen!!.invoke(0,true);(content(it) as GameView).setSpeed(0) }
    }
    private fun saveSmallCity(tutorial: Boolean=false) {
        val world=SaveGame.expandToPlanet(SaveGame.Loaded(City(),Tutorial().apply { if(!tutorial) skip() },47L))
        world.planet!!.apply { introductionSeen=true;zoom=24f;terraform(world.city.originX,world.city.originY,PlanetTerrain.PLAIN,1) }
        SaveGame.save(context,0,world.city,world.tutorial,world.seed,world.planet)
    }

    @Test fun allNewExamplesStartInTheirRequestedRegionsAndRenderOnTheSamePlanet() {
        for(kind in ShowcaseCity.Kind.entries) {
            clean()
            val plan=RegionalShowcase.build(kind)
            ActivityScenario.launch(MainActivity::class.java).use { s ->
                s.onActivity { (content(it) as TitleView).onShowcase!!.invoke() }
                awaitRendered(s,::content)
                if(kind==ShowcaseCity.Kind.HARBOUR) screenshot("picker")
                s.onActivity { (content(it) as ShowcasePickerView).onChosen!!.invoke(kind) }
                device.wait(Until.findObject(By.text("市政を続ける")),5000)?.click()
                device.wait(Until.findObject(By.text("開始")),5000)!!.click()
                s.onActivity { val g=content(it) as GameView
                    g.setSpeed(0);assertEquals(plan.originX,g.city.originX);assertEquals(plan.originY,g.city.originY)
                    assertSame(g.city,g.planet!!.city);assertEquals(plan.city.population,g.city.population)
                }
                capture(s,"${kind.name.lowercase()}-city",true)
                s.onActivity { (content(it) as GameView).globe!!.setZoom(6f) }
                capture(s,"${kind.name.lowercase()}-coast",true)
                s.onActivity { (content(it) as GameView).globe!!.setZoom(1f) }
                capture(s,"${kind.name.lowercase()}-planet")
            }
            val saved=SaveGame.load(context,0)!!
            assertEquals(plan.originX,saved.city.originX);assertEquals(plan.originY,saved.city.originY)
        }
    }

    @Test fun anExistingExampleKeepsItsNorthAmericanLocationAndPlayerChanges() {
        clean()
        val old=SaveGame.expandToPlanet(SaveGame.Loaded(ShowcaseCity.build(ShowcaseCity.Kind.HARBOUR),Tutorial().apply { skip() },ShowcaseCity.Kind.HARBOUR.seed))
        old.planet!!.apply { introductionSeen=true;zoom=24f;terraform(401,202,PlanetTerrain.VOLCANO,1) }
        old.city.funds=123456
        val original=old.city.tiles.map { Triple(it.kind,it.stage,it.biome) }
        SaveGame.save(context,0,old.city,old.tutorial,old.seed,old.planet)
        continueSlot().use { s ->
            s.onActivity { val g=content(it) as GameView
                assertEquals(EarthTerrain.startX,g.city.originX);assertEquals(EarthTerrain.startY,g.city.originY)
                assertEquals(123456,g.city.funds);assertEquals(original,g.city.tiles.map { Triple(it.kind,it.stage,it.biome) })
            }
        }
        val restored=SaveGame.load(context,0)!!
        assertEquals(123456,restored.city.funds);assertEquals(original,restored.city.tiles.map { Triple(it.kind,it.stage,it.biome) })
    }

    @Test fun terrainEditingKeepsMusicPlayingThroughEditsMenuAndBackgroundResume() {
        clean();saveSmallCity()
        continueSlot().use { s ->
            await(s,"Music never started") { track(it)?.playbackHeadPosition?.let { head -> head>8000 }==true }
            lateinit var original: AudioTrack;var month=0;var funds=0
            s.onActivity { g -> (content(g) as GameView).let { original=track(it)!!;month=it.city.month;funds=it.city.funds;it.openTerrainEditor() } }
            fun advances(label: String) {
                val before=original.playbackHeadPosition
                await(s,"Music stopped $label") { g -> assertSame(original,track(g));original.playbackHeadPosition>before+4000 }
            }
            advances("during terrain editing")
            device.wait(Until.findObject(By.text("地形：平野")),5000)!!.click()
            device.wait(Until.findObject(By.text("海")),5000)!!.click()
            device.wait(Until.findObject(By.text("地形を適用 $0")),5000)!!.click();advances("after applying terrain")
            device.findObject(By.text("変更を戻す")).click();advances("after undo")
            s.moveToState(Lifecycle.State.CREATED)
            SystemClock.sleep(200);val stopped=original.playbackHeadPosition
            SystemClock.sleep(250);assertEquals(stopped,original.playbackHeadPosition)
            assertEquals(AudioTrack.PLAYSTATE_PAUSED,original.playState)
            s.moveToState(Lifecycle.State.RESUMED);advances("after returning to the editor")
            screenshot("terrain-music")
            device.findObject(By.text("完了")).click();advances("after closing the editor")
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.text("市長メニュー")),5000))
            assertFalse(device.hasObject(By.textContains("ゴッド")))
            device.findObject(By.text("地形編集")).click();advances("when opened from the mayor menu")
            device.findObject(By.text("完了")).click()
            s.onActivity { (content(it) as GameView).let { g ->
                assertEquals(month,g.city.month);assertEquals(funds,g.city.funds);assertEquals(0,g.speedIndex)
            } }
        }
    }

    @Test fun mutedMusicStaysMutedAndTutorialTimeStopsWhileEditing() {
        clean();Settings.setBgmEnabled(context,false);saveSmallCity(tutorial=true)
        continueSlot().use { s ->
            s.onActivity { (content(it) as GameView).openTerrainEditor() }
            assertTrue(device.wait(Until.hasObject(By.text("完了")),5000))
            SystemClock.sleep(700)
            s.onActivity { (content(it) as GameView).let { g -> assertFalse(g.audio.bgmEnabled);assertNull(track(g));assertEquals(0,g.speedIndex) } }
            device.findObject(By.text("完了")).click()
        }
    }
}
