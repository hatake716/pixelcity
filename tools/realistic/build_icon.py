"""Original felt launcher icon, using the same wool shader as the city.

Preserves the three-building motif of the original VectorDrawable. Foreground
and opaque background stay separate for Android adaptive masks and parallax.
Run: blender -b -t 12 -P tools/realistic/build_icon.py
"""
import bpy, math, sys, json, hashlib
from pathlib import Path
from mathutils import Vector

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'artifacts/felt-icon';OUT.mkdir(parents=True,exist_ok=True)
sys.path.insert(0,str(Path(__file__).parent))
from felt_materials import felt,dye,warm_light

bpy.ops.wm.read_factory_settings(use_empty=True)
scene=bpy.context.scene
scene.render.engine='CYCLES';scene.cycles.device='CPU'
scene.cycles.samples=64;scene.cycles.use_denoising=True;scene.cycles.max_bounces=5
scene.render.film_transparent=True
scene.render.image_settings.file_format='PNG';scene.render.image_settings.color_mode='RGBA'
scene.render.resolution_x=1536;scene.render.resolution_y=1536;scene.render.resolution_percentage=100
scene.world=bpy.data.worlds.new('Craft table daylight');scene.world.use_nodes=True
bpy.ops.object.light_add(type='SUN',location=(-3,-4,8));sun=bpy.context.object
sun.rotation_euler=(.40,-.48,-.40)
warm_light(scene,sun)
# A large, soft fill keeps the icon legible on small launcher cells.
bpy.ops.object.light_add(type='AREA',location=(1,-4,6));fill=bpy.context.object
fill.data.energy=110;fill.data.shape='DISK';fill.data.size=5
fill.rotation_euler=(Vector((0,0,.5))-fill.location).to_track_quat('-Z','Y').to_euler()
bpy.ops.object.camera_add();camera=bpy.context.object;camera.data.type='ORTHO';scene.camera=camera
static={sun,fill,camera}

mats={name:felt(name,dye(hex)) for name,hex in {
    'ivory':'f8dfaa','cream':'fff0d1','roof':'d54f40','roofShadow':'b93435',
    'blue':'429fbb','navy':'2c5269','window':'80c9d7','grass':'81ac45',
    'edge':'527b39','leaf':'40916a','wood':'805039','thread':'fff1ce',
    'sky':'b7dcd1','yellow':'e9b840',
}.items()}

def finish(o,mat,bevel=0):
    o.data.materials.append(mats[mat])
    if bevel:
        m=o.modifiers.new('Padded wool edge','BEVEL');m.width=bevel;m.segments=5
        o.modifiers.new('Soft normals','WEIGHTED_NORMAL')
    return o

def box(loc,size,mat,bevel=.025):
    bpy.ops.mesh.primitive_cube_add(size=1,location=loc)
    o=bpy.context.object;o.scale=size
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    return finish(o,mat,min(bevel,min(size)*.32))

def ball(loc,size,mat):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=32,ring_count=20,location=loc)
    o=bpy.context.object;o.scale=size
    for p in o.data.polygons:p.use_smooth=True
    return finish(o,mat)

def thread(a,b,r=.0055):
    delta=Vector(b)-Vector(a)
    bpy.ops.mesh.primitive_cylinder_add(vertices=12,radius=r,depth=delta.length,location=(Vector(a)+Vector(b))/2)
    o=bpy.context.object;o.rotation_euler=delta.to_track_quat('Z','Y').to_euler()
    for p in o.data.polygons:p.use_smooth=True
    return finish(o,'thread')

def stitch(a,b,step=.067):
    a,b=Vector(a),Vector(b);count=max(1,int((b-a).length/step))
    for i in range(count):thread(a+(b-a)*((i+.14)/count),a+(b-a)*((i+.64)/count))

def roof(x,y,z,w,d,h):
    vs=[(x-w/2,y-d/2,z),(x+w/2,y-d/2,z),(x+w/2,y+d/2,z),(x-w/2,y+d/2,z),(x,y-d/2,z+h),(x,y+d/2,z+h)]
    me=bpy.data.meshes.new('Folded roof');me.from_pydata(vs,[],[(0,1,4),(3,5,2),(0,4,5,3),(4,1,2,5)]);me.update()
    o=bpy.data.objects.new('Red roof',me);scene.collection.objects.link(o);finish(o,'roof')
    m=o.modifiers.new('Thick felt','SOLIDIFY');m.thickness=.045
    m=o.modifiers.new('Rounded fold','BEVEL');m.width=.02;m.segments=4
    for a,b in [(0,4),(4,1),(3,5),(5,2),(0,3),(1,2)]:
        stitch(Vector(vs[a])+Vector((0,0,.019)),Vector(vs[b])+Vector((0,0,.019)))

def window(x,y,z,side=False):
    # Thick appliqué trim survives downsampling to launcher size.
    if side:
        box((x,y,z),(.026,.103,.124),'cream',.006)
        box((x+.016,y,z),(.018,.071,.090),'navy',.005)
        box((x+.027,y-.018,z+.013),(.008,.029,.046),'window',.003)
    else:
        box((x,y,z),(.103,.026,.124),'cream',.006)
        box((x,y+.016,z),(.071,.018,.090),'navy',.005)
        box((x-.018,y+.027,z+.013),(.029,.008,.046),'window',.003)

def house(x,y,w,d,h,floors):
    z=.105
    box((x,y,z+h/2),(w,d,h),'ivory',.043)
    for floor in range(floors):
        zz=z+.14+floor*.23
        for xx in [-w*.25,w*.25]:window(x+xx,y+d/2+.006,zz)
        window(x+w/2+.006,y-d*.18,zz,True)
    box((x+w/2+.012,y+d*.24,z+.091),(.033,.112,.175),'wood',.01)
    roof(x,y,z+h+.017,w+.105,d+.09,.20)
    box((x-w*.26,y-d*.20,z+h+.17),(.09,.105,.23),'roofShadow',.015)

# One plush lawn tile ties the buildings together without a surrounding badge.
box((0,0,.040),(1.70,1.51,.10),'edge',.06)
box((0,0,.087),(1.69,1.50,.06),'grass',.045)
for a,b in [((-.75,-.66,.124),(.75,-.66,.124)),((.75,-.66,.124),(.75,.66,.124)),((.75,.66,.124),(-.75,.66,.124)),((-.75,.66,.124),(-.75,-.66,.124))]:stitch(a,b)

# Back-right office, left apartment, small front house: original icon identity.
x,y=-.31,-.25;w,d,h=.51,.47,1.04
box((x,y,.105+h/2),(w,d,h),'blue',.045)
for floor in range(4):
    z=.25+floor*.23
    for xx in [-.135,.135]:window(x+xx,y+d/2+.008,z)
    for yy in [-.12,.12]:window(x+w/2+.008,y+yy,z,True)
box((x,y,h+.12),(w+.07,d+.07,.072),'navy',.023)
stitch((x-w/2,y+d/2+.045,h+.16),(x+w/2,y+d/2+.045,h+.16))
house(.36,-.18,.48,.49,.53,2)
house(.17,.40,.47,.43,.29,1)
# A small stuffed tree and a low shrub give the street a warm garden edge.
box((-.58,.36,.22),(.055,.06,.23),'wood',.015)
ball((-.58,.36,.39),(.15,.15,.16),'leaf')
ball((-.62,.32,.49),(.12,.13,.13),'grass')
ball((.66,.29,.17),(.11,.105,.09),'leaf')

bpy.context.view_layer.update()
right=Vector((-1,1,0)).normalized()
up=Vector((-1/math.sqrt(8),-1/math.sqrt(8),math.sqrt(3)/2))
direction=Vector((math.sqrt(3/8),math.sqrt(3/8),.5))
points=[o.matrix_world@Vector(v) for o in scene.objects if o.type=='MESH' for v in o.bound_box]
xy=[(p.dot(right),p.dot(up)) for p in points]
cx=(min(x for x,y in xy)+max(x for x,y in xy))/2
cy=(min(y for x,y in xy)+max(y for x,y in xy))/2
radius=max(math.hypot(x-cx,y-cy) for x,y in xy)+.045
target=right*cx+up*cy
camera.location=target+direction*20;camera.rotation_euler=(target-camera.location).to_track_quat('-Z','Y').to_euler()
# All content fits inside Android's 66/108 safe circle, including stitches.
camera.data.ortho_scale=radius/.298
scene.render.filepath=str(OUT/'foreground.png');bpy.ops.render.render(write_still=True)

# A separate seamless felt background fills any launcher mask.
for o in list(scene.objects):
    if o not in static:bpy.data.objects.remove(o,do_unlink=True)
bpy.ops.mesh.primitive_plane_add(size=5)
bpy.context.object.data.materials.append(mats['sky'])
camera.location=(0,0,6);camera.rotation_euler=(0,0,0);camera.data.ortho_scale=2.2
scene.render.film_transparent=False
scene.render.resolution_x=1024;scene.render.resolution_y=1024
scene.render.filepath=str(OUT/'background.png');bpy.ops.render.render(write_still=True)
print('FELT_ICON_RENDERED',flush=True)
