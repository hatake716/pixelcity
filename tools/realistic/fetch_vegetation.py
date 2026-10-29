import json,hashlib,urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def get(u):return urllib.request.urlopen(urllib.request.Request(u,headers={'User-Agent':'Pixelcity-AssetBuilder/0.4 (https://github.com/hatake716/pixelcity)'}),timeout=120).read()
sources=json.loads((ROOT/'visual-source/sources.json').read_text())
for name in ['tree_small_02','shrub_02']:
    folder=ROOT/'visual-source/vegetation'/name;folder.mkdir(parents=True,exist_ok=True)
    info=json.loads(get('https://api.polyhaven.com/info/'+name));files=json.loads(get('https://api.polyhaven.com/files/'+name));g=files['gltf']['1k']['gltf']
    downloads={name+'.gltf':g,**g['include']};records=[]
    for local,meta in downloads.items():
        p=folder/local;p.parent.mkdir(parents=True,exist_ok=True)
        if not p.exists():p.write_bytes(get(meta['url']))
        assert hashlib.md5(p.read_bytes()).hexdigest()==meta['md5']
        records.append(dict(file=str(p.relative_to(ROOT)),url=meta['url'],sha256=hashlib.sha256(p.read_bytes()).hexdigest()))
    (folder/'metadata.json').write_text(json.dumps(info,indent=2))
    if not any(s['id']==name for s in sources):sources.append(dict(id=name,type='CC0 botanical 3D model',source='https://polyhaven.com/a/'+name,author=info.get('authors'),license='CC0',license_url='https://polyhaven.com/license',modification='Scaled and rotated; baked with city models into orthographic images.',files=records))
    print('VEGETATION',name,flush=True)
(ROOT/'visual-source/sources.json').write_text(json.dumps(sources,indent=2,ensure_ascii=False))
