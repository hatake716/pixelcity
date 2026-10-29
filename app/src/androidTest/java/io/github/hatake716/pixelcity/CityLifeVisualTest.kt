package io.github.hatake716.pixelcity

import android.content.Context
import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.hatake716.pixelcity.game.*
import io.github.hatake716.pixelcity.ui.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CityLifeVisualTest {
    private val context: Context get()=ApplicationProvider.getApplicationContext()
    private fun save(bitmap: Bitmap,name: String) {
        val dir=File(context.getExternalFilesDir(null),"city-life").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    private fun fixture() = City(14,14).apply {
        for(t in tiles) { t.terrain=Terrain.LAND;t.kind=TileKind.EMPTY }
        for(y in 2..11) for(x in 2..11) tileAt(x,y).apply {
            kind=if(x%3==0 || y%3==0) TileKind.ROAD else when {
                y<5->TileKind.ZONE_I
                x>7->TileKind.ZONE_C
                else->TileKind.ZONE_R
            }
            if(kind.isZone) stage=if((x+y)%3==0) 3 else 2
        }
        tileAt(7,7).kind=TileKind.PARK
        tileAt(8,7).kind=TileKind.PARK
        tileAt(4,10).kind=TileKind.SCHOOL
    }
    @Test fun actualCarBodyPixelsAreAlignedWithAllFourTravelDirections() {
        // Regression for the camera/game axis mismatch: inspect the baked pixels,
        // rather than merely repeating the renderer's enum-to-ID mapping.
        for(d in 0..3) {
            val b=context.assets.open("realistic/CAR_0_$d.webp").use { BitmapFactory.decodeStream(it)!! }
            var n=0.0;var sx=0.0;var sy=0.0;var sxy=0.0
            for(y in 0 until b.height) for(x in 0 until b.width) {
                val c=b.getPixel(x,y)
                if(Color.alpha(c)>200 && Color.red(c)>Color.green(c)*1.8 && Color.red(c)>Color.blue(c)*1.5) {
                    n++;sx+=x;sy+=y;sxy+=x.toDouble()*y
                }
            }
            assertTrue("visible car body $d",n>50)
            val covariance=sxy/n-sx*sy/(n*n)
            assertTrue("body is sideways for direction $d: $covariance",if(d%2==0) covariance>8 else covariance< -8)
            b.recycle()
        }
        val city=City(9,9).apply {
            style=City.Style.RURAL
            for(t in tiles) { t.terrain=Terrain.LAND;t.kind=TileKind.EMPTY }
            for(i in 0..8) { tileAt(i,4).kind=TileKind.ROAD;tileAt(4,i).kind=TileKind.ROAD }
            tileAt(3,3).apply { kind=TileKind.ZONE_R;stage=1 }
            tileAt(5,3).kind=TileKind.PARK
        }
        val renderer=RealisticCityRenderer(context)
        val ink=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(43,65,65);textSize=27f;typeface=Typeface.DEFAULT_BOLD }
        val directions=listOf(Vehicles.Dir.EAST,Vehicles.Dir.SOUTH,Vehicles.Dir.WEST,Vehicles.Dir.NORTH)
        for(frame in 0 until 24) {
            val b=Bitmap.createBitmap(1440,960,Bitmap.Config.ARGB_8888);val c=Canvas(b)
            c.drawColor(Color.rgb(249,239,218))
            for((i,d) in directions.withIndex()) {
                val x=i%2*720f;val y=i/2*480f
                c.save();c.translate(x,y+44)
                renderer.draw(c,city,5f,4f,.82f,720,0,430,
                    listOf(TrafficAnimation.Car(Vehicles.Kind.CAR_RED,4,4,d,.13f+frame*.03f)),time=12f+frame/12f)
                c.restore()
                c.drawText(listOf("東：右下へ ↘","南：左下へ ↙","西：左上へ ↖","北：右上へ ↗")[i],x+18,y+33,ink)
            }
            save(b,"motion-%02d".format(frame));b.recycle()
        }
    }
    @Test fun everyStyleChangesTheSameCityAndRestoringStyleRestoresTheImage() {
        val city=fixture();val funds=city.funds;val population=city.population
        val kinds=city.tiles.map { it.kind to it.stage }
        val renderer=RealisticCityRenderer(context)
        val styles=City.Style.entries.filter { it!=City.Style.CUSTOM }
        val hashes=mutableSetOf<Int>();var original:IntArray?=null
        val contact=Bitmap.createBitmap(2400,1560,Bitmap.Config.ARGB_8888)
        val contactCanvas=Canvas(contact);contactCanvas.drawColor(Color.rgb(249,239,218))
        val main=Bitmap.createBitmap(1800,810,Bitmap.Config.ARGB_8888)
        val mainCanvas=Canvas(main);mainCanvas.drawColor(Color.rgb(249,239,218))
        val ink=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(42,64,62);textSize=31f;typeface=Typeface.DEFAULT_BOLD }
        for((i,s) in styles.withIndex()) {
            city.style=s
            val b=Bitmap.createBitmap(1200,1440,Bitmap.Config.ARGB_8888);val c=Canvas(b)
            renderer.draw(c,city,6.7f,7.7f,.37f,1200,0,1440,time=12f)
            save(b,"style-${s.name.lowercase()}")
            val pixels=IntArray(b.width*b.height);b.getPixels(pixels,0,b.width,0,0,b.width,b.height)
            assertTrue("style must change geometry/colour: $s",hashes.add(pixels.contentHashCode()))
            if(s==City.Style.STANDARD)original=pixels
            val x=i%4*600f;val y=i/4*780f
            contactCanvas.drawBitmap(b,null,RectF(x,y+55,x+600,y+775),null)
            contactCanvas.drawText(s.label,x+20,y+38,ink)
            val j=listOf(City.Style.PRIME,City.Style.RURAL,City.Style.GRITTY).indexOf(s)
            if(j>=0) {
                mainCanvas.drawBitmap(b,null,RectF(j*600f,65f,j*600f+600,785f),null)
                mainCanvas.drawText(s.label+"の街",j*600f+22,43f,ink)
            }
            b.recycle()
        }
        city.style=City.Style.STANDARD
        val restored=Bitmap.createBitmap(1200,1440,Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(restored),city,6.7f,7.7f,.37f,1200,0,1440,time=12f)
        val pixels=IntArray(restored.width*restored.height);restored.getPixels(pixels,0,restored.width,0,0,restored.width,restored.height)
        assertArrayEquals("terrain cache must follow style changes",original,pixels)
        assertEquals(funds,city.funds);assertEquals(population,city.population);assertEquals(kinds,city.tiles.map { it.kind to it.stage })
        save(contact,"all-city-styles");save(main,"city-personalities")
        contact.recycle();main.recycle();restored.recycle()
    }
    @Test fun streetLifeMovesAndIncludesBothAxesAndAllVehicleKinds() {
        val city=fixture();city.style=City.Style.RURAL
        val renderer=RealisticCityRenderer(context)
        val cars=Vehicles.Kind.entries.mapIndexed { i,k ->
            TrafficAnimation.Car(k,3+i,9,Vehicles.Dir.EAST,.25f)
        }+Vehicles.Dir.entries.mapIndexed { i,d -> TrafficAnimation.Car(Vehicles.Kind.CAR_RED,6,5+i,d,.5f) }
        val a=Bitmap.createBitmap(1600,1200,Bitmap.Config.ARGB_8888)
        val b=Bitmap.createBitmap(1600,1200,Bitmap.Config.ARGB_8888)
        renderer.draw(Canvas(a),city,6f,9f,.70f,1600,0,1200,cars,time=15f)
        renderer.draw(Canvas(b),city,6f,9f,.70f,1600,0,1200,cars,time=15.6f)
        val ap=IntArray(1600*1200);val bp=IntArray(ap.size)
        a.getPixels(ap,0,1600,0,0,1600,1200);b.getPixels(bp,0,1600,0,0,1600,1200)
        assertTrue("walking poses should visibly move",ap.indices.count { ap[it]!=bp[it] }>200)
        save(a,"street-life");save(b,"street-life-next");a.recycle();b.recycle()
    }
}
