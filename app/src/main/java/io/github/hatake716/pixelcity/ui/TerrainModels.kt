package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.PlanetTerrain
import kotlin.math.*

/** Authored 3D terrain prefabs shared by the globe and the quarter-view city. Local Z is up. */
object TerrainModels {
    data class Point(val x: Float,val y: Float,val z: Float)
    data class Face(val a: Point,val b: Point,val c: Point,val color: Int)
    val models: Map<PlanetTerrain,List<Face>> by lazy { PlanetTerrain.entries.associateWith { build(it) } }
    private fun build(terrain: PlanetTerrain): List<Face> {
        val faces=ArrayList<Face>()
        fun tri(a: Point,b: Point,c: Point,color: Int) { faces.add(Face(a,b,c,color)) }
        fun quad(a: Point,b: Point,c: Point,d: Point,color: Int) { tri(a,b,c,color);tri(a,c,d,color) }
        val green=0xffa4c87c.toInt(); val snow=0xffe5efeb.toInt()
        val base=when(terrain) { PlanetTerrain.RIVER,PlanetTerrain.MOUNTAIN,PlanetTerrain.RANGE->green;else->terrain.color }
        quad(Point(0f,0f,0f),Point(1f,0f,0f),Point(1f,1f,0f),Point(0f,1f,0f),base)
        fun cone(x: Float,y: Float,z: Float,r: Float,h: Float,color: Int,sides: Int=8,top: Float=0f) {
            fun p(i: Int,radius: Float,height: Float): Point {
                val a=i*2f*PI.toFloat()/sides
                return Point(x+cos(a)*radius,y+sin(a)*radius,z+height)
            }
            for(i in 0 until sides) {
                val a=p(i,r,0f); val b=p(i+1,r,0f); val c=p(i+1,top,h); val d=p(i,top,h)
                if(top==0f) tri(a,b,Point(x,y,z+h),color) else {
                    quad(a,b,c,d,color);tri(d,c,Point(x,y,z+h),color)
                }
            }
        }
        fun hill(cx: Float,cy: Float,r: Float,h: Float,color: Int) {
            val sides=8; val rings=3
            fun p(i: Int,j: Int): Point {
                val a=i*2f*PI.toFloat()/sides; val t=j.toFloat()/rings*PI.toFloat()/2
                return Point(cx+cos(a)*r*cos(t),cy+sin(a)*r*cos(t),sin(t)*h)
            }
            for(j in 0 until rings) for(i in 0 until sides) quad(p(i,j),p(i+1,j),p(i+1,j+1),p(i,j+1),color)
        }
        fun peak(x: Float,y: Float,r: Float,h: Float) {
            cone(x,y,0f,r,h,0xffa39c87.toInt())
            cone(x,y,h*0.67f,r*0.34f,h*0.34f,snow)
        }
        when(terrain) {
            PlanetTerrain.PLAIN -> Unit
            PlanetTerrain.SEA -> for(j in 0..2) {
                val y=0.18f+j*0.30f
                for(i in 0..4) {
                    val x=i/5f; val z=0.02f+sin((i+j)*1.7f)*0.012f
                    quad(Point(x,y,z),Point(x+0.2f,y+0.035f,z+0.015f),Point(x+0.2f,y+0.075f,z),Point(x,y+0.04f,z),0xff81bdc9.toInt())
                }
            }
            PlanetTerrain.RIVER -> for(i in 0..7) {
                val y=i/8f; val next=(i+1)/8f
                val a=0.5f+0.16f*sin(y*6.28f); val b=0.5f+0.16f*sin(next*6.28f)
                quad(Point(a-0.17f,y,0.008f),Point(a+0.17f,y,0.008f),Point(b+0.17f,next,0.008f),Point(b-0.17f,next,0.008f),0xff69b3c3.toInt())
            }
            PlanetTerrain.MOUNTAIN -> peak(0.5f,0.5f,0.47f,0.93f)
            PlanetTerrain.RANGE -> { peak(0.24f,0.30f,0.25f,0.59f);peak(0.52f,0.49f,0.32f,0.93f);peak(0.76f,0.76f,0.25f,0.67f) }
            PlanetTerrain.VOLCANO -> {
                // Open truncated cone with a recessed orange crater, not a flat orange cap.
                for(i in 0..11) {
                    fun ring(k: Int,r: Float,z: Float)=Point(0.5f+cos(k*PI.toFloat()/6)*r,0.5f+sin(k*PI.toFloat()/6)*r,z)
                    quad(ring(i,0.46f,0f),ring(i+1,0.46f,0f),ring(i+1,0.16f,0.70f),ring(i,0.16f,0.70f),0xff997b70.toInt())
                    quad(ring(i,0.16f,0.70f),ring(i+1,0.16f,0.70f),ring(i+1,0.08f,0.52f),ring(i,0.08f,0.52f),0xffc18364.toInt())
                    tri(ring(i,0.08f,0.52f),ring(i+1,0.08f,0.52f),Point(0.5f,0.5f,0.50f),0xffffbb69.toInt())
                }
            }
            PlanetTerrain.FOREST,PlanetTerrain.RAINFOREST -> {
                val places=if(terrain==PlanetTerrain.FOREST) listOf(0.27f to 0.28f,0.7f to 0.60f,0.29f to 0.77f)
                    else listOf(0.25f to 0.24f,0.73f to 0.27f,0.51f to 0.55f,0.25f to 0.77f,0.78f to 0.76f)
                for((i,p) in places.withIndex()) {
                    cone(p.first,p.second,0f,0.035f,0.33f,0xff987451.toInt(),6,0.03f)
                    if(terrain==PlanetTerrain.FOREST) cone(p.first,p.second,0.14f,0.20f,0.49f,if(i%2==0) 0xff63946d.toInt() else 0xff8cac72.toInt())
                    else {
                        cone(p.first,p.second,0.25f,0.21f,0.17f,0xff53a274.toInt(),8,0.14f)
                        cone(p.first,p.second,0.42f,0.15f,0.15f,0xff7db974.toInt())
                    }
                }
            }
            PlanetTerrain.DESERT -> { hill(0.35f,0.30f,0.32f,0.22f,0xffe1be80.toInt());hill(0.68f,0.72f,0.30f,0.31f,0xffd2a66d.toInt()) }
            PlanetTerrain.SNOW -> hill(0.5f,0.5f,0.47f,0.17f,snow)
            PlanetTerrain.GLACIER -> {
                cone(0.29f,0.31f,0f,0.24f,0.42f,0xff9bcad7.toInt(),5,0.10f)
                cone(0.70f,0.65f,0f,0.30f,0.55f,0xffd7eeec.toInt(),5,0.14f)
                cone(0.31f,0.79f,0f,0.18f,0.26f,0xffb9dce5.toInt(),5,0.10f)
            }
        }
        return faces
    }
}
