"""Export the existing felt city designs as bounded, colored meshes for live GLES.

blender -b -P tools/realistic/export_globe_models.py
No render, external download, or photo reconstruction is performed here.
"""
import sys, json, math, gzip, struct
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(Path(__file__).parent))
sys.argv=['blender','--','--felt']
source=(Path(__file__).parent/'build_assets.py').read_text().split('def render(ident,')[0]
source=source.replace('ring_count=12','ring_count=6')
scope={'__file__':str(Path(__file__).parent/'build_assets.py')}
exec(compile(source,'build_assets.py','exec'),scope)
bpy=scope['bpy']
# Stitching is supplied by the live shader; don't export thousands of thread cylinders.
scope['seam']=lambda *a,**kw:None
original_sphere=scope['sphere']
scope['sphere']=lambda loc,scale,mat,segments=20:original_sphere(loc,scale,mat,min(segments,10))
OUT=ROOT/'app/src/main/assets/globe';OUT.mkdir(parents=True,exist_ok=True)
manifest={}
def srgb(v):return 12.92*v if v<=.0031308 else 1.055*v**(1/2.4)-.055
def render(ident,fn,landmark=False):
    target=OUT/(ident+'.meshdata')
    if target.exists():
        with gzip.open(target,'rb') as f: count=struct.unpack('>I',f.read(4))[0]
        manifest[ident]={'vertices':count};return
    scope['clear']();fn();bpy.context.view_layer.update()
    values=[]
    for o in scope['scene'].objects:
        if o.type!='MESH':continue
        mesh=o.data;mesh.calc_loop_triangles();matrix=o.matrix_world;normal=matrix.to_3x3().inverted().transposed()
        for tri in mesh.loop_triangles:
            mat=mesh.materials[tri.material_index] if mesh.materials else None
            col=tuple(max(0,min(1,srgb(v))) for v in (mat.diffuse_color[:3] if mat else (.5,.5,.5)))
            for vi in tri.vertices:
                v=mesh.vertices[vi];p=matrix@v.co
                n=(normal@(v.normal if mesh.polygons[tri.polygon_index].use_smooth else tri.normal)).normalized()
                # Blender +Y is the game's +X, and Blender +X is the game's +Y.
                values.extend((p.y,p.x,p.z,n.y,n.x,n.z,*col))
    count=len(values)//9
    assert count>0 and count<150000,(ident,count)
    with gzip.GzipFile(filename=str(target),mode='wb',mtime=0) as f:
        f.write(struct.pack('>I',count));f.write(struct.pack('>'+str(len(values))+'f',*values))
    manifest[ident]={'vertices':count}
    print('GLOBE',ident,count,flush=True)
scope['render']=render
suffix=(Path(__file__).parent/'build_assets.py').read_text().split('# Ordered so an early residential')[1]
exec(compile('# Ordered so an early residential'+suffix,'model-list','exec'),scope)
(OUT/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('EXPORTED',len(manifest),'MODELS',flush=True)
