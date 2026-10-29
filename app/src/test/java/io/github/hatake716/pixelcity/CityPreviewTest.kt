package io.github.hatake716.pixelcity

import io.github.hatake716.pixelcity.ui.*
import java.io.File
import org.junit.Test

/** Optional export of the game's actual renderer, without Android UI chrome. */
class CityPreviewTest {
    @Test fun exportNeighbourhoods() {
        val path=System.getProperty("pixelcity.preview")?.takeIf { it.isNotBlank() } ?: return
        val dir=File(path,"cities").apply { mkdirs() }
        data class Scene(val name:String,val kind:ShowcaseCity.Kind,val x:Float,val y:Float,val denominator:Int)
        for(scene in listOf(
            Scene("harbour",ShowcaseCity.Kind.HARBOUR,30f,36f,5),
            Scene("street-detail",ShowcaseCity.Kind.HARBOUR,31f,31f,2),
            Scene("skyline",ShowcaseCity.Kind.METROPOLIS,30f,30f,4),
        )) {
            val city=ShowcaseCity.build(scene.kind)
            val canvas=PixelCanvas(1400,1050)
            val traffic=TrafficAnimation().apply { refill(city,0,0,59,59) }
            CityRenderer().draw(canvas,city,scene.x,scene.y,1,scene.denominator,0,canvas.height,cars=traffic.all)
            val colors=IntArray(canvas.pixels.size) { Palette.of(canvas.pixels[it].toInt()) }
            File(dir,"${scene.name}.png").writeBytes(BuildingPreviewTest().png(canvas.width,canvas.height,colors))
        }
    }
}
