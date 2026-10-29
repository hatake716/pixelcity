package io.github.hatake716.pixelcity.ui

import android.annotation.SuppressLint
import android.content.Context
import android.opengl.GLES20.*
import io.github.hatake716.pixelcity.game.EarthTerrain
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.VelocityTracker
import io.github.hatake716.pixelcity.game.Planet
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

/** Saved per-tile terrain on a relief globe, with bounded detail meshes and GPU resources. */
@SuppressLint("ViewConstructor") // Created with a live Planet by the activity; never inflated from XML.
class PlanetSurface(context: Context, private val planet: Planet,private val cityRendering: Boolean=false) : GLSurfaceView(context) {
    private val scene = GlobeRenderer()
    val renderedFrames: Long get() = scene.frames
    val renderedRevision: Int get() = scene.renderedRevision
    val renderedZoom: Float get() = scene.renderedZoom
    val cityModelsPending: Boolean get() = scene.cityModelsPending
    val cityModelsDrawn: Int get() = scene.cityModelsDrawn
    val cityVehiclesDrawn: Int get() = scene.cityVehiclesDrawn
    val cityPedestriansDrawn: Int get() = scene.cityPedestriansDrawn
    val camera: PlanetGeometry.Camera get() = scene.camera
    fun pick(x: Float,y: Float): Int? {
        val (origin,direction)=camera.ray(x,y,width,height)
        val ground=scene.mesh?.hit(origin,direction);val building=scene.pickTown(origin,direction)
        return if(building!=null && (ground==null || building.first<ground.first)) building.second else ground?.second
    }
    private var cameraGestureAnchor: PlanetGeometry.V?=null
    /** Intersect relief once per gesture, then retain the same world point throughout the pinch. */
    fun beginCameraGesture(x: Float,y: Float) {
        stopMotion();cameraGestureAnchor=null
        if(width<=0 || height<=0) return
        val (o,d)=camera.ray(x,y,width,height)
        scene.mesh?.hit(o,d)?.let { cameraGestureAnchor=o+d*it.first }
    }
    fun endCameraGesture() { cameraGestureAnchor=null }
    fun moveCamera(dx: Float,dy: Float,ratio: Float=1f,anchorX: Float=width/2f,anchorY: Float=height/2f) {
        val radius=cameraGestureAnchor?.length() ?: camera.focusRadius
        fun anchor(cam: PlanetGeometry.Camera,x: Float,y: Float): PlanetGeometry.V? {
            if(width==0 || height==0) return null
            val (o,d)=cam.ray(x,y,width,height);val b=o.dot(d);val c=o.dot(o)-radius*radius
            val disc=b*b-c;if(disc<0) return null
            return (o+d*(-b-sqrt(disc))).unit()
        }
        val held=cameraGestureAnchor?.unit() ?: anchor(camera,anchorX-dx,anchorY-dy)
        planet.zoom=(planet.zoom*ratio).coerceIn(.75f,96f);syncCamera()
        if(held!=null) repeat(4) {
            val now=anchor(camera,anchorX,anchorY) ?: return@repeat
            val focus=(camera.focus+held-now).unit()
            planet.longitude=atan2(focus.x,focus.z);planet.latitude=asin(focus.y.coerceIn(-.9999f,.9999f));syncCamera()
        } else rotate(dx,dy)
    }
    fun setZoom(value: Float) { planet.zoom=value.coerceIn(.75f,96f);syncCamera();updateDetail();onCameraChanged?.invoke() }
    fun updateTown(overlay: CityRenderer.Overlay=CityRenderer.Overlay.NONE) {
        val town=GlobeTown.of(planet.city,overlay)
        queueEvent { scene.town=town;scene.townDirty=true;scene.overlayEnabled=overlay!=CityRenderer.Overlay.NONE }
        requestRender()
    }
    fun updateMovers(movers: List<GlobeMover>) { scene.movers=movers;requestRender() }
    fun setHighlights(selected: IntArray,hints: IntArray=IntArray(0)) {
        queueEvent {
            scene.markBytes.fill(0)
            for(i in hints) if(i in 0 until planet.tileCount) scene.markBytes[i*4+1]=100
            for(i in selected) if(i in 0 until planet.tileCount) scene.markBytes[i*4]=(-1).toByte()
            scene.marksDirty=true
        };requestRender()
    }
    var onTileSelected: ((Int) -> Unit)? = null
    var onCameraChanged: (() -> Unit)? = null
    private var tracker: VelocityTracker? = null
    private var lastX=0f; private var lastY=0f
    private var downX=0f; private var downY=0f
    private var dragged=false; private var pinched=false
    private var vx=0f; private var vy=0f
    private var frameTime=0L
    private var running=false
    private val fling = object : Runnable {
        override fun run() {
            if(!running) return
            val now=System.nanoTime(); val dt=((now-frameTime)/1e9f).coerceIn(0f,0.04f); frameTime=now
            rotate(vx*dt,vy*dt)
            val friction=exp(-5f*dt); vx*=friction; vy*=friction
            if(abs(vx)+abs(vy)>8f) postOnAnimation(this) else { running=false; onCameraChanged?.invoke() }
        }
    }
    private var lastSpan=0f
    private var lastMidX=0f;private var lastMidY=0f
    init {
        isClickable=true; isFocusable=true; importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription="立体惑星。指で回転、二本指で拡大縮小。座標欄から各マスを選択できます。"
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8,8,8,8,16,0)
        preserveEGLContextOnPause=false
        scene.snapshot=PlanetGeometry.Snapshot.of(planet)
        if(cityRendering) scene.town=GlobeTown.of(planet.city)
        syncCamera(false)
        setRenderer(scene)
        renderMode=RENDERMODE_WHEN_DIRTY
    }
    fun setSeabed(visible: Boolean) {
        planet.seabedVisible=visible
        refresh(planet.selectedIndex)
    }
    fun refresh(selected: Int) {
        syncCamera()
        val snapshot=PlanetGeometry.Snapshot.of(planet)
        queueEvent { scene.snapshot=snapshot; scene.rebuild=true; scene.selected=selected }
        requestRender()
    }
    fun select(index: Int, focus: Boolean, detail: Boolean = false) {
        if(focus) {
            stopMotion()
            val center=PlanetGeometry.center(index)
            planet.longitude=center.first; planet.latitude=center.second
            planet.zoom=if(detail) 24f else 1f
            syncCamera()
            updateDetail()
        }
        scene.selected=index
        requestRender()
    }
    fun updateBrush(size: Int) { scene.brushSize=size;requestRender() }
    fun updateDetail() {
        queueEvent { if(scene.detailKey()!=scene.builtDetailKey) scene.rebuild=true }
        requestRender()
    }
    private fun syncCamera(render: Boolean=true) {
        val base=PlanetGeometry.Camera(planet.longitude,planet.latitude,planet.zoom,cityView=cityRendering)
        val i=PlanetGeometry.tileIndex(base.focus)
        val altitude=EarthTerrain.relief(i,planet.city.tiles[i].biome)
        scene.camera=base.copy(focusRadius=1f+if(planet.seabedVisible) altitude else max(0f,altitude))
        if(render) requestRender()
    }
    private fun rotate(dx: Float, dy: Float) {
        if(cityRendering) {
            val cam=camera;val distance=2*cam.halfHeight(width.toFloat()/height.coerceAtLeast(1))/height.coerceAtLeast(1)
            val north=cam.focus.cross(cam.right)
            val focus=(cam.focus-cam.right*(dx*distance)+north*(dy*distance/max(.5f,cam.up.dot(north)))).unit()
            planet.longitude=atan2(focus.x,focus.z);planet.latitude=asin(focus.y.coerceIn(-.9999f,.9999f));syncCamera();return
        }
        val turn=PI.toFloat()/width.coerceAtLeast(1)/planet.zoom
        planet.longitude=((planet.longitude-dx*turn+3*PI.toFloat())%(2*PI.toFloat()))-PI.toFloat()
        planet.latitude=(planet.latitude+dy*turn).coerceIn(-1.56f,1.56f)
        syncCamera()
    }
    fun stopMotion() { running=false; removeCallbacks(fling); vx=0f; vy=0f }
    private var rendererPaused=false
    override fun onPause() {
        endCameraGesture();stopMotion(); tracker?.recycle(); tracker=null
        if(!rendererPaused) {
            // Delete while this context is current, before GLSurfaceView suspends its thread.
            queueEvent { scene.releaseGl() }
            super.onPause()
            rendererPaused=true
        }
    }
    override fun onResume() { super.onResume();rendererPaused=false;requestRender() }
    override fun onDetachedFromWindow() {
        endCameraGesture();stopMotion(); super.onDetachedFromWindow() // GLSurfaceView joins its render thread here.
        scene.mesh=null
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                endCameraGesture();stopMotion(); tracker?.recycle(); tracker=VelocityTracker.obtain().also { it.addMovement(event) }
                lastX=event.x; lastY=event.y; downX=event.x; downY=event.y; dragged=false; pinched=false; lastSpan=0f
                parent.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                pinched=true; stopMotion(); tracker?.clear()
                lastSpan=if(event.pointerCount==2) hypot(event.getX(0)-event.getX(1),event.getY(0)-event.getY(1)) else 0f
                if(event.pointerCount==2) {
                    lastMidX=(event.getX(0)+event.getX(1))/2;lastMidY=(event.getY(0)+event.getY(1))/2
                    if(cityRendering) beginCameraGesture(lastMidX,lastMidY)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                endCameraGesture()
                val index=if(event.actionIndex==0) 1 else 0
                lastX=event.getX(index); lastY=event.getY(index); lastSpan=0f
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(event)
                if(event.pointerCount==2) {
                    val span=hypot(event.getX(0)-event.getX(1),event.getY(0)-event.getY(1))
                    if(lastSpan>0f && span>0f) {
                        val mx=(event.getX(0)+event.getX(1))/2;val my=(event.getY(0)+event.getY(1))/2
                        if(cityRendering) moveCamera(mx-lastMidX,my-lastMidY,span/lastSpan,mx,my)
                        else { planet.zoom=(planet.zoom*span/lastSpan).coerceIn(0.75f,32f);syncCamera() }
                        lastMidX=mx;lastMidY=my
                    }
                    lastSpan=span
                }
                if(event.pointerCount==1 && !pinched) {
                    if(hypot(event.x-downX,event.y-downY)>8*resources.displayMetrics.density) dragged=true
                    if(dragged) rotate(event.x-lastX,event.y-lastY)
                }
                lastX=event.x; lastY=event.y
            }
            MotionEvent.ACTION_UP -> {
                endCameraGesture()
                if(!dragged && !pinched) {
                    performClick()
                    val ray=scene.camera.ray(event.x,event.y,width,height)
                    scene.mesh?.pick(ray.first,ray.second)?.let { onTileSelected?.invoke(it) }
                } else if(dragged && !pinched) {
                    tracker?.apply { computeCurrentVelocity(1000); vx=xVelocity.coerceIn(-1800f,1800f); vy=yVelocity.coerceIn(-1800f,1800f) }
                    running=true; frameTime=System.nanoTime(); postOnAnimation(fling)
                }
                tracker?.recycle(); tracker=null
                parent.requestDisallowInterceptTouchEvent(false)
                onCameraChanged?.invoke()
            }
            MotionEvent.ACTION_CANCEL -> { endCameraGesture();stopMotion(); tracker?.recycle(); tracker=null; parent.requestDisallowInterceptTouchEvent(false) }
        }
        return true
    }

    private inner class GlobeRenderer : Renderer {
        @Volatile var frames=0L
        @Volatile var renderedRevision=-1
        @Volatile var renderedZoom=0f
        @Volatile var cityModelsPending=true
        @Volatile var camera=PlanetGeometry.Camera(0f,0f,1f)
        @Volatile var brushSize=1
        @Volatile var selected=Planet.SIZE*Planet.SIZE/2+Planet.SIZE/2
        @Volatile var mesh: PlanetGeometry.Mesh?=null
        @Volatile var movers: List<GlobeMover> = emptyList()
        @Volatile var cityModelsDrawn=0
        @Volatile var cityVehiclesDrawn=0
        @Volatile var cityPedestriansDrawn=0
        var town: GlobeTown?=null
        var townDirty=true;var overlayEnabled=false
        val markBytes=ByteArray(512*512*4)
        var marksDirty=true
        private val townModels=GlobeModels(context.applicationContext.assets)
        fun pickTown(origin: PlanetGeometry.V,direction: PlanetGeometry.V)=townModels.pick(origin,direction)
        lateinit var snapshot: PlanetGeometry.Snapshot
        var rebuild=true
        var builtDetailKey=Int.MIN_VALUE
        fun detailKey(): Int {
            val cam=camera
            if(cam.zoom<8f) return -1
            val index=PlanetGeometry.tileIndex(cam.focus)
            return (index/Planet.SIZE/2)*Planet.SIZE+index%Planet.SIZE/2
        }
        fun releaseGl() {
            if(program==0) return
            glBindBuffer(GL_ARRAY_BUFFER,0)
            glDeleteBuffers(3,intArrayOf(vbo,borderVbo,atmosphereVbo),0)
            glBindTexture(GL_TEXTURE_2D,0)
            glDeleteTextures(1,intArrayOf(texture),0)
            glDeleteTextures(2,intArrayOf(townTexture,markTexture),0);townModels.release()
            glUseProgram(0);glDeleteProgram(program)
            program=0;vbo=0;borderVbo=0;atmosphereVbo=0;texture=0
            mesh=null;rebuild=true
        }
        private var program=0; private var vbo=0; private var borderVbo=0; private var borderCount=0
        private var atmosphereVbo=0; private var atmosphereCount=0; private var texture=0
        private var townTexture=0;private var markTexture=0
        private lateinit var landscape: PlanetGeometry.Landscape
        private var w=1; private var h=1
        private val projection=FloatArray(16); private val view=FloatArray(16); private val mvp=FloatArray(16)
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            glClearColor(0.012f,0.025f,0.05f,1f)
            glEnable(GL_DEPTH_TEST)
            // Triangle normals are oriented explicitly, including tapered decoration geometry.
            glDisable(GL_CULL_FACE)
            program=glCreateProgram()
            fun shader(type: Int, source: String): Int {
                val id=glCreateShader(type); glShaderSource(id,source); glCompileShader(id)
                val ok=IntArray(1); glGetShaderiv(id,GL_COMPILE_STATUS,ok,0)
                check(ok[0]!=0) { glGetShaderInfoLog(id) }; return id
            }
            val vs=shader(GL_VERTEX_SHADER,"""
                uniform mat4 uMvp;
                attribute vec3 aPosition; attribute vec3 aNormal; attribute vec3 aColor;
                attribute float aRegion; attribute float aSurface;
                varying vec3 vNormal; varying vec3 vColor; varying vec3 vPosition;
                varying float vRegion; varying float vSurface;
                void main() {
                    vNormal=aNormal; vColor=aColor; vPosition=aPosition;
                    vRegion=aRegion; vSurface=aSurface; gl_Position=uMvp*vec4(aPosition,1.0);
                }
            """.trimIndent())
            val fs=shader(GL_FRAGMENT_SHADER,"""
                precision highp float;
                uniform float uSelected; uniform float uLines; uniform vec3 uEye;
                uniform sampler2D uTerrain;uniform sampler2D uTown;uniform sampler2D uMarks;
                uniform float uCity;uniform float uOverlay;uniform vec3 uOverlayColors[6]; uniform float uSeabed; uniform float uZoom;
                varying vec3 vNormal; varying vec3 vColor; varying vec3 vPosition;
                varying float vRegion; varying float vSurface;
                void main() {
                    vec3 radial=normalize(vPosition);
                    float facing=dot(radial,uEye);
                    if(uLines>1.5) {
                        if(facing<0.0 || uZoom>7.0) discard;
                        float glow=pow(1.0-facing,3.0)*smoothstep(0.0,0.22,facing)*0.68;
                        gl_FragColor=vec4(0.22,0.53,0.94,glow); return;
                    }
                    if(uLines>0.5) {
                        if(facing<0.0) discard;
                        gl_FragColor=vec4(vColor,1.0); return;
                    }
                    // Same equal-area grid as PlanetGeometry.tileIndex, not latitude-photo UVs.
                    vec2 grid=vec2(fract(atan(radial.x,radial.z)/6.283185307+0.5)*512.0,
                        clamp((1.0-radial.y)*256.0,0.0,511.999));
                    vec2 tile=floor(grid); vec2 local=fract(grid);
                    vec4 cell=texture2D(uTerrain,(tile+0.5)/512.0);
                    float kind=floor(cell.a*255.0+0.5);
                    float water=(kind>0.5 && kind<2.5)?1.0:0.0;
                    float dye=fract(sin(dot(tile,vec2(12.9898,78.233)))*43758.5453);
                    vec3 terrain=cell.rgb*(0.96+0.08*dye);
                    // Small woven fibres and running stitches become visible only when enlarged.
                    float close=smoothstep(3.0,12.0,uZoom);
                    float weave=sin(local.x*100.531)*sin(local.y*100.531);
                    terrain*=1.0+weave*0.035*close;
                    float edge=min(min(local.x,1.0-local.x),min(local.y,1.0-local.y));
                    float stitch=(1.0-smoothstep(0.025,0.055,edge))*step(0.45,fract((local.x+local.y)*12.0));
                    terrain=mix(terrain,terrain*0.78,stitch*0.35*close);
                    if(kind>0.5 && kind<1.5) {
                        float wave=pow(0.5+0.5*sin(local.y*18.85+sin(local.x*6.28)*0.55),12.0);
                        terrain=mix(terrain,vec3(0.51,0.75,0.80),wave*0.22*close);
                    } else if(kind>1.5 && kind<2.5) {
                        float bank=smoothstep(0.15,0.19,abs(local.x-0.5-0.16*sin(local.y*6.283)));
                        terrain=mix(terrain,vec3(0.64,0.78,0.49),bank*close);
                    } else if(kind>7.5 && kind<8.5) {
                        terrain*=1.0+0.065*sin(local.x*12.0+local.y*8.0+sin(local.y*6.0))*close;
                    }
                    float depth=clamp((1.0-length(vPosition))/0.082,0.0,1.0);
                    terrain=mix(terrain,terrain*(1.0-depth*0.65),water*uSeabed);
                    if(uCity>0.5) {
                        vec4 town=texture2D(uTown,(tile+0.5)/512.0);
                        float use=floor(town.r*255.0+0.5);float mask=floor(town.g*255.0+0.5);
                        if(use>0.5 && water<0.5) {
                            bool road=use==1.0 || use==16.0 || use==17.0 || use==15.0 || use==18.0;
                            if(road) {
                                float halfWidth=use==1.0?0.27:0.37;
                                bool west=mod(mask,2.0)>0.5;bool east=mod(floor(mask/2.0),2.0)>0.5;
                                bool north=mod(floor(mask/4.0),2.0)>0.5;bool south=mod(floor(mask/8.0),2.0)>0.5;
                                bool alongX=abs(local.y-0.5)<halfWidth && ((local.x<0.5 && west)||(local.x>=0.5 && east)||abs(local.x-.5)<halfWidth);
                                bool alongY=abs(local.x-0.5)<halfWidth && ((local.y<0.5 && north)||(local.y>=0.5 && south)||abs(local.y-.5)<halfWidth);
                                terrain=mix(terrain,vec3(.68,.64,.54),.75);
                                if(alongX||alongY) {
                                    terrain=vec3(.29,.32,.34)*(1.0+weave*.035*close);
                                    bool line=(alongX && abs(local.y-.5)<.016 && fract(local.x*3.0)<.60)||(alongY && abs(local.x-.5)<.016 && fract(local.y*3.0)<.60);
                                    if(line) terrain=vec3(.95,.86,.57);
                                    if(use==15.0 || use==18.0) {
                                        if(abs(abs(local.x-.5)-.12)<.025 || abs(abs(local.y-.5)-.12)<.025) terrain=vec3(.80,.83,.82);
                                    }
                                }
                            } else if(use>1.5 && use<4.5) {
                                vec3 zone=use==2.0?vec3(.44,.77,.48):(use==3.0?vec3(.38,.66,.84):vec3(.88,.70,.37));
                                terrain=mix(terrain,zone, town.b<.001?.66:.20);
                            } else terrain=mix(terrain,vec3(.69,.64,.52),.40);
                        }
                        if(uOverlay>.5 && town.a>.01) {
                            vec3 ink=town.a>.9?uOverlayColors[5]:(town.a>.7?uOverlayColors[4]:(town.a>.5?uOverlayColors[3]:(town.a>.3?uOverlayColors[2]:uOverlayColors[1])));
                            terrain=mix(terrain,ink,.65);
                        }
                        vec4 mark=texture2D(uMarks,(tile+.5)/512.0);
                        terrain=mix(terrain,vec3(.98,.78,.31),mark.g*.5);
                        if(edge<.055 && mark.g>.05) terrain=vec3(1.0,.91,.59);
                        terrain=mix(terrain,vec3(.078,.20,.48),mark.r*.82);
                        if(edge<.055 && mark.r>.05) terrain=vec3(.07,.19,.43);
                    }
                    vec3 color=mix(vColor,terrain,vSurface);
                    vec3 n=normalize(vNormal);
                    vec3 light=normalize(uEye+vec3(-0.8,0.85,0.45));
                    float diffuse=max(0.0,dot(n,light));
                    color*=mix(0.48+0.70*diffuse,0.32+0.90*diffuse,vSurface);
                    float spec=pow(max(0.0,dot(reflect(-light,n),uEye)),48.0)*0.28;
                    color+=vec3(0.57,0.75,0.87)*spec*water*vSurface*(1.0-uSeabed);
                    float haze=pow(1.0-max(0.0,facing),3.0)*0.24/(1.0+uZoom*0.08);
                    color=mix(color,vec3(0.30,0.54,0.77),haze);
                    float region=mix(vRegion,tile.y*512.0+tile.x,vSurface);
                    if(abs(region-uSelected)<0.1 && uZoom>6.0) color=mix(color,vec3(.078,.20,.48),0.45);
                    gl_FragColor=vec4(color,1.0);
                }
            """.trimIndent())
            glAttachShader(program,vs); glAttachShader(program,fs); glLinkProgram(program)
            val linked=IntArray(1); glGetProgramiv(program,GL_LINK_STATUS,linked,0)
            check(linked[0]!=0) { glGetProgramInfoLog(program) }
            glDeleteShader(vs); glDeleteShader(fs)
            val buffers=IntArray(3); glGenBuffers(3,buffers,0); vbo=buffers[0]; borderVbo=buffers[1]; atmosphereVbo=buffers[2]
            val halo=PlanetGeometry.atmosphere();upload(atmosphereVbo,halo.vertices);atmosphereCount=halo.count
            val textures=IntArray(1);glGenTextures(1,textures,0);texture=textures[0]
            glBindTexture(GL_TEXTURE_2D,texture)
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST)
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST)
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_REPEAT)
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
            uploadedTerrainRevision=Int.MIN_VALUE
            val auxiliary=IntArray(2);glGenTextures(2,auxiliary,0);townTexture=auxiliary[0];markTexture=auxiliary[1]
            for(id in auxiliary) {
                glBindTexture(GL_TEXTURE_2D,id)
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST)
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_REPEAT);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE)
            }
            townDirty=true;marksDirty=true
            if(cityRendering) townModels.create()
            rebuild=true
        }
        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { w=width; h=height; glViewport(0,0,w,h) }
        private val uploadScratch=ByteBuffer.allocateDirect(256*1024).order(ByteOrder.nativeOrder()).asFloatBuffer()
        private fun upload(id: Int, values: FloatArray) {
            glBindBuffer(GL_ARRAY_BUFFER,id)
            glBufferData(GL_ARRAY_BUFFER,values.size*4,null,GL_STATIC_DRAW)
            var offset=0
            while(offset<values.size) {
                val count=min(uploadScratch.capacity(),values.size-offset)
                uploadScratch.clear();uploadScratch.put(values,offset,count);uploadScratch.flip()
                glBufferSubData(GL_ARRAY_BUFFER,offset*4,count*4,uploadScratch)
                offset+=count
            }
        }
        private fun bind(id: Int) {
            glBindBuffer(GL_ARRAY_BUFFER,id)
            for((name,offset,count) in listOf(Triple("aPosition",0,3),Triple("aNormal",3,3),Triple("aColor",6,3),Triple("aRegion",9,1),Triple("aSurface",10,1))) {
                val a=glGetAttribLocation(program,name)
                glEnableVertexAttribArray(a); glVertexAttribPointer(a,count,GL_FLOAT,false,PlanetGeometry.STRIDE*4,offset*4)
            }
        }
        private var borderSelection=-2
        private var borderBrush=-1
        private fun borders() {
            val values=ArrayList<Float>()
            fun point(u: Float, v: Float, selectedBorder: Boolean) {
                val p=landscape.point(u*Planet.SIZE,v*Planet.SIZE,0.0008f)
                val c=if(selectedBorder) floatArrayOf(.07f,.19f,.43f) else floatArrayOf(0.62f,0.75f,0.70f)
                values.addAll(listOf(p.x,p.y,p.z,p.x,p.y,p.z,c[0],c[1],c[2],-1f,0f))
            }
            fun rectangle(x0: Int,y0: Int,width: Int,highlight: Boolean,height: Int=width) {
                for(edge in 0..3) for(i in 0 until 64) {
                    if(!highlight && i%3==2) continue
                    for(j in 0..1) {
                        val t=(i+j)/64f
                        val x=x0+when(edge) { 0,2 -> t*width; 1 -> width.toFloat(); else -> 0f }
                        val y=y0+when(edge) { 1,3 -> t*height; 2 -> height.toFloat(); else -> 0f }
                        point(x/Planet.SIZE,y/Planet.SIZE,highlight)
                    }
                }
            }
            val y0=(selected/Planet.SIZE-brushSize/2).coerceAtLeast(0)
            val y1=(selected/Planet.SIZE-brushSize/2+brushSize).coerceAtMost(Planet.SIZE)
            rectangle(selected%Planet.SIZE-brushSize/2,y0,brushSize,true,y1-y0)
            val data=values.toFloatArray(); upload(borderVbo,data); borderCount=data.size/PlanetGeometry.STRIDE; borderSelection=selected; borderBrush=brushSize
        }
        private val terrainScratch=ByteBuffer.allocateDirect(512*512*4).order(ByteOrder.nativeOrder())
        private var uploadedTerrainRevision=Int.MIN_VALUE
        private fun uploadTerrain() {
            terrainScratch.clear();terrainScratch.put(PlanetGeometry.terrainTexels(snapshot));terrainScratch.flip()
            glBindTexture(GL_TEXTURE_2D,texture)
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,512,512,0,GL_RGBA,GL_UNSIGNED_BYTE,terrainScratch)
            uploadedTerrainRevision=snapshot.revision
        }
        override fun onDrawFrame(gl: GL10?) {
            glActiveTexture(GL_TEXTURE0)
            if(uploadedTerrainRevision!=snapshot.revision) uploadTerrain()
            fun bytesTexture(id: Int,bytes: ByteArray) {
                terrainScratch.clear();terrainScratch.put(bytes);terrainScratch.flip();glBindTexture(GL_TEXTURE_2D,id)
                glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,512,512,0,GL_RGBA,GL_UNSIGNED_BYTE,terrainScratch)
            }
            if(townDirty) { bytesTexture(townTexture,town?.surface ?: markBytes);townDirty=false }
            if(marksDirty) { bytesTexture(markTexture,markBytes);marksDirty=false }
            if(rebuild) { landscape=PlanetGeometry.Landscape(snapshot); mesh=PlanetGeometry.build(snapshot,camera,landscape,!cityRendering); builtDetailKey=detailKey(); upload(vbo,mesh!!.vertices); rebuild=false; borderSelection=-2 }
            if(borderSelection!=selected || borderBrush!=brushSize) borders()
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
            val cam=camera; val eye=cam.eye; val up=cam.up; val aspect=w.toFloat()/h.coerceAtLeast(1); val hh=cam.halfHeight(aspect)
            Matrix.orthoM(projection,0,-hh*aspect,hh*aspect,-hh,hh,1f,10f)
            val focus=cam.focus*cam.focusRadius
            Matrix.setLookAtM(view,0,focus.x+eye.x*5,focus.y+eye.y*5,focus.z+eye.z*5,focus.x,focus.y,focus.z,up.x,up.y,up.z)
            Matrix.multiplyMM(mvp,0,projection,0,view,0)
            glUseProgram(program)
            glUniformMatrix4fv(glGetUniformLocation(program,"uMvp"),1,false,mvp,0)
            glUniform3f(glGetUniformLocation(program,"uEye"),eye.x,eye.y,eye.z)
            glUniform1f(glGetUniformLocation(program,"uSelected"),selected.toFloat())
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture)
            glUniform1i(glGetUniformLocation(program,"uTerrain"),0)
            glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,townTexture);glUniform1i(glGetUniformLocation(program,"uTown"),1)
            glActiveTexture(GL_TEXTURE2);glBindTexture(GL_TEXTURE_2D,markTexture);glUniform1i(glGetUniformLocation(program,"uMarks"),2)
            glUniform1f(glGetUniformLocation(program,"uCity"),if(cityRendering) 1f else 0f)
            glUniform1f(glGetUniformLocation(program,"uOverlay"),if(overlayEnabled) 1f else 0f)
            town?.let { glUniform3fv(glGetUniformLocation(program,"uOverlayColors"),6,it.overlayColors,0) }
            glUniform1f(glGetUniformLocation(program,"uSeabed"),if(snapshot.seabed) 1f else 0f)
            glUniform1f(glGetUniformLocation(program,"uZoom"),cam.zoom)
            glUniform1f(glGetUniformLocation(program,"uLines"),0f)
            bind(vbo); glDrawArrays(GL_TRIANGLES,0,mesh?.count ?: 0)
            glUniform1f(glGetUniformLocation(program,"uLines"),2f)
            glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glDepthMask(false)
            bind(atmosphereVbo);glDrawArrays(GL_TRIANGLES,0,atmosphereCount)
            glDepthMask(true);glDisable(GL_BLEND)
            glUniform1f(glGetUniformLocation(program,"uLines"),1f)
            glDisable(GL_DEPTH_TEST);bind(borderVbo);glLineWidth(1.5f);glDrawArrays(GL_LINES,0,borderCount);glEnable(GL_DEPTH_TEST)
            town?.let {
                townModels.draw(it,cam,landscape,mvp,w,h,movers)
                cityModelsDrawn=townModels.drawn;cityVehiclesDrawn=townModels.vehiclesDrawn;cityPedestriansDrawn=townModels.pedestriansDrawn
            }
            if(townModels.pending) requestRender()
            cityModelsPending=townModels.pending
            renderedZoom=cam.zoom
            frames++
            renderedRevision=snapshot.revision
        }
    }
}
