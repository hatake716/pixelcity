package io.github.hatake716.pixelcity.ui

/** Area sampling keeps window frames and roof details calm when viewing a whole neighbourhood. */
internal object SpriteScale {
    private data class Key(val source: Sprite, val width: Int, val height: Int)
    private val cache = object : LinkedHashMap<Key, Sprite>(160, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Sprite>?) = size > 160
    }
    private val nearest by lazy {
        IntArray(32768) { code ->
            val r=(code shr 10)*8+4; val g=((code shr 5) and 31)*8+4; val b=(code and 31)*8+4
            var best=0;var distance=Int.MAX_VALUE
            for((i,c) in Palette.COLORS.withIndex()) {
                val dr=r-((c shr 16) and 255);val dg=g-((c shr 8) and 255);val db=b-(c and 255)
                val d=dr*dr*2+dg*dg*3+db*db
                if(d<distance){distance=d;best=i}
            }
            best
        }
    }
    @Synchronized
    fun of(source: Sprite, numerator: Int, denominator: Int): Sprite {
        if(numerator>=denominator)return source
        val w=(source.width*numerator/denominator).coerceAtLeast(1)
        val h=(source.height*numerator/denominator).coerceAtLeast(1)
        return cache.getOrPut(Key(source,w,h)) {
            val data=ByteArray(w*h){Pix.TRANSPARENT}
            for(y in 0 until h)for(x in 0 until w) {
                val x0=x*source.width/w;val x1=(x+1)*source.width/w
                val y0=y*source.height/h;val y1=(y+1)*source.height/h
                var r=0;var g=0;var b=0;var n=0
                for(sy in y0 until y1)for(sx in x0 until x1) {
                    val index=source.data[sy*source.width+sx].toInt()
                    if(index<0)continue
                    val c=Palette.COLORS[index]
                    r+=(c shr 16) and 255;g+=(c shr 8) and 255;b+=c and 255;n++
                }
                if(n>0 && n*3>=(x1-x0)*(y1-y0)) {
                    val code=((r/n shr 3) shl 10) or ((g/n shr 3) shl 5) or (b/n shr 3)
                    data[y*w+x]=nearest[code].toByte()
                }
            }
            Sprite(w,h,data)
        }
    }
}
