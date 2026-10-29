package io.github.hatake716.pixelcity

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VisualRenewalTest {
    private val context: Context get()=ApplicationProvider.getApplicationContext()
    private fun root(a: MainActivity)=((a.findViewById<ViewGroup>(android.R.id.content)).getChildAt(0) as ViewGroup).getChildAt(0)
    private fun image(v: View,name: String): Bitmap {
        val b=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888);v.draw(Canvas(b))
        val dir=File(context.getExternalFilesDir(null),"visual-renewal").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }
        publish(b,name)
        return b
    }
    private fun publish(b: Bitmap,name: String) {
        val values=android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"$name.png")
            put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,"Pictures/PixelCityRealistic")
        }
        val uri=context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)!!
        context.contentResolver.openOutputStream(uri)!!.use { b.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun packagedModelsHaveRealAlphaAndCoverEveryBuildingAndVehicle() {
        val a=JSONArray(context.assets.open("realistic/manifest.json").bufferedReader().readText())
        assertEquals(322,a.length())
        val ids=(0 until a.length()).map { a.getJSONObject(it).getString("id") }.toSet()
        assertEquals(a.length(),ids.size)
        for(k in TileKind.entries.filter { it.isBuilding && it!=TileKind.MONUMENT && !it.isZone }) assertTrue(k.name,ids.contains(k.name))
        for(m in Monument.entries) assertTrue(ids.contains(m.name))
        for(k in listOf("ZONE_R","ZONE_C","ZONE_I")) for(s in 1..3) for(v in 0..5) assertTrue(ids.contains("${k}_${s}_$v"))
        for(style in City.Style.entries.filter { it!=City.Style.STANDARD && it!=City.Style.CUSTOM }) {
            for(k in listOf("ZONE_R","ZONE_C","ZONE_I")) for(s in 1..3) for(v in 0..1) assertTrue(ids.contains("${style.name}_${k}_${s}_$v"))
            for(k in listOf("PARK","GROVE","FARM","WATER_TOWER","SCHOOL")) assertTrue(ids.contains("${style.name}_$k"))
        }
        for(k in 0..5) for(d in 0..3) for(step in 0..1) assertTrue(ids.contains("PERSON_${k}_${d}_$step"))
        for(k in Vehicles.Kind.entries.indices) for(d in 0..3) assertTrue(ids.contains("CAR_${k}_$d"))
        for(i in 0 until a.length()) {
            val m=a.getJSONObject(i);val id=m.getString("id")
            assertEquals("All city assets must use the same felt finish: $id", "felt", m.getString("finish"))
            val b=context.assets.open("realistic/$id.webp").use { BitmapFactory.decodeStream(it)!! }
            assertEquals(m.getInt("width"),b.width);assertEquals(m.getInt("height"),b.height)
            assertTrue(id,b.hasAlpha());assertTrue(id,android.graphics.Color.alpha(b.getPixel(0,0))<10)
            b.recycle()
        }
    }
    @Test fun titleIsAnimatedAndAllPrimaryMenuTargetsAreAtLeast48dp() {
        context.getSharedPreferences("pixelcity",Context.MODE_PRIVATE).edit().clear().commit()
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            var first: Bitmap?=null
            sc.onActivity { a -> val v=root(a) as TitleView;first=image(v,"title");checkTargets(v) }
            SystemClock.sleep(600)
            sc.onActivity { a ->
                val next=image(root(a),"title-motion")
                var changed=0
                for(y in 240 until next.height/2 step 8) for(x in 40 until next.width-40 step 8) if(first!!.getPixel(x,y)!=next.getPixel(x,y)) changed++
                assertTrue("animated city did not move",changed>150)
                first!!.recycle();next.recycle()
                root(a).findViewWithTag<View>("startTutorial").performClick()
                (root(a) as GameView).pause()
            }
            awaitRendered(sc,::root)
            sc.onActivity { a -> assertTrue(root(a) is GameView);image(root(a),"tutorial").recycle() }
        }
    }
    private fun checkTargets(v: View) {
        if(v.isClickable) assertTrue("small target ${v.contentDescription}: ${v.height}",v.height>=48*v.resources.displayMetrics.density)
        if(v is ViewGroup) for(i in 0 until v.childCount) checkTargets(v.getChildAt(i))
    }
    @Test fun showcaseAndAllInformationTabsRenderAndScrollToTheirLastSetting() {
        SaveGame.save(context,0,ShowcaseCity.build(ShowcaseCity.Kind.HARBOUR),Tutorial().apply { skip() },23L)
        ActivityScenario.launch(MainActivity::class.java).use { sc ->
            sc.onActivity { (root(it) as TitleView).onContinue!!.invoke() }
            sc.onActivity {
                (root(it) as SlotView).onSlotChosen!!.invoke(0,true)
                (root(it) as GameView).pause()
            }
            awaitRendered(sc,::root)
            sc.onActivity { a ->
                val v=root(a) as GameView;v.setSpeed(0);v.pause();image(v,"harbour").recycle()
                val info=GameView::class.java.getDeclaredField("info").apply { isAccessible=true }.get(v) as InfoPanel
                GameView::class.java.getDeclaredField("screen").apply { isAccessible=true }.set(v,GameView.Screen.INFO)
                for(tab in InfoPanel.Tab.entries) { info.tab=tab;image(v,"info-${tab.name.lowercase()}").recycle() }
                info.scrollBy(10000);image(v,"settings-bottom").recycle()
                val targetY=InfoPanel::class.java.getDeclaredField("settingsDisasterY").apply { isAccessible=true }.getInt(info)-info.scrollOffset
                assertNotNull(info.disasterAt(40,targetY+20))
                // The compact budget and monument picker used to retain phonetic labels.
                for(screen in listOf(GameView.Screen.BUDGET, GameView.Screen.MONUMENTS)) {
                    GameView::class.java.getDeclaredField("screen").apply { isAccessible=true }.set(v,screen)
                    image(v,"panel-${screen.name.lowercase()}").recycle()
                }
                GameView::class.java.getDeclaredField("screen").apply { isAccessible=true }.set(v,GameView.Screen.PLAYING)
                v.cycleZoom();image(v,"harbour-detail").recycle()
            }
        }
    }
    @Test fun cameraProjectionRemainsInvertibleAtNonQuantizedZoom() {
        for(z in listOf(.015625f,.071234f,.124831f,.4998f)) for(tx in listOf(10.125f,16f,20.75f)) {
            val ty=17.312f;val cx=16.4f;val cy=15.2f
            val px=270+(tx-ty-cx+cy)*256*z+256*z
            val py=150+388+(tx+ty-cx-cy)*128*z+128*z
            val actual=RealisticCityRenderer.unproject(px,py,cx,cy,z,540,150,776)
            assertEquals(tx,actual.first,.0001f);assertEquals(ty,actual.second,.0001f)
        }
    }

    @Test fun largeSystemTextKeepsMenusScrollableAndButtonsComfortable() {
        val config=android.content.res.Configuration(context.resources.configuration).apply { fontScale=1.6f }
        val enlarged=context.createConfigurationContext(config)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val title=TitleView(enlarged,false)
            title.measure(View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY))
            title.layout(0,0,1080,2400)
            image(title,"large-text-title").recycle();checkTargets(title)
        }
    }
    @Test fun allEightMonumentsUseTheirOwnThreeDimensionalSilhouettes() {
        val city=City(32,32)
        for(t in city.tiles)t.terrain=Terrain.LAND
        val monuments=Monument.entries
        for((i,m) in monuments.withIndex()) {
            val x=10+(i%4)*3;val y=10+(i/4)*4
            val t=city.tileAt(x,y);t.kind=TileKind.MONUMENT;t.monument=m;t.monumentAnchor=city.index(x,y)
            for(dx in 0..1) for(dy in 0..1) if(dx+dy>0) city.tileAt(x+dx,y+dy).apply { kind=TileKind.MONUMENT;monumentAnchor=city.index(x,y) }
        }
        val renderer=RealisticCityRenderer(context)
        val b=Bitmap.createBitmap(1600,1500,Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(b),city,14f,12f,.34f,1600,0,1500)
        val dir=File(context.getExternalFilesDir(null),"visual-renewal").apply { mkdirs() }
        File(dir,"landmarks.png").outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) };publish(b,"landmarks");b.recycle()
    }
}
