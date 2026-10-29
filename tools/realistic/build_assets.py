"""Blender 5.x: authored PBR city models -> orthographic RGBA sprites + landmark GLBs.
Coordinate unit = one tile. Camera elevation 30 degrees produces exact 2:1 diamonds.
Run: nix shell nixpkgs#blender --command blender -b -t 8 -P tools/realistic/build_assets.py -- [asset IDs]
"""
import bpy, math, random, json, sys
from pathlib import Path
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[2]
FELT = '--felt' in sys.argv
OUT=ROOT/('artifacts/felt/rendered' if FELT else 'artifacts/realistic/rendered'); OUT.mkdir(parents=True,exist_ok=True)
MODELS=ROOT/('visual-source/felt-models' if FELT else 'visual-source/models'); MODELS.mkdir(parents=True,exist_ok=True)
P=512/math.sqrt(2)
selected=[arg for arg in sys.argv[sys.argv.index('--')+1:] if arg != '--felt'] if '--' in sys.argv else []
GENERATION='felt-city-v2' if FELT else 'photographic-v1'
sys.path.insert(0,str(Path(__file__).parent))
if FELT: import felt_materials
bpy.ops.wm.read_factory_settings(use_empty=True)
scene=bpy.context.scene
scene.render.engine='CYCLES';scene.cycles.device='CPU';scene.cycles.samples=24
scene.cycles.use_denoising=True;scene.cycles.max_bounces=5
scene.render.film_transparent=True
scene.render.image_settings.file_format='PNG';scene.render.image_settings.color_mode='RGBA'
scene.view_settings.view_transform='AgX'
world=bpy.data.worlds.new('Soft blue daylight');world.use_nodes=True
world.node_tree.nodes['Background'].inputs['Color'].default_value=(0.67,0.79,1,1)
world.node_tree.nodes['Background'].inputs['Strength'].default_value=0.65
scene.world=world
bpy.ops.object.light_add(type='SUN',location=(-3,-4,7));sun=bpy.context.object
sun.rotation_euler=(math.radians(24),math.radians(-28),math.radians(-25));sun.data.energy=2.7;sun.data.angle=0.15
bpy.ops.object.camera_add();camera=bpy.context.object;camera.data.type='ORTHO';scene.camera=camera
static={sun,camera}
if FELT: felt_materials.warm_light(scene,sun)

def material(name,color,rough=.65,metal=0,texture=None):
    if FELT: return felt_materials.material(name,color)
    m=bpy.data.materials.new(name);m.diffuse_color=(*color,1);m.use_nodes=True
    nodes=m.node_tree.nodes;links=m.node_tree.links;bs=nodes.get('Principled BSDF')
    bs.inputs['Base Color'].default_value=(*color,1);bs.inputs['Roughness'].default_value=rough;bs.inputs['Metallic'].default_value=metal
    if texture:
        for channel,slot in [('Diffuse','Base Color'),('Rough','Roughness'),('nor_gl','Normal')]:
            files=list((ROOT/'visual-source/textures').glob(texture+'_'+channel+'.*'))
            if not files:continue
            tex=nodes.new('ShaderNodeTexImage');tex.image=bpy.data.images.load(str(files[0]),check_existing=True)
            if channel!='Diffuse':tex.image.colorspace_settings.name='Non-Color'
            coord=nodes.new('ShaderNodeTexCoord');mapping=nodes.new('ShaderNodeMapping')
            mapping.inputs['Scale'].default_value=(2,2,2)
            links.new(coord.outputs['Generated'],mapping.inputs['Vector']);links.new(mapping.outputs['Vector'],tex.inputs['Vector']);tex.projection='BOX';tex.projection_blend=.12
            if channel=='Normal':
                norm=nodes.new('ShaderNodeNormalMap');norm.inputs['Strength'].default_value=.35
                links.new(tex.outputs['Color'],norm.inputs['Color']);links.new(norm.outputs['Normal'],bs.inputs['Normal'])
            else:
                if channel=='Diffuse' and (name.startswith('Paint ') or name=='Lime plaster'):
                    tint=nodes.new('ShaderNodeMixRGB');tint.blend_type='MULTIPLY';tint.inputs[0].default_value=.82;tint.inputs[2].default_value=(*color,1)
                    ramp=nodes.new('ShaderNodeValToRGB');ramp.color_ramp.elements[0].color=(.64,.64,.64,1);ramp.color_ramp.elements[1].color=(1,1,1,1)
                    links.new(tex.outputs['Color'],ramp.inputs[0]);links.new(ramp.outputs['Color'],tint.inputs[1]);links.new(tint.outputs[0],bs.inputs[slot])
                else:links.new(tex.outputs['Color'],bs.inputs[slot])
    return m
stone=material('Warm limestone',(.7,.65,.53),texture='sandstone_blocks_04')
plaster=material('Lime plaster',(.85,.81,.72),texture='white_plaster_rough_02')
brick=material('Weathered fired brick',(.42,.19,.11),texture='red_bricks_04')
slate=material('Individual slate tiles',(.18,.21,.23),texture='grey_roof_tiles')
grass=material('Meadow soil',(.28,.38,.12),texture='aerial_grass_rock')
asphalt=material('Asphalt',(.16,.18,.19),texture='aerial_asphalt_01')
sand=material('Sand',(.7,.61,.4),texture='coast_sand_02')
concrete=material('Concrete',(.55,.57,.54),.8)
white=material('Ivory stone',(.82,.83,.77),.65)
dark=material('Ironwork',(.045,.065,.067),.36,.7)
wood=material('Timber',(.16,.095,.045),.78)
red=material('Vermilion',(.64,.12,.052),.32,.25)
gold=material('Brass',(.65,.40,.08),.27,.7)
copper=material('Oxidised copper',(.14,.36,.29),.55,.3)
glass=material('Reflected sky glass',(.14,.27,.32),.14,.62)
lightglass=material('Pale reflected sky',(.34,.51,.56),.16,.48)
water=material('Water',(.035,.23,.30),.13,.42)
foliage=[material('Leaves '+str(i),c,.92) for i,c in enumerate([(.075,.20,.035),(.14,.28,.055),(.23,.34,.08),(.32,.40,.10)])]
facades=[material('Paint '+str(i),c,.82,texture='white_plaster_rough_02') for i,c in enumerate([(.73,.58,.36),(.67,.39,.30),(.52,.66,.62),(.80,.75,.59),(.67,.63,.47),(.67,.47,.44)])]

def finish(o,mat,bevel=0):
    o.data.materials.append(mat)
    if bevel:
        mod=o.modifiers.new('Soft masonry edges','BEVEL');mod.width=bevel;mod.segments=4 if FELT else 2
        mod=o.modifiers.new('Weighted face normals','WEIGHTED_NORMAL')
    return o

def box(loc,size,mat,bevel=.006):
    bpy.ops.mesh.primitive_cube_add(size=1,location=loc);o=bpy.context.object;o.scale=size
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    if FELT and bevel: bevel=min(min(size)*.28,max(bevel,.023))
    return finish(o,mat,bevel)

def cyl(loc,r,depth,mat,verts=24):
    bpy.ops.mesh.primitive_cylinder_add(vertices=verts,radius=r,depth=depth,location=loc)
    o=finish(bpy.context.object,mat,.002)
    for p in o.data.polygons:p.use_smooth=len(p.vertices)==4
    return o

def sphere(loc,scale,mat,segments=20):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segments,ring_count=12,radius=1,location=loc)
    o=bpy.context.object;o.scale=scale
    for p in o.data.polygons:p.use_smooth=True
    if FELT:
        dis=o.modifiers.new('Soft wool stuffing','DISPLACE');dis.texture=bpy.data.textures.get('Felt surface') or bpy.data.textures.new('Felt surface',type='CLOUDS');dis.texture.noise_scale=.12;dis.strength=.025
    return finish(o,mat)

def rod(a,b,r,mat):
    d=Vector(b)-Vector(a);o=cyl((Vector(a)+Vector(b))/2,r,d.length,mat,12);o.rotation_euler=d.to_track_quat('Z','Y').to_euler();return o

def mesh(name,verts,faces,mat):
    m=bpy.data.meshes.new(name);m.from_pydata(verts,[],faces);m.update()
    o=bpy.data.objects.new(name,m);scene.collection.objects.link(o);return finish(o,mat)

def roof(x,y,z,w,d,h,mat):
    verts=[(x-w/2,y-d/2,z),(x+w/2,y-d/2,z),(x+w/2,y+d/2,z),(x-w/2,y+d/2,z),(x,y-d/2,z+h),(x,y+d/2,z+h)]
    o=mesh('Pitched roof',verts,[(0,1,4),(3,5,2),(0,4,5,3),(4,1,2,5)],mat)
    if FELT:
        mod=o.modifiers.new('Padded roof cloth','SOLIDIFY');mod.thickness=.024
        mod=o.modifiers.new('Rounded cloth folds','BEVEL');mod.width=.018;mod.segments=4
        for a,b in [(0,4),(4,1),(3,5),(5,2),(0,3),(1,2)]: seam(verts[a],verts[b],white)
    return o

def seam(a,b,mat,r=.0035):
    delta=Vector(b)-Vector(a);count=max(1,int(delta.length/.040))
    for i in range(count):
        start=Vector(a)+delta*((i+.18)/count);end=Vector(a)+delta*((i+.62)/count)
        start.z+=.002;end.z+=.002
        rod(start,end,r,mat)


plant_templates={}
def botanical(name,x,y,height,seed,z=.025):
    if FELT:
        rng=random.Random(seed)
        if name=='tree_small_02':
            cyl((x,y,z+height*.29),height*.045,height*.58,wood,16)
            for dx,dy,h,w in [(-.13,0,.62,.26),(.13,.04,.66,.25),(0,-.1,.76,.29),(0,.08,.87,.23)]:
                sphere((x+height*dx,y+height*dy,z+height*h),(height*w,height*w,height*w*1.1),foliage[seed%4],24)
        else:
            for dx,dy in [(-.13,0),(.13,0),(0,.14)]:
                sphere((x+height*dx,y+height*dy,z+height*.34),(height*.36,height*.36,height*.37),foliage[(seed+1)%4],20)
        return
    from mathutils import Matrix
    if name not in plant_templates:
        before=set(scene.objects)
        bpy.ops.import_scene.gltf(filepath=str(ROOT/'visual-source/vegetation'/name/(name+'.gltf')))
        imported=list(set(scene.objects)-before)
        meshes=[o for o in imported if o.type=='MESH']
        for o in meshes:
            bpy.context.view_layer.objects.active=o
            if len(o.data.polygons)>200000:
                mod=o.modifiers.new('Sprite LOD','DECIMATE');mod.ratio=.12
                bpy.ops.object.modifier_apply(modifier=mod.name)
        bpy.context.view_layer.update()
        points=[o.matrix_world@Vector(v) for o in meshes for v in o.bound_box]
        lo=min(v.z for v in points);hi=max(v.z for v in points)
        center=Vector(((min(v.x for v in points)+max(v.x for v in points))/2,(min(v.y for v in points)+max(v.y for v in points))/2,lo))
        templates=[]
        for o in meshes:
            o.data.use_fake_user=True
            templates.append((o.data,Matrix.Scale(1/(hi-lo),4)@Matrix.Translation(-center)@o.matrix_world.copy()))
        for o in imported:bpy.data.objects.remove(o,do_unlink=True)
        plant_templates[name]=templates
    for data,matrix in plant_templates[name]:
        o=bpy.data.objects.new('Botanical '+name,data);scene.collection.objects.link(o)
        o.matrix_world=Matrix.Translation((x,y,z))@Matrix.Rotation(seed*1.83,4,'Z')@Matrix.Scale(height,4)@matrix

def tree(x,y,scale=1,seed=1):
    botanical('tree_small_02',x,y,.52*scale,seed)

def base(span=1,mat=concrete):
    box((0,0,.006),(span,span,.022),mat,.006)

def window_wall(x,y,z,w,h,side=False):
    # Recess, frame, reflective glazing, vertical and horizontal mullions.
    sz=(.018,w,h) if side else (w,.018,h)
    box((x,y,z),sz,white,.002)
    pos=(x+.011,y,z) if side else (x,y+.011,z)
    box(pos,(.018,w-.018,h-.018) if side else (w-.018,.018,h-.018),glass,.001)
    box((x+.021,y,z) if side else (x,y+.021,z),(.014,.008,h) if side else (.008,.014,h),white,.001)
    if FELT:
        # Short contrasting embroidery around the blue felt window appliqué.
        for offset in [-w/2+.011,w/2-.011]:
            if side: seam((x+.025,y+offset,z-h/2+.006),(x+.025,y+offset,z+h/2-.015),wood,.0018)
            else: seam((x+offset,y+.025,z-h/2+.006),(x+offset,y+.025,z+h/2-.015),wood,.0018)

def building(kind,stage,variant):
    rng=random.Random(variant+stage*80)
    base(mat=stone)
    if kind=='ZONE_I':
        width=.76;height=.23+stage*.08
        box((0,0,height/2+.02),(width,.67,height),brick)
        # Sawtooth roof glazing, vents, industrial pipe work and loading dock.
        for i in range(3):roof(-.24+i*.24,0,height+.025,.245,.70,.105,slate)
        for i in range(stage+1):
            x=-.28+i*.19
            cyl((x,-.20,height+.25),.035,.50,red)
            cyl((x,-.20,height+.50),.042,.028,dark)
        box((.13,.35,.16),(.27,.018,.21),dark)
        for i in range(4):window_wall(-.29+i*.18,.34,.21,.12,.10)
        box((-.20,.37,.055),(.27,.15,.07),concrete)
        for i in range(3):box((.34,-.15+i*.2,height+.10),(.09,.13,.13),concrete)
        return
    floors=[0,2,4,7][stage]
    if kind=='ZONE_C':floors+=1
    height=.15*floors
    w=.66 if variant%3 else .73;d=.65
    exterior=facades[variant] if kind=='ZONE_R' or variant<4 else white
    if variant==4 and stage>=2:
        # Stepped glass office; balconies and rooftop planting retain the previous city's character.
        box((0,0,height/2+.025),(w,d,height),lightglass)
        for level in range(floors+1):box((0,0,.035+level*.15),(w+.025,d+.025,.018),white,.003)
        for x in [-.25,-.12,0,.12,.25]:box((x,d/2+.009,height/2),(.008,.02,height),dark,.001)
        for y in [-.24,-.10,.04,.18,.30]:box((w/2+.009,y,height/2),(.02,.008,height),dark,.001)
    else:
        box((0,0,height/2+.025),(w,d,height),exterior)
        for level in range(floors):
            z=.13+level*.15
            box((0,0,z+.08),(w+.016,d+.016,.018),white,.002)
            for x in [-.22,0,.22]:window_wall(x,d/2+.006,z,.125,.102)
            for y in [-.20,.015,.22]:window_wall(w/2+.006,y,z,.12,.10,True)
            if variant%3==1 and level>0:
                for x in [-.22,.22]:
                    box((x,d/2+.055,z-.045),(.17,.12,.015),stone)
                    for k in range(5):rod((x-.075+k*.037,d/2+.11,z-.04),(x-.075+k*.037,d/2+.11,z+.025),.003,dark)
                    rod((x-.083,d/2+.11,z+.028),(x+.083,d/2+.11,z+.028),.004,dark)
    z=height+.035
    if variant%3==0:
        roof(0,0,z,w+.06,d+.05,.22,slate)
        # Dormer windows, chimney and guttering.
        for x in [-.20,.20]:
            box((x,.18,z+.07),(.14,.18,.15),exterior)
            roof(x,.18,z+.145,.17,.20,.055,slate)
            window_wall(x,.28,z+.07,.09,.09)
        box((-.23,-.16,z+.23),(.08,.09,.26),brick)
    elif variant%3==1:
        roof(0,0,z,w+.035,d+.035,.15,red if variant==1 else slate)
        box((.24,-.17,z+.19),(.075,.08,.24),brick)
    else:
        box((0,0,z),(w+.02,d+.02,.028),slate)
        for y in [-d/2,d/2]:box((0,y,z+.04),(w,.023,.08),white)
        for x in [-w/2,w/2]:box((x,0,z+.04),(.023,d,.08),white)
        box((-.14,-.12,z+.07),(.15,.12,.10),concrete)
        before=set(scene.objects)
        tree(.17,.1,.36,variant)
        for o in set(scene.objects)-before:o.location.z+=z
    box((0,d/2+.012,.085),(.10,.022,.14),dark)
    if kind=='ZONE_C' or (stage>=2 and variant%2):
        box((0,d/2+.013,.20),(.59,.028,.065),dark)
        aw=box((0,d/2+.08,.18),(.62,.17,.016),facades[(variant+2)%6]);aw.rotation_euler.x=.14
        for i in range(6):box((-.25+i*.1,d/2+.085,.189),(.045,.17,.005),white,.001)
    # A tree and a planter add human scale without covering the façade.
    tree(-.39,.33,.6,variant+3)
    box((.33,.37,.055),(.12,.12,.09),stone)
    botanical('shrub_02',.33,.37,.12,variant,z=.10)

def utility(kind):
    if kind not in ['POWER_COAL','INCINERATOR','RECYCLING']:
        base(mat=grass if kind in ['PARK','GROVE','FARM','POWER_WIND','POWER_SOLAR'] else concrete)
    if kind in ['PARK','GROVE']:
        if kind=='PARK':
            cyl((.03,.03,.035),.20,.04,stone,48);cyl((.03,.03,.06),.17,.015,water,48)
            cyl((.03,.03,.11),.032,.11,white);sphere((.03,.03,.19),(.06,.06,.018),water)
            for y in [-.29,.29]:box((0,y,.065),(.26,.06,.035),wood)
        for i,(x,y) in enumerate([(-.3,-.24),(.28,-.28),(-.3,.28),(.30,.30)]):tree(x,y,.9,i+5)
    elif kind=='POWER_WIND':
        cyl((0,0,.52),.036,1,white);sphere((0,0,1.05),(.07,.07,.07),white)
        for a in [0,math.tau/3,math.tau*2/3]:
            rod((0,-.06,1.05),(math.cos(a)*.32,-.06,1.05+math.sin(a)*.32),.02,white)
    elif kind=='POWER_SOLAR':
        for x in [-.30,0,.30]:
            for y in [-.27,.10]:
                o=box((x,y,.10),(.25,.32,.025),glass);o.rotation_euler.x=.25
                for i in range(3):box((x-.08+i*.08,y,.123),(.003,.31,.002),white,0)
    elif kind=='POWER_LINE':
        for x in [-.16,.16]:rod((x,0,0),(x*.45,0,.8),.014,dark)
        for z in [.25,.5,.7]:rod((-.14,0,z),(.14,0,z),.012,dark)
        rod((-.28,0,.77),(.28,0,.77),.016,dark)
        for x in [-.25,0,.25]:rod((x,-.50,.80),(x,.50,.80),.004,dark)
    elif kind=='WATER_TOWER':
        for x in [-.17,.17]:
            for y in [-.17,.17]:rod((x,y,.02),(x,y,.55),.021,white)
        cyl((0,0,.56),.25,.30,white);sphere((0,0,.72),(.25,.25,.08),white)
        cyl((0,0,.82),.022,.15,dark)
    elif kind in ['WATER_PLANT','SEWAGE_PLANT']:
        for x in [-.24,.24]:
            for y in [-.22,.20]:
                cyl((x,y,.06),.185,.10,white);cyl((x,y,.113),.16,.009,water)
                rod((x-.16,y,.13),(x+.16,y,.13),.006,dark)
        box((.25,-.37,.13),(.30,.15,.20),brick)
    elif kind=='FARM':
        for x in range(11):
            for y in range(9):sphere((-.44+x*.08,-.43+y*.07,.035),(.025,.035,.032),foliage[(x+y)%4],8)
        box((.16,.28,.10),(.39,.25,.18),red);roof(.16,.28,.20,.43,.29,.12,slate)
    elif kind in ['BUS_STOP','SUBWAY_STATION']:
        for x in [-.24,.24]:rod((x,.08,.02),(x,.08,.27),.009,dark)
        box((0,.08,.28),(.61,.21,.035),white)
        box((0,.02,.15),(.55,.01,.24),lightglass)
        box((0,.08,.085),(.41,.065,.025),wood)
        cyl((.35,.09,.18),.010,.35,dark);box((.35,.09,.36),(.08,.015,.10),red)
    elif kind=='AIRPORT':
        box((0,-.23,.11),(.80,.32,.19),lightglass);box((0,-.23,.22),(.86,.36,.025),white)
        box((-.34,-.2,.32),(.12,.12,.40),white);box((-.34,-.2,.56),(.17,.17,.10),glass)
        box((.08,.23,.04),(.14,.63,.09),white);sphere((.08,.47,.06),(.075,.12,.055),white)
        mesh('Aircraft wings',[(-.30,.13,.075),(.46,.13,.075),(.20,.30,.075),(-.04,.30,.075)],[(0,1,2,3)],white)
    elif kind=='SEAPORT':
        base(mat=wood)
        for i in range(4):box((-.28+i*.18,-.15,.10),(.15,.44,.17),facades[i])
        for y in [-.25,.27]:rod((.35,y,0),(.35,y,.64),.018,gold)
        rod((.35,-.28,.64),(.35,.49,.64),.026,gold);rod((.35,.40,.64),(.35,.40,.25),.006,dark)
    elif kind=='LANDFILL':
        for i in range(18):
            rng=random.Random(i);sphere((rng.uniform(-.36,.36),rng.uniform(-.36,.36),.09),(.13,.16,.12),[stone,concrete,wood][i%3],12)
    elif kind in ['POWER_COAL','INCINERATOR','RECYCLING']:
        building('ZONE_I',2,3)
        if kind=='POWER_COAL':
            for x in [-.22,.23]:cyl((x,.0,.36),.14,.6,concrete)
    else:
        floors=4 if kind=='HOSPITAL' else 2
        height=floors*.16
        mat=brick if kind=='SCHOOL' else white
        box((0,0,height/2+.03),(.73,.66,height),mat)
        for level in range(floors):
            for x in [-.26,-.09,.09,.26]:window_wall(x,.34,.12+level*.16,.12,.11)
            for y in [-.22,0,.22]:window_wall(.375,y,.12+level*.16,.12,.11,True)
        roof(0,0,height+.04,.79,.72,.13,slate) if kind=='SCHOOL' else box((0,0,height+.045),(.78,.71,.035),slate)
        box((0,.355,.10),(.12,.03,.17),glass)
        sign=red if kind in ['FIRE','HOSPITAL','CLINIC'] else dark
        box((0,.36,.27),(.28,.022,.06),sign)
        if kind in ['HOSPITAL','CLINIC']:
            box((.22,.36,height+.14),(.045,.025,.16),red);box((.22,.36,height+.14),(.13,.025,.045),red)
        if kind=='FIRE':
            for x in [-.22,.15]:box((x,.35,.115),(.25,.03,.19),red)
        tree(-.4,.29,.7,3)

def arch(x,y,z,width,height,depth,mat):
    # Actual open arch, built from jambs and 16 voussoir wedges.
    thick=width*.13;r=width/2-thick;spring=height-r
    for sign in [-1,1]:box((x+sign*(width/2-thick/2),y,z+spring/2),(thick,depth,spring),mat,.002)
    for i in range(16):
        a=i*math.pi/16;b=(i+1)*math.pi/16
        points=[]
        for yy in [y-depth/2,y+depth/2]:
            for rr,angle in [(r,a),(r+thick,a),(r+thick,b),(r,b)]:points.append((x+rr*math.cos(angle),yy,z+spring+rr*math.sin(angle)))
        mesh('Arch stone',points,[(0,1,2,3),(4,7,6,5),(0,4,5,1),(3,2,6,7),(1,5,6,2),(0,3,7,4)],mat)

def monument(kind):
    base(1.88,stone)
    if kind=='TOKYO_TOWER':
        tiers=[(0,.64),(.6,.34),(1.13,.19),(1.70,.105),(2.23,.035)]
        for j,((z,r),(zz,rr)) in enumerate(zip(tiers,tiers[1:])):
            mat=red if j%2==0 else white
            for x,y in [(-1,-1),(1,-1),(1,1),(-1,1)]:
                rod((x*r,y*r,z),(x*rr,y*rr,zz),.019,mat)
                rod((x*r,y*r,z),(-x*rr,y*rr,zz),.009,mat)
                rod((x*r,y*r,z),(x*rr,-y*rr,zz),.009,mat)
            for k in range(1,4):
                f=k/4;rad=r+(rr-r)*f
                for s in [-1,1]:
                    rod((-rad,s*rad,z+(zz-z)*f),(rad,s*rad,z+(zz-z)*f),.007,mat)
                    rod((s*rad,-rad,z+(zz-z)*f),(s*rad,rad,z+(zz-z)*f),.007,mat)
        for z,r in [(1.05,.27),(1.8,.13)]:
            box((0,0,z),(r*2,r*2,.12),glass);box((0,0,z+.066),(r*2+.035,r*2+.035,.018),white)
        rod((0,0,2.20),(0,0,2.72),.022,red)
        for z in [2.28,2.42,2.56]:cyl((0,0,z),.025,.07,white)
        box((0,0,.095),(.81,.80,.18),white)
    elif kind=='ARC_DE_TRIOMPHE':
        # A single solid with real intersecting arched passages avoids coplanar jamb faces.
        body=box((0,0,.75),(1.42,.82,1.40),stone,0)
        def subtract(cutter):
            bpy.context.view_layer.objects.active=body
            mod=body.modifiers.new('Masonry opening','BOOLEAN');mod.operation='DIFFERENCE';mod.solver='EXACT';mod.object=cutter
            bpy.ops.object.modifier_apply(modifier=mod.name)
            bpy.data.objects.remove(cutter,do_unlink=True)
        subtract(box((0,0,.37),(.64,1.3,.83),dark,0))
        o=cyl((0,0,.785),.32,1.3,dark,64);o.rotation_euler.x=math.pi/2;subtract(o)
        # The smaller transverse arch is visible from this quarter view as in the reference photograph.
        subtract(box((0,0,.255),(1.8,.30,.50),dark,0))
        o=cyl((0,0,.50),.15,1.8,dark,48);o.rotation_euler.y=math.pi/2;subtract(o)
        for z,w,d in [(1.15,1.49,.88),(1.46,1.53,.92),(1.50,1.49,.89)]:box((0,0,z),(w,d,.035),white)
        for x in [-.52,.52]:
            for i in range(5):
                xx=x+(i%3-1)*.055;zz=.33+i//3*.18
                sphere((xx,.427,zz+.085),(.018,.013,.021),stone,12)
                sphere((xx,.427,zz+.03),(.032,.022,.048),stone,12)
                for dx in [-.013,.013]:rod((xx+dx,.428,zz+.018),(xx+dx*1.4,.428,zz-.053),.008,stone)
        for i in range(19):box((-.70+i*.078,.448,1.405),(.027,.02,.035),white,.001)
    elif kind=='COLOSSEUM':
        # Elliptical ring, genuine arcades, open arena and the broken upper wall.
        for level in range(3):
            for i in range(32):
                a=i*math.tau/32;x=math.cos(a)*.76;y=math.sin(a)*.58
                before=set(scene.objects);arch(0,0,.04+level*.24,.14,.23,.11,stone)
                for o in set(scene.objects)-before:
                    from mathutils import Matrix
                    o.matrix_world=Matrix.Translation((x,y,0))@Matrix.Rotation(a+math.pi/2,4,'Z')@o.matrix_world
                # continuous entablature segment
                o=box((x,y,.28+level*.24),(.17,.14,.025),stone,.001);o.rotation_euler.z=a+math.pi/2
                if level==2 and i<14:
                    o=box((x,y,.85),(.17,.12,.14),stone,.001);o.rotation_euler.z=a+math.pi/2
        for ring in range(4):
            for i in range(40):
                a=i*math.tau/40;r=.34+ring*.07
                o=box((math.cos(a)*r,math.sin(a)*r*.77,.06+ring*.065),(.105,.08,.035),stone,.001);o.rotation_euler.z=a+math.pi/2
    elif kind=='BIG_BEN':
        box((0,0,.71),(.46,.46,1.40),stone)
        for z in [.22,.46,.70,.94,1.18,1.42]:box((0,0,z),(.49,.49,.035),white)
        for side in [False,True]:
            for k in range(6):
                if side:box((.24,-.195+k*.078,.73),(.021,.023,1.32),white,.002)
                else:box((-.195+k*.078,.24,.73),(.023,.021,1.32),white,.002)
        box((0,0,1.58),(.53,.53,.35),stone)
        for side in [False,True]:
            o=cyl((.277,0,1.60) if side else (0,.277,1.60),.193,.015,white,64)
            o.rotation_euler.y=math.pi/2 if side else 0;o.rotation_euler.x=0 if side else math.pi/2
            for i in range(12):
                a=i*math.tau/12;u=math.sin(a)*.157;v=math.cos(a)*.157
                box((.291,u,1.6+v) if side else (u,.291,1.6+v),(.015,.016,.035) if side else (.016,.015,.035),dark,.001)
            rod((.303,0,1.60) if side else (0,.303,1.60),(.303,.105,1.64) if side else (.105,.303,1.64),.009,dark)
            rod((.303,0,1.60) if side else (0,.303,1.60),(.303,-.025,1.74) if side else (-.025,.303,1.74),.007,dark)
        box((0,0,1.80),(.60,.60,.07),white)
        for x in [-.27,.27]:
            for y in [-.27,.27]:rod((x,y,1.74),(x,y,2.03),.02,stone)
        bpy.ops.mesh.primitive_cone_add(vertices=4,radius1=.36,radius2=.13,depth=.31,location=(0,0,2.00),rotation=(0,0,math.pi/4));finish(bpy.context.object,slate)
        box((0,0,2.06),(.26,.26,.23),dark)
        bpy.ops.mesh.primitive_cone_add(vertices=4,radius1=.235,radius2=0,depth=.36,location=(0,0,2.35),rotation=(0,0,math.pi/4));finish(bpy.context.object,slate)
        rod((0,0,2.51),(0,0,2.72),.008,gold)
    elif kind=='LEANING_TOWER':
        before=set(scene.objects)
        cyl((0,0,.88),.31,1.70,white,64)
        for level in range(8):
            z=.05+level*.205
            cyl((0,0,z),.40,.033,stone,64)
            for i in range(24):
                a=i*math.tau/24
                cyl((math.cos(a)*.36,math.sin(a)*.36,z+.095),.011,.17,white,12)
                # stone capitals and curved lintel silhouettes
                sphere((math.cos(a)*.36,math.sin(a)*.36,z+.176),(.024,.024,.018),stone,12)
        cyl((0,0,1.74),.405,.036,stone,64)
        cyl((0,0,1.84),.26,.18,white,48)
        for i in range(14):
            a=i*math.tau/14;cyl((math.cos(a)*.27,math.sin(a)*.27,1.84),.015,.20,white)
        cyl((0,0,1.95),.30,.035,stone,48)
        # Lean the actual mesh coordinates, not just the screen image.
        from mathutils import Matrix
        tilt=Matrix.Rotation(math.radians(4),4,'Y')
        for o in set(scene.objects)-before:o.matrix_world=tilt@o.matrix_world
    elif kind=='TAJ_MAHAL':
        box((0,0,.18),(1.55,1.42,.22),white)
        box((0,0,.51),(.86,.80,.52),plaster)
        for side in [-1,1]:
            for x in [-.29,0,.29]:
                arch(x,side*.41,.30,.24,.38,.035,white)
        cyl((0,0,.83),.28,.18,white,64)
        sphere((0,0,1.08),(.36,.36,.33),white,48)
        # onion apex and finial
        bpy.ops.mesh.primitive_cone_add(vertices=48,radius1=.21,radius2=.012,depth=.28,location=(0,0,1.36));finish(bpy.context.object,white)
        rod((0,0,1.48),(0,0,1.66),.010,gold)
        for x in [-.53,.53]:
            for y in [-.50,.50]:
                cyl((x,y,.66),.057,1.05,white,24)
                for z in [.32,.68,1.03]:cyl((x,y,z),.075,.028,stone)
                for a in range(8):
                    aa=a*math.tau/8;rod((x+math.cos(aa)*.045,y+math.sin(aa)*.045,1.13),(x+math.cos(aa)*.045,y+math.sin(aa)*.045,1.28),.007,white)
                sphere((x,y,1.28),(.08,.08,.07),white)
        for x in [-.34,.34]:
            for y in [-.32,.32]:sphere((x,y,.83),(.12,.12,.12),white)
    elif kind=='PYRAMID':
        for row in range(32):
            span=1.68*(1-row/33)
            box((0,0,.038+row*.043),(span,span,.047),stone,.007)
            # Discrete irregular stones along visible edges.
            n=max(1,int(span/.085))
            for i in range(n):
                rng=random.Random(row*100+i)
                box((-span/2+(i+.5)*span/n,span/2,.035+row*.043),(span/n*.95,.035+rng.random()*.027,.043),stone,.004)
        box((.05,.69,.21),(.11,.04,.16),dark)
    elif kind=='STATUE_OF_LIBERTY':
        for z,w,h in [(.07,1.25,.14),(.22,.93,.18),(.38,.71,.12),(.59,.50,.36),(.80,.61,.07),(.87,.44,.08)]:box((0,0,z),(w,w,h),stone)
        # Sculpted volume of robe with individually folded surface, head, arms, tablet and seven rays.
        verts=[];faces=[]
        for j in range(15):
            z=.9+j*.055;r=.22*(1-j/24)
            for i in range(48):
                a=i*math.tau/48;rr=r*(1+.06*math.cos(i*1.7+j*.8));verts.append((math.cos(a)*rr,math.sin(a)*rr,z))
        for j in range(14):
            for i in range(48):a=j*48+i;b=j*48+(i+1)%48;faces.append((a,b,b+48,a+48))
        o=mesh('Folded copper robe',verts,faces,copper)
        for p in o.data.polygons:p.use_smooth=True
        sphere((0,0,1.77),(.115,.10,.16),copper,32)
        sphere((0,.088,1.78),(.075,.040,.10),copper,24)
        rod((-.12,0,1.60),(-.23,0,1.94),.067,copper);rod((-.23,0,1.94),(-.25,0,2.26),.042,copper)
        cyl((-.25,0,2.29),.072,.07,copper);sphere((-.25,0,2.40),(.055,.05,.10),gold)
        rod((.12,0,1.61),(.25,.04,1.45),.07,copper);rod((.25,.04,1.45),(.26,.12,1.64),.045,copper)
        o=box((.25,.14,1.57),(.15,.042,.27),copper);o.rotation_euler.y=.22
        for i in range(7):
            a=math.pi*.12+i*math.pi*.76/6
            rod((math.cos(a)*.1,.0,1.83+math.sin(a)*.055),(math.cos(a)*.27,.0,1.83+math.sin(a)*.24),.012,copper)


def sailboat():
    verts=[(-.42,0,.03),(-.26,-.14,.03),(.31,-.12,.03),(.46,0,.03),(.31,.12,.03),(-.26,.14,.03),(-.34,0,.10),(-.22,-.15,.10),(.30,-.13,.10),(.44,0,.10),(.30,.13,.10),(-.22,.15,.10)]
    mesh('Fibreglass hull',verts,[(i,(i+1)%6,(i+1)%6+6,i+6) for i in range(6)]+[(6,7,8,9,10,11)],white)
    box((-.10,0,.13),(.26,.17,.06),wood,.015)
    rod((.02,0,.10),(.02,0,.82),.008,dark)
    mesh('Canvas mainsail',[(.01,0,.80),(.01,0,.19),(-.35,.025,.22)],[(0,1,2)],white)
    mesh('Canvas jib',[(.04,0,.73),(.07,0,.18),(.39,.01,.17)],[(0,1,2)],white)
    for y in [-.14,.14]:rod((-.18,y,.14),(.25,y,.14),.003,dark)


def car(kind,direction):
    colors=[(.61,.09,.055),(.10,.29,.43),(.80,.81,.77),(.85,.62,.13),(.72,.71,.62),(.78,.30,.05),(.70,.08,.045)]
    mat=material('Vehicle paint',colors[kind],.21,.5)
    length=.28 if kind<4 else (.38 if kind<6 else .46)
    before=set(scene.objects)
    box((0,0,.059),(length,.132,.070),mat,.018)
    box((-.02,0,.105),(length*.52,.115,.065),glass,.019)
    box((-.02,0,.139),(length*.44,.11,.014),mat,.006)
    if kind==4:box((-.03,0,.12),(.23,.13,.10),white,.008)
    if kind==5:box((-.06,0,.145),(.25,.14,.15),white,.008)
    if kind==6:
        box((0,0,.13),(.42,.14,.13),mat,.011)
        for x in [-.16,-.08,0,.08,.16]:
            for y in [-.074,.074]:box((x,y,.155),(.055,.006,.058),glass,.002)
    for x in [-length*.31,length*.31]:
        for y in [-.070,.070]:
            o=cyl((x,y,.041),.038,.022,dark,20);o.rotation_euler.x=math.pi/2
            o=cyl((x,y*1.14,.041),.021,.025,white,20);o.rotation_euler.x=math.pi/2
    for y in [-.043,.043]:box((length/2+.003,y,.065),(.008,.029,.017),white,.003)
    from mathutils import Matrix
    # The bake camera sees +Y to screen-right and +X to screen-left.
    # Direction IDs use the GAME basis: east (+x), south (+y), west, north.
    bpy.context.view_layer.update()
    rot=Matrix.Rotation((1-direction)*math.pi/2,4,'Z')
    for o in set(scene.objects)-before:o.matrix_world=rot@o.matrix_world

def pedestrian(kind,direction,step):
    """Small stuffed residents; alternating leg/arm poses, six outfits."""
    before=set(scene.objects)
    shirt=material('Resident coat',felt_materials.dye(['e34e54','f3c442','4cb6ae','735cc0','ed9861','4087c5'][kind]))
    skin=material('Resident skin',felt_materials.dye(['f0bd91','b67850','e5aa79','d09466','7b4e39','edc59b'][kind]))
    hair=material('Resident hair',felt_materials.dye(['613d2a','302e39','b57c36','4b342b','292c36','815644'][kind]))
    trouser=material('Resident trousers',felt_materials.dye('304767'))
    stride=.022 if step==0 else -.022
    sphere((0,0,.097),(.025,.025,.036),shirt,16)
    sphere((0,0,.150),(.026,.026,.028),skin,16)
    sphere((-.007,0,.166),(.024,.027,.018),hair,16)
    sphere((.025,0,.151),(.006,.010,.006),skin,12)
    for side in [-1,1]:
        foot=side*stride
        rod((0,side*.014,.078),(foot,side*.017,.018),.010,trouser)
        sphere((foot+.004,side*.017,.012),(.016,.011,.009),dark,12)
        rod((0,side*.026,.113),(-foot,side*.033,.075),.008,shirt)
        sphere((-foot,side*.033,.071),(.009,.008,.01),skin,12)
    if kind==1:
        cyl((0,0,.179),.034,.009,gold)
        sphere((0,0,.186),(.022,.022,.011),gold,12)
    if kind in [2,4]:box((-.016,-.036,.076),(.030,.016,.034),red,.008)
    from mathutils import Matrix
    bpy.context.view_layer.update()
    rot=Matrix.Rotation((1-direction)*math.pi/2,4,'Z')
    for o in set(scene.objects)-before:o.matrix_world=rot@o.matrix_world

def clear():
    for o in list(scene.objects):
        if o not in static:bpy.data.objects.remove(o,do_unlink=True)
    for m in list(bpy.data.meshes):
        if m.users==0:bpy.data.meshes.remove(m)

def render(ident,fn,landmark=False):
    if selected and ident not in selected:return
    meta=OUT/(ident+'.json')
    if meta.exists() and (OUT/(ident+'.png')).exists() and json.loads(meta.read_text()).get('generation')==GENERATION:return
    clear();fn();bpy.context.view_layer.update()
    if landmark:
        reference=next(r for r in json.loads((ROOT/'visual-source/sources.json').read_text()) if r['id']==ident)
        for o in scene.objects:
            if o.type=='MESH' and not o.data.uv_layers:
                uv=o.data.uv_layers.new(name='Photographic stone UV')
                for poly in o.data.polygons:
                    axis=max(range(3),key=lambda a: abs(poly.normal[a]))
                    axes=[a for a in range(3) if a!=axis]
                    for loop in poly.loop_indices:
                        v=o.data.vertices[o.data.loops[loop].vertex_index].co
                        uv.data[loop].uv=(v[axes[0]]*2,v[axes[1]]*2)
            if o not in static:
                o['reference_photo']=reference['source'];o['reference_author']=reference['author']
                o['reference_license']='CC0';o['reference_sha256']=reference['sha256']
                o['reconstruction']='Authored from a photograph; unseen geometry inferred. Not photogrammetry.'
            o.select_set(o not in static)
        bpy.ops.export_scene.gltf(filepath=str(MODELS/(ident+'.glb')),use_selection=True,export_format='GLB',export_extras=True)
    # Bounds in orthographic screen space; pad captures soft silhouettes.
    coords=[]
    for o in scene.objects:
        if o.type=='MESH':
            for v in o.bound_box:
                p=o.matrix_world@Vector(v)
                coords.append(((p.x-p.y)/math.sqrt(2),(p.x+p.y)/math.sqrt(8)-p.z*math.sqrt(3)/2))
    minx=min(p[0] for p in coords)-.03;maxx=max(p[0] for p in coords)+.03
    miny=min(p[1] for p in coords)-.03;maxy=max(p[1] for p in coords)+.03
    w=math.ceil((maxx-minx)*P);h=math.ceil((maxy-miny)*P)
    maxx=minx+w/P;maxy=miny+h/P
    right=Vector((1,-1,0)).normalized();up=Vector((-1/math.sqrt(8),-1/math.sqrt(8),math.sqrt(3)/2))
    target=right*((minx+maxx)/2)-up*((miny+maxy)/2)
    direction=Vector((math.sqrt(3/8),math.sqrt(3/8),.5))
    camera.location=target+direction*20;camera.rotation_euler=(target-camera.location).to_track_quat('-Z','Y').to_euler()
    camera.data.ortho_scale=max(w,h)/P
    scene.render.resolution_x=w;scene.render.resolution_y=h;scene.render.resolution_percentage=100
    scene.render.filepath=str(OUT/(ident+'.png'))
    bpy.ops.render.render(write_still=True)
    data=dict(id=ident,width=w,height=h,anchorX=-minx*P,anchorY=-miny*P,tilePixels=512,finish='felt' if FELT else 'photographic',generation=GENERATION)
    if ident.startswith(('CAR_','PERSON_')):
        d=int(ident.split('_')[2]);data['facing']=['EAST','SOUTH','WEST','NORTH'][d]
        data['forwardScreen']=[(2,1),(-2,1),(-2,-1),(2,-1)][d]
    meta.write_text(json.dumps(data))
    print('ASSET_DONE',ident,w,h,flush=True)

# Ordered so an early residential and commercial preview can be inspected first.
for kind in ['ZONE_R','ZONE_C','ZONE_I']:
    for stage in range(1,4):
        for variant in range(6):render(f'{kind}_{stage}_{variant}',lambda k=kind,s=stage,v=variant:building(k,s,v))
for kind in ['PARK','GROVE','POWER_COAL','POWER_SOLAR','POWER_WIND','POWER_LINE','WATER_TOWER','WATER_PLANT','SEWAGE_PLANT','LANDFILL','INCINERATOR','RECYCLING','POLICE','FIRE','HOSPITAL','CLINIC','SCHOOL','FARM','BUS_STOP','SUBWAY_STATION','AIRPORT','SEAPORT']:
    render(kind,lambda k=kind:utility(k))
for kind in ['TOKYO_TOWER','ARC_DE_TRIOMPHE','COLOSSEUM','STATUE_OF_LIBERTY','BIG_BEN','LEANING_TOWER','TAJ_MAHAL','PYRAMID']:
    render(kind,lambda k=kind:monument(k),True)
for k in range(7):
    for d in range(4):render(f'CAR_{k}_{d}',lambda k=k,d=d:car(k,d))

render('SAILBOAT',sailboat)

if FELT:
    for k in range(6):
        for d in range(4):
            for step in range(2):render(f'PERSON_{k}_{d}_{step}',lambda k=k,d=d,s=step:pedestrian(k,d,s))
    from theme_models import render_themes
    render_themes(globals())
