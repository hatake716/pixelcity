"""Fetch NASA source rasters; originals stay in ignored artifacts, attribution is retained."""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import urllib.request,hashlib,json
ROOT=Path(__file__).resolve().parents[2]
items=[
 ('blue-marble-july.jpg','https://assets.science.nasa.gov/content/dam/science/esd/eo/images/bmng/bmng-base/july/world.200407.3x5400x2700.jpg','NASA Earth Observatory / Reto Stöckli; Blue Marble Next Generation, July 2004','https://science.nasa.gov/earth/earth-observatory/blue-marble-next-generation/base-map/'),
 ('elevation.tif','https://assets.science.nasa.gov/content/dam/science/esd/eo/images/bmng/topography/gebco_08_rev_elev_5400x2700.tif','NASA Earth Observatory / Jesse Allen; GEBCO / British Oceanographic Data Centre','https://science.nasa.gov/earth/earth-observatory/blue-marble-next-generation/topography-bathymetry-maps/'),
 ('bathymetry.tif','https://assets.science.nasa.gov/content/dam/science/esd/eo/images/bmng/bathymetry/gebco_08_rev_bath_5400x2700.tif','NASA Earth Observatory / Jesse Allen; GEBCO / British Oceanographic Data Centre','https://science.nasa.gov/earth/earth-observatory/blue-marble-next-generation/topography-bathymetry-maps/')]
def fetch(item):
 name,url,credit,page=item;p=ROOT/'artifacts/earth'/name
 p.parent.mkdir(parents=True,exist_ok=True)
 if not p.exists():
  with urllib.request.urlopen(url,timeout=120) as response:p.write_bytes(response.read())
 return dict(file=name,url=url,sourcePage=page,credit=credit,sha256=hashlib.sha256(p.read_bytes()).hexdigest(),bytes=p.stat().st_size)
with ThreadPoolExecutor(max_workers=3) as pool:result=list(pool.map(fetch,items))
metadata={'sources':result,'terms':['https://www.nasa.gov/nasa-brand-center/images-and-media/','https://www.gebco.net/data-products/gridded-bathymetry/terms-of-use'],'usage':'NASA image-use guidelines; GEBCO grid public domain. Credit retained. No endorsement. Not for navigation.','modifications':'Reprojected and resampled to game tiles; terrain categories derived for gameplay; relief exaggerated for visibility; water and atmosphere rendered independently.'}
output=ROOT/'app/src/main/assets/earth/sources.json'
output.parent.mkdir(parents=True,exist_ok=True)
output.write_text(json.dumps(metadata,ensure_ascii=False,indent=2))
print(json.dumps(result,indent=2))
