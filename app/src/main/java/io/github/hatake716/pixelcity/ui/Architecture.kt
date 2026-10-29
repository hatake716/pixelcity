package io.github.hatake716.pixelcity.ui

import io.github.hatake716.pixelcity.game.TileKind
import kotlin.math.*

/** Original procedural architecture. World-space façades, roofs and gardens share a 2:1 projection. */
internal object Architecture {
    private data class Material(val light: Int, val shade: Int, val roof: Int, val roofShade: Int)
    private val materials = arrayOf(
        Material(Palette.HOUSE_LEFT, Palette.HOUSE_RIGHT, Palette.HOUSE_ROOF, Palette.HOUSE_ROOF_DARK),
        Material(Palette.TOWER_ORANGE_LIT, Palette.HOUSE_ROOF, Palette.JP_ROOF, Palette.JP_ROOF_DARK),
        Material(Palette.PATINA_LIT, Palette.PATINA, Palette.RURAL_ROOF, Palette.RURAL_ROOF_DARK),
        Material(Palette.GOLD, Palette.LONDON_DARK, Palette.JP_ROOF, Palette.JP_ROOF_DARK),
        Material(Palette.PRIME_LEFT, Palette.OFFICE_LEFT, Palette.EURO_ROOF, Palette.EURO_ROOF_DARK),
        Material(Palette.FACTORY_LEFT, Palette.FACTORY_RIGHT, Palette.PATINA, Palette.PATINA_DARK),
    )
    private val civic = Material(Palette.EURO_WALL, Palette.EURO_WALL_DARK, Palette.JP_ROOF, Palette.JP_ROOF_DARK)
    private val glass = Material(Palette.GLASS_LIT, Palette.GLASS, Palette.PRIME_ROOF, Palette.OFFICE_ROOF)

    private data class P(val x: Int, val y: Int)
    private class Drawing(val top: Int) {
        val height = top + Iso.TILE_H
        val data = ByteArray(Iso.TILE_W * height) { Pix.TRANSPARENT }
        fun p(x: Double, y: Double, z: Double = 0.0) = P((256 + x - y).roundToInt(), (top + (x + y) / 2 - z).roundToInt())
        fun dot(x: Int, y: Int, color: Int) { if (x in 0..511 && y in 0 until height) data[y * 512 + x] = color.toByte() }
        fun line(a: P, b: P, color: Int, thickness: Int = 2) {
            val steps = max(abs(a.x - b.x), abs(a.y - b.y)).coerceAtLeast(1)
            for (i in 0..steps) {
                val x = a.x + (b.x - a.x) * i / steps
                val y = a.y + (b.y - a.y) * i / steps
                for (dy in 0 until thickness) for (dx in 0 until thickness) dot(x + dx, y + dy, color)
            }
        }
        fun poly(color: Int, vararg points: P, edge: Int = -1) {
            val lo = points.minOf { it.y }.coerceAtLeast(0)
            val hi = points.maxOf { it.y }.coerceAtMost(height - 1)
            val hits = DoubleArray(points.size)
            for (y in lo..hi) {
                var n = 0
                for (i in points.indices) {
                    val a = points[i]; val b = points[(i + 1) % points.size]
                    if ((a.y <= y && b.y > y) || (b.y <= y && a.y > y))
                        hits[n++] = a.x + (y - a.y).toDouble() * (b.x - a.x) / (b.y - a.y)
                }
                hits.sort(0, n)
                for (i in 0 until n - 1 step 2) {
                    val x0 = ceil(hits[i]).toInt().coerceAtLeast(0)
                    val x1 = floor(hits[i + 1]).toInt().coerceAtMost(511)
                    if (x0 <= x1) data.fill(color.toByte(), y * 512 + x0, y * 512 + x1 + 1)
                }
            }
            if (edge >= 0) for (i in points.indices) line(points[i], points[(i + 1) % points.size], edge)
        }
        fun plane(x: Double, y: Double, w: Double, d: Double, z: Double, color: Int, edge: Int = -1) =
            poly(color, p(x,y,z),p(x+w,y,z),p(x+w,y+d,z),p(x,y+d,z),edge=edge)
        fun block(x: Double, y: Double, w: Double, d: Double, z: Double, h: Double, m: Material) {
            poly(m.light,p(x,y+d,z),p(x+w,y+d,z),p(x+w,y+d,z+h),p(x,y+d,z+h),edge=Palette.WALL_EDGE)
            poly(m.shade,p(x+w,y,z),p(x+w,y+d,z),p(x+w,y+d,z+h),p(x+w,y,z+h),edge=Palette.WALL_EDGE)
            plane(x,y,w,d,z+h,m.roof,Palette.WALL_EDGE)
        }
        fun oval(c: P, rx: Int, ry: Int, color: Int) {
            for (dy in -ry..ry) {
                val span = (rx * sqrt((1.0 - dy.toDouble()*dy/(ry*ry).coerceAtLeast(1)).coerceAtLeast(0.0))).toInt()
                for (dx in -span..span) dot(c.x+dx,c.y+dy,color)
            }
        }
        fun tree(x: Double, y: Double, z: Double = 0.0, size: Int = 26) {
            oval(p(x+6,y+4,z),size, size/3,Palette.GRASS_DARK)
            line(p(x,y,z),p(x,y,z+size*1.5),Palette.TRUNK,4)
            oval(p(x,y,z+size*1.55),size*3/4,size,Palette.TREE_DARK)
            oval(p(x-5,y+2,z+size*1.85),size*3/4,size*3/4,Palette.TREE)
            oval(p(x-8,y+1,z+size*2.03),size/2,size/2,Palette.TREE_LIT)
        }
        fun planter(x: Double, y: Double, z: Double = 0.0, w: Double = 26.0) {
            block(x,y,w,10.0,z,6.0,Material(Palette.HOUSE_ROOF,Palette.HOUSE_ROOF_DARK,Palette.TRUNK,Palette.TRUNK))
            plane(x+1,y+1,w-2,8.0,z+9,Palette.TREE)
            for (i in 0 until (w/8).toInt()) oval(p(x+5+i*8,y+5,z+12),3,3,if (i%2==0) Palette.RED else Palette.GOLD)
        }
        fun person(x: Double, y: Double, shirt: Int = Palette.RED, z: Double = 0.0) {
            val c=p(x,y,z)
            oval(P(c.x+3,c.y+1),5,2,Palette.STONE_DARK)
            line(P(c.x-2,c.y),P(c.x-1,c.y-7),Palette.JP_ROOF_DARK,2)
            line(P(c.x+3,c.y),P(c.x+1,c.y-7),Palette.JP_ROOF_DARK,2)
            line(P(c.x,c.y-7),P(c.x,c.y-15),shirt,5)
            oval(P(c.x+2,c.y-18),3,4,Palette.HOUSE_LEFT)
            line(P(c.x-3,c.y-11),P(c.x+7,c.y-8),shirt,2)
        }
        fun umbrella(x: Double, y: Double, z: Double = 0.0, color: Int = Palette.RED) {
            oval(p(x,y,z),18,8,Palette.STONE_DARK)
            line(p(x,y,z),p(x,y,z+30),Palette.TRUNK,2)
            val center=p(x,y,z+37)
            val ring=(0..7).map { i -> val a=i*PI/4; p(x+cos(a)*25,y+sin(a)*25,z+27) }
            for (i in 0..7) poly(if(i%2==0)color else Palette.WHITE,center,ring[i],ring[(i+1)%8],edge=Palette.STONE_DARK)
        }
        fun bench(x: Double,y: Double,z: Double = 0.0) {
            block(x+3,y+2,3.0,9.0,z,7.0,civic);block(x+29,y+2,3.0,9.0,z,7.0,civic)
            block(x,y,36.0,13.0,z+7,3.0,materials[0])
            block(x,y+11,36.0,3.0,z+9,12.0,materials[0])
        }
        fun lamp(x: Double,y: Double,z: Double = 0.0) {
            line(p(x,y,z),p(x,y,z+45),Palette.PATINA_EDGE,3)
            oval(p(x,y,z+46),5,7,Palette.WINDOW_LIT)
            line(p(x-5,y,z+54),p(x+5,y,z+54),Palette.PATINA_EDGE,2)
        }
        fun face(side: Int,x: Double,y: Double,w: Double,d: Double,u: Double,z: Double): P =
            if(side==0)p(x+u,y+d,z) else p(x+w,y+u,z)
        fun panel(side: Int,x: Double,y: Double,w: Double,d: Double,u: Double,z: Double,pw: Double,ph: Double,color: Int,edge: Int = -1) =
            poly(color,face(side,x,y,w,d,u,z),face(side,x,y,w,d,u+pw,z),face(side,x,y,w,d,u+pw,z+ph),face(side,x,y,w,d,u,z+ph),edge=edge)
        fun roof(x: Double,y: Double,w: Double,d: Double,z: Double,m: Material,type: Int) {
            if(type==0) { // Sloping tiled roof and chimneys; ridge is a real silhouette.
                val r=52.0
                poly(m.roofShade,p(x-6,y-5,z),p(x+w+6,y-5,z),p(x+w+6,y+d/2,z+r),p(x-6,y+d/2,z+r),edge=Palette.WALL_EDGE)
                poly(m.light,p(x+w,y,z),p(x+w,y+d,z),p(x+w,y+d/2,z+r),edge=Palette.WALL_EDGE)
                poly(m.roof,p(x-6,y+d/2,z+r),p(x+w+6,y+d/2,z+r),p(x+w+6,y+d+6,z),p(x-6,y+d+6,z),edge=Palette.WALL_EDGE)
                for(i in 1..6) {
                    val f=i/7.0
                    line(p(x-5,y+d/2+(d/2+6)*f,z+r*(1-f)),p(x+w+5,y+d/2+(d/2+6)*f,z+r*(1-f)),m.roofShade,1)
                }
                for(i in 1..(w/22).toInt()) line(p(x+i*22,y+d/2,z+r),p(x+i*22,y+d+5,z),m.roofShade,1)
                block(x+w*.65,y+d*.3,15.0,16.0,z+33,48.0,materials[5])
                plane(x+w*.65-2,y+d*.3-2,19.0,20.0,z+83,Palette.STONE_LIT)
            } else if(type==1) { // Mansard: slate slopes, dormers and a pale ridge.
                val inset=22.0; val h=48.0
                poly(m.roof,p(x-4,y+d+4,z),p(x+w+4,y+d+4,z),p(x+w-inset,y+d-inset,z+h),p(x+inset,y+d-inset,z+h),edge=Palette.WALL_EDGE)
                poly(m.roofShade,p(x+w+4,y-4,z),p(x+w+4,y+d+4,z),p(x+w-inset,y+d-inset,z+h),p(x+w-inset,y+inset,z+h),edge=Palette.WALL_EDGE)
                plane(x+inset,y+inset,w-inset*2,d-inset*2,z+h,m.roofShade,Palette.WALL_EDGE)
                for(i in 1..5) {
                    val t=i/6.0
                    line(p(x-4+(inset+4)*t,y+d+4-(inset+4)*t,z+h*t),
                         p(x+w+4-(inset+4)*t,y+d+4-(inset+4)*t,z+h*t),m.roofShade,1)
                }
                for(i in 1 until (w/18).toInt())line(p(x+i*18,y+d+4,z),p(x+inset+(w-2*inset)*i/(w/18),y+d-inset,z+h),m.roofShade,1)
                for(u in 30 until w.toInt()-18 step 45) {
                    block(x+u,y+d-17,20.0,17.0,z+8,29.0,civic)
                    panel(0,x+u,y+d-17,20.0,17.0,4.0,z+12,12.0,20.0,Palette.GLASS,Palette.WHITE)
                    poly(Palette.WHITE,p(x+u-2,y+d,z+37),p(x+u+22,y+d,z+37),p(x+u+10,y+d,z+48),edge=Palette.WALL_EDGE)
                }
                block(x+w*.65,y+25,13.0,19.0,z+h,24.0,materials[5])
            } else terrace(x,y,w,d,z,m)
        }
        fun terrace(x: Double,y: Double,w: Double,d: Double,z: Double,m: Material) {
            plane(x+3,y+3,w-6,d-6,z+2,Palette.SAND_LIT)
            for(i in 1..(w/18).toInt()) line(p(x+i*18,y+4,z+2),p(x+i*18,y+d-4,z+2),Palette.STONE,1)
            block(x,y,w,5.0,z,11.0,civic)
            planter(x+12,y+14,z+2,w*.38)
            tree(x+w-22,y+24,z+2,17)
            block(x+12,y+d-26,w*.2,14.0,z+2,7.0,m)
            umbrella(x+w*.58,y+d*.58,z+2,if(m.light==Palette.GLASS_LIT)Palette.GOLD else Palette.PATINA)
            person(x+w*.65,y+d*.75,Palette.RED,z+2)
            // Thin wrought-iron railings on the two visible edges.
            for(u in 0..w.toInt() step 16)line(p(x+u,y+d,z+3),p(x+u,y+d,z+18),Palette.PATINA_EDGE,2)
            for(u in 0..d.toInt() step 16)line(p(x+w,y+u,z+3),p(x+w,y+u,z+18),Palette.PATINA_EDGE,2)
            line(p(x,y+d,z+18),p(x+w,y+d,z+18),Palette.PATINA_EDGE,2)
            line(p(x+w,y,z+18),p(x+w,y+d,z+18),Palette.PATINA_EDGE,2)
        }
        fun house(x: Double,y: Double,w: Double,d: Double,floors: Int,m: Material,roofType: Int,shop: Boolean=false,base: Double=0.0) {
            val h=floors*40.0+8
            block(x,y,w,d,base,h,m)
            for(side in 0..1) {
                val len=if(side==0)w else d
                val count=(len/36).toInt().coerceAtLeast(1)
                val spacing=len/count
                // Pale stone courses, corners and window reveals.
                panel(side,x,y,w,d,0.0,base+4,len,5.0,Palette.STONE_LIT)
                for(f in 0 until floors) {
                    val z=base+f*40+13
                    if(m==glass) {
                        for(col in 0 until count) {
                            val u=col*spacing+3
                            val c=if(side==0)Palette.GLASS_LIT else Palette.GLASS
                            panel(side,x,y,w,d,u,base+f*40+6,spacing-4,34.0,c,Palette.OFFICE_RIGHT)
                            panel(side,x,y,w,d,u+3,base+f*40+7,4.0,30.0,Palette.PRIME_LEFT)
                            if((f+col)%4==0)panel(side,x,y,w,d,u+9,base+f*40+9,spacing-15,9.0,Palette.WINDOW_LIT)
                        }
                        panel(side,x,y,w,d,0.0,base+(f+1)*40+3,len,3.0,Palette.PRIME_ROOF)
                        continue
                    }
                    for(col in 0 until count) {
                        val u=spacing*(col+.5)-9
                        if(f==0 && col==count/2 && side==0 && !shop) {
                            panel(side,x,y,w,d,u-2,base+5,22.0,31.0,Palette.STONE_LIT)
                            panel(side,x,y,w,d,u+1,base+5,16.0,27.0,Palette.PATINA_EDGE)
                            panel(side,x,y,w,d,u+3,base+19,12.0,10.0,Palette.WINDOW_LIT)
                        } else {
                            val wide=if(f==0&&shop)spacing-8 else 18.0
                            panel(side,x,y,w,d,u-3,z-3,wide+6,29.0,Palette.STONE_LIT)
                            panel(side,x,y,w,d,u,z,wide,24.0,if((f+col)%4==0)Palette.WINDOW_LIT else Palette.GLASS,Palette.WINDOW_DARK)
                            panel(side,x,y,w,d,u+wide/2-1,z,2.0,24.0,Palette.STONE_LIT)
                            panel(side,x,y,w,d,u,z+10,wide,2.0,Palette.STONE_LIT)
                            panel(side,x,y,w,d,u-4,z-5,wide+8,3.0,Palette.WHITE)
                            if(f>0 && (col+f)%2==0 && m!=glass) {
                                panel(side,x,y,w,d,u-4,z-9,wide+8,5.0,Palette.TRUNK)
                                panel(side,x,y,w,d,u-3,z-7,wide+6,5.0,Palette.TREE)
                                for(k in 0..2)panel(side,x,y,w,d,u+k*6,z-5,3.0,3.0,Palette.RED)
                            }
                        }
                    }
                    panel(side,x,y,w,d,0.0,base+(f+1)*40+3,len,3.0,Palette.STONE_LIT)
                }
                panel(side,x,y,w,d,0.0,base+h-1,len,5.0,Palette.WHITE)
                for(z in 6 until h.toInt()-3 step 14) {
                    panel(side,x,y,w,d,0.0,base+z,6.0,8.0,Palette.STONE_LIT)
                    panel(side,x,y,w,d,len-6,base+z,6.0,8.0,Palette.STONE_LIT)
                }
                if(shop) awning(side,x,y,w,d,base+36,if(m==materials[3])Palette.PATINA else Palette.RED)
            }
            roof(x,y,w,d,base+h+4,m,roofType)
        }
        fun awning(side: Int,x: Double,y: Double,w: Double,d: Double,z: Double,color: Int) {
            val len=if(side==0)w else d
            for(i in 5 until len.toInt()-5 step 14) {
                val next=min(i+14.0,len-5)
                val a=face(side,x,y,w,d,i.toDouble(),z+1)
                val b=face(side,x,y,w,d,next,z+1)
                val c=if(side==0)p(x+next,y+d+17,z-9)else p(x+w+17,y+next,z-9)
                val e=if(side==0)p(x+i,y+d+17,z-9)else p(x+w+17,y+i,z-9)
                val shade=if((i/14)%2==0)color else Palette.WHITE
                poly(shade,a,b,c,e,edge=Palette.STONE_DARK)
                line(e,P(e.x,e.y+6),shade,3);line(e,c,shade,4)
            }
        }
        fun sign(x: Double,y: Double,z: Double,label: String,color: Int=Palette.PATINA_EDGE) {
            val glyphs=mapOf('A' to "0111010001111111000110001",'B' to "1111010001111101000111110",'C' to "0111110000100001000001111",'E' to "1111110000111101000011111",'F' to "1111110000111101000010000",'H' to "1000110001111111000110001",'I' to "1111100100001000010011111",'K' to "1000110010111001001010001",'L' to "1000010000100001000011111",'M' to "1000111011101011000110001",'N' to "1000111001101011001110001",'O' to "0111010001100011000101110",'P' to "1111010001111101000010000",'R' to "1111010001111101001010001",'S' to "0111110000011100000111110",'T' to "1111100100001000010000100",'U' to "1000110001100011000101110",'Y' to "1000101010001000010000100")
            val w=label.length*15.0+12
            panel(0,x,y,w,0.0,0.0,z,w,20.0,color,Palette.GILT_LIT)
            for((n,c) in label.withIndex())for((i,v) in (glyphs[c]?:continue).withIndex())if(v=='1')
                panel(0,x,y,w,0.0,6+n*15.0+(i%5)*2.0,z+14-(i/5)*2.5,2.0,2.5,Palette.WHITE)
        }
        fun lot(green: Boolean=false) {
            plane(9.0,9.0,238.0,238.0,0.0,if(green)Palette.GRASS_LIT else Palette.SAND_LIT)
            plane(20.0,18.0,212.0,190.0,0.0,if(green)Palette.GRASS else Palette.STONE)
            for(i in 0..5) line(p(10.0+i*40,218.0),p(10.0+i*40,246.0),Palette.SAND_DARK,1)
        }
        fun streetLife(variant: Int) {
            planter(28.0,232.0,w=38.0)
            lamp(238.0,212.0)
            bench(148.0,234.0)
            person(86.0,239.0,Palette.OFFICE_LEFT)
            person(100.0,240.0,Palette.RED)
            if(variant%2==0)person(232.0,152.0,Palette.GOLD)
        }
        fun result(): Sprite {
            // Trim only the transparent sky. Ground alignment is unchanged.
            var first=0
            while(first<height-256 && (0..511).all { data[first*512+it]==Pix.TRANSPARENT })first++
            first=(first-4).coerceAtLeast(0)
            return Sprite(512,height-first,data.copyOfRange(first*512,data.size))
        }
    }

    fun zone(kind: TileKind, stage: Int, variant: Int): Sprite {
        val v=Math.floorMod(variant,6)
        val s=stage.coerceIn(1,3)
        val a=Drawing(720)
        val m=materials[v]
        with(a) {
            lot(kind==TileKind.ZONE_R&&s==1)
            tree(25.0,38.0,size=28)
            if(kind==TileKind.ZONE_I) {
                // Brick workshops -> sawtooth works -> factory and a tall water tower.
                val floors=s+v%2
                house(34.0,32.0,154.0,136.0,floors,materials[5],2,shop=false)
                for(i in 0..2) roof(40.0,40.0+i*38,140.0,36.0,floors*40.0+12,glass,0)
                block(200.0,36.0,20.0,24.0,0.0,145.0+s*35,materials[5])
                for(z in 100..(145+s*35).toInt() step 26)block(198.0,34.0,24.0,28.0,z.toDouble(),7.0,civic)
                block(190.0,155.0,37.0,40.0,0.0,24.0,materials[1])
                panel(0,34.0,32.0,154.0,136.0,22.0,4.0,36.0,33.0,Palette.JP_ROOF_DARK)
                sign(48.0,168.0,43.0,if(v%2==0)"WORKS" else "MILL")
                for(i in 0..2)block(44.0+i*35,200.0,24.0,17.0,0.0,14.0,materials[(v+i)%6])
            } else if(s==1) {
                if(v==1||v==4) {
                    house(36.0,34.0,78.0,143.0,2,materials[v],0,kind==TileKind.ZONE_C)
                    house(122.0,46.0,81.0,131.0,2,materials[(v+1)%6],1,kind==TileKind.ZONE_C)
                } else if(v==3) {
                    house(48.0,38.0,152.0,135.0,2,m,1,kind==TileKind.ZONE_C)
                    house(158.0,116.0,49.0,54.0,3,m,0)
                } else {
                    house(35.0+v*3,38.0,151.0-v*5,133.0,2,m,if(v==2)2 else 0,kind==TileKind.ZONE_C)
                    if(v==5)house(152.0,88.0,58.0,82.0,1,civic,0)
                }
                if(kind==TileKind.ZONE_C) {
                    sign(54.0,180.0,48.0,if(v%2==0)"CAFE" else "BOOKS")
                    umbrella(93.0,210.0,color=Palette.GOLD)
                    person(123.0,217.0,Palette.PATINA)
                } else {
                    tree(204.0,190.0,size=25)
                    planter(41.0,190.0,w=86.0)
                }
            } else if(s==2) {
                val f=4+v%2
                house(28.0,28.0,180.0-v*5,172.0-v*3,f,m,if(v==2||v==5)2 else 1,true)
                if(v==1||v==3)house(163.0,115.0,51.0,73.0,f+1,m,0)
                if(v==4)house(50.0,56.0,120.0,95.0,2,glass,2,base=f*40.0+62)
                sign(44.0,207.0,49.0,if(kind==TileKind.ZONE_C)"MARKET" else "HOME")
            } else {
                // Six silhouettes: terraced garden block, glass tower, courtyard,
                // clock corner, paired spires and a brick/greenhouse conversion.
                house(25.0,25.0,192.0,177.0,3,m,2,true)
                val base=136.0
                when(v) {
                    0 -> { house(38.0,37.0,157.0,131.0,4,m,2,base=base); house(53.0,51.0,126.0,101.0,2,civic,1,base=base+172) }
                    1 -> { house(48.0,43.0,127.0,119.0,9,glass,2,base=base) }
                    2 -> { house(31.0,30.0,65.0,135.0,6,m,1,base=base); house(112.0,31.0,85.0,99.0,5,materials[4],2,base=base) }
                    3 -> { house(44.0,36.0,148.0,112.0,5,m,1,base=base); house(142.0,133.0,65.0,57.0,8,m,0,base=base); val c=p(174.0,190.0,base+304); oval(c,14,17,Palette.STONE_LIT); line(c,P(c.x,c.y-11),Palette.JP_ROOF_DARK); line(c,P(c.x+8,c.y+4),Palette.JP_ROOF_DARK) }
                    4 -> { house(34.0,39.0,74.0,120.0,7,glass,2,base=base);house(119.0,32.0,77.0,101.0,9,m,1,base=base) }
                    else -> { house(47.0,37.0,141.0,128.0,5,m,2,base=base);house(64.0,46.0,97.0,84.0,2,glass,0,base=base+214) }
                }
                sign(37.0,202.0,48.0,if(kind==TileKind.ZONE_C)"HOTEL" else "HOME")
            }
            streetLife(v)
        }
        return a.result()
    }

    val sailboat: Sprite by lazy {
        val a=Drawing(150)
        with(a) {
            oval(p(127.0,142.0),82,25,Palette.WATER_LIT)
            poly(Palette.JP_ROOF_DARK,p(49.0,138.0,8.0),p(126.0,100.0,8.0),p(204.0,125.0,8.0),p(152.0,153.0,8.0),edge=Palette.WHITE)
            poly(Palette.WHITE,p(55.0,133.0,15.0),p(126.0,104.0,15.0),p(194.0,124.0,15.0),p(150.0,146.0,15.0))
            line(p(122.0,126.0,10.0),p(122.0,126.0,127.0),Palette.TRUNK,3)
            poly(Palette.SAND_LIT,p(122.0,126.0,123.0),p(122.0,126.0,32.0),p(57.0,126.0,24.0),edge=Palette.WHITE)
            poly(Palette.WHITE,p(124.0,126.0,109.0),p(184.0,126.0,28.0),p(124.0,126.0,33.0),edge=Palette.STONE)
            person(149.0,136.0,Palette.RED,16.0)
        }
        a.result()
    }

    val grove: Sprite by lazy {
        val a=Drawing(145)
        with(a) {tree(57.0,76.0,size=35);tree(157.0,99.0,size=31);tree(106.0,168.0,size=37)}
        a.result()
    }

    fun facility(kind: TileKind): Sprite {
        val a=Drawing(530)
        with(a) {
            lot(kind==TileKind.PARK)
            when(kind) {
                TileKind.PARK -> {
                    plane(27.0,27.0,200.0,196.0,0.0,Palette.GRASS)
                    plane(95.0,15.0,46.0,222.0,1.0,Palette.SAND_LIT)
                    plane(15.0,104.0,222.0,34.0,1.0,Palette.SAND_LIT)
                    val c=p(118.0,118.0);oval(c,46,23,Palette.STONE_DARK);oval(P(c.x,c.y-3),42,21,Palette.STONE_LIT);oval(P(c.x,c.y-5),34,16,Palette.WATER_LIT)
                    block(111.0,111.0,14.0,14.0,2.0,24.0,civic);oval(p(118.0,118.0,33.0),21,10,Palette.STONE_LIT)
                    line(p(118.0,118.0,32.0),p(118.0,118.0,56.0),Palette.WHITE,3)
                    for((x,y) in listOf(45.0 to 42.0,194.0 to 43.0,44.0 to 179.0,203.0 to 183.0))tree(x,y,size=34)
                    bench(51.0,139.0);bench(150.0,99.0);person(126.0,186.0,Palette.RED);umbrella(198.0,127.0,color=Palette.GOLD)
                }
                TileKind.POWER_WIND -> {
                    tree(34.0,56.0);house(140.0,77.0,69.0,88.0,1,civic,0)
                    block(99.0,103.0,16.0,16.0,0.0,283.0,civic)
                    val c=p(107.0,111.0,284.0)
                    for(i in 0..2) {val t=i*PI*2/3;poly(Palette.WHITE,c,P(c.x+(cos(t)*108).toInt(),c.y+(sin(t)*108).toInt()),P(c.x+(cos(t+.14)*42).toInt(),c.y+(sin(t+.14)*42).toInt()),edge=Palette.METAL_DARK)}
                    oval(c,9,9,Palette.GOLD)
                }
                TileKind.POWER_LINE -> {
                    for(x in listOf(86.0,137.0))block(x,112.0,6.0,8.0,0.0,150.0,materials[5])
                    block(63.0,108.0,100.0,14.0,120.0,7.0,civic)
                    for(x in listOf(74.0,113.0,153.0))line(p(x,8.0,147.0),p(x,244.0,136.0),Palette.WINDOW_DARK,2)
                }
                TileKind.WATER_TOWER -> {
                    for(x in listOf(75.0,160.0))for(y in listOf(66.0,151.0))block(x,y,8.0,8.0,0.0,162.0,civic)
                    block(64.0,55.0,117.0,117.0,151.0,74.0,materials[2]);roof(59.0,50.0,127.0,127.0,228.0,materials[2],1)
                    house(40.0,160.0,71.0,49.0,1,civic,0)
                }
                TileKind.POWER_SOLAR -> {
                    house(32.0,25.0,90.0,58.0,1,civic,0)
                    for(y in 0..2)for(x in 0..2) {
                        block(32.0+x*64,101.0+y*35,54.0,26.0,0.0,9.0,civic)
                        plane(31.0+x*64,99.0+y*35,56.0,28.0,12.0,Palette.OFFICE_RIGHT,Palette.METAL_LIT)
                        for(i in 1..3)line(p(31.0+x*64+i*14,99.0+y*35,12.0),p(31.0+x*64+i*14,127.0+y*35,12.0),Palette.GLASS_LIT,1)
                    }
                }
                TileKind.WATER_PLANT,TileKind.SEWAGE_PLANT -> {
                    house(31.0,29.0,178.0,73.0,2,civic,2)
                    for(x in listOf(72.0,170.0)) { val c=p(x,161.0);oval(c,55,27,Palette.STONE_DARK);oval(P(c.x,c.y-8),52,26,Palette.STONE_LIT);oval(P(c.x,c.y-10),43,20,if(kind==TileKind.WATER_PLANT)Palette.WATER else Palette.PATINA);line(P(c.x-43,c.y-10),P(c.x+43,c.y-10),Palette.WHITE,3)}
                }
                TileKind.LANDFILL -> {
                    house(27.0,28.0,84.0,76.0,1,materials[5],0)
                    for(y in 0..4)for(x in 0..4){val c=p(74.0+x*28,94.0+y*25,((x+y)%3)*9.0);oval(c,29,14,if((x+y)%2==0)Palette.RURAL_ROOF else Palette.STONE_DARK)}
                    for(x in 20..236 step 18)line(p(x.toDouble(),222.0),p(x.toDouble(),222.0,29.0),Palette.TRUNK,3)
                }
                TileKind.BUS_STOP,TileKind.SUBWAY_STATION -> {
                    if(kind==TileKind.SUBWAY_STATION) {
                        block(51.0,46.0,135.0,125.0,0.0,12.0,civic)
                        plane(66.0,69.0,105.0,75.0,13.0,Palette.OPENING)
                        for(i in 0..5)line(p(72.0,81.0+i*10,14.0),p(164.0,81.0+i*10,14.0),Palette.STONE,3)
                    }
                    for(x in listOf(45.0,192.0))block(x,159.0,5.0,6.0,0.0,71.0,materials[2])
                    block(37.0,111.0,168.0,62.0,72.0,8.0,materials[2]);bench(60.0,155.0)
                    sign(51.0,174.0,77.0,if(kind==TileKind.BUS_STOP)"BUS" else "METRO")
                    person(127.0,170.0,Palette.GOLD);person(156.0,179.0,Palette.RED)
                }
                TileKind.SEAPORT -> {
                    plane(23.0,104.0,210.0,103.0,1.0,Palette.RURAL_ROOF)
                    for(i in 0..8)line(p(24.0,106.0+i*11,2.0),p(230.0,106.0+i*11,2.0),Palette.RURAL_ROOF_DARK,2)
                    house(28.0,21.0,101.0,108.0,2,materials[1],0,true)
                    block(167.0,84.0,15.0,17.0,0.0,196.0,materials[3]);block(108.0,69.0,122.0,20.0,190.0,10.0,materials[3]);line(p(219.0,79.0,190.0),p(219.0,79.0,76.0),Palette.WALL_EDGE,2)
                    for(i in 0..2)block(148.0,145.0+i*23,57.0,20.0,0.0,22.0,materials[(i+1)%6])
                }
                TileKind.AIRPORT -> {
                    house(29.0,25.0,140.0,81.0,2,glass,2)
                    house(185.0,34.0,35.0,39.0,5,civic,2)
                    block(180.0,29.0,45.0,49.0,204.0,27.0,glass)
                    plane(19.0,148.0,219.0,66.0,1.0,Palette.ROAD)
                    for(i in 0..5)plane(24.0+i*38,178.0,18.0,3.0,2.0,Palette.WHITE)
                    poly(Palette.WHITE,p(51.0,186.0,15.0),p(140.0,140.0,15.0),p(154.0,148.0,15.0),p(114.0,177.0,15.0),p(178.0,190.0,15.0),p(172.0,199.0,15.0),p(97.0,189.0,15.0),edge=Palette.OFFICE_RIGHT)
                }
                TileKind.POWER_COAL,TileKind.INCINERATOR,TileKind.RECYCLING -> {
                    house(31.0,31.0,151.0,142.0,3,materials[5],2)
                    for(x in listOf(171.0,206.0)) {
                        block(x,38.0,22.0,27.0,0.0,244.0,materials[5])
                        for(z in 160..235 step 32)block(x-1,37.0,24.0,29.0,z.toDouble(),9.0,civic)
                    }
                    for(i in 0..2)block(34.0+i*58,185.0,46.0,31.0,0.0,29.0,materials[if(kind==TileKind.RECYCLING)2 else i])
                    sign(46.0,173.0,50.0,if(kind==TileKind.RECYCLING)"REUSE" else "POWER")
                }
                else -> {
                    val floors=when(kind){TileKind.HOSPITAL->5;TileKind.SCHOOL->3;else->2}
                    val m=when(kind){TileKind.FIRE->materials[1];TileKind.POLICE->materials[4];TileKind.CLINIC,TileKind.HOSPITAL->civic;else->materials[3]}
                    house(30.0,33.0,171.0,148.0,floors,m,if(kind==TileKind.HOSPITAL)2 else 1)
                    when(kind) {
                        TileKind.SCHOOL -> {house(93.0,116.0,54.0,64.0,4,civic,0);sign(42.0,182.0,48.0,"SCHOOL");for(i in 0..3)person(67.0+i*19,210.0,Palette.GOLD)}
                        TileKind.FIRE -> {for(u in listOf(18.0,72.0,126.0))panel(0,30.0,33.0,171.0,148.0,u,6.0,38.0,35.0,Palette.RED_DARK,Palette.STONE_LIT);sign(62.0,184.0,54.0,"FIRE")}
                        TileKind.POLICE -> sign(50.0,184.0,52.0,"POLICE",Palette.OFFICE_RIGHT)
                        else -> {panel(0,30.0,33.0,171.0,148.0,78.0,74.0,13.0,39.0,Palette.RED);panel(0,30.0,33.0,171.0,148.0,65.0,87.0,39.0,13.0,Palette.RED);sign(62.0,184.0,49.0,"CLINIC")}
                    }
                    tree(217.0,155.0,size=24)
                }
            }
            streetLife(kind.saveId)
        }
        return a.result()
    }
}
