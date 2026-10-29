"""Reproject NASA/GEBCO rasters to the game's equal-area 512 x 512 sphere.

Run fetch_earth_sources.py first. Requires Pillow, numpy and scipy. Biomes are
gameplay classifications, not a scientific land-cover product. Heights are
decoded from NASA's published 0..6400 m / -8000..0 m grayscale maps.
"""
from pathlib import Path
import gzip, hashlib, json, struct
import numpy as np
from PIL import Image
from scipy.ndimage import map_coordinates, minimum_filter

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'artifacts/earth'
ASSETS = ROOT / 'app/src/main/assets/earth'
RESOURCE = ROOT / 'app/src/main/resources/earth'
RESOURCE.mkdir(parents=True, exist_ok=True)
SIZE = 512
lat = np.arcsin(1 - 2 * (np.arange(SIZE) + .5) / SIZE)
yy, xx = np.meshgrid((.5 - lat / np.pi) * 2700 - .5,
                     (np.arange(SIZE) + .5) / SIZE * 5400 - .5, indexing='ij')

def sample(name):
    data = np.asarray(Image.open(SOURCE / name), dtype=np.float32)
    if data.ndim == 2:
        return map_coordinates(data, [yy, xx], order=1, mode='nearest')
    return np.stack([map_coordinates(data[:, :, c], [yy, xx], order=1, mode='nearest')
                     for c in range(3)], axis=-1)

rgb = sample('blue-marble-july.jpg')
elev = sample('elevation.tif') / 255 * 6400
bathy = (sample('bathymetry.tif') / 255 - 1) * 8000
r, g, b = rgb.transpose(2, 0, 1)
water = (bathy < -20) | ((b > r * 1.45) & (b > g * 1.15) & (r < 75))
height = np.where(water, np.minimum(-1, bathy), elev).round().astype('>i2')
latitude = np.broadcast_to(np.abs(lat[:, None]) * 180 / np.pi, (SIZE, SIZE))
# Lowlands remain buildable; forests / desert / ice follow photographed cover.
biome = np.zeros((SIZE, SIZE), dtype=np.uint8)
biome[(g > r * 1.12) & (elev > 500)] = 6
biome[(latitude < 20) & (g > r * 1.12) & (g < 90)] = 7
biome[(r > 110) & (r > g * 1.08) & (g > b * 1.2)] = 8
biome[elev > 1400] = 3
biome[elev > 2500] = 4
biome[(latitude > 60) & (r > 145) & (b > 140)] = 9
biome[(latitude > 68) & (r > 160) & (b > 155)] = 10
biome[(latitude < 68) & (elev > 2400) & (b > 155) & (r > 160)] = 10
biome[water] = 1
# Select an existing natural tract large enough for Hiyori's street and services.
# No pixels or terrain categories are changed to manufacture a starting square.
valid = minimum_filter((biome == 0).astype(np.uint8), size=(7, 17), mode='constant') > 0
rows, cols = np.where(valid)
assert len(rows), 'No natural tutorial site found'
target_x = (-95 / 360 + .5) * SIZE
target_y = (1 - np.sin(np.deg2rad(37))) * SIZE / 2
k = np.argmin((cols - target_x) ** 2 + (rows - target_y) ** 2)
start_x, start_y = int(cols[k]), int(rows[k])
colors = (0xff000000 | (r.round().astype(np.uint32) << 16) |
          (g.round().astype(np.uint32) << 8) | b.round().astype(np.uint32)).astype('>u4')
payload = struct.pack('>5i', 0x45525448, 1, SIZE, start_x, start_y)
payload += height.tobytes() + biome.tobytes() + colors.tobytes()
with (RESOURCE / 'terrain.bin.gz').open('wb') as out:
    with gzip.GzipFile(fileobj=out, mode='wb', mtime=0) as stream:
        stream.write(payload)
Image.open(SOURCE / 'blue-marble-july.jpg').resize((4096, 2048), Image.Resampling.LANCZOS).save(
    ASSETS / 'blue-marble.jpg', quality=93, optimize=True)
metadata = json.loads((ASSETS / 'sources.json').read_text())
metadata['derived'] = dict(size=SIZE, projection='Longitude / sine latitude (equal area)',
    startX=start_x, startY=start_y, landHeightRangeMeters=[0, 6400], oceanDepthRangeMeters=[-8000, 0],
    verticalExaggeration=65, classification='Heuristic gameplay biomes from height and photographed color; not scientific land cover',
    generated=[dict(file=str(p.relative_to(ROOT)), sha256=hashlib.sha256(p.read_bytes()).hexdigest())
               for p in [RESOURCE / 'terrain.bin.gz', ASSETS / 'blue-marble.jpg']])
(ASSETS / 'sources.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + '\n')
print(json.dumps(metadata['derived'], indent=2))
print('Biome counts:', dict(zip(*[a.tolist() for a in np.unique(biome, return_counts=True)])))
