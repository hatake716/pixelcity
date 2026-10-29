package io.github.hatake716.pixelcity.ui

import android.graphics.*
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import io.github.hatake716.pixelcity.game.PlanetTerrain
import kotlin.math.*
import kotlin.random.Random

/** Orthographic bake of the same 3D meshes used on the planet; cached by the city sprite LRU. */
object FeltTerrainSprite {
    const val ANCHOR_X=256f
    const val ANCHOR_Y=384f
    fun bake(terrain: PlanetTerrain, sample: Int = 1): Bitmap {
        val bitmap=createBitmap(512/sample,640/sample,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        fun x(p: TerrainModels.Point)=((p.x-p.y)*256+256)/sample
        fun y(p: TerrainModels.Point)=((p.x+p.y)*128+256-p.z*250)/sample
        val faces=TerrainModels.models.getValue(terrain)
        val colors=IntArray(bitmap.width*bitmap.height)
        val depths=FloatArray(colors.size) { Float.NEGATIVE_INFINITY }
        // A depth buffer keeps high snow caps and tree crowns above the ground and other faces.
        // Sorting whole triangles cannot correctly resolve these overlapping projections.
        fun depth(p: TerrainModels.Point)=p.x+p.y+p.z*(256f/250f)
        for(f in faces) {
            val ux=f.b.x-f.a.x;val uy=f.b.y-f.a.y;val uz=f.b.z-f.a.z
            val vx=f.c.x-f.a.x;val vy=f.c.y-f.a.y;val vz=f.c.z-f.a.z
            var nx=uy*vz-uz*vy;var ny=uz*vx-ux*vz;var nz=ux*vy-uy*vx
            val len=sqrt(nx*nx+ny*ny+nz*nz).coerceAtLeast(0.00001f)
            nx/=len;ny/=len;nz/=len
            val light=(0.72f+max(0f,-nx*0.3f-ny*0.4f+nz*0.86f)*0.28f)
            paint.color=Color.rgb((Color.red(f.color)*light).toInt(),(Color.green(f.color)*light).toInt(),(Color.blue(f.color)*light).toInt())
            val ax=x(f.a);val ay=y(f.a);val bx=x(f.b);val by=y(f.b);val cx=x(f.c);val cy=y(f.c)
            val determinant=(by-cy)*(ax-cx)+(cx-bx)*(ay-cy)
            if(abs(determinant)<0.00001f) continue
            val minX=floor(minOf(ax,bx,cx)).toInt().coerceAtLeast(0)
            val maxX=ceil(maxOf(ax,bx,cx)).toInt().coerceAtMost(bitmap.width-1)
            val minY=floor(minOf(ay,by,cy)).toInt().coerceAtLeast(0)
            val maxY=ceil(maxOf(ay,by,cy)).toInt().coerceAtMost(bitmap.height-1)
            val da=depth(f.a);val db=depth(f.b);val dc=depth(f.c)
            for(py in minY..maxY) for(px in minX..maxX) {
                val dx=px+0.5f-cx;val dy=py+0.5f-cy
                val a=((by-cy)*dx+(cx-bx)*dy)/determinant
                val b=((cy-ay)*dx+(ax-cx)*dy)/determinant
                val c=1f-a-b
                if(a>=-0.00001f && b>=-0.00001f && c>=-0.00001f) {
                    val z=a*da+b*db+c*dc;val index=py*bitmap.width+px
                    if(z>=depths[index]) { depths[index]=z;colors[index]=paint.color }
                }
            }
        }
        bitmap.setPixels(colors,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        val random=Random(terrain.id*631+417)
        paint.strokeWidth=0.75f/sample
        repeat(22000/(sample*sample)) {
            val x=random.nextInt(bitmap.width);val y=random.nextInt(bitmap.height)
            if(Color.alpha(bitmap[x,y])>220) {
                paint.color=if(it%3==0) 0x1842392f else 0x28fff7df
                canvas.drawLine(x.toFloat(),y.toFloat(),x+random.nextFloat()*2.2f/sample,y+random.nextFloat()*1.2f/sample,paint)
            }
        }
        return bitmap
    }
}
