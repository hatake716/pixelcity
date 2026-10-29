"""Original wool-felt materials. No photographic textures or external models required.

Shared by the building baker and the four ground swatches. Noise supplies the
compressed wool body; a repeatable fibre texture supplies visible loose threads.
"""
import bpy, math, random, struct, zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'artifacts/felt'
OUT.mkdir(parents=True, exist_ok=True)


def fibre_image():
    path = OUT / 'wool-fibres.png'
    if not path.exists():
        # Wrap every fibre at the tile edge, so swatches repeat without a seam.
        size = 1024
        rng = random.Random(716)
        pixels = bytearray([128]) * (size * size)
        for _ in range(98000):
            x, y = rng.randrange(size), rng.randrange(size)
            angle = rng.random() * math.tau
            length = rng.uniform(3, 19)
            shade = rng.choice((56, 76, 92, 160, 184, 209, 228))
            for step in range(math.ceil(length * 1.5)):
                t = step / 1.5
                xx = int(x + math.cos(angle) * t) % size
                yy = int(y + math.sin(angle) * t + math.sin(t / length * math.pi) * 1.4) % size
                idx = yy * size + xx
                pixels[idx] = int(pixels[idx] * .2 + shade * .8)
        def chunk(kind, data):
            return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
        rows = b''.join(b'\x00' + pixels[y*size:(y+1)*size] for y in range(size))
        path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 0, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(rows, 9)) + chunk(b'IEND', b''))
    return bpy.data.images.load(str(path), check_existing=True)


def felt(name, color):
    m = bpy.data.materials.new('Wool felt / ' + name)
    m.diffuse_color = (*color, 1)
    m.use_nodes = True
    n, l = m.node_tree.nodes, m.node_tree.links
    bs = n.get('Principled BSDF')
    bs.inputs['Roughness'].default_value = .98
    bs.inputs['Metallic'].default_value = 0
    bs.inputs['Specular IOR Level'].default_value = .12
    bs.inputs['Base Color'].default_value = (*color, 1)
    bs.inputs['Sheen Weight'].default_value = .28
    bs.inputs['Sheen Roughness'].default_value = .85
    bs.inputs['Sheen Tint'].default_value = (*(min(1, v * 1.4 + .08) for v in color), 1)
    tex = n.new('ShaderNodeTexImage')
    tex.image = fibre_image()
    tex.image.colorspace_settings.name = 'Non-Color'
    tex.projection = 'BOX'; tex.projection_blend = .25
    coord = n.new('ShaderNodeTexCoord')
    l.new(coord.outputs['Object'], tex.inputs['Vector'])
    body = n.new('ShaderNodeTexNoise')
    body.inputs['Scale'].default_value = 48
    body.inputs['Detail'].default_value = 3
    l.new(coord.outputs['Object'], body.inputs['Vector'])
    mix = n.new('ShaderNodeMixRGB'); mix.blend_type = 'MULTIPLY'
    mix.inputs[0].default_value = .38
    l.new(tex.outputs['Color'], mix.inputs[1]); l.new(body.outputs['Fac'], mix.inputs[2])
    ramp = n.new('ShaderNodeValToRGB')
    ramp.color_ramp.elements[0].position = .08
    ramp.color_ramp.elements[0].color = (*(v*.57 for v in color), 1)
    ramp.color_ramp.elements[1].position = .79
    ramp.color_ramp.elements[1].color = (*(min(1,v*1.35+.006) for v in color), 1)
    l.new(mix.outputs[0], ramp.inputs[0]); l.new(ramp.outputs[0], bs.inputs['Base Color'])
    bump = n.new('ShaderNodeBump')
    bump.inputs['Strength'].default_value = .68
    bump.inputs['Distance'].default_value = .025
    l.new(mix.outputs[0], bump.inputs['Height']); l.new(bump.outputs[0], bs.inputs['Normal'])
    return m


def warm_light(scene, sun):
    bg = scene.world.node_tree.nodes['Background']
    bg.inputs['Color'].default_value = (1, .96, .89, 1)
    bg.inputs['Strength'].default_value = .7
    sun.data.energy = 1.8; sun.data.angle = .55
    scene.view_settings.view_transform = 'AgX'
    scene.view_settings.look = 'AgX - Medium High Contrast'


PALETTE = {
    'Warm limestone': (.67,.47,.28), 'Lime plaster': (.93,.80,.60),
    'Weathered fired brick': (.63,.28,.20), 'Individual slate tiles': (.26,.43,.43),
    'Meadow soil': (.40,.57,.22), 'Asphalt': (.34,.34,.39),
    'Sand': (.88,.66,.36), 'Concrete': (.67,.55,.41),
    'Ivory stone': (.96,.84,.66), 'Ironwork': (.21,.24,.25),
    'Timber': (.39,.22,.14), 'Vermilion': (.74,.31,.24),
    'Brass': (.88,.62,.23), 'Oxidised copper': (.31,.60,.45),
    'Reflected sky glass': (.26,.48,.59), 'Pale reflected sky': (.51,.71,.72),
    'Water': (.23,.56,.66),
}

def dye(hex_color):
    """Artist palettes are sRGB; Blender's shader sockets require linear RGB."""
    values = [int(hex_color.lstrip('#')[i:i+2], 16)/255 for i in (0, 2, 4)]
    return tuple(v/12.92 if v <= .04045 else ((v+.055)/1.055)**2.4 for v in values)

PALETTE.update({name: dye(value) for name, value in {
    'Warm limestone': 'd6b07c', 'Lime plaster': 'f6dcab',
    'Weathered fired brick': 'b94c38', 'Individual slate tiles': '285264',
    'Meadow soil': '79a944', 'Asphalt': '465266', 'Sand': 'e6bc6b',
    'Concrete': 'bfac91', 'Ivory stone': 'fff0ce', 'Ironwork': '263747',
    'Timber': '724334', 'Vermilion': 'db483f', 'Brass': 'eaae25',
    'Oxidised copper': '39967a', 'Reflected sky glass': '247eac',
    'Pale reflected sky': '6cbccc', 'Water': '259db6',
}.items()})
FACADE_COLORS = list(map(dye, ['edaf36', 'e56855', '40a98a', 'f5cf66', '4f94c4', 'b5578b']))


def material(name, color):
    if name.startswith('Paint '): color = FACADE_COLORS[int(name.split()[-1]) % 6]
    elif name.startswith('Leaves '): color = dye(['287354','429343','82b842','d2b744'][int(name.split()[-1])%4])
    elif name == 'Vehicle paint': color = tuple(v ** 1.7 for v in color)
    else: color = PALETTE.get(name, color)
    return felt(name, color)
