package io.github.hatake716.pixelcity

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.core.view.DisplayCutoutCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

/** Exercises real window cutouts and rendered pixels, only on a disposable emulator. */
@RunWith(AndroidJUnit4::class)
class CameraSafeAreaTest {
    private val context: Context get()=ApplicationProvider.getApplicationContext()
    private fun root(a: MainActivity)=(a.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0) as ViewGroup
    private fun content(a: MainActivity)=root(a).getChildAt(0)
    private fun scroll(v: View): ScrollView? {
        if(v is ScrollView) return v
        if(v is ViewGroup) for(i in 0 until v.childCount) scroll(v.getChildAt(i))?.let { return it }
        return null
    }
    @Before fun seed() {
        context.getSharedPreferences("pixelcity",Context.MODE_PRIVATE).edit().clear().commit()
        val loaded=SaveGame.expandToPlanet(SaveGame.Loaded(City(),Tutorial().apply { skip() },47L))
        loaded.planet!!.apply { introductionSeen=true;zoom=24f }
        SaveGame.save(context,0,loaded.city,loaded.tutorial,loaded.seed,loaded.planet)
    }
    private fun minimumTop(a: MainActivity): Int {
        val i=ViewCompat.getRootWindowInsets(root(a))!!
        return maxOf(i.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars()).top,i.displayCutout?.safeInsetTop ?: 0)+
            (a.resources.displayMetrics.density*8+.5f).toInt()
    }
    private fun screenshot(name: String) {
        val b=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()!!
        File(context.getExternalFilesDir(null),"camera-safe-$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) };b.recycle()
    }
    private fun assertMenuSafe(s: ActivityScenario<MainActivity>,scrolled: Boolean=false) {
        awaitRendered(s,::content)
        s.onActivity { a ->
            val v=scroll(content(a))!!
            assertTrue("Menu failed to receive current cutout",v.paddingTop>=minimumTop(a))
            if(scrolled) { v.scrollTo(0,v.getChildAt(0).height);assertTrue(v.scrollY>0) }
        }
        awaitRendered(s,::content)
        s.onActivity { a ->
            val v=scroll(content(a))!!
            val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888)
            v.draw(Canvas(b))
            for(y in 0 until v.paddingTop step 2) for(x in 1 until v.width-1 step 3)
                assertEquals("Menu content entered camera margin at $x,$y",0,Color.alpha(b.getPixel(x,y)))
            b.recycle()
        }
    }
    @Test fun titleAndNewlyOpenedMenusKeepTheirTextBelowTheCameraWhileScrolling() {
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            assertMenuSafe(s);screenshot("title")
            s.onActivity { (content(it) as TitleView).onShowcase!!.invoke() }
            assertMenuSafe(s);assertMenuSafe(s,scrolled=true);screenshot("showcase-scrolled")
            s.onActivity { (content(it) as ShowcasePickerView).onBack!!.invoke() }
            assertMenuSafe(s)
            s.onActivity { (content(it) as TitleView).onContinue!!.invoke() }
            assertMenuSafe(s,scrolled=true);screenshot("slots-scrolled")
        }
    }
    private fun openCity(s: ActivityScenario<MainActivity>) {
        s.onActivity { (content(it) as TitleView).onContinue!!.invoke() }
        s.onActivity { (content(it) as SlotView).onSlotChosen!!.invoke(0,true);(content(it) as GameView).pause() }
        awaitRendered(s,::content);awaitGlobe(s,::content)
    }
    private fun assertGameMargin(s: ActivityScenario<MainActivity>) {
        awaitRendered(s,::content)
        s.onActivity { a ->
            val g=content(a) as GameView
            assertTrue(SystemBars.rawTop>=minimumTop(a))
            val b=Bitmap.createBitmap(g.width,g.height,Bitmap.Config.ARGB_8888)
            g.draw(Canvas(b))
            val background=b.getPixel(g.width/2,0)
            for(y in 0 until SystemBars.rawTop step 2) for(x in 1 until g.width-1 step 3)
                assertEquals("${g.screen}: UI entered camera margin at $x,$y",background,b.getPixel(x,y))
            b.recycle()
        }
    }
    @Test fun everyCityPanelReservesTheWholeCameraBand() {
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            openCity(s)
            for(screen in GameView.Screen.entries) {
                s.onActivity { val g=content(it) as GameView
                    GameView::class.java.getDeclaredField("screen").apply { isAccessible=true }.set(g,screen);g.invalidate()
                }
                assertGameMargin(s)
                if(screen in setOf(GameView.Screen.PLAYING,GameView.Screen.INFO,GameView.Screen.CAMPAIGN)) screenshot(screen.name.lowercase())
            }
        }
    }
    @Test fun aDeeperCutoutMovesBothTheHudAndItsTapTargetAfterInitialLayout() {
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            openCity(s)
            var previousTop=0;var expected=0
            s.onActivity { a ->
                val g=content(a) as GameView;previousTop=g.globe!!.top
                val depth=240
                val simulated=WindowInsetsCompat.Builder(ViewCompat.getRootWindowInsets(root(a))!!)
                    .setDisplayCutout(DisplayCutoutCompat(Rect(0,depth,0,0),listOf(Rect(g.width/2-40,0,g.width/2+40,depth)))).build()
                ViewCompat.dispatchApplyWindowInsets(root(a),simulated)
                expected=depth+(a.resources.displayMetrics.density*8+.5f).toInt()
            }
            assertGameMargin(s)
            s.onActivity { a ->
                val g=content(a) as GameView
                assertEquals(expected,SystemBars.rawTop)
                assertTrue("Globe viewport did not follow the HUD",g.globe!!.top>previousTop)
                val scale=GameView.layoutFor(g.width,g.height).first
                val b=GameView::class.java.getDeclaredField("inspectionButton").apply { isAccessible=true }.get(g) as Rect
                val x=b.centerX().toFloat()*scale;val y=b.centerY().toFloat()*scale
                val now=android.os.SystemClock.uptimeMillis()
                for(action in intArrayOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
                    val e=android.view.MotionEvent.obtain(now,now+20*action,action,x,y,0);g.dispatchTouchEvent(e);e.recycle()
                }
                val field=GameView::class.java.getDeclaredField("inspecting").apply { isAccessible=true }
                assertTrue("Shifted inspection button no longer responds",field.getBoolean(g))
            }
        }
    }
}
