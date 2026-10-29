"""Bake a continuous physically lit water surface; no external image input."""
import bpy,math
from pathlib import Path
bpy.ops.wm.read_factory_settings(use_empty=True)
s=bpy.context.scene;s.render.engine='CYCLES';s.cycles.samples=48;s.cycles.use_denoising=True
s.render.resolution_x=512;s.render.resolution_y=512;s.render.resolution_percentage=100
s.world=bpy.data.worlds.new('Marine sky');s.world.use_nodes=True
s.world.node_tree.nodes['Background'].inputs[0].default_value=(.60,.81,1,1);s.world.node_tree.nodes['Background'].inputs[1].default_value=.7
bpy.ops.mesh.primitive_plane_add(size=3)
m=bpy.data.materials.new('Small wind waves');m.use_nodes=True;b=bpy.context.object;b.data.materials.append(m)
n=m.node_tree.nodes;l=m.node_tree.links;p=n['Principled BSDF'];p.inputs['Base Color'].default_value=(.055,.19,.22,1);p.inputs['Roughness'].default_value=.21;p.inputs['Metallic'].default_value=.35
noise=n.new('ShaderNodeTexNoise');noise.inputs['Scale'].default_value=22;noise.inputs['Detail'].default_value=3;noise.inputs['Roughness'].default_value=.65
bump=n.new('ShaderNodeBump');bump.inputs['Strength'].default_value=.55;bump.inputs['Distance'].default_value=.09;l.new(noise.outputs['Fac'],bump.inputs['Height']);l.new(bump.outputs[0],p.inputs['Normal'])
bpy.ops.object.light_add(type='SUN');bpy.context.object.rotation_euler=(.7,-.4,-.4);bpy.context.object.data.energy=1.8;bpy.context.object.data.angle=.14
bpy.ops.object.camera_add(location=(0,0,4));cam=bpy.context.object;cam.data.type='ORTHO';cam.data.ortho_scale=2;cam.rotation_euler=(0,0,0);s.camera=cam
s.render.image_settings.file_format='PNG';s.render.filepath=str(Path(__file__).resolve().parents[2]/'artifacts/realistic/water.png');bpy.ops.render.render(write_still=True)
