package io.github.hatake716.pixelcity

import android.content.Context
import android.content.ComponentCallbacks2
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import io.github.hatake716.pixelcity.data.SaveGame
import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hatake716.pixelcity.game.*
import io.github.hatake716.pixelcity.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RenderMemoryTest {
    private val context: Context get()=ApplicationProvider.getApplicationContext()

    private fun content(a: MainActivity): View=((a.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0) as ViewGroup).getChildAt(0)

    @Test fun backgroundAndMemoryPressureKeepCityStateAndRebuildVisibleImages() {
        val save=SaveGame.expandToPlanet(SaveGame.Loaded(City(),Tutorial().apply { skip() },51L))
        save.planet!!.introductionSeen=true
        context.getSharedPreferences("pixelcity",Context.MODE_PRIVATE).edit().clear().commit()
        SaveGame.save(context,0,save.city,save.tutorial,save.seed,save.planet)
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            s.onActivity { (content(it) as TitleView).onContinue!!.invoke() }
            s.onActivity { (content(it) as SlotView).onSlotChosen!!.invoke(0,true);(content(it) as GameView).pause() }
            awaitRendered(s,::content)
            s.moveToState(Lifecycle.State.CREATED)
            assertEquals(0,RealisticCityRenderer.cachedSpriteBytes())
            s.moveToState(Lifecycle.State.RESUMED)
            awaitRendered(s,::content)
            s.onActivity {
                it.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
                assertEquals(0,RealisticCityRenderer.cachedSpriteBytes())
                val v=content(it) as GameView
                val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888)
                v.draw(Canvas(b));b.recycle()
                assertTrue(RealisticCityRenderer.cachedSpriteBytes()>0)
                assertEquals(City.STARTING_FUNDS,v.city.funds);assertEquals(0,v.city.month)
            }
        }
    }

    @Test fun nativeCheckerboardMatchesPaletteAtOffsetAndClippedEdgesInOneCommand() {
        for((x,y) in listOf(3 to 5,-3 to -2,18 to 19)) {
            val legacy=PixelCanvas(24,24);legacy.clear(Palette.UI_BG)
            legacy.ditherRect(x,y,11,13,Palette.UI_ACCENT)
            val b=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888)
            var commands=0
            val c=object: Canvas(b) {
                override fun drawRect(l: Float,t: Float,r: Float,b: Float,p: Paint) { commands++;super.drawRect(l,t,r,b,p) }
            }
            c.drawColor(Palette.of(Palette.UI_BG))
            PixelCanvas(24,24).apply { nativeCanvas=c;ditherRect(x,y,11,13,Palette.UI_ACCENT) }
            assertEquals(1,commands)
            for(py in 0..23) for(px in 0..23) assertEquals("$px,$py offset $x,$y",Palette.of(legacy.get(px,py)),b.getPixel(px,py))
            b.recycle()
        }
    }

    @Test fun menuImagesAreSampledAndCacheCanBeReleasedWithoutRecyclingRecordedImages() {
        RealisticCityRenderer.trimMemory()
        val renderer=RealisticCityRenderer(context)
        val b=Bitmap.createBitmap(160,160,Bitmap.Config.ARGB_8888);val c=Canvas(b)
        val ids=Monument.entries.map { it.name } + Hud.TOOLS.map { if(it.kind.isZone) "${it.kind.name}_3_0" else it.kind.name }
        for(id in ids) renderer.drawIcon(c,id,RectF(0f,0f,80f,80f))
        val bytes=RealisticCityRenderer.cachedSpriteBytes()
        assertTrue("Menu thumbnails retained $bytes bytes",bytes in 1 until 4*1024*1024)
        assertTrue(RealisticCityRenderer.spriteCacheLimitBytes()<=32*1024*1024)
        RealisticCityRenderer.trimMemory()
        assertEquals(0,RealisticCityRenderer.cachedSpriteBytes())
        renderer.drawIcon(c,Monument.entries.first().name,RectF(0f,0f,160f,160f))
        assertTrue(RealisticCityRenderer.cachedSpriteBytes()>0)
        b.recycle()
    }

    @Test fun cityAfterMemoryTrimRetainsAppearanceAndBuildingPickingAtFractionalZoom() {
        val city=City(8,8).apply {
            for(t in tiles) { t.terrain=Terrain.LAND;t.biome=PlanetTerrain.PLAIN }
            tileAt(4,4).apply { kind=TileKind.ZONE_R;stage=3 }
        }
        val renderer=RealisticCityRenderer(context)
        val b=Bitmap.createBitmap(720,720,Bitmap.Config.ARGB_8888);val c=Canvas(b)
        fun draw(): IntArray {
            renderer.draw(c,city,4f,4f,.23f,720,0,720,time=12f)
            return IntArray(720*720).also { b.getPixels(it,0,720,0,0,720,720) }
        }
        val before=draw()
        assertTrue("Sampled alpha mask must remain pickable",(260..410 step 5).any { y ->
            (350..480 step 5).any { x -> renderer.buildingAtScreen(city,4f,4f,.23f,720,0,720,x,y)==city.index(4,4) }
        })
        RealisticCityRenderer.trimMemory()
        assertArrayEquals(before,draw())
        b.recycle()
    }
}
