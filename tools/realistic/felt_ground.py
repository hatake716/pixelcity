"""Render four original felt swatches with the same wool shader as the city."""
import bpy, math, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
from felt_materials import felt, warm_light, OUT, PALETTE

bpy.ops.wm.read_factory_settings(use_empty=True)
s=bpy.context.scene;s.render.engine='CYCLES';s.cycles.samples=32;s.cycles.use_denoising=True
s.render.resolution_x=1024;s.render.resolution_y=1024;s.render.resolution_percentage=100
s.world=bpy.data.worlds.new('Craft room daylight');s.world.use_nodes=True
bpy.ops.object.light_add(type='SUN');sun=bpy.context.object;sun.rotation_euler=(.45,-.4,-.4)
warm_light(s,sun)
bpy.ops.object.camera_add(location=(0,0,4));cam=bpy.context.object;cam.data.type='ORTHO';cam.data.ortho_scale=1;cam.rotation_euler=(0,0,0);s.camera=cam

def line(points,mat,r=.0018):
    cu=bpy.data.curves.new('Embroidered wave','CURVE');cu.dimensions='3D';cu.bevel_depth=r;cu.bevel_resolution=2
    sp=cu.splines.new('POLY');sp.points.add(len(points)-1)
    for p,v in zip(sp.points,points):p.co=(*v,1)
    ob=bpy.data.objects.new('Cotton thread',cu);s.collection.objects.link(ob);ob.data.materials.append(mat)

for name,key in [('grass','Meadow soil'),('sand','Sand'),('asphalt','Asphalt'),('water','Water')]:
    color=PALETTE[key]
    for o in list(s.objects):
        if o not in (sun,cam):bpy.data.objects.remove(o,do_unlink=True)
    bpy.ops.mesh.primitive_plane_add(size=1.1)
    bpy.context.object.data.materials.append(felt(name,color))
    if name=='water':
        thread=felt('Sea embroidery',(.48,.73,.66))
        for row in range(3):
            y=-.5+row/3+.13
            # Padded, scalloped appliqué strips; repeat exactly along both tile axes.
            points=[]
            for i in range(129):
                x=-.5+i/128;yy=y+.033*math.sin(i/128*math.tau+row*math.tau/3)
                points.extend([(x,yy,.005),(x,yy+.047,.005)])
            me=bpy.data.meshes.new('Felt wave');me.from_pydata(points,[],[(i*2,i*2+1,i*2+3,i*2+2) for i in range(128)]);me.update()
            ob=bpy.data.objects.new('Layered blue cloth',me);s.collection.objects.link(ob)
            ob.data.materials.append(felt('Wave '+str(row),tuple(v*1.3 for v in color)))
            sol=ob.modifiers.new('Felt thickness','SOLIDIFY');sol.thickness=.004
            for i in range(0,128,5):
                pts=[]
                for j in range(3):
                    x=-.5+(i+j)/128;yy=y+.033*math.sin((i+j)/128*math.tau+row*math.tau/3)+.012
                    pts.append((x,yy,.008))
                line(pts,thread,.0014)
    s.render.image_settings.file_format='PNG';s.render.filepath=str(OUT/(name+'.png'))
    bpy.ops.render.render(write_still=True)
