package io.github.hatake716.pixelcity.ui

import android.content.res.AssetManager
import android.opengl.GLES20.*
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.GZIPInputStream
import kotlin.math.*
import org.json.JSONObject

/** Live versions of the existing felt designs. Owned exclusively by the GL thread. */
class GlobeModels(private val assets: AssetManager) {
    private data class Model(val vbo: Int,val count: Int) { val bytes get()=count*36 }
    private val models=LinkedHashMap<String,Model>(32,.75f,true)
    private var bytes=0
    private var program=0
    private val scratch=ByteBuffer.allocateDirect(256*1024).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var uploads=0
    private var nearbyTown: GlobeTown?=null
    private var nearbyKey=Int.MIN_VALUE
    private var nearby=emptyList<Int>()
    private val metadata by lazy { JSONObject(assets.open("globe/manifest.json").bufferedReader().use { it.readText() }) }
    private data class PickBox(val index: Int,val position: PlanetGeometry.V,val axes: Array<PlanetGeometry.V>,val scales: FloatArray,val bounds: FloatArray)
    @Volatile private var pickBoxes=emptyList<PickBox>()
    fun pick(origin: PlanetGeometry.V,direction: PlanetGeometry.V): Pair<Float,Int>? {
        var nearest=Float.POSITIVE_INFINITY;var index=-1
        for(box in pickBoxes) {
            val p=origin-box.position;var lo=0f;var hi=Float.POSITIVE_INFINITY
            for(a in 0..2) {
                val o=p.dot(box.axes[a])/box.scales[a];val d=direction.dot(box.axes[a])/box.scales[a]
                if(abs(d)<1e-7f) { if(o<box.bounds[a] || o>box.bounds[a+3]) hi=-1f }
                else { val t0=(box.bounds[a]-o)/d;val t1=(box.bounds[a+3]-o)/d;lo=max(lo,min(t0,t1));hi=min(hi,max(t0,t1)) }
            }
            if(hi>=lo && lo<nearest) { nearest=lo;index=box.index }
        }
        return if(index>=0) nearest to index else null
    }
    var pending=false
        private set
    var drawn=0
        private set
    var vehiclesDrawn=0
        private set
    var pedestriansDrawn=0
        private set
    private var position=0;private var normal=0;private var color=0
    private var origin=0;private var basis=0
    private val transform=FloatArray(9)
    fun create() {
        fun shader(type: Int,source: String): Int {
            val id=glCreateShader(type);glShaderSource(id,source);glCompileShader(id)
            val ok=IntArray(1);glGetShaderiv(id,GL_COMPILE_STATUS,ok,0);check(ok[0]!=0) { glGetShaderInfoLog(id) };return id
        }
        val vs=shader(GL_VERTEX_SHADER,"""
            uniform mat4 uMvp; uniform mat3 uBasis; uniform vec3 uOrigin;
            attribute vec3 aPosition; attribute vec3 aNormal; attribute vec3 aColor;
            varying vec3 vNormal; varying vec3 vColor; varying vec3 vLocal;
            void main(){vLocal=aPosition;vColor=aColor;
                vNormal=normalize(uBasis*aNormal);
                gl_Position=uMvp*vec4(uOrigin+uBasis*aPosition,1.0);}
        """.trimIndent())
        val fs=shader(GL_FRAGMENT_SHADER,"""
            precision mediump float; uniform vec3 uLight;uniform vec4 uTint;
            varying vec3 vNormal;varying vec3 vColor;varying vec3 vLocal;
            void main(){
                float yarn=sin(vLocal.x*350.0+vLocal.z*37.0)*sin(vLocal.y*310.0+vLocal.z*290.0);
                float light=0.62+0.65*max(0.0,dot(normalize(vNormal),uLight));
                vec3 dyed=mix(vColor,dot(vColor,vec3(.32,.60,.08))*uTint.rgb,uTint.a);
                gl_FragColor=vec4(dyed*light*(1.0+0.055*yarn),1.0);
            }
        """.trimIndent())
        program=glCreateProgram();glAttachShader(program,vs);glAttachShader(program,fs);glLinkProgram(program)
        val ok=IntArray(1);glGetProgramiv(program,GL_LINK_STATUS,ok,0);check(ok[0]!=0) { glGetProgramInfoLog(program) }
        glDeleteShader(vs);glDeleteShader(fs)
        position=glGetAttribLocation(program,"aPosition");normal=glGetAttribLocation(program,"aNormal");color=glGetAttribLocation(program,"aColor")
        origin=glGetUniformLocation(program,"uOrigin");basis=glGetUniformLocation(program,"uBasis")
    }
    private fun model(id: String): Model? {
        models[id]?.let { return it }
        if(uploads>=4) { pending=true;return null }
        uploads++
        val data=DataInputStream(GZIPInputStream(assets.open("globe/$id.meshdata")).buffered())
        return data.use { stream ->
            val count=stream.readInt();require(count in 3..150000 && count%3==0)
            val size=count*36
            while(bytes+size>24*1024*1024 && models.isNotEmpty()) {
                val old=models.entries.first();glDeleteBuffers(1,intArrayOf(old.value.vbo),0);bytes-=old.value.bytes;models.remove(old.key)
            }
            val ids=IntArray(1);glGenBuffers(1,ids,0);glBindBuffer(GL_ARRAY_BUFFER,ids[0]);glBufferData(GL_ARRAY_BUFFER,size,null,GL_STATIC_DRAW)
            var offset=0
            while(offset<count*9) {
                val length=min(scratch.capacity(),count*9-offset);scratch.clear()
                repeat(length) { scratch.put(stream.readFloat()) };scratch.flip()
                glBufferSubData(GL_ARRAY_BUFFER,offset*4,length*4,scratch);offset+=length
            }
            Model(ids[0],count).also { models[id]=it;bytes+=it.bytes }
        }
    }
    fun draw(town: GlobeTown,camera: PlanetGeometry.Camera,land: PlanetGeometry.Landscape,mvp: FloatArray,
             width: Int,height: Int,movers: List<GlobeMover>) {
        pending=false;drawn=0;uploads=0;vehiclesDrawn=0;pedestriansDrawn=0
        if(program==0 || camera.zoom<3f) { pickBoxes=emptyList();return }
        glUseProgram(program);glUniformMatrix4fv(glGetUniformLocation(program,"uMvp"),1,false,mvp,0)
        val light=(camera.eye+PlanetGeometry.V(-.8f,.85f,.45f)).unit()
        glUniform3f(glGetUniformLocation(program,"uLight"),light.x,light.y,light.z)
        val aspect=width.toFloat()/height.coerceAtLeast(1);val hh=camera.halfHeight(aspect)
        var vertices=0
        val boxes=ArrayList<PickBox>()
        fun instance(id: String,x: Float,y: Float,index: Int=-1): Boolean {
            val p=land.ground(x+.5f,y+.5f)
            val relative=p-camera.focus*camera.focusRadius
            if(p.unit().dot(camera.eye)<.08f || abs(relative.dot(camera.right))>hh*aspect+.035f || abs(relative.dot(camera.up))>hh+.06f) return false
            val detail=camera.zoom>=32f && vertices<250000
            val mesh=model(if(detail) id else "$id.lod") ?: return false
            if(vertices+mesh.count>1200000) return false
            val up=p.unit();val lon=atan2(up.x,up.z);val east=PlanetGeometry.V(cos(lon),0f,-sin(lon));val south=east.cross(up)
            val cosLat=sqrt(max(.006f,1-up.y*up.y))
            val sx=2*PI.toFloat()/512*cosLat;val sy=(2f/512/cosLat).coerceAtMost(.04f);val sz=sqrt(sx*sy)
            val ex=east*sx;val ey=south*sy;val ez=up*sz
            transform[0]=ex.x;transform[1]=ex.y;transform[2]=ex.z
            transform[3]=ey.x;transform[4]=ey.y;transform[5]=ey.z
            transform[6]=ez.x;transform[7]=ez.y;transform[8]=ez.z
            glUniform3f(origin,p.x,p.y,p.z);glUniformMatrix3fv(basis,1,false,transform,0)
            val tint=town.tint(index)
            glUniform4f(glGetUniformLocation(program,"uTint"),(tint ushr 16 and 255)/255f,(tint ushr 8 and 255)/255f,(tint and 255)/255f,if(tint==0) 0f else .65f)
            glBindBuffer(GL_ARRAY_BUFFER,mesh.vbo)
            for((a,offset) in arrayOf(position to 0,normal to 12,color to 24)) {
                glEnableVertexAttribArray(a);glVertexAttribPointer(a,3,GL_FLOAT,false,36,offset)
            }
            glDrawArrays(GL_TRIANGLES,0,mesh.count);vertices+=mesh.count;drawn++
            if(index>=0) {
                val b=metadata.getJSONObject(id).getJSONArray("bounds")
                boxes.add(PickBox(index,p,arrayOf(east,south,up),floatArrayOf(sx,sy,sz),FloatArray(6) { b.getDouble(it).toFloat() }))
            }
            return true
        }
        // Street life gets a reserved part of the draw budget instead of disappearing in dense cities.
        if(camera.zoom>=12f) for(m in movers.take(100)) if(instance(m.model,m.x,m.y)) {
            if(m.model.startsWith("PERSON_")) pedestriansDrawn++ else vehiclesDrawn++
        }
        val key=PlanetGeometry.tileIndex(camera.focus)*2+if(camera.zoom>=12f) 1 else 0
        if(nearbyTown!==town || nearbyKey!=key) { nearby=town.nearby(camera);nearbyTown=town;nearbyKey=key }
        for(i in nearby) {
            if(drawn>=600 || vertices>=1200000) break
            val id=town.model(i) ?: continue
            val shift=if(town.monuments[i].toInt()>=0) .5f else 0f
            instance(id,i%512+shift,i/512+shift,i)
        }
        pickBoxes=boxes
    }
    fun release() {
        for(m in models.values) glDeleteBuffers(1,intArrayOf(m.vbo),0)
        models.clear();bytes=0
        pickBoxes=emptyList();nearby=emptyList();nearbyTown=null
        if(program!=0) glDeleteProgram(program)
        program=0
    }
}
